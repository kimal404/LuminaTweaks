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

use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::process::Command;

fn get_prop(prop: &str) -> String {
    Command::new("getprop")
        .arg(prop)
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default()
}

pub fn is_mtk_device() -> bool {
    let platform = get_prop("ro.board.platform").to_lowercase();
    let hardware = get_prop("ro.hardware").to_lowercase();
    let soc_mfg = get_prop("ro.soc.manufacturer").to_lowercase();

    if soc_mfg.contains("samsung") || platform.contains("exynos") || hardware.contains("exynos") {
        return false;
    }

    let is_mtk_prop = platform.starts_with("mt")
        || platform.contains("mediatek")
        || hardware.starts_with("mt")
        || hardware.contains("mediatek")
        || soc_mfg.contains("mediatek");

    let has_mtk_exclusive_node = Path::new("/sys/class/misc/mali0").exists()
        || Path::new("/sys/class/devfreq/mtk-dvfsrc-devfreq").exists()
        || Path::new("/proc/gpufreqv2").exists();

    is_mtk_prop || has_mtk_exclusive_node
}

fn write_sysfs(path: &str, val: &str, lock: bool) -> bool {
    if !Path::new(path).exists() {
        return false;
    }
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o644));
    let res = match fs::write(path, val.as_bytes()) {
        Ok(_) => true,
        Err(e) => {
            crate::log!("[WARN] Failed to write '{}' to {}: {}", val, path, e);
            false
        }
    };
    let mode = if lock { 0o444 } else { 0o644 };
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(mode));
    res
}

fn lock_mtk_cpu_clocks(lock_max: bool) {
    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") {
        for entry in entries.flatten() {
            let fname = entry.file_name().to_string_lossy().into_owned();
            if fname.starts_with("policy") {
                let p_str = format!("/sys/devices/system/cpu/cpufreq/{}", fname);
                let min_path = format!("{}/scaling_min_freq", p_str);
                let max_path = format!("{}/scaling_max_freq", p_str);
                let hw_min_path = format!("{}/cpuinfo_min_freq", p_str);
                let hw_max_path = format!("{}/cpuinfo_max_freq", p_str);
                let avail_path = format!("{}/scaling_available_frequencies", p_str);

                let hw_min = fs::read_to_string(&hw_min_path)
                    .ok()
                    .and_then(|s| s.trim().parse::<u64>().ok())
                    .unwrap_or(300000);
                let hw_max = fs::read_to_string(&hw_max_path)
                    .ok()
                    .and_then(|s| s.trim().parse::<u64>().ok())
                    .unwrap_or(2000000);

                let freqs: Vec<u64> = fs::read_to_string(&avail_path)
                    .unwrap_or_default()
                    .split_whitespace()
                    .filter_map(|s| s.parse::<u64>().ok())
                    .collect();

                let effective_max = freqs.iter().copied().max().unwrap_or(hw_max);

                if lock_max {
                    write_sysfs(&min_path, &effective_max.to_string(), true);
                    write_sysfs(&max_path, &effective_max.to_string(), true);
                } else {
                    write_sysfs(&min_path, &hw_min.to_string(), false);
                    write_sysfs(&max_path, &effective_max.to_string(), false);
                }
            }
        }
    }
}

fn apply_mtk_dvfsrc(lock_max: bool) {
    let vcore_node = "/sys/kernel/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp";
    if lock_max {
        write_sysfs(vcore_node, "0", true);
    } else if Path::new(vcore_node).exists() {
        let _ = fs::set_permissions(vcore_node, fs::Permissions::from_mode(0o644));
    }

    let base_dirs = ["/sys/devices/platform", "/sys/devices/platform/soc"];
    for base in base_dirs {
        if let Ok(entries) = fs::read_dir(base) {
            for entry in entries.flatten() {
                let path = entry.path();
                let name = path.file_name().and_then(|n| n.to_str()).unwrap_or("");
                if name.contains("dvfsrc") {
                    let ddr_node = path.join("helio-dvfsrc/dvfsrc_req_ddr_opp");
                    if ddr_node.exists() {
                        if let Some(p) = ddr_node.to_str() {
                            if lock_max {
                                write_sysfs(p, "0", true);
                            } else {
                                let _ = fs::set_permissions(p, fs::Permissions::from_mode(0o644));
                            }
                        }
                    }
                }
            }
        }
    }
}

pub fn set_thermal_throttling(bypass: bool) {
    if !is_mtk_device() {
        return;
    }

    if bypass {
        crate::log!("[MTK] Disabling SoC thermal throttling policies");
        write_sysfs("/proc/cpufreq/cpufreq_imax_thermal_protect", "0", true);
        write_sysfs("/proc/gpufreq/gpufreq_power_limited", "ignore_thermal_protect 1", false);
        write_sysfs("/proc/ppm/enabled", "0", true);

        if let Ok(content) = fs::read_to_string("/proc/ppm/policy_status") {
            for line in content.lines() {
                let is_limit = line.contains("FORCE_LIMIT") || line.contains("PWR_THRO") || line.contains("THERMAL") || line.contains("USER_LIMIT");
                let is_boost = line.contains("SYS_BOOST");
                if let Some(idx_str) = line.split('[').nth(1).and_then(|s| s.split(']').next()) {
                    if is_limit {
                        write_sysfs("/proc/ppm/policy_status", &format!("{} 0", idx_str), false);
                    }
                    if is_boost {
                        write_sysfs("/proc/ppm/policy_status", &format!("{} 1", idx_str), false);
                    }
                }
            }
        }
    } else {
        crate::log!("[MTK] Restoring SoC thermal throttling policies");
        write_sysfs("/proc/cpufreq/cpufreq_imax_thermal_protect", "1", false);
        write_sysfs("/proc/gpufreq/gpufreq_power_limited", "ignore_thermal_protect 0", false);
        write_sysfs("/proc/ppm/enabled", "1", false);

        if let Ok(content) = fs::read_to_string("/proc/ppm/policy_status") {
            for line in content.lines() {
                let is_limit = line.contains("FORCE_LIMIT") || line.contains("PWR_THRO") || line.contains("THERMAL") || line.contains("USER_LIMIT");
                let is_boost = line.contains("SYS_BOOST");
                if let Some(idx_str) = line.split('[').nth(1).and_then(|s| s.split(']').next()) {
                    if is_limit {
                        write_sysfs("/proc/ppm/policy_status", &format!("{} 1", idx_str), false);
                    }
                    if is_boost {
                        write_sysfs("/proc/ppm/policy_status", &format!("{} 0", idx_str), false);
                    }
                }
            }
        }
    }
}

pub fn apply_mtk_performance() {
    if !is_mtk_device() {
        return;
    }

    crate::log!("[MTK] Applying Performance Profile");
    let no_perf = crate::quirks::active("NO_PERFORMANCE_GOVERNOR");
    let no_thermal = crate::quirks::active("NO_THERMAL_BYPASS");
    let no_ddr = crate::quirks::active("NO_DDR_TWEAK");

    write_sysfs("/sys/class/misc/mali0/device/power_policy", "always_on", true);
    write_sysfs(
        "/sys/class/devfreq/mtk-dvfsrc-devfreq/governor",
        if no_perf { "simple_ondemand" } else { "performance" },
        !no_perf,
    );
    write_sysfs("/proc/gpufreqv2/fix_target_opp_index", "0", true);

    write_sysfs("/sys/kernel/fpsgo/common/force_onoff", "0", true);
    if !no_thermal {
        write_sysfs("/sys/kernel/eara_thermal/enable", "0", true);
    }
    write_sysfs("/sys/devices/system/cpu/eas/enable", "0", true);

    write_sysfs("/sys/kernel/ged/hal/gpu_boost_level", "100", true);
    write_sysfs("/sys/module/mtk_fpsgo/parameters/uboost_enhance_f", "100", true);
    write_sysfs("/sys/module/mtk_fpsgo/parameters/isolation_limit_cap", "0", true);
    write_sysfs("/sys/pnpmgr/fpsgo_boost/boost_enable", "1", true);
    write_sysfs("/sys/pnpmgr/fpsgo_boost/boost_mode", "1", true);

    if !no_ddr {
        write_sysfs("/proc/cpufreq/cpufreq_cci_mode", "1", true);
    }
    write_sysfs("/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled", "0", true);
    if !no_ddr {
        write_sysfs("/sys/devices/platform/boot_dramboost/dramboost/dramboost", "1", true);
    }
    write_sysfs("/proc/mtk_batoc_throttling/battery_oc_protect_stop", "stop 1", true);
    write_sysfs("/proc/perfmgr/syslimiter/syslimiter_force_disable", "0", false);

    if !no_ddr {
        apply_mtk_dvfsrc(true);
    }
    lock_mtk_cpu_clocks(true);
}

pub fn apply_mtk_balance() {
    if !is_mtk_device() {
        return;
    }

    crate::log!("[MTK] Restoring Balanced Baseline");
    write_sysfs("/sys/class/misc/mali0/device/power_policy", "coarse_demand", false);
    write_sysfs("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "simple_ondemand", false);
    write_sysfs("/proc/gpufreqv2/fix_target_opp_index", "-1", false);

    write_sysfs("/sys/kernel/fpsgo/common/force_onoff", "2", false);
    write_sysfs("/sys/kernel/eara_thermal/enable", "1", false);
    write_sysfs("/sys/devices/system/cpu/eas/enable", "1", false);

    write_sysfs("/sys/kernel/ged/hal/gpu_boost_level", "0", false);
    write_sysfs("/sys/module/mtk_fpsgo/parameters/uboost_enhance_f", "0", false);
    write_sysfs("/sys/pnpmgr/fpsgo_boost/boost_enable", "0", false);
    write_sysfs("/sys/pnpmgr/fpsgo_boost/boost_mode", "0", false);

    write_sysfs("/proc/cpufreq/cpufreq_cci_mode", "0", false);
    write_sysfs("/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled", "1", false);
    write_sysfs("/sys/devices/platform/boot_dramboost/dramboost/dramboost", "0", false);
    write_sysfs("/proc/mtk_batoc_throttling/battery_oc_protect_stop", "stop 0", false);
    write_sysfs("/proc/perfmgr/syslimiter/syslimiter_force_disable", "0", false);

    apply_mtk_dvfsrc(false);
    lock_mtk_cpu_clocks(false);
}

pub fn apply_mtk_eco() {
    if !is_mtk_device() {
        return;
    }

    crate::log!("[MTK] Applying Eco Profile");
    write_sysfs("/sys/class/misc/mali0/device/power_policy", "coarse_demand", false);
    write_sysfs("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "powersave", false);
    write_sysfs("/proc/gpufreqv2/fix_target_opp_index", "-1", false);

    write_sysfs("/sys/kernel/fpsgo/common/force_onoff", "2", false);
    write_sysfs("/sys/kernel/eara_thermal/enable", "1", false);
    write_sysfs("/sys/devices/system/cpu/eas/enable", "1", false);

    write_sysfs("/sys/kernel/ged/hal/gpu_boost_level", "0", false);
    write_sysfs("/sys/module/mtk_fpsgo/parameters/uboost_enhance_f", "0", false);
    write_sysfs("/proc/cpufreq/cpufreq_cci_mode", "0", false);
    write_sysfs("/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled", "1", false);
    write_sysfs("/sys/devices/platform/boot_dramboost/dramboost/dramboost", "0", false);
    write_sysfs("/proc/mtk_batoc_throttling/battery_oc_protect_stop", "stop 0", false);

    apply_mtk_dvfsrc(false);
    lock_mtk_cpu_clocks(false);
}
