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
use std::sync::{Arc, Mutex};
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
const FRAME_TAP_CAPACITY: usize = 8192;
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

#[derive(Default)]
struct TapState {
    frames: VecDeque<f32>,
    measured: VecDeque<f32>,
    measured_seq: u64,
}

#[derive(Clone)]
pub struct FrameSample {
    pub seq: u64,
    pub frames: Vec<f32>,
}

#[derive(Clone)]
pub struct FrameTap {
    state: Arc<Mutex<TapState>>,
}

impl FrameTap {
    fn new() -> Self {
        Self {
            state: Arc::new(Mutex::new(TapState::default())),
        }
    }

    fn push(&self, frametime_ms: f32) {
        self.push_internal(frametime_ms, false);
    }

    pub fn push_measured(&self, frametime_ms: f32) {
        self.push_internal(frametime_ms, true);
    }

    fn push_internal(&self, frametime_ms: f32, measured: bool) {
        if !frametime_ms.is_finite() || frametime_ms <= 0.0 {
            return;
        }
        if let Ok(mut state) = self.state.lock() {
            if state.frames.len() >= FRAME_TAP_CAPACITY {
                state.frames.pop_front();
            }
            state.frames.push_back(frametime_ms);
            if measured {
                if state.measured.len() >= FRAME_TAP_CAPACITY {
                    state.measured.pop_front();
                }
                state.measured.push_back(frametime_ms);
                state.measured_seq = state.measured_seq.saturating_add(1);
            }
        }
    }

    fn push_window(&self, fps: f32, frametime_ms: f32) {
        if !fps.is_finite() || !frametime_ms.is_finite() || fps <= 0.0 || frametime_ms <= 0.0 {
            return;
        }
        let frames = (1000.0 / frametime_ms).round().clamp(1.0, 600.0) as usize;
        for _ in 0..frames {
            self.push(frametime_ms);
        }
    }

    pub fn drain_into(&self, out: &mut Vec<f32>) {
        if let Ok(mut state) = self.state.lock() {
            out.reserve(state.frames.len());
            out.extend(state.frames.drain(..));
        }
    }

    pub fn measured_snapshot(&self, limit: usize) -> FrameSample {
        let Ok(state) = self.state.lock() else {
            return FrameSample {
                seq: 0,
                frames: Vec::new(),
            };
        };
        let take = limit.min(state.measured.len());
        let frames = state
            .measured
            .iter()
            .skip(state.measured.len().saturating_sub(take))
            .copied()
            .collect();
        FrameSample {
            seq: state.measured_seq,
            frames,
        }
    }

    pub fn clear(&self) {
        if let Ok(mut state) = self.state.lock() {
            state.frames.clear();
            state.measured.clear();
        }
    }
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

fn write_status(
    fps: f32,
    frametime_ms: f32,
    game_active: bool,
    package: Option<&str>,
    session_start_ms: Option<u64>,
) {
    let frame = format!(
        "{:.1} {} {:.1} {} {} {}",
        fps,
        epoch_ms(),
        frametime_ms.max(0.0),
        if game_active { 1 } else { 0 },
        package.unwrap_or("-"),
        session_start_ms
            .map(|start| start.to_string())
            .unwrap_or_else(|| "-".to_string())
    );
    write_status_file(STATUS_PATH, &frame);
    write_status_file(PUBLIC_STATUS_PATH, &frame);
    if Path::new(APP_PACKAGE_DIR).exists() {
        write_status_file(APP_FPS_PATH, &frame);
    }
    publish_property(&frame);
}

fn write_idle(game_active: bool, package: Option<&str>, session_start_ms: Option<u64>) {
    write_status(IDLE_MARKER, 0.0, game_active, package, session_start_ms);
}

fn publish_property(frame: &str) {
    let _ = Command::new("setprop")
        .args(["sys.lumina.fps", frame])
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();
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

    fn drain(&mut self, tap: &FrameTap) {
        while let Some((pid, frametime)) = self.probe.recv_with_deadline(Duration::ZERO) {
            if pid != self.pid || frametime.is_zero() || frametime > EBPF_MAX_FRAME_TIME {
                continue;
            }
            tap.push_measured(frametime.as_secs_f32() * 1000.0);
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
        session_start_ms: Option<u64>,
    },
    Stop,
}

pub struct FpsMonitor {
    tx: Sender<Request>,
    last: Option<(Option<String>, bool, i32, Option<u64>)>,
    stopped: Arc<Mutex<bool>>,
    tap: FrameTap,
}

impl FpsMonitor {
    pub fn new() -> Self {
        let (tx, rx) = mpsc::channel();
        let stopped = Arc::new(Mutex::new(false));
        let worker_stopped = Arc::clone(&stopped);
        let tap = FrameTap::new();
        let worker_tap = tap.clone();

        let _ = thread::Builder::new()
            .name("fps-sampler".to_string())
            .spawn(move || sampler_loop(rx, worker_stopped, worker_tap));

        Self {
            tx,
            last: None,
            stopped,
            tap,
        }
    }

    pub fn tap(&self) -> FrameTap {
        self.tap.clone()
    }

    pub fn observe(
        &mut self,
        package: Option<&str>,
        game_active: bool,
        pid: i32,
        session_start_ms: Option<u64>,
    ) {
        let package = package.map(str::to_string);
        let pid = pid.max(0);
        let current = (package, game_active, pid, session_start_ms);

        if self.last.as_ref() == Some(&current) {
            return;
        }

        let (package, game_active, pid, session_start_ms) = current.clone();
        self.last = Some(current);
        let _ = self.tx.send(Request::Track {
            package,
            game_active,
            pid,
            session_start_ms,
        });
    }

    pub fn stop(&self) {
        if let Ok(mut stopped) = self.stopped.lock() {
            *stopped = true;
        }
        let _ = self.tx.send(Request::Stop);
    }
}

fn sampler_loop(rx: Receiver<Request>, stopped: Arc<Mutex<bool>>, tap: FrameTap) {
    let mut package: Option<String> = None;
    let mut game_active = false;
    let mut pid = 0;
    let mut session_start_ms: Option<u64> = None;

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
            write_idle(false, None, None);
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
                session_start_ms: next_start,
            }) => {
                let changed = next != package
                    || next_game != game_active
                    || next_pid != pid
                    || next_start != session_start_ms;
                if next != package {
                    last_window_start = 0;
                    last_layer = None;
                    missing = 0;
                    source = Source::TimeStats;
                    unavailable_at = None;
                    ebpf = None;
                    ebpf_failed_at = None;
                    tap.clear();
                }
                if next_pid != pid {
                    ebpf_failed_at = None;
                }
                package = next;
                game_active = next_game;
                pid = next_pid;
                session_start_ms = next_start;
                if changed && package.is_none() {
                    if timestats_ready {
                        disable_timestats();
                        timestats_ready = false;
                    }
                    drop(ebpf.take());
                    ebpf_failed_at = None;
                    write_idle(false, None, None);
                    continue;
                }
            }
            Ok(Request::Stop) | Err(mpsc::RecvTimeoutError::Disconnected) => {
                if timestats_ready {
                    disable_timestats();
                }
                drop(ebpf.take());
                write_idle(false, None, None);
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
            probe.drain(&tap);
        }

        let ebpf_reading = ebpf.as_ref().and_then(EbpfSource::reading);
        let ebpf_fresh = ebpf
            .as_ref()
            .map(EbpfSource::has_recent_frames)
            .unwrap_or(false);
        let ebpf_warming = ebpf.as_ref().map(EbpfSource::warming_up).unwrap_or(false);

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
            write_status(
                fps,
                frametime,
                game_active,
                package.as_deref(),
                session_start_ms,
            );
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
                write_idle(game_active, package.as_deref(), session_start_ms);
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
                slow = fps < 1.0;
                tap.push_window(fps, frametime);
                write_status(
                    fps,
                    frametime,
                    game_active,
                    package.as_deref(),
                    session_start_ms,
                );
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
                    slow = false;
                    if source == Source::Unavailable {
                        unavailable_at = Some(Instant::now());
                    }

                    write_idle(game_active, package.as_deref(), session_start_ms);
                }
            }
        }
    }
}
