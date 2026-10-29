use std::process::Command;
use serde_json::Value;

/// Ekstraksi package foreground aktif dari dumpsys window
pub fn get_focused_package() -> Option<String> {
    let output = Command::new("dumpsys")
        .args(["window"])
        .output()
        .ok()?;

    if !output.status.success() {
        return None;
    }

    let stdout = String::from_utf8_lossy(&output.stdout);

    for line in stdout.lines() {
        if line.contains("mCurrentFocus") || line.contains("mFocusedApp") {
            if let Some(token) = line.split_whitespace().find(|t| t.contains('/')) {
                let cleaned = token.trim_start_matches('{').trim_end_matches('}');
                if let Some(pkg) = cleaned.split('/').next() {
                    if !pkg.is_empty() && pkg != "null" {
                        return Some(pkg.to_string());
                    }
                }
            }
        }
    }

    None
}

/// Cek master switch global
pub fn is_master_enabled() -> bool {
    if let Ok(output) = Command::new("getprop").arg("persist.lumina.master").output() {
        let val = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if val == "1" || val == "true" {
            return true;
        } else if val == "0" || val == "false" {
            return false;
        }
    }

    if let Ok(content) = std::fs::read_to_string("/data/adb/luminatweaks/master.conf") {
        return content.trim() == "1" || content.trim() == "true";
    }

    true
}

/// Cek Android Battery Saver
pub fn is_low_power_active() -> bool {
    if let Ok(output) = Command::new("getprop").arg("low_power").output() {
        if String::from_utf8_lossy(&output.stdout).trim() == "1" {
            return true;
        }
    }

    if let Ok(output) = Command::new("settings").args(["get", "global", "low_power"]).output() {
        if String::from_utf8_lossy(&output.stdout).trim() == "1" {
            return true;
        }
    }

    false
}

/// Cek status Mode Lite Global
pub fn is_lite_mode_active() -> bool {
    if let Ok(output) = Command::new("getprop").arg("persist.lumina.litemode").output() {
        let val = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if val == "1" || val == "true" {
            return true;
        } else if val == "0" || val == "false" {
            return false;
        }
    }

    for path in ["/data/adb/luminatweaks/configs/lite_mode", "/data/adb/luminatweaks/lite_mode.conf"] {
        if let Ok(content) = std::fs::read_to_string(path) {
            let trimmed = content.trim();
            if trimmed == "1" || trimmed.eq_ignore_ascii_case("true") || trimmed.eq_ignore_ascii_case("on") {
                return true;
            }
        }
    }

    if is_low_power_active() {
        return true;
    }

    false
}

/// Evaluasi nilai boolean, integer, atau string ("true", "1", "on")
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

/// Helper untuk menginspeksi nilai field per-package dari JSON secara presisi
fn get_package_field(pkg: &str, field_keys: &[&str]) -> bool {
    // 1. Cek dari file master app_config.json
    if let Ok(content) = std::fs::read_to_string("/data/adb/luminatweaks/app_config.json") {
        if let Ok(json) = serde_json::from_str::<Value>(&content) {
            // Skema Array: [{"pkg": "flar2.devcheck", "saklar_utama": true, "lite_mode": "on"}]
            if let Some(list) = json.as_array() {
                for item in list {
                    let matches_pkg = item.get("pkg").and_then(|v| v.as_str()) == Some(pkg)
                        || item.get("package").and_then(|v| v.as_str()) == Some(pkg)
                        || item.get("packageName").and_then(|v| v.as_str()) == Some(pkg);

                    if matches_pkg {
                        for key in field_keys {
                            if let Some(val) = item.get(*key) {
                                if is_truthy(val) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }

            // Skema Map: {"flar2.devcheck": {"saklar_utama": true, "lite_mode": "on"}}
            if let Some(app_obj) = json.get(pkg) {
                for key in field_keys {
                    if let Some(val) = app_obj.get(*key) {
                        if is_truthy(val) {
                            return true;
                        }
                    }
                }
            }
        }
    }

    // 2. Cek dari file satuan configs/{pkg}.json
    let conf_path = format!("/data/adb/luminatweaks/configs/{}.json", pkg);
    if let Ok(content) = std::fs::read_to_string(conf_path) {
        if let Ok(json) = serde_json::from_str::<Value>(&content) {
            for key in field_keys {
                if let Some(val) = json.get(*key) {
                    if is_truthy(val) {
                        return true;
                    }
                }
            }
        }
    }

    false
}

/// Checks whether an app has active performance profile enabled
pub fn is_app_enabled(pkg: &str) -> bool {
    get_package_field(pkg, &["saklar_utama", "saklarUtama", "enabled", "isBoosted"])
}

/// Checks whether an app specifically has Lite Mode enabled
pub fn is_app_lite_mode(pkg: &str) -> bool {
    get_package_field(
        pkg,
        &[
            "lite_mode",
            "mode_lite",
            "liteMode",
            "modeLite",
            "isLite",
            "performanceLite",
        ],
    )
}
