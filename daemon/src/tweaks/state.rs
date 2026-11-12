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
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::sync::Mutex;

const STATE_DIR: &str = "/data/adb/.config/lumina";
const STATE_PATH: &str = "/data/adb/.config/lumina/engine_state.json";
const BOOT_ID_PATH: &str = "/proc/sys/kernel/random/boot_id";

#[derive(Clone, Serialize, Deserialize)]
struct Entry {
    value: String,
    mode: u32,
}

#[derive(Serialize, Deserialize)]
struct Snapshot {
    boot_id: String,
    entries: BTreeMap<String, Entry>,
}

static STATE: Mutex<Option<Snapshot>> = Mutex::new(None);

fn boot_id() -> String {
    let boot_id = fs::read_to_string(BOOT_ID_PATH)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty());
    if let Some(boot_id) = boot_id {
        return boot_id;
    }

    let btime = fs::read_to_string("/proc/stat").ok().and_then(|content| {
        content.lines().find_map(|line| {
            line.strip_prefix("btime ")
                .map(|value| value.trim().to_string())
                .filter(|value| !value.is_empty())
        })
    });
    btime
        .map(|value| format!("btime:{}", value))
        .unwrap_or_else(|| "unknown".to_string())
}

fn load_snapshot() -> Snapshot {
    let boot_id = boot_id();
    let content = match fs::read_to_string(STATE_PATH) {
        Ok(content) => content,
        Err(_) => {
            return Snapshot {
                boot_id,
                entries: BTreeMap::new(),
            }
        }
    };

    match serde_json::from_str::<Snapshot>(&content) {
        Ok(snapshot) if snapshot.boot_id == boot_id => snapshot,
        Ok(_) => {
            let _ = fs::remove_file(STATE_PATH);
            Snapshot {
                boot_id,
                entries: BTreeMap::new(),
            }
        }
        Err(_) => {
            let _ = fs::remove_file(STATE_PATH);
            Snapshot {
                boot_id,
                entries: BTreeMap::new(),
            }
        }
    }
}

fn persist(snapshot: &Snapshot) {
    let Ok(content) = serde_json::to_string(snapshot) else {
        return;
    };
    let _ = fs::create_dir_all(STATE_DIR);
    let temp_path = format!("{}.tmp", STATE_PATH);
    if fs::write(&temp_path, content).is_err() {
        let _ = fs::remove_file(&temp_path);
        return;
    }
    let _ = fs::set_permissions(&temp_path, fs::Permissions::from_mode(0o644));
    if fs::rename(&temp_path, STATE_PATH).is_err() {
        let _ = fs::remove_file(&temp_path);
    }
}

fn capture(path: &str) -> bool {
    let metadata = match fs::metadata(path) {
        Ok(metadata) => metadata,
        Err(_) => return false,
    };
    let value = match fs::read_to_string(path) {
        Ok(value) => value,
        Err(_) => return false,
    };
    let mode = metadata.permissions().mode() & 0o777;

    let mut guard = STATE.lock().unwrap_or_else(|error| error.into_inner());
    let snapshot = guard.get_or_insert_with(load_snapshot);

    let boot_id = boot_id();
    if snapshot.boot_id != boot_id {
        snapshot.boot_id = boot_id;
        snapshot.entries.clear();
    }

    if snapshot.entries.contains_key(path) {
        return true;
    }

    snapshot.entries.insert(
        path.to_string(),
        Entry {
            value,
            mode,
        },
    );
    persist(snapshot);
    true
}

pub fn write_tracked(path: &str, value: &str, lock: bool) -> bool {
    if !capture(path) {
        return false;
    }
    crate::tweaks::engine::write_sysfs(path, value, lock)
}

pub fn restore_all() {
    let mut guard = STATE.lock().unwrap_or_else(|error| error.into_inner());
    let snapshot = match guard.take() {
        Some(snapshot) => snapshot,
        None => load_snapshot(),
    };

    if snapshot.entries.is_empty() {
        let _ = fs::remove_file(STATE_PATH);
        return;
    }

    if snapshot.boot_id != boot_id() {
        let _ = fs::remove_file(STATE_PATH);
        crate::log!("[ENGINE] Discarded stale snapshot from another boot");
        return;
    }

    let mut restored = 0;
    for (path, entry) in &snapshot.entries {
        if !Path::new(path).exists() {
            continue;
        }
        let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o644));
        if fs::write(path, entry.value.as_bytes()).is_ok() {
            restored += 1;
        }
        let _ = fs::set_permissions(path, fs::Permissions::from_mode(entry.mode));
    }

    let _ = fs::remove_file(STATE_PATH);
    crate::log!("[ENGINE] Restored {} original node(s)", restored);
}
