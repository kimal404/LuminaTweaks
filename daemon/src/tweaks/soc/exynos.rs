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

use super::SocProfile;
use std::path::Path;

const GPU_DIRS: &[&str] = &["/sys/kernel/gpu", "/sys/devices/platform/gpu"];
const FREQ_TABLES: &[&str] = &["gpu_available_frequencies", "gpu_freq_table"];
const BUS_NEEDLES: &[&str] = &[
    "devfreq_mif",
    "devfreq_int",
    "devfreq_disp",
    "devfreq_memlat",
];

fn gpu_dir() -> Option<&'static str> {
    GPU_DIRS.iter().copied().find(|dir| Path::new(dir).is_dir())
}

fn freq_table(dir: &str) -> Option<String> {
    FREQ_TABLES.iter().find_map(|name| {
        let path = format!("{}/{}", dir, name);
        Path::new(&path).exists().then_some(path)
    })
}

fn write_clock(dir: &str, primary: &str, fallback: &str, value: u64) -> usize {
    let primary_path = format!("{}/{}", dir, primary);
    if Path::new(&primary_path).exists() {
        return usize::from(super::apply_node(&primary_path, &value.to_string()));
    }
    let fallback_path = format!("{}/{}", dir, fallback);
    usize::from(super::apply_node(&fallback_path, &value.to_string()))
}

fn apply_gpu(profile: SocProfile) -> usize {
    let Some(dir) = gpu_dir() else {
        return 0;
    };
    let Some(table) = freq_table(dir) else {
        return 0;
    };
    let values = super::read_frequencies(&table);
    let (Some(min), Some(max)) = (
        crate::tweaks::devfreq::min_frequency(&values),
        crate::tweaks::devfreq::max_frequency(&values),
    ) else {
        return 0;
    };
    let (max_target, min_target) = match profile {
        SocProfile::Lite => (max, min),
        SocProfile::Balance => (max, min),
        SocProfile::Eco => (min, min),
        SocProfile::Performance => (max, max),
    };

    let mut applied = write_clock(dir, "gpu_max_clock", "scaling_max_freq", max_target);
    applied += write_clock(dir, "gpu_min_clock", "scaling_min_freq", min_target);
    applied
}

fn mif(profile: SocProfile) -> usize {
    if crate::tweaks::mitigation::has("DISABLE_DDR_TWEAK") {
        return 0;
    }
    if !matches!(
        profile,
        SocProfile::Lite | SocProfile::Balance | SocProfile::Performance
    ) {
        return 0;
    }
    super::devfreq_group_profile(BUS_NEEDLES, profile)
}

pub(crate) fn frame_boost() -> usize {
    mif(SocProfile::Performance)
}

pub(crate) fn frame_relax() -> usize {
    mif(SocProfile::Balance)
}

pub fn apply(profile: SocProfile) -> usize {
    let mut applied = apply_gpu(profile);
    applied += super::mali::apply_power_policy(profile);
    applied += mif(profile);
    applied
}

#[cfg(test)]
mod tests {
    use super::{BUS_NEEDLES, FREQ_TABLES, GPU_DIRS};

    #[test]
    fn has_exynos_gpu_nodes() {
        assert!(GPU_DIRS.contains(&"/sys/kernel/gpu"));
        assert!(FREQ_TABLES.contains(&"gpu_available_frequencies"));
        assert!(FREQ_TABLES.contains(&"gpu_freq_table"));
        assert!(BUS_NEEDLES.contains(&"devfreq_mif"));
        assert!(BUS_NEEDLES.contains(&"devfreq_int"));
    }
}
