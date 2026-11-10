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
use std::sync::{OnceLock, RwLock};

const CONFIG_DIR: &str = "/data/adb/.config/lumina";
const MODULE_DIR: &str = "/data/adb/modules/luminatweaks";

#[derive(Clone)]
struct PathRule {
    path: String,
    value: String,
}

#[derive(Default, Clone)]
struct QuirkRules {
    items: HashSet<String>,
    writes: Vec<PathRule>,
}

static STATE: OnceLock<RwLock<QuirkRules>> = OnceLock::new();

fn read_json(path: &str) -> Option<Value> {
    fs::read_to_string(path)
        .ok()
        .and_then(|text| serde_json::from_str::<Value>(&text).ok())
}

fn config_enabled() -> bool {
    for path in [
        format!("{}/config.json", CONFIG_DIR),
        format!("{}/config.json", MODULE_DIR),
    ] {
        if let Some(json) = read_json(&path) {
            if let Some(enabled) = json.get("hardware_quirks").and_then(Value::as_bool) {
                return enabled;
            }
        }
    }

    let prop = crate::detector::get_system_prop("persist.lumina.hardware_quirks");
    prop == "1" || prop.eq_ignore_ascii_case("true")
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

fn collect_writes(target: &mut Vec<PathRule>, value: Option<&Value>) {
    let Some(rules) = value.and_then(Value::as_array) else {
        return;
    };

    for rule in rules {
        let Some(path) = rule.get("path").and_then(Value::as_str) else {
            continue;
        };
        if path.contains("..") || (!path.starts_with("/sys/") && !path.starts_with("/proc/")) {
            continue;
        }

        let value = match rule.get("value") {
            Some(Value::String(text)) => text.clone(),
            Some(Value::Number(number)) => number.to_string(),
            Some(Value::Bool(flag)) => flag.to_string(),
            _ => continue,
        };

        target.push(PathRule {
            path: path.to_string(),
            value,
        });
    }
}

fn collect_rule(target: &mut QuirkRules, value: Option<&Value>) {
    collect_items(&mut target.items, value.and_then(|rule| rule.get("items")));
    collect_writes(
        &mut target.writes,
        value.and_then(|rule| rule.get("writes")),
    );
}

fn load() -> QuirkRules {
    let mut rules = QuirkRules::default();
    if !config_enabled() {
        return rules;
    }

    let json = [
        format!("{}/hardware_quirks.json", CONFIG_DIR),
        format!("{}/hardware_quirks.json", MODULE_DIR),
    ]
    .into_iter()
    .find_map(|path| read_json(&path));

    let Some(json) = json else {
        return rules;
    };

    if let Some(global) = json.get("global") {
        if global.is_array() {
            collect_items(&mut rules.items, Some(global));
        } else {
            collect_rule(&mut rules, Some(global));
        }
    }
    collect_writes(&mut rules.writes, json.get("writes"));

    if let Some(devices) = json.get("devices").and_then(Value::as_array) {
        for rule in devices {
            if props_match(rule) {
                collect_rule(&mut rules, Some(rule));
            }
        }
    }

    rules
}

pub fn apply_writes() {
    let Some(state) = STATE.get() else {
        return;
    };
    let Ok(guard) = state.read() else {
        return;
    };
    if guard.writes.is_empty() {
        return;
    }

    for rule in &guard.writes {
        crate::engine::write_sysfs(&rule.path, &rule.value, false);
    }
    crate::log!("[QUIRKS] Applied {} path rule(s)", guard.writes.len());
}

pub fn init() {
    let rules = load();
    let active_items = rules.items.len();

    let state = STATE.get_or_init(|| RwLock::new(QuirkRules::default()));
    if let Ok(mut guard) = state.write() {
        *guard = rules;
    }

    if active_items > 0 {
        crate::log!("[QUIRKS] Active items: {}", active_items);
    }
    apply_writes();
}

pub fn reload() {
    let Some(state) = STATE.get() else {
        init();
        return;
    };

    let rules = load();
    let active_items = rules.items.len();
    let path_rules = rules.writes.len();
    if let Ok(mut guard) = state.write() {
        *guard = rules;
    }

    crate::log!(
        "[QUIRKS] Reloaded ({} item(s), {} path rule(s))",
        active_items,
        path_rules
    );
    apply_writes();
}

pub fn active(item: &str) -> bool {
    STATE
        .get()
        .and_then(|state| state.read().ok().map(|guard| guard.items.contains(item)))
        .unwrap_or(false)
}
