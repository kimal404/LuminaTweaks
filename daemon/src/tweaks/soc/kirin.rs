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

const GPU_DIRS: &[&str] = &[
    "/sys/class/devfreq/gpufreq",
    "/sys/devices/platform/gpufreq",
    "/sys/kernel/gpu",
];
const DEVFREQ_DIR: &str = "/sys/class/devfreq";
const NEEDLES: &[&str] = &["gpu", "mali", "kirin", "ddr", "bus"];

fn gpu_devfreq() -> Option<String> {
    for dir in GPU_DIRS {
        if Path::new(dir).join("available_frequencies").exists() {
            return Some((*dir).to_string());
        }
    }

    let entries = fs::read_dir(DEVFREQ_DIR).ok()?;
    entries.flatten().find_map(|entry| {
        let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
        if !name.contains("gpu") && !name.contains("mali") {
            return None;
        }
        let base = format!("{}/{}", DEVFREQ_DIR, entry.file_name().to_string_lossy());
        Path::new(&base)
            .join("available_frequencies")
            .exists()
            .then_some(base)
    })
}

fn frequencies(base: &str) -> Vec<u64> {
    fs::read_to_string(format!("{}/available_frequencies", base))
        .map(|content| {
            content
                .split_whitespace()
                .filter_map(|value| value.parse().ok())
                .collect()
        })
        .unwrap_or_default()
}

fn apply_gpu(profile: SocProfile) -> usize {
    let Some(base) = gpu_devfreq() else {
        return 0;
    };
    let values = frequencies(&base);
    let (Some(min), Some(max)) = (values.iter().copied().min(), values.iter().copied().max())
    else {
        return 0;
    };

    let (target_min, target_max) = match profile {
        SocProfile::Performance => (max, max),
        SocProfile::Lite => (
            min,
            devfreq::mid_frequency(&values).unwrap_or(max),
        ),
        SocProfile::Eco => (min, min),
    };

    let mut applied = 0;
    for (name, target) in [("min_freq", target_min), ("max_freq", target_max)] {
        let path = format!("{}/{}", base, name);
        if state::write_tracked(&path, &target.to_string(), false) {
            applied += 1;
        }
    }
    applied
}

pub fn apply(profile: SocProfile) -> usize {
    let applied = super::apply_devfreq(profile, NEEDLES);
    let gpu = apply_gpu(profile);
    if gpu > 0 {
        log!("[SOC] Kirin GPU tuned for {:?}", profile);
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
    use super::NEEDLES;

    #[test]
    fn covers_kirin_bus() {
        assert!(NEEDLES.contains(&"kirin"));
    }
}