use std::collections::{HashMap, HashSet};
use std::fs;
use std::path::Path;

#[derive(Debug, Clone)]
pub struct CpuTopology {
    pub total_cores: usize,
    pub little_mask: u64,
    pub big_mask: u64,
    pub prime_mask: u64,
    pub fast_mask: u64,
    pub all_mask: u64,
    pub little_range_str: String,
    pub all_range_str: String,
}

impl CpuTopology {
    pub fn detect() -> Self {
        let mut core_caps: Vec<(usize, u64)> = Vec::new();
        let mut idx = 0;

        loop {
            let cap_path = format!("/sys/devices/system/cpu/cpu{}/cpu_capacity", idx);
            let freq_path = format!("/sys/devices/system/cpu/cpu{}/cpufreq/scaling_max_freq", idx);

            if !Path::new(&format!("/sys/devices/system/cpu/cpu{}", idx)).exists() {
                break;
            }

            let cap = if let Ok(content) = fs::read_to_string(&cap_path) {
                content.trim().parse::<u64>().unwrap_or(0)
            } else if let Ok(content) = fs::read_to_string(&freq_path) {
                content.trim().parse::<u64>().unwrap_or(0)
            } else {
                0
            };

            core_caps.push((idx, cap));
            idx += 1;
        }

        if core_caps.is_empty() {
            return Self::fallback_8core();
        }

        let total_cores = core_caps.len();
        let mut sorted_caps: Vec<u64> = core_caps.iter().map(|(_, c)| *c).collect();
        sorted_caps.sort_unstable();
        sorted_caps.dedup();

        let min_cap = *sorted_caps.first().unwrap_or(&0);
        let max_cap = *sorted_caps.last().unwrap_or(&0);

        let mut little_mask: u64 = 0;
        let mut big_mask: u64 = 0;
        let mut prime_mask: u64 = 0;
        let mut all_mask: u64 = 0;
        let mut little_cores_vec: Vec<usize> = Vec::new();

        if sorted_caps.len() == 1 {
            for (c, _) in &core_caps {
                all_mask |= 1 << c;
            }
            return Self {
                total_cores,
                little_mask: all_mask,
                big_mask: all_mask,
                prime_mask: all_mask,
                fast_mask: all_mask,
                all_mask,
                little_range_str: format!("0-{}", total_cores.saturating_sub(1)),
                all_range_str: format!("0-{}", total_cores.saturating_sub(1)),
            };
        }

        for (core, cap) in &core_caps {
            all_mask |= 1 << core;
            if *cap == min_cap {
                little_mask |= 1 << core;
                little_cores_vec.push(*core);
            } else if *cap == max_cap && sorted_caps.len() > 2 {
                prime_mask |= 1 << core;
            } else {
                big_mask |= 1 << core;
            }
        }

        if prime_mask == 0 {
            prime_mask = big_mask;
        }
        let fast_mask = big_mask | prime_mask;

        let little_range_str = if !little_cores_vec.is_empty() {
            let min = little_cores_vec.iter().min().unwrap();
            let max = little_cores_vec.iter().max().unwrap();
            format!("{}-{}", min, max)
        } else {
            "0-3".to_string()
        };

        let all_range_str = format!("0-{}", total_cores.saturating_sub(1));

        Self {
            total_cores,
            little_mask,
            big_mask,
            prime_mask,
            fast_mask,
            all_mask,
            little_range_str,
            all_range_str,
        }
    }

    fn fallback_8core() -> Self {
        Self {
            total_cores: 8,
            little_mask: 0x0F,
            big_mask: 0x70,
            prime_mask: 0x80,
            fast_mask: 0xF0,
            all_mask: 0xFF,
            little_range_str: "0-3".to_string(),
            all_range_str: "0-7".to_string(),
        }
    }
}

#[repr(C)]
struct CpuSet {
    bits: [u64; 16],
}

extern "C" {
    fn sched_setaffinity(pid: i32, cpusetsize: usize, mask: *const CpuSet) -> i32;
}

fn set_affinity_mask(tid: i32, mask: u64) -> bool {
    let mut cpuset = CpuSet { bits: [0u64; 16] };
    cpuset.bits[0] = mask;
    let ret = unsafe {
        sched_setaffinity(tid, std::mem::size_of::<CpuSet>(), &cpuset)
    };
    ret == 0
}

fn format_thread_counts(names: &[String], counts: &HashMap<String, usize>) -> String {
    names
        .iter()
        .map(|name| {
            let cnt = counts.get(name).copied().unwrap_or(1);
            if cnt > 1 {
                format!("{} x{}", name, cnt)
            } else {
                name.clone()
            }
        })
        .collect::<Vec<_>>()
        .join(", ")
}

pub fn get_pkg_pid(pkg: &str) -> Option<i32> {
    let entries = fs::read_dir("/proc").ok()?;
    for entry in entries.flatten() {
        let name = entry.file_name();
        let name_str = name.to_str()?;
        if !name_str.chars().all(|c| c.is_ascii_digit()) {
            continue;
        }
        let pid: i32 = name_str.parse().ok()?;

        let cmdline_path = entry.path().join("cmdline");
        let cmdline_bytes = match fs::read(cmdline_path) {
            Ok(b) => b,
            Err(_) => continue,
        };

        let first_arg = match cmdline_bytes.split(|&b| b == 0).next() {
            Some(arg) => arg,
            None => continue,
        };

        let proc_cmd = match std::str::from_utf8(first_arg) {
            Ok(s) => s.trim(),
            Err(_) => continue,
        };

        if proc_cmd != pkg {
            continue;
        }

        let status_path = entry.path().join("status");
        if let Ok(status_str) = fs::read_to_string(status_path) {
            for line in status_str.lines() {
                if line.starts_with("Uid:") {
                    let mut parts = line.split_whitespace();
                    parts.next();
                    if let Some(uid_str) = parts.next() {
                        if let Ok(uid) = uid_str.parse::<u32>() {
                            if uid >= 10000 {
                                return Some(pid);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
    None
}

pub fn sync_process_threads(
    pid: i32,
    tracked_tids: &mut HashSet<i32>,
    topology: &CpuTopology,
) {
    let task_dir = format!("/proc/{}/task", pid);
    let entries = match fs::read_dir(task_dir) {
        Ok(e) => e,
        Err(_) => return,
    };

    let mut newly_bound = 0;
    let mut fast_names: Vec<String> = Vec::new();
    let mut fast_counts: HashMap<String, usize> = HashMap::new();
    let mut big_names: Vec<String> = Vec::new();
    let mut big_counts: HashMap<String, usize> = HashMap::new();
    let mut worker_count = 0;

    for entry in entries.flatten() {
        if let Ok(tid) = entry.file_name().to_string_lossy().parse::<i32>() {
            if !tracked_tids.contains(&tid) {
                let comm_path = format!("/proc/{}/task/{}/comm", pid, tid);
                let mut comm = fs::read_to_string(comm_path)
                    .unwrap_or_default()
                    .trim()
                    .to_string();

                if comm.is_empty() {
                    comm = "unnamed".to_string();
                }

                let target_mask = if tid == pid {
                    topology.fast_mask
                } else if comm.contains("Render")
                    || comm.contains("GL")
                    || comm.contains("GPU")
                    || comm.contains("SurfaceFlinger")
                    || comm.contains("RenderThread")
                    || comm.contains("main")
                    || comm.contains("GameCore")
                    || comm.contains("UnityMain")
                {
                    topology.fast_mask
                } else if comm.contains("Audio") {
                    topology.big_mask
                } else {
                    topology.all_mask
                };

                if set_affinity_mask(tid, target_mask) {
                    tracked_tids.insert(tid);
                    newly_bound += 1;

                    if target_mask == topology.fast_mask {
                        if !fast_counts.contains_key(&comm) {
                            fast_names.push(comm.clone());
                        }
                        *fast_counts.entry(comm.clone()).or_insert(0) += 1;
                    } else if target_mask == topology.big_mask {
                        if !big_counts.contains_key(&comm) {
                            big_names.push(comm.clone());
                        }
                        *big_counts.entry(comm.clone()).or_insert(0) += 1;
                    } else {
                        worker_count += 1;
                    }

                    log_verbose!(
                        "[AFFINITY] TID {} ({}) bound to mask 0x{:X}",
                        tid,
                        comm,
                        target_mask
                    );
                }
            }
        }
    }

    if newly_bound > 0 {
        let mut summary_parts = Vec::new();
        if !fast_names.is_empty() {
            summary_parts.push(format!(
                "Fast(0x{:X}): {}",
                topology.fast_mask,
                format_thread_counts(&fast_names, &fast_counts)
            ));
        }
        if !big_names.is_empty() {
            summary_parts.push(format!(
                "Big(0x{:X}): {}",
                topology.big_mask,
                format_thread_counts(&big_names, &big_counts)
            ));
        }
        if worker_count > 0 {
            summary_parts.push(format!(
                "All(0x{:X}): {} workers",
                topology.all_mask,
                worker_count
            ));
        }

        let details = summary_parts.join(" | ");
        log!(
            "[AFFINITY] PID {} | {} threads bound | {}",
            pid,
            newly_bound,
            details
        );
    }
}

pub fn restore_process_threads(
    tracked_tids: &mut HashSet<i32>,
    topology: &CpuTopology,
) {
    if tracked_tids.is_empty() {
        return;
    }
    let total = tracked_tids.len();
    for tid in tracked_tids.drain() {
        set_affinity_mask(tid, topology.all_mask);
    }
    log!(
        "[AFFINITY] Restored {} thread(s) to All Cores (0x{:X})",
        total,
        topology.all_mask
    );
}

pub fn isolate_background(enable: bool, topology: &CpuTopology) {
    let target = if enable {
        &topology.little_range_str
    } else {
        &topology.all_range_str
    };

    let cpuset_nodes = [
        "/dev/cpuset/background/cpus",
        "/dev/cpuset/system-background/cpus",
        "/dev/cpuset/restricted/cpus",
    ];

    let mut configured_count = 0;
    let total_nodes = cpuset_nodes.len();

    for node in &cpuset_nodes {
        if Path::new(node).exists() {
            match fs::write(node, target) {
                Ok(_) => {
                    configured_count += 1;
                    log!("[AFFINITY] Configured cpuset node {}: {}", node, target);
                }
                Err(e) => {
                    log!("[WARN] Failed to write cpuset node {}: {}", node, e);
                }
            }
        } else {
            log!("[AFFINITY] Cpuset node {} not found, skipped gracefully", node);
        }
    }

    log!(
        "[AFFINITY] {}/{} cpuset nodes configured",
        configured_count,
        total_nodes
    );
}
