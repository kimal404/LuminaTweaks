/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::process::Command;

pub fn write_sysfs(path: &str, val: &str, lock: bool) -> bool {
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

pub fn apply_thermal_kill(disable: bool) {
    let mode_str = if disable { "disabled" } else { "enabled" };
    if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
        for entry in entries.flatten() {
            let fname = entry.file_name().to_string_lossy().into_owned();
            if fname.starts_with("thermal_zone") {
                let path = format!("/sys/class/thermal/{}/mode", fname);
                write_sysfs(&path, mode_str, false);
            }
        }
    }
}

pub fn apply_zram(size_gb: u32, algorithm: &str) {
    if size_gb == 0 {
        return;
    }
    let zram_block = if Path::new("/dev/block/zram0").exists() {
        "/dev/block/zram0"
    } else if Path::new("/dev/zram0").exists() {
        "/dev/zram0"
    } else {
        log!("[ZRAM] No zram device found on system");
        return;
    };

    let current_algo = fs::read_to_string("/sys/block/zram0/comp_algorithm").unwrap_or_default();
    let current_disksize_bytes = fs::read_to_string("/sys/block/zram0/disksize")
        .unwrap_or_default()
        .trim()
        .parse::<u64>()
        .unwrap_or(0);
    let target_disksize_bytes = (size_gb as u64) * 1024 * 1024 * 1024;

    let algo_matches = if !algorithm.is_empty() {
        current_algo.contains(&format!("[{}]", algorithm))
    } else {
        true
    };

    let size_matches = current_disksize_bytes == target_disksize_bytes;

    if algo_matches && size_matches {
        log!("[ZRAM] Target config already active ({}GB, {})", size_gb, algorithm);
        return;
    }

    log!("[ZRAM] Applying config: {}GB, compression: {}", size_gb, algorithm);

    let _ = Command::new("swapoff").arg(zram_block).status();
    write_sysfs("/sys/block/zram0/reset", "1", false);

    if !algorithm.is_empty() {
        write_sysfs("/sys/block/zram0/comp_algorithm", algorithm, false);
    }

    let size_str = format!("{}", target_disksize_bytes);
    write_sysfs("/sys/block/zram0/disksize", &size_str, false);

    let _ = Command::new("mkswap").arg(zram_block).status();
    let _ = Command::new("swapon").args([zram_block, "-p", "32767"]).status();

    log!("[ZRAM] Successfully applied {}GB with {}", size_gb, algorithm);
}

pub fn apply_swappiness(val: u32) {
    let s = val.clamp(0, 200).to_string();
    write_sysfs("/proc/sys/vm/swappiness", &s, false);
    log!("[VM] Swappiness set to {}", s);
}

pub fn apply_io_scheduler(scheduler: &str) {
    if scheduler.is_empty() {
        return;
    }
    let mut applied_count = 0;
    if let Ok(entries) = fs::read_dir("/sys/block") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.starts_with("loop") || name.starts_with("zram") || name.starts_with("ram") || name.starts_with("dm-") {
                continue;
            }
            let sched_path = format!("/sys/block/{}/queue/scheduler", name);
            if Path::new(&sched_path).exists() && write_sysfs(&sched_path, scheduler, false) {
                applied_count += 1;
            }
        }
    }
    if applied_count > 0 {
        log!("[IO] Applied scheduler '{}' across {} block device(s)", scheduler, applied_count);
    }
}

pub fn sync_global_tweaks() {
    let (zram_size, zram_algo, swappiness) = crate::detector::get_global_zram_config();
    if let (Some(size), Some(algo)) = (zram_size, zram_algo) {
        apply_zram(size, &algo);
    }
    if let Some(swap) = swappiness {
        apply_swappiness(swap);
    }
    if let Some(sched) = crate::detector::get_global_io_scheduler() {
        apply_io_scheduler(&sched);
    }
}

fn apply_universal_cpu(gov: &str, lock_gov: bool, min_clamp: bool) {
    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") {
        for entry in entries.flatten() {
            let fname = entry.file_name().to_string_lossy().into_owned();
            if fname.starts_with("policy") {
                let p_str = format!("/sys/devices/system/cpu/cpufreq/{}", fname);
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

                    write_sysfs(&min_path, &target_min.to_string(), false);
                    write_sysfs(&max_path, &hw_max.to_string(), false);
                } else {
                    write_sysfs(&min_path, &hw_min.to_string(), false);
                    write_sysfs(&max_path, &hw_max.to_string(), false);
                }
            }
        }
    }

    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu") {
        for entry in entries.flatten() {
            let fname = entry.file_name().to_string_lossy().into_owned();
            if fname.starts_with("cpu") && fname[3..].chars().all(|c| c.is_ascii_digit()) {
                let gov_path = format!("/sys/devices/system/cpu/{}/cpufreq/scaling_governor", fname);
                write_sysfs(&gov_path, gov, lock_gov);
            }
        }
    }

    let ra_val = if min_clamp { "1024" } else { "128" };
    for block in ["sda", "sdb", "sdc", "mmcblk0", "dm-0"] {
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
    crate::snapdragon::SnapdragonEngine::apply_performance();
    crate::unisoc::UnisocEngine::apply_performance();
}

pub fn performance_lite_profile() {
    log!("[ENGINE] Applying Performance Lite Profile (Vendor Chipset Tweaks Reset)");
    apply_universal_cpu("schedutil", true, false);
    apply_thermal_kill(false);

    crate::mediatek::apply_mtk_balance();
    crate::snapdragon::SnapdragonEngine::apply_balance();
    crate::unisoc::UnisocEngine::apply_balance();
}

pub fn balance_profile() {
    log!("[ENGINE] Restoring Balanced Profile");
    apply_universal_cpu("schedutil", false, false);
    apply_thermal_kill(false);

    crate::mediatek::apply_mtk_balance();
    crate::snapdragon::SnapdragonEngine::apply_balance();
    crate::unisoc::UnisocEngine::apply_balance();
}

pub fn restore_balance() {
    balance_profile();
}

pub fn eco_profile() {
    log!("[ENGINE] Applying Eco Profile");
    apply_universal_cpu("powersave", false, false);
    apply_thermal_kill(false);

    crate::mediatek::apply_mtk_eco();
    crate::snapdragon::SnapdragonEngine::apply_balance();
    crate::unisoc::UnisocEngine::apply_balance();
}
