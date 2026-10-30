use std::fs;
use std::path::Path;
use std::process::Command;

pub fn is_mtk_device() -> bool {
    Path::new("/sys/kernel/fpsgo/common/force_onoff").exists()
        || Path::new("/sys/class/misc/mali0").exists()
        || Path::new("/sys/kernel/ged/hal").exists()
}

fn read_flag(path: &str) -> Option<String> {
    fs::read_to_string(path).ok().map(|s| s.trim().to_string())
}

fn get_prop(prop: &str) -> String {
    Command::new("getprop")
        .arg(prop)
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default()
}

pub fn is_fpsgo_bypass_enabled() -> bool {
    if let Some(val) = read_flag("/data/adb/luminatweaks/fpsgo_bypass.txt") {
        return val == "1";
    }
    let prop = get_prop("persist.lumina.fpsgo_bypass");
    prop == "1"
}

pub fn is_ged_boost_enabled() -> bool {
    if let Some(val) = read_flag("/data/adb/luminatweaks/ged_boost.txt") {
        return val == "1";
    }
    let prop = get_prop("persist.lumina.ged_boost");
    if !prop.is_empty() {
        return prop == "1";
    }
    true
}

fn write_sysfs(path: &str, val: &str, lock: bool) -> bool {
    if !Path::new(path).exists() {
        return false;
    }
    let _ = Command::new("chmod").args(["644", path]).status();
    let res = fs::write(path, val.as_bytes()).is_ok();
    if lock {
        let _ = Command::new("chmod").args(["444", path]).status();
    } else {
        let _ = Command::new("chmod").args(["644", path]).status();
    }
    res
}

pub fn apply_mtk_performance() {
    if !is_mtk_device() {
        return;
    }

    let bypass_fpsgo = is_fpsgo_bypass_enabled();
    let boost_ged = is_ged_boost_enabled();

    log!(
        "[MTK] Performance Profile (Bypass FPSGo: {}, Boost GED: {})",
        bypass_fpsgo,
        boost_ged
    );

    if bypass_fpsgo {
        write_sysfs("/sys/kernel/fpsgo/common/force_onoff", "0", true);
    } else {
        write_sysfs("/sys/kernel/fpsgo/common/force_onoff", "1", false);
    }

    if boost_ged {
        write_sysfs("/sys/kernel/ged/hal/custom_boost_gpu_freq", "1", true);
    } else {
        write_sysfs("/sys/kernel/ged/hal/custom_boost_gpu_freq", "0", false);
    }
    write_sysfs("/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled", "0", true);

    write_sysfs("/sys/class/misc/mali0/device/power_policy", "always_on", true);
    write_sysfs("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "performance", true);
    write_sysfs("/proc/gpufreqv2/fix_target_opp_index", "0", true);
}

pub fn apply_mtk_balance() {
    if !is_mtk_device() {
        return;
    }

    log!("[MTK] Restoring Balanced Baseline");
    write_sysfs("/sys/kernel/fpsgo/common/force_onoff", "1", false);
    write_sysfs("/sys/kernel/ged/hal/custom_boost_gpu_freq", "0", false);
    write_sysfs("/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled", "1", false);
    write_sysfs("/sys/class/misc/mali0/device/power_policy", "coarse_demand", false);
    write_sysfs("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "simple_ondemand", false);
    write_sysfs("/proc/gpufreqv2/fix_target_opp_index", "-1", false);
}

pub fn apply_mtk_eco() {
    if !is_mtk_device() {
        return;
    }

    log!("[MTK] Applying Eco Profile");
    write_sysfs("/sys/kernel/fpsgo/common/force_onoff", "1", false);
    write_sysfs("/sys/kernel/ged/hal/custom_boost_gpu_freq", "0", false);
    write_sysfs("/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled", "0", false);
    write_sysfs("/sys/class/misc/mali0/device/power_policy", "coarse_demand", false);
    write_sysfs("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "powersave", false);
}
