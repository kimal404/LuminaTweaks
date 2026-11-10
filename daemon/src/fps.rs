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
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::process::Command;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

const STATUS_PATH: &str = "/data/adb/.config/lumina/fps.txt";
const SAMPLE_INTERVAL: Duration = Duration::from_millis(1500);
const MAX_FRAME_AGE_NS: u64 = 3_000_000_000;

#[repr(C)]
struct Timespec {
    tv_sec: i64,
    tv_nsec: i64,
}

extern "C" {
    fn clock_gettime(clock_id: i32, tp: *mut Timespec) -> i32;
}

const CLOCK_MONOTONIC: i32 = 1;

fn monotonic_ns() -> u64 {
    let mut value = Timespec { tv_sec: 0, tv_nsec: 0 };
    let result = unsafe { clock_gettime(CLOCK_MONOTONIC, &mut value) };
    if result != 0 || value.tv_sec < 0 {
        return 0;
    }
    (value.tv_sec as u64)
        .saturating_mul(1_000_000_000)
        .saturating_add(value.tv_nsec.max(0) as u64)
}

fn write_status(value: Option<f32>) {
    let Some(parent) = Path::new(STATUS_PATH).parent() else {
        return;
    };
    if fs::create_dir_all(parent).is_err() {
        return;
    }

    let frame = match value {
        Some(fps) => format!("{:.1} {}", fps.clamp(1.0, 240.0), epoch_ms()),
        None => format!("-1 {}", epoch_ms()),
    };

    let temp = format!("{}.tmp", STATUS_PATH);
    if fs::write(&temp, frame).is_ok() {
        let _ = fs::set_permissions(&temp, fs::Permissions::from_mode(0o644));
        let _ = fs::rename(&temp, STATUS_PATH);
    }
}

fn epoch_ms() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis())
        .unwrap_or(0)
}

fn find_layer(package: &str) -> Option<String> {
    let output = Command::new("dumpsys")
        .args(["SurfaceFlinger", "--list"])
        .output()
        .ok()?;
    let text = String::from_utf8_lossy(&output.stdout);
    let package_lower = package.to_ascii_lowercase();

    let mut candidates: Vec<(u8, String)> = text
        .lines()
        .map(str::trim)
        .filter(|line| {
            let lower = line.to_ascii_lowercase();
            !line.is_empty() && lower.contains(&package_lower) && !lower.contains("background")
        })
        .map(|line| {
            let lower = line.to_ascii_lowercase();
            let rank = if lower.contains("surfaceview") {
                0
            } else if lower.contains("#0") {
                1
            } else {
                2
            };
            (rank, line.to_string())
        })
        .collect();

    candidates.sort_by_key(|(rank, _)| *rank);
    candidates.into_iter().map(|(_, line)| line).next()
}

fn query_fps(layer: &str) -> Option<f32> {
    let output = Command::new("dumpsys")
        .args(["SurfaceFlinger", "--latency", layer])
        .output()
        .ok()?;
    let text = String::from_utf8_lossy(&output.stdout);
    let now = monotonic_ns();

    let mut timestamps: Vec<u64> = Vec::new();
    for line in text.lines() {
        let values: Vec<u64> = line
            .split_whitespace()
            .filter_map(|value| value.parse().ok())
            .collect();
        if values.len() < 3 {
            continue;
        }

        let timestamp = if values[1] > 0 { values[1] } else { values[0] };
        if timestamp == 0 {
            continue;
        }
        if now > 0 && (timestamp > now || now.saturating_sub(timestamp) > MAX_FRAME_AGE_NS) {
            continue;
        }
        timestamps.push(timestamp);
    }

    timestamps.sort_unstable();
    timestamps.dedup();
    if timestamps.len() < 2 {
        return None;
    }

    let span = timestamps[timestamps.len() - 1].saturating_sub(timestamps[0]);
    if span == 0 {
        return None;
    }

    let fps = ((timestamps.len() - 1) as f64 * 1_000_000_000.0) / span as f64;
    if fps.is_finite() && fps > 0.0 {
        Some(fps as f32)
    } else {
        None
    }
}

pub struct FpsMonitor {
    layer: Option<String>,
    last_package: Option<String>,
    last_sample: Option<Instant>,
}

impl FpsMonitor {
    pub fn new() -> Self {
        Self {
            layer: None,
            last_package: None,
            last_sample: None,
        }
    }

    pub fn observe(&mut self, package: Option<&str>) {
        let Some(package) = package else {
            if self.last_package.take().is_some() {
                self.layer = None;
                self.last_sample = None;
                write_status(None);
            }
            return;
        };

        if self.last_package.as_deref() != Some(package) {
            self.last_package = Some(package.to_string());
            self.layer = None;
            self.last_sample = None;
        }

        if let Some(last) = self.last_sample {
            if last.elapsed() < SAMPLE_INTERVAL {
                return;
            }
        }
        self.last_sample = Some(Instant::now());

        if self.layer.is_none() {
            self.layer = find_layer(package);
        }

        let result = self
            .layer
            .as_deref()
            .and_then(query_fps);

        if result.is_none() {
            self.layer = None;
        }
        write_status(result);
    }
}
