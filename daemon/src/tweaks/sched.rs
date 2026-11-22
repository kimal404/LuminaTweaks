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

const STUNE_DIRS: &[&str] = &["/dev/stune/top-app", "/sys/fs/cgroup/stune/top-app"];
const UCLAMP_DIRS: &[&str] = &[
    "/sys/fs/cgroup/top-app",
    "/dev/cpuctl/top-app",
    "/sys/fs/cgroup/cpu/top-app",
];
const SCHED_FEATURES_PATH: &str = "/sys/kernel/debug/sched_features";
const SPLIT_LOCK_PATH: &str = "/proc/sys/kernel/split_lock_mitigate";
const TOUCHPANEL_DIR: &str = "/proc/touchpanel";
const CPUSET_DIR: &str = "/dev/cpuset";
const CPU_POLICY_DIR: &str = "/sys/devices/system/cpu/cpufreq";
const CPU_ONLINE_PATH: &str = "/sys/devices/system/cpu/online";

fn exists(path: &str) -> bool {
    std::path::Path::new(path).exists()
}

fn write_node(path: &str, value: &str) {
    if exists(path) {
        crate::tweaks::state::write_tracked(path, value, false);
    }
}

fn write_first_available(candidates: &[&str], nodes: &[(&str, &str)]) {
    for dir in candidates {
        if !exists(dir) {
            continue;
        }
        for (name, value) in nodes {
            write_node(&format!("{}/{}", dir, name), value);
        }
    }
}

fn set_sched_features(features: &[&str]) {
    if !exists(SCHED_FEATURES_PATH) {
        return;
    }
    for feature in features {
        write_node(SCHED_FEATURES_PATH, feature);
    }
}

fn set_split_lock(enabled: bool) {
    write_node(SPLIT_LOCK_PATH, if enabled { "1" } else { "0" });
}

fn tune_top_app(
    boost: &str,
    prefer_idle: &str,
    uclamp: Option<(&str, &str, &str)>,
) {
    write_first_available(
        STUNE_DIRS,
        &[
            ("schedtune.boost", boost),
            ("schedtune.prefer_idle", prefer_idle),
        ],
    );

    if let Some((uclamp_min, uclamp_max, latency_sensitive)) = uclamp {
        write_first_available(
            UCLAMP_DIRS,
            &[
                ("cpu.uclamp.min", uclamp_min),
                ("cpu.uclamp.max", uclamp_max),
                ("cpu.uclamp.latency_sensitive", latency_sensitive),
            ],
        );
    }
}

fn tune_touchpanel(performance: bool) {
    if !exists(TOUCHPANEL_DIR) {
        return;
    }
    let value = |on: bool| if on { "1" } else { "0" };
    for (name, enabled) in [
        ("game_switch_enable", performance),
        ("oplus_tp_limit_enable", !performance),
        ("oppo_tp_limit_enable", !performance),
        ("oplus_tp_direction", performance),
        ("oppo_tp_direction", performance),
    ] {
        write_node(
            &format!("{}/{}", TOUCHPANEL_DIR, name),
            value(enabled),
        );
    }
}

fn parse_cpu_list(raw: &str) -> Vec<u32> {
    let mut cpus = Vec::new();
    for part in raw.split([',', ' ', '\n', '\t']) {
        let part = part.trim();
        if part.is_empty() {
            continue;
        }
        if let Some((start, end)) = part.split_once('-') {
            if let (Ok(start), Ok(end)) = (start.trim().parse::<u32>(), end.trim().parse::<u32>()) {
                for cpu in start..=end {
                    cpus.push(cpu);
                }
            }
        } else if let Ok(cpu) = part.parse::<u32>() {
            cpus.push(cpu);
        }
    }
    cpus.sort_unstable();
    cpus.dedup();
    cpus
}

fn format_cpu_list(cpus: &[u32]) -> String {
    let mut parts = Vec::new();
    let mut index = 0;
    while index < cpus.len() {
        let start = cpus[index];
        let mut end = start;
        while index + 1 < cpus.len() && cpus[index + 1] == end + 1 {
            index += 1;
            end = cpus[index];
        }
        if start == end {
            parts.push(start.to_string());
        } else {
            parts.push(format!("{}-{}", start, end));
        }
        index += 1;
    }
    parts.join(",")
}

fn policy_dirs() -> Vec<String> {
    let mut dirs = Vec::new();
    let Ok(entries) = std::fs::read_dir(CPU_POLICY_DIR) else {
        return dirs;
    };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if !name.starts_with("policy") {
            continue;
        }
        dirs.push(name);
    }
    dirs.sort_by_key(|name| {
        name.trim_start_matches("policy")
            .parse::<u32>()
            .unwrap_or(u32::MAX)
    });
    dirs.into_iter()
        .map(|name| format!("{}/{}", CPU_POLICY_DIR, name))
        .collect()
}

fn first_policy_cpus() -> Vec<u32> {
    let Some(base) = policy_dirs().into_iter().next() else {
        return Vec::new();
    };
    let raw = std::fs::read_to_string(format!("{}/related_cpus", base))
        .or_else(|_| std::fs::read_to_string(format!("{}/cpus", base)))
        .unwrap_or_default();
    parse_cpu_list(&raw)
}

fn active_cpus() -> Vec<u32> {
    let raw = std::fs::read_to_string(CPU_ONLINE_PATH).unwrap_or_default();
    let online = parse_cpu_list(&raw);
    if !online.is_empty() {
        return online;
    }
    first_policy_cpus()
}

fn apply_cpuset_isolation() {
    let first = first_policy_cpus();
    if first.is_empty() {
        return;
    }
    let first_list = format_cpu_list(&first);
    let active_list = format_cpu_list(&active_cpus());
    for group in ["background", "system-background"] {
        write_node(&format!("{}/{}/cpus", CPUSET_DIR, group), &first_list);
    }
    write_node(&format!("{}/top-app/cpus", CPUSET_DIR), &active_list);
}

pub fn apply_balance() {
    set_split_lock(true);
    set_sched_features(&["NEXT_BUDDY", "TTWU_QUEUE"]);
    tune_top_app("0", "0", None);
    tune_touchpanel(false);
}

pub fn apply_powersave() {
    set_split_lock(true);
    set_sched_features(&["NEXT_BUDDY", "TTWU_QUEUE"]);
    tune_top_app("0", "1", None);
    tune_touchpanel(false);
}

pub fn tune_daemon() {
    unsafe {
        libc::setpriority(libc::PRIO_PROCESS, 0, 10);
        libc::syscall(libc::SYS_ioprio_set, 1, 0, (2 << 13) | 7);
    }
}

#[cfg(test)]
mod tests {
    use super::{format_cpu_list, parse_cpu_list};

    #[test]
    fn parses_cpu_lists() {
        assert_eq!(parse_cpu_list("0 1 2 3"), vec![0, 1, 2, 3]);
        assert_eq!(parse_cpu_list("0-3"), vec![0, 1, 2, 3]);
        assert_eq!(parse_cpu_list("0,2-4"), vec![0, 2, 3, 4]);
        assert!(parse_cpu_list("").is_empty());
    }

    #[test]
    fn formats_cpu_ranges() {
        assert_eq!(format_cpu_list(&[0, 1, 2, 3]), "0-3");
        assert_eq!(format_cpu_list(&[0, 1, 4]), "0-1,4");
        assert_eq!(format_cpu_list(&[7]), "7");
    }
}
