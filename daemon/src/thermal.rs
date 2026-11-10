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
use std::time::{Duration, Instant};

const SAMPLE_INTERVAL: Duration = Duration::from_secs(2);
const WARM_C: f32 = 68.0;
const CRITICAL_C: f32 = 78.0;
const WARM_RECOVER_C: f32 = 62.0;
const CRITICAL_RECOVER_C: f32 = 70.0;
const SMOOTHING: f32 = 0.4;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ThermalLevel {
    Normal,
    Warm,
    Critical,
}

fn normalize(raw: i64) -> Option<f32> {
    let value = if raw.abs() >= 1000 {
        raw as f32 / 1000.0
    } else if raw.abs() >= 100 {
        raw as f32 / 10.0
    } else {
        raw as f32
    };
    (value > 0.0 && value <= 150.0).then_some(value)
}

fn zone_temperature(name: &str) -> Option<f32> {
    let kind = fs::read_to_string(format!("/sys/class/thermal/{}/type", name))
        .unwrap_or_default()
        .to_ascii_lowercase();
    if kind.contains("usb") || kind.contains("charger") || kind.contains("pa_") {
        return None;
    }

    let raw = fs::read_to_string(format!("/sys/class/thermal/{}/temp", name))
        .ok()?
        .trim()
        .parse()
        .ok()?;
    normalize(raw)
}

fn read_temperature() -> Option<f32> {
    let mut max: Option<f32> = None;
    if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.starts_with("thermal_zone") {
                continue;
            }
            if let Some(temp) = zone_temperature(&name) {
                max = Some(max.map_or(temp, |current| current.max(temp)));
            }
        }
    }

    for path in [
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/bms/temp",
    ] {
        if let Ok(raw) = fs::read_to_string(path) {
            if let Ok(value) = raw.trim().parse::<i64>() {
                if let Some(temp) = normalize(value) {
                    max = Some(max.map_or(temp, |current| current.max(temp)));
                }
            }
        }
    }
    max
}

pub struct ThermalWatchdog {
    last_sample: Option<Instant>,
    smoothed: Option<f32>,
    level: ThermalLevel,
}

impl ThermalWatchdog {
    pub fn new() -> Self {
        Self {
            last_sample: None,
            smoothed: None,
            level: ThermalLevel::Normal,
        }
    }

    pub fn reset(&mut self) {
        self.last_sample = None;
        self.smoothed = None;
        self.level = ThermalLevel::Normal;
    }

    pub fn sample(&mut self) -> ThermalLevel {
        if let Some(last) = self.last_sample {
            if last.elapsed() < SAMPLE_INTERVAL {
                return self.level;
            }
        }
        self.last_sample = Some(Instant::now());

        let Some(temp) = read_temperature() else {
            return self.level;
        };
        let smoothed = match self.smoothed {
            Some(previous) => previous * (1.0 - SMOOTHING) + temp * SMOOTHING,
            None => temp,
        };
        self.smoothed = Some(smoothed);

        let next = match self.level {
            ThermalLevel::Normal if smoothed >= CRITICAL_C => ThermalLevel::Critical,
            ThermalLevel::Normal if smoothed >= WARM_C => ThermalLevel::Warm,
            ThermalLevel::Warm if smoothed >= CRITICAL_C => ThermalLevel::Critical,
            ThermalLevel::Warm if smoothed <= WARM_RECOVER_C => ThermalLevel::Normal,
            ThermalLevel::Critical if smoothed <= WARM_RECOVER_C => ThermalLevel::Normal,
            ThermalLevel::Critical if smoothed <= CRITICAL_RECOVER_C => ThermalLevel::Warm,
            current => current,
        };

        if next != self.level {
            crate::log!(
                "[THERMAL] {} -> {} at {:.1}C (smoothed {:.1}C)",
                self.level_name(),
                Self::name(next),
                temp,
                smoothed
            );
            self.level = next;
        }
        self.level
    }

    fn level_name(&self) -> &'static str {
        Self::name(self.level)
    }

    fn name(level: ThermalLevel) -> &'static str {
        match level {
            ThermalLevel::Normal => "normal",
            ThermalLevel::Warm => "warm",
            ThermalLevel::Critical => "critical",
        }
    }
}
