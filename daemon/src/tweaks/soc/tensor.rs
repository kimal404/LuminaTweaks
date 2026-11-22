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

const MIF_NEEDLES: &[&str] = &["devfreq_mif"];

fn gpu_dir() -> Option<String> {
    let candidates = super::platform_matching("mali");
    for base in candidates {
        if Path::new(&base).join("available_frequencies").exists() {
            return Some(base);
        }
    }
    super::mali_dir().filter(|base| Path::new(base).join("available_frequencies").exists())
}

fn set_freq(base: &str, node: &str, value: u64) -> usize {
    let path = format!("{}/{}", base, node);
    usize::from(super::apply_node(&path, &value.to_string()))
}

fn apply_gpu(profile: SocProfile) -> usize {
    let Some(base) = gpu_dir() else {
        return 0;
    };
    let values = super::read_frequencies(&format!("{}/available_frequencies", base));
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
        SocProfile::Maestro => (min, max),
        SocProfile::Performance => (max, max),
    };

    let mut applied = set_freq(&base, "scaling_max_freq", cap);
    applied += set_freq(&base, "scaling_min_freq", floor);
    let policy = match profile {
        SocProfile::Lite => SocProfile::Lite,
        SocProfile::Balance => SocProfile::Balance,
        SocProfile::Eco => SocProfile::Eco,
        SocProfile::Maestro => SocProfile::Maestro,
        SocProfile::Performance => SocProfile::Performance,
    };
    applied += super::mali::apply_power_policy(policy);
    applied
}

fn mif(profile: SocProfile) -> usize {
    if crate::tweaks::mitigation::has("DISABLE_DDR_TWEAK") {
        return 0;
    }
    if !matches!(
        profile,
        SocProfile::Lite | SocProfile::Balance | SocProfile::Maestro | SocProfile::Performance
    ) {
        return 0;
    }
    let mut applied = 0;
    for base in super::devfreq_matching(MIF_NEEDLES) {
        applied += match profile {
            SocProfile::Lite => super::devfreq_mid_perf(&base),
            SocProfile::Maestro | SocProfile::Performance => super::devfreq_max_perf(&base),
            _ => super::devfreq_unlock(&base),
        };
    }
    applied
}

pub(crate) fn frame_boost() -> usize {
    mif(SocProfile::Maestro)
}

pub(crate) fn frame_relax() -> usize {
    mif(SocProfile::Balance)
}

pub fn apply(profile: SocProfile) -> usize {
    apply_gpu(profile) + mif(profile)
}

#[cfg(test)]
mod tests {
    use super::MIF_NEEDLES;

    #[test]
    fn covers_tensor_mif_nodes() {
        assert!(MIF_NEEDLES.contains(&"devfreq_mif"));
    }
}
