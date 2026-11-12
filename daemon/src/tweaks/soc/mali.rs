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

use super::SocProfile;
use crate::tweaks::state;
use std::fs;
use std::path::Path;

const PLATFORM_DIRS: &[&str] = &["/sys/devices/platform", "/sys/devices/platform/13000000.mali"];
const POWER_POLICY: &str = "power_policy";
const AVAILABLE_FREQUENCIES: &[&str] = &["available_frequencies", "devfreq/available_frequencies"];

fn mali_dir() -> Option<String> {
    for dir in PLATFORM_DIRS {
        if Path::new(dir).join(POWER_POLICY).exists() {
            return Some((*dir).to_string());
        }
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
            if !name.contains("mali") {
                continue;
            }
            let base = entry.path().to_string_lossy().into_owned();
            if Path::new(&base).join(POWER_POLICY).exists() {
                return Some(base);
            }
        }
    }
    None
}

fn frequencies(dir: &str) -> Vec<u64> {
    AVAILABLE_FREQUENCIES.iter().find_map(|name| {
        let path = format!("{}/{}", dir, name);
        if !Path::new(&path).exists() {
            return None;
        }
        fs::read_to_string(path).ok().map(|content| {
            content
                .split_whitespace()
                .filter_map(|value| value.parse().ok())
                .collect()
        })
    }).unwrap_or_default()
}

fn power_policy_value(available: &str, performance: bool) -> &'static str {
    if performance {
        "always_on"
    } else if available.contains("adaptive") {
        "adaptive"
    } else {
        "coarse_demand"
    }
}

pub fn apply_power_policy(profile: SocProfile) -> usize {
    let Some(dir) = mali_dir() else {
        return 0;
    };
    let path = format!("{}/{}", dir, POWER_POLICY);
    let available = fs::read_to_string(&path).unwrap_or_default();
    let value = power_policy_value(&available, profile == SocProfile::Performance);

    let mut applied = 0;
    if state::write_tracked(&path, value, false) {
        applied += 1;
    }

    let values = frequencies(&dir);
    if !values.is_empty() {
        let min = values.iter().copied().min().unwrap_or_default();
        let max = values.iter().copied().max().unwrap_or_default();
        let (target_min, target_max) = match profile {
            SocProfile::Performance => (max, max),
            SocProfile::Lite => (
                min,
                crate::tweaks::devfreq::mid_frequency(&values).unwrap_or(max),
            ),
            SocProfile::Eco => (min, min),
        };
        for (name, target) in [
            ("scaling_min_freq", target_min),
            ("scaling_max_freq", target_max),
        ] {
            let freq_path = format!("{}/{}", dir, name);
            if state::write_tracked(&freq_path, &target.to_string(), false) {
                applied += 1;
            }
        }
    }

    applied
}

#[cfg(test)]
mod tests {
    use super::power_policy_value;

    #[test]
    fn performance_uses_always_on() {
        assert_eq!(power_policy_value("coarse_demand adaptive", true), "always_on");
    }

    #[test]
    fn normal_prefers_adaptive_when_available() {
        assert_eq!(power_policy_value("coarse_demand adaptive", false), "adaptive");
        assert_eq!(power_policy_value("coarse_demand", false), "coarse_demand");
    }
}