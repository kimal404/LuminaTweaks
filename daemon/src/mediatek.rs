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


pub fn set_thermal_throttling(bypass: bool) {
    if !is_mtk_device() {
        return;
    }

    if bypass {
        crate::log!("[MTK] Disabling SoC thermal throttling policies");

        write_sysfs("/proc/cpufreq/cpufreq_imax_thermal_protect", "0", true);


        write_sysfs("/proc/gpufreq/gpufreq_power_limited", "ignore_thermal_protect 1", false);

        write_sysfs("/proc/ppm/enabled", "0", true);
        for idx in [2, 3, 4, 6, 7] {
            let policy = format!("{} 0", idx);
            write_sysfs("/proc/ppm/policy_status", &policy, false);
        }

        
        lock_mali_tmu(true);
    } else {
        crate::log!("[MTK] Restoring SoC thermal throttling policies");
        write_sysfs("/proc/cpufreq/cpufreq_imax_thermal_protect", "1", false);
        write_sysfs("/proc/gpufreq/gpufreq_power_limited", "ignore_thermal_protect 0", false);
        write_sysfs("/proc/ppm/enabled", "1", false);
        for idx in [2, 3, 4, 6, 7] {
            let policy = format!("{} 1", idx);
            write_sysfs("/proc/ppm/policy_status", &policy, false);
        }
        lock_mali_tmu(false);
    }
}

fn lock_mali_tmu(lock: bool) {
    let base_dirs = ["/sys/devices", "/sys/devices/platform"];
    for base in base_dirs {
        if let Ok(entries) = fs::read_dir(base) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().and_then(|s| s.to_str()) == Some("mali") {
                    let perm = if lock { 0o000 } else { 0o644 };
                    for sub in ["tmu", "throttling", "tripping"] {
                        let target = path.join(sub);
                        if target.exists() {
                            let _ = fs::set_permissions(&target, fs::Permissions::from_mode(perm));
                        }
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

    crate::log!("[MTK] Applying Mali GPU & DVFSRC Performance Profile");
    write_sysfs("/sys/class/misc/mali0/device/power_policy", "always_on", true);
    write_sysfs("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "performance", true);
    write_sysfs("/proc/gpufreqv2/fix_target_opp_index", "0", true);
}

pub fn apply_mtk_balance() {
    if !is_mtk_device() {
        return;
    }

    crate::log!("[MTK] Restoring Balanced Baseline");
    write_sysfs("/sys/class/misc/mali0/device/power_policy", "coarse_demand", false);
    write_sysfs("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "simple_ondemand", false);
    write_sysfs("/proc/gpufreqv2/fix_target_opp_index", "-1", false);
}

pub fn apply_mtk_eco() {
    if !is_mtk_device() {
        return;
    }

    crate::log!("[MTK] Applying Eco Profile");
    write_sysfs("/sys/class/misc/mali0/device/power_policy", "coarse_demand", false);
    write_sysfs("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "powersave", false);
    write_sysfs("/proc/gpufreqv2/fix_target_opp_index", "-1", false);
}
