use crate::tweaks::soc::{self, SocProfile};
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::process::Command;
use std::sync::mpsc::{self, RecvTimeoutError};
use std::sync::{Mutex, OnceLock};
use std::time::Duration;

const BASELINE_GOVERNOR_PATH: &str = "/data/adb/.config/lumina/baseline_governor";
const BOOT_BACKUP_PATH: &str = "/data/adb/.config/lumina/boot_backup.json";
const PPM_MAX_PATH: &str = "/proc/ppm/policy/hard_userlimit_max_cpu_freq";
const PPM_MIN_PATH: &str = "/proc/ppm/policy/hard_userlimit_min_cpu_freq";

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

fn profile_write(path: &str, value: &str) -> bool {
    crate::tweaks::state::write_tracked(path, value, false)
}

fn profile_apply(path: &str, value: &str) -> bool {
    crate::tweaks::state::write_tracked(path, value, true)
}

fn read_u64(path: &str) -> Option<u64> {
    fs::read_to_string(path)
        .ok()
        .and_then(|value| value.trim().parse::<u64>().ok())
}

fn read_string(path: &str) -> Option<String> {
    fs::read_to_string(path)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
}

fn cpu_frequencies(base: &str) -> Vec<u64> {
    fs::read_to_string(format!("{}/scaling_available_frequencies", base))
        .unwrap_or_default()
        .split_whitespace()
        .filter_map(|value| value.parse::<u64>().ok())
        .collect()
}

fn hardware_limits(base: &str) -> Option<(u64, u64, Vec<u64>)> {
    let freqs = cpu_frequencies(base);
    let hw_min = read_u64(&format!("{}/cpuinfo_min_freq", base))
        .or_else(|| crate::tweaks::devfreq::min_frequency(&freqs))?;
    let hw_max = read_u64(&format!("{}/cpuinfo_max_freq", base))
        .or_else(|| crate::tweaks::devfreq::max_frequency(&freqs))?;
    Some((hw_min, hw_max, freqs))
}

fn cpu_policy_dirs() -> Vec<String> {
    let mut dirs = Vec::new();
    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.starts_with("policy") {
                continue;
            }
            let base = format!("/sys/devices/system/cpu/cpufreq/{}", name);
            if Path::new(&format!("{}/cpuinfo_max_freq", base)).exists()
                || Path::new(&format!("{}/scaling_governor", base)).exists()
            {
                dirs.push(base);
            }
        }
    }

    if dirs.is_empty() {
        if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().into_owned();
                if !name.starts_with("cpu") || !name[3..].chars().all(|c| c.is_ascii_digit()) {
                    continue;
                }
                let base = format!("/sys/devices/system/cpu/{}/cpufreq", name);
                if Path::new(&base).join("scaling_governor").exists() {
                    dirs.push(base);
                }
            }
        }
    }

    dirs.sort();
    dirs.dedup();
    dirs
}

fn cpu_governor_paths() -> Vec<String> {
    let mut paths: Vec<String> = cpu_policy_dirs()
        .into_iter()
        .map(|base| format!("{}/scaling_governor", base))
        .collect();

    if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.starts_with("cpu") || !name[3..].chars().all(|c| c.is_ascii_digit()) {
                continue;
            }
            let path = format!(
                "/sys/devices/system/cpu/{}/cpufreq/scaling_governor",
                name
            );
            if Path::new(&path).exists() && !paths.contains(&path) {
                paths.push(path);
            }
        }
    }

    paths.retain(|path| Path::new(path).exists());
    paths
}

fn fallback_governor() -> String {
    for base in cpu_policy_dirs() {
        let available = fs::read_to_string(format!("{}/scaling_available_governors", base))
            .unwrap_or_default();
        for preferred in ["schedutil", "walt", "interactive", "ondemand", "conservative"] {
            if available.split_whitespace().any(|item| item == preferred) {
                return preferred.to_string();
            }
        }
        if let Some(other) = available
            .split_whitespace()
            .find(|item| *item != "performance")
        {
            return other.to_string();
        }
    }
    "schedutil".to_string()
}

fn governor_cache() -> &'static Mutex<Option<String>> {
    static CACHE: OnceLock<Mutex<Option<String>>> = OnceLock::new();
    CACHE.get_or_init(|| Mutex::new(None))
}

pub fn init_baseline_governor() {
    {
        let guard = governor_cache()
            .lock()
            .unwrap_or_else(|error| error.into_inner());
        if guard.is_some() {
            return;
        }
    }

    let stored = read_string(BASELINE_GOVERNOR_PATH);
    let governor = stored.unwrap_or_else(|| {
        let current = cpu_policy_dirs()
            .first()
            .and_then(|base| read_string(&format!("{}/scaling_governor", base)))
            .unwrap_or_else(|| "schedutil".to_string());
        if current == "performance" {
            fallback_governor()
        } else {
            current
        }
    });

    if !Path::new(BASELINE_GOVERNOR_PATH).exists() {
        if let Some(parent) = Path::new(BASELINE_GOVERNOR_PATH).parent() {
            let _ = fs::create_dir_all(parent);
        }
        let _ = fs::write(BASELINE_GOVERNOR_PATH, &governor);
    }

    let mut guard = governor_cache()
        .lock()
        .unwrap_or_else(|error| error.into_inner());
    *guard = Some(governor);
}

fn default_governor() -> String {
    init_baseline_governor();
    let guard = governor_cache()
        .lock()
        .unwrap_or_else(|error| error.into_inner());
    guard
        .as_ref()
        .cloned()
        .unwrap_or_else(|| "schedutil".to_string())
}

fn change_cpu_governor(governor: &str) {
    if governor.is_empty() {
        return;
    }
    for path in cpu_governor_paths() {
        profile_write(&path, governor);
    }
}

fn cpufreq_max_perf(lite: bool) {
    for base in cpu_policy_dirs() {
        let Some((hw_min, hw_max, freqs)) = hardware_limits(&base) else {
            continue;
        };
        let min_target = if lite {
            crate::tweaks::devfreq::mid_frequency(&freqs).unwrap_or(hw_min)
        } else {
            hw_max
        };
        profile_apply(
            &format!("{}/scaling_max_freq", base),
            &hw_max.to_string(),
        );
        profile_apply(
            &format!("{}/scaling_min_freq", base),
            &min_target.to_string(),
        );
    }
}

fn cpufreq_ppm_max_perf(lite: bool) {
    for (cluster, base) in cpu_policy_dirs().iter().enumerate() {
        let Some((hw_min, hw_max, freqs)) = hardware_limits(base) else {
            continue;
        };
        profile_write(PPM_MAX_PATH, &format!("{} {}", cluster, hw_max));
        let min_target = if lite {
            crate::tweaks::devfreq::mid_frequency(&freqs).unwrap_or(hw_min)
        } else {
            hw_max
        };
        profile_write(PPM_MIN_PATH, &format!("{} {}", cluster, min_target));
    }
}

fn cpufreq_unlock() {
    for base in cpu_policy_dirs() {
        let Some((hw_min, hw_max, _)) = hardware_limits(&base) else {
            continue;
        };
        profile_write(
            &format!("{}/scaling_max_freq", base),
            &hw_max.to_string(),
        );
        profile_write(
            &format!("{}/scaling_min_freq", base),
            &hw_min.to_string(),
        );
    }
}

fn cpufreq_ppm_unlock() {
    for (cluster, base) in cpu_policy_dirs().iter().enumerate() {
        let Some((hw_min, hw_max, _)) = hardware_limits(base) else {
            continue;
        };
        profile_write(PPM_MAX_PATH, &format!("{} {}", cluster, hw_max));
        profile_write(PPM_MIN_PATH, &format!("{} {}", cluster, hw_min));
    }
}

fn ppm_available() -> bool {
    Path::new("/proc/ppm").is_dir()
}

fn apply_battery_saver(enabled: bool) {
    let path = "/sys/module/battery_saver/parameters/enabled";
    let Some(content) = read_string(path) else {
        return;
    };
    let numeric = content.chars().any(|value| value.is_ascii_digit());
    let value = if numeric {
        if enabled { "1" } else { "0" }
    } else if enabled {
        "Y"
    } else {
        "N"
    };
    profile_apply(path, value);
}

fn apply_vfs_cache_pressure(value: &str) {
    profile_apply("/proc/sys/vm/vfs_cache_pressure", value);
}

fn apply_io_latency(performance: bool) {
    let (read_ahead, nr_requests) = if performance {
        ("32", "32")
    } else {
        ("128", "64")
    };

    let Ok(entries) = fs::read_dir("/sys/block") else {
        return;
    };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.starts_with("loop")
            || name.starts_with("zram")
            || name.starts_with("ram")
            || name.starts_with("dm-")
        {
            continue;
        }
        let base = format!("/sys/block/{}", name);
        profile_apply(
            &format!("{}/queue/read_ahead_kb", base),
            read_ahead,
        );
        profile_apply(&format!("{}/queue/nr_requests", base), nr_requests);
    }
}

fn drop_caches() {
    let _ = fs::write("/proc/sys/vm/drop_caches", b"3");
}

pub fn performance_profile() {
    apply_battery_saver(false);
    crate::tweaks::sched::apply_performance();
    apply_vfs_cache_pressure("80");
    change_cpu_governor("performance");
    if ppm_available() {
        cpufreq_ppm_max_perf(false);
    } else {
        cpufreq_max_perf(false);
    }
    apply_io_latency(true);
    soc::apply(SocProfile::Performance);
    drop_caches();
}

pub fn performance_lite_profile() {
    apply_battery_saver(false);
    crate::tweaks::sched::apply_performance();
    apply_vfs_cache_pressure("80");
    change_cpu_governor(&default_governor());
    if ppm_available() {
        cpufreq_ppm_max_perf(true);
    } else {
        cpufreq_max_perf(true);
    }
    apply_io_latency(true);
    soc::apply(SocProfile::Lite);
    drop_caches();
}

pub fn balance_profile() {
    crate::tweaks::state::restore_all();
    apply_battery_saver(false);
    crate::tweaks::sched::apply_balance();
    apply_vfs_cache_pressure("120");
    change_cpu_governor(&default_governor());
    if ppm_available() {
        cpufreq_ppm_unlock();
    } else {
        cpufreq_unlock();
    }
    apply_io_latency(false);
    soc::apply(SocProfile::Balance);
    crate::tweaks::state::discard();
}

pub fn restore_balance() {
    balance_profile();
}

pub fn eco_profile() {
    balance_profile();
    crate::tweaks::sched::apply_powersave();
    apply_battery_saver(true);
    change_cpu_governor(&default_governor());
    soc::apply(SocProfile::Eco);
}

fn block_paths() -> Vec<String> {
    let mut paths = Vec::new();
    let Ok(entries) = fs::read_dir("/sys/block") else {
        return paths;
    };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.starts_with("loop") || name.starts_with("ram") {
            continue;
        }
        let base = format!("/sys/block/{}", name);
        paths.push(format!("{}/queue/iostats", base));
        paths.push(format!("{}/queue/add_random", base));
    }
    paths
}

fn thermal_paths() -> Vec<String> {
    let mut paths = Vec::new();
    for dir in ["/sys/class/thermal", "/sys/devices/virtual/thermal"] {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.starts_with("thermal_zone") {
                paths.push(format!("{}/{}/policy", dir, name));
            }
        }
    }
    paths
}

fn boot_tuned_paths() -> Vec<String> {
    let mut paths: Vec<String> = [
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
        "/proc/sys/kernel/sched_lib_name",
        "/proc/sys/kernel/sched_lib_mask_force",
        "/proc/sys/vm/page-cluster",
        "/proc/sys/vm/compaction_proactiveness",
        "/proc/sys/vm/stat_interval",
        "/sys/module/mmc_core/parameters/use_spi_crc",
        "/sys/module/opchain/parameters/chain_on",
        "/sys/module/cpufreq_bouncing/parameters/enable",
        "/proc/task_info/task_sched_info/task_sched_info_enable",
        "/proc/oplus_scheduler/sched_assist/sched_assist_enabled",
    ]
    .iter()
    .map(|path| (*path).to_string())
    .collect();

    paths.extend(block_paths());
    paths.extend(thermal_paths());
    paths
}

fn snapshot_boot_settings() -> serde_json::Map<String, serde_json::Value> {
    let mut map = serde_json::Map::new();
    for path in boot_tuned_paths() {
        if map.contains_key(&path) {
            continue;
        }
        if let Ok(value) = fs::read_to_string(&path) {
            map.insert(path, serde_json::Value::String(value));
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

    let _ = Command::new("sync").status();

    let mut disk_count = 0;
    for path in block_paths() {
        if write_sysfs(&path, "0", false) {
            disk_count += 1;
        }
    }
    log!(
        "[BOOT] I/O Queue: Disabled iostats & add_random on {} node(s)",
        disk_count
    );

    let avail_cc = fs::read_to_string("/proc/sys/net/ipv4/tcp_available_congestion_control")
        .unwrap_or_default();
    for algo in ["bbr3", "bbr2", "bbrplus", "bbr", "westwood", "cubic"] {
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

    let game_libs = "libunity.so, libil2cpp.so, libmain.so, libUE4.so, libgodot_android.so, libgdx.so, libgdx-box2d.so, libminecraftpe.so, libLive2DCubismCore.so, libyuzu-android.so, libryujinx.so, libcitra-android.so, libhdr_pro_engine.so, libandroidx.graphics.path.so, libeffect.so";
    write_sysfs("/proc/sys/kernel/sched_lib_name", game_libs, false);
    write_sysfs("/proc/sys/kernel/sched_lib_mask_force", "255", false);

    write_sysfs("/proc/sys/vm/page-cluster", "0", false);
    write_sysfs("/proc/sys/vm/compaction_proactiveness", "0", false);
    write_sysfs("/proc/sys/vm/stat_interval", "15", false);
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

    let zones = crate::tweaks::thermal::apply_kernel_governor();
    log!("[BOOT] Thermal governor step_wise on {} zone(s)", zones);
    log!("[BOOT] Encore perfcommon profile applied");
}

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
