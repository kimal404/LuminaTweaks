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

use crate::tweaks::soc::{self, SocProfile};
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::process::Command;

pub fn get_zen_mode() -> String {
    match Command::new("settings")
        .args(["get", "global", "zen_mode"])
        .output()
    {
        Ok(o) => {
            let val = String::from_utf8_lossy(&o.stdout).trim().to_string();
            if !val.is_empty() && val.chars().all(|c| c.is_ascii_digit()) {
                val
            } else {
                "0".to_string()
            }
        }
        Err(_) => "0".to_string(),
    }
}

pub fn zen_to_dnd_arg(zen_mode: &str) -> &'static str {
    match zen_mode {
        "1" => "priority",
        "2" => "none",
        "3" => "alarms",
        _ => "off",
    }
}

pub fn set_dnd_mode(mode: &str) {
    let _ = Command::new("cmd")
        .args(["notification", "set_dnd", mode])
        .status();
}

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

const BOOT_BACKUP_PATH: &str = "/data/adb/.config/lumina/boot_backup.json";

const BOOT_TUNED_PATHS: &[&str] = &[
    "/proc/sys/kernel/panic",
    "/proc/sys/kernel/panic_on_oops",
    "/proc/sys/kernel/panic_on_warn",
    "/proc/sys/kernel/softlockup_panic",
    "/proc/sys/net/ipv4/tcp_congestion_control",
    "/proc/sys/net/ipv4/tcp_low_latency",
    "/proc/sys/net/ipv4/tcp_ecn",
    "/proc/sys/net/ipv4/tcp_fastopen",
    "/proc/sys/net/ipv4/tcp_sack",
    "/proc/sys/net/ipv4/tcp_timestamps",
    "/proc/sys/kernel/perf_cpu_time_max_percent",
    "/proc/sys/kernel/sched_schedstats",
    "/proc/sys/kernel/task_cpustats_enable",
    "/proc/sys/kernel/sched_autogroup_enabled",
    "/proc/sys/kernel/sched_child_runs_first",
    "/proc/sys/kernel/sched_nr_migrate",
    "/proc/sys/kernel/sched_migration_cost_ns",
    "/proc/sys/kernel/sched_min_granularity_ns",
    "/proc/sys/kernel/sched_wakeup_granularity_ns",
    "/proc/sys/kernel/printk",
    "/sys/kernel/debug/tracing/tracing_on",
    "/proc/sys/kernel/sched_lib_name",
    "/proc/sys/kernel/sched_lib_mask_force",
    "/proc/sys/vm/page-cluster",
    "/proc/sys/vm/compaction_proactiveness",
    "/proc/sys/vm/stat_interval",
    "/proc/sys/vm/dirty_ratio",
    "/proc/sys/vm/dirty_background_ratio",
    "/sys/module/mmc_core/parameters/use_spi_crc",
    "/sys/module/opchain/parameters/chain_on",
    "/sys/module/cpufreq_bouncing/parameters/enable",
    "/proc/task_info/task_sched_info/task_sched_info_enable",
    "/proc/oplus_scheduler/sched_assist/sched_assist_enabled",
    "/sys/kernel/debug/sched_features",
    "/proc/sys/kernel/split_lock_mitigate",
];

fn snapshot_boot_settings() -> serde_json::Map<String, serde_json::Value> {
    let mut map = serde_json::Map::new();
    for path in BOOT_TUNED_PATHS {
        if map.contains_key(*path) {
            continue;
        }
        if let Ok(value) = fs::read_to_string(path) {
            map.insert((*path).to_string(), serde_json::Value::String(value));
        }
    }
    map
}

pub fn restore_boot_profile() {
    let Ok(content) = fs::read_to_string(BOOT_BACKUP_PATH) else {
        return;
    };
    let Ok(map) = serde_json::from_str::<serde_json::Map<String, serde_json::Value>>(&content)
    else {
        let _ = fs::remove_file(BOOT_BACKUP_PATH);
        return;
    };

    let mut restored = 0;
    for (path, value) in map {
        if let Some(value) = value.as_str() {
            if write_sysfs(&path, value, false) {
                restored += 1;
            }
        }
    }

    let _ = fs::remove_file(BOOT_BACKUP_PATH);
    log!("[BOOT] Restored {} original boot setting(s)", restored);
}

pub fn apply_boot_profile() {
    if !Path::new(BOOT_BACKUP_PATH).exists() {
        let snapshot = snapshot_boot_settings();
        if !snapshot.is_empty() {
            if let Some(parent) = Path::new(BOOT_BACKUP_PATH).parent() {
                let _ = fs::create_dir_all(parent);
            }
            let _ = fs::write(
                BOOT_BACKUP_PATH,
                serde_json::Value::Object(snapshot).to_string(),
            );
        }
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
        log!(
            "[BOOT] I/O Queue: Disabled iostats & add_random across {} device(s)",
            disk_count
        );
    }

    let avail_cc = fs::read_to_string("/proc/sys/net/ipv4/tcp_available_congestion_control")
        .unwrap_or_default();
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
    write_sysfs(
        "/proc/sys/kernel/sched_min_granularity_ns",
        "1000000",
        false,
    );
    write_sysfs(
        "/proc/sys/kernel/sched_wakeup_granularity_ns",
        "1500000",
        false,
    );
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
    write_sysfs(
        "/proc/task_info/task_sched_info/task_sched_info_enable",
        "0",
        false,
    );
    write_sysfs(
        "/proc/oplus_scheduler/sched_assist/sched_assist_enabled",
        "0",
        false,
    );

    if Path::new("/sys/kernel/debug/sched_features").exists() {
        write_sysfs("/sys/kernel/debug/sched_features", "NEXT_BUDDY", false);
        write_sysfs("/sys/kernel/debug/sched_features", "NO_TTWU_QUEUE", false);
    }

    write_sysfs("/proc/sys/kernel/split_lock_mitigate", "0", false);

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
        log!(
            "[ZRAM] Target config already active ({}GB, {})",
            size_gb,
            algorithm
        );
        return;
    }

    log!(
        "[ZRAM] Applying config: {}GB, compression: {}",
        size_gb,
        algorithm
    );

    let _ = Command::new("swapoff").arg(zram_block).status();
    if !write_sysfs("/sys/block/zram0/reset", "1", false) {
        log!("[ZRAM] Failed to reset {}; aborting", zram_block);
        return;
    }

    if !algorithm.is_empty()
        && !write_sysfs("/sys/block/zram0/comp_algorithm", algorithm, false)
    {
        log!(
            "[ZRAM] Compression '{}' unavailable; using device default",
            algorithm
        );
    }

    let size_str = format!("{}", target_disksize_bytes);
    if !write_sysfs("/sys/block/zram0/disksize", &size_str, false) {
        log!(
            "[ZRAM] Failed to set size to {} bytes; aborting",
            target_disksize_bytes
        );
        return;
    }

    let formatted = Command::new("mkswap")
        .arg(zram_block)
        .status()
        .map(|status| status.success())
        .unwrap_or(false);
    if !formatted {
        log!("[ZRAM] mkswap failed for {}; aborting", zram_block);
        return;
    }

    let swapon = Command::new("swapon")
        .args([zram_block, "-p", "32767"])
        .status()
        .map(|status| status.success())
        .unwrap_or(false);
    if !swapon {
        log!("[ZRAM] swapon failed for {}; aborting", zram_block);
        return;
    }

    log!(
        "[ZRAM] Successfully applied {}GB with {}",
        size_gb,
        algorithm
    );
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
            if name.starts_with("loop")
                || name.starts_with("zram")
                || name.starts_with("ram")
                || name.starts_with("dm-")
            {
                continue;
            }
            let sched_path = format!("/sys/block/{}/queue/scheduler", name);
            if Path::new(&sched_path).exists() && write_sysfs(&sched_path, scheduler, false) {
                applied_count += 1;
            }
        }
    }
    if applied_count > 0 {
        log!(
            "[IO] Applied scheduler '{}' across {} block device(s)",
            scheduler,
            applied_count
        );
    }
}

pub fn sync_global_tweaks() {
    let (zram_size, zram_algo, swappiness) = crate::core::detector::get_global_zram_config();
    if let (Some(size), Some(algo)) = (zram_size, zram_algo) {
        apply_zram(size, &algo);
    }
    if let Some(swap) = swappiness {
        apply_swappiness(swap);
    }
    if let Some(sched) = crate::core::detector::get_global_io_scheduler() {
        apply_io_scheduler(&sched);
    }
}

pub fn tune_schedutil_rate_limits(up_us: u64, down_us: u64) {
    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") {
        for entry in entries.flatten() {
            let fname = entry.file_name().to_string_lossy().into_owned();
            if fname.starts_with("policy") {
                let p_str = format!("/sys/devices/system/cpu/cpufreq/{}", fname);
                let up_path = format!("{}/schedutil/up_rate_limit_us", p_str);
                let down_path = format!("{}/schedutil/down_rate_limit_us", p_str);

                crate::tweaks::state::write_tracked(
                    &up_path,
                    &up_us.to_string(),
                    false,
                );
                crate::tweaks::state::write_tracked(
                    &down_path,
                    &down_us.to_string(),
                    false,
                );
            }
        }
    }

    crate::tweaks::state::write_tracked(
        "/sys/devices/system/cpu/cpufreq/schedutil/up_rate_limit_us",
        &up_us.to_string(),
        false,
    );
    crate::tweaks::state::write_tracked(
        "/sys/devices/system/cpu/cpufreq/schedutil/down_rate_limit_us",
        &down_us.to_string(),
        false,
    );
}

fn apply_universal_cpu(gov: &str, lock_gov: bool, min_clamp: bool, max_cap_ratio: Option<u64>) {
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

                crate::tweaks::state::write_tracked(&gov_path, gov, lock_gov);

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

                if min_clamp {
                    let mut sorted_freqs = freqs.clone();
                    sorted_freqs.sort_unstable();

                    let target_min = sorted_freqs
                        .iter()
                        .copied()
                        .find(|&f| f >= 900000)
                        .unwrap_or(hw_min);

                    crate::tweaks::state::write_tracked(
                        &min_path,
                        &target_min.to_string(),
                        false,
                    );
                    crate::tweaks::state::write_tracked(&max_path, &hw_max.to_string(), false);
                } else if let Some(pct) = max_cap_ratio {
                    let target_cap = (hw_max * pct) / 100;
                    let target_max = if !freqs.is_empty() {
                        freqs
                            .iter()
                            .copied()
                            .filter(|&f| f <= target_cap)
                            .max()
                            .unwrap_or_else(|| freqs.iter().copied().min().unwrap_or(target_cap))
                    } else {
                        target_cap
                    };
                    let target_max = target_max.max(hw_min);

                    crate::tweaks::state::write_tracked(
                        &min_path,
                        &hw_min.to_string(),
                        false,
                    );
                    crate::tweaks::state::write_tracked(
                        &max_path,
                        &target_max.to_string(),
                        true,
                    );
                    log!(
                        "[LITE] {}: Capped max freq to {} kHz (~{}% of {} kHz)",
                        fname,
                        target_max,
                        pct,
                        hw_max
                    );
                } else {
                    crate::tweaks::state::write_tracked(
                        &min_path,
                        &hw_min.to_string(),
                        false,
                    );
                    crate::tweaks::state::write_tracked(
                        &max_path,
                        &hw_max.to_string(),
                        false,
                    );
                }
            }
        }
    }

    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu") {
        for entry in entries.flatten() {
            let fname = entry.file_name().to_string_lossy().into_owned();
            if fname.starts_with("cpu") && fname[3..].chars().all(|c| c.is_ascii_digit()) {
                let gov_path =
                    format!("/sys/devices/system/cpu/{}/cpufreq/scaling_governor", fname);
                crate::tweaks::state::write_tracked(&gov_path, gov, lock_gov);
            }
        }
    }

    let ra_val = if min_clamp { "1024" } else { "128" };
    for block in ["sda", "sdb", "sdc", "mmcblk0", "dm-0"] {
        let ra_path = format!("/sys/block/{}/queue/read_ahead_kb", block);
        crate::tweaks::state::write_tracked(&ra_path, ra_val, min_clamp);
    }
    crate::tweaks::state::write_tracked(
        "/proc/sys/vm/vfs_cache_pressure",
        if min_clamp { "80" } else { "100" },
        min_clamp,
    );
}

pub fn performance_profile() {
    crate::tweaks::sched::apply_performance();
    apply_universal_cpu("performance", true, true, None);
    soc::apply(SocProfile::Performance);
}

pub fn performance_lite_profile() {
    crate::tweaks::sched::apply_balance();
    apply_universal_cpu("schedutil", true, false, Some(72));
    tune_schedutil_rate_limits(500, 20000);
    soc::apply(SocProfile::Lite);
}

pub fn balance_profile() {
    crate::tweaks::state::restore_all();
}

pub fn restore_balance() {
    crate::tweaks::state::restore_all();
}

pub fn eco_profile() {
    crate::tweaks::sched::apply_eco();
    apply_universal_cpu("powersave", false, false, None);
    tune_schedutil_rate_limits(4000, 8000);
    soc::apply(SocProfile::Eco);
}
