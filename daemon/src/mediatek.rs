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

    let has_mtk_exclusive_node = Path::new("/sys/kernel/fpsgo/common/force_onoff").exists()
        || Path::new("/sys/module/fpsgo").exists()
        || Path::new("/sys/kernel/ged/hal").exists()
        || Path::new("/proc/gpufreqv2").exists();

    is_mtk_prop || has_mtk_exclusive_node
}

fn read_flag(path: &str) -> Option<String> {
    fs::read_to_string(path).ok().map(|s| s.trim().to_string())
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
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o644));
    let res = match fs::write(path, val.as_bytes()) {
        Ok(_) => true,
        Err(e) => {
            log!("[WARN] Failed to write '{}' to {}: {}", val, path, e);
            false
        }
    };
    let mode = if lock { 0o444 } else { 0o644 };
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(mode));
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
