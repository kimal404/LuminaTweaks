use crate::tweaks::devfreq::nearest_frequency;
use crate::tweaks::engine::{cpu_policy_dirs, hardware_limits, set_ppm_hard_max};
use crate::tweaks::fps::FrameTap;
use crate::tweaks::soc;
use crate::tweaks::state;
use crate::tweaks::thermal::ThermalPressure;
use serde_json;
use std::collections::HashMap;
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::time::Instant;

const PPM_MAX_PATH: &str = "/proc/ppm/policy/hard_userlimit_max_cpu_freq";
const MEMORY_PATH: &str = "/data/adb/.config/lumina/maestro_memory.json";
const STEP_FLOOR_PERCENT: u64 = 65;
const LITE_CAP_PERCENT: u64 = 78;
const BUDGET_TOLERANCE_PERCENT: f32 = 105.0;
const BOOST_P95_PERCENT: f32 = 110.0;
const TRIM_P95_PERCENT: f32 = 95.0;
const HEAVY_FRAME_PERCENT: f32 = 150.0;
const HEAVY_FRAME_STREAK: usize = 3;
const FAST_WINDOW_MS: f32 = 500.0;
const SLOW_WINDOW_MS: f32 = 5000.0;
const BASELINE_WINDOW_MS: f32 = 10_000.0;
const BASELINE_RAISE_HOLD_MS: u128 = 15_000;
const BASELINE_LOWER_HOLD_MS: u128 = 3_000;
const TRIM_HOLD_MS: u128 = 5_000;
const WARM_TRIM_HOLD_MS: u128 = 2_000;
const SAFETY_STEP_MS: u128 = 2_000;
const RECOVER_HOLD_MS: u128 = 5_000;
const STALE_AFTER_MS: u128 = 1_500;
const STATS_INTERVAL_MS: u128 = 60_000;
const MIN_WINDOW_FRAMES: usize = 12;
const MIN_BASELINE_FRAMES: usize = 60;
const FRAME_WINDOW: usize = 4096;
const BASELINE_LOWER_PERCENT: u64 = 20;
const BASELINE_DECREASE_PERCENT: f32 = 98.0;
const BASELINE_INCREASE_PERCENT: f32 = 102.0;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum FasState {
    Idle,
    Arm,
    Hold,
    Boost,
    Trim,
    Safety,
    Recover,
}

impl FasState {
    fn as_str(self) -> &'static str {
        match self {
            Self::Idle => "idle",
            Self::Arm => "arm",
            Self::Hold => "hold",
            Self::Boost => "boost",
            Self::Trim => "trim",
            Self::Safety => "safety",
            Self::Recover => "recover",
        }
    }
}

#[derive(Clone)]
struct Cluster {
    cluster: usize,
    base: String,
    hw_max: u64,
    floor: u64,
    cap: u64,
    freqs: Vec<u64>,
    performance: bool,
    last_written: Option<u64>,
}

impl Cluster {
    fn discover(cluster: usize, base: &str, performance: bool) -> Option<Self> {
        let (hw_min, hw_max, mut freqs) = hardware_limits(base)?;
        freqs.sort_unstable();
        freqs.dedup();
        let floor_target = hw_max * STEP_FLOOR_PERCENT / 100;
        let floor = nearest_frequency(&freqs, floor_target)
            .unwrap_or(hw_min)
            .clamp(hw_min, hw_max);
        let last_written = fs::read_to_string(format!("{}/scaling_max_freq", base))
            .ok()
            .and_then(|value| value.trim().parse::<u64>().ok());
        Some(Self {
            cluster,
            base: base.to_string(),
            hw_max,
            floor,
            cap: hw_max,
            freqs,
            performance,
            last_written,
        })
    }

    fn lite_cap(&self) -> u64 {
        let target = self.hw_max * LITE_CAP_PERCENT / 100;
        nearest_frequency(&self.freqs, target)
            .unwrap_or(target)
            .clamp(self.floor, self.hw_max)
    }

    fn step_down(&self, current: u64) -> Option<u64> {
        self.freqs
            .iter()
            .copied()
            .filter(|freq| *freq < current && *freq >= self.floor)
            .max()
    }
}

fn last_window(frames: &[f32], window_ms: f32) -> &[f32] {
    let mut total = 0.0f32;
    let mut start = frames.len();
    for index in (0..frames.len()).rev() {
        let value = frames[index];
        if !value.is_finite() || value <= 0.0 {
            continue;
        }
        total += value;
        start = index;
        if total >= window_ms {
            break;
        }
    }
    &frames[start..]
}

fn percentile(frames: &[f32], percent: u64) -> Option<f32> {
    let mut values: Vec<f32> = frames
        .iter()
        .copied()
        .filter(|value| value.is_finite() && *value > 0.0)
        .collect();
    if values.len() < MIN_WINDOW_FRAMES {
        return None;
    }
    values.sort_by(|left, right| {
        left.partial_cmp(right)
            .unwrap_or(std::cmp::Ordering::Equal)
    });
    let index = ((values.len() as f32 * percent as f32 / 100.0).ceil() as usize)
        .saturating_sub(1)
        .min(values.len() - 1);
    Some(values[index])
}

fn classify_state(
    safety: bool,
    recovering: bool,
    has_baseline: bool,
    boost: bool,
    trim: bool,
) -> FasState {
    if safety {
        if recovering {
            FasState::Recover
        } else {
            FasState::Safety
        }
    } else if !has_baseline {
        FasState::Arm
    } else if boost {
        FasState::Boost
    } else if trim {
        FasState::Trim
    } else {
        FasState::Hold
    }
}

fn load_memory() -> HashMap<String, f32> {
    fs::read_to_string(MEMORY_PATH)
        .ok()
        .and_then(|content| serde_json::from_str(&content).ok())
        .unwrap_or_default()
}

fn save_memory(memory: &HashMap<String, f32>) {
    let Ok(content) = serde_json::to_string(memory) else {
        return;
    };
    let Some(parent) = Path::new(MEMORY_PATH).parent() else {
        return;
    };
    let _ = fs::create_dir_all(parent);
    let temp = format!("{}.tmp", MEMORY_PATH);
    if fs::write(&temp, content).is_err() {
        let _ = fs::remove_file(&temp);
        return;
    }
    let _ = fs::set_permissions(&temp, fs::Permissions::from_mode(0o644));
    if fs::rename(&temp, MEMORY_PATH).is_err() {
        let _ = fs::remove_file(&temp);
    }
}

pub struct FasController {
    tap: FrameTap,
    clusters: Vec<Cluster>,
    seen_seq: u64,
    baseline_period: Option<f32>,
    baseline_raised_at: Option<Instant>,
    baseline_lowered_at: Option<Instant>,
    trim_since: Option<Instant>,
    heavy_streak: usize,
    last_frame_at: Option<Instant>,
    last_hz: Option<i32>,
    safety: bool,
    recover_since: Option<Instant>,
    last_safety_step: Option<Instant>,
    active: bool,
    sampled: bool,
    lite: Option<bool>,
    state: FasState,
    package: Option<String>,
    memory: HashMap<String, f32>,
    stats_at: Instant,
    boost_count: u64,
    trim_count: u64,
    safety_count: u64,
    ppm_failed: bool,
    soc_level: i8,
}

impl FasController {
    pub fn new(tap: FrameTap) -> Self {
        let clusters = cpu_policy_dirs()
            .iter()
            .enumerate()
            .filter_map(|(index, base)| Cluster::discover(index, base, index > 0))
            .collect();
        Self {
            tap,
            clusters,
            seen_seq: 0,
            baseline_period: None,
            baseline_raised_at: None,
            baseline_lowered_at: None,
            trim_since: None,
            heavy_streak: 0,
            last_frame_at: None,
            last_hz: None,
            safety: false,
            recover_since: None,
            last_safety_step: None,
            active: false,
            sampled: false,
            lite: None,
            state: FasState::Idle,
            package: None,
            memory: load_memory(),
            stats_at: Instant::now(),
            boost_count: 0,
            trim_count: 0,
            safety_count: 0,
            ppm_failed: false,
            soc_level: 0,
        }
    }

    fn set_state(&mut self, next: FasState) {
        if self.state == next {
            return;
        }
        if next == FasState::Safety {
            self.safety_count = self.safety_count.saturating_add(1);
        }
        log!(
            "[FAS] state {} -> {} | pkg {}",
            self.state.as_str(),
            next.as_str(),
            self.package.as_deref().unwrap_or("-")
        );
        self.state = next;
    }

    fn apply_caps(&mut self, lite: bool) {
        for index in 0..self.clusters.len() {
            let cap = if lite {
                self.clusters[index].lite_cap()
            } else {
                self.clusters[index].hw_max
            };
            self.clusters[index].cap = cap;
        }
    }

    fn write(&mut self, index: usize, freq: u64) -> bool {
        let Some(cluster) = self.clusters.get(index) else {
            return false;
        };
        if cluster.last_written == Some(freq) {
            return true;
        }
        let base = cluster.base.clone();
        let cluster_id = cluster.cluster;
        let ok = state::write_tracked_quiet(
            &format!("{}/scaling_max_freq", base),
            &freq.to_string(),
            false,
        );
        if !ok {
            return false;
        }
        if Path::new(PPM_MAX_PATH).exists() {
            let ppm_ok = set_ppm_hard_max(cluster_id, freq);
            if !ppm_ok && !self.ppm_failed {
                self.ppm_failed = true;
                log!("[FAS] PPM hard limit unavailable; cpufreq ceiling only");
            } else if ppm_ok {
                self.ppm_failed = false;
            }
        }
        if let Some(cluster) = self.clusters.get_mut(index) {
            cluster.last_written = Some(freq);
        }
        true
    }

    fn restore_caps(&mut self) {
        for index in 0..self.clusters.len() {
            let cap = self.clusters[index].cap;
            self.write(index, cap);
        }
    }

    fn dynamic_boost(&mut self) {
        if self.soc_level >= 1 {
            return;
        }
        soc::frame_boost();
        self.soc_level = 1;
    }

    fn dynamic_relax(&mut self) {
        if self.soc_level <= -1 {
            return;
        }
        soc::frame_relax();
        self.soc_level = -1;
    }

    fn boost(&mut self) {
        for index in 0..self.clusters.len() {
            if !self.clusters[index].performance {
                continue;
            }
            let cap = self.clusters[index].cap;
            self.write(index, cap);
        }
        self.dynamic_boost();
        self.boost_count = self.boost_count.saturating_add(1);
    }

    fn step_down(&mut self) {
        for index in 0..self.clusters.len() {
            if !self.clusters[index].performance {
                continue;
            }
            let current = self.clusters[index]
                .last_written
                .unwrap_or(self.clusters[index].cap);
            if let Some(target) = self.clusters[index].step_down(current) {
                self.write(index, target);
            }
        }
    }

    fn remember_baseline(&mut self) {
        let Some(package) = self.package.clone() else {
            return;
        };
        let Some(baseline) = self.baseline_period else {
            return;
        };
        self.memory.insert(package, baseline);
        save_memory(&self.memory);
    }

    fn update_baseline(&mut self, frames: &[f32], now: Instant) {
        let window = last_window(frames, BASELINE_WINDOW_MS);
        if window.len() < MIN_BASELINE_FRAMES {
            return;
        }
        let Some(candidate) = percentile(window, BASELINE_LOWER_PERCENT) else {
            return;
        };
        match self.baseline_period {
            None => {
                self.baseline_period = Some(candidate);
                self.baseline_raised_at = None;
                self.baseline_lowered_at = None;
            }
            Some(current) if candidate <= current * BASELINE_DECREASE_PERCENT / 100.0 => {
                match self.baseline_lowered_at {
                    Some(at)
                        if now.saturating_duration_since(at).as_millis()
                            >= BASELINE_LOWER_HOLD_MS =>
                    {
                        self.baseline_period = Some(candidate);
                        self.baseline_raised_at = None;
                        self.baseline_lowered_at = None;
                    }
                    Some(_) => {}
                    None => self.baseline_lowered_at = Some(now),
                }
            }
            Some(current) if candidate >= current * BASELINE_INCREASE_PERCENT / 100.0 => {
                match self.baseline_raised_at {
                    Some(at)
                        if now.saturating_duration_since(at).as_millis()
                            >= BASELINE_RAISE_HOLD_MS =>
                    {
                        self.baseline_period = Some(candidate);
                        self.baseline_raised_at = None;
                        self.baseline_lowered_at = None;
                    }
                    Some(_) => {}
                    None => self.baseline_raised_at = Some(now),
                }
            }
            Some(_) => {
                self.baseline_raised_at = None;
                self.baseline_lowered_at = None;
            }
        }
    }

    fn target_period(&self, active_hz: Option<i32>) -> Option<f32> {
        let baseline = self.baseline_period?;
        let display_period = active_hz
            .filter(|hz| *hz >= 30)
            .map(|hz| 1000.0 / hz as f32);
        Some(match display_period {
            Some(period) => baseline.max(period),
            None => baseline,
        })
    }

    fn update_thermal(&mut self, pressure: ThermalPressure, now: Instant) {
        match pressure {
            ThermalPressure::Critical => {
                self.safety = true;
                self.recover_since = None;
            }
            ThermalPressure::Warm => {
                self.recover_since = None;
            }
            ThermalPressure::Normal => {
                if self.safety {
                    match self.recover_since {
                        Some(at)
                            if now.saturating_duration_since(at).as_millis()
                                >= RECOVER_HOLD_MS =>
                        {
                            self.safety = false;
                            self.recover_since = None;
                            self.last_safety_step = None;
                            self.sampled = false;
                            self.restore_caps();
                        }
                        Some(_) => {}
                        None => self.recover_since = Some(now),
                    }
                }
            }
        }
    }

    fn log_stats(&mut self, frames: &[f32], thermal: ThermalPressure, now: Instant) {
        if now.saturating_duration_since(self.stats_at).as_millis() < STATS_INTERVAL_MS {
            return;
        }
        self.stats_at = now;
        let window = last_window(frames, SLOW_WINDOW_MS);
        let p50 = percentile(window, 50).unwrap_or(0.0);
        let p95 = percentile(window, 95).unwrap_or(0.0);
        let p99 = percentile(window, 99).unwrap_or(0.0);
        let low_fps = if p99 > 0.0 { 1000.0 / p99 } else { 0.0 };
        log!(
            "[FAS] p50={:.1}ms p95={:.1}ms p99={:.1}ms 1%low={:.1}fps thermal={:?} boost={} trim={} safety={}",
            p50,
            p95,
            p99,
            low_fps,
            thermal,
            self.boost_count,
            self.trim_count,
            self.safety_count
        );
    }

    fn clear_runtime(&mut self) {
        self.trim_since = None;
        self.heavy_streak = 0;
        self.last_frame_at = None;
        self.safety = false;
        self.recover_since = None;
        self.last_safety_step = None;
        self.sampled = false;
        self.seen_seq = self.tap.measured_snapshot(1).seq;
        self.state = FasState::Idle;
    }

    pub fn reset(&mut self, package: Option<&str>) {
        self.remember_baseline();
        if self.active {
            self.restore_caps();
        }
        let next_package = package.map(str::to_string);
        self.baseline_period = next_package
            .as_ref()
            .and_then(|pkg| self.memory.get(pkg).copied());
        self.baseline_raised_at = None;
        self.baseline_lowered_at = None;
        self.package = next_package;
        self.active = false;
        self.soc_level = 0;
        self.clear_runtime();
    }

    pub fn tick(
        &mut self,
        maestro: bool,
        enabled: bool,
        active_hz: Option<i32>,
        lite: bool,
        thermal: ThermalPressure,
    ) {
        if !maestro {
            self.remember_baseline();
            self.baseline_period = None;
            self.baseline_raised_at = None;
            self.baseline_lowered_at = None;
            self.last_hz = None;
            self.package = None;
            self.soc_level = 0;
            self.active = false;
            self.clear_runtime();
            return;
        }

        if self.lite != Some(lite) {
            self.lite = Some(lite);
            self.apply_caps(lite);
            if self.active {
                self.restore_caps();
            }
        }

        if let Some(hz) = active_hz.filter(|hz| *hz >= 30) {
            if self.last_hz != Some(hz) {
                self.baseline_period = None;
                self.baseline_raised_at = None;
                self.baseline_lowered_at = None;
                self.trim_since = None;
                self.last_hz = Some(hz);
            }
        }

        if !enabled {
            if self.active {
                self.restore_caps();
            }
            self.active = false;
            self.clear_runtime();
            return;
        }

        self.active = true;
        let now = Instant::now();
        self.update_thermal(thermal, now);

        let sample = self.tap.measured_snapshot(FRAME_WINDOW);
        let mut new_count = sample.seq.saturating_sub(self.seen_seq) as usize;
        self.seen_seq = sample.seq;

        if new_count > 0 {
            self.last_frame_at = Some(now);
        }

        if self.safety {
            if self.recover_since.is_some() {
                self.set_state(FasState::Recover);
                return;
            }
            self.heavy_streak = 0;
            self.trim_since = None;
            self.dynamic_relax();
            let step_due = self
                .last_safety_step
                .map(|at| now.saturating_duration_since(at).as_millis() >= SAFETY_STEP_MS)
                .unwrap_or(true);
            if step_due {
                self.step_down();
                self.last_safety_step = Some(now);
            }
            self.set_state(FasState::Safety);
            return;
        }
        self.last_safety_step = None;

        if new_count > 0 {
            self.update_baseline(&sample.frames, now);
        } else if self
            .last_frame_at
            .map(|at| now.saturating_duration_since(at).as_millis() >= STALE_AFTER_MS)
            .unwrap_or(true)
        {
            self.restore_caps();
            self.set_state(FasState::Arm);
            self.clear_runtime();
            return;
        } else {
            return;
        }

        if !self.sampled {
            self.restore_caps();
            self.sampled = true;
        }

        let Some(target_ms) = self.target_period(active_hz) else {
            self.set_state(FasState::Arm);
            self.restore_caps();
            return;
        };
        if new_count > sample.frames.len() {
            new_count = sample.frames.len();
        }
        if new_count == 0 {
            return;
        }

        self.log_stats(&sample.frames, thermal, now);

        let budget_ms = target_ms * BUDGET_TOLERANCE_PERCENT / 100.0;
        let fast_window = last_window(&sample.frames, FAST_WINDOW_MS);
        let slow_window = last_window(&sample.frames, SLOW_WINDOW_MS);
        let p95_fast = percentile(fast_window, 95);
        let p95_slow = percentile(slow_window, 95);
        let start = sample.frames.len().saturating_sub(new_count);

        for frametime in &sample.frames[start..] {
            if *frametime >= budget_ms * HEAVY_FRAME_PERCENT / 100.0 {
                self.heavy_streak = self.heavy_streak.saturating_add(1);
            } else {
                self.heavy_streak = 0;
            }
        }

        let boost = self.heavy_streak >= HEAVY_FRAME_STREAK
            || p95_fast
                .map(|value| value >= budget_ms * BOOST_P95_PERCENT / 100.0)
                .unwrap_or(false);

        if boost {
            self.heavy_streak = 0;
            self.trim_since = None;
            self.boost();
            self.set_state(FasState::Boost);
            return;
        }

        let mut trimmed = false;
        if let Some(value) = p95_slow {
            if value <= budget_ms * TRIM_P95_PERCENT / 100.0 {
                let since = *self.trim_since.get_or_insert(now);
                let hold = if thermal == ThermalPressure::Warm {
                    WARM_TRIM_HOLD_MS
                } else {
                    TRIM_HOLD_MS
                };
                if now.saturating_duration_since(since).as_millis() >= hold {
                    self.trim_since = None;
                    self.step_down();
                    self.dynamic_relax();
                    self.trim_count = self.trim_count.saturating_add(1);
                    trimmed = true;
                }
            } else {
                self.trim_since = None;
            }
        } else {
            self.trim_since = None;
        }

        let next = classify_state(
            self.safety,
            self.recover_since.is_some(),
            self.baseline_period.is_some(),
            boost,
            trimmed,
        );
        self.set_state(next);
    }
}

#[cfg(test)]
mod tests {
    use super::{classify_state, last_window, percentile, Cluster, FasState};

    fn cluster(freqs: Vec<u64>, hw_max: u64, floor: u64) -> Cluster {
        Cluster {
            cluster: 1,
            base: "/tmp".to_string(),
            hw_max,
            floor,
            cap: hw_max,
            freqs,
            performance: true,
            last_written: Some(hw_max),
        }
    }

    #[test]
    fn steps_exactly_one_frequency_down() {
        let item = cluster(
            vec![300000, 500000, 800000, 1200000, 1800000, 2400000],
            2400000,
            1200000,
        );
        assert_eq!(item.step_down(2400000), Some(1800000));
        assert_eq!(item.step_down(1800000), Some(1200000));
        assert_eq!(item.step_down(1200000), None);
    }

    #[test]
    fn respects_step_floor() {
        let item = cluster(vec![300000, 800000, 1200000, 2000000], 2000000, 1300000);
        assert_eq!(item.step_down(2000000), None);
    }

    #[test]
    fn computes_percentile_from_tail() {
        let frames: Vec<f32> = (1..=100).map(|value| value as f32).collect();
        assert_eq!(percentile(&frames, 95), Some(95.0));
        assert_eq!(percentile(&frames, 20), Some(20.0));
    }

    #[test]
    fn accumulates_time_window_from_newest_frames() {
        let frames = [100.0, 10.0, 10.0, 10.0, 10.0];
        assert_eq!(last_window(&frames, 30.0), &[10.0, 10.0, 10.0]);
    }

    #[test]
    fn prioritizes_safety_and_recovery_states() {
        assert_eq!(
            classify_state(true, false, true, true, true),
            FasState::Safety
        );
        assert_eq!(
            classify_state(true, true, true, true, true),
            FasState::Recover
        );
        assert_eq!(classify_state(false, false, false, true, true), FasState::Arm);
        assert_eq!(classify_state(false, false, true, true, false), FasState::Boost);
        assert_eq!(classify_state(false, false, true, false, true), FasState::Trim);
        assert_eq!(classify_state(false, false, true, false, false), FasState::Hold);
    }
}