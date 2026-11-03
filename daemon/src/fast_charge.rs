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
use std::path::Path;

const BATT_STATUS: &str = "/sys/class/power_supply/battery/status";
const SCONFIG_PATH: &str = "/sys/devices/virtual/thermal/thermal_message/sconfig";
const CHARGE_CONTROL_LIMIT: &str = "/sys/class/power_supply/battery/charge_control_limit";
const STEP_CHARGING: &str = "/sys/class/power_supply/battery/step_charging_enabled";
const COOL_DOWN: &str = "/sys/class/power_supply/battery/cool_down";
const FAST_CHARGE_FLAG: &str = "/data/adb/luminatweaks/fast_charge";

pub struct FastChargeController {
    is_active: bool,
}

impl FastChargeController {
    pub fn new() -> Self {
        Self { is_active: false }
    }

    pub fn tick(&mut self) {
        let is_enabled = self.is_flag_enabled();

        if !is_enabled {
            if self.is_active {
                self.restore();
            }
            return;
        }

        if self.is_charging() {
            if !self.is_active {
                self.apply();
            }
        } else if self.is_active {
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
        if Path::new(CHARGE_CONTROL_LIMIT).exists() {
            let _ = fs::write(CHARGE_CONTROL_LIMIT, "0");
        }

        if Path::new(STEP_CHARGING).exists() {
            let _ = fs::write(STEP_CHARGING, "0");
        }

        if Path::new(COOL_DOWN).exists() {
            let _ = fs::write(COOL_DOWN, "0");
        }

        if Path::new(SCONFIG_PATH).exists() {
            let _ = fs::write(SCONFIG_PATH, "6");
        }

        self.is_active = true;
    }

    pub fn restore(&mut self) {
        if Path::new(SCONFIG_PATH).exists() {
            let _ = fs::write(SCONFIG_PATH, "0");
        }

        if Path::new(STEP_CHARGING).exists() {
            let _ = fs::write(STEP_CHARGING, "1");
        }

        self.is_active = false;
    }
}
