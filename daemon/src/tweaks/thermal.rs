// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use crate::tweaks::state;
use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Mutex, OnceLock};

const ENERGY_AWARE_PATHS: &[&str] = &[
    "/sys/devices/system/cpu/sched_energy_aware",
    "/proc/sys/kernel/sched_energy_aware",
];
const COOLING_DIRS: &[&str] = &["/sys/class/thermal", "/sys/devices/virtual/thermal"];
const COOLING_TARGETS: &[&str] = &["cpu-pause", "devfreq", "apu_cooler", "gpu"];

const PPM_STATUS_PATH: &str = "/proc/ppm/policy_status";
const PPM_ENABLED_PATH: &str = "/proc/ppm/enabled";
const PPM_MAX_PATH: &str = "/proc/ppm/policy/hard_userlimit_max_cpu_freq";
const PPM_MIN_PATH: &str = "/proc/ppm/policy/hard_userlimit_min_cpu_freq";
const PPM_LIMIT_POLICIES: &[&str] = &["PWR_THRO", "THERMAL"];

const THERMAL_SCONFIG_PATHS: &[&str] = &[
    "/sys/class/thermal/thermal_message/sconfig",
    "/sys/devices/virtual/thermal/thermal_message/sconfig",
];
const GPU_LIMIT_TABLE_PATHS: &[&str] = &[
    "/proc/gpufreqv2/limit_table",
    "/proc/gpufreq/gpufreq_limit_table",
];
const THERMAL_GATE_MILLI: i64 = 82_000;

static THERMAL_HOT: AtomicBool = AtomicBool::new(false);
static FAILED_WRITES: OnceLock<Mutex<HashSet<String>>> = OnceLock::new();

fn failed_writes() -> &'static Mutex<HashSet<String>> {
    FAILED_WRITES.get_or_init(|| Mutex::new(HashSet::new()))
}

pub(crate) fn write_enforced(path: &str, value: &str) -> bool {
    if !Path::new(path).exists() {
        return false;
    }
    let key = format!("{}={}", path, value);
    {
        let set = failed_writes()
            .lock()
            .unwrap_or_else(|error| error.into_inner());
        if set.contains(&key) {
            return false;
        }
    }
    if state::write_tracked(path, value, false) {
        return true;
    }
    failed_writes()
        .lock()
        .unwrap_or_else(|error| error.into_inner())
        .insert(key);
    false
}

fn max_zone_temp() -> Option<i64> {
    let mut max: Option<i64> = None;
    for dir in COOLING_DIRS {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.starts_with("thermal_zone") {
                continue;
            }
            let base = format!("{}/{}", dir, name);
            let Ok(raw) = fs::read_to_string(format!("{}/temp", base)) else {
                continue;
            };
            let Ok(raw) = raw.trim().parse::<i64>() else {
                continue;
            };
            let temp = if raw.abs() > 1000 { raw } else { raw * 1000 };
            if !(1_000..=200_000).contains(&temp) {
                continue;
            }
            max = Some(max.map_or(temp, |current: i64| current.max(temp)));
        }
    }
    max
}

pub(crate) fn thermal_budget_open() -> bool {
    let hot = max_zone_temp()
        .map(|temp| temp >= THERMAL_GATE_MILLI)
        .unwrap_or(false);
    let previous = THERMAL_HOT.swap(hot, Ordering::Relaxed);
    if hot != previous {
        if hot {
            log!(
                "[THERMAL] Zone reached {}C; keeping stock thermal limits",
                THERMAL_GATE_MILLI / 1000
            );
        } else {
            log!("[THERMAL] Headroom recovered; lifting thermal limiters");
        }
    }
    !hot
}

fn ppm_policy_limit(value: &str) -> usize {
    if !Path::new(PPM_STATUS_PATH).exists() {
        return 0;
    }
    let content = fs::read_to_string(PPM_STATUS_PATH).unwrap_or_default();
    let mut applied = 0;
    for line in content.lines() {
        if !PPM_LIMIT_POLICIES
            .iter()
            .any(|policy| line.contains(policy))
        {
            continue;
        }
        let Some(index) = line
            .split('[')
            .nth(1)
            .and_then(|rest| rest.split(']').next())
            .map(str::trim)
            .filter(|value| !value.is_empty())
        else {
            continue;
        };
        if write_enforced(PPM_STATUS_PATH, &format!("{} {}", index, value)) {
            applied += 1;
        }
    }
    applied
}

fn ppm_hard_limits(lite: bool) -> usize {
    if !Path::new(PPM_MAX_PATH).exists() {
        return 0;
    }
    let mut applied = 0;
    let mut cluster = 0u32;
    for index in 0..16 {
        let base = format!("/sys/devices/system/cpu/cpufreq/policy{}", index);
        let Ok(max_value) = fs::read_to_string(format!("{}/cpuinfo_max_freq", base)) else {
            continue;
        };
        let max_value = max_value.trim().to_string();
        if max_value.is_empty() {
            continue;
        }
        let min_value = fs::read_to_string(format!("{}/cpuinfo_min_freq", base))
            .map(|value| value.trim().to_string())
            .unwrap_or_default();
        if write_enforced(PPM_MAX_PATH, &format!("{} {}", cluster, max_value)) {
            applied += 1;
        }
        let target_min = if lite {
            let available = fs::read_to_string(format!("{}/scaling_available_frequencies", base))
                .unwrap_or_default();
            let mut freqs: Vec<u64> = available
                .split_whitespace()
                .filter_map(|value| value.parse::<u64>().ok())
                .collect();
            freqs.sort_unstable();
            freqs
                .get(freqs.len() / 2)
                .map(|value| value.to_string())
                .unwrap_or(min_value)
        } else {
            max_value
        };
        if write_enforced(PPM_MIN_PATH, &format!("{} {}", cluster, target_min)) {
            applied += 1;
        }
        cluster += 1;
    }
    applied
}

fn apply_zone_governors() -> usize {
    let mut applied = 0;
    for dir in COOLING_DIRS {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.starts_with("thermal_zone") {
                continue;
            }
            let policy = format!("{}/{}/policy", dir, name);
            if write_enforced(&policy, "step_wise") {
                applied += 1;
            }
        }
    }
    applied
}

pub fn enforce_gaming(lite: bool) -> usize {
    if !thermal_budget_open() {
        return 0;
    }
    let mut applied = 0;
    for path in THERMAL_SCONFIG_PATHS {
        if write_enforced(path, "0") {
            applied += 1;
        }
    }
    if Path::new(PPM_ENABLED_PATH).exists() {
        write_enforced(PPM_ENABLED_PATH, "1");
    }
    applied += ppm_policy_limit("0");
    applied += ppm_hard_limits(lite);
    for path in GPU_LIMIT_TABLE_PATHS {
        if write_enforced(path, "0") {
            applied += 1;
        }
    }
    applied += apply_zone_governors();
    applied += hold_cooling_at_minimum();
    applied
}

fn write(path: &str, value: &str, lock: bool) -> bool {
    if !Path::new(path).exists() {
        return false;
    }
    state::write_tracked(path, value, lock)
}

fn set_energy_aware(enabled: bool) -> bool {
    let value = if enabled { "1" } else { "0" };
    for path in ENERGY_AWARE_PATHS {
        if write(path, value, false) {
            return true;
        }
    }
    false
}

fn cooling_device_state(cooling_type: &str, state_value: &str) -> usize {
    let mut applied = 0;

    for dir in COOLING_DIRS {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.starts_with("cooling_device") {
                continue;
            }
            let base = format!("{}/{}", dir, name);
            let kind = fs::read_to_string(format!("{}/type", base))
                .unwrap_or_default()
                .trim()
                .to_ascii_lowercase();
            if !kind.starts_with(cooling_type) {
                continue;
            }
            if write(&format!("{}/cur_state", base), state_value, false) {
                applied += 1;
            }
        }
    }

    applied
}

fn hold_cooling_at_minimum() -> usize {
    let mut applied = 0;
    for cooling_type in COOLING_TARGETS {
        applied += cooling_device_state(cooling_type, "0");
    }
    applied
}

pub fn apply(profile: &str) {
    let performance = profile == "performance";

    if performance {
        let cooling = hold_cooling_at_minimum();
        if cooling > 0 {
            log!("[THERMAL] {} cooling device(s) held at minimum", cooling);
        }
        if set_energy_aware(false) {
            log!("[THERMAL] Energy-aware scheduler disabled");
        }
    } else if set_energy_aware(true) {
        log!("[THERMAL] Energy-aware scheduler enabled");
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn cooling_targets_are_known() {
        assert!(super::COOLING_TARGETS.contains(&"cpu-pause"));
        assert!(super::COOLING_TARGETS.contains(&"devfreq"));
    }
}