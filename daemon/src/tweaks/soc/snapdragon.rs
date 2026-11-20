// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use super::SocProfile;
use crate::tweaks::state;
use std::fs;
use std::path::Path;

const KGSL_CANDIDATES: &[&str] = &[
    "/sys/class/kgsl/kgsl-3d0",
    "/sys/class/kgsl/kgsl-2d0",
    "/sys/class/graphics/fb0",
];
const ADRENO_IDLER_PATHS: &[&str] = &[
    "/sys/module/adreno_idler/parameters/adreno_idler_active",
    "/sys/module/adreno_idler/parameters/adreno_idler_idleworkload",
];
const NEEDLES: &[&str] = &[
    "gpu",
    "mali",
    "kgsl",
    "ddr",
    "bus",
    "memlat",
    "qcom",
    "cpubw",
    "latfloor",
    "ddr-lat",
    "llcc",
];
const BUS_COMPONENTS: &[&str] = &["DDR", "LLCC", "L3"];

fn kgsl_base() -> Option<&'static str> {
    KGSL_CANDIDATES.iter().copied().find(|base| {
        Path::new(base).is_dir() && Path::new(base).join("devfreq").exists()
    })
}

fn kgsl_devfreq() -> Option<String> {
    kgsl_base().map(|base| format!("{}/devfreq", base))
}

fn force_values(profile: SocProfile) -> (&'static str, &'static str) {
    match profile {
        SocProfile::Performance => ("1", "0"),
        SocProfile::Lite | SocProfile::Eco => ("0", "1"),
    }
}

fn hwcg_values(profile: SocProfile) -> &'static str {
    match profile {
        SocProfile::Performance => "0",
        SocProfile::Lite | SocProfile::Eco => "1",
    }
}

fn write_kgsl(name: &str, value: &str) -> bool {
    let Some(base) = kgsl_base() else {
        return false;
    };
    let path = format!("{}/{}", base, name);
    if !Path::new(&path).exists() {
        return false;
    }
    state::write_tracked(&path, value, false)
}

fn write_devfreq(name: &str, value: &str, lock: bool) -> bool {
    let Some(base) = kgsl_devfreq() else {
        return false;
    };
    let path = format!("{}/{}", base, name);
    if !Path::new(&path).exists() {
        return false;
    }
    state::write_tracked(&path, value, lock)
}

fn apply_kgsl_devfreq(profile: SocProfile) -> usize {
    let Some(base) = kgsl_devfreq() else {
        return 0;
    };
    let governor = match profile {
        SocProfile::Performance => "performance",
        SocProfile::Lite => "simple_ondemand",
        SocProfile::Eco => "powersave",
    };
    let lock = profile == SocProfile::Performance;

    if Path::new(&format!("{}/governor", base)).exists() {
        write_devfreq("governor", governor, false);
    }

    let values: Vec<u64> = fs::read_to_string(format!("{}/available_frequencies", base))
        .ok()
        .map(|content| {
            content
                .split_whitespace()
                .filter_map(|value| value.parse().ok())
                .collect()
        })
        .unwrap_or_default();

    let (Some(min), Some(max)) = (values.iter().copied().min(), values.iter().copied().max())
    else {
        return 0;
    };

    let (target_min, target_max) = match profile {
        SocProfile::Performance => (max, max),
        SocProfile::Lite => (
            crate::tweaks::devfreq::mid_frequency(&values).unwrap_or(min),
            crate::tweaks::devfreq::mid_frequency(&values).unwrap_or(max),
        ),
        SocProfile::Eco => (min, min),
    };

    write_devfreq("min_freq", &target_min.to_string(), lock);
    write_devfreq("max_freq", &target_max.to_string(), lock);
    1
}

fn apply_kgsl_flags(profile: SocProfile) -> usize {
    let (force, idler) = force_values(profile);
    let mut applied = 0;
    for node in ["force_clk_on", "force_bus_on", "force_rail_on", "force_no_nap"] {
        if write_kgsl(node, force) {
            applied += 1;
        }
    }

    let bus_split = match profile {
        SocProfile::Performance => "0",
        SocProfile::Lite | SocProfile::Eco => "1",
    };
    if write_kgsl("bus_split", bus_split) {
        applied += 1;
    }

    for path in ADRENO_IDLER_PATHS {
        if Path::new(path).exists() {
            state::write_tracked(path, idler, false);
            applied += 1;
            break;
        }
    }

    let hwcg = hwcg_values(profile);
    for node in ["hwcg", "hwcg_skip_ib"] {
        if write_devfreq(node, hwcg, false) {
            applied += 1;
        }
    }

    let throttling = match profile {
        SocProfile::Performance => "0",
        SocProfile::Lite | SocProfile::Eco => "1",
    };
    if write_kgsl("throttling", throttling) {
        applied += 1;
    }
    applied
}

fn bus_dcvs_targets(profile: SocProfile, min: u64, mid: u64, max: u64) -> (u64, u64) {
    match profile {
        SocProfile::Performance => (max, max),
        SocProfile::Lite => (mid, max),
        SocProfile::Eco => (min, min),
    }
}

fn apply_bus_dcvs(profile: SocProfile) -> usize {
    let mut applied = 0;
    for component in BUS_COMPONENTS {
        let base = format!("/sys/devices/system/cpu/bus_dcvs/{}", component);
        let Some((min, mid, max)) = crate::tweaks::devfreq::frequency_range(&base) else {
            continue;
        };
        let (target_min, target_max) = bus_dcvs_targets(profile, min, mid, max);
        for (node, target) in [("hw_min_freq", target_min), ("hw_max_freq", target_max)] {
            let path = format!("{}/{}", base, node);
            if Path::new(&path).exists()
                && state::write_tracked(&path, &target.to_string(), false)
            {
                applied += 1;
            }
        }
    }
    applied
}

fn apply_kgsl_pwrlevels(profile: SocProfile) -> usize {
    let Some(base) = kgsl_base() else {
        return 0;
    };
    let Some(count) = fs::read_to_string(format!("{}/num_pwrlevels", base))
        .ok()
        .and_then(|value| value.trim().parse::<u64>().ok())
    else {
        return 0;
    };
    let (target_min, target_max) = match profile {
        SocProfile::Performance => (0, 0),
        SocProfile::Lite | SocProfile::Eco => (count, 0),
    };
    let mut applied = 0;
    for (node, target) in [
        ("min_pwrlevel", target_min),
        ("max_pwrlevel", target_max),
    ] {
        let path = format!("{}/{}", base, node);
        if Path::new(&path).exists()
            && state::write_tracked(&path, &target.to_string(), false)
        {
            applied += 1;
        }
    }
    applied
}

fn guard_node(path: &str, target: &str) -> usize {
    if !Path::new(path).exists() {
        return 0;
    }
    let current = fs::read_to_string(path)
        .ok()
        .map(|value| value.trim().to_string());
    if current.as_deref() == Some(target) {
        return 0;
    }
    usize::from(state::write_tracked(path, target, false))
}

pub fn guard(profile: SocProfile) -> usize {
    let mut applied = super::guard_devfreq(profile, NEEDLES);
    if let Some(base) = kgsl_devfreq() {
        let values = fs::read_to_string(format!("{}/available_frequencies", base))
            .ok()
            .map(|content| {
                content
                    .split_whitespace()
                    .filter_map(|value| value.parse::<u64>().ok())
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default();
        if let (Some(min), Some(max)) =
            (values.iter().copied().min(), values.iter().copied().max())
        {
            let mid = crate::tweaks::devfreq::mid_frequency(&values).unwrap_or(max);
            let (target_min, target_max) = match profile {
                SocProfile::Performance => (max, max),
                SocProfile::Lite => (min, mid),
                SocProfile::Eco => (min, min),
            };
            applied += guard_node(&format!("{}/min_freq", base), &target_min.to_string());
            applied += guard_node(&format!("{}/max_freq", base), &target_max.to_string());
        }
    }

    if let Some(base) = kgsl_base() {
        let count = fs::read_to_string(format!("{}/num_pwrlevels", base))
            .ok()
            .and_then(|value| value.trim().parse::<u64>().ok());
        if let Some(count) = count {
            let (target_min, target_max) = match profile {
                SocProfile::Performance => (0, 0),
                SocProfile::Lite | SocProfile::Eco => (count, 0),
            };
            applied += guard_node(
                &format!("{}/min_pwrlevel", base),
                &target_min.to_string(),
            );
            applied += guard_node(
                &format!("{}/max_pwrlevel", base),
                &target_max.to_string(),
            );
        }
    }

    for component in BUS_COMPONENTS {
        let base = format!("/sys/devices/system/cpu/bus_dcvs/{}", component);
        let Some((min, mid, max)) = crate::tweaks::devfreq::frequency_range(&base) else {
            continue;
        };
        let (target_min, target_max) = bus_dcvs_targets(profile, min, mid, max);
        applied += guard_node(&format!("{}/hw_min_freq", base), &target_min.to_string());
        applied += guard_node(&format!("{}/hw_max_freq", base), &target_max.to_string());
    }

    applied
}

pub fn apply(profile: SocProfile) -> usize {
    let applied = super::apply_devfreq(profile, NEEDLES);
    let devfreq = apply_kgsl_devfreq(profile);
    let flags = apply_kgsl_flags(profile);
    let bus_dcvs = apply_bus_dcvs(profile);
    let pwrlevels = apply_kgsl_pwrlevels(profile);
    if devfreq > 0 {
        log!("[SOC] Snapdragon KGSL devfreq tuned for {:?}", profile);
    }
    if flags > 0 {
        log!("[SOC] Snapdragon KGSL flags applied ({})", flags);
    }
    if bus_dcvs > 0 {
        log!("[SOC] Snapdragon bus_dcvs locked ({})", bus_dcvs);
    }
    if pwrlevels > 0 {
        log!("[SOC] Snapdragon KGSL power levels locked ({})", pwrlevels);
    }
    applied
}

pub fn optimize_storage(profile: SocProfile) -> usize {
    let governor = match profile {
        SocProfile::Performance => "performance",
        SocProfile::Lite => "simple_ondemand",
        SocProfile::Eco => "powersave",
    };
    crate::tweaks::devfreq::apply(&["ufs", "mmc"], governor, profile == SocProfile::Performance)
}

#[cfg(test)]
mod tests {
    use super::{force_values, hwcg_values, SocProfile};

    #[test]
    fn performance_forces_kgsl_rails_on() {
        assert_eq!(force_values(SocProfile::Performance), ("1", "0"));
    }

    #[test]
    fn lite_and_eco_release_kgsl_rails() {
        assert_eq!(force_values(SocProfile::Lite), ("0", "1"));
        assert_eq!(force_values(SocProfile::Eco), ("0", "1"));
    }

    #[test]
    fn performance_disables_hardware_clock_gating() {
        assert_eq!(hwcg_values(SocProfile::Performance), "0");
        assert_eq!(hwcg_values(SocProfile::Lite), "1");
    }
}