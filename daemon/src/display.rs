use std::fs;
use std::io::{BufRead, BufReader};
use std::process::{Command, Stdio};
use std::thread;
use std::time::Duration;
use serde_json::Value;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DisplayMode {
    pub id: i32,
    pub hz: i32,
}

pub fn parse_display_modes(output: &str) -> Vec<DisplayMode> {
    let mut modes: Vec<DisplayMode> = Vec::new();
    for part in output.split("id=") {
        if !part.contains("vsyncRate=") {
            continue;
        }
        let id_str: String = part.chars().take_while(|c| c.is_ascii_digit()).collect();
        if let Ok(id) = id_str.parse::<i32>() {
            if let Some(pos) = part.find("vsyncRate=") {
                let hz_sub = &part[pos + 10..];
                let hz_str: String = hz_sub.chars().take_while(|c| c.is_ascii_digit()).collect();
                if let Ok(hz) = hz_str.parse::<i32>() {
                    if hz > 0 && !modes.iter().any(|m| m.hz == hz) {
                        modes.push(DisplayMode { id, hz });
                    }
                }
            }
        }
    }
    modes.sort_by_key(|m| m.hz);
    modes
}

pub fn apply_refresh_rate_id(id: i32) {
    let _ = Command::new("/system/bin/service")
        .args(["call", "SurfaceFlinger", "1035", "i32", &id.to_string()])
        .output();
}

pub fn get_saved_mode_id() -> Option<i32> {
    for path in [
        "/data/adb/luminatweaks/current_rr.txt",
        "/data/adb/modules/luminatweaks/current_rr.txt",
    ] {
        if let Ok(content) = fs::read_to_string(path) {
            if let Ok(id) = content.trim().parse::<i32>() {
                return Some(id);
            }
        }
    }
    None
}

pub fn resolve_mode_id(val: &str) -> Option<i32> {
    let trimmed = val.trim();
    if trimmed.is_empty() || trimmed.eq_ignore_ascii_case("default") {
        return None;
    }

    let num = trimmed.parse::<i32>().ok()?;

    if let Ok(content) = fs::read_to_string("/data/adb/luminatweaks/rr_modes.json") {
        if let Ok(json) = serde_json::from_str::<Value>(&content) {
            if let Some(arr) = json.as_array() {
                for item in arr {
                    if item.get("id").and_then(|v| v.as_i64()) == Some(num as i64) {
                        return Some(num);
                    }
                }
                for item in arr {
                    if item.get("hz").and_then(|v| v.as_i64()) == Some(num as i64) {
                        if let Some(id) = item.get("id").and_then(|v| v.as_i64()) {
                            return Some(id as i32);
                        }
                    }
                }
            }
        }
    }

    Some(num)
}

pub fn start_screen_toggle_listener() {
    thread::spawn(|| {
        loop {
            let mut child = match Command::new("/system/bin/logcat")
                .args(["-b", "events", "-s", "screen_toggled"])
                .stdout(Stdio::piped())
                .spawn()
            {
                Ok(c) => c,
                Err(_) => {
                    thread::sleep(Duration::from_secs(5));
                    continue;
                }
            };

            if let Some(stdout) = child.stdout.take() {
                let reader = BufReader::new(stdout);
                for line in reader.lines().flatten() {
                    if line.contains(": 1") {
                        thread::sleep(Duration::from_millis(300));
                        if let Some(saved_id) = get_saved_mode_id() {
                            apply_refresh_rate_id(saved_id);
                        }
                    }
                }
            }

            let _ = child.wait();
            thread::sleep(Duration::from_secs(2));
        }
    });
}

pub fn init_display_subsystem() {
    let output = match Command::new("/system/bin/dumpsys")
        .arg("SurfaceFlinger")
        .output()
    {
        Ok(out) => String::from_utf8_lossy(&out.stdout).to_string(),
        Err(_) => String::new(),
    };

    let modes = parse_display_modes(&output);
    if !modes.is_empty() {
        let entries: Vec<String> = modes
            .iter()
            .map(|m| format!(r#"{{"id":{},"hz":{}}}"#, m.id, m.hz))
            .collect();
        let json_str = format!("[{}]", entries.join(","));

        let _ = fs::create_dir_all("/data/adb/luminatweaks");
        let _ = fs::write("/data/adb/luminatweaks/rr_modes.json", &json_str);

        let mod_path = "/data/adb/modules/luminatweaks";
        if std::path::Path::new(mod_path).exists() {
            let _ = fs::write(format!("{}/rr_modes.json", mod_path), &json_str);
            let _ = fs::create_dir_all(format!("{}/webroot", mod_path));
            let _ = fs::write(format!("{}/webroot/rr_modes.json", mod_path), &json_str);
        }
    }

    if let Some(saved_id) = get_saved_mode_id() {
        apply_refresh_rate_id(saved_id);
    }

    start_screen_toggle_listener();
}
