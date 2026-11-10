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
use std::path::Path;
use std::process::Command;

pub struct SnapdragonEngine;

impl SnapdragonEngine {
    fn get_prop(prop: &str) -> String {
        Command::new("getprop")
            .arg(prop)
            .output()
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
            .unwrap_or_default()
    }

    pub fn is_snapdragon() -> bool {
        let platform = Self::get_prop("ro.board.platform").to_lowercase();
        let hardware = Self::get_prop("ro.hardware").to_lowercase();
        let soc_mfg = Self::get_prop("ro.soc.manufacturer").to_lowercase();

        let is_qcom_prop = platform.starts_with("sm")
            || platform.starts_with("qcom")
            || platform.starts_with("sdm")
            || hardware.contains("qcom")
            || soc_mfg.contains("qualcomm");

        let has_kgsl_node = Path::new("/sys/class/kgsl/kgsl-3d0").exists();

        is_qcom_prop || has_kgsl_node
    }

    fn get_cpu_count() -> usize {
        if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu") {
            let count = entries
                .flatten()
                .filter(|e| {
                    let name = e.file_name();
                    let name_str = name.to_string_lossy();
                    name_str.starts_with("cpu")
                        && name_str[3..].chars().all(|c| c.is_ascii_digit())
                        && !name_str[3..].is_empty()
                })
                .count();
            if count > 0 {
                return count;
            }
        }
        8
    }

    fn lock_cpu_clocks(lock_max: bool) {
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
                        crate::engine::write_sysfs(&min_path, &effective_max.to_string(), true);
                        crate::engine::write_sysfs(&max_path, &effective_max.to_string(), true);
                    } else {
                        crate::engine::write_sysfs(&min_path, &hw_min.to_string(), false);
                        crate::engine::write_sysfs(&max_path, &effective_max.to_string(), false);
                    }
                }
            }
        }
    }

    fn apply_bus_dcvs(lock_max: bool) {
        {
            for comp in ["DDR", "LLCC", "L3", "DDRQOS"] {
                let base = format!("/sys/devices/system/cpu/bus_dcvs/{}", comp);
                let avail_p = format!("{}/available_frequencies", base);
                if let Ok(content) = fs::read_to_string(&avail_p) {
                    let freqs: Vec<u64> = content
                        .split_whitespace()
                        .filter_map(|s| s.parse::<u64>().ok())
                        .collect();

                    if let Some(&max_f) = freqs.iter().max() {
                        let min_f = freqs.iter().min().copied().unwrap_or(0);
                        let hw_max_p = format!("{}/hw_max_freq", base);
                        let hw_min_p = format!("{}/hw_min_freq", base);

                        if lock_max {
                            crate::engine::write_sysfs(&hw_max_p, &max_f.to_string(), true);
                            crate::engine::write_sysfs(&hw_min_p, &max_f.to_string(), true);
                        } else {
                            crate::engine::write_sysfs(&hw_max_p, &max_f.to_string(), false);
                            crate::engine::write_sysfs(&hw_min_p, &min_f.to_string(), false);
                        }

                        if let Ok(entries) = fs::read_dir(&base) {
                            for entry in entries.flatten() {
                                let path = entry.path();
                                if path.is_dir() {
                                    let sub_max = path.join("max_freq");
                                    let sub_min = path.join("min_freq");
                                    if lock_max {
                                        if let Some(p) = sub_max.to_str() {
                                            crate::engine::write_sysfs(p, &max_f.to_string(), true);
                                        }
                                        if let Some(p) = sub_min.to_str() {
                                            crate::engine::write_sysfs(p, &max_f.to_string(), true);
                                        }
                                    } else {
                                        if let Some(p) = sub_max.to_str() {
                                            crate::engine::write_sysfs(
                                                p,
                                                &max_f.to_string(),
                                                false,
                                            );
                                        }
                                        if let Some(p) = sub_min.to_str() {
                                            crate::engine::write_sysfs(
                                                p,
                                                &min_f.to_string(),
                                                false,
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            let governor = if lock_max { "performance" } else { "" };
            crate::devfreq::apply(&["memlat", "latfloor", "ddr-lat", "gpubw"], governor, false);
        }
    }

    pub fn set_thermal_throttling(bypass: bool) {
        if !Self::is_snapdragon() {
            return;
        }

        if bypass {
            crate::log!("[QCOM] Disabling Adreno KGSL & MSM Thermal Throttling");
            crate::engine::write_sysfs("/sys/module/msm_thermal/parameters/enabled", "N", true);
            crate::engine::write_sysfs("/sys/devices/soc:qcom,bcl/mode", "disable", true);
            crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/throttling", "0", true);
            crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0", true);
        } else {
            crate::log!("[QCOM] Restoring Adreno KGSL & MSM Thermal Throttling");
            crate::engine::write_sysfs("/sys/module/msm_thermal/parameters/enabled", "Y", false);
            crate::engine::write_sysfs("/sys/devices/soc:qcom,bcl/mode", "enable", false);
            crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/throttling", "1", false);
            crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0", false);
        }
    }

    pub fn apply_performance() {
        if !Self::is_snapdragon() {
            return;
        }

        crate::log!("[QCOM] Applying Snapdragon Performance Profile");
        crate::devfreq::apply_patterns(
            &[
                ("cpu-ddr-latfloor", "performance"),
                ("cpu0-cpu", "performance"),
                ("cpu-cpu-ddr-bw", "performance"),
                ("cpu-cpu-llcc-bw", "performance"),
                ("gpubw", "performance"),
            ],
            true,
        );

        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "3", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/bus_split", "0", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_clk_on", "1", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_bus_on", "1", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_rail_on", "1", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/idle_timer", "80", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/throttling", "0", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", "0", true);

        let gpu_devfreq = "/sys/class/kgsl/kgsl-3d0/devfreq";
        let gpu_avail = format!("{}/available_frequencies", gpu_devfreq);
        if let Ok(content) = fs::read_to_string(&gpu_avail) {
            let freqs: Vec<u64> = content
                .split_whitespace()
                .filter_map(|s| s.parse::<u64>().ok())
                .collect();
            if let Some(&max_f) = freqs.iter().max() {
                crate::engine::write_sysfs(
                    &format!("{}/min_freq", gpu_devfreq),
                    &max_f.to_string(),
                    true,
                );
                crate::engine::write_sysfs(
                    &format!("{}/max_freq", gpu_devfreq),
                    &max_f.to_string(),
                    true,
                );
            }
        }

        crate::engine::write_sysfs("/proc/sys/kernel/sched_energy_aware", "0", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_boost", "1", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_upmigrate", "60", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_downmigrate", "40", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_group_upmigrate", "65", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_group_downmigrate", "45", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_coloc_busy_hyst_ns", "0", false);

        if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") {
            for entry in entries.flatten() {
                let p = entry.path();
                let up_rate = p.join("walt/up_rate_limit_us");
                let down_rate = p.join("walt/down_rate_limit_us");
                if up_rate.exists() {
                    let _ = fs::write(&up_rate, b"8000");
                }
                if down_rate.exists() {
                    let _ = fs::write(&down_rate, b"12000");
                }
            }
        }

        for i in 0..Self::get_cpu_count() {
            let core_ctl_path = format!("/sys/devices/system/cpu/cpu{}/core_ctl/enable", i);
            crate::engine::write_sysfs(&core_ctl_path, "0", false);
        }

        if !crate::engine::vendor_thermal_managed() {
            crate::engine::write_sysfs("/sys/devices/soc:qcom,bcl/mode", "disable", true);
            crate::engine::write_sysfs("/sys/module/msm_thermal/parameters/enabled", "N", true);
        }

        Self::apply_bus_dcvs(true);
        Self::lock_cpu_clocks(true);
    }

    pub fn apply_balance() {
        if !Self::is_snapdragon() {
            return;
        }

        crate::log!("[QCOM] Restoring Snapdragon Balanced Baseline");

        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "1", false);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/bus_split", "1", false);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_clk_on", "0", false);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_bus_on", "0", false);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_rail_on", "0", false);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/idle_timer", "24", false);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/throttling", "1", false);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0", false);

        let num_pwr = fs::read_to_string("/sys/class/kgsl/kgsl-3d0/num_pwrlevels")
            .unwrap_or_else(|_| "6".to_string());
        let target_min = num_pwr
            .trim()
            .parse::<u32>()
            .unwrap_or(6)
            .saturating_sub(1)
            .to_string();

        if !crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", &target_min, false)
        {
            crate::engine::write_sysfs(
                "/sys/class/kgsl/kgsl-3d0/min_pwrlevel",
                num_pwr.trim(),
                false,
            );
        }
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0", false);

        let gpu_devfreq = "/sys/class/kgsl/kgsl-3d0/devfreq";
        let gpu_avail = format!("{}/available_frequencies", gpu_devfreq);
        if let Ok(content) = fs::read_to_string(&gpu_avail) {
            let freqs: Vec<u64> = content
                .split_whitespace()
                .filter_map(|s| s.parse::<u64>().ok())
                .collect();
            if freqs.len() >= 2 {
                crate::engine::write_sysfs(
                    &format!("{}/min_freq", gpu_devfreq),
                    &freqs[1].to_string(),
                    false,
                );
                crate::engine::write_sysfs(
                    &format!("{}/max_freq", gpu_devfreq),
                    &freqs[freqs.len() - 1].to_string(),
                    false,
                );
            }
        }

        crate::engine::write_sysfs("/proc/sys/kernel/sched_energy_aware", "1", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_boost", "0", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_upmigrate", "85", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_downmigrate", "65", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_group_upmigrate", "85", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_group_downmigrate", "65", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_coloc_busy_hyst_ns", "39000000", false);

        let cpu_count = Self::get_cpu_count();
        for i in 0..cpu_count {
            let core_ctl_path = format!("/sys/devices/system/cpu/cpu{}/core_ctl/enable", i);
            crate::engine::write_sysfs(&core_ctl_path, "1", false);
        }

        if !crate::engine::vendor_thermal_managed() {
            crate::engine::write_sysfs("/sys/devices/soc:qcom,bcl/mode", "enable", false);
            crate::engine::write_sysfs("/sys/module/msm_thermal/parameters/enabled", "Y", false);
        }

        Self::apply_bus_dcvs(false);
        Self::lock_cpu_clocks(false);
    }
}
