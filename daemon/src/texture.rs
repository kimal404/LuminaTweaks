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

use std::fs;
use std::process::Command;
use serde_json::Value;
use crate::log;

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

    pub fn as_str(&self) -> &'static str {
        match self {
            TextureDriver::Native => "native",
            TextureDriver::Angle => "angle",
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

pub struct TextureController;

impl TextureController {
    pub fn ensure_setup() {
        Self::sync_config(None);
    }

    pub fn build_features_string(lod: i32, pixel_sharp: bool) -> String {
        let mut features = Vec::new();

        if pixel_sharp {
            features.push("forceNearestFiltering");
        }

        match lod.clamp(-3, 5) {
            -3 => {
                features.push("maximumAnisotropy");
            }
            -2 => {
                features.push("highQualityFiltering");
            }
            -1 => {}
            0 => {}
            1 => {
                features.push("forceTextureLodOffset1");
            }
            2 => {
                features.push("forceTextureLodOffset2");
            }
            3 => {
                features.push("forceTextureLodOffset3");
            }
            4 => {
                features.push("forceTextureLodOffset4");
            }
            5 => {
                features.push("forceTextureLodOffset5");
            }
            _ => (),
        }

        features.join(":")
    }

    pub fn sync_config(focused_pkg: Option<&str>) {
        let config_path = "/data/adb/luminatweaks/app_config.json";
        let content = match fs::read_to_string(config_path) {
            Ok(c) => c,
            Err(_) => {
                Self::restore();
                return;
            }
        };

        let json: Value = match serde_json::from_str(&content) {
            Ok(j) => j,
            Err(_) => {
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
            let master = item
                .get("saklar_utama")
                .or_else(|| item.get("saklarUtama"))
                .or_else(|| item.get("enabled"))
                .map(is_truthy)
                .unwrap_or(false);

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

            let lod = raw_lod.clamp(-3, 5);

            let pixel_sharp = item
                .get("pixel_sharp")
                .or_else(|| item.get("pixelSharp"))
                .map(is_truthy)
                .unwrap_or(false);

            let pkg_name = item
                .get("pkg")
                .or_else(|| item.get("package"))
                .or_else(|| item.get("packageName"))
                .and_then(|v| v.as_str())
                .map(|s| s.to_string())
                .or(key_opt);

            if master && driver == TextureDriver::Angle {
                if let Some(pkg) = pkg_name {
                    active_pkgs.push(pkg.clone());

                    let feat = Self::build_features_string(lod, pixel_sharp);
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
        }

        if active_pkgs.is_empty() {
            Self::restore();
            return;
        }

        let pkgs_joined = active_pkgs.join(",");
        let values_joined = vec!["angle"; active_pkgs.len()].join(",");

        set_global_setting("angle_gl_driver_selection_pkgs", &pkgs_joined);
        set_global_setting("angle_gl_driver_selection_values", &values_joined);

        let applied_features = if focused_pkg.is_some() {
            target_features.unwrap_or_else(|| fallback_features.unwrap_or_default())
        } else {
            fallback_features.unwrap_or_default()
        };

        let _ = Command::new("setprop")
            .args(["debug.angle.feature_overrides_enabled", &applied_features])
            .status();

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
        let _ = Command::new("setprop")
            .args(["debug.angle.feature_overrides_enabled", ""])
            .status();
        delete_global_setting("angle_gl_driver_selection_pkgs");
        delete_global_setting("angle_gl_driver_selection_values");

        log!("[TEXTURE] All ANGLE driver settings restored to Native");
    }
}
