use std::fs::{self, Permissions};
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::thread;

pub fn write_lock<P: AsRef<Path>, S: AsRef<[u8]>>(path: P, content: S) -> bool {
    let p = path.as_ref();
    if !p.exists() {
        return false;
    }

    let _ = fs::set_permissions(p, Permissions::from_mode(0o644));
    let success = fs::write(p, content.as_ref()).is_ok();
    let _ = fs::set_permissions(p, Permissions::from_mode(0o444));

    if success {
        crate::log!("[+] write_lock OK: {}", p.display());
    } else {
        crate::log!("[-] write_lock FAIL: {}", p.display());
    }
    success
}

pub fn write_unlock<P: AsRef<Path>, S: AsRef<[u8]>>(path: P, content: S) -> bool {
    let p = path.as_ref();
    if !p.exists() {
        return false;
    }

    let _ = fs::set_permissions(p, Permissions::from_mode(0o644));
    let success = fs::write(p, content.as_ref()).is_ok();

    if success {
        crate::log!("[+] write_unlock OK: {}", p.display());
    } else {
        crate::log!("[-] write_unlock FAIL: {}", p.display());
    }
    success
}

fn find_matching_nodes<P: AsRef<Path>, F>(base: P, matcher: F) -> Vec<PathBuf>
where
    F: Fn(&str) -> bool,
{
    let mut matches = Vec::new();
    if let Ok(entries) = fs::read_dir(base) {
        for entry in entries.flatten() {
            if let Ok(name) = entry.file_name().into_string() {
                if matcher(&name) {
                    matches.push(entry.path());
                }
            }
        }
    }
    matches
}

fn which_maxfreq<P: AsRef<Path>>(path: P) -> Option<u64> {
    fs::read_to_string(path).ok().and_then(|content| {
        content
            .split_whitespace()
            .filter_map(|val| val.parse::<u64>().ok())
            .max()
    })
}

fn which_minfreq<P: AsRef<Path>>(path: P) -> Option<u64> {
    fs::read_to_string(path).ok().and_then(|content| {
        content
            .split_whitespace()
            .filter_map(|val| val.parse::<u64>().ok())
            .min()
    })
}

pub fn get_soc_type() -> u8 {
    fs::read_to_string("/data/adb/luminatweaks/soc_recognition")
        .ok()
        .and_then(|s| s.trim().parse::<u8>().ok())
        .unwrap_or(0)
}

fn get_default_governor() -> &'static str {
    let sample = Path::new("/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors");
    if let Ok(govs) = fs::read_to_string(sample) {
        if govs.contains("schedutil") {
            return "schedutil";
        }
    }
    "interactive"
}

fn baseline_generic() {
    crate::log!("[*] Reverting Generic Baseline (Balance)...");
    let def_gov = get_default_governor();

    for policy in find_matching_nodes("/sys/devices/system/cpu/cpufreq", |s| s.starts_with("policy")) {
        write_unlock(policy.join("scaling_governor"), def_gov);
    }
    for cpu in find_matching_nodes("/sys/devices/system/cpu", |s| s.starts_with("cpu") && s[3..].chars().all(char::is_numeric)) {
        write_unlock(cpu.join("cpufreq/scaling_governor"), def_gov);
    }

    let has_ppm = Path::new("/proc/ppm").exists();
    let policies = find_matching_nodes("/sys/devices/system/cpu/cpufreq", |s| s.starts_with("policy"));

    if has_ppm {
        for policy in &policies {
            let policy_name = policy.file_name().unwrap_or_default().to_string_lossy();
            let cluster_id = policy_name.trim_start_matches("policy");

            if let Ok(min_freq) = fs::read_to_string(policy.join("cpuinfo_min_freq")) {
                let val = format!("{} {}", cluster_id, min_freq.trim());
                write_unlock("/proc/ppm/policy/hard_userlimit_min_cpu_freq", val);
            }
            if let Ok(max_freq) = fs::read_to_string(policy.join("cpuinfo_max_freq")) {
                let val = format!("{} {}", cluster_id, max_freq.trim());
                write_unlock("/proc/ppm/policy/hard_userlimit_max_cpu_freq", val);
            }
        }
    } else {
        for policy in &policies {
            if let Ok(min_freq) = fs::read_to_string(policy.join("cpuinfo_min_freq")) {
                write_unlock(policy.join("scaling_min_freq"), min_freq.trim());
            }
            if let Ok(max_freq) = fs::read_to_string(policy.join("cpuinfo_max_freq")) {
                write_unlock(policy.join("scaling_max_freq"), max_freq.trim());
            }
        }
        for cpu in find_matching_nodes("/sys/devices/system/cpu", |s| s.starts_with("cpu") && s[3..].chars().all(char::is_numeric)) {
            let cpufreq = cpu.join("cpufreq");
            if let Ok(min_freq) = fs::read_to_string(cpufreq.join("cpuinfo_min_freq")) {
                write_unlock(cpufreq.join("scaling_min_freq"), min_freq.trim());
            }
            if let Ok(max_freq) = fs::read_to_string(cpufreq.join("cpuinfo_max_freq")) {
                write_unlock(cpufreq.join("scaling_max_freq"), max_freq.trim());
            }
        }
    }

    let bsave_path = "/sys/module/battery_saver/parameters/enabled";
    if let Ok(content) = fs::read_to_string(bsave_path) {
        if content.trim().chars().any(|c| c.is_ascii_digit()) {
            write_unlock(bsave_path, "0");
        } else {
            write_unlock(bsave_path, "N");
        }
    }

    write_unlock("/sys/devices/system/cpu/eas/enable", "1");
    write_unlock("/sys/kernel/debug/sched_features", "NEXT_BUDDY");
    write_unlock("/sys/kernel/debug/sched_features", "TTWU_QUEUE");

    write_unlock("/proc/sys/vm/vfs_cache_pressure", "100");
    write_unlock("/proc/sys/kernel/split_lock_mitigate", "1");

    for stune in find_matching_nodes("/dev/stune", |_| true) {
        write_unlock(stune.join("schedtune.boost"), "0");
        write_unlock(stune.join("schedtune.sched_boost_enabled"), "0");
        write_unlock(stune.join("schedtune.prefer_idle"), "0");
        write_unlock(stune.join("schedtune.colocate"), "0");
    }

    let tp_path = Path::new("/proc/touchpanel");
    if tp_path.exists() {
        write_unlock(tp_path.join("game_switch_enable"), "0");
        write_unlock(tp_path.join("oplus_tp_limit_enable"), "0");
        write_unlock(tp_path.join("oppo_tp_limit_enable"), "0");
    }

    thread::spawn(|| {
        for block in find_matching_nodes("/sys/block", |s| s.starts_with("mmcblk") || s.starts_with("sd")) {
            write_unlock(block.join("queue/read_ahead_kb"), "128");
        }
    });
}

fn mediatek_balance() {
    crate::log!("[*] Applying MediaTek Balanced State...");

    if Path::new("/proc/ppm/policy_status").exists() {
        if let Ok(content) = fs::read_to_string("/proc/ppm/policy_status") {
            for line in content.lines() {
                let is_enable = line.contains("PWR_THRO") || line.contains("THERMAL");
                let is_unboost = line.contains("SYS_BOOST");
                if let Some(idx_str) = line.split('[').nth(1).and_then(|s| s.split(']').next()) {
                    if is_enable {
                        write_unlock("/proc/ppm/policy_status", format!("{} 1", idx_str));
                    } else if is_unboost {
                        write_unlock("/proc/ppm/policy_status", format!("{} 0", idx_str));
                    }
                }
            }
        }
    }

    write_unlock("/proc/cpufreq/cpufreq_power_mode", "0");
    write_unlock("/proc/cpufreq/cpufreq_cci_mode", "0");
    write_unlock("/sys/devices/platform/boot_dramboost/dramboost/dramboost", "0");

    write_unlock("/proc/gpufreqv2/fix_target_opp_index", "-1");
    write_unlock("/proc/gpufreq/gpufreq_opp_freq", "0");
    write_unlock("/sys/kernel/ged/hal/custom_boost_gpu_freq", "-1");

    let mali_candidates = [
        "/sys/class/misc/mali0/device/power_policy",
        "/sys/devices/platform/soc/13000000.mali/power_policy",
        "/sys/devices/platform/soc/13040000.mali/power_policy",
    ];

    let mut mali_applied = false;
    for path in &mali_candidates {
        if Path::new(path).exists() {
            write_unlock(path, "coarse_demand");
            mali_applied = true;
            break;
        }
    }

    if !mali_applied {
        for mali in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".mali")) {
            write_unlock(mali.join("power_policy"), "coarse_demand");
        }
    }

    let dvfsrc_node = "/sys/class/devfreq/mtk-dvfsrc-devfreq/governor";
    let dvfsrc_avail = "/sys/class/devfreq/mtk-dvfsrc-devfreq/available_governors";
    if let Ok(govs) = fs::read_to_string(dvfsrc_avail) {
        if govs.contains("simple_ondemand") {
            write_unlock(dvfsrc_node, "simple_ondemand");
        } else if govs.contains("userspace") {
            write_unlock(dvfsrc_node, "userspace");
        }
    } else {
        write_unlock(dvfsrc_node, "simple_ondemand");
    }

    for dvfsrc in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".dvfsrc")) {
        write_unlock(dvfsrc.join("dvfsrc_req_ddr_opp"), "-1");
        write_unlock(dvfsrc.join("dvfsrc_force_vcore_dvfs_opp"), "-1");
    }

    write_unlock("/sys/kernel/fpsgo/common/force_onoff", "2");
    write_unlock("/sys/kernel/eara_thermal/enable", "1");
    write_unlock("/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled", "1");
}

fn snapdragon_balance() {
    crate::log!("[*] Applying Snapdragon Balanced State...");
    let kgsl_path = Path::new("/sys/class/kgsl/kgsl-3d0");
    if let Ok(num_pwr) = fs::read_to_string(kgsl_path.join("num_pwrlevels")) {
        write_unlock(kgsl_path.join("min_pwrlevel"), num_pwr.trim());
    }
    write_unlock(kgsl_path.join("max_pwrlevel"), "0");
    write_unlock(kgsl_path.join("adrenoboost"), "0");

    let kgsl_devfreq = kgsl_path.join("devfreq");
    let avail_gpu = kgsl_devfreq.join("available_frequencies");
    if let (Some(min), Some(max)) = (which_minfreq(&avail_gpu), which_maxfreq(&avail_gpu)) {
        write_unlock(kgsl_devfreq.join("min_freq"), min.to_string());
        write_unlock(kgsl_devfreq.join("max_freq"), max.to_string());
    }

    thread::spawn(|| {
        for bus_name in ["LLCC", "L3", "DDR", "DDRQOS"] {
            let bus_path = PathBuf::from(format!("/sys/devices/system/cpu/bus_dcvs/{}", bus_name));
            let avail = bus_path.join("available_frequencies");
            if let (Some(min), Some(max)) = (which_minfreq(&avail), which_maxfreq(&avail)) {
                for node in find_matching_nodes(&bus_path, |_| true) {
                    write_unlock(node.join("max_freq"), max.to_string());
                    write_unlock(node.join("min_freq"), min.to_string());
                    write_unlock(node.join("hw_max_freq"), max.to_string());
                    write_unlock(node.join("hw_min_freq"), min.to_string());
                }
            }
        }
    });
}

fn exynos_balance() {
    crate::log!("[*] Applying Exynos Balanced State...");
    let exynos_gpu = Path::new("/sys/kernel/gpu");
    let avail = exynos_gpu.join("gpu_available_frequencies");
    if let (Some(min), Some(max)) = (which_minfreq(&avail), which_maxfreq(&avail)) {
        write_unlock(exynos_gpu.join("gpu_min_clock"), min.to_string());
        write_unlock(exynos_gpu.join("gpu_max_clock"), max.to_string());
    }

    for mali in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".mali")) {
        write_unlock(mali.join("power_policy"), "coarse_demand");
    }

    thread::spawn(|| {
        for mif in find_matching_nodes("/sys/class/devfreq", |s| s.contains("devfreq_mif")) {
            let avail = mif.join("available_frequencies");
            if let (Some(min), Some(max)) = (which_minfreq(&avail), which_maxfreq(&avail)) {
                write_unlock(mif.join("min_freq"), min.to_string());
                write_unlock(mif.join("max_freq"), max.to_string());
            }
        }
    });
}

fn unisoc_balance() {
    crate::log!("[*] Applying Unisoc Balanced State...");
    for dev in find_matching_nodes("/sys/class/devfreq", |s| s.ends_with(".gpu")) {
        let avail = dev.join("available_frequencies");
        if let (Some(min), Some(max)) = (which_minfreq(&avail), which_maxfreq(&avail)) {
            write_unlock(dev.join("min_freq"), min.to_string());
            write_unlock(dev.join("max_freq"), max.to_string());
        }
    }
}

fn tensor_balance() {
    crate::log!("[*] Applying Tensor Balanced State...");
    for mali in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".mali")) {
        let avail = mali.join("available_frequencies");
        if let (Some(min), Some(max)) = (which_minfreq(&avail), which_maxfreq(&avail)) {
            write_unlock(mali.join("scaling_min_freq"), min.to_string());
            write_unlock(mali.join("scaling_max_freq"), max.to_string());
        }
        write_unlock(mali.join("power_policy"), "coarse_demand");
    }

    thread::spawn(|| {
        for mif in find_matching_nodes("/sys/class/devfreq", |s| s.contains("devfreq_mif")) {
            let avail = mif.join("available_frequencies");
            if let (Some(min), Some(max)) = (which_minfreq(&avail), which_maxfreq(&avail)) {
                write_unlock(mif.join("min_freq"), min.to_string());
                write_unlock(mif.join("max_freq"), max.to_string());
            }
        }
    });
}

pub fn balance_profile() {
    baseline_generic();

    let soc = get_soc_type();
    thread::spawn(move || {
        match soc {
            1 => mediatek_balance(),
            2 => snapdragon_balance(),
            3 => exynos_balance(),
            4 => unisoc_balance(),
            5 => tensor_balance(),
            _ => crate::log!("[*] Unknown SoC: Universal baseline applied."),
        }
    });
}

/// Universal performance core: sets requested governor and locks max frequency
fn apply_universal_performance(governor: &str) {
    crate::log!("[*] Applying Universal Performance Tweaks (Governor: {})...", governor);

    for policy in find_matching_nodes("/sys/devices/system/cpu/cpufreq", |s| s.starts_with("policy")) {
        write_lock(policy.join("scaling_governor"), governor);
    }
    for cpu in find_matching_nodes("/sys/devices/system/cpu", |s| s.starts_with("cpu") && s[3..].chars().all(char::is_numeric)) {
        write_lock(cpu.join("cpufreq/scaling_governor"), governor);
    }

    let has_ppm = Path::new("/proc/ppm").exists();
    let policies = find_matching_nodes("/sys/devices/system/cpu/cpufreq", |s| s.starts_with("policy"));

    if has_ppm {
        for policy in &policies {
            let policy_name = policy.file_name().unwrap_or_default().to_string_lossy();
            let cluster_id = policy_name.trim_start_matches("policy");

            if let Ok(max_freq) = fs::read_to_string(policy.join("cpuinfo_max_freq")) {
                let val = format!("{} {}", cluster_id, max_freq.trim());
                write_lock("/proc/ppm/policy/hard_userlimit_min_cpu_freq", &val);
                write_lock("/proc/ppm/policy/hard_userlimit_max_cpu_freq", &val);
            }
        }
    } else {
        for policy in &policies {
            if let Ok(max_freq) = fs::read_to_string(policy.join("cpuinfo_max_freq")) {
                write_lock(policy.join("scaling_min_freq"), max_freq.trim());
                write_lock(policy.join("scaling_max_freq"), max_freq.trim());
            }
        }
        for cpu in find_matching_nodes("/sys/devices/system/cpu", |s| s.starts_with("cpu") && s[3..].chars().all(char::is_numeric)) {
            let cpufreq = cpu.join("cpufreq");
            if let Ok(max_freq) = fs::read_to_string(cpufreq.join("cpuinfo_max_freq")) {
                write_lock(cpufreq.join("scaling_min_freq"), max_freq.trim());
                write_lock(cpufreq.join("scaling_max_freq"), max_freq.trim());
            }
        }
    }

    write_lock("/sys/devices/system/cpu/eas/enable", "0");
    write_lock("/proc/sys/kernel/split_lock_mitigate", "0");
    write_lock("/proc/sys/vm/vfs_cache_pressure", "80");
    write_lock("/sys/kernel/debug/sched_features", "NEXT_BUDDY");
    write_lock("/sys/kernel/debug/sched_features", "NO_TTWU_QUEUE");

    for stune in find_matching_nodes("/dev/stune", |_| true) {
        write_lock(stune.join("schedtune.boost"), "30");
        write_lock(stune.join("schedtune.sched_boost_enabled"), "1");
        write_lock(stune.join("schedtune.prefer_idle"), "0");
        write_lock(stune.join("schedtune.colocate"), "0");
    }

    let tp_path = Path::new("/proc/touchpanel");
    if tp_path.exists() {
        write_lock(tp_path.join("game_switch_enable"), "1");
        write_lock(tp_path.join("oplus_tp_limit_enable"), "0");
        write_lock(tp_path.join("oppo_tp_limit_enable"), "0");
        write_lock(tp_path.join("oplus_tp_direction"), "1");
        write_lock(tp_path.join("oppo_tp_direction"), "1");
    }

    thread::spawn(|| {
        for block in find_matching_nodes("/sys/block", |s| s.starts_with("mmcblk") || s.starts_with("sd")) {
            write_lock(block.join("queue/read_ahead_kb"), "512");
        }
    });
}

fn mediatek_performance() {
    crate::log!("[*] Applying MediaTek Performance Profile...");

    if Path::new("/proc/ppm/policy_status").exists() {
        if let Ok(content) = fs::read_to_string("/proc/ppm/policy_status") {
            for line in content.lines() {
                let is_disable = line.contains("PWR_THRO")
                    || line.contains("THERMAL")
                    || line.contains("FORCE_LIMIT")
                    || line.contains("USER_LIMIT");
                let is_boost = line.contains("SYS_BOOST");
                if let Some(idx_str) = line.split('[').nth(1).and_then(|s| s.split(']').next()) {
                    if is_disable {
                        write_lock("/proc/ppm/policy_status", format!("{} 0", idx_str));
                    } else if is_boost {
                        write_lock("/proc/ppm/policy_status", format!("{} 1", idx_str));
                    }
                }
            }
        }
    }

    write_lock("/proc/cpufreq/cpufreq_cci_mode", "1");
    write_lock("/proc/cpufreq/cpufreq_power_mode", "3");
    write_lock("/sys/devices/platform/boot_dramboost/dramboost/dramboost", "1");
    write_lock("/sys/kernel/fpsgo/common/force_onoff", "0");
    write_lock("/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled", "0");

    write_lock("/proc/gpufreqv2/fix_target_opp_index", "0");
    write_lock("/proc/gpufreq/gpufreq_opp_freq", "0");
    write_lock("/sys/kernel/ged/hal/custom_boost_gpu_freq", "0");

    let mali_candidates = [
        "/sys/class/misc/mali0/device/power_policy",
        "/sys/devices/platform/soc/13000000.mali/power_policy",
        "/sys/devices/platform/soc/13040000.mali/power_policy",
    ];

    let mut mali_applied = false;
    for path in &mali_candidates {
        if Path::new(path).exists() {
            write_lock(path, "always_on");
            mali_applied = true;
            break;
        }
    }

    if !mali_applied {
        for mali in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".mali")) {
            write_lock(mali.join("power_policy"), "always_on");
        }
    }

    write_lock("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "performance");
}

fn snapdragon_performance() {
    crate::log!("[*] Applying Snapdragon Performance Profile...");
    let kgsl_path = Path::new("/sys/class/kgsl/kgsl-3d0");
    write_lock(kgsl_path.join("min_pwrlevel"), "0");
    write_lock(kgsl_path.join("max_pwrlevel"), "0");
    write_lock(kgsl_path.join("adrenoboost"), "3");

    let kgsl_devfreq = kgsl_path.join("devfreq");
    if let Some(max_gpu) = which_maxfreq(kgsl_devfreq.join("available_frequencies")) {
        write_lock(kgsl_devfreq.join("min_freq"), max_gpu.to_string());
        write_lock(kgsl_devfreq.join("max_freq"), max_gpu.to_string());
    }

    thread::spawn(|| {
        for bus_name in ["LLCC", "L3", "DDR", "DDRQOS"] {
            let bus_path = PathBuf::from(format!("/sys/devices/system/cpu/bus_dcvs/{}", bus_name));
            if let Some(max_bus) = which_maxfreq(bus_path.join("available_frequencies")) {
                for node in find_matching_nodes(&bus_path, |_| true) {
                    write_lock(node.join("max_freq"), max_bus.to_string());
                    write_lock(node.join("min_freq"), max_bus.to_string());
                    write_lock(node.join("hw_max_freq"), max_bus.to_string());
                    write_lock(node.join("hw_min_freq"), max_bus.to_string());
                }
            }
        }

        for df in find_matching_nodes("/sys/class/devfreq", |s| {
            s.contains("memlat")
                || s.contains("latfloor")
                || s.contains("-lat")
                || s.contains("ddr-lat")
                || s.contains("cpu-cpu-ddr-bw")
                || s.contains("cpu-cpu-llcc-bw")
                || s.contains("gpubw")
        }) {
            write_lock(df.join("governor"), "performance");
        }
    });
}

fn exynos_performance() {
    crate::log!("[*] Applying Exynos Performance Profile...");
    let exynos_gpu = Path::new("/sys/kernel/gpu");
    let avail = exynos_gpu.join("gpu_available_frequencies");
    if let Some(max) = which_maxfreq(&avail) {
        write_lock(exynos_gpu.join("gpu_min_clock"), max.to_string());
        write_lock(exynos_gpu.join("gpu_max_clock"), max.to_string());
    }

    for mali in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".mali")) {
        write_lock(mali.join("power_policy"), "always_on");
    }

    thread::spawn(|| {
        for mif in find_matching_nodes("/sys/class/devfreq", |s| s.contains("devfreq_mif")) {
            if let Some(max) = which_maxfreq(mif.join("available_frequencies")) {
                write_lock(mif.join("min_freq"), max.to_string());
                write_lock(mif.join("max_freq"), max.to_string());
            }
        }
    });
}

fn unisoc_performance() {
    crate::log!("[*] Applying Unisoc Performance Profile...");
    for dev in find_matching_nodes("/sys/class/devfreq", |s| s.ends_with(".gpu")) {
        if let Some(max) = which_maxfreq(dev.join("available_frequencies")) {
            write_lock(dev.join("min_freq"), max.to_string());
            write_lock(dev.join("max_freq"), max.to_string());
        }
    }
}

fn tensor_performance() {
    crate::log!("[*] Applying Tensor Performance Profile...");
    for mali in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".mali")) {
        if let Some(max) = which_maxfreq(mali.join("available_frequencies")) {
            write_lock(mali.join("scaling_min_freq"), max.to_string());
            write_lock(mali.join("scaling_max_freq"), max.to_string());
        }
        write_lock(mali.join("power_policy"), "always_on");
    }

    thread::spawn(|| {
        for mif in find_matching_nodes("/sys/class/devfreq", |s| s.contains("devfreq_mif")) {
            if let Some(max) = which_maxfreq(mif.join("available_frequencies")) {
                write_lock(mif.join("min_freq"), max.to_string());
                write_lock(mif.join("max_freq"), max.to_string());
            }
        }
    });
}

/// Applies Performance Profile: Governor "performance" + Full SoC Vendor Lock
pub fn performance_profile() {
    apply_universal_performance("performance");

    let soc = get_soc_type();
    thread::spawn(move || {
        match soc {
            1 => mediatek_performance(),
            2 => snapdragon_performance(),
            3 => exynos_performance(),
            4 => unisoc_performance(),
            5 => tensor_performance(),
            _ => crate::log!("[*] Unknown SoC: Universal performance applied."),
        }
    });
}

/// Applies Performance Lite Profile: Governor "schedutil" + SoC Vendor Nodes Bypassed
pub fn performance_lite_profile() {
    crate::log!("[*] Applying Performance Lite Profile (Universal schedutil, SoC Bypass)...");
    apply_universal_performance("schedutil");

    let soc = get_soc_type();
    thread::spawn(move || {
        match soc {
            1 => mediatek_balance(),
            2 => snapdragon_balance(),
            3 => exynos_balance(),
            4 => unisoc_balance(),
            5 => tensor_balance(),
            _ => crate::log!("[*] Unknown SoC: Vendor nodes bypassed."),
        }
    });
}

fn apply_universal_eco() {
    crate::log!("[*] Applying Universal ECO Tweaks...");

    for policy in find_matching_nodes("/sys/devices/system/cpu/cpufreq", |s| s.starts_with("policy")) {
        write_unlock(policy.join("scaling_governor"), "powersave");
    }
    for cpu in find_matching_nodes("/sys/devices/system/cpu", |s| s.starts_with("cpu") && s[3..].chars().all(char::is_numeric)) {
        write_unlock(cpu.join("cpufreq/scaling_governor"), "powersave");
    }

    let has_ppm = Path::new("/proc/ppm").exists();
    let policies = find_matching_nodes("/sys/devices/system/cpu/cpufreq", |s| s.starts_with("policy"));

    if has_ppm {
        for policy in &policies {
            let policy_name = policy.file_name().unwrap_or_default().to_string_lossy();
            let cluster_id = policy_name.trim_start_matches("policy");

            if let Ok(min_freq) = fs::read_to_string(policy.join("cpuinfo_min_freq")) {
                let val = format!("{} {}", cluster_id, min_freq.trim());
                write_unlock("/proc/ppm/policy/hard_userlimit_min_cpu_freq", val);
            }
            if let Ok(max_freq) = fs::read_to_string(policy.join("cpuinfo_max_freq")) {
                let val = format!("{} {}", cluster_id, max_freq.trim());
                write_unlock("/proc/ppm/policy/hard_userlimit_max_cpu_freq", val);
            }
        }
    } else {
        for policy in &policies {
            if let Ok(min_freq) = fs::read_to_string(policy.join("cpuinfo_min_freq")) {
                write_unlock(policy.join("scaling_min_freq"), min_freq.trim());
            }
            if let Ok(max_freq) = fs::read_to_string(policy.join("cpuinfo_max_freq")) {
                write_unlock(policy.join("scaling_max_freq"), max_freq.trim());
            }
        }
        for cpu in find_matching_nodes("/sys/devices/system/cpu", |s| s.starts_with("cpu") && s[3..].chars().all(char::is_numeric)) {
            let cpufreq = cpu.join("cpufreq");
            if let Ok(min_freq) = fs::read_to_string(cpufreq.join("cpuinfo_min_freq")) {
                write_unlock(cpufreq.join("scaling_min_freq"), min_freq.trim());
            }
            if let Ok(max_freq) = fs::read_to_string(cpufreq.join("cpuinfo_max_freq")) {
                write_unlock(cpufreq.join("scaling_max_freq"), max_freq.trim());
            }
        }
    }

    let bsave_path = "/sys/module/battery_saver/parameters/enabled";
    if let Ok(content) = fs::read_to_string(bsave_path) {
        if content.trim().chars().any(|c| c.is_ascii_digit()) {
            write_unlock(bsave_path, "1");
        } else {
            write_unlock(bsave_path, "Y");
        }
    }

    write_unlock("/sys/devices/system/cpu/eas/enable", "1");
    write_unlock("/sys/module/workqueue/parameters/power_efficient", "Y");
    write_unlock("/proc/sys/vm/vfs_cache_pressure", "120");
    write_unlock("/proc/sys/kernel/split_lock_mitigate", "1");
    write_unlock("/sys/kernel/debug/sched_features", "NO_NEXT_BUDDY");
    write_unlock("/sys/kernel/debug/sched_features", "NO_TTWU_QUEUE");

    for stune in find_matching_nodes("/dev/stune", |_| true) {
        write_unlock(stune.join("schedtune.boost"), "0");
        write_unlock(stune.join("schedtune.sched_boost_enabled"), "0");
        write_unlock(stune.join("schedtune.prefer_idle"), "1");
        write_unlock(stune.join("schedtune.colocate"), "0");
    }

    thread::spawn(|| {
        for block in find_matching_nodes("/sys/block", |s| s.starts_with("mmcblk") || s.starts_with("sd")) {
            write_unlock(block.join("queue/read_ahead_kb"), "128");
        }
    });
}

fn mediatek_eco() {
    crate::log!("[*] Applying MediaTek ECO Profile...");
    write_unlock("/proc/cpufreq/cpufreq_power_mode", "1");

    if Path::new("/proc/gpufreqv2/gpu_working_opp_table").exists() {
        if let Ok(content) = fs::read_to_string("/proc/gpufreqv2/gpu_working_opp_table") {
            if let Some(last_line) = content.lines().filter(|l| !l.trim().is_empty()).last() {
                if let Some(min_idx) = last_line.split_whitespace().next() {
                    write_unlock("/proc/gpufreqv2/fix_target_opp_index", min_idx);
                }
            }
        }
    } else if Path::new("/proc/gpufreq/gpufreq_opp_dump").exists() {
        if let Ok(content) = fs::read_to_string("/proc/gpufreq/gpufreq_opp_dump") {
            if let Some(last_line) = content.lines().filter(|l| !l.trim().is_empty()).last() {
                if let Some(min_freq) = last_line.split_whitespace().find_map(|s| s.parse::<u64>().ok()) {
                    write_unlock("/proc/gpufreq/gpufreq_opp_freq", min_freq.to_string());
                }
            }
        }
    }

    let mali_candidates = [
        "/sys/class/misc/mali0/device/power_policy",
        "/sys/devices/platform/soc/13000000.mali/power_policy",
        "/sys/devices/platform/soc/13040000.mali/power_policy",
    ];

    let mut mali_applied = false;
    for path in &mali_candidates {
        if Path::new(path).exists() {
            write_unlock(path, "coarse_demand");
            mali_applied = true;
            break;
        }
    }

    if !mali_applied {
        for mali in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".mali")) {
            write_unlock(mali.join("power_policy"), "coarse_demand");
        }
    }

    write_unlock("/sys/class/devfreq/mtk-dvfsrc-devfreq/governor", "powersave");
    for dvfsrc in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".dvfsrc")) {
        write_unlock(dvfsrc.join("dvfsrc_req_ddr_opp"), "-1");
        write_unlock(dvfsrc.join("dvfsrc_force_vcore_dvfs_opp"), "-1");
    }

    write_unlock("/sys/kernel/fpsgo/common/force_onoff", "2");
    write_unlock("/sys/kernel/eara_thermal/enable", "1");
    write_unlock("/sys/devices/platform/boot_dramboost/dramboost/dramboost", "0");
}

fn snapdragon_eco() {
    crate::log!("[*] Applying Snapdragon ECO Profile...");
    let kgsl_devfreq = Path::new("/sys/class/kgsl/kgsl-3d0/devfreq");
    if let Some(min_gpu) = which_minfreq(kgsl_devfreq.join("available_frequencies")) {
        write_unlock(kgsl_devfreq.join("min_freq"), min_gpu.to_string());
        write_unlock(kgsl_devfreq.join("max_freq"), min_gpu.to_string());
    }
    write_unlock("/sys/class/kgsl/kgsl-3d0/adrenoboost", "0");

    thread::spawn(|| {
        for bus_name in ["LLCC", "L3", "DDR", "DDRQOS"] {
            let bus_path = PathBuf::from(format!("/sys/devices/system/cpu/bus_dcvs/{}", bus_name));
            if let Some(min_bus) = which_minfreq(bus_path.join("available_frequencies")) {
                write_unlock(bus_path.join("min_freq"), min_bus.to_string());
                write_unlock(bus_path.join("max_freq"), min_bus.to_string());
            }
        }

        for df in find_matching_nodes("/sys/class/devfreq", |s| {
            s.contains("cpu-ddr-latfloor")
                || s.contains("-lat")
                || s.contains("cpu-cpu-ddr-bw")
                || s.contains("cpu-cpu-llcc-bw")
                || s.contains("gpubw")
        }) {
            write_unlock(df.join("governor"), "powersave");
        }
    });
}

fn exynos_eco() {
    crate::log!("[*] Applying Exynos ECO Profile...");
    let exynos_gpu = Path::new("/sys/kernel/gpu");
    let avail = exynos_gpu.join("gpu_available_frequencies");
    if let Some(min) = which_minfreq(&avail) {
        write_unlock(exynos_gpu.join("gpu_min_clock"), min.to_string());
        write_unlock(exynos_gpu.join("gpu_max_clock"), min.to_string());
    }

    for mali in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".mali")) {
        write_unlock(mali.join("power_policy"), "coarse_demand");
    }
}

fn unisoc_eco() {
    crate::log!("[*] Applying Unisoc ECO Profile...");
    for dev in find_matching_nodes("/sys/class/devfreq", |s| s.ends_with(".gpu")) {
        if let Some(min_freq) = which_minfreq(dev.join("available_frequencies")) {
            write_unlock(dev.join("min_freq"), min_freq.to_string());
            write_unlock(dev.join("max_freq"), min_freq.to_string());
        }
    }
}

fn tensor_eco() {
    crate::log!("[*] Applying Tensor ECO Profile...");
    for mali in find_matching_nodes("/sys/devices/platform", |s| s.ends_with(".mali")) {
        if let Some(min_freq) = which_minfreq(mali.join("available_frequencies")) {
            write_unlock(mali.join("scaling_min_freq"), min_freq.to_string());
            write_unlock(mali.join("scaling_max_freq"), min_freq.to_string());
        }
        write_unlock(mali.join("power_policy"), "coarse_demand");
    }
}

pub fn eco_profile() {
    apply_universal_eco();

    let soc = get_soc_type();
    thread::spawn(move || {
        match soc {
            1 => mediatek_eco(),
            2 => snapdragon_eco(),
            3 => exynos_eco(),
            4 => unisoc_eco(),
            5 => tensor_eco(),
            _ => crate::log!("[*] Unknown SoC: Universal ECO applied."),
        }
    });
}
