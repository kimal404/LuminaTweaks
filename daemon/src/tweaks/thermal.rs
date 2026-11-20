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

use crate::tweaks::soc::{self, Vendor};
use crate::tweaks::state;
use std::fs;
use std::path::Path;
use std::sync::Mutex;
use std::time::Duration;

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

const SCONFIG_NORMAL_VALUE: &str = "0";
const SCONFIG_VALIDATION_DELAY: Duration = Duration::from_millis(120);

enum SconfigMode {
    Unknown,
    Valid(String),
    Unsupported,
}

static SCONFIG_MODE: Mutex<SconfigMode> = Mutex::new(SconfigMode::Unknown);

pub(crate) fn write_enforced(path: &str, value: &str) -> bool {
    if !Path::new(path).exists() {
        return false;
    }
    state::write_tracked(path, value, false)
}

fn sconfig_path() -> Option<&'static str> {
    THERMAL_SCONFIG_PATHS
        .iter()
        .copied()
        .find(|path| Path::new(path).exists())
}

fn read_sconfig(path: &str) -> Option<String> {
    fs::read_to_string(path)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
}

fn sconfig_candidates() -> [&'static str; 2] {
    if soc::vendor() == Vendor::Snapdragon {
        ["10", "6"]
    } else {
        ["6", "10"]
    }
}

fn select_sconfig() -> Option<String> {
    let path = sconfig_path()?;
    let original = read_sconfig(path)?;
    for candidate in sconfig_candidates() {
        if !write_enforced(path, candidate) {
            continue;
        }
        std::thread::sleep(SCONFIG_VALIDATION_DELAY);
        if read_sconfig(path).as_deref() == Some(candidate) {
            log!("[THERMAL] sconfig {} accepted by kernel", candidate);
            return Some(candidate.to_string());
        }
    }
    write_enforced(path, &original);
    log!("[THERMAL] sconfig rejected every candidate; keeping {}", original);
    None
}

fn ensure_sconfig() -> bool {
    let Some(path) = sconfig_path() else {
        return false;
    };
    let cached = {
        let guard = SCONFIG_MODE
            .lock()
            .unwrap_or_else(|error| error.into_inner());
        match &*guard {
            SconfigMode::Valid(value) => Some(value.clone()),
            SconfigMode::Unsupported => return false,
            SconfigMode::Unknown => None,
        }
    };
    if let Some(value) = cached {
        if read_sconfig(path).as_deref() == Some(value.as_str()) {
            return true;
        }
        return write_enforced(path, &value);
    }

    let selected = select_sconfig();
    let mut guard = SCONFIG_MODE
        .lock()
        .unwrap_or_else(|error| error.into_inner());
    match selected {
        Some(value) => {
            *guard = SconfigMode::Valid(value);
            true
        }
        None => {
            *guard = SconfigMode::Unsupported;
            false
        }
    }
}

pub fn reset_sconfig() {
    if let Some(path) = sconfig_path() {
        crate::tweaks::engine::write_sysfs(path, SCONFIG_NORMAL_VALUE, false);
    }
    let mut guard = SCONFIG_MODE
        .lock()
        .unwrap_or_else(|error| error.into_inner());
    *guard = SconfigMode::Unknown;
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
    let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") else {
        return 0;
    };
    let mut applied = 0;
    let mut cluster = 0u32;
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if !name.starts_with("policy") {
            continue;
        }
        let base = format!("/sys/devices/system/cpu/cpufreq/{}", name);
        let available: Vec<u64> =
            fs::read_to_string(format!("{}/scaling_available_frequencies", base))
                .unwrap_or_default()
                .split_whitespace()
                .filter_map(|value| value.parse::<u64>().ok())
                .collect();
        let max_value = fs::read_to_string(format!("{}/cpuinfo_max_freq", base))
            .ok()
            .and_then(|value| value.trim().parse::<u64>().ok())
            .or_else(|| available.iter().copied().max());
        let min_value = fs::read_to_string(format!("{}/cpuinfo_min_freq", base))
            .ok()
            .and_then(|value| value.trim().parse::<u64>().ok())
            .or_else(|| available.iter().copied().min());
        let (Some(max_value), Some(min_value)) = (max_value, min_value) else {
            continue;
        };
        if write_enforced(PPM_MAX_PATH, &format!("{} {}", cluster, max_value)) {
            applied += 1;
        }
        let target_min = if lite { min_value } else { max_value };
        if write_enforced(PPM_MIN_PATH, &format!("{} {}", cluster, target_min)) {
            applied += 1;
        }
        cluster += 1;
    }
    applied
}

fn release_cpu_limits() -> usize {
    let path = "/sys/class/thermal/thermal_message/cpu_limits";
    if !Path::new(path).exists() {
        return 0;
    }
    let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") else {
        return 0;
    };
    let mut applied = 0;
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if !name.starts_with("policy") {
            continue;
        }
        let base = format!("/sys/devices/system/cpu/cpufreq/{}", name);
        let cpu = fs::read_to_string(format!("{}/affected_cpus", base))
            .ok()
            .and_then(|value| value.split_whitespace().next().map(str::to_string))
            .or_else(|| name.strip_prefix("policy").map(str::to_string));
        let Some(cpu) = cpu else {
            continue;
        };
        let max_value = fs::read_to_string(format!("{}/cpuinfo_max_freq", base))
            .ok()
            .map(|value| value.trim().to_string())
            .filter(|value| !value.is_empty())
            .or_else(|| {
                fs::read_to_string(format!("{}/scaling_available_frequencies", base))
                    .ok()
                    .and_then(|value| {
                        value
                            .split_whitespace()
                            .filter_map(|freq| freq.parse::<u64>().ok())
                            .max()
                            .map(|freq| freq.to_string())
                    })
            });
        let Some(max_value) = max_value else {
            continue;
        };
        if write_enforced(path, &format!("{} {}", cpu, max_value)) {
            applied += 1;
        }
    }
    applied
}

fn apply_zone_policy(performance: bool) -> usize {
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
            let base = format!("{}/{}/", dir, name);
            if performance {
                if write_enforced(&format!("{}mode", base), "disabled") {
                    applied += 1;
                }
                if write_enforced(&format!("{}policy", base), "userspace") {
                    applied += 1;
                }
            } else if write_enforced(&format!("{}policy", base), "step_wise") {
                applied += 1;
            }
        }
    }
    applied
}

pub fn apply_performance_lockers(lite: bool) -> usize {
    let mut applied = 0;
    if !lite && ensure_sconfig() {
        applied += 1;
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
    applied += release_cpu_limits();
    applied += apply_zone_policy(!lite);
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