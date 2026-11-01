use std::fs;
use std::process::Command;
use serde_json::Value;
use crate::log;

/// Supported GPU graphics drivers for game profiles
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

    /// Builds the ANGLE feature overrides string for a given LOD (-3 to +3)
    pub fn build_features_string(lod: i32) -> String {
        let mut features = Vec::new();

        match lod.clamp(-3, 3) {
            // Negative values: Enhance quality
            -3 => {
                // Force maximum anisotropic filtering for crisp high-resolution textures
                features.push("maximumAnisotropy");
            }
            -2 => {
                // High quality texture filtering
                features.push("highQualityFiltering");
            }
            -1 => {
                // Slightly sharper: standard linear filtering, nearest filtering explicitly off
            }
            // 0: Default, no overrides
            0 => {}
            // Positive values: Downscale for performance
            1 => {
                features.push("forceTextureLodOffset1");
            }
            2 => {
                features.push("forceTextureLodOffset2");
            }
            3 => {
                features.push("forceTextureLodOffset3");
                features.push("forceNearestFiltering");
            }
            _ => (),
        }

        features.join(":")
    }

    /// Directly applies driver and LOD configuration for a specific package
    pub fn apply(pkg: &str, driver: TextureDriver, lod: i32) {
        match driver {
            TextureDriver::Native => {
                // Revert package to system native GPU driver
                let current_pkgs = get_global_setting("angle_gl_driver_selection_pkgs");
                if let Some(pkgs_str) = current_pkgs {
                    let filtered: Vec<&str> = pkgs_str
                        .split(',')
                        .map(|s| s.trim())
                        .filter(|p| !p.is_empty() && *p != pkg)
                        .collect();

                    if filtered.is_empty() {
                        Self::restore();
                    } else {
                        let values = vec!["angle"; filtered.len()].join(",");
                        set_global_setting("angle_gl_driver_selection_pkgs", &filtered.join(","));
                        set_global_setting("angle_gl_driver_selection_values", &values);
                        let _ = Command::new("setprop")
                            .args(["debug.angle.feature_overrides_enabled", ""])
                            .status();
                    }
                } else {
                    Self::restore();
                }

                log!("[TEXTURE] Reverted [{}] to Native GPU driver", pkg);
            }
            TextureDriver::Angle => {
                // Activate ANGLE runtime and apply LOD feature overrides
                let mut pkgs: Vec<String> = get_global_setting("angle_gl_driver_selection_pkgs")
                    .map(|s| {
                        s.split(',')
                            .map(|p| p.trim().to_string())
                            .filter(|p| !p.is_empty())
                            .collect()
                    })
                    .unwrap_or_default();

                if !pkgs.contains(&pkg.to_string()) {
                    pkgs.push(pkg.to_string());
                }

                let values = vec!["angle"; pkgs.len()].join(",");
                set_global_setting("angle_gl_driver_selection_pkgs", &pkgs.join(","));
                set_global_setting("angle_gl_driver_selection_values", &values);

                let features = Self::build_features_string(lod);
                let _ = Command::new("setprop")
                    .args(["debug.angle.feature_overrides_enabled", &features])
                    .status();

                log!(
                    "[TEXTURE] Applied ANGLE for [{}] with LOD [{}] -> Overrides: [{}]",
                    pkg,
                    lod,
                    features
                );
            }
        }
    }

    /// Reads app_config.json and synchronizes global ANGLE settings across all packages
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

            // Read driver selection (defaults to Native)
            let driver_str = item
                .get("texture_driver")
                .or_else(|| item.get("textureDriver"))
                .and_then(|v| v.as_str());

            let driver = match driver_str {
                Some(s) => TextureDriver::from_str(s),
                None => {
                    // Fallback to legacy texture_enabled flag if texture_driver is absent
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

            // Read bidirectional LOD offset (-3 to +3)
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

            let lod = raw_lod.clamp(-3, 3);

            let pkg_name = item
                .get("pkg")
                .or_else(|| item.get("package"))
                .or_else(|| item.get("packageName"))
                .and_then(|v| v.as_str())
                .map(|s| s.to_string())
                .or(key_opt);

            // ANGLE is only enabled when master switch is active and driver is set to Angle
            if master && driver == TextureDriver::Angle {
                if let Some(pkg) = pkg_name {
                    active_pkgs.push(pkg.clone());

                    let feat = Self::build_features_string(lod);
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

    /// Resets all ANGLE driver settings and feature overrides
    pub fn restore() {
        let _ = Command::new("setprop")
            .args(["debug.angle.feature_overrides_enabled", ""])
            .status();
        delete_global_setting("angle_gl_driver_selection_pkgs");
        delete_global_setting("angle_gl_driver_selection_values");

        log!("[TEXTURE] All ANGLE driver settings restored to Native");
    }
}
