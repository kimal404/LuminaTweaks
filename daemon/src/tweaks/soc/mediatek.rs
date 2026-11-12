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

const GED_PARAM_DIRS: &[&str] = &[
    "/sys/module/ged/parameters",
    "/sys/module/met_ged/parameters",
];
const GED_BOOST_NODES: &[&str] = &["boost_gpu_enable", "ged_boost_enable", "ged_smart_boost"];
const DEVFREQ_DIR: &str = "/sys/class/devfreq";
const NEEDLES: &[&str] = &[
    "gpu",
    "mali",
    "kgsl",
    "ddr",
    "bus",
    "memlat",
    "mtk-dvfsrc",
    "dvfsrc",
];

fn ged_dir() -> Option<&'static str> {
    GED_PARAM_DIRS
        .iter()
        .copied()
        .find(|dir| Path::new(dir).is_dir())
}

fn ged_param_path(name: &str) -> Option<String> {
    let dir = ged_dir()?;
    let path = format!("{}/{}", dir, name);
    Path::new(&path).exists().then_some(path)
}

fn write_param(name: &str, value: &str) -> bool {
    match ged_param_path(name) {
        Some(path) => state::write_tracked(&path, value, false),
        None => false,
    }
}

fn read_param(name: &str) -> Option<u64> {
    let path = ged_param_path(name)?;
    fs::read_to_string(path)
        .ok()
        .and_then(|value| value.trim().parse().ok())
}

fn gpu_devfreq_node() -> Option<String> {
    let entries = fs::read_dir(DEVFREQ_DIR).ok()?;
    let mut best: Option<(String, usize)> = None;

    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
        if !(name.contains("mali") || name.contains("gpu")) {
            continue;
        }
        let base = format!("{}/{}", DEVFREQ_DIR, entry.file_name().to_string_lossy());
        let values = read_frequencies(&base);
        if values.is_empty() {
            continue;
        }
        if best
            .as_ref()
            .map(|(_, count)| values.len() > *count)
            .unwrap_or(true)
        {
            best = Some((base, values.len()));
        }
    }

    best.map(|(base, _)| base)
}

fn read_frequencies(base: &str) -> Vec<u64> {
    fs::read_to_string(format!("{}/available_frequencies", base))
        .map(|content| {
            content
                .split_whitespace()
                .filter_map(|value| value.parse().ok())
                .collect()
        })
        .unwrap_or_default()
}

fn gpu_frequency_range() -> Option<(u64, u64)> {
    let base = gpu_devfreq_node()?;
    let values = read_frequencies(&base);
    let min = values.iter().copied().min()?;
    let max = values.iter().copied().max()?;
    Some((min, max))
}

fn capped_frequency(max: u64, max_pct: u64) -> u64 {
    (max * max_pct.clamp(1, 100)) / 100
}

fn boost_values(profile: SocProfile) -> (&'static str, &'static str, &'static str) {
    match profile {
        SocProfile::Performance => ("1", "1", "1"),
        SocProfile::Lite | SocProfile::Eco => ("0", "0", "0"),
    }
}

fn apply_gpu_frequency(profile: SocProfile) -> usize {
    let Some((min, max)) = gpu_frequency_range() else {
        return 0;
    };
    let (bottom, boost, upbound) = match profile {
        SocProfile::Performance => (max, max, max),
        SocProfile::Lite => {
            let cap = capped_frequency(max, 85);
            (min.min(cap), cap, cap)
        }
        SocProfile::Eco => (min, min, min),
    };

    let mut applied = 0;
    if write_param("gpu_bottom_freq", &bottom.to_string()) {
        applied += 1;
    }
    if read_param("gpu_cust_upbound_freq").is_some() {
        write_param("gpu_cust_boost_freq", &boost.to_string());
        write_param("gpu_cust_upbound_freq", &upbound.to_string());
    }
    applied
}

fn apply_ged_flags(profile: SocProfile) -> usize {
    if ged_dir().is_none() {
        return 0;
    }

    let (boost_gpu, ged_boost, smart_boost) = boost_values(profile);
    let flags = [
        ("boost_gpu_enable", boost_gpu),
        ("ged_boost_enable", ged_boost),
        ("ged_smart_boost", smart_boost),
    ];
    let mut applied = 0;
    for (name, value) in flags {
        if GED_BOOST_NODES.contains(&name) && write_param(name, value) {
            applied += 1;
        }
    }

    let (dvfs, idle, block) = match profile {
        SocProfile::Performance | SocProfile::Lite => ("1", "0", "0"),
        SocProfile::Eco => ("0", "1", "1"),
    };
    for (name, value) in [
        ("gpu_dvfs_enable", dvfs),
        ("gpu_idle", idle),
        ("gpu_block", block),
    ] {
        if write_param(name, value) {
            applied += 1;
        }
    }
    applied
}

pub fn apply(profile: SocProfile) -> usize {
    let applied = super::apply_devfreq(profile, NEEDLES);
    let gpu = apply_gpu_frequency(profile);
    let flags = apply_ged_flags(profile);
    if gpu > 0 {
        log!("[SOC] MediaTek GPU frequency tuned for {:?}", profile);
    }
    if flags > 0 {
        log!("[SOC] MediaTek GED flags applied ({})", flags);
    }
    applied
}

pub fn optimize_storage(profile: SocProfile) -> usize {
    let governor = match profile {
        SocProfile::Performance => "performance",
        SocProfile::Lite => "simple_ondemand",
        SocProfile::Eco => "powersave",
    };
    let applied = crate::tweaks::devfreq::apply(
        &["ufshci", "ufs"],
        governor,
        profile == SocProfile::Performance,
    );
    if applied > 0 {
        log!("[SOC] MediaTek storage governor '{}'", governor);
    }
    applied
}

#[cfg(test)]
mod tests {
    use super::{boost_values, capped_frequency, SocProfile};

    #[test]
    fn performance_enables_all_ged_boosts() {
        assert_eq!(boost_values(SocProfile::Performance), ("1", "1", "1"));
    }

    #[test]
    fn lite_and_eco_disable_ged_boosts() {
        assert_eq!(boost_values(SocProfile::Lite), ("0", "0", "0"));
        assert_eq!(boost_values(SocProfile::Eco), ("0", "0", "0"));
    }

    #[test]
    fn caps_gpu_frequency_to_percentage() {
        assert_eq!(capped_frequency(1300000000, 85), 1105000000);
        assert_eq!(capped_frequency(260000000, 100), 260000000);
    }
}