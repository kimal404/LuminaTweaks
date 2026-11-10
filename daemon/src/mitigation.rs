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

use serde_json::Value;
use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::sync::{OnceLock, RwLock};

const CONFIG_DIR: &str = "/data/adb/.config/lumina";
const MODULE_DIR: &str = "/data/adb/modules/luminatweaks";

#[derive(Default, Clone)]
struct Mitigation {
    items: HashSet<String>,
}

static STATE: OnceLock<RwLock<Mitigation>> = OnceLock::new();

fn read_json(path: &str) -> Option<Value> {
    fs::read_to_string(path)
        .ok()
        .and_then(|text| serde_json::from_str::<Value>(&text).ok())
}

fn config_enabled() -> bool {
    for path in [format!("{}/config.json", CONFIG_DIR), format!("{}/config.json", MODULE_DIR)] {
        if let Some(json) = read_json(&path) {
            return json
                .get("device_mitigation")
                .and_then(Value::as_bool)
                .unwrap_or(false);
        }
    }
    false
}

fn props_match(rule: &Value) -> bool {
    let Some(props) = rule.get("props").and_then(Value::as_object) else {
        return false;
    };
    if props.is_empty() {
        return false;
    }

    props.iter().all(|(prop, expected)| {
        let Some(expected) = expected.as_str() else {
            return false;
        };
        let actual = crate::detector::get_system_prop(prop).to_ascii_lowercase();
        !actual.is_empty() && actual.contains(&expected.to_ascii_lowercase())
    })
}

fn collect_items(target: &mut HashSet<String>, value: Option<&Value>) {
    if let Some(items) = value.and_then(Value::as_array) {
        for item in items {
            if let Some(name) = item.as_str() {
                let name = name.trim();
                if !name.is_empty() {
                    target.insert(name.to_ascii_uppercase());
                }
            }
        }
    }
}

fn load() -> Mitigation {
    let mut mitigation = Mitigation::default();
    if !config_enabled() {
        return mitigation;
    }

    let path = [
        format!("{}/device_mitigation.json", CONFIG_DIR),
        format!("{}/device_mitigation.json", MODULE_DIR),
    ]
    .into_iter()
    .find(|p| Path::new(p).exists());

    let Some(path) = path else {
        return mitigation;
    };
    let Some(json) = read_json(&path) else {
        crate::log!("[MITIGATION] Ignoring malformed {}", path);
        return mitigation;
    };

    collect_items(&mut mitigation.items, json.get("global"));

    if let Some(devices) = json.get("devices").and_then(Value::as_array) {
        for rule in devices {
            if props_match(rule) {
                collect_items(&mut mitigation.items, rule.get("items"));
            }
        }
    }

    mitigation
}

pub fn init() {
    let state = STATE.get_or_init(|| RwLock::new(Mitigation::default()));
    if let Ok(mut guard) = state.write() {
        *guard = load();
        if !guard.items.is_empty() {
            crate::log!("[MITIGATION] Active items: {:?}", guard.items);
        }
    }
}

pub fn reload() {
    let Some(state) = STATE.get() else {
        init();
        return;
    };
    if let Ok(mut guard) = state.write() {
        *guard = load();
        crate::log!("[MITIGATION] Reloaded ({} active item(s))", guard.items.len());
    }
}

pub fn active(item: &str) -> bool {
    STATE
        .get()
        .and_then(|state| state.read().ok().map(|guard| guard.items.contains(item)))
        .unwrap_or(false)
}
