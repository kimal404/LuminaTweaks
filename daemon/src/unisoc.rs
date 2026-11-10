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

pub struct UnisocEngine;

impl UnisocEngine {
    fn get_prop(prop: &str) -> String {
        Command::new("getprop")
            .arg(prop)
            .output()
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
            .unwrap_or_default()
    }

    pub fn is_unisoc() -> bool {
        let platform = Self::get_prop("ro.board.platform").to_lowercase();
        let hardware = Self::get_prop("ro.hardware").to_lowercase();
        let soc_mfg = Self::get_prop("ro.soc.manufacturer").to_lowercase();

        let is_unisoc_prop = platform.starts_with("ums")
            || platform.starts_with("uis")
            || platform.starts_with("sp9")
            || platform.starts_with("sp7")
            || platform.starts_with("sc9")
            || platform.contains("roc1")
            || platform.contains("shark")
            || platform.contains("unisoc")
            || hardware.contains("sprd")
            || hardware.contains("unisoc")
            || soc_mfg.contains("unisoc")
            || soc_mfg.contains("spreadtrum");

        let has_sprd_node = Path::new("/sys/class/devfreq/scene-frequency/sprd_governor").exists()
            || Path::new("/sys/module/sprd_thermal").exists();

        is_unisoc_prop || has_sprd_node
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

    fn apply_gpu_devfreq(lock_max: bool) {
        if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().into_owned();
                if name.contains("gpu") || name.contains("mali") {
                    let base = format!("/sys/class/devfreq/{}", name);
                    let avail_p = format!("{}/available_frequencies", base);
                    if let Ok(content) = fs::read_to_string(&avail_p) {
                        let freqs: Vec<u64> = content
                            .split_whitespace()
                            .filter_map(|s| s.parse::<u64>().ok())
                            .collect();

                        if let Some(&max_f) = freqs.iter().max() {
                            let min_f = freqs.iter().min().copied().unwrap_or(0);
                            let max_p = format!("{}/max_freq", base);
                            let min_p = format!("{}/min_freq", base);

                            if lock_max {
                                crate::engine::write_sysfs(&max_p, &max_f.to_string(), true);
                                crate::engine::write_sysfs(&min_p, &max_f.to_string(), true);
                            } else {
                                crate::engine::write_sysfs(&max_p, &max_f.to_string(), false);
                                crate::engine::write_sysfs(&min_p, &min_f.to_string(), false);
                            }
                        }
                    }
                }
            }
        }
    }

    pub fn apply_performance() {
        if !Self::is_unisoc() {
            return;
        }

        crate::log!("[UNISOC] Applying Unisoc Performance Tuning");
        let no_perf = crate::quirks::active("NO_PERFORMANCE_GOVERNOR");
        let no_thermal = crate::quirks::active("NO_THERMAL_BYPASS");
        let no_ddr = crate::quirks::active("NO_DDR_TWEAK");

        crate::engine::write_sysfs("/sys/class/misc/mali0/device/power_policy", "always_on", true);

        if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().into_owned();
                if name.contains("gpu") || name.contains("mali") {
                    let gov = format!("/sys/class/devfreq/{}/governor", name);
                    crate::engine::write_sysfs(
                        &gov,
                        if no_perf { "simple_ondemand" } else { "performance" },
                        !no_perf,
                    );
                }
            }
        }

        crate::engine::write_sysfs("/sys/class/devfreq/scene-frequency/sprd_governor/scene_boost_dfs", "1", true);
        crate::engine::write_sysfs("/sys/class/devfreq/scene-frequency/sprd_governor/backlight_boost", "1", false);

        if !no_ddr {
            if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
                for entry in entries.flatten() {
                    let name = entry.file_name().to_string_lossy().into_owned();
                    if name.contains("mddr") || name.contains("dram") || name.contains("sprd-dvfs") {
                        let gov = format!("/sys/class/devfreq/{}/governor", name);
                        crate::engine::write_sysfs(&gov, "performance", false);
                    }
                }
            }
        }

        if !no_thermal {
            crate::engine::write_sysfs("/sys/module/sprd_thermal/parameters/enable", "0", true);
        }

        Self::apply_gpu_devfreq(true);
        Self::lock_cpu_clocks(true);
    }

    pub fn apply_balance() {
        if !Self::is_unisoc() {
            return;
        }

        crate::log!("[UNISOC] Restoring Unisoc Balanced Baseline");

        crate::engine::write_sysfs("/sys/class/misc/mali0/device/power_policy", "coarse_demand", false);

        if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().into_owned();
                if name.contains("gpu") || name.contains("mali") {
                    let gov = format!("/sys/class/devfreq/{}/governor", name);
                    crate::engine::write_sysfs(&gov, "simple_ondemand", false);
                }
            }
        }

        crate::engine::write_sysfs("/sys/class/devfreq/scene-frequency/sprd_governor/scene_boost_dfs", "0", false);
        crate::engine::write_sysfs("/sys/class/devfreq/scene-frequency/sprd_governor/backlight_boost", "0", false);

        if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().into_owned();
                if name.contains("mddr") || name.contains("dram") || name.contains("sprd-dvfs") {
                    let gov = format!("/sys/class/devfreq/{}/governor", name);
                    crate::engine::write_sysfs(&gov, "sprd_governor", false);
                }
            }
        }

        crate::engine::write_sysfs("/sys/module/sprd_thermal/parameters/enable", "1", false);

        Self::apply_gpu_devfreq(false);
        Self::lock_cpu_clocks(false);
    }
}
