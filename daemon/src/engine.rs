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

pub fn apply_boot_profile() {
    log!("[BOOT] Executing universal post-boot & post-flash baseline profile...");

    if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
        let mut tuned_tz = 0;
        let mut skipped_tz = 0;
        for entry in entries.flatten() {
            let fname = entry.file_name().to_string_lossy().into_owned();
            if !fname.starts_with("thermal_zone") {
                continue;
            }

            let policy_path = format!("/sys/class/thermal/{}/policy", fname);
            let avail_path = format!("/sys/class/thermal/{}/available_policies", fname);
            let avail = fs::read_to_string(&avail_path).unwrap_or_default();

            if avail.split_whitespace().any(|p| p == "step_wise") {
                if write_sysfs(&policy_path, "step_wise", true) {
                    tuned_tz += 1;
                }
            } else {
                skipped_tz += 1;
            }
        }
        log!("[BOOT] Thermal: Locked {} zone(s) to step_wise ({} skipped/unsupported)", tuned_tz, skipped_tz);
    }

    write_sysfs("/proc/sys/kernel/panic", "0", false);
    write_sysfs("/proc/sys/kernel/panic_on_oops", "0", false);
    write_sysfs("/proc/sys/kernel/panic_on_warn", "0", false);
    write_sysfs("/proc/sys/kernel/softlockup_panic", "0", false);

    if let Ok(entries) = fs::read_dir("/sys/block") {
        let mut disk_count = 0;
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.starts_with("loop") || name.starts_with("ram") {
                continue;
            }
            let iostats_path = format!("/sys/block/{}/queue/iostats", name);
            let add_random_path = format!("/sys/block/{}/queue/add_random", name);

            write_sysfs(&iostats_path, "0", false);
            write_sysfs(&add_random_path, "0", false);
            disk_count += 1;
        }
        log!("[BOOT] I/O Queue: Disabled iostats & add_random across {} device(s)", disk_count);
    }

    let avail_cc = fs::read_to_string("/proc/sys/net/ipv4/tcp_available_congestion_control").unwrap_or_default();
    let cc_candidates = ["bbr3", "bbr2", "bbrplus", "bbr", "westwood", "cubic"];
    for algo in cc_candidates {
        if avail_cc.contains(algo) {
            write_sysfs("/proc/sys/net/ipv4/tcp_congestion_control", algo, false);
            log!("[BOOT] TCP Congestion Control selected: {}", algo);
            break;
        }
    }

    write_sysfs("/proc/sys/net/ipv4/tcp_low_latency", "1", false);
    write_sysfs("/proc/sys/net/ipv4/tcp_ecn", "1", false);
    write_sysfs("/proc/sys/net/ipv4/tcp_fastopen", "3", false);
    write_sysfs("/proc/sys/net/ipv4/tcp_sack", "1", false);
    write_sysfs("/proc/sys/net/ipv4/tcp_timestamps", "0", false);

    write_sysfs("/proc/sys/kernel/perf_cpu_time_max_percent", "3", false);
    write_sysfs("/proc/sys/kernel/sched_schedstats", "0", false);
    write_sysfs("/proc/sys/kernel/task_cpustats_enable", "0", false);
    write_sysfs("/proc/sys/kernel/sched_autogroup_enabled", "0", false);
    write_sysfs("/proc/sys/kernel/sched_child_runs_first", "1", false);
    write_sysfs("/proc/sys/kernel/sched_nr_migrate", "32", false);
    write_sysfs("/proc/sys/kernel/sched_migration_cost_ns", "50000", false);
    write_sysfs("/proc/sys/kernel/sched_min_granularity_ns", "1000000", false);
    write_sysfs("/proc/sys/kernel/sched_wakeup_granularity_ns", "1500000", false);
    write_sysfs("/proc/sys/kernel/printk", "0 0 0 0", false);
    write_sysfs("/sys/kernel/debug/tracing/tracing_on", "0", false);

    let game_libs = "libunity.so, libil2cpp.so, libmain.so, libUE4.so, libgodot_android.so, libgdx.so, libgdx-box2d.so, libminecraftpe.so, libLive2DCubismCore.so, libyuzu-android.so, libryujinx.so, libcitra-android.so, libhdr_pro_engine.so, libandroidx.graphics.path.so, libeffect.so";
    write_sysfs("/proc/sys/kernel/sched_lib_name", game_libs, false);
    write_sysfs("/proc/sys/kernel/sched_lib_mask_force", "255", false);

    write_sysfs("/proc/sys/vm/page-cluster", "0", false);
    write_sysfs("/proc/sys/vm/compaction_proactiveness", "0", false);
    write_sysfs("/proc/sys/vm/stat_interval", "15", false);
    write_sysfs("/proc/sys/vm/dirty_ratio", "10", false);
    write_sysfs("/proc/sys/vm/dirty_background_ratio", "5", false);
    write_sysfs("/sys/module/mmc_core/parameters/use_spi_crc", "0", false);

    write_sysfs("/sys/module/opchain/parameters/chain_on", "0", false);
    write_sysfs("/sys/module/cpufreq_bouncing/parameters/enable", "0", false);
    write_sysfs("/proc/task_info/task_sched_info/task_sched_info_enable", "0", false);
    write_sysfs("/proc/oplus_scheduler/sched_assist/sched_assist_enabled", "0", false);

    std::thread::spawn(|| {
        let _ = Command::new("sync").status();
        let _ = Command::new("fstrim").arg("/data").output();
        log!("[BOOT] Storage maintenance (/data fstrim) completed in background");
    });

    log!("[BOOT] Universal Boot Profile applied successfully");
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
