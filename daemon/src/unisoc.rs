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

    pub fn apply_performance() {
        if !Self::is_unisoc() {
            return;
        }

        crate::log!("[UNISOC] Applying Unisoc / Spreadtrum Performance Tuning");

        crate::engine::write_sysfs("/sys/class/misc/mali0/device/power_policy", "always_on", true);

        if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().into_owned();
                if name.contains("gpu") || name.contains("mali") {
                    let gov = format!("/sys/class/devfreq/{}/governor", name);
                    crate::engine::write_sysfs(&gov, "performance", true);
                }
            }
        }

        crate::engine::write_sysfs("/sys/class/devfreq/scene-frequency/sprd_governor/scene_boost_dfs", "1", true);
        crate::engine::write_sysfs("/sys/class/devfreq/scene-frequency/sprd_governor/backlight_boost", "1", false);

        if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().into_owned();
                if name.contains("mddr") || name.contains("dram") || name.contains("sprd-dvfs") {
                    let gov = format!("/sys/class/devfreq/{}/governor", name);
                    crate::engine::write_sysfs(&gov, "performance", false);
                }
            }
        }

        crate::engine::write_sysfs("/sys/module/sprd_thermal/parameters/enable", "0", true);
    }

    pub fn apply_balance() {
        if !Self::is_unisoc() {
            return;
        }

        crate::log!("[UNISOC] Restoring Unisoc / Spreadtrum Balanced Baseline");

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
    }
}
