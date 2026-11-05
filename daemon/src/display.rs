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
use std::io::{BufRead, BufReader};
use std::process::{Command, Stdio};
use std::thread;
use std::time::Duration;
use serde_json::Value;

pub const CONFIG_DIR: &str = "/data/adb/.config/lumina";

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
        format!("{}/current_rr.txt", CONFIG_DIR),
        "/data/adb/modules/lumina_tweaks/current_rr.txt".to_string(),
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

    let rr_path = format!("{}/rr_modes.json", CONFIG_DIR);
    if let Ok(content) = fs::read_to_string(rr_path) {
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

        let _ = fs::create_dir_all(CONFIG_DIR);
        let _ = fs::write(format!("{}/rr_modes.json", CONFIG_DIR), &json_str);
    }

    if let Some(saved_id) = get_saved_mode_id() {
        apply_refresh_rate_id(saved_id);
    }

    start_screen_toggle_listener();
}
