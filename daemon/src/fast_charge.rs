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
use std::path::{Path, PathBuf};
use std::process::Command;

const BATT_STATUS: &str = "/sys/class/power_supply/battery/status";
const SCONFIG_PATH: &str = "/sys/devices/virtual/thermal/thermal_message/sconfig";
const FAST_CHARGE_FLAG: &str = "/data/adb/luminatweaks/fast_charge";

pub struct FastChargeController {
    battery_dirs: Vec<PathBuf>,
    thermal_services: Vec<String>,
    thermal_disabled: bool,
}

impl FastChargeController {
    pub fn new() -> Self {
        let battery_dirs = Self::discover_battery_dirs();
        let thermal_services = Self::discover_thermal_services();

        Self {
            battery_dirs,
            thermal_services,
            thermal_disabled: false,
        }
    }

    fn discover_battery_dirs() -> Vec<PathBuf> {
        let output = Command::new("sh")
            .arg("-c")
            .arg("find /sys/devices -name health -exec dirname {} + 2>/dev/null")
            .output();

        if let Ok(out) = output {
            String::from_utf8_lossy(&out.stdout)
                .lines()
                .map(|line| PathBuf::from(line.trim()))
                .filter(|p| p.exists())
                .collect()
        } else {
            Vec::new()
        }
    }

    fn discover_thermal_services() -> Vec<String> {
        let output = Command::new("sh")
            .arg("-c")
            .arg("find /system/etc/init /vendor/etc/init /odm/etc/init -type f 2>/dev/null | xargs grep -Eh '^service .*thermal' 2>/dev/null | awk '{print $2}'")
            .output();

        if let Ok(out) = output {
            String::from_utf8_lossy(&out.stdout)
                .lines()
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .collect()
        } else {
            Vec::new()
        }
    }

    pub fn tick(&mut self) {
        let is_enabled = self.is_flag_enabled();

        if !is_enabled {
            if self.thermal_disabled {
                self.restore();
            }
            return;
        }

        if self.is_charging() {
            self.apply();
        } else if self.thermal_disabled {
            self.restore();
        }
    }

    fn is_flag_enabled(&self) -> bool {
        fs::read_to_string(FAST_CHARGE_FLAG)
            .map(|s| s.trim() == "1")
            .unwrap_or(false)
    }

    fn is_charging(&self) -> bool {
        fs::read_to_string(BATT_STATUS)
            .map(|s| s.trim().eq_ignore_ascii_case("charging"))
            .unwrap_or(false)
    }

    fn apply(&mut self) {
        for dir in &self.battery_dirs {
            let limit_path = dir.join("charge_control_limit");
            if limit_path.exists() {
                let _ = fs::write(&limit_path, "0");
            }
            let health_path = dir.join("health");
            if health_path.exists() {
                let _ = fs::write(&health_path, "Good");
            }
        }

        if let Ok(entries) = fs::read_dir("/sys/devices/virtual/thermal") {
            for entry in entries.flatten() {
                let path = entry.path();
                if let Some(file_name) = path.file_name().and_then(|n| n.to_str()) {
                    if file_name.starts_with("thermal_zone") {
                        let cur_state = path.join("cur_state");
                        if cur_state.exists() {
                            let _ = fs::write(&cur_state, "0");
                        }
                        let mode = path.join("mode");
                        if mode.exists() {
                            let _ = fs::write(&mode, "disabled");
                        }
                    }
                }
            }
        }

        for svc in &self.thermal_services {
            let status = Command::new("getprop")
                .arg(format!("init.svc.{}", svc))
                .output()
                .ok()
                .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
                .unwrap_or_default();

            if status == "running" {
                let _ = Command::new("stop").arg(svc).status();
            }
        }

        if !self.thermal_disabled {
            if Path::new(SCONFIG_PATH).exists() {
                let _ = Command::new("chmod").args(["644", SCONFIG_PATH]).status();
                let _ = fs::write(SCONFIG_PATH, "6");
                let _ = Command::new("chmod").args(["444", SCONFIG_PATH]).status();
            }
            self.thermal_disabled = true;
        }
    }

    pub fn restore(&mut self) {
        if self.thermal_disabled {
            if Path::new(SCONFIG_PATH).exists() {
                let _ = Command::new("chmod").args(["644", SCONFIG_PATH]).status();
                let _ = fs::write(SCONFIG_PATH, "0");
            }

            if let Ok(entries) = fs::read_dir("/sys/devices/virtual/thermal") {
                for entry in entries.flatten() {
                    let path = entry.path();
                    if let Some(file_name) = path.file_name().and_then(|n| n.to_str()) {
                        if file_name.starts_with("thermal_zone") {
                            let mode = path.join("mode");
                            if mode.exists() {
                                let _ = fs::write(&mode, "enabled");
                            }
                        }
                    }
                }
            }

            for svc in &self.thermal_services {
                let status = Command::new("getprop")
                    .arg(format!("init.svc.{}", svc))
                    .output()
                    .ok()
                    .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
                    .unwrap_or_default();

                if status != "running" {
                    let _ = Command::new("start").arg(svc).status();
                }
            }

            self.thermal_disabled = false;
        }
    }
}
