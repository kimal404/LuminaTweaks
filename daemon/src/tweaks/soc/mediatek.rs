/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use super::SocProfile;
use std::fs;
use std::path::Path;

const FPSGO_PATH: &str = "/sys/kernel/fpsgo/common/force_onoff";
const FPSGO_FBT_DIR: &str = "/sys/kernel/fpsgo/fbt";
const GED_HAL_DIR: &str = "/sys/kernel/ged/hal";
const GED_HAL_SAFE_NODES: &[&str] = &["gx_game_mode", "gx_force_cpu_boost", "disp_dejitter"];
const CCI_MODE_PATH: &str = "/proc/cpufreq/cpufreq_cci_mode";
const POWER_MODE_PATH: &str = "/proc/cpufreq/cpufreq_power_mode";
const DRAMBOOST_PATH: &str = "/sys/devices/platform/boot_dramboost/dramboost/dramboost";
const EAS_PATH: &str = "/sys/devices/system/cpu/eas/enable";
const GED_KPI_PATH: &str = "/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled";
const LEGACY_GPUFREQ_PATH: &str = "/proc/gpufreq/gpufreq_opp_freq";
const FIX_OPP_PATH: &str = "/proc/gpufreqv2/fix_target_opp_index";
const GPUFREQ_V2_TABLE: &str = "/proc/gpufreqv2/gpu_working_opp_table";
const GPUFREQ_V1_TABLE: &str = "/proc/gpufreq/gpufreq_opp_dump";
const POWER_LIMITED_PATH: &str = "/proc/gpufreq/gpufreq_power_limited";
const POWER_LIMITED_V2_PATHS: &[&str] = &[
    "/proc/gpufreqv2/power_limited",
    "/proc/gpufreqv2/gpufreq_power_limited",
];
const TARGET_FPS_PATHS: &[&str] = &[
    "/sys/kernel/fpsgo/fstb/fstb_target_fps_hi",
    "/sys/kernel/fpsgo/fstb/fstb_target_fps_lo",
    "/sys/kernel/fpsgo/fstb/gx_dfps",
    "/sys/kernel/fpsgo/common/gx_dfps",
    "/sys/kernel/ged/hal/gx_dfps",
];
const BATOC_PATH: &str = "/proc/mtk_batoc_throttling/battery_oc_protect_stop";
const HELIO_FORCE_PATH: &str = "/sys/kernel/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp";
const EARA_THERMAL_PATH: &str = "/sys/kernel/eara_thermal/enable";
const PPM_STATUS_PATH: &str = "/proc/ppm/policy_status";
const POWER_LIMIT_KEYS: &[&str] = &[
    "ignore_batt_oc",
    "ignore_batt_percent",
    "ignore_low_batt",
    "ignore_thermal_protect",
    "ignore_pbm_limited",
];
const PPM_LIMIT_POLICIES: &[&str] = &["PWR_THRO", "THERMAL"];

fn parse_opp_table(path: &str) -> Vec<(usize, u64)> {
    let Ok(content) = fs::read_to_string(path) else {
        return Vec::new();
    };
    content
        .lines()
        .filter_map(|line| {
            let index = line
                .split('[')
                .nth(1)?
                .split(']')
                .next()?
                .trim()
                .parse::<usize>()
                .ok()?;
            let freq = line
                .split("freq:")
                .nth(1)?
                .split(',')
                .next()?
                .trim()
                .parse::<u64>()
                .ok()?;
            Some((index, freq))
        })
        .collect()
}

fn min_opp_index(table: &str) -> Option<usize> {
    parse_opp_table(table).last().map(|(index, _)| *index)
}

fn max_opp_index(table: &str) -> Option<usize> {
    parse_opp_table(table).first().map(|(index, _)| *index)
}

#[cfg(test)]
fn first_opp_freq(table: &str) -> Option<u64> {
    parse_opp_table(table).first().map(|(_, freq)| *freq)
}

fn last_opp_freq(table: &str) -> Option<u64> {
    parse_opp_table(table).last().map(|(_, freq)| *freq)
}

fn ppm_policies(enabled: bool) -> usize {
    let Ok(content) = fs::read_to_string(PPM_STATUS_PATH) else {
        return 0;
    };
    let value = if enabled { "1" } else { "0" };
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
        if super::write_node(PPM_STATUS_PATH, &format!("{} {}", index, value)) {
            applied += 1;
        }
    }
    applied
}

fn ged_hal_node(name: &str) -> Option<String> {
    if !GED_HAL_SAFE_NODES.contains(&name) {
        return None;
    }
    let path = format!("{}/{}", GED_HAL_DIR, name);
    Path::new(&path).exists().then_some(path)
}

fn power_limited(value: &str) -> usize {
    let mut applied = 0;
    for key in POWER_LIMIT_KEYS {
        if super::apply_node(POWER_LIMITED_PATH, &format!("{} {}", key, value)) {
            applied += 1;
        }
    }
    applied
}

fn dvfsrc_platform_nodes() -> Vec<String> {
    super::platform_matching(".dvfsrc")
        .into_iter()
        .map(|dir| format!("{}/helio-dvfsrc/dvfsrc_req_ddr_opp", dir))
        .filter(|path| Path::new(path).exists())
        .collect()
}

fn ddr_performance(lite: bool) -> usize {
    if crate::tweaks::mitigation::has("DISABLE_DDR_TWEAK") {
        return 0;
    }
    let mut applied = usize::from(super::apply_node(HELIO_FORCE_PATH, "0"));
    for path in dvfsrc_platform_nodes() {
        if super::apply_node(&path, "0") {
            applied += 1;
        }
    }
    if let Some(base) = super::find_devfreq(&["mtk-dvfsrc-devfreq"]) {
        applied += if lite {
            super::devfreq_mid_perf(&base)
        } else {
            super::devfreq_max_perf(&base)
        };
    }
    applied
}

fn ddr_balance() -> usize {
    let mut applied = 0;
    if crate::tweaks::state::restore_path(HELIO_FORCE_PATH) {
        applied += 1;
    }
    for path in dvfsrc_platform_nodes() {
        if crate::tweaks::state::restore_path(&path) {
            applied += 1;
        }
    }
    if let Some(base) = super::find_devfreq(&["mtk-dvfsrc-devfreq"]) {
        applied += super::devfreq_unlock(&base);
    }
    applied
}

fn gpu_max_performance() -> usize {
    if Path::new("/proc/gpufreqv2").is_dir() {
        if let Some(index) = max_opp_index(GPUFREQ_V2_TABLE) {
            return usize::from(super::apply_node(FIX_OPP_PATH, &index.to_string()));
        }
    }
    let Some(freq) = parse_opp_table(GPUFREQ_V1_TABLE)
        .first()
        .map(|(_, freq)| *freq)
    else {
        return 0;
    };
    usize::from(super::apply_node(LEGACY_GPUFREQ_PATH, &freq.to_string()))
}

fn performance_base_profile() -> usize {
    let mut applied = ppm_policies(true);
    if super::apply_node(FPSGO_PATH, "1") {
        applied += 1;
    }
    for (name, value) in [
        ("enable_ceiling", "0"),
        ("engine_cooler_enable", "1"),
        ("boost_ta", "1"),
        ("boost_VIP", "1"),
    ] {
        if super::apply_node(&format!("{}/{}", FPSGO_FBT_DIR, name), value) {
            applied += 1;
        }
    }
    for (name, value) in [
        ("gx_game_mode", "1"),
        ("gx_force_cpu_boost", "1"),
        ("disp_dejitter", "1"),
    ] {
        if let Some(path) = ged_hal_node(name) {
            if super::apply_node(&path, value) {
                applied += 1;
            }
        }
    }
    if let Some(hz) = crate::tweaks::display::saved_mode_hz().filter(|hz| *hz >= 30) {
        let target = hz.to_string();
        for path in TARGET_FPS_PATHS {
            if super::apply_node(path, &target) {
                applied += 1;
            }
        }
    }
    for path in POWER_LIMITED_V2_PATHS {
        if super::apply_node(path, "0") {
            applied += 1;
        }
    }
    applied += power_limited("0");
    if super::apply_node(EARA_THERMAL_PATH, "1") {
        applied += 1;
    }
    applied += ddr_performance(false);
    applied
}

fn performance_profile() -> usize {
    let mut applied = performance_base_profile();
    applied += usize::from(super::apply_node(CCI_MODE_PATH, "1"));
    applied += usize::from(super::apply_node(POWER_MODE_PATH, "3"));
    applied += usize::from(super::apply_node(DRAMBOOST_PATH, "1"));
    applied += gpu_max_performance();
    applied += super::mali_power_policy(SocProfile::Performance);
    applied
}

fn balance_profile() -> usize {
    let mut applied = ppm_policies(true);
    if super::apply_node(FPSGO_PATH, "2") {
        applied += 1;
    }
    for (path, value) in [
        (CCI_MODE_PATH, "0"),
        (POWER_MODE_PATH, "0"),
        (DRAMBOOST_PATH, "0"),
        (EAS_PATH, "2"),
        (GED_KPI_PATH, "1"),
    ] {
        if super::apply_node(path, value) {
            applied += 1;
        }
    }

    if super::write_node(LEGACY_GPUFREQ_PATH, "0") {
        applied += 1;
    }
    if super::write_node(FIX_OPP_PATH, "-1") {
        applied += 1;
    }

    applied += power_limited("0");
    applied += super::mali_power_policy(SocProfile::Balance);
    if super::apply_node(BATOC_PATH, "stop 0") {
        applied += 1;
    }
    applied += ddr_balance();
    if super::apply_node(EARA_THERMAL_PATH, "1") {
        applied += 1;
    }
    applied
}

fn powersave_profile() -> usize {
    let mut applied = usize::from(super::apply_node(POWER_MODE_PATH, "1"));
    if Path::new("/proc/gpufreqv2").is_dir() {
        if let Some(index) = min_opp_index(GPUFREQ_V2_TABLE) {
            if super::apply_node(FIX_OPP_PATH, &index.to_string()) {
                applied += 1;
            }
        }
    } else if let Some(freq) = last_opp_freq(GPUFREQ_V1_TABLE) {
        if super::apply_node(LEGACY_GPUFREQ_PATH, &freq.to_string()) {
            applied += 1;
        }
    }
    applied += super::mali_power_policy(SocProfile::Eco);
    applied
}

pub(crate) fn frame_boost() -> usize {
    ddr_performance(false)
}

pub(crate) fn frame_relax() -> usize {
    ddr_balance()
}

pub fn apply(profile: SocProfile) -> usize {
    match profile {
        SocProfile::Lite => super::mali_power_policy(SocProfile::Lite),
        SocProfile::Balance => balance_profile(),
        SocProfile::Eco => powersave_profile(),
        SocProfile::Performance => performance_profile(),
    }
}

#[cfg(test)]
mod tests {
    use super::{first_opp_freq, last_opp_freq, min_opp_index, parse_opp_table};

    fn sample_table(name: &str) -> String {
        let dir = std::env::temp_dir().join(name);
        std::fs::write(
            &dir,
            "[00] freq: 1300000, volt: 93750, vsram: 93750\n[01] freq: 1274000, volt: 92500, vsram: 92500\n[02] freq: 1248000, volt: 90000, vsram: 90000\n",
        )
        .unwrap();
        dir.to_string_lossy().into_owned()
    }

    #[test]
    fn parses_gpufreq_opp_table() {
        let path = sample_table("lumina_mtk_opp_table_parse");
        let opps = parse_opp_table(&path);
        assert_eq!(opps, vec![(0, 1300000), (1, 1274000), (2, 1248000)]);
        let _ = std::fs::remove_file(path);
    }

    #[test]
    fn picks_opp_indices() {
        let path = sample_table("lumina_mtk_opp_table_indices");
        assert_eq!(min_opp_index(&path), Some(2));
        assert_eq!(first_opp_freq(&path), Some(1300000));
        assert_eq!(last_opp_freq(&path), Some(1248000));
        let _ = std::fs::remove_file(path);
    }
}
