use std::process::Command;
use serde_json::Value;

#[derive(Debug, Clone, Default)]
pub struct AppConfig {
    pub enabled: bool,
    pub lite_mode: String,
    pub refresh_rate: String,
    pub renderer: String,
    pub dnd: String,
}

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

pub fn is_lite_mode_active() -> bool {
    for path in [
        "/data/adb/luminatweaks/config.json",
        "/data/adb/luminatweaks/configs/config.json",
    ] {
        if let Ok(content) = std::fs::read_to_string(path) {
            if let Ok(json) = serde_json::from_str::<Value>(&content) {
                if let Some(val) = json.get("lite_mode_global") {
                    if is_truthy(val) {
                        return true;
                    }
                }
            }
        }
    }

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

fn extract_app_config(item: &Value) -> AppConfig {
    let enabled = item.get("saklar_utama")
        .or_else(|| item.get("saklarUtama"))
        .or_else(|| item.get("enabled"))
        .map(is_truthy)
        .unwrap_or(false);

    let lite_mode = match item.get("lite_mode").or_else(|| item.get("liteMode")) {
        Some(Value::Bool(b)) => if *b { "on".to_string() } else { "off".to_string() },
        Some(Value::String(s)) => s.clone(),
        _ => "default".to_string(),
    };

    let refresh_rate = match item.get("refresh_rate").or_else(|| item.get("refreshRate")) {
        Some(Value::Number(n)) => n.to_string(),
        Some(Value::String(s)) => s.clone(),
        _ => "default".to_string(),
    };

    let renderer = match item.get("renderer") {
        Some(Value::String(s)) => s.clone(),
        _ => "default".to_string(),
    };

    let dnd = match item.get("dnd") {
        Some(Value::Bool(b)) => if *b { "on".to_string() } else { "off".to_string() },
        Some(Value::String(s)) => s.clone(),
        _ => "default".to_string(),
    };

    AppConfig {
        enabled,
        lite_mode,
        refresh_rate,
        renderer,
        dnd,
    }
}

pub fn get_app_config(pkg: &str) -> Option<AppConfig> {
    if let Ok(content) = std::fs::read_to_string("/data/adb/luminatweaks/app_config.json") {
        if let Ok(json) = serde_json::from_str::<Value>(&content) {
            if let Some(list) = json.as_array() {
                for item in list {
                    let matches_pkg = item.get("pkg").and_then(|v| v.as_str()) == Some(pkg)
                        || item.get("package").and_then(|v| v.as_str()) == Some(pkg)
                        || item.get("packageName").and_then(|v| v.as_str()) == Some(pkg);

                    if matches_pkg {
                        return Some(extract_app_config(item));
                    }
                }
            }

            if let Some(app_obj) = json.get(pkg) {
                return Some(extract_app_config(app_obj));
            }
        }
    }

    let conf_path = format!("/data/adb/luminatweaks/configs/{}.json", pkg);
    if let Ok(content) = std::fs::read_to_string(conf_path) {
        if let Ok(item) = serde_json::from_str::<Value>(&content) {
            return Some(extract_app_config(&item));
        }
    }

    None
}

pub fn is_app_enabled(pkg: &str) -> bool {
    get_app_config(pkg).map(|c| c.enabled).unwrap_or(false)
}

pub fn is_process_alive(pkg: &str) -> bool {
    if let Ok(entries) = std::fs::read_dir("/proc") {
        for entry in entries.flatten() {
            let name = entry.file_name();
            if let Some(s) = name.to_str() {
                if s.chars().all(|c| c.is_ascii_digit()) {
                    let cmdline_path = entry.path().join("cmdline");
                    if let Ok(cmd) = std::fs::read(cmdline_path) {
                        if let Some(first) = cmd.split(|&b| b == 0).next() {
                            if let Ok(proc_name) = std::str::from_utf8(first) {
                                if proc_name == pkg || proc_name.starts_with(&format!("{}:", pkg)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    false
}

pub fn is_task_in_recents(pkg: &str) -> bool {
    if let Ok(output) = Command::new("dumpsys").args(["activity", "recents"]).output() {
        if output.status.success() {
            let s = String::from_utf8_lossy(&output.stdout);
            for line in s.lines() {
                if (line.contains("Recent #") || line.contains("Task{") || line.contains("realActivity="))
                    && line.contains(pkg)
                {
                    return true;
                }
            }
            return false;
        }
    }
    true
}

pub fn is_app_retained(pkg: &str) -> bool {
    if !is_process_alive(pkg) {
        return false;
    }
    is_task_in_recents(pkg)
}

pub fn get_zen_mode() -> Option<String> {
    Command::new("settings")
        .args(["get", "global", "zen_mode"])
        .output()
        .ok()
        .map(|out| String::from_utf8_lossy(&out.stdout).trim().to_string())
}

pub fn set_zen_mode(mode: &str) {
    let _ = Command::new("settings")
        .args(["put", "global", "zen_mode", mode])
        .output();
}
