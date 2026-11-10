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
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

const SAMPLE_INTERVAL: Duration = Duration::from_secs(2);
const ZONE_RESCAN_INTERVAL: Duration = Duration::from_secs(60);
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

#[derive(Clone)]
struct Zone {
    temp: String,
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

fn usable_zone(kind: &str) -> bool {
    !kind.contains("usb")
        && !kind.contains("charger")
        && !kind.contains("pa_")
        && !kind.contains("battery")
        && !kind.contains("critical")
}

fn scan_zones() -> Vec<Zone> {
    let mut zones = Vec::new();
    let Ok(entries) = fs::read_dir("/sys/class/thermal") else {
        return zones;
    };

    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if !name.starts_with("thermal_zone") {
            continue;
        }

        let kind = fs::read_to_string(format!("/sys/class/thermal/{}/type", name))
            .unwrap_or_default()
            .to_ascii_lowercase();
        if !usable_zone(&kind) {
            continue;
        }

        zones.push(Zone {
            temp: format!("/sys/class/thermal/{}/temp", name),
        });
    }

    zones
}

fn read_temperature(zones: &[Zone]) -> Option<f32> {
    let mut max: Option<f32> = None;
    for zone in zones {
        let Ok(raw) = fs::read_to_string(&zone.temp) else {
            continue;
        };
        let Ok(value) = raw.trim().parse::<i64>() else {
            continue;
        };
        if let Some(temp) = normalize(value) {
            max = Some(max.map_or(temp, |current| current.max(temp)));
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

struct SamplerState {
    smoothed: Option<f32>,
    level: ThermalLevel,
}

pub struct ThermalWatchdog {
    level: Arc<Mutex<ThermalLevel>>,
    state: Arc<Mutex<SamplerState>>,
}

impl ThermalWatchdog {
    pub fn new() -> Self {
        let level = Arc::new(Mutex::new(ThermalLevel::Normal));
        let state = Arc::new(Mutex::new(SamplerState {
            smoothed: None,
            level: ThermalLevel::Normal,
        }));

        let worker_level = Arc::clone(&level);
        let worker_state = Arc::clone(&state);

        let _ = thread::Builder::new()
            .name("thermal-sampler".to_string())
            .spawn(move || {
                let mut zones = scan_zones();
                let mut last_scan = Instant::now();

                loop {
                    thread::sleep(SAMPLE_INTERVAL);

                    if last_scan.elapsed() >= ZONE_RESCAN_INTERVAL {
                        zones = scan_zones();
                        last_scan = Instant::now();
                    }

                    let Some(temp) = read_temperature(&zones) else {
                        continue;
                    };

                    let Ok(mut guard) = worker_state.lock() else {
                        continue;
                    };

                    let smoothed = match guard.smoothed {
                        Some(previous) => previous * (1.0 - SMOOTHING) + temp * SMOOTHING,
                        None => temp,
                    };
                    guard.smoothed = Some(smoothed);

                    let next = match guard.level {
                        ThermalLevel::Normal if smoothed >= CRITICAL_C => ThermalLevel::Critical,
                        ThermalLevel::Normal if smoothed >= WARM_C => ThermalLevel::Warm,
                        ThermalLevel::Warm if smoothed >= CRITICAL_C => ThermalLevel::Critical,
                        ThermalLevel::Warm if smoothed <= WARM_RECOVER_C => ThermalLevel::Normal,
                        ThermalLevel::Critical if smoothed <= WARM_RECOVER_C => {
                            ThermalLevel::Normal
                        }
                        ThermalLevel::Critical if smoothed <= CRITICAL_RECOVER_C => {
                            ThermalLevel::Warm
                        }
                        current => current,
                    };

                    if next != guard.level {
                        crate::log!(
                            "[THERMAL] {} -> {} at {:.1}C (smoothed {:.1}C)",
                            name(guard.level),
                            name(next),
                            temp,
                            smoothed
                        );
                        guard.level = next;
                    }

                    if let Ok(mut current) = worker_level.lock() {
                        *current = next;
                    }
                }
            });

        Self { level, state }
    }

    pub fn reset(&mut self) {
        if let Ok(mut state) = self.state.lock() {
            state.smoothed = None;
            state.level = ThermalLevel::Normal;
        }
        if let Ok(mut level) = self.level.lock() {
            *level = ThermalLevel::Normal;
        }
    }

    pub fn sample(&mut self) -> ThermalLevel {
        self.level
            .lock()
            .map(|level| *level)
            .unwrap_or(ThermalLevel::Normal)
    }
}

fn name(level: ThermalLevel) -> &'static str {
    match level {
        ThermalLevel::Normal => "normal",
        ThermalLevel::Warm => "warm",
        ThermalLevel::Critical => "critical",
    }
}
