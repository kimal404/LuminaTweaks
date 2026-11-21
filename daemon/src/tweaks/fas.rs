use crate::tweaks::devfreq::nearest_frequency;
use crate::tweaks::engine::{cpu_policy_dirs, hardware_limits, set_ppm_hard_max};
use crate::tweaks::fps::FrameTap;
use crate::tweaks::state;
use std::fs;
use std::path::Path;

const PPM_MAX_PATH: &str = "/proc/ppm/policy/hard_userlimit_max_cpu_freq";
const FAST_BOOST_PERCENT: u64 = 88;
const LIGHT_FRAME_PERCENT: u64 = 76;
const MIN_LIGHT_FRAMES: usize = 20;
const STEP_FLOOR_PERCENT: u64 = 65;
const LITE_CAP_PERCENT: u64 = 78;
const RECENT_FRAMES: usize = 96;

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

pub struct FasController {
    tap: FrameTap,
    clusters: Vec<Cluster>,
    seen_seq: u64,
    light_frames: usize,
    active: bool,
    sampled: bool,
    lite: Option<bool>,
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
            light_frames: 0,
            active: false,
            sampled: false,
            lite: None,
        }
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
            set_ppm_hard_max(cluster_id, freq);
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

    fn boost(&mut self) {
        for index in 0..self.clusters.len() {
            if !self.clusters[index].performance {
                continue;
            }
            let cap = self.clusters[index].cap;
            self.write(index, cap);
        }
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

    pub fn reset(&mut self) {
        if self.active {
            self.restore_caps();
        }
        self.sampled = false;
        self.light_frames = 0;
        self.seen_seq = self.tap.measured_snapshot(1).seq;
    }

    pub fn tick(&mut self, maestro: bool, enabled: bool, active_hz: Option<i32>, lite: bool) {
        if !maestro {
            self.active = false;
            self.sampled = false;
            self.light_frames = 0;
            self.seen_seq = self.tap.measured_snapshot(1).seq;
            return;
        }

        if self.lite != Some(lite) {
            self.lite = Some(lite);
            self.apply_caps(lite);
            if self.active {
                self.restore_caps();
            }
        }

        if !enabled {
            if self.active {
                self.restore_caps();
            }
            self.active = false;
            self.sampled = false;
            self.light_frames = 0;
            self.seen_seq = self.tap.measured_snapshot(1).seq;
            return;
        }

        self.active = true;
        let Some(hz) = active_hz.filter(|hz| *hz >= 30) else {
            self.restore_caps();
            self.sampled = false;
            self.light_frames = 0;
            self.seen_seq = self.tap.measured_snapshot(1).seq;
            return;
        };

        let sample = self.tap.measured_snapshot(RECENT_FRAMES);
        let mut new_count = sample.seq.saturating_sub(self.seen_seq) as usize;
        self.seen_seq = sample.seq;

        if !self.sampled {
            self.restore_caps();
            self.sampled = true;
        }
        if new_count == 0 {
            return;
        }
        if new_count > sample.frames.len() {
            new_count = sample.frames.len();
        }
        if new_count == 0 {
            return;
        }

        let target_ms = 1000.0 / hz as f32;
        let boost_ms = target_ms * FAST_BOOST_PERCENT as f32 / 100.0;
        let light_ms = target_ms * LIGHT_FRAME_PERCENT as f32 / 100.0;
        let start = sample.frames.len().saturating_sub(new_count);
        let mut boost = false;

        for frametime in &sample.frames[start..] {
            if *frametime >= boost_ms {
                boost = true;
                break;
            }
            if *frametime <= light_ms {
                self.light_frames = self.light_frames.saturating_add(1);
            } else {
                self.light_frames = 0;
            }
        }

        if boost {
            self.light_frames = 0;
            self.boost();
        } else if self.light_frames >= MIN_LIGHT_FRAMES {
            self.light_frames = 0;
            self.step_down();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::Cluster;

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
}
