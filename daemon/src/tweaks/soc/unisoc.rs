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

const NEEDLES: &[&str] = &[
    "gpu",
    "mali",
    "ddr",
    "bus",
    "memlat",
    "sprd",
    "unisoc",
    "scene",
    "sprd-gpu",
    "sprd-mem",
];
const SPRD_GPU_PARAMS: &[&str] = &[
    "/sys/module/sprd_gpu/parameters",
    "/sys/module/gpu/parameters",
    "/sys/module/ged/parameters",
];

fn sprd_param_dir() -> Option<&'static str> {
    SPRD_GPU_PARAMS
        .iter()
        .copied()
        .find(|dir| Path::new(dir).is_dir())
}

fn sprd_param_path(name: &str) -> Option<String> {
    let dir = sprd_param_dir()?;
    let path = format!("{}/{}", dir, name);
    Path::new(&path).exists().then_some(path)
}

fn write_sprd_param(name: &str, value: &str) -> bool {
    match sprd_param_path(name) {
        Some(path) => state::write_tracked(&path, value, false),
        None => false,
    }
}

fn apply_vendor(profile: SocProfile) -> usize {
    let mut applied = 0;

    let (wanted, value) = match profile {
        SocProfile::Performance => ("boost", "1"),
        SocProfile::Lite | SocProfile::Eco => ("idle", "1"),
    };
    if write_sprd_param(wanted, value) {
        applied += 1;
    }

    let Ok(entries) = fs::read_dir("/sys/class/devfreq") else {
        return applied;
    };

    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
        if !(name.contains("sprd-gpu") || name.contains("sprd-mem") || name.contains("sprd")) {
            continue;
        }
        let base = format!("/sys/class/devfreq/{}", entry.file_name().to_string_lossy());
        let values: Vec<u64> =
            fs::read_to_string(format!("{}/available_frequencies", base))
                .ok()
                .map(|content| {
                    content
                        .split_whitespace()
                        .filter_map(|value| value.parse().ok())
                        .collect()
                })
                .unwrap_or_default();
        let (Some(min), Some(max)) = (values.iter().copied().min(), values.iter().copied().max())
        else {
            continue;
        };
        let governor = match profile {
            SocProfile::Performance => "performance",
            SocProfile::Lite => "simple_ondemand",
            SocProfile::Eco => "powersave",
        };
        let target = match profile {
            SocProfile::Performance => max,
            SocProfile::Lite => (max * 85) / 100,
            SocProfile::Eco => min,
        };
        if !Path::new(&format!("{}/governor", base)).exists() {
            continue;
        }
        state::write_tracked(&format!("{}/governor", base), governor, false);
        state::write_tracked(&format!("{}/min_freq", base), &target.to_string(), false);
        state::write_tracked(&format!("{}/max_freq", base), &target.to_string(), true);
        applied += 1;
    }

    applied
}

pub fn apply(profile: SocProfile) -> usize {
    let applied = super::apply_devfreq(profile, NEEDLES);
    let vendor = apply_vendor(profile);
    if vendor > 0 {
        log!("[SOC] Unisoc vendor nodes applied ({})", vendor);
    }
    applied
}

pub fn optimize_storage(profile: SocProfile) -> usize {
    let governor = match profile {
        SocProfile::Performance => "performance",
        SocProfile::Lite => "simple_ondemand",
        SocProfile::Eco => "powersave",
    };
    crate::tweaks::devfreq::apply(&["ufs", "mmc"], governor, profile == SocProfile::Performance)
}

#[cfg(test)]
mod tests {
    use super::SocProfile;

    #[test]
    fn uses_unisoc_specific_devfreq_needles() {
        assert!(super::NEEDLES.contains(&"sprd"));
        assert!(super::NEEDLES.contains(&"unisoc"));
    }

    #[test]
    fn profiles_choose_expected_param() {
        let pick = |profile: SocProfile| match profile {
            SocProfile::Performance => "boost",
            SocProfile::Lite | SocProfile::Eco => "idle",
        };
        assert_eq!(pick(SocProfile::Performance), "boost");
        assert_eq!(pick(SocProfile::Lite), "idle");
    }
}