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

use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::sync::Mutex;

const CONFIG_DIR: &str = "/data/adb/.config/lumina";
const REPORT_PATH: &str = "/data/adb/.config/lumina/capabilities.json";
const REPORT_TMP: &str = "/data/adb/.config/lumina/capabilities.json.tmp";
const MODE_PATH: &str = "/data/adb/.config/lumina/capability_mode";
const CAP_LOG_PATH: &str = "/data/local/tmp/lumina_capability.log";
const CAP_LOG_OLD: &str = "/data/local/tmp/lumina_capability.log.old";
const MAX_ENTRIES: usize = 256;
const PERSIST_INTERVAL_MS: u64 = 2_000;
const CAP_LOG_MAX_BYTES: u64 = 128 * 1024;

const PROBES: &[&str] = &[
    "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
    "/proc/sys/net/ipv4/tcp_congestion_control",
    "/proc/sys/vm/vfs_cache_pressure",
    "/sys/block/sda/queue/scheduler",
    "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
    "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
];

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Status {
    Supported,
    Coerced,
    Blocked,
    Degraded,
    Unsupported,
    Unknown,
    RolledBack,
    ReadOnly,
    WriteOnly,
}

impl Status {
    pub fn needs_rollback(self) -> bool {
        matches!(self, Status::Blocked | Status::Degraded)
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Status::Supported => "supported",
            Status::Coerced => "coerced",
            Status::Blocked => "blocked",
            Status::Degraded => "degraded",
            Status::Unsupported => "unsupported",
            Status::Unknown => "unknown",
            Status::RolledBack => "rolled_back",
            Status::ReadOnly => "read_only",
            Status::WriteOnly => "write_only",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Entry {
    pub path: String,
    pub category: String,
    pub status: Status,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub requested: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub actual: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
    pub updated_at_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Report {
    pub boot_id: String,
    pub generated_at_ms: u64,
    pub mode: String,
    pub entries: Vec<Entry>,
}

struct State {
    entries: BTreeMap<String, Entry>,
    dirty: bool,
    last_persist_ms: u64,
}

static STATE: Mutex<Option<State>> = Mutex::new(None);
static CAP_LOG_LOCK: Mutex<()> = Mutex::new(());

#[derive(Serialize)]
struct LogEvent<'a> {
    ts: u64,
    boot_id: &'a str,
    path: &'a str,
    category: &'a str,
    status: Status,
    #[serde(skip_serializing_if = "Option::is_none")]
    requested: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    actual: Option<&'a str>,
    #[serde(skip_serializing_if = "Option::is_none")]
    detail: Option<&'a str>,
}

fn should_log(previous: Option<Status>, status: Status) -> bool {
    previous != Some(status) || status == Status::RolledBack
}

fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis() as u64)
        .unwrap_or(0)
}

fn boot_id() -> String {
    if let Ok(value) = fs::read_to_string("/proc/sys/kernel/random/boot_id") {
        let value = value.trim();
        if !value.is_empty() {
            return value.to_string();
        }
    }
    fs::read_to_string("/proc/stat")
        .ok()
        .and_then(|content| {
            content.lines().find_map(|line| {
                line.strip_prefix("btime ")
                    .map(|value| format!("btime:{}", value.trim()))
            })
        })
        .unwrap_or_else(|| "unknown".to_string())
}

fn with_state<T>(f: impl FnOnce(&mut State) -> T) -> T {
    let mut guard = STATE.lock().unwrap_or_else(|error| error.into_inner());
    let state = guard.get_or_insert_with(|| State {
        entries: BTreeMap::new(),
        dirty: false,
        last_persist_ms: 0,
    });
    f(state)
}

fn category_for(path: &str) -> &'static str {
    if path.starts_with("/sys/devices/system/cpu") {
        "cpu"
    } else if path.contains("/devfreq")
        || path.contains("/kgsl")
        || path.contains("/gpu")
        || path.contains("/mali")
    {
        "gpu"
    } else if path.starts_with("/sys/block")
        || path.starts_with("/proc/sys/vm")
        || path.contains("/mmc")
        || path.contains("/ufs")
        || path.contains("/scsi")
    {
        "storage"
    } else if path.starts_with("/proc/sys/net") {
        "network"
    } else if path.starts_with("/sys/class/thermal")
        || path.starts_with("/sys/devices/virtual/thermal")
    {
        "thermal"
    } else if path.starts_with("/proc/sys/kernel") || path.starts_with("/sys/module") {
        "scheduler"
    } else {
        "system"
    }
}

fn read_actual(path: &str) -> Option<String> {
    fs::read_to_string(path)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
}

fn writable(path: &str) -> bool {
    fs::metadata(path)
        .map(|meta| meta.permissions().mode() & 0o222 != 0)
        .unwrap_or(false)
}

fn looks_like_value(requested: &str, actual: &str) -> bool {
    let actual = actual.trim();
    if actual.is_empty() || actual.starts_with('[') {
        return false;
    }
    let requested = requested.trim();
    if requested.chars().all(|ch| ch.is_ascii_digit() || ch == '-') {
        return actual.chars().all(|ch| ch.is_ascii_digit() || ch == '-');
    }
    true
}

fn append_event(entry: &Entry) {
    let boot = boot_id();
    let event = LogEvent {
        ts: now_ms(),
        boot_id: &boot,
        path: &entry.path,
        category: &entry.category,
        status: entry.status,
        requested: entry.requested.as_deref(),
        actual: entry.actual.as_deref(),
        detail: entry.detail.as_deref(),
    };
    let Ok(mut line) = serde_json::to_string(&event) else {
        return;
    };
    line.push('\n');

    let _guard = CAP_LOG_LOCK
        .lock()
        .unwrap_or_else(|error| error.into_inner());
    if fs::metadata(CAP_LOG_PATH)
        .map(|meta| meta.len() >= CAP_LOG_MAX_BYTES)
        .unwrap_or(false)
    {
        if Path::new(CAP_LOG_OLD).exists() {
            let _ = fs::remove_file(CAP_LOG_OLD);
        }
        let _ = fs::rename(CAP_LOG_PATH, CAP_LOG_OLD);
    }
    if let Ok(mut file) = OpenOptions::new()
        .create(true)
        .append(true)
        .open(CAP_LOG_PATH)
    {
        let _ = file.write_all(line.as_bytes());
        let _ = fs::set_permissions(CAP_LOG_PATH, fs::Permissions::from_mode(0o644));
    }
}

fn detail_for(status: Status) -> Option<String> {
    match status {
        Status::Unsupported => Some("path_missing".to_string()),
        Status::Blocked => Some("write_failed".to_string()),
        Status::Degraded => Some("no_readback".to_string()),
        Status::Coerced => Some("value_coerced".to_string()),
        Status::RolledBack => Some("restored".to_string()),
        Status::ReadOnly => Some("read_only".to_string()),
        Status::WriteOnly => Some("write_only".to_string()),
        _ => None,
    }
}

fn store(path: &str, status: Status, requested: Option<String>, actual: Option<String>) {
    let updated_at_ms = now_ms();
    let logged = with_state(|state| {
        if state.entries.len() >= MAX_ENTRIES && !state.entries.contains_key(path) {
            return None;
        }
        let previous = state.entries.get(path).map(|entry| entry.status);
        let entry = Entry {
            path: path.to_string(),
            category: category_for(path).to_string(),
            status,
            requested,
            actual,
            detail: detail_for(status),
            updated_at_ms,
        };
        let logged = should_log(previous, status).then(|| entry.clone());
        state.entries.insert(path.to_string(), entry);
        state.dirty = true;
        logged
    });
    if let Some(entry) = logged {
        append_event(&entry);
    }
}

pub fn record_write(path: &str, requested: &str, write_ok: bool) -> Status {
    if path.is_empty() {
        return Status::Unknown;
    }
    let actual = read_actual(path);
    let status = if !Path::new(path).exists() {
        Status::Unsupported
    } else if !write_ok {
        Status::Blocked
    } else {
        match actual.as_deref() {
            None => Status::WriteOnly,
            Some(value) if !looks_like_value(requested, value) => Status::WriteOnly,
            Some(value) if value == requested.trim() => Status::Supported,
            Some(_) => Status::Coerced,
        }
    };
    store(path, status, Some(requested.trim().to_string()), actual);
    flush(false);
    status
}

pub fn record_rollback(path: &str) {
    if path.is_empty() {
        return;
    }
    let actual = read_actual(path);
    store(path, Status::RolledBack, None, actual);
    flush(false);
}

pub fn last_status(path: &str) -> Option<Status> {
    let guard = STATE.lock().unwrap_or_else(|error| error.into_inner());
    guard
        .as_ref()
        .and_then(|state| state.entries.get(path).map(|entry| entry.status))
}

pub fn probe_all() {
    with_state(|state| {
        for path in PROBES {
            if state.entries.contains_key(*path) {
                continue;
            }
            let status = if !Path::new(path).exists() {
                Status::Unsupported
            } else if fs::metadata(path)
                .map(|meta| meta.is_dir())
                .unwrap_or(false)
            {
                Status::Unknown
            } else if fs::read_to_string(path).is_ok() {
                if writable(path) {
                    Status::Unknown
                } else {
                    Status::ReadOnly
                }
            } else {
                Status::WriteOnly
            };
            state.entries.insert(
                path.to_string(),
                Entry {
                    path: path.to_string(),
                    category: category_for(path).to_string(),
                    status,
                    requested: None,
                    actual: None,
                    detail: detail_for(status),
                    updated_at_ms: now_ms(),
                },
            );
        }
        state.dirty = true;
    });
    flush(true);
}

pub fn mode() -> String {
    fs::read_to_string(MODE_PATH)
        .ok()
        .map(|value| value.trim().to_ascii_lowercase())
        .filter(|value| value == "strict" || value == "observe")
        .unwrap_or_else(|| "observe".to_string())
}

pub fn strict_mode() -> bool {
    mode() == "strict"
}

pub fn set_mode(value: &str) -> bool {
    let normalized = if value.trim().eq_ignore_ascii_case("strict") {
        "strict"
    } else {
        "observe"
    };
    let _ = fs::create_dir_all(CONFIG_DIR);
    if fs::write(MODE_PATH, normalized).is_err() {
        return false;
    }
    let _ = fs::set_permissions(MODE_PATH, fs::Permissions::from_mode(0o644));
    flush(true);
    true
}

fn build_report() -> Report {
    let entries = with_state(|state| state.entries.values().cloned().collect());
    Report {
        boot_id: boot_id(),
        generated_at_ms: now_ms(),
        mode: mode(),
        entries,
    }
}

pub fn report_json() -> String {
    let empty = with_state(|state| state.entries.is_empty());
    if empty {
        probe_all();
    }
    serde_json::to_string(&build_report()).unwrap_or_else(|_| "{}".to_string())
}

pub fn log_summary(tag: &str) {
    let (
        supported,
        coerced,
        blocked,
        degraded,
        unsupported,
        unknown,
        rolled_back,
        read_only,
        write_only,
    ) = with_state(|state| {
        let mut supported = 0;
        let mut coerced = 0;
        let mut blocked = 0;
        let mut degraded = 0;
        let mut unsupported = 0;
        let mut unknown = 0;
        let mut rolled_back = 0;
        let mut read_only = 0;
        let mut write_only = 0;
        for entry in state.entries.values() {
            match entry.status {
                Status::Supported => supported += 1,
                Status::Coerced => coerced += 1,
                Status::Blocked => blocked += 1,
                Status::Degraded => degraded += 1,
                Status::Unsupported => unsupported += 1,
                Status::Unknown => unknown += 1,
                Status::RolledBack => rolled_back += 1,
                Status::ReadOnly => read_only += 1,
                Status::WriteOnly => write_only += 1,
            }
        }
        (
            supported,
            coerced,
            blocked,
            degraded,
            unsupported,
            unknown,
            rolled_back,
            read_only,
            write_only,
        )
    });
    crate::log!(
        "[CAP] {}: supported={} coerced={} blocked={} degraded={} unsupported={} unknown={} rolled_back={} read_only={} write_only={}",
        tag,
        supported,
        coerced,
        blocked,
        degraded,
        unsupported,
        unknown,
        rolled_back,
        read_only,
        write_only
    );
}

pub fn flush(force: bool) {
    let now = now_ms();
    let report = with_state(|state| {
        if !state.dirty && !force {
            return None;
        }
        if !force && now.saturating_sub(state.last_persist_ms) < PERSIST_INTERVAL_MS {
            return None;
        }
        state.dirty = false;
        state.last_persist_ms = now;
        Some(Report {
            boot_id: boot_id(),
            generated_at_ms: now,
            mode: mode(),
            entries: state.entries.values().cloned().collect(),
        })
    });
    let Some(report) = report else {
        return;
    };
    let Ok(content) = serde_json::to_string(&report) else {
        return;
    };
    let _ = fs::create_dir_all(CONFIG_DIR);
    if fs::write(REPORT_TMP, content).is_err() {
        let _ = fs::remove_file(REPORT_TMP);
        return;
    }
    let _ = fs::set_permissions(REPORT_TMP, fs::Permissions::from_mode(0o644));
    if fs::rename(REPORT_TMP, REPORT_PATH).is_err() {
        let _ = fs::remove_file(REPORT_TMP);
    }
}

pub fn flush_if_due() {
    flush(false);
}

#[cfg(test)]
mod tests {
    use super::{category_for, looks_like_value, should_log, Status};

    #[test]
    fn maps_capability_categories() {
        assert_eq!(category_for("/sys/devices/system/cpu/cpu0/cpufreq"), "cpu");
        assert_eq!(category_for("/sys/class/kgsl/kgsl-3d0/devfreq"), "gpu");
        assert_eq!(category_for("/sys/block/sda/queue/scheduler"), "storage");
        assert_eq!(
            category_for("/proc/sys/net/ipv4/tcp_low_latency"),
            "network"
        );
        assert_eq!(category_for("/sys/class/thermal/thermal_zone0"), "thermal");
        assert_eq!(
            category_for("/proc/sys/kernel/sched_nr_migrate"),
            "scheduler"
        );
        assert_eq!(category_for("persist.lumina.renderer"), "system");
    }

    #[test]
    fn only_hard_failures_request_rollback() {
        assert!(Status::Blocked.needs_rollback());
        assert!(Status::Degraded.needs_rollback());
        assert!(!Status::Supported.needs_rollback());
        assert!(!Status::Coerced.needs_rollback());
        assert!(!Status::Unknown.needs_rollback());
        assert!(!Status::ReadOnly.needs_rollback());
        assert!(!Status::WriteOnly.needs_rollback());
    }

    #[test]
    fn serializes_status_names() {
        assert_eq!(
            serde_json::to_string(&Status::RolledBack).unwrap(),
            "\"rolled_back\""
        );
        assert_eq!(Status::Supported.as_str(), "supported");
        assert_eq!(
            serde_json::to_string(&Status::WriteOnly).unwrap(),
            "\"write_only\""
        );
    }

    #[test]
    fn detects_debug_readback() {
        assert!(!looks_like_value(
            "-1",
            "[GPUFREQ-DEBUG] fix GPU/STACK OPP index is disabled"
        ));
        assert!(looks_like_value("3", "3"));
        assert!(looks_like_value("sugov_ext", "sugov_ext"));
        assert!(looks_like_value("1 1800000", "1 1800000"));
    }

    #[test]
    fn logs_only_changes_and_rollbacks() {
        assert!(should_log(None, Status::Supported));
        assert!(!should_log(Some(Status::Supported), Status::Supported));
        assert!(should_log(Some(Status::Supported), Status::Coerced));
        assert!(should_log(Some(Status::Blocked), Status::RolledBack));
        assert!(should_log(Some(Status::RolledBack), Status::RolledBack));
    }
}
