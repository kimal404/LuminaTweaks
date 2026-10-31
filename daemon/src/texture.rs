use std::fs;
use std::process::Command;
use serde_json::Value;
use crate::log;

pub struct TextureController;

impl TextureController {
    pub fn ensure_setup() {
        Self::sync_config();
    }

    pub fn sync_config() {
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
        let mut first_features: Option<String> = None;

        if let Some(list) = json.as_array() {
            for item in list {
                let master = item.get("saklar_utama")
                    .or_else(|| item.get("saklarUtama"))
                    .map(|v| match v {
                        Value::Bool(b) => *b,
                        Value::String(s) => s == "true" || s == "1" || s == "on",
                        _ => false,
                    })
                    .unwrap_or(false);

                let tex_enabled = item.get("texture_enabled")
                    .or_else(|| item.get("textureEnabled"))
                    .map(|v| match v {
                        Value::Bool(b) => *b,
                        Value::String(s) => s == "true" || s == "1" || s == "on",
                        _ => false,
                    })
                    .unwrap_or(false);

                let detail = match item.get("texture_detail").or_else(|| item.get("textureDetail")) {
                    Some(Value::Number(n)) => n.as_i64().unwrap_or(0) as i32,
                    Some(Value::String(s)) => s.parse::<i32>().unwrap_or(0),
                    _ => 0,
                };

                let sharp = item.get("pixel_sharp")
                    .or_else(|| item.get("pixelSharp"))
                    .map(|v| match v {
                        Value::Bool(b) => *b,
                        Value::String(s) => s == "true" || s == "1" || s == "on",
                        _ => false,
                    })
                    .unwrap_or(false);

                let hard = item.get("hard_distance")
                    .or_else(|| item.get("hardDistance"))
                    .map(|v| match v {
                        Value::Bool(b) => *b,
                        Value::String(s) => s == "true" || s == "1" || s == "on",
                        _ => false,
                    })
                    .unwrap_or(false);

                // Tetap aktif jika saklar ON dan salah satu fitur/toggle grafis hidup
                let has_feature = detail > 0 || sharp || hard;

                if master && tex_enabled && has_feature {
                    if let Some(pkg) = item.get("pkg").and_then(|v| v.as_str()) {
                        active_pkgs.push(pkg.to_string());

                        if first_features.is_none() {
                            first_features = Some(Self::build_features_string(detail, sharp, hard));
                        }
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

        let _ = Command::new("settings")
            .args(["put", "global", "angle_gl_driver_selection_pkgs", &pkgs_joined])
            .status();
        let _ = Command::new("settings")
            .args(["put", "global", "angle_gl_driver_selection_values", &values_joined])
            .status();

        if let Some(ref feat) = first_features {
            if !feat.is_empty() {
                let _ = Command::new("setprop")
                    .args(["debug.angle.feature_overrides_enabled", feat])
                    .status();
            }
        }

        log!("[TEXTURE] Registered ANGLE packages -> [{}]", pkgs_joined);
    }

    fn build_features_string(detail: i32, sharp: bool, hard: bool) -> String {
        let mut features = Vec::new();
        if sharp {
            features.push("forceNearestFiltering");
        }
        if hard {
            features.push("forceNearestMipFiltering");
        }

        match detail {
            1 => features.push("forceTextureLodOffset1"),
            2 => features.push("forceTextureLodOffset2"),
            3 => features.push("forceTextureLodOffset3"),
            4 => features.push("forceTextureLodOffset4"),
            _ => (),
        }

        features.join(":")
    }

    pub fn apply(pkg: &str, enabled: bool, detail: i32, sharp: bool, hard: bool) {
        if !enabled {
            return;
        }

        let overrides = Self::build_features_string(detail, sharp, hard);
        if !overrides.is_empty() {
            let _ = Command::new("setprop")
                .args(["debug.angle.feature_overrides_enabled", &overrides])
                .status();
            log!("[TEXTURE] Foreground: {} -> Overrides: [{}]", pkg, overrides);
        }
    }

    pub fn restore() {
        let _ = Command::new("setprop")
            .args(["debug.angle.feature_overrides_enabled", ""])
            .status();

        let _ = Command::new("settings")
            .args(["delete", "global", "angle_gl_driver_selection_pkgs"])
            .status();

        let _ = Command::new("settings")
            .args(["delete", "global", "angle_gl_driver_selection_values"])
            .status();

        log!("[TEXTURE] All ANGLE overrides cleared");
    }
}
