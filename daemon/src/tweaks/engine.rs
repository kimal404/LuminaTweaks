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
use std::sync::mpsc::{self, RecvTimeoutError};
use std::sync::OnceLock;
use std::time::Duration;

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

/// Latest-wins mailbox for ZRAM reconfiguration. Reapplying ZRAM runs
/// `swapoff`/`mkswap`/`swapon`, which can take seconds; doing it on the main
/// event loop stalled the daemon for up to 14s in the field. Requests are
/// coalesced with a short quiet window so dragging a slider only applies the
/// final value once.
static ZRAM_TX: OnceLock<mpsc::Sender<(u32, String)>> = OnceLock::new();

fn zram_sender() -> &'static mpsc::Sender<(u32, String)> {
    ZRAM_TX.get_or_init(|| {
        let (tx, rx) = mpsc::channel::<(u32, String)>();
        let _ = std::thread::Builder::new()
            .name("zram-worker".to_string())
            .spawn(move || zram_worker(rx));
        tx
    })
}

fn zram_worker(rx: mpsc::Receiver<(u32, String)>) {
    while let Ok(first) = rx.recv() {
        let mut latest = first;
        loop {
            match rx.recv_timeout(Duration::from_millis(250)) {
                Ok(next) => latest = next,
                Err(RecvTimeoutError::Timeout) => break,
                Err(RecvTimeoutError::Disconnected) => {
                    if latest.0 > 0 {
                        apply_zram(latest.0, &latest.1);
                    }
                    return;
                }
            }
        }
        if latest.0 > 0 {
            apply_zram(latest.0, &latest.1);
        }
    }
}

/// Queues a ZRAM configuration for the background worker.
pub fn request_zram(size_gb: u32, algorithm: &str) {
    if size_gb == 0 {
        return;
    }
    let _ = zram_sender().send((size_gb, algorithm.to_string()));
}

fn read_swap_zram_device() -> Option<String> {
    let content = fs::read_to_string("/proc/swaps").ok()?;
    content.lines().skip(1).find_map(|line| {
        let device = line.split_whitespace().next()?;
        if device.contains("zram") {
            Some(device.to_string())
        } else {
            None
        }
    })
}

/// Finds the ZRAM block device dynamically (zram0, zram1, ...). The active
/// swap device wins; otherwise the lowest index is used.
fn zram_device() -> Option<(String, String)> {
    let mut candidates: Vec<(u32, String, String)> = Vec::new();
    if let Ok(entries) = fs::read_dir("/sys/block") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            let Some(index) = name
                .strip_prefix("zram")
                .and_then(|value| value.parse::<u32>().ok())
            else {
                continue;
            };
            let block = if Path::new(&format!("/dev/block/{}", name)).exists() {
                format!("/dev/block/{}", name)
            } else if Path::new(&format!("/dev/{}", name)).exists() {
                format!("/dev/{}", name)
            } else {
                continue;
            };
            candidates.push((index, format!("/sys/block/{}", name), block));
        }
    }
    candidates.sort_by_key(|(index, _, _)| *index);

    if let Some(active) = read_swap_zram_device() {
        if let Some((_, sysfs, block)) = candidates.iter().find(|(_, _, block)| {
            Path::new(block).file_name() == Path::new(&active).file_name()
        }) {
            return Some((sysfs.clone(), block.clone()));
        }
    }

    candidates
        .into_iter()
        .next()
        .map(|(_, sysfs, block)| (sysfs, block))
}

fn selected_algorithm(available: &str) -> Option<&str> {
    available
        .split_whitespace()
        .find(|entry| entry.starts_with('['))
        .map(|entry| entry.trim_matches(|c| c == '[' || c == ']'))
}

fn reset_with_retry(path: &str) -> bool {
    for attempt in 0..3 {
        if write_sysfs(path, "1", false) {
            return true;
        }
        if attempt < 2 {
            std::thread::sleep(Duration::from_millis(150));
        }
    }
    false
}

pub fn apply_zram(size_gb: u32, algorithm: &str) {
    if size_gb == 0 {
        return;
    }
    let Some((sysfs, zram_block)) = zram_device() else {
        log!("[ZRAM] No zram device found on system");
        return;
    };

    let target_disksize_bytes = (size_gb as u64) * 1024 * 1024 * 1024;
    let comp_path = format!("{}/comp_algorithm", sysfs);
    let available = fs::read_to_string(&comp_path).unwrap_or_default();
    let requested = algorithm.trim();

    // Validate the compressor before touching the device so an unsupported
    // algorithm cannot leave ZRAM reset without swap.
    let wanted_algo = if requested.is_empty() {
        None
    } else if available
        .split_whitespace()
        .any(|entry| entry.trim_matches(|c| c == '[' || c == ']') == requested)
    {
        Some(requested)
    } else {
        log!(
            "[ZRAM] Compression '{}' unavailable on {}; keeping device default",
            requested,
            sysfs
        );
        None
    };

    let current_algo_ok = wanted_algo
        .map(|wanted| selected_algorithm(&available) == Some(wanted))
        .unwrap_or(true);
    let current_disksize_bytes = fs::read_to_string(format!("{}/disksize", sysfs))
        .unwrap_or_default()
        .trim()
        .parse::<u64>()
        .unwrap_or(0);

    if current_algo_ok && current_disksize_bytes == target_disksize_bytes {
        log!(
            "[ZRAM] Target config already active ({}GB, {})",
            size_gb,
            wanted_algo.unwrap_or("device default")
        );
        return;
    }

    log!(
        "[ZRAM] Applying config: {}GB, compression: {}",
        size_gb,
        wanted_algo.unwrap_or("device default")
    );

    // reset returns EBUSY while the device is still an active swap device, so
    // swapoff must succeed first (and it is only attempted when active).
    if read_swap_zram_device()
        .map(|active| {
            Path::new(&active).file_name() == Path::new(&zram_block).file_name()
        })
        .unwrap_or(false)
    {
        let off = Command::new("swapoff")
            .arg(&zram_block)
            .status()
            .map(|status| status.success())
            .unwrap_or(false);
        if !off {
            log!("[ZRAM] swapoff failed for {}; aborting", zram_block);
            return;
        }
    }

    if !reset_with_retry(&format!("{}/reset", sysfs)) {
        log!("[ZRAM] Failed to reset {}; aborting", sysfs);
        return;
    }

    if let Some(algo) = wanted_algo {
        if !write_sysfs(&comp_path, algo, false) {
            log!("[ZRAM] Failed to set compression '{}'", algo);
        }
    }

    let size_str = format!("{}", target_disksize_bytes);
    if !write_sysfs(&format!("{}/disksize", sysfs), &size_str, false) {
        log!(
            "[ZRAM] Failed to set size to {} bytes; aborting",
            target_disksize_bytes
        );
        return;
    }

    let formatted = Command::new("mkswap")
        .arg(&zram_block)
        .status()
        .map(|status| status.success())
        .unwrap_or(false);
    if !formatted {
        log!("[ZRAM] mkswap failed for {}; aborting", zram_block);
        return;
    }

    let swapon = Command::new("swapon")
        .args([zram_block.as_str(), "-p", "32767"])
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
        wanted_algo.unwrap_or("device default")
    );
}

pub fn apply_swappiness(val: u32) {
    let s = val.clamp(0, 200).to_string();
    if fs::read_to_string("/proc/sys/vm/swappiness")
        .map(|current| current.trim() == s)
        .unwrap_or(false)
    {
        return;
    }
    write_sysfs("/proc/sys/vm/swappiness", &s, false);
    log!("[VM] Swappiness set to {}", s);
}

pub fn apply_io_scheduler(scheduler: &str) {
    if scheduler.is_empty() {
        return;
    }
    let mut applied_count = 0;
    let mut already_set = 0;
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
            if !Path::new(&sched_path).exists() {
                continue;
            }
            let current = fs::read_to_string(&sched_path).unwrap_or_default();
            if current.contains(&format!("[{}]", scheduler)) {
                already_set += 1;
                continue;
            }
            if write_sysfs(&sched_path, scheduler, false) {
                applied_count += 1;
            }
        }
    }
    if applied_count > 0 {
        log!(
            "[IO] Applied scheduler '{}' across {} block device(s) ({} already set)",
            scheduler,
            applied_count,
            already_set
        );
    }
}

pub fn sync_global_tweaks() {
    let (zram_size, zram_algo, swappiness) = crate::core::detector::get_global_zram_config();
    if let (Some(size), Some(algo)) = (zram_size, zram_algo) {
        request_zram(size, &algo);
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
                    crate::tweaks::state::write_tracked(
                        &min_path,
                        &hw_max.to_string(),
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
    if let Ok(entries) = fs::read_dir("/sys/block") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.starts_with("loop") || name.starts_with("ram") {
                continue;
            }
            let ra_path = format!("/sys/block/{}/queue/read_ahead_kb", name);
            crate::tweaks::state::write_tracked(&ra_path, ra_val, min_clamp);
        }
    }
    crate::tweaks::state::write_tracked(
        "/proc/sys/vm/vfs_cache_pressure",
        if min_clamp { "80" } else { "100" },
        min_clamp,
    );
}

pub fn performance_profile() {
    crate::tweaks::sched::apply_performance();
    crate::tweaks::thermal::apply("performance");
    apply_universal_cpu("performance", true, true, None);
    soc::apply(SocProfile::Performance);
    soc::optimize_storage(SocProfile::Performance);
}

pub fn performance_lite_profile() {
    crate::tweaks::sched::apply_balance();
    crate::tweaks::thermal::apply("lite");
    apply_universal_cpu("schedutil", true, false, Some(72));
    tune_schedutil_rate_limits(500, 20000);
    soc::apply(SocProfile::Lite);
    soc::optimize_storage(SocProfile::Lite);
}

pub fn balance_profile() {
    crate::tweaks::state::restore_all();
}

pub fn restore_balance() {
    crate::tweaks::state::restore_all();
}

pub fn eco_profile() {
    crate::tweaks::sched::apply_eco();
    crate::tweaks::thermal::apply("eco");
    apply_universal_cpu("powersave", false, false, None);
    tune_schedutil_rate_limits(4000, 8000);
    soc::apply(SocProfile::Eco);
}

#[cfg(test)]
mod tests {
    use super::selected_algorithm;

    #[test]
    fn selects_bracketed_compression_algorithm() {
        assert_eq!(
            selected_algorithm("lzo lzo-rle lz4 [lz4hc] zstd"),
            Some("lz4hc")
        );
        assert_eq!(selected_algorithm("lzo lz4 zstd"), None);
    }
}
