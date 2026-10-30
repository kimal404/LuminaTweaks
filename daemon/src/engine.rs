use std::fs;
use std::path::Path;
use std::process::Command;

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

pub fn is_disable_thermal_enabled() -> bool {
    if let Ok(val) = fs::read_to_string("/data/adb/luminatweaks/disable_thermal.txt") {
        return val.trim() == "1";
    }
    let prop = Command::new("getprop")
        .arg("persist.lumina.disable_thermal")
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default();
    prop == "1"
}

fn apply_thermal_kill(disable: bool) {
    if disable {
        log!("[THERMAL] Disabling system thermal throttling for Performance");
        let _ = Command::new("stop").arg("mi_thermald").status();
        let _ = Command::new("stop").arg("thermal-engine").status();
        let _ = Command::new("stop").arg("thermald").status();
        let _ = Command::new("stop").arg("thermal").status();
        for i in 0..64 {
            let path = format!("/sys/class/thermal/thermal_zone{}/mode", i);
            if Path::new(&path).exists() {
                write_sysfs(&path, "disabled", false);
            }
        }
    } else {
        log!("[THERMAL] Restoring system thermal management");
        let _ = Command::new("start").arg("mi_thermald").status();
        let _ = Command::new("start").arg("thermal-engine").status();
        let _ = Command::new("start").arg("thermald").status();
        let _ = Command::new("start").arg("thermal").status();
        for i in 0..64 {
            let path = format!("/sys/class/thermal/thermal_zone{}/mode", i);
            if Path::new(&path).exists() {
                write_sysfs(&path, "enabled", false);
            }
        }
    }
}

fn apply_universal_cpu(gov: &str, lock_gov: bool, min_clamp: bool) {
    for policy_idx in 0..16 {
        let p_str = format!("/sys/devices/system/cpu/cpufreq/policy{}", policy_idx);
        if !Path::new(&p_str).exists() {
            continue;
        }

        let gov_path = format!("{}/scaling_governor", p_str);
        let min_path = format!("{}/scaling_min_freq", p_str);
        let max_path = format!("{}/scaling_max_freq", p_str);
        let avail_path = format!("{}/scaling_available_frequencies", p_str);
        let hw_min_path = format!("{}/cpuinfo_min_freq", p_str);
        let hw_max_path = format!("{}/cpuinfo_max_freq", p_str);

        write_sysfs(&gov_path, gov, lock_gov);

        let hw_min = fs::read_to_string(&hw_min_path)
            .ok()
            .and_then(|s| s.trim().parse::<u64>().ok())
            .unwrap_or(300000);
        let hw_max = fs::read_to_string(&hw_max_path)
            .ok()
            .and_then(|s| s.trim().parse::<u64>().ok())
            .unwrap_or(2000000);

        if min_clamp {
            let mut freqs: Vec<u64> = fs::read_to_string(&avail_path)
                .unwrap_or_default()
                .split_whitespace()
                .filter_map(|s| s.parse::<u64>().ok())
                .collect();
            freqs.sort_unstable();

            let target_min = freqs
                .iter()
                .copied()
                .find(|&f| f >= 900000)
                .unwrap_or(hw_min);

            write_sysfs(&min_path, &target_min.to_string(), true);
            write_sysfs(&max_path, &hw_max.to_string(), true);
        } else {
            write_sysfs(&min_path, &hw_min.to_string(), false);
            write_sysfs(&max_path, &hw_max.to_string(), false);
        }
    }

    for cpu_idx in 0..16 {
        let c_str = format!("/sys/devices/system/cpu/cpu{}", cpu_idx);
        if !Path::new(&c_str).exists() {
            break;
        }
        let gov_path = format!("{}/cpufreq/scaling_governor", c_str);
        write_sysfs(&gov_path, gov, lock_gov);
    }

    let ra_val = if min_clamp { "1024" } else { "128" };
    for block in ["sda", "sdb", "sdc"] {
        let ra_path = format!("/sys/block/{}/queue/read_ahead_kb", block);
        write_sysfs(&ra_path, ra_val, min_clamp);
    }
    write_sysfs("/proc/sys/vm/vfs_cache_pressure", if min_clamp { "80" } else { "100" }, min_clamp);
}

pub fn performance_profile() {
    log!("[ENGINE] Applying Performance Profile");
    apply_universal_cpu("performance", true, true);
    if is_disable_thermal_enabled() {
        apply_thermal_kill(true);
    }
    crate::mediatek::apply_mtk_performance();
}

pub fn performance_lite_profile() {
    log!("[ENGINE] Applying Performance Lite Profile");
    apply_universal_cpu("schedutil", true, false);
    apply_thermal_kill(false);
    crate::mediatek::apply_mtk_balance();
}

pub fn balance_profile() {
    log!("[ENGINE] Restoring Balanced Profile");
    apply_universal_cpu("schedutil", false, false);
    apply_thermal_kill(false);
    crate::mediatek::apply_mtk_balance();
}

pub fn eco_profile() {
    log!("[ENGINE] Applying Eco Profile");
    apply_universal_cpu("powersave", false, false);
    apply_thermal_kill(false);
    crate::mediatek::apply_mtk_eco();
}
