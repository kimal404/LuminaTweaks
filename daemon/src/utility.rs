/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::fs;
use std::process::Command;
use std::time::{Duration, Instant};

const DOZE_FLAG: &str = "/data/adb/.config/lumina/aggressive_doze";
const GMS_FLAG: &str = "/data/adb/.config/lumina/gms_limit";

pub struct UtilityController {
    was_screen_on: bool,
    doze_forced: bool,
    last_gms_state: Option<bool>,
    last_doze_check: Instant,
    last_gms_check: Instant,
}

impl UtilityController {
    pub fn new() -> Self {
        Self {
            was_screen_on: true,
            doze_forced: false,
            last_gms_state: None,
            last_doze_check: Instant::now() - Duration::from_secs(10),
            last_gms_check: Instant::now() - Duration::from_secs(10),
        }
    }

    pub fn tick(&mut self) {
        if self.last_doze_check.elapsed() >= Duration::from_millis(1500) {
            self.handle_doze();
            self.last_doze_check = Instant::now();
        }
        if self.last_gms_check.elapsed() >= Duration::from_millis(2000) {
            self.handle_gms();
            self.last_gms_check = Instant::now();
        }
    }

    fn is_screen_on() -> bool {
        if let Ok(entries) = fs::read_dir("/sys/class/backlight") {
            for entry in entries.flatten() {
                let b_path = entry.path().join("brightness");
                if let Ok(val_str) = fs::read_to_string(b_path) {
                    if let Ok(val) = val_str.trim().parse::<u32>() {
                        return val > 0;
                    }
                }
            }
        }

        if let Ok(output) = Command::new("dumpsys").args(["power"]).output() {
            let s = String::from_utf8_lossy(&output.stdout);
            return s.contains("Display Power: state=ON")
                || s.contains("mWakefulness=Awake")
                || s.contains("mHoldingDisplaySuspendBlocker=true");
        }
        true
    }

    fn handle_doze(&mut self) {
        let is_doze_enabled = fs::read_to_string(DOZE_FLAG)
            .map(|s| s.trim() == "1")
            .unwrap_or(false);

        if !is_doze_enabled {
            if self.doze_forced {
                let _ = Command::new("dumpsys").args(["deviceidle", "unforce"]).status();
                self.doze_forced = false;
                log!("[DOZE] Aggressive Doze disabled -> Unforced deviceidle");
            }
            return;
        }

        let current_screen_on = Self::is_screen_on();

        if self.was_screen_on && !current_screen_on {
            let _ = Command::new("dumpsys").args(["deviceidle", "force-idle"]).status();
            self.doze_forced = true;
            log!("[DOZE] Screen turned off -> Forced Deep Sleep (deviceidle force-idle)");
        } else if !self.was_screen_on && current_screen_on {
            if self.doze_forced {
                let _ = Command::new("dumpsys").args(["deviceidle", "unforce"]).status();
                self.doze_forced = false;
                log!("[DOZE] Screen turned on -> Restored standard idle state");
            }
        }

        self.was_screen_on = current_screen_on;
    }

    fn handle_gms(&mut self) {
        let is_gms_enabled = fs::read_to_string(GMS_FLAG)
            .map(|s| s.trim() == "1")
            .unwrap_or(false);

        if self.last_gms_state != Some(is_gms_enabled) {
            if is_gms_enabled {
                let _ = Command::new("cmd")
                    .args(["appops", "set", "com.google.android.gms", "RUN_IN_BACKGROUND", "ignore"])
                    .status();
                let _ = Command::new("cmd")
                    .args(["appops", "set", "com.google.android.gms", "WAKE_LOCK", "ignore"])
                    .status();
                log!("[GMS] Restricted Google Play Services background execution & wakelocks");
            } else if self.last_gms_state.is_some() {
                let _ = Command::new("cmd")
                    .args(["appops", "set", "com.google.android.gms", "RUN_IN_BACKGROUND", "allow"])
                    .status();
                let _ = Command::new("cmd")
                    .args(["appops", "set", "com.google.android.gms", "WAKE_LOCK", "allow"])
                    .status();
                log!("[GMS] Restored Google Play Services background execution & wakelocks");
            }
            self.last_gms_state = Some(is_gms_enabled);
        }
    }

    pub fn restore(&mut self) {
        if self.doze_forced {
            let _ = Command::new("dumpsys").args(["deviceidle", "unforce"]).status();
            self.doze_forced = false;
        }

        if self.last_gms_state == Some(true) {
            let _ = Command::new("cmd")
                .args(["appops", "set", "com.google.android.gms", "RUN_IN_BACKGROUND", "allow"])
                .status();
            let _ = Command::new("cmd")
                .args(["appops", "set", "com.google.android.gms", "WAKE_LOCK", "allow"])
                .status();
            self.last_gms_state = Some(false);
        }
    }
}
