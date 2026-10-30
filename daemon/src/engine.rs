use std::fs;
use std::path::Path;
use std::process::Command;

fn write_sysfs(path: &str, val: &str, lock: bool) -> bool {
    if !Path::new(path).exists() {
        return false;
    }
    if lock {
        let _ = Command::new("chmod").args(["644", path]).status();
    }
    let res = fs::write(path, val.as_bytes()).is_ok();
    if lock {
        let _ = Command::new("chmod").args(["444", path]).status();
    }
    res
}

fn apply_universal_cpu(gov: &str, lock_gov: bool, min_clamp: bool) {
    let mut policy_idx = 0;
    loop {
        let p_str = format!("/sys/devices/system/cpu/cpufreq/policy{}", policy_idx);
        if !Path::new(&p_str).exists() {
            break;
        }

        let gov_path = format!("{}/scaling_governor", p_str);
        let min_path = format!("{}/scaling_min_freq", p_str);
        let max_path = format!("{}/scaling_max_freq", p_str);
        let avail_path = format!("{}/scaling_available_frequencies", p_str);

        write_sysfs(&gov_path, gov, lock_gov);

        if min_clamp {
            if let Ok(freqs) = fs::read_to_string(&avail_path) {
                let list: Vec<&str> = freqs.split_whitespace().collect();
                if let (Some(lowest), Some(highest)) = (list.first(), list.last()) {
                    let mid_idx = list.len() / 2;
                    let target_min = list.get(mid_idx).unwrap_or(lowest);
                    write_sysfs(&min_path, target_min, true);
                    write_sysfs(&max_path, highest, true);
                }
            }
        } else if let Ok(freqs) = fs::read_to_string(&avail_path) {
            let list: Vec<&str> = freqs.split_whitespace().collect();
            if let (Some(lowest), Some(highest)) = (list.first(), list.last()) {
                write_sysfs(&min_path, lowest, false);
                write_sysfs(&max_path, highest, false);
            }
        }

        policy_idx += 1;
    }

    // VM and Storage read ahead
    let ra_val = if min_clamp { "1024" } else { "128" };
    for block in ["sda", "sdb", "sdc"] {
        let ra_path = format!("/sys/block/{}/queue/read_ahead_kb", block);
        write_sysfs(&ra_path, ra_val, min_clamp);
    }
    write_sysfs("/proc/sys/vm/vfs_cache_pressure", if min_clamp { "80" } else { "100" }, min_clamp);
}

pub fn performance_profile() {
    log!("[ENGINE] Applying Performance Profile (Universal Governor: performance)");
    apply_universal_cpu("performance", true, true);
    crate::mediatek::apply_mtk_performance(false);
}

pub fn performance_lite_profile() {
    log!("[ENGINE] Applying Performance Lite Profile (Universal Governor: schedutil)");
    apply_universal_cpu("schedutil", true, false);
    crate::mediatek::apply_mtk_performance(true);
}

pub fn balance_profile() {
    log!("[ENGINE] Restoring Balanced Profile (Universal Governor: schedutil)");
    apply_universal_cpu("schedutil", false, false);
    crate::mediatek::apply_mtk_balance();
}

pub fn eco_profile() {
    log!("[ENGINE] Applying Eco Profile (Universal Powersave)");
    apply_universal_cpu("powersave", false, false);
    crate::mediatek::apply_mtk_eco();
}
