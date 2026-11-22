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

const GPU_DIRS: &[&str] = &[
    "/sys/class/devfreq/gpufreq",
    "/sys/devices/platform/gpufreq",
    "/sys/kernel/gpu",
];
const GPU_NEEDLES: &[&str] = &["gpu", "mali"];

fn gpu_devfreq() -> Option<String> {
    for dir in GPU_DIRS {
        if Path::new(dir).join("available_frequencies").exists() {
            return Some((*dir).to_string());
        }
    }
    super::devfreq_matching(GPU_NEEDLES).into_iter().next()
}

pub(crate) fn frame_boost() -> usize {
    gpu_devfreq()
        .map(|base| super::devfreq_max_perf(&base))
        .unwrap_or(0)
}

pub(crate) fn frame_relax() -> usize {
    gpu_devfreq()
        .map(|base| super::devfreq_unlock(&base))
        .unwrap_or(0)
}

pub fn apply(profile: SocProfile) -> usize {
    let Some(base) = gpu_devfreq() else {
        return 0;
    };
    match profile {
        SocProfile::Lite => super::devfreq_mid_perf(&base),
        SocProfile::Balance => super::devfreq_unlock(&base),
        SocProfile::Eco => super::devfreq_min_perf(&base),
        SocProfile::Maestro => super::devfreq_unlock(&base),
        SocProfile::Performance => super::devfreq_max_perf(&base),
    }
}

#[cfg(test)]
mod tests {
    use super::GPU_NEEDLES;

    #[test]
    fn covers_kirin_gpu_nodes() {
        assert!(GPU_NEEDLES.contains(&"gpu"));
    }
}
