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
use std::time::{Duration, Instant};

const THERMAL_DIR: &str = "/sys/class/thermal";
const BATTERY_TEMP_PATHS: &[&str] = &[
    "/sys/class/power_supply/battery/temp",
    "/sys/class/power_supply/battery/batt_temp",
];
const WARM_MARGIN_C: f32 = 12.0;
const CRITICAL_MARGIN_C: f32 = 5.0;
const BATTERY_WARM_C: f32 = 43.0;
const BATTERY_CRITICAL_C: f32 = 46.0;
const SAMPLE_INTERVAL: Duration = Duration::from_secs(1);
const MAX_TRIP_POINTS: usize = 32;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ThermalPressure {
    Normal,
    Warm,
    Critical,
}

struct Zone {
    temp_path: PathBuf,
    trips: Vec<f32>,
}

pub struct ThermalMonitor {
    zones: Vec<Zone>,
    battery_path: Option<PathBuf>,
    last_read: Option<Instant>,
    pressure: ThermalPressure,
}

impl ThermalMonitor {
    pub fn new() -> Self {
        let zones = discover_zones();
        let battery_path = BATTERY_TEMP_PATHS
            .iter()
            .map(PathBuf::from)
            .find(|path| path.exists());
        Self {
            zones,
            battery_path,
            last_read: None,
            pressure: ThermalPressure::Normal,
        }
    }

    pub fn sample(&mut self) -> ThermalPressure {
        let now = Instant::now();
        if self
            .last_read
            .map(|at| now.saturating_duration_since(at) < SAMPLE_INTERVAL)
            .unwrap_or(false)
        {
            return self.pressure;
        }
        self.last_read = Some(now);

        let mut headroom = None;
        for zone in &self.zones {
            if zone.trips.is_empty() {
                continue;
            }
            let Some(temp) = read_celsius(&zone.temp_path) else {
                continue;
            };
            let Some(next_trip) = zone.trips.iter().copied().find(|trip| *trip > temp) else {
                headroom = Some(0.0f32);
                break;
            };
            let value = (next_trip - temp).max(0.0);
            headroom = Some(headroom.map(|current: f32| current.min(value)).unwrap_or(value));
        }

        let mut pressure = headroom
            .map(classify_headroom)
            .unwrap_or(ThermalPressure::Normal);

        if pressure == ThermalPressure::Normal {
            if let Some(path) = self.battery_path.as_ref() {
                if let Some(temp) = read_celsius(path) {
                    pressure = classify_battery(temp);
                }
            }
        }

        self.pressure = pressure;
        pressure
    }
}

fn classify_headroom(headroom: f32) -> ThermalPressure {
    if headroom <= CRITICAL_MARGIN_C {
        ThermalPressure::Critical
    } else if headroom <= WARM_MARGIN_C {
        ThermalPressure::Warm
    } else {
        ThermalPressure::Normal
    }
}

fn classify_battery(temp: f32) -> ThermalPressure {
    if temp >= BATTERY_CRITICAL_C {
        ThermalPressure::Critical
    } else if temp >= BATTERY_WARM_C {
        ThermalPressure::Warm
    } else {
        ThermalPressure::Normal
    }
}

fn normalize_temp(raw: f32) -> Option<f32> {
    if !raw.is_finite() {
        return None;
    }
    let celsius = if raw.abs() > 1_000.0 {
        raw / 1_000.0
    } else if raw.abs() > 200.0 {
        raw / 10.0
    } else {
        raw
    };
    (-40.0..=200.0).contains(&celsius).then_some(celsius)
}

fn read_celsius(path: &Path) -> Option<f32> {
    fs::read_to_string(path)
        .ok()
        .and_then(|value| value.trim().parse::<f32>().ok())
        .and_then(normalize_temp)
}

fn discover_zones() -> Vec<Zone> {
    let mut zones = Vec::new();
    let Ok(entries) = fs::read_dir(THERMAL_DIR) else {
        return zones;
    };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if !name.starts_with("thermal_zone") {
            continue;
        }
        let base = Path::new(THERMAL_DIR).join(&name);
        let temp_path = base.join("temp");
        if !temp_path.exists() {
            continue;
        }
        let mut trips = Vec::new();
        for index in 0..MAX_TRIP_POINTS {
            let path = base.join(format!("trip_point_{}_temp", index));
            let Ok(value) = fs::read_to_string(&path) else {
                continue;
            };
            let Ok(raw) = value.trim().parse::<f32>() else {
                continue;
            };
            if let Some(celsius) = normalize_temp(raw).filter(|value| *value > 0.0) {
                trips.push(celsius);
            }
        }
        trips.sort_by(|left, right| {
            left.partial_cmp(right)
                .unwrap_or(std::cmp::Ordering::Equal)
        });
        trips.dedup();
        zones.push(Zone { temp_path, trips });
    }
    zones
}

pub fn apply_kernel_governor() -> usize {
    let mut applied = 0;
    let Ok(entries) = fs::read_dir(THERMAL_DIR) else {
        return applied;
    };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if !name.starts_with("thermal_zone") {
            continue;
        }
        let path = format!("{}/{}/policy", THERMAL_DIR, name);
        if Path::new(&path).exists() && crate::tweaks::engine::write_sysfs(&path, "step_wise", true)
        {
            applied += 1;
        }
    }
    applied
}

#[cfg(test)]
mod tests {
    use super::{classify_battery, classify_headroom, ThermalPressure};

    #[test]
    fn classifies_thermal_headroom() {
        assert_eq!(classify_headroom(20.0), ThermalPressure::Normal);
        assert_eq!(classify_headroom(10.0), ThermalPressure::Warm);
        assert_eq!(classify_headroom(4.0), ThermalPressure::Critical);
    }

    #[test]
    fn classifies_battery_temperature() {
        assert_eq!(classify_battery(40.0), ThermalPressure::Normal);
        assert_eq!(classify_battery(44.0), ThermalPressure::Warm);
        assert_eq!(classify_battery(47.0), ThermalPressure::Critical);
    }
}