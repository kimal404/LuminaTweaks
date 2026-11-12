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

const STUNE_DIRS: [&str; 2] = ["/dev/stune/top-app", "/sys/fs/cgroup/stune/top-app"];
const UCLAMP_DIRS: [&str; 3] = [
    "/sys/fs/cgroup/top-app",
    "/dev/cpuctl/top-app",
    "/sys/fs/cgroup/cpu/top-app",
];

fn write_node(path: &str, value: &str) {
    crate::tweaks::state::write_tracked(path, value, false);
}

fn tune_top_app(boost: &str, prefer_idle: &str, uclamp_min: &str, uclamp_max: &str) {
    for dir in STUNE_DIRS {
        write_node(&format!("{}/schedtune.boost", dir), boost);
        write_node(&format!("{}/schedtune.prefer_idle", dir), prefer_idle);
        write_node(&format!("{}/schedtune.sched_boost_enabled", dir), boost);
    }

    for dir in UCLAMP_DIRS {
        write_node(&format!("{}/cpu.uclamp.min", dir), uclamp_min);
        write_node(&format!("{}/cpu.uclamp.max", dir), uclamp_max);
    }
}

pub fn apply_performance() {
    tune_top_app("1", "1", "80", "max");
}

pub fn apply_balance() {
    tune_top_app("0", "0", "0", "max");
}

pub fn apply_eco() {
    tune_top_app("0", "1", "0", "50");
}

pub fn tune_daemon() {
    unsafe {
        libc::setpriority(libc::PRIO_PROCESS, 0, 10);
        libc::syscall(libc::SYS_ioprio_set, 1, 0, (2 << 13) | 7);
    }
}
