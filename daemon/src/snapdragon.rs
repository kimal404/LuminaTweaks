use std::fs;
use std::os::unix::fs::PermissionsExt;
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

    fn write_sysfs(path: &str, val: &str, lock: bool) -> bool {
        if !Path::new(path).exists() {
            return false;
        }
        let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o644));
        let res = fs::write(path, val.as_bytes()).is_ok();
        let mode = if lock { 0o444 } else { 0o644 };
        let _ = fs::set_permissions(path, fs::Permissions::from_mode(mode));
        res
    }

    pub fn apply_performance() {
        if !Self::is_snapdragon() {
            return;
        }

        crate::log!("[QCOM] Applying Snapdragon & Adreno Performance Tuning");

        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "3", true);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/bus_split", "0", true);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_clk_on", "1", true);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_bus_on", "1", true);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_rail_on", "1", true);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/idle_timer", "80", true);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/throttling", "0", true);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0", true);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0", false);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", "1", false);

        Self::write_sysfs("/proc/sys/kernel/sched_energy_aware", "0", false);
        Self::write_sysfs("/proc/sys/walt/sched_boost", "1", false);
        Self::write_sysfs("/proc/sys/walt/sched_upmigrate", "60", false);
        Self::write_sysfs("/proc/sys/walt/sched_downmigrate", "40", false);
        Self::write_sysfs("/proc/sys/walt/sched_group_upmigrate", "65", false);
        Self::write_sysfs("/proc/sys/walt/sched_group_downmigrate", "45", false);
        Self::write_sysfs("/proc/sys/walt/sched_coloc_busy_hyst_ns", "0", false);

        for i in 0..8 {
            let core_ctl_path = format!("/sys/devices/system/cpu/cpu{}/core_ctl/enable", i);
            Self::write_sysfs(&core_ctl_path, "0", false);
        }

        for entry in ["soc:qcom,cpu-llcc-ddr-bw", "soc:qcom,cpu-cpu-llcc-bw", "soc:qcom,kgsl-busmon"] {
            let gov = format!("/sys/class/devfreq/{}/governor", entry);
            Self::write_sysfs(&gov, "performance", false);
        }

        Self::write_sysfs("/sys/devices/soc:qcom,bcl/mode", "disable", true);
        Self::write_sysfs("/sys/module/msm_thermal/parameters/enabled", "N", true);
    }

    pub fn apply_balance() {
        if !Self::is_snapdragon() {
            return;
        }

        crate::log!("[QCOM] Restoring Snapdragon Balanced Baseline");

        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "0", false);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/bus_split", "1", false);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_clk_on", "0", false);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_bus_on", "0", false);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/force_rail_on", "0", false);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/idle_timer", "24", false);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/throttling", "1", false);

        let num_pwr = fs::read_to_string("/sys/class/kgsl/kgsl-3d0/num_pwrlevels")
            .unwrap_or_else(|_| "5".to_string());
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", num_pwr.trim(), false);
        Self::write_sysfs("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0", false);

        Self::write_sysfs("/proc/sys/kernel/sched_energy_aware", "1", false);
        Self::write_sysfs("/proc/sys/walt/sched_boost", "0", false);
        Self::write_sysfs("/proc/sys/walt/sched_upmigrate", "85", false);
        Self::write_sysfs("/proc/sys/walt/sched_downmigrate", "65", false);
        Self::write_sysfs("/proc/sys/walt/sched_group_upmigrate", "85", false);
        Self::write_sysfs("/proc/sys/walt/sched_group_downmigrate", "65", false);

        for i in 0..8 {
            let core_ctl_path = format!("/sys/devices/system/cpu/cpu{}/core_ctl/enable", i);
            Self::write_sysfs(&core_ctl_path, "1", false);
        }

        for entry in ["soc:qcom,cpu-llcc-ddr-bw", "soc:qcom,cpu-cpu-llcc-bw", "soc:qcom,kgsl-busmon"] {
            let gov = format!("/sys/class/devfreq/{}/governor", entry);
            Self::write_sysfs(&gov, "bw_hwmon", false);
        }

        Self::write_sysfs("/sys/devices/soc:qcom,bcl/mode", "enable", false);
        Self::write_sysfs("/sys/module/msm_thermal/parameters/enabled", "Y", false);
    }
}
