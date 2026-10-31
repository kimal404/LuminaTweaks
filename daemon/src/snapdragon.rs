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

    pub fn apply_performance() {
        if !Self::is_snapdragon() {
            return;
        }

        crate::log!("[QCOM] Applying Snapdragon & Adreno Performance Tuning");

        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "3", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/bus_split", "0", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_clk_on", "1", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_bus_on", "1", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_rail_on", "1", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/idle_timer", "80", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/throttling", "0", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0", true);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0", false);
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", "1", false);

        crate::engine::write_sysfs("/proc/sys/kernel/sched_energy_aware", "0", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_boost", "1", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_upmigrate", "60", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_downmigrate", "40", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_group_upmigrate", "65", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_group_downmigrate", "45", false);
        crate::engine::write_sysfs("/proc/sys/walt/sched_coloc_busy_hyst_ns", "0", false);

        let cpu_count = Self::get_cpu_count();
        for i in 0..cpu_count {
            let core_ctl_path = format!("/sys/devices/system/cpu/cpu{}/core_ctl/enable", i);
            crate::engine::write_sysfs(&core_ctl_path, "0", false);
        }

        for entry in ["soc:qcom,cpu-llcc-ddr-bw", "soc:qcom,cpu-cpu-llcc-bw", "soc:qcom,kgsl-busmon"] {
            let gov = format!("/sys/class/devfreq/{}/governor", entry);
            crate::engine::write_sysfs(&gov, "performance", false);
        }

        crate::engine::write_sysfs("/sys/devices/soc:qcom,bcl/mode", "disable", true);
        crate::engine::write_sysfs("/sys/module/msm_thermal/parameters/enabled", "N", true);
    }

    pub fn apply_balance() {
        if !Self::is_snapdragon() {
            return;
        }

        crate::log!("[QCOM] Restoring Snapdragon Balanced Baseline");

        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "0", false);
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
        if !crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", &target_min, false) {
            crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", num_pwr.trim(), false);
        }
        crate::engine::write_sysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0", false);

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

        for entry in ["soc:qcom,cpu-llcc-ddr-bw", "soc:qcom,cpu-cpu-llcc-bw"] {
            let gov = format!("/sys/class/devfreq/{}/governor", entry);
            crate::engine::write_sysfs(&gov, "bw_hwmon", false);
        }
        crate::engine::write_sysfs("/sys/class/devfreq/soc:qcom,kgsl-busmon/governor", "gpubw_mon", false);

        crate::engine::write_sysfs("/sys/devices/soc:qcom,bcl/mode", "enable", false);
        crate::engine::write_sysfs("/sys/module/msm_thermal/parameters/enabled", "Y", false);
    }
}
