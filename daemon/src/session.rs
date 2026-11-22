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

use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use crate::core::detector::{sanitize_pkg_name, CONFIG_DIR};
use crate::tweaks::fps::FrameTap;

const RECORD_APPS_FILE: &str = "record_apps.json";
const SESSIONS_FILE: &str = "sessions.json";
const LABEL_CACHE_FILES: &[&str] = &[
    "/data/adb/.config/lumina/app_labels.json",
    "/data/adb/modules/luminatweaks/config/app_labels.json",
    "/data/adb/modules/lumina_tweaks/config/app_labels.json",
];

const CONFIG_POLL_INTERVAL: Duration = Duration::from_secs(2);
const SENSOR_INTERVAL: Duration = Duration::from_secs(1);
const GRACE_PERIOD: Duration = Duration::from_secs(10);

const MIN_SESSION: Duration = Duration::from_secs(10);
const IDLE_TAP_CLEAR_INTERVAL: Duration = Duration::from_secs(5);
const MAX_SESSIONS: usize = 25;

const MAX_SAMPLE_POINTS: usize = 300;
const JANK_FRAME_FACTOR: f32 = 1.5;
const HIST_BIN_MS: f32 = 0.1;
const HIST_BINS: usize = 20_000;

const STALL_FRAME_MS: f32 = 250.0;
const THERMAL_DIR: &str = "/sys/class/thermal";
const POWER_SUPPLY_DIR: &str = "/sys/class/power_supply";

fn config_path(name: &str) -> String {
    format!("{}/{}", CONFIG_DIR, name)
}

fn epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis() as u64)
        .unwrap_or(0)
}

fn round2(value: f32) -> f32 {
    (value * 100.0).round() / 100.0
}

fn game_process_alive(package: &str) -> bool {
    let Ok(entries) = fs::read_dir("/proc") else {
        return true;
    };

    for entry in entries.flatten() {
        let name = entry.file_name();
        let Some(pid) = name.to_str() else {
            continue;
        };
        if !pid.bytes().all(|byte| byte.is_ascii_digit()) {
            continue;
        }

        let Ok(cmdline) = fs::read(entry.path().join("cmdline")) else {
            continue;
        };
        let Some(first) = cmdline.split(|byte| *byte == 0).next() else {
            continue;
        };
        let Ok(proc_name) = std::str::from_utf8(first) else {
            continue;
        };

        if let Some(rest) = proc_name.strip_prefix(package) {
            if rest.is_empty() || rest.starts_with(':') {
                return true;
            }
        }
    }

    false
}

struct RecordApps {
    packages: HashSet<String>,
    mtime: Option<SystemTime>,
    last_check: Instant,
}

impl RecordApps {
    fn new() -> Self {
        let mut config = Self {
            packages: HashSet::new(),
            mtime: None,
            last_check: Instant::now()
                .checked_sub(CONFIG_POLL_INTERVAL)
                .unwrap_or_else(Instant::now),
        };
        config.refresh(true);
        config
    }

    fn refresh(&mut self, force: bool) {

        let invalidated = crate::core::take_record_apps_stale();
        if !force && !invalidated && self.last_check.elapsed() < CONFIG_POLL_INTERVAL {
            return;
        }
        self.last_check = Instant::now();

        let path = config_path(RECORD_APPS_FILE);
        let mtime = fs::metadata(&path).ok().and_then(|meta| meta.modified().ok());
        if !force && !invalidated && mtime == self.mtime {
            return;
        }
        self.mtime = mtime;

        self.packages = fs::read_to_string(&path)
            .map(|raw| parse_record_apps(&raw))
            .unwrap_or_default();
    }
}

fn parse_record_apps(raw: &str) -> HashSet<String> {
    let mut packages = HashSet::new();
    let Ok(value) = serde_json::from_str::<serde_json::Value>(raw) else {
        log!("[SESSION] record_apps.json is not valid JSON; ignoring");
        return packages;
    };

    let mut insert = |candidate: &str| {
        let pkg = sanitize_pkg_name(candidate);
        if !pkg.is_empty() {
            packages.insert(pkg.to_string());
        }
    };

    match value {
        serde_json::Value::Array(items) => {
            for item in items {
                if let Some(pkg) = item.as_str() {
                    insert(pkg);
                }
            }
        }
        serde_json::Value::Object(map) => {
            for key in map.keys() {
                insert(key);
            }
        }
        _ => {}
    }

    packages
}

struct LabelCache {
    labels: HashMap<String, String>,
}

impl LabelCache {
    fn load() -> Self {
        let mut labels = HashMap::new();
        for path in LABEL_CACHE_FILES {
            let Ok(raw) = fs::read_to_string(path) else {
                continue;
            };
            parse_labels(&raw, &mut labels);
        }
        Self { labels }
    }

    fn resolve(&self, package: &str) -> String {
        self.labels
            .get(package)
            .cloned()
            .unwrap_or_else(|| package.to_string())
    }
}

fn parse_labels(raw: &str, labels: &mut HashMap<String, String>) {
    let Ok(value) = serde_json::from_str::<serde_json::Value>(raw) else {
        return;
    };

    match value {
        serde_json::Value::Object(map) => {
            for (pkg, label) in map {
                if let Some(label) = label.as_str() {
                    let label = label.trim();
                    if !label.is_empty() {
                        labels.insert(pkg, label.to_string());
                    }
                }
            }
        }
        serde_json::Value::Array(items) => {
            for item in items {
                let pkg = item
                    .get("package")
                    .or_else(|| item.get("packageName"))
                    .or_else(|| item.get("pkg"))
                    .and_then(|value| value.as_str());
                let label = item
                    .get("label")
                    .or_else(|| item.get("appName"))
                    .or_else(|| item.get("name"))
                    .and_then(|value| value.as_str());
                if let (Some(pkg), Some(label)) = (pkg, label) {
                    let label = label.trim();
                    if !label.is_empty() {
                        labels.insert(pkg.to_string(), label.to_string());
                    }
                }
            }
        }
        _ => {}
    }
}

struct ThermalZone {
    path: PathBuf,
    tier: u8,
}

struct BatterySensor {
    dir: PathBuf,
}

struct Sensors {
    zones: Vec<ThermalZone>,
    battery: Option<BatterySensor>,
}

impl Sensors {
    fn discover() -> Self {
        let zones = discover_thermal_zones();
        let battery = discover_battery();
        Self { zones, battery }
    }

    fn cpu_temp_max(&self) -> Option<f32> {
        for tier in [3u8, 2, 1] {
            let mut best: Option<f32> = None;
            for zone in self.zones.iter().filter(|zone| zone.tier == tier) {
                let Some(value) = read_number(&zone.path).and_then(normalize_temp) else {
                    continue;
                };
                best = Some(best.map(|current| current.max(value)).unwrap_or(value));
            }
            if best.is_some() {
                return best;
            }
        }
        None
    }

    fn battery_temp(&self) -> Option<f32> {
        let battery = self.battery.as_ref()?;
        first_number(&battery.dir, &["temp", "batt_temp"]).and_then(normalize_temp)
    }

    fn battery_power_mw(&self) -> Option<f32> {
        let battery = self.battery.as_ref()?;

        if let Some(value) = first_number(&battery.dir, &["power_now"]) {
            let value = value.abs();
            if value > 0.0 {
                return Some(if value > 20_000.0 {
                    value / 1_000.0
                } else {
                    value
                });
            }
        }

        let current = first_number(&battery.dir, &["current_now", "current_avg"])?.abs();
        let voltage = first_number(&battery.dir, &["voltage_now", "voltage_avg"])?;
        if current <= 0.0 || voltage <= 0.0 {
            return None;
        }

        let voltage_mv = if voltage > 100_000.0 {
            voltage / 1_000.0
        } else {
            voltage
        };
        Some(current * voltage_mv / 1_000_000.0)
    }
}

fn discover_thermal_zones() -> Vec<ThermalZone> {
    let mut zones = Vec::new();
    let Ok(entries) = fs::read_dir(THERMAL_DIR) else {
        return zones;
    };

    let mut dirs: Vec<PathBuf> = entries
        .flatten()
        .map(|entry| entry.path())
        .filter(|path| {
            path.file_name()
                .and_then(|name| name.to_str())
                .map(|name| name.starts_with("thermal_zone"))
                .unwrap_or(false)
        })
        .collect();
    dirs.sort();

    for dir in dirs {
        let kind = fs::read_to_string(dir.join("type"))
            .map(|value| value.trim().to_ascii_lowercase())
            .unwrap_or_else(|_| {
                dir.file_name()
                    .and_then(|name| name.to_str())
                    .unwrap_or("")
                    .to_ascii_lowercase()
            });
        let tier = classify_thermal(&kind);
        if tier > 0 && dir.join("temp").exists() {
            zones.push(ThermalZone {
                path: dir.join("temp"),
                tier,
            });
        }
    }

    zones.sort_by(|a, b| b.tier.cmp(&a.tier).then_with(|| a.path.cmp(&b.path)));
    zones.truncate(24);
    zones
}

fn classify_thermal(kind: &str) -> u8 {
    const EXCLUDED: &[&str] = &[
        "batt", "charger", "charging", "usb", "vbus", "pmic", "ambient", "skin", "quiet", "xo",
        "wlan", "modem", "camera", "display", "flash", "pa_", "speaker", "case", "back",
        "front", "vbat",
    ];
    const CPU: &[&str] = &[
        "cpu", "cpuss", "mtktscpu", "mtktsap", "tmu", "cluster", "little", "big", "mid", "core",
        "soc_therm", "soc-thermal", "soc_thermal", "cpu_therm",
    ];
    const SOC: &[&str] = &["soc", "gpu", "tsens", "g3d", "kgsl", "mali", "devfreq"];

    if EXCLUDED.iter().any(|needle| kind.contains(needle)) {
        return 0;
    }
    if CPU.iter().any(|needle| kind.contains(needle)) {
        return 3;
    }
    if SOC.iter().any(|needle| kind.contains(needle)) {
        return 2;
    }
    if kind.contains("thermal") || kind.contains("temp") {
        return 1;
    }
    0
}

fn discover_battery() -> Option<BatterySensor> {
    let root = Path::new(POWER_SUPPLY_DIR);
    let preferred = root.join("battery");
    if preferred.is_dir() {
        return Some(BatterySensor { dir: preferred });
    }

    let entries = fs::read_dir(root).ok()?;
    let mut candidates: Vec<PathBuf> = entries
        .flatten()
        .map(|entry| entry.path())
        .filter(|path| path.is_dir())
        .collect();
    candidates.sort();

    for dir in candidates {
        let name = dir
            .file_name()
            .and_then(|value| value.to_str())
            .unwrap_or("")
            .to_ascii_lowercase();
        let kind = fs::read_to_string(dir.join("type"))
            .map(|value| value.trim().to_ascii_lowercase())
            .unwrap_or_default();
        if kind.contains("battery") || name.contains("batt") {
            return Some(BatterySensor { dir });
        }
    }
    None
}

fn first_number(dir: &Path, names: &[&str]) -> Option<f32> {
    for name in names {
        if let Some(value) = read_number(&dir.join(name)) {
            return Some(value);
        }
    }
    None
}

fn read_number(path: &Path) -> Option<f32> {
    fs::read_to_string(path)
        .ok()?
        .trim()
        .parse::<f32>()
        .ok()
        .filter(|value| value.is_finite())
}

fn normalize_temp(raw: f32) -> Option<f32> {
    if !raw.is_finite() || raw < -50.0 {
        return None;
    }
    let celsius = if raw.abs() > 1_000.0 {
        raw / 1_000.0
    } else if raw.abs() > 200.0 {
        raw / 10.0
    } else {
        raw
    };
    if (-20.0..=150.0).contains(&celsius) {
        Some(celsius)
    } else {
        None
    }
}

fn current_display_hz() -> i32 {
    if let Some(hz) = crate::tweaks::display::saved_mode_hz() {
        if (1..=240).contains(&hz) {
            return hz;
        }
    }

    for path in ["/sys/class/graphics/fb0/mode", "/sys/class/graphics/fb0/modes"] {
        if let Ok(text) = fs::read_to_string(path) {
            if let Some(hz) = parse_display_hz(&text) {
                return hz;
            }
        }
    }
    60
}

fn parse_display_hz(text: &str) -> Option<i32> {
    let mut best = None;
    for token in text.split(|ch: char| !ch.is_ascii_digit()) {
        if let Ok(value) = token.parse::<i32>() {
            if (24..=240).contains(&value) {
                best = Some(value);
            }
        }
    }
    best
}

#[derive(Default)]
struct Bucket {
    offset_sec: u32,
    frames: Vec<f32>,
    jank: u32,
    cpu_temp: f32,
    battery_temp: f32,
    power_mw: f32,
}

struct ActiveSession {
    package: String,
    app_name: String,
    profile: String,
    display_hz: i32,
    start_epoch_ms: u64,
    started_at: Instant,
    last_tick: Instant,
    left_at: Option<Instant>,
    active_time: Duration,
    last_sensor_at: Instant,
    start_battery_temp: Option<f32>,
    peak_battery_temp: f32,
    max_cpu_temp: f32,
    power_sum: f64,
    power_samples: u32,
    total_frames: u64,
    pacing_count: u64,
    pacing_mean: f64,
    pacing_m2: f64,
    histogram: Vec<u32>,
    jank_count: u32,
    buckets: Vec<Bucket>,
}

impl ActiveSession {
    fn new(
        package: &str,
        app_name: String,
        profile: &str,
        sensors: &Sensors,
    ) -> Self {
        let now = Instant::now();
        let cpu_temp = sensors.cpu_temp_max().unwrap_or(0.0);
        let battery_temp = sensors.battery_temp();
        let power = sensors.battery_power_mw();

        let mut session = Self {
            package: package.to_string(),
            app_name,
            profile: profile.to_string(),
            display_hz: current_display_hz(),
            start_epoch_ms: epoch_ms(),
            started_at: now,
            last_tick: now,
            left_at: None,
            active_time: Duration::ZERO,
            last_sensor_at: now,
            start_battery_temp: battery_temp,
            peak_battery_temp: battery_temp.unwrap_or(0.0),
            max_cpu_temp: cpu_temp,
            power_sum: power.map(|value| value as f64).unwrap_or(0.0),
            power_samples: power.map(|_| 1).unwrap_or(0),
            total_frames: 0,
            pacing_count: 0,
            pacing_mean: 0.0,
            pacing_m2: 0.0,
            histogram: vec![0; HIST_BINS],
            jank_count: 0,
            buckets: Vec::new(),
        };

        let index = session.ensure_bucket(0);
        let bucket = &mut session.buckets[index];
        bucket.cpu_temp = cpu_temp;
        bucket.battery_temp = battery_temp.unwrap_or(0.0);
        bucket.power_mw = power.unwrap_or(0.0);
        session
    }

    fn ensure_bucket(&mut self, offset_sec: u32) -> usize {
        match self.buckets.last() {
            Some(bucket) if bucket.offset_sec == offset_sec => self.buckets.len() - 1,
            _ => {
                self.buckets.push(Bucket {
                    offset_sec,
                    ..Bucket::default()
                });
                self.buckets.len() - 1
            }
        }
    }

    fn ingest(&mut self, frames: &[f32], offset_sec: u32) {
        if frames.is_empty() {
            return;
        }

        let refresh_ms = 1000.0 / self.display_hz.max(1) as f32;
        let index = self.ensure_bucket(offset_sec);
        let bucket = &mut self.buckets[index];

        for &frametime in frames {
            if !frametime.is_finite() || frametime <= 0.0 || frametime > STALL_FRAME_MS {
                continue;
            }
            let frametime = frametime.max(refresh_ms);

            self.total_frames = self.total_frames.saturating_add(1);

            self.pacing_count = self.pacing_count.saturating_add(1);
            let value = frametime as f64;
            let delta = value - self.pacing_mean;
            self.pacing_mean += delta / self.pacing_count as f64;
            self.pacing_m2 += delta * (value - self.pacing_mean);

            let bin = ((frametime / HIST_BIN_MS) as usize).min(HIST_BINS - 1);
            self.histogram[bin] = self.histogram[bin].saturating_add(1);

            if frametime > refresh_ms * JANK_FRAME_FACTOR {
                self.jank_count = self.jank_count.saturating_add(1);
                bucket.jank = bucket.jank.saturating_add(1);
            }

            bucket.frames.push(frametime);
        }
    }

    fn sample_sensors(&mut self, sensors: &Sensors, now: Instant, offset_sec: u32) {
        if now.saturating_duration_since(self.last_sensor_at) < SENSOR_INTERVAL {
            return;
        }
        self.last_sensor_at = now;

        let cpu_temp = sensors.cpu_temp_max();
        let battery_temp = sensors.battery_temp();
        let power = sensors.battery_power_mw();

        if let Some(value) = cpu_temp {
            self.max_cpu_temp = self.max_cpu_temp.max(value);
        }
        if let Some(value) = battery_temp {
            self.peak_battery_temp = self.peak_battery_temp.max(value);
            if self.start_battery_temp.is_none() {
                self.start_battery_temp = Some(value);
            }
        }
        if let Some(value) = power {
            self.power_sum += value as f64;
            self.power_samples = self.power_samples.saturating_add(1);
        }

        let index = self.ensure_bucket(offset_sec);
        let bucket = &mut self.buckets[index];
        if let Some(value) = cpu_temp {
            bucket.cpu_temp = bucket.cpu_temp.max(value);
        }
        if let Some(value) = battery_temp {
            bucket.battery_temp = value;
        }
        if let Some(value) = power {
            bucket.power_mw = value;
        }
    }

    fn finalize(self) -> Option<StoredSession> {
        let elapsed = self.started_at.elapsed();
        if elapsed < MIN_SESSION {
            return None;
        }

        let duration_sec = elapsed.as_secs() as i32;
        let active_secs = self.active_time.as_secs_f64().max(0.001);
        let display_hz = self.display_hz.max(1) as f32;
        let avg_fps = if self.total_frames > 0 {
            (self.total_frames as f64 / active_secs).min(display_hz as f64) as f32
        } else {
            0.0
        };

        let mut min_fps = 0.0f32;
        let mut max_fps = 0.0f32;
        for bucket in &self.buckets {
            let Some(value) = bucket_avg_fps(bucket, display_hz) else {
                continue;
            };
            if min_fps == 0.0 || value < min_fps {
                min_fps = value;
            }
            if value > max_fps {
                max_fps = value;
            }
        }

        let one_percent_low_fps = low_fps_from_histogram(&self.histogram, 0.01).min(display_hz);
        let zero_point_one_low_fps = low_fps_from_histogram(&self.histogram, 0.001).min(display_hz);
        let frame_pacing_std_dev_ms = if self.pacing_count > 1 {
            (self.pacing_m2 / self.pacing_count as f64).sqrt() as f32
        } else {
            0.0
        };
        let avg_power_mw = if self.power_samples > 0 {
            (self.power_sum / self.power_samples as f64).round() as i32
        } else {
            0
        };

        Some(StoredSession {
            id: self.start_epoch_ms.to_string(),
            package_name: self.package,
            app_name: self.app_name,
            profile: self.profile,
            display_hz: self.display_hz,
            stop_reason: "Otomatis".to_string(),
            start_time_ms: self.start_epoch_ms as i64,
            duration_sec,
            avg_fps: round2(avg_fps),
            min_fps: min_fps.round() as i32,
            max_fps: max_fps.round() as i32,
            one_percent_low_fps: round2(one_percent_low_fps),
            zero_point_one_percent_low_fps: round2(zero_point_one_low_fps),
            frame_pacing_std_dev_ms: round2(frame_pacing_std_dev_ms),
            jank_count: self.jank_count as i32,
            max_cpu_temp_c: round2(self.max_cpu_temp),
            start_battery_temp_c: round2(self.start_battery_temp.unwrap_or(0.0)),
            peak_battery_temp_c: round2(self.peak_battery_temp),
            avg_power_mw,
            samples: build_samples(&self.buckets, display_hz),
        })
    }
}

fn bucket_avg_fps(bucket: &Bucket, display_hz: f32) -> Option<f32> {
    if bucket.frames.is_empty() {
        return None;
    }
    let total: f32 = bucket.frames.iter().sum();
    if total <= 0.0 {
        return None;
    }
    Some((bucket.frames.len() as f32 * 1000.0 / total).min(display_hz))
}

fn low_fps_from_histogram(histogram: &[u32], fraction: f64) -> f32 {
    let total: u64 = histogram.iter().map(|count| *count as u64).sum();
    if total == 0 {
        return 0.0;
    }

    let target = ((total as f64 * fraction).ceil() as u64).clamp(1, total);
    let mut taken = 0u64;
    let mut weighted_ms = 0.0f64;

    for (index, count) in histogram.iter().enumerate().rev() {
        let count = *count as u64;
        if count == 0 {
            continue;
        }
        let take = count.min(target - taken);
        let representative = (index as f32 + 0.5) * HIST_BIN_MS;
        weighted_ms += representative as f64 * take as f64;
        taken += take;
        if taken >= target {
            break;
        }
    }

    if taken == 0 || weighted_ms <= 0.0 {
        return 0.0;
    }
    (1000.0 / (weighted_ms / taken as f64)).clamp(0.0, 1_000.0) as f32
}

fn low_fps_from_sorted(sorted: &[f32], fraction: f32) -> f32 {
    if sorted.is_empty() {
        return 0.0;
    }
    let take = ((sorted.len() as f32 * fraction).ceil() as usize)
        .max(1)
        .min(sorted.len());
    let tail = &sorted[sorted.len() - take..];
    let mean_ms = tail.iter().sum::<f32>() / take as f32;
    if mean_ms <= 0.0 {
        return 0.0;
    }
    (1000.0 / mean_ms).clamp(0.0, 1_000.0)
}

fn build_samples(buckets: &[Bucket], display_hz: f32) -> Vec<StoredSample> {
    let active: Vec<&Bucket> = buckets
        .iter()
        .filter(|bucket| !bucket.frames.is_empty())
        .collect();
    if active.is_empty() {
        return Vec::new();
    }

    let groups = active.len().min(MAX_SAMPLE_POINTS);
    let mut samples = Vec::with_capacity(groups);

    for group in 0..groups {
        let start = group * active.len() / groups;
        let end = (group + 1) * active.len() / groups;
        let slice = &active[start..end];
        if slice.is_empty() {
            continue;
        }

        let mut frames: Vec<f32> = Vec::new();
        let mut jank = 0u32;
        let mut cpu_temp = 0.0f32;
        let mut battery_temp = 0.0f32;
        let mut power_sum = 0.0f64;
        let mut power_count = 0u32;

        for bucket in slice {
            frames.extend_from_slice(&bucket.frames);
            jank = jank.saturating_add(bucket.jank);
            cpu_temp = cpu_temp.max(bucket.cpu_temp);
            if bucket.battery_temp > 0.0 {
                battery_temp = bucket.battery_temp;
            }
            if bucket.power_mw > 0.0 {
                power_sum += bucket.power_mw as f64;
                power_count = power_count.saturating_add(1);
            }
        }

        if frames.is_empty() {
            continue;
        }

        frames.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
        let total_ms: f32 = frames.iter().sum();
        let avg_fps = if total_ms > 0.0 {
            (frames.len() as f32 * 1000.0 / total_ms).min(display_hz)
        } else {
            0.0
        };
        let min_fps = (1000.0 / frames[frames.len() - 1].max(0.001)).min(display_hz);
        let power_mw = if power_count > 0 {
            (power_sum / power_count as f64).round() as i32
        } else {
            0
        };

        samples.push(StoredSample {
            offset_sec: slice[0].offset_sec as i32,
            avg_fps: round2(avg_fps),
            min_fps: round2(min_fps),
            one_percent_low: round2(low_fps_from_sorted(&frames, 0.01).min(display_hz)),
            zero_point_one_percent_low: round2(
                low_fps_from_sorted(&frames, 0.001).min(display_hz),
            ),
            jank_frames: jank as i32,
            cpu_temp_c: round2(cpu_temp),
            battery_temp_c: round2(battery_temp),
            power_mw,
        });
    }

    samples
}

#[derive(Serialize, Deserialize, Clone, Default)]
#[serde(rename_all = "camelCase", default)]
struct StoredSample {
    offset_sec: i32,
    avg_fps: f32,
    min_fps: f32,
    one_percent_low: f32,
    zero_point_one_percent_low: f32,
    jank_frames: i32,
    cpu_temp_c: f32,
    battery_temp_c: f32,
    power_mw: i32,
}

#[derive(Serialize, Deserialize, Clone, Default)]
#[serde(rename_all = "camelCase", default)]
struct StoredSession {
    id: String,
    package_name: String,
    app_name: String,
    profile: String,
    display_hz: i32,
    stop_reason: String,
    start_time_ms: i64,
    duration_sec: i32,
    avg_fps: f32,
    min_fps: i32,
    max_fps: i32,
    one_percent_low_fps: f32,
    zero_point_one_percent_low_fps: f32,
    frame_pacing_std_dev_ms: f32,
    jank_count: i32,
    max_cpu_temp_c: f32,
    start_battery_temp_c: f32,
    peak_battery_temp_c: f32,
    avg_power_mw: i32,
    samples: Vec<StoredSample>,
}

fn read_sessions() -> Vec<StoredSession> {
    let path = config_path(SESSIONS_FILE);
    let Ok(raw) = fs::read_to_string(&path) else {
        return Vec::new();
    };
    if raw.trim().is_empty() {
        return Vec::new();
    }
    match serde_json::from_str::<Vec<StoredSession>>(&raw) {
        Ok(sessions) => sessions,
        Err(error) => {
            log!("[SESSION] Failed to parse {}: {}", path, error);
            Vec::new()
        }
    }
}

fn persist_session(session: StoredSession) -> std::io::Result<String> {
    let mut sessions = read_sessions();
    sessions.retain(|existing| existing.id != session.id);
    sessions.push(session);
    sessions.sort_by(|a, b| b.start_time_ms.cmp(&a.start_time_ms));
    sessions.truncate(MAX_SESSIONS);

    let path = config_path(SESSIONS_FILE);
    let json = serde_json::to_string(&sessions)
        .map_err(|error| std::io::Error::new(std::io::ErrorKind::Other, error))?;
    write_atomic(&path, &json)?;
    Ok(path)
}

fn write_atomic(path: &str, content: &str) -> std::io::Result<()> {
    if let Some(parent) = Path::new(path).parent() {
        fs::create_dir_all(parent)?;
    }
    let temp = format!("{}.tmp", path);
    fs::write(&temp, content)?;
    let _ = fs::set_permissions(&temp, fs::Permissions::from_mode(0o644));
    fs::rename(&temp, path)
}

pub struct SessionRecorder {
    config: RecordApps,
    labels: LabelCache,
    sensors: Sensors,
    tap: FrameTap,
    active: Option<ActiveSession>,
    last_idle_clear: Instant,
}

impl SessionRecorder {
    pub fn new(tap: FrameTap) -> Self {
        let sensors = Sensors::discover();
        log!(
            "[SESSION] Sensor cache ready | {} thermal zone(s){}",
            sensors.zones.len(),
            if sensors.battery.is_some() {
                " + battery"
            } else {
                ""
            }
        );
        Self {
            config: RecordApps::new(),
            labels: LabelCache::load(),
            sensors,
            tap,
            active: None,
            last_idle_clear: Instant::now(),
        }
    }

    pub fn candidate(&mut self, screen_on: bool, foreground: Option<&str>) -> Option<String> {
        self.config.refresh(false);
        if !screen_on {
            return None;
        }
        let foreground = foreground.map(sanitize_pkg_name).filter(|pkg| !pkg.is_empty())?;
        if self.config.packages.contains(foreground) {
            Some(foreground.to_string())
        } else {
            None
        }
    }

    pub fn tick(&mut self, screen_on: bool, foreground: Option<&str>, profile: &str) {
        self.config.refresh(false);
        let now = Instant::now();
        let foreground = foreground
            .map(sanitize_pkg_name)
            .filter(|pkg| !pkg.is_empty());

        if self.active.is_some() {
            let stop = {
                let active = self
                    .active
                    .as_mut()
                    .expect("active session checked above");
                let in_foreground =
                    screen_on && foreground == Some(active.package.as_str());

                if !self.config.packages.contains(&active.package) {
                    log!(
                        "[SESSION] Recording disabled for {}; closing session",
                        active.package
                    );
                    true
                } else if in_foreground {
                    if active.left_at.take().is_some() {
                        active.last_tick = now;
                        self.tap.clear();
                        log!("[SESSION] Resumed: {}", active.package);
                    } else {
                        let delta = now.saturating_duration_since(active.last_tick);
                        if delta <= Duration::from_secs(2) {
                            active.active_time += delta;
                        }
                        active.last_tick = now;
                    }

                    let offset_sec = active.started_at.elapsed().as_secs() as u32;
                    active.sample_sensors(&self.sensors, now, offset_sec);

                    let mut frames = Vec::new();
                    self.tap.drain_into(&mut frames);
                    active.ingest(&frames, offset_sec);
                    false
                } else {
                    let process_alive = game_process_alive(&active.package);
                    if active.left_at.is_none() {
                        active.left_at = Some(now);
                        if process_alive {
                            log!(
                                "[SESSION] Paused (grace 10s): {}",
                                active.package
                            );
                        } else {
                            log!(
                                "[SESSION] Process gone; finalizing now: {}",
                                active.package
                            );
                        }
                    }
                    self.tap.clear();
                    if !process_alive {
                        true
                    } else {
                        active
                            .left_at
                            .map(|left| left.elapsed() >= GRACE_PERIOD)
                            .unwrap_or(false)
                    }
                }
            };

            if stop {
                self.finish_active();
            }
        } else {
            if screen_on {
                if let Some(foreground) = foreground {
                    if self.config.packages.contains(foreground) {
                        self.start_session(foreground, profile);
                        self.tap.clear();
                    }
                }
            }

            if self.last_idle_clear.elapsed() >= IDLE_TAP_CLEAR_INTERVAL {
                self.tap.clear();
                self.last_idle_clear = now;
            }
        }
    }

    pub fn shutdown(&mut self) {
        if self.active.is_some() {
            self.finish_active();
        }
    }

    pub fn active_start_ms(&self) -> Option<(String, u64)> {
        self.active
            .as_ref()
            .map(|session| (session.package.clone(), session.start_epoch_ms))
    }

    fn start_session(&mut self, package: &str, profile: &str) {
        let app_name = self.labels.resolve(package);
        let session = ActiveSession::new(package, app_name, profile, &self.sensors);
        log!(
            "[RECORD] Started session for {} | {}Hz | {}",
            package,
            session.display_hz,
            profile
        );
        self.active = Some(session);
    }

    fn finish_active(&mut self) {
        let Some(mut active) = self.active.take() else {
            return;
        };

        let offset_sec = active.started_at.elapsed().as_secs() as u32;
        let mut pending = Vec::new();
        self.tap.drain_into(&mut pending);
        active.ingest(&pending, offset_sec);

        let package = active.package.clone();
        let elapsed = active.started_at.elapsed();
        if elapsed < MIN_SESSION {
            log!(
                "[RECORD] Discarded short session for {} ({}s < {}s)",
                package,
                elapsed.as_secs(),
                MIN_SESSION.as_secs()
            );
            self.tap.clear();
            return;
        }

        match active.finalize() {
            Some(session) => {
                let duration_sec = session.duration_sec;
                let avg_fps = session.avg_fps;
                match persist_session(session) {
                    Ok(path) => log!(
                        "[RECORD] Saved session to {} | {} | {}s | {:.1} FPS",
                        path,
                        package,
                        duration_sec,
                        avg_fps
                    ),
                    Err(error) => log!("[RECORD] Save error for {}: {}", package, error),
                }
            }
            None => log!(
                "[RECORD] Nothing to save for {} (finalize returned no record)",
                package
            ),
        }
        self.tap.clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    fn dead_frame_source_session(elapsed: Duration) -> ActiveSession {
        let now = Instant::now();
        ActiveSession {
            package: "com.example.game".to_string(),
            app_name: "Example".to_string(),
            profile: "Maestro".to_string(),
            display_hz: 120,
            start_epoch_ms: 1_700_000_000_000,
            started_at: now - elapsed,
            last_tick: now - elapsed,
            left_at: None,
            active_time: elapsed,
            last_sensor_at: now - elapsed,
            start_battery_temp: None,
            peak_battery_temp: 0.0,
            max_cpu_temp: 0.0,
            power_sum: 0.0,
            power_samples: 0,
            total_frames: 0,
            pacing_count: 0,
            pacing_mean: 0.0,
            pacing_m2: 0.0,
            histogram: vec![0; HIST_BINS],
            jank_count: 0,
            buckets: Vec::new(),
        }
    }

    #[test]
    fn zero_frame_source_still_saves_at_ten_seconds() {
        let session = dead_frame_source_session(Duration::from_secs(10));
        let stored = session.finalize().expect("10s session must still save");
        assert_eq!(stored.avg_fps, 0.0);
        assert_eq!(stored.min_fps, 0);
        assert_eq!(stored.max_fps, 0);
        assert!(stored.samples.is_empty());
        assert_eq!(stored.duration_sec, 10);
    }

    #[test]
    fn zero_frame_source_saves_with_some_sensor_points() {
        let mut session = dead_frame_source_session(Duration::from_secs(12));
        session.max_cpu_temp = 41.5;
        session.buckets.push(Bucket {
            offset_sec: 0,
            cpu_temp: 41.5,
            battery_temp: 33.0,
            power_mw: 2500.0,
            ..Bucket::default()
        });
        let stored = session.finalize().expect("12s session must still save");
        assert_eq!(stored.avg_fps, 0.0);
        assert!(stored.max_cpu_temp_c >= 41.5);
        assert_eq!(stored.avg_power_mw, 0);
    }

    #[test]
    fn session_below_floor_is_rejected() {
        let session = dead_frame_source_session(Duration::from_secs(9));
        assert!(session.finalize().is_none());
    }

    #[test]
    fn stored_session_round_trips_through_json() {
        let session = dead_frame_source_session(Duration::from_secs(10));
        let stored = session.finalize().unwrap();
        let json = serde_json::to_string(&stored).unwrap();
        let parsed: StoredSession = serde_json::from_str(&json).unwrap();
        assert_eq!(parsed.package_name, "com.example.game");
        assert_eq!(parsed.duration_sec, 10);
        assert!(json.contains("\"packageName\""));
    }

    #[test]
    fn samples_are_capped_for_long_sessions() {
        let mut buckets = Vec::with_capacity(2_000);
        for sec in 0..2_000u32 {
            buckets.push(Bucket {
                offset_sec: sec,
                frames: vec![16.6],
                ..Bucket::default()
            });
        }
        let samples = build_samples(&buckets, 120.0);
        assert_eq!(samples.len(), MAX_SAMPLE_POINTS);

        assert!(samples.windows(2).all(|pair| pair[0].offset_sec <= pair[1].offset_sec));
    }

    #[test]
    fn short_sessions_keep_one_point_per_second() {
        let mut buckets = Vec::with_capacity(30);
        for sec in 0..30u32 {
            buckets.push(Bucket {
                offset_sec: sec,
                frames: vec![16.6],
                ..Bucket::default()
            });
        }
        assert_eq!(build_samples(&buckets, 120.0).len(), 30);
    }
}
