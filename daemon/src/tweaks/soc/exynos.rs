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

use super::{SocProfile, mali};
use crate::tweaks::{devfreq, state};
use std::fs;
use std::path::Path;

const GPU_DIRS: &[&str] = &["/sys/kernel/gpu", "/sys/devices/platform/gpu"];
const CLOCK_CANDIDATES: &[&str] = &[
    "gpu_max_clock",
    "gpu_min_clock",
    "scaling_max_freq",
    "scaling_min_freq",
];
const FREQ_TABLES: &[&str] = &[
    "gpu_available_frequencies",
    "gpu_freq_table",
    "available_frequencies",
];
const POWER_POLICY: &str = "power_policy";
const NEEDLES: &[&str] = &["gpu", "mali", "devfreq_mif", "mif", "ddr", "bus"];

fn gpu_dir() -> Option<&'static str> {
    GPU_DIRS
        .iter()
        .copied()
        .find(|dir| Path::new(dir).is_dir())
}

fn freq_table(dir: &str) -> Option<String> {
    FREQ_TABLES.iter().find_map(|name| {
        let path = format!("{}/{}", dir, name);
        Path::new(&path).exists().then_some(path)
    })
}

fn read_frequencies(path: &str) -> Vec<u64> {
    fs::read_to_string(path)
        .map(|content| {
            content
                .split_whitespace()
                .filter_map(|value| value.parse().ok())
                .collect()
        })
        .unwrap_or_default()
}

fn clock_value(dir: &str, name: &str) -> Option<String> {
    let path = format!("{}/{}", dir, name);
    Path::new(&path).exists().then_some(path)
}

fn target_frequencies(profile: SocProfile) -> Option<(u64, u64)> {
    let dir = gpu_dir()?;
    let table = freq_table(dir)?;
    let values = read_frequencies(&table);
    let min = values.iter().copied().min()?;
    let max = values.iter().copied().max()?;

    Some(match profile {
        SocProfile::Performance => (max, max),
        SocProfile::Lite => (
            min,
            devfreq::mid_frequency(&values).unwrap_or(max),
        ),
        SocProfile::Eco => (min, min),
    })
}

fn apply_gpu(profile: SocProfile) -> usize {
    let Some(dir) = gpu_dir() else {
        return 0;
    };
    let Some((min_freq, max_freq)) = target_frequencies(profile) else {
        return 0;
    };

    let mut applied = 0;
    for name in ["gpu_max_clock", "scaling_max_freq"] {
        if let Some(path) = clock_value(dir, name) {
            if state::write_tracked(&path, &max_freq.to_string(), false) {
                applied += 1;
            }
        }
    }
    for name in ["gpu_min_clock", "scaling_min_freq"] {
        if let Some(path) = clock_value(dir, name) {
            if state::write_tracked(&path, &min_freq.to_string(), false) {
                applied += 1;
            }
        }
    }

    if let Some(path) = clock_value(dir, POWER_POLICY) {
        let value = match profile {
            SocProfile::Performance => "always_on",
            SocProfile::Lite | SocProfile::Eco => "coarse_demand",
        };
        state::write_tracked(&path, value, false);
        applied += 1;
    }

    applied
}

pub fn apply(profile: SocProfile) -> usize {
    let applied = super::apply_devfreq(profile, NEEDLES);
    let gpu = apply_gpu(profile);
    let mali = mali::apply_power_policy(profile);
    if gpu > 0 {
        log!("[SOC] Exynos GPU clocks tuned for {:?}", profile);
    }
    if mali > 0 {
        log!("[SOC] Exynos Mali power policy applied");
    }
    applied
}

pub fn optimize_storage(profile: SocProfile) -> usize {
    let governor = match profile {
        SocProfile::Performance => "performance",
        SocProfile::Lite => "simple_ondemand",
        SocProfile::Eco => "powersave",
    };
    devfreq::apply(&["ufs", "mmc"], governor, profile == SocProfile::Performance)
}

#[cfg(test)]
mod tests {
    use super::{CLOCK_CANDIDATES, FREQ_TABLES};

    #[test]
    fn has_clock_candidates() {
        assert!(CLOCK_CANDIDATES.contains(&"gpu_max_clock"));
        assert!(CLOCK_CANDIDATES.contains(&"scaling_max_freq"));
    }

    #[test]
    fn has_frequency_tables() {
        assert!(FREQ_TABLES.contains(&"gpu_available_frequencies"));
        assert!(FREQ_TABLES.contains(&"gpu_freq_table"));
    }
}