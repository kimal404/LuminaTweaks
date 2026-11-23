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

const GPU_DIRS: &[&str] = &["/sys/kernel/tegra_gpu", "/sys/devices/platform/tegra_gpu"];
const BUS_NEEDLES: &[&str] = &["emc", "ddr", "memlat"];

fn gpu_dir() -> Option<&'static str> {
    GPU_DIRS
        .iter()
        .copied()
        .find(|dir| Path::new(dir).join("available_frequencies").exists())
}

fn set_rate(dir: &str, node: &str, value: u64) -> usize {
    usize::from(super::apply_node(
        &format!("{}/{}", dir, node),
        &value.to_string(),
    ))
}

fn gpu(profile: SocProfile) -> usize {
    let Some(dir) = gpu_dir() else {
        return 0;
    };
    let values = super::read_frequencies(&format!("{}/available_frequencies", dir));
    let (Some(min), Some(max)) = (
        crate::tweaks::devfreq::min_frequency(&values),
        crate::tweaks::devfreq::max_frequency(&values),
    ) else {
        return 0;
    };
    let (floor, cap) = match profile {
        SocProfile::Lite => (min, max),
        SocProfile::Balance => (min, max),
        SocProfile::Eco => (min, min),
        SocProfile::Performance => (max, max),
    };

    let mut applied = set_rate(dir, "gpu_cap_rate", cap);
    applied += set_rate(dir, "gpu_floor_rate", floor);
    applied
}

fn bus(profile: SocProfile) -> usize {
    if profile == SocProfile::Eco || crate::tweaks::mitigation::has("DISABLE_DDR_TWEAK") {
        return 0;
    }
    super::devfreq_group_profile(BUS_NEEDLES, profile)
}

pub(crate) fn frame_boost() -> usize {
    gpu(SocProfile::Performance) + bus(SocProfile::Performance)
}

pub(crate) fn frame_relax() -> usize {
    gpu(SocProfile::Balance) + bus(SocProfile::Balance)
}

pub fn apply(profile: SocProfile) -> usize {
    gpu(profile) + bus(profile)
}

#[cfg(test)]
mod tests {
    use super::{BUS_NEEDLES, GPU_DIRS};

    #[test]
    fn has_tegra_gpu_node() {
        assert!(GPU_DIRS.contains(&"/sys/kernel/tegra_gpu"));
        assert!(BUS_NEEDLES.contains(&"emc"));
    }
}
