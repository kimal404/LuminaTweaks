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
use std::fs;
use std::path::Path;

const ENERGY_AWARE_PATHS: &[&str] = &[
    "/sys/devices/system/cpu/sched_energy_aware",
    "/proc/sys/kernel/sched_energy_aware",
];
const COOLING_DIRS: &[&str] = &["/sys/class/thermal", "/sys/devices/virtual/thermal"];
const COOLING_TARGETS: &[&str] = &["cpu-pause", "devfreq", "apu_cooler", "gpu"];

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