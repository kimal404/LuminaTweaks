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

const GPU_NEEDLES: &[&str] = &["gpu", "mali"];
const BUS_NEEDLES: &[&str] = &["ddr", "emc", "memlat", "mif"];

fn gpu(profile: SocProfile) -> usize {
    super::find_devfreq(GPU_NEEDLES)
        .map(|base| super::devfreq_profile(&base, profile))
        .unwrap_or(0)
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
    gpu(profile) + bus(profile) + super::mali::apply_power_policy(profile)
}

#[cfg(test)]
mod tests {
    use super::{BUS_NEEDLES, GPU_NEEDLES};

    #[test]
    fn covers_generic_devfreq_nodes() {
        assert!(GPU_NEEDLES.contains(&"gpu"));
        assert!(BUS_NEEDLES.contains(&"ddr"));
        assert!(BUS_NEEDLES.contains(&"emc"));
    }
}
