const STUNE_DIRS: &[&str] = &["/dev/stune/top-app", "/sys/fs/cgroup/stune/top-app"];
const UCLAMP_DIRS: &[&str] = &[
    "/sys/fs/cgroup/top-app",
    "/dev/cpuctl/top-app",
    "/sys/fs/cgroup/cpu/top-app",
];
const SCHED_FEATURES_PATH: &str = "/sys/kernel/debug/sched_features";
const SPLIT_LOCK_PATH: &str = "/proc/sys/kernel/split_lock_mitigate";
const TOUCHPANEL_DIR: &str = "/proc/touchpanel";

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

pub fn apply_performance() {
    set_split_lock(false);
    set_sched_features(&["NEXT_BUDDY", "NO_TTWU_QUEUE"]);
    tune_top_app("1", "1", Some(("max", "max", "1")));
    tune_touchpanel(true);
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
