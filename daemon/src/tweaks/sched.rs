// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

const STUNE_DIRS: &[&str] = &["/dev/stune/top-app", "/sys/fs/cgroup/stune/top-app"];
const UCLAMP_DIRS: &[&str] = &[
    "/sys/fs/cgroup/top-app",
    "/dev/cpuctl/top-app",
    "/sys/fs/cgroup/cpu/top-app",
];

fn node_exists(path: &str) -> bool {
    std::path::Path::new(path).exists()
}

fn write_node(path: &str, value: &str) {
    if node_exists(path) {
        crate::tweaks::state::write_tracked(path, value, false);
    }
}

fn write_first_available(candidates: &[&str], nodes: &[(&str, &str)]) {
    for dir in candidates {
        if !node_exists(dir) {
            continue;
        }
        for (name, value) in nodes {
            write_node(&format!("{}/{}", dir, name), value);
        }
    }
}

fn tune_top_app(
    boost: &str,
    prefer_idle: &str,
    boost_enabled: &str,
    uclamp_min: &str,
    uclamp_max: &str,
) {
    write_first_available(
        STUNE_DIRS,
        &[
            ("schedtune.boost", boost),
            ("schedtune.prefer_idle", prefer_idle),
            ("schedtune.sched_boost_enabled", boost_enabled),
        ],
    );

    write_first_available(
        UCLAMP_DIRS,
        &[
            ("cpu.uclamp.min", uclamp_min),
            ("cpu.uclamp.max", uclamp_max),
        ],
    );
}

pub fn apply_performance() {
    tune_top_app("100", "1", "1", "100", "max");
}

pub fn enforce_performance() {
    for dir in UCLAMP_DIRS {
        write_node(&format!("{}/cpu.uclamp.min", dir), "100");
        write_node(&format!("{}/cpu.uclamp.max", dir), "max");
    }
}

pub fn apply_balance() {
    tune_top_app("0", "0", "0", "0", "max");
}

pub fn apply_eco() {
    tune_top_app("0", "1", "0", "0", "50");
}

pub fn tune_daemon() {
    unsafe {
        libc::setpriority(libc::PRIO_PROCESS, 0, 10);
        libc::syscall(libc::SYS_ioprio_set, 1, 0, (2 << 13) | 7);
    }
}