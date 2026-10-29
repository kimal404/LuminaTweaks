use std::process::Command;

/// Extracts the currently focused foreground package from window manager
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

/// Verifies whether the master switch is enabled
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

/// Checks if Android Battery Saver mode is active
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

/// Checks if Global Lite Mode is active
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
            if trimmed == "1" || trimmed.eq_ignore_ascii_case("true") {
                return true;
            }
        }
    }

    if is_low_power_active() {
        return true;
    }

    false
}

/// Checks whether an app has active performance profile enabled
pub fn is_app_enabled(pkg: &str) -> bool {
    let conf_path = format!("/data/adb/luminatweaks/configs/{}.json", pkg);
    if let Ok(content) = std::fs::read_to_string(conf_path) {
        return content.contains("\"saklarUtama\":true") || content.contains("\"saklarUtama\": true");
    }
    pkg == "flar2.devcheck"
}

/// Checks whether an app specifically has Lite Mode enabled in its per-app JSON config
pub fn is_app_lite_mode(pkg: &str) -> bool {
    let conf_path = format!("/data/adb/luminatweaks/configs/{}.json", pkg);
    if let Ok(content) = std::fs::read_to_string(conf_path) {
        let lower = content.to_ascii_lowercase();
        return lower.contains("\"modelite\":true")
            || lower.contains("\"modelite\": true")
            || lower.contains("\"litemode\":true")
            || lower.contains("\"litemode\": true")
            || lower.contains("\"islite\":true")
            || lower.contains("\"islite\": true")
            || lower.contains("\"performancelite\":true")
            || lower.contains("\"performancelite\": true")
            || lower.contains("\"profile\":\"lite\"")
            || lower.contains("\"profile\": \"lite\"")
            || lower.contains("\"profile\":\"performance_lite\"")
            || lower.contains("\"profile\": \"performance_lite\"");
    }
    false
}
