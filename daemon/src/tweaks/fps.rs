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

use std::collections::VecDeque;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::process::{Command, Stdio};
use std::sync::mpsc::{self, Receiver, Sender};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use kala_ebpf::FrameProbe;

const STATUS_PATH: &str = "/data/adb/.config/lumina/fps.txt";
pub const PUBLIC_STATUS_PATH: &str = "/data/local/tmp/lumina.fps";

const APP_FPS_PATH: &str = "/data/data/com.luminatweaks/files/lumina.fps";
const APP_PACKAGE_DIR: &str = "/data/data/com.luminatweaks";
const SAMPLE_INTERVAL: Duration = Duration::from_millis(500);
const SLOW_INTERVAL: Duration = Duration::from_millis(1000);
const IDLE_INTERVAL: Duration = Duration::from_secs(20);
const RETRY_SOURCE_AFTER: Duration = Duration::from_secs(10);
const MAX_CONSECUTIVE_MISSES: u32 = 3;
const WINDOW_NS: u64 = 1_000_000_000;
const EBPF_WINDOW: Duration = Duration::from_secs(1);
const EBPF_STALE_AFTER: Duration = Duration::from_millis(1500);
const EBPF_RETRY_AFTER: Duration = Duration::from_secs(30);
const EBPF_MAX_HISTORY: usize = 144;
const EBPF_MAX_FRAME_TIME: Duration = Duration::from_secs(2);
const IDLE_MARKER: f32 = -1.0;
const FPSGO_STATUS_PATHS: &[&str] = &[
    "/sys/kernel/debug/fpsgo/fstb/fpsgo_status",
    "/sys/kernel/fpsgo/fstb/fpsgo_status",
];

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
    let mut value = Timespec {
        tv_sec: 0,
        tv_nsec: 0,
    };
    let result = unsafe { clock_gettime(CLOCK_MONOTONIC, &mut value) };
    if result != 0 || value.tv_sec < 0 {
        return 0;
    }
    (value.tv_sec as u64)
        .saturating_mul(1_000_000_000)
        .saturating_add(value.tv_nsec.max(0) as u64)
}

fn write_status_file(path: &str, frame: &str) {
    let Some(parent) = Path::new(path).parent() else {
        return;
    };
    if fs::create_dir_all(parent).is_err() {
        return;
    }

    let temp = format!("{}.tmp", path);
    if fs::write(&temp, frame).is_ok() {
        let _ = fs::set_permissions(&temp, fs::Permissions::from_mode(0o644));
        let _ = fs::rename(&temp, path);
    }
}

enum GpuSource {
    Pair(String),
    Percent(String),
}

struct LoadSampler {
    previous_cpu: Option<(u64, u64)>,
    gpu_sources: Vec<GpuSource>,
    last_cpu: f32,
    last_gpu: f32,
}

impl LoadSampler {
    fn new() -> Self {
        Self {
            previous_cpu: None,
            gpu_sources: discover_gpu_sources(),
            last_cpu: 0.0,
            last_gpu: 0.0,
        }
    }

    fn sample(&mut self) -> (f32, f32) {
        if let Some(value) = self.sample_cpu() {
            self.last_cpu = value;
        }
        if let Some(value) = self.sample_gpu() {
            self.last_gpu = value;
        }
        (self.last_cpu, self.last_gpu)
    }

    fn sample_cpu(&mut self) -> Option<f32> {
        let text = fs::read_to_string("/proc/stat").ok()?;
        let line = text.lines().find(|line| line.starts_with("cpu "))?;
        let values: Vec<u64> = line
            .split_whitespace()
            .skip(1)
            .filter_map(|value| value.parse::<u64>().ok())
            .collect();
        if values.len() < 5 {
            return None;
        }
        let idle = values[3].saturating_add(values[4]);
        let total = values
            .iter()
            .fold(0u64, |acc, value| acc.saturating_add(*value));
        let (previous_total, previous_idle) = self.previous_cpu.replace((total, idle))?;
        let total_delta = total.saturating_sub(previous_total);
        let idle_delta = idle.saturating_sub(previous_idle);
        if total_delta == 0 {
            return None;
        }
        let busy = total_delta.saturating_sub(idle_delta) as f32;
        Some((busy * 100.0 / total_delta as f32).clamp(0.0, 100.0))
    }

    fn sample_gpu(&self) -> Option<f32> {
        let mut fallback = None;
        for source in &self.gpu_sources {
            let value = match source {
                GpuSource::Pair(path) => read_gpu_pair(path),
                GpuSource::Percent(path) => read_gpu_percent(path),
            };
            if let Some(value) = value {
                if value > 0.0 {
                    return Some(value);
                }
                fallback = Some(value);
            }
        }
        read_gpu_clock_percent().or(fallback)
    }
}

fn read_gpu_clock_percent() -> Option<f32> {
    for (cur_path, max_path) in [
        ("/sys/kernel/gpu/gpu_clock", "/sys/kernel/gpu/gpu_max_clock"),
        (
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
        ),
        (
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
        ),
    ] {
        let cur = fs::read_to_string(cur_path)
            .ok()
            .and_then(|text| text.trim().parse::<u64>().ok());
        let max = fs::read_to_string(max_path)
            .ok()
            .and_then(|text| text.trim().parse::<u64>().ok());
        if let (Some(cur), Some(max)) = (cur, max) {
            if max > 0 {
                return Some((cur as f32 * 100.0 / max as f32).clamp(1.0, 100.0));
            }
        }
    }
    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
            if !name.contains("gpu") && !name.contains("mali") && !name.contains("kgsl") {
                continue;
            }
            let cur = fs::read_to_string(entry.path().join("cur_freq"))
                .ok()
                .and_then(|text| text.trim().parse::<u64>().ok());
            let max = fs::read_to_string(entry.path().join("max_freq"))
                .ok()
                .and_then(|text| text.trim().parse::<u64>().ok());
            if let (Some(cur), Some(max)) = (cur, max) {
                if max > 0 {
                    return Some((cur as f32 * 100.0 / max as f32).clamp(1.0, 100.0));
                }
            }
        }
    }
    None
}

fn discover_gpu_sources() -> Vec<GpuSource> {
    let mut sources = Vec::new();
    let pair_path = "/sys/class/kgsl/kgsl-3d0/gpubusy";
    if Path::new(pair_path).exists() {
        sources.push(GpuSource::Pair(pair_path.to_string()));
    }
    for path in [
        "/sys/module/ged/parameters/gpu_loading",
        "/sys/kernel/ged/hal/gpu_utilization",
        "/sys/kernel/ged/hal/gpu_loading",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/class/misc/mali0/device/utilization",
        "/sys/class/misc/mali0/device/utilisation",
        "/proc/mali/utilization",
        "/sys/class/devfreq/gpufreq/gpu_busy_percentage",
        "/sys/class/devfreq/gpufreq/gpu_loading",
    ] {
        if Path::new(path).exists() {
            sources.push(GpuSource::Percent(path.to_string()));
        }
    }
    if let Ok(entries) = fs::read_dir("/sys/devices/platform") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
            if !name.contains("mali") && !name.contains("gpu") {
                continue;
            }
            for node in ["utilisation", "utilization", "gpu_loading", "load"] {
                let path = entry.path().join(node);
                if path.is_file() {
                    sources.push(GpuSource::Percent(path.to_string_lossy().into_owned()));
                }
            }
        }
    }
    if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
            if !name.contains("gpu") && !name.contains("mali") && !name.contains("kgsl") {
                continue;
            }
            for node in [
                "gpu_busy_percentage",
                "gpu_load",
                "utilisation",
                "utilization",
                "load",
            ] {
                let path = entry.path().join(node);
                if path.is_file() {
                    sources.push(GpuSource::Percent(path.to_string_lossy().into_owned()));
                }
            }
        }
    }
    if sources.is_empty() {
        for path in [
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        ] {
            if Path::new(path).exists() {
                sources.push(GpuSource::Percent(path.to_string()));
            }
        }
    }
    sources
}

fn read_gpu_pair(path: &str) -> Option<f32> {
    let text = fs::read_to_string(path).ok()?;
    let mut parts = text.split_whitespace();
    let busy = parts.next()?.parse::<f32>().ok()?;
    let total = parts.next()?.parse::<f32>().ok()?;
    if !busy.is_finite() || !total.is_finite() || total <= 0.0 {
        return None;
    }
    Some((busy / total * 100.0).clamp(0.0, 100.0))
}

fn read_gpu_percent(path: &str) -> Option<f32> {
    let text = fs::read_to_string(path).ok()?;
    parse_leading_number(&text)
        .filter(|value| value.is_finite() && *value >= 0.0)
        .map(|value| value.clamp(0.0, 100.0))
}

fn parse_leading_number(text: &str) -> Option<f32> {
    let start = text
        .char_indices()
        .find(|(_, ch)| ch.is_ascii_digit() || *ch == '-')
        .map(|(index, _)| index)?;
    let rest = &text[start..];
    let end = rest
        .char_indices()
        .find(|(_, ch)| !(ch.is_ascii_digit() || *ch == '.' || *ch == '-'))
        .map(|(index, _)| index)
        .unwrap_or(rest.len());
    rest[..end].parse::<f32>().ok()
}

fn sample_load() -> (f32, f32) {
    static SAMPLER: OnceLock<Mutex<LoadSampler>> = OnceLock::new();
    let sampler = SAMPLER.get_or_init(|| Mutex::new(LoadSampler::new()));
    match sampler.lock() {
        Ok(mut guard) => guard.sample(),
        Err(_) => (0.0, 0.0),
    }
}

fn write_status(fps: f32, frametime_ms: f32, game_active: bool, package: Option<&str>) {
    let (cpu, gpu) = sample_load();
    let frame = format!(
        "{:.1} {} {:.1} {} {} {:.1} {:.1}",
        fps,
        epoch_ms(),
        frametime_ms.max(0.0),
        if game_active { 1 } else { 0 },
        package.unwrap_or("-"),
        cpu,
        gpu,
    );
    write_status_file(STATUS_PATH, &frame);
    write_status_file(PUBLIC_STATUS_PATH, &frame);
    if Path::new(APP_PACKAGE_DIR).exists() {
        write_status_file(APP_FPS_PATH, &frame);
    }
    publish_property(&frame);
    publish_load(cpu, gpu);
}

fn publish_load(cpu: f32, gpu: f32) {
    let _ = crate::core::sysprop::set("sys.lumina.cpu", &format!("{:.1}", cpu));
    let _ = crate::core::sysprop::set("sys.lumina.gpu", &format!("{:.1}", gpu));
}

fn write_idle(game_active: bool, package: Option<&str>) {
    write_status(IDLE_MARKER, 0.0, game_active, package);
}

fn publish_property(frame: &str) {
    let _ = crate::core::sysprop::set("sys.lumina.fps", frame);
}

fn epoch_ms() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis())
        .unwrap_or(0)
}

fn run_command(args: &[&str]) -> Option<String> {
    let (program, rest) = args.split_first()?;
    let output = Command::new(program)
        .args(rest)
        .stdin(Stdio::null())
        .stderr(Stdio::null())
        .output()
        .ok()?;
    if !output.status.success() {
        return None;
    }
    Some(String::from_utf8_lossy(&output.stdout).into_owned())
}

#[derive(Clone)]
struct RawLayer {
    name: String,
    frames: u64,
    average_fps: f32,
}

fn score_layer(name: &str) -> u8 {
    let lower = name.to_ascii_lowercase();
    let mut score = 0u8;
    if lower.contains("surfaceview") {
        score += 4;
    }
    if lower.contains("blast") {
        score += 2;
    }
    if lower.contains("vulkan") || lower.contains("angle") {
        score += 1;
    }
    score
}

fn parse_timestats(text: &str, package: &str) -> Option<RawLayer> {
    let package = package.to_ascii_lowercase();
    let mut best: Option<RawLayer> = None;
    let mut best_score = 0u8;

    let mut name: Option<String> = None;
    let mut frames: u64 = 0;
    let mut fps: f32 = 0.0;
    let mut waiting_name = false;

    let flush = |name: &Option<String>,
                 frames: u64,
                 fps: f32,
                 best: &mut Option<RawLayer>,
                 best_score: &mut u8| {
        let Some(layer) = name.as_deref() else {
            return;
        };
        if !layer.to_ascii_lowercase().contains(&package)
            || frames == 0
            || !fps.is_finite()
            || fps <= 0.0
        {
            return;
        }

        let lower = layer.to_ascii_lowercase();
        if lower.contains("background for")
            || lower.contains("dim layer")
            || lower.contains("screenshot")
        {
            return;
        }
        let score = score_layer(layer);
        if best.is_none()
            || score > *best_score
            || (score == *best_score && frames > best.as_ref().map(|l| l.frames).unwrap_or(0))
        {
            *best_score = score;
            *best = Some(RawLayer {
                name: layer.to_string(),
                frames,
                average_fps: fps,
            });
        }
    };

    for raw in text.lines() {
        let line = raw.trim();
        if line.starts_with("layerName =") {
            flush(&name, frames, fps, &mut best, &mut best_score);
            let value = line["layerName =".len()..].trim();
            name = if value.is_empty() {
                waiting_name = true;
                None
            } else {
                Some(value.to_string())
            };
            frames = 0;
            fps = 0.0;
        } else if waiting_name && !line.is_empty() {
            name = Some(line.to_string());
            waiting_name = false;
        } else if name.is_some() {
            if let Some(value) = line.strip_prefix("totalFrames =") {
                frames = value.trim().parse().unwrap_or(0);
            } else if let Some(value) = line.strip_prefix("averageFPS =") {
                fps = value.trim().parse().unwrap_or(0.0);
            }
        }
    }
    flush(&name, frames, fps, &mut best, &mut best_score);
    best
}

fn timestats_command(extra: &str) -> Option<String> {
    let mut args = vec!["dumpsys", "SurfaceFlinger", "--timestats"];
    args.extend(extra.split_whitespace());
    run_command(&args)
}

fn enable_timestats() {
    let _ = timestats_command("-clear -enable");
}

fn disable_timestats() {
    let _ = timestats_command("-disable");
}

fn query_timestats(package: &str) -> Option<RawLayer> {
    let text = timestats_command("-dump -clear")?;
    parse_timestats(&text, package)
}

fn find_layer(package: &str) -> Option<String> {
    let text = run_command(&["dumpsys", "SurfaceFlinger", "--list"])?;
    let package = package.to_ascii_lowercase();
    let mut best: Option<(u8, String)> = None;
    for line in text.lines().map(str::trim) {
        if line.is_empty() {
            continue;
        }
        let lower = line.to_ascii_lowercase();
        if !lower.contains(&package)
            || lower.contains("background for")
            || lower.contains("dim layer")
            || lower.contains("screenshot")
        {
            continue;
        }
        let score = score_layer(line);
        if best
            .as_ref()
            .map(|(current, _)| score >= *current)
            .unwrap_or(true)
        {
            best = Some((score, line.to_string()));
        }
    }
    best.map(|(_, layer)| layer)
}

fn query_latency(package: &str) -> Option<(f32, f32)> {
    let layer = find_layer(package)?;
    let text = run_command(&["dumpsys", "SurfaceFlinger", "--latency", &layer])?;
    let mut timestamps: Vec<u64> = Vec::new();
    for line in text.lines().skip(1) {
        let values: Vec<u64> = line
            .split_whitespace()
            .filter_map(|value| value.parse().ok())
            .collect();
        if values.len() < 2 {
            continue;
        }
        let present = values[1];
        if present == 0 || present == u64::MAX {
            continue;
        }
        timestamps.push(present);
    }
    if timestamps.len() < 2 {
        return None;
    }
    timestamps.sort_unstable();
    timestamps.dedup();
    let newest = *timestamps.last()?;
    let now = monotonic_ns();
    if now > 0 && now.saturating_sub(newest) > WINDOW_NS {
        return None;
    }
    let cutoff = newest.saturating_sub(WINDOW_NS);
    let window: Vec<u64> = timestamps.into_iter().filter(|ts| *ts >= cutoff).collect();
    if window.len() < 2 {
        return None;
    }
    let span = window[window.len() - 1].saturating_sub(window[0]);
    if span == 0 {
        return None;
    }
    let fps = ((window.len() - 1) as f64 * 1_000_000_000.0 / span as f64) as f32;
    let frametime = span as f32 / (window.len() - 1) as f32 / 1_000_000.0;
    Some((fps, frametime))
}

fn query_gfxinfo(package: &str) -> Option<(f32, f32)> {
    let text = run_command(&["dumpsys", "gfxinfo", package, "framestats"])?;
    let mut in_data = false;
    let mut timestamps: Vec<u64> = Vec::new();
    for line in text.lines() {
        let line = line.trim();
        if line == "---PROFILEDATA---" {
            in_data = !in_data;
            continue;
        }
        if !in_data || line.is_empty() || line.starts_with("Flags") {
            continue;
        }
        let values: Vec<&str> = line.split(',').collect();
        if values.len() < 13 {
            continue;
        }

        let present = values
            .get(18)
            .and_then(|v| v.trim().parse::<u64>().ok())
            .filter(|v| *v > 0)
            .or_else(|| values.get(12).and_then(|v| v.trim().parse::<u64>().ok()));
        if let Some(present) = present {
            timestamps.push(present);
        }
    }
    if timestamps.len() < 2 {
        return None;
    }
    timestamps.sort_unstable();
    let newest = *timestamps.last()?;
    let now = monotonic_ns();
    if now > 0 && now.saturating_sub(newest) > WINDOW_NS {
        return None;
    }
    let cutoff = newest.saturating_sub(WINDOW_NS);
    let window: Vec<u64> = timestamps.into_iter().filter(|ts| *ts >= cutoff).collect();
    if window.len() < 2 {
        return None;
    }
    let span = window[window.len() - 1].saturating_sub(window[0]);
    if span == 0 {
        return None;
    }
    let fps = ((window.len() - 1) as f64 * 1_000_000_000.0 / span as f64) as f32;
    let frametime = span as f32 / (window.len() - 1) as f32 / 1_000_000.0;
    Some((fps, frametime))
}

fn query_fpsgo(pid: i32) -> Option<(f32, f32)> {
    if pid <= 0 {
        return None;
    }
    for path in FPSGO_STATUS_PATHS {
        let Ok(text) = fs::read_to_string(path) else {
            continue;
        };
        for line in text.lines() {
            let fields: Vec<&str> = line.split_whitespace().collect();
            if fields.len() < 6 || fields[0].parse::<i32>().ok() != Some(pid) {
                continue;
            }
            let Ok(fps) = fields[5].parse::<f32>() else {
                continue;
            };
            if fps.is_finite() && fps > 0.0 && fps <= 360.0 {
                return Some((fps, 1000.0 / fps));
            }
        }
    }
    None
}

#[derive(Clone, Copy, PartialEq)]
enum Source {
    TimeStats,
    Latency,
    GfxInfo,
    Vendor,
    Unavailable,
}

impl Source {
    fn next(self) -> Self {
        match self {
            Source::TimeStats => Source::Latency,
            Source::Latency => Source::GfxInfo,
            Source::GfxInfo => Source::Vendor,
            Source::Vendor | Source::Unavailable => Source::Unavailable,
        }
    }
}

struct EbpfSource {
    probe: FrameProbe,
    pid: i32,
    attached_at: Instant,
    frames: VecDeque<(Instant, Duration)>,
    last_frame: Option<Instant>,
}

impl EbpfSource {
    fn attach(pid: i32) -> Option<Self> {
        let mut probe = FrameProbe::new().ok()?;
        probe.attach(pid).ok()?;
        Some(Self {
            probe,
            pid,
            attached_at: Instant::now(),
            frames: VecDeque::new(),
            last_frame: None,
        })
    }

    fn drain(&mut self) {
        while let Some((pid, frametime)) = self.probe.recv_with_deadline(Duration::ZERO) {
            if pid != self.pid || frametime.is_zero() || frametime > EBPF_MAX_FRAME_TIME {
                continue;
            }
            let now = Instant::now();
            self.frames.push_back((now, frametime));
            self.last_frame = Some(now);
        }

        let now = Instant::now();
        while self
            .frames
            .front()
            .map(|(at, _)| now.saturating_duration_since(*at) > EBPF_MAX_FRAME_TIME)
            .unwrap_or(false)
        {
            self.frames.pop_front();
        }
        while self.frames.len() > EBPF_MAX_HISTORY {
            self.frames.pop_front();
        }
    }

    fn reading(&self) -> Option<(f32, f32)> {
        let last = self.last_frame?;
        if last.elapsed() > EBPF_STALE_AFTER {
            return None;
        }

        let now = Instant::now();
        let mut count = 0usize;
        let mut total = Duration::ZERO;
        for (at, frametime) in self.frames.iter().rev() {
            if now.saturating_duration_since(*at) > EBPF_WINDOW {
                break;
            }
            total = total.saturating_add(*frametime);
            count += 1;
        }
        if count < 2 {
            return None;
        }

        let total_ns = total.as_nanos().max(1) as f64;
        let fps = count as f64 * 1_000_000_000.0 / total_ns;
        if !fps.is_finite() || !(0.1..=360.0).contains(&fps) {
            return None;
        }
        let frametime_ms = (total_ns / count as f64 / 1_000_000.0) as f32;
        Some((fps as f32, frametime_ms))
    }

    fn has_recent_frames(&self) -> bool {
        self.last_frame
            .map(|at| at.elapsed() <= EBPF_STALE_AFTER)
            .unwrap_or(false)
    }

    fn warming_up(&self) -> bool {
        self.last_frame.is_none() && self.attached_at.elapsed() < EBPF_STALE_AFTER
    }
}

fn maintain_ebpf(
    ebpf: &mut Option<EbpfSource>,
    failed_at: &mut Option<Instant>,
    want_pid: Option<i32>,
) {
    let want_pid = want_pid.filter(|pid| *pid > 0);

    if ebpf
        .as_ref()
        .map(|source| Some(source.pid) != want_pid)
        .unwrap_or(false)
    {
        *ebpf = None;
    }

    let Some(pid) = want_pid else {
        return;
    };
    if ebpf.is_some() {
        return;
    }
    if failed_at
        .map(|at| at.elapsed() < EBPF_RETRY_AFTER)
        .unwrap_or(false)
    {
        return;
    }

    match EbpfSource::attach(pid) {
        Some(source) => {
            *ebpf = Some(source);
            *failed_at = None;
            log!("[FPS] eBPF uprobe attached to pid {}", pid);
        }
        None => {
            *failed_at = Some(Instant::now());
            log!("[FPS] eBPF unavailable; using SurfaceFlinger fallback");
        }
    }
}

enum Request {
    Track {
        package: Option<String>,
        game_active: bool,
        pid: i32,
    },
    Stop,
}

pub struct FpsMonitor {
    tx: Sender<Request>,
    last: Option<(Option<String>, bool, i32)>,
    stopped: Arc<Mutex<bool>>,
}

impl FpsMonitor {
    pub fn new() -> Self {
        let (tx, rx) = mpsc::channel();
        let stopped = Arc::new(Mutex::new(false));
        let worker_stopped = Arc::clone(&stopped);

        let _ = thread::Builder::new()
            .name("fps-sampler".to_string())
            .spawn(move || sampler_loop(rx, worker_stopped));

        Self {
            tx,
            last: None,
            stopped,
        }
    }

    pub fn observe(&mut self, package: Option<&str>, game_active: bool, pid: i32) {
        let package = package.map(str::to_string);
        let pid = pid.max(0);
        let current = (package, game_active, pid);

        if self.last.as_ref() == Some(&current) {
            return;
        }

        let (package, game_active, pid) = current.clone();
        self.last = Some(current);
        let _ = self.tx.send(Request::Track {
            package,
            game_active,
            pid,
        });
    }

    pub fn stop(&self) {
        if let Ok(mut stopped) = self.stopped.lock() {
            *stopped = true;
        }
        let _ = self.tx.send(Request::Stop);
    }
}

fn sampler_loop(rx: Receiver<Request>, stopped: Arc<Mutex<bool>>) {
    let mut package: Option<String> = None;
    let mut game_active = false;
    let mut pid = 0;

    let mut source = Source::TimeStats;
    let mut missing = 0u32;
    let mut unavailable_at: Option<Instant> = None;

    let mut timestats_ready = false;
    let mut last_window_start = 0u64;
    let mut last_layer: Option<String> = None;
    let mut slow = false;
    let mut ebpf: Option<EbpfSource> = None;
    let mut ebpf_failed_at: Option<Instant> = None;

    loop {
        if stopped.lock().map(|s| *s).unwrap_or(true) {
            if timestats_ready {
                disable_timestats();
            }
            drop(ebpf.take());
            write_idle(false, None);
            return;
        }

        let wait = if package.is_some() {
            if slow {
                SLOW_INTERVAL
            } else {
                SAMPLE_INTERVAL
            }
        } else {
            IDLE_INTERVAL
        };

        match rx.recv_timeout(wait) {
            Ok(Request::Track {
                package: next,
                game_active: next_game,
                pid: next_pid,
            }) => {
                let changed = next != package || next_game != game_active || next_pid != pid;
                if next != package {
                    last_window_start = 0;
                    last_layer = None;
                    missing = 0;
                    source = Source::TimeStats;
                    unavailable_at = None;
                    ebpf = None;
                    ebpf_failed_at = None;
                }
                if next_pid != pid {
                    ebpf_failed_at = None;
                }
                package = next;
                game_active = next_game;
                pid = next_pid;
                if changed {
                    if package.is_none() {
                        if timestats_ready {
                            disable_timestats();
                            timestats_ready = false;
                        }
                        drop(ebpf.take());
                        ebpf_failed_at = None;
                        write_idle(false, None);
                        continue;
                    }
                    write_idle(game_active, package.as_deref());
                }
            }
            Ok(Request::Stop) | Err(mpsc::RecvTimeoutError::Disconnected) => {
                if timestats_ready {
                    disable_timestats();
                }
                drop(ebpf.take());
                write_idle(false, None);
                return;
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {}
        }

        let Some(ref target) = package else {
            continue;
        };

        let target_pid = package.as_deref().and_then(|pkg| {
            (pid > 0 && crate::core::detector::pid_matches_package(pid, pkg)).then_some(pid)
        });
        maintain_ebpf(&mut ebpf, &mut ebpf_failed_at, target_pid);
        if let Some(probe) = ebpf.as_mut() {
            probe.drain();
        }

        let ebpf_reading = ebpf.as_ref().and_then(EbpfSource::reading);
        let ebpf_fresh = ebpf
            .as_ref()
            .map(EbpfSource::has_recent_frames)
            .unwrap_or(false);
        let ebpf_warming = false;

        if let Some((fps, frametime)) = ebpf_reading {
            missing = 0;
            slow = fps < 1.0;
            source = Source::TimeStats;
            unavailable_at = None;
            last_window_start = 0;
            last_layer = None;
            if timestats_ready {
                disable_timestats();
                timestats_ready = false;
            }
            write_status(fps, frametime, game_active, package.as_deref());
            continue;
        }
        if ebpf_fresh || ebpf_warming {
            continue;
        }

        if source == Source::Unavailable {
            if unavailable_at
                .map(|at| at.elapsed() >= RETRY_SOURCE_AFTER)
                .unwrap_or(true)
            {
                source = Source::TimeStats;
                unavailable_at = None;
                last_window_start = 0;
                last_layer = None;
            } else {
                write_idle(game_active, package.as_deref());
                continue;
            }
        }

        if source == Source::TimeStats && !timestats_ready {
            enable_timestats();
            timestats_ready = true;
            last_window_start = 0;
            last_layer = None;
        }

        let now = monotonic_ns();
        let mut reading = None;

        match source {
            Source::TimeStats => {
                if let Some(raw) = query_timestats(target) {
                    let layer_changed = last_layer.as_deref() != Some(raw.name.as_str());
                    let mut fps = raw.average_fps;
                    let mut frametime = if fps > 0.0 { 1000.0 / fps } else { 0.0 };

                    let window = if last_window_start > 0 && !layer_changed {
                        now.saturating_sub(last_window_start)
                    } else {
                        0
                    };
                    if window >= 100_000_000 && raw.frames >= 1 {
                        fps = raw.frames as f32 * 1_000_000_000.0 / window as f32;
                        frametime = window as f32 / raw.frames as f32 / 1_000_000.0;
                    }

                    if fps.is_finite() && (0.0..=360.0).contains(&fps) {
                        reading = Some((fps, frametime));
                    }
                    last_window_start = now;
                    last_layer = Some(raw.name);
                }
            }
            Source::Latency => {
                reading = query_latency(target);
            }
            Source::GfxInfo => {
                reading = query_gfxinfo(target);
            }
            Source::Vendor => {
                reading = query_fpsgo(pid);
            }
            Source::Unavailable => {}
        }

        match reading {
            Some((fps, frametime)) => {
                missing = 0;
                slow = fps < 1.0 || source != Source::Vendor;
                write_status(fps, frametime, game_active, package.as_deref());
            }
            None => {
                missing += 1;
                if missing >= MAX_CONSECUTIVE_MISSES {
                    missing = 0;
                    let next = source.next();
                    if source == Source::TimeStats && timestats_ready {
                        disable_timestats();
                        timestats_ready = false;
                    }
                    source = next;
                    slow = next != Source::Vendor;
                    if source == Source::Unavailable {
                        unavailable_at = Some(Instant::now());
                    }

                    write_idle(game_active, package.as_deref());
                }
            }
        }
    }
}
