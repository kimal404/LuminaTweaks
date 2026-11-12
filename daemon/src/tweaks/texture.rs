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

use crate::log;
use serde_json::Value;
use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;

const ANGLE_BACKUP_PATH: &str = "/data/adb/.config/lumina/angle_backup.json";
static ANGLE_APPLIED: AtomicBool = AtomicBool::new(false);
static LAST_SYNC_STATE: Mutex<Option<String>> = Mutex::new(None);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TextureDriver {
    Native,
    Angle,
}

impl TextureDriver {
    pub fn from_str(s: &str) -> Self {
        match s.trim().to_ascii_lowercase().as_str() {
            "angle" => TextureDriver::Angle,
            _ => TextureDriver::Native,
        }
    }
}

fn is_truthy(val: &Value) -> bool {
    if let Some(b) = val.as_bool() {
        return b;
    }
    if let Some(s) = val.as_str() {
        let lower = s.trim().to_ascii_lowercase();
        return lower == "true" || lower == "1" || lower == "on";
    }
    if let Some(n) = val.as_i64() {
        return n == 1;
    }
    false
}

fn get_global_setting(key: &str) -> Option<String> {
    let output = Command::new("settings")
        .args(["get", "global", key])
        .output()
        .ok()?;

    if output.status.success() {
        let s = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if s.is_empty() || s == "null" {
            None
        } else {
            Some(s)
        }
    } else {
        None
    }
}

fn set_global_setting(key: &str, value: &str) {
    let _ = Command::new("settings")
        .args(["put", "global", key, value])
        .status();
}

fn delete_global_setting(key: &str) {
    let _ = Command::new("settings")
        .args(["delete", "global", key])
        .status();
}

fn save_angle_backup() {
    if Path::new(ANGLE_BACKUP_PATH).exists() {
        return;
    }

    let Some(parent) = Path::new(ANGLE_BACKUP_PATH).parent() else {
        return;
    };
    let _ = fs::create_dir_all(parent);

    let backup = serde_json::json!({
        "pkgs": get_global_setting("angle_gl_driver_selection_pkgs"),
        "values": get_global_setting("angle_gl_driver_selection_values"),
        "features": crate::core::detector::get_system_prop("debug.angle.feature_overrides_enabled"),
    });
    let _ = fs::write(ANGLE_BACKUP_PATH, backup.to_string());
}

fn merge_with_user_angle_settings(lumina_pkgs: &[String]) -> (String, String) {
    let lumina_set: HashSet<&str> = lumina_pkgs.iter().map(|s| s.as_str()).collect();
    let mut merged: Vec<(String, String)> = Vec::new();
    let mut seen: HashSet<String> = HashSet::new();

    if let Ok(content) = fs::read_to_string(ANGLE_BACKUP_PATH) {
        if let Ok(backup) = serde_json::from_str::<Value>(&content) {
            let orig_pkgs = backup.get("pkgs").and_then(|v| v.as_str()).unwrap_or("");
            let orig_vals = backup.get("values").and_then(|v| v.as_str()).unwrap_or("");

            if !orig_pkgs.is_empty() {
                let pkg_iter = orig_pkgs.split(',').map(str::trim);
                let val_iter = orig_vals.split(',').map(str::trim);

                for (pkg, val) in pkg_iter.zip(val_iter) {
                    if pkg.is_empty() || !seen.insert(pkg.to_string()) {
                        continue;
                    }
                    let effective_val = if lumina_set.contains(pkg) {
                        "angle".to_string()
                    } else if val.is_empty() {
                        "default".to_string()
                    } else {
                        val.to_string()
                    };
                    merged.push((pkg.to_string(), effective_val));
                }
            }
        }
    }

    for pkg in lumina_pkgs {
        if seen.insert(pkg.clone()) {
            merged.push((pkg.clone(), "angle".to_string()));
        }
    }

    let pkgs_joined = merged
        .iter()
        .map(|(p, _)| p.as_str())
        .collect::<Vec<_>>()
        .join(",");
    let values_joined = merged
        .iter()
        .map(|(_, v)| v.as_str())
        .collect::<Vec<_>>()
        .join(",");

    (pkgs_joined, values_joined)
}

fn restore_global_setting(key: &str, value: Option<&str>) {
    match value {
        Some(v) if !v.is_empty() => set_global_setting(key, v),
        _ => delete_global_setting(key),
    }
}

fn restore_angle_backup() {
    let Ok(content) = fs::read_to_string(ANGLE_BACKUP_PATH) else {
        return;
    };
    let Ok(backup) = serde_json::from_str::<Value>(&content) else {
        let _ = fs::remove_file(ANGLE_BACKUP_PATH);
        return;
    };

    restore_global_setting(
        "angle_gl_driver_selection_pkgs",
        backup.get("pkgs").and_then(|v| v.as_str()),
    );
    restore_global_setting(
        "angle_gl_driver_selection_values",
        backup.get("values").and_then(|v| v.as_str()),
    );

    let features = backup
        .get("features")
        .and_then(|v| v.as_str())
        .unwrap_or("");
    let _ = Command::new("setprop")
        .args(["debug.angle.feature_overrides_enabled", features])
        .status();

    let _ = fs::remove_file(ANGLE_BACKUP_PATH);
}

pub struct TextureController;

impl TextureController {
    pub fn ensure_setup() {
        if Path::new(ANGLE_BACKUP_PATH).exists() {
            restore_angle_backup();
        }
        if let Ok(mut state) = LAST_SYNC_STATE.lock() {
            *state = None;
        }
        Self::sync_config(None);
    }

    pub fn build_features_string(lod: i32, pixel_sharp: bool) -> String {
        let mut features = Vec::new();

        if pixel_sharp {
            features.push("forceNearestFiltering");
        }

        match lod.clamp(0, 4) {
            1 => features.push("forceTextureLodOffset1"),
            2 => features.push("forceTextureLodOffset2"),
            3 => features.push("forceTextureLodOffset3"),
            4 => features.push("forceTextureLodOffset4"),
            _ => (),
        }

        features.join(":")
    }

    fn parse_angle_entry(key_opt: Option<String>, item: &Value) -> Option<(String, String)> {
        let master = item
            .get("saklar_utama")
            .or_else(|| item.get("saklarUtama"))
            .or_else(|| item.get("enabled"))
            .map(is_truthy)
            .unwrap_or(false);

        if !master {
            return None;
        }

        let driver_str = item
            .get("texture_driver")
            .or_else(|| item.get("textureDriver"))
            .and_then(|v| v.as_str());

        let driver = match driver_str {
            Some(s) => TextureDriver::from_str(s),
            None => {
                let legacy_enabled = item
                    .get("texture_enabled")
                    .or_else(|| item.get("textureEnabled"))
                    .map(is_truthy)
                    .unwrap_or(false);

                if legacy_enabled {
                    TextureDriver::Angle
                } else {
                    TextureDriver::Native
                }
            }
        };

        if driver != TextureDriver::Angle {
            return None;
        }

        let pkg = item
            .get("pkg")
            .or_else(|| item.get("package"))
            .or_else(|| item.get("packageName"))
            .and_then(|v| v.as_str())
            .map(|s| s.to_string())
            .or(key_opt)?;

        let raw_lod = item
            .get("texture_lod")
            .or_else(|| item.get("textureLod"))
            .or_else(|| item.get("texture_detail"))
            .or_else(|| item.get("textureDetail"))
            .and_then(|v| {
                if let Some(n) = v.as_i64() {
                    Some(n as i32)
                } else if let Some(s) = v.as_str() {
                    s.parse::<i32>().ok()
                } else {
                    None
                }
            })
            .unwrap_or(0);

        let lod = raw_lod.clamp(0, 4);

        let pixel_sharp = item
            .get("pixel_sharp")
            .or_else(|| item.get("pixelSharp"))
            .map(is_truthy)
            .unwrap_or(false);

        let features = Self::build_features_string(lod, pixel_sharp);
        Some((pkg, features))
    }

    pub fn is_angle_active_for(target_pkg: &str) -> bool {
        let Some(json) = crate::core::detector::read_app_json_cached() else {
            return false;
        };

        if let Some(list) = json.as_array() {
            for item in list {
                if let Some((pkg, _)) = Self::parse_angle_entry(None, item) {
                    if pkg == target_pkg {
                        return true;
                    }
                }
            }
        } else if let Some(map) = json.as_object() {
            for (k, v) in map {
                if let Some((pkg, _)) = Self::parse_angle_entry(Some(k.clone()), v) {
                    if pkg == target_pkg {
                        return true;
                    }
                }
            }
        }

        false
    }

    pub fn sync_config(focused_pkg: Option<&str>) {
        let json: Value = match crate::core::detector::read_app_json_cached() {
            Some(json) => json,
            None => {
                Self::restore();
                return;
            }
        };

        let mut active_pkgs: Vec<String> = Vec::new();
        let mut target_features: Option<String> = None;
        let mut fallback_features: Option<String> = None;

        let mut items: Vec<(Option<String>, &Value)> = Vec::new();
        if let Some(list) = json.as_array() {
            for item in list {
                items.push((None, item));
            }
        } else if let Some(map) = json.as_object() {
            for (k, v) in map {
                items.push((Some(k.clone()), v));
            }
        }

        for (key_opt, item) in items {
            if let Some((pkg, feat)) = Self::parse_angle_entry(key_opt, item) {
                active_pkgs.push(pkg.clone());

                if let Some(f_pkg) = focused_pkg {
                    if pkg == f_pkg {
                        target_features = Some(feat.clone());
                    }
                }
                if fallback_features.is_none() {
                    fallback_features = Some(feat);
                }
            }
        }

        if active_pkgs.is_empty() {
            Self::restore();
            return;
        }

        save_angle_backup();

        let (pkgs_joined, values_joined) = merge_with_user_angle_settings(&active_pkgs);

        let applied_features = if focused_pkg.is_some() {
            target_features.unwrap_or_else(|| fallback_features.unwrap_or_default())
        } else {
            fallback_features.unwrap_or_default()
        };

        let next_state = format!("{}|{}|{}", pkgs_joined, values_joined, applied_features);
        if let Ok(mut state_guard) = LAST_SYNC_STATE.lock() {
            if ANGLE_APPLIED.load(Ordering::Relaxed)
                && state_guard.as_deref() == Some(next_state.as_str())
            {
                return;
            }
            *state_guard = Some(next_state);
        }

        set_global_setting("angle_gl_driver_selection_pkgs", &pkgs_joined);
        set_global_setting("angle_gl_driver_selection_values", &values_joined);

        let _ = Command::new("setprop")
            .args(["debug.angle.feature_overrides_enabled", &applied_features])
            .status();
        ANGLE_APPLIED.store(true, Ordering::Relaxed);

        log!(
            "[TEXTURE] Synced ANGLE pkgs: [{}] -> Overrides: [{}]",
            pkgs_joined,
            applied_features
        );
    }

    pub fn apply_foreground(pkg: &str) {
        Self::sync_config(Some(pkg));
    }

    pub fn restore() {
        if let Ok(mut state) = LAST_SYNC_STATE.lock() {
            *state = None;
        }

        let was_applied = ANGLE_APPLIED.swap(false, Ordering::Relaxed);
        if !was_applied && !Path::new(ANGLE_BACKUP_PATH).exists() {
            return;
        }

        restore_angle_backup();
        log!("[TEXTURE] ANGLE driver settings restored to previous values");
    }
}
