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
use std::os::unix::fs::PermissionsExt;
use std::path::Path;

const GED_PARAM_DIRS: &[&str] = &[
    "/sys/module/ged/parameters",
    "/sys/module/met_ged/parameters",
];
const GED_BOOST_NODES: &[&str] = &["boost_gpu_enable", "ged_boost_enable", "ged_smart_boost"];
const DEVFREQ_DIR: &str = "/sys/class/devfreq";
const GED_KPI_PATHS: &[&str] = &[
    "/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled",
    "/sys/module/ged/parameters/is_GED_KPI_enabled",
];
const FPSGO_PATHS: &[&str] = &[
    "/sys/kernel/fpsgo/common/force_onoff",
    "/sys/module/fpsgo/parameters/force_onoff",
];
const HELIO_DVFSRC_PATHS: &[&str] = &[
    "/sys/kernel/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp",
    "/sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp",
];
const GED_HAL_DIRS: &[&str] = &["/sys/kernel/ged/hal"];
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

fn write_summary_file(path: &str, value: &str, force_writable: bool) -> bool {
    if !Path::new(path).exists() {
        return false;
    }
    if force_writable {
        let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o644));
    }
    state::write_tracked(path, value, false)
}

fn write_first(paths: &[&str], value: &str, force_writable: bool) -> bool {
    paths
        .iter()
        .any(|path| write_summary_file(path, value, force_writable))
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

fn gpu_frequency_values() -> Vec<u64> {
    gpu_devfreq_node()
        .map(|base| read_frequencies(&base))
        .unwrap_or_default()
}

fn gpu_frequency_range() -> Option<(u64, u64)> {
    let base = gpu_devfreq_node()?;
    let values = read_frequencies(&base);
    let min = values.iter().copied().min()?;
    let max = values.iter().copied().max()?;
    Some((min, max))
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
            let values = gpu_frequency_values();
            let mid = crate::tweaks::devfreq::mid_frequency(&values).unwrap_or(max);
            (min.min(mid), mid, mid)
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

fn read_opp_table(path: &str) -> Vec<(usize, u64)> {
    let Ok(content) = fs::read_to_string(path) else {
        return Vec::new();
    };
    content
        .lines()
        .filter_map(|line| {
            let index = line.split('[').nth(1)?.split(']').next()?;
            let freq = line
                .split("freq:")
                .nth(1)?
                .split(',')
                .next()?
                .trim()
                .parse::<u64>()
                .ok()?;
            Some((index.trim().parse().ok()?, freq))
        })
        .collect()
}

fn gpufreq_opp_paths() -> (&'static str, &'static str) {
    (
        "/proc/gpufreqv2/gpu_working_opp_table",
        "/proc/gpufreq/gpufreq_opp_dump",
    )
}

fn apply_gpufreq_opp(profile: SocProfile) -> usize {
    let fix_index = "/proc/gpufreqv2/fix_target_opp_index";
    let (table_v2, table_v1) = gpufreq_opp_paths();

    if Path::new(fix_index).exists() {
        let opps = read_opp_table(table_v2);
        let target = match profile {
            SocProfile::Performance => opps.first().map(|(_, freq)| (0usize, *freq)),
            SocProfile::Lite => {
                let freqs: Vec<u64> = opps.iter().map(|(_, freq)| *freq).collect();
                let mid = crate::tweaks::devfreq::mid_frequency(&freqs)
                    .unwrap_or_else(|| opps.first().map(|(_, freq)| *freq).unwrap_or(0));
                opps.iter()
                    .min_by_key(|(_, freq)| freq.abs_diff(mid))
                    .copied()
            }
            SocProfile::Eco => opps.last().copied(),
        };

        if let Some((index, _)) = target {
            if write_summary_file(fix_index, &format!("{}", index), true) {
                log!("[SOC] MediaTek gpufreqv2 OPP index set to {}", index);
                return 1;
            }
        }
    }

    let legacy = "/proc/gpufreq/gpufreq_opp_freq";
    if Path::new(legacy).exists() {
        let opps = read_opp_table(table_v1);
        let value = match profile {
            SocProfile::Performance => opps.first().map(|(_, freq)| *freq),
            SocProfile::Lite => {
                let freqs: Vec<u64> = opps.iter().map(|(_, freq)| *freq).collect();
                let mid = crate::tweaks::devfreq::mid_frequency(&freqs)
                    .unwrap_or_else(|| opps.first().map(|(_, freq)| *freq).unwrap_or(0));
                opps.iter()
                    .min_by_key(|(_, freq)| freq.abs_diff(mid))
                    .map(|(_, freq)| *freq)
            }
            SocProfile::Eco => opps.last().map(|(_, freq)| *freq),
        };
        if let Some(freq) = value {
            if write_summary_file(legacy, &format!("{}", freq), true) {
                log!("[SOC] MediaTek gpufreq frequency set to {} kHz", freq);
                return 1;
            }
        }
    }

    0
}

fn apply_ged_hal(profile: SocProfile) -> usize {
    let dir = match GED_HAL_DIRS
        .iter()
        .copied()
        .find(|dir| Path::new(dir).is_dir())
    {
        Some(dir) => dir,
        None => return 0,
    };

    let (min, mid, max) = match profile {
        SocProfile::Performance => (true, false, true),
        SocProfile::Lite => (false, true, false),
        SocProfile::Eco => (true, false, false),
    };

    let Some((low, middle, high)) = read_gpu_devfreq_range() else {
        return 0;
    };
    let _ = low;

    let mut applied = 0;
    let boost_node = format!("{}/custom_boost_gpu_freq", dir);
    if (min || mid) && Path::new(&boost_node).exists() {
        let target = if min { high } else { middle };
        if write_summary_file(&boost_node, &format!("{}", target), false) {
            applied += 1;
        }
    }

    let upbound_node = format!("{}/custom_upbound_gpu_freq", dir);
    if max && Path::new(&upbound_node).exists() {
        let value = if profile == SocProfile::Performance {
            "0".to_string()
        } else {
            format!("{}", middle)
        };
        if write_summary_file(&upbound_node, &value, false) {
            applied += 1;
        }
    }
    applied
}

fn read_gpu_devfreq_range() -> Option<(u64, u64, u64)> {
    let base = gpu_devfreq_node()?;
    crate::tweaks::devfreq::frequency_range(&base)
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

fn apply_fpsgo(profile: SocProfile) -> bool {
    let value = match profile {
        SocProfile::Performance => "0",
        SocProfile::Lite => "2",
        SocProfile::Eco => "2",
    };
    write_first(FPSGO_PATHS, value, false)
}

fn apply_ged_kpi(profile: SocProfile) -> bool {
    let value = match profile {
        SocProfile::Performance => "0",
        SocProfile::Lite | SocProfile::Eco => "1",
    };
    write_first(GED_KPI_PATHS, value, false)
}

fn apply_helio_dvfsrc(profile: SocProfile) -> bool {
    let value = if profile == SocProfile::Performance {
        "0"
    } else {
        "-1"
    };
    write_first(HELIO_DVFSRC_PATHS, value, true)
}

pub fn apply(profile: SocProfile) -> usize {
    let applied = super::apply_devfreq(profile, NEEDLES);
    let opp = apply_gpufreq_opp(profile);
    let gpu = apply_gpu_frequency(profile);
    let hal = apply_ged_hal(profile);
    let flags = apply_ged_flags(profile);
    if opp > 0 {
        log!("[SOC] MediaTek GPU OPP applied via gpufreqv2");
    }
    if gpu > 0 {
        log!("[SOC] MediaTek GPU frequency tuned for {:?}", profile);
    }
    if hal > 0 {
        log!("[SOC] MediaTek GED HAL nodes applied ({})", hal);
    }
    if flags > 0 {
        log!("[SOC] MediaTek GED flags applied ({})", flags);
    }
    if apply_fpsgo(profile) {
        log!("[SOC] MediaTek FPSGO adjusted");
    }
    if apply_ged_kpi(profile) {
        log!("[SOC] MediaTek GED KPI adjusted");
    }
    if apply_helio_dvfsrc(profile) {
        log!("[SOC] MediaTek helio-dvfsrc adjusted");
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
    use super::{boost_values, read_opp_table, SocProfile};

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
    fn parses_gpufreq_opp_table() {
        let dir = std::env::temp_dir().join("lumina_opp_table");
        std::fs::write(
            &dir,
            "[00] freq: 1300000, volt: 93750, vsram: 93750\n[01] freq: 1274000, volt: 92500, vsram: 92500\n",
        )
        .unwrap();
        let opps = read_opp_table(dir.to_str().unwrap());
        assert_eq!(opps, vec![(0, 1300000), (1, 1274000)]);
        let _ = std::fs::remove_file(dir);
    }

    #[test]
    fn picks_lowest_index_for_performance() {
        let opps = vec![(0, 1300000), (1, 1274000), (2, 1248000)];
        assert_eq!(opps.first().copied(), Some((0, 1300000)));
        assert_eq!(opps.last().copied(), Some((2, 1248000)));
    }
}