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
use crate::tweaks::{devfreq, state};
use std::fs;
use std::path::Path;

const GPU_DIRS: &[&str] = &["/sys/kernel/tegra_gpu", "/sys/devices/platform/tegra_gpu"];
const CAP_NODES: &[&str] = &["gpu_cap_rate", "gpu_floor_rate"];
const NEEDLES: &[&str] = &["gpu", "tegra", "mali", "emc", "ddr", "bus"];

fn gpu_dir() -> Option<&'static str> {
    GPU_DIRS
        .iter()
        .copied()
        .find(|dir| Path::new(dir).is_dir())
}

fn read_frequencies(dir: &str) -> Vec<u64> {
    fs::read_to_string(format!("{}/available_frequencies", dir))
        .map(|content| {
            content
                .split_whitespace()
                .filter_map(|value| value.parse().ok())
                .collect()
        })
        .unwrap_or_default()
}

fn target_rates(profile: SocProfile, min: u64, mid: u64, max: u64) -> (u64, u64) {
    match profile {
        SocProfile::Performance => (max, max),
        SocProfile::Lite => (min, mid),
        SocProfile::Eco => (min, min),
    }
}

fn apply_gpu(profile: SocProfile) -> usize {
    let Some(dir) = gpu_dir() else {
        return 0;
    };
    let values = read_frequencies(dir);
    let (Some(min), Some(max)) = (values.iter().copied().min(), values.iter().copied().max())
    else {
        return 0;
    };

    let mid = crate::tweaks::devfreq::mid_frequency(&values).unwrap_or(max);
    let (floor, cap) = target_rates(profile, min, mid, max);
    let mut applied = 0;
    for (name, target) in [("gpu_floor_rate", floor), ("gpu_cap_rate", cap)] {
        if !CAP_NODES.contains(&name) {
            continue;
        }
        let path = format!("{}/{}", dir, name);
        if Path::new(&path).exists() && state::write_tracked(&path, &target.to_string(), false) {
            applied += 1;
        }
    }
    applied
}

pub fn apply(profile: SocProfile) -> usize {
    let applied = super::apply_devfreq(profile, NEEDLES);
    let gpu = apply_gpu(profile);
    if gpu > 0 {
        log!("[SOC] Tegra GPU rates tuned for {:?}", profile);
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
    use super::{target_rates, SocProfile};

    #[test]
    fn performance_pins_both_rates_to_max() {
        assert_eq!(
            target_rates(SocProfile::Performance, 100, 550, 1000),
            (1000, 1000)
        );
    }

    #[test]
    fn eco_pins_both_rates_to_min() {
        assert_eq!(target_rates(SocProfile::Eco, 100, 550, 1000), (100, 100));
    }

    #[test]
    fn lite_uses_median_rate() {
        assert_eq!(target_rates(SocProfile::Lite, 100, 550, 1000), (100, 550));
    }
}