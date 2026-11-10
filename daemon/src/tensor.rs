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

use crate::detector::get_system_prop;

const NODES: &[&str] = &["mif", "mem", "dram", "bus", "dvfs", "gpu", "mali"];
const PLATFORMS: &[&str] = &["zuma", "ripcurrent", "laguna", "deepspace", "gs101", "gs201", "gs301"];

pub struct TensorEngine;

impl TensorEngine {
    pub fn is_tensor() -> bool {
        let platform = get_system_prop("ro.board.platform").to_ascii_lowercase();
        let hardware = get_system_prop("ro.hardware").to_ascii_lowercase();
        let soc_model = get_system_prop("ro.soc.model").to_ascii_lowercase();
        let soc_mfg = get_system_prop("ro.soc.manufacturer").to_ascii_lowercase();

        soc_model.contains("tensor")
            || soc_mfg.contains("google")
            || hardware.contains("tensor")
            || PLATFORMS.iter().any(|name| platform.contains(name) || hardware.contains(name))
    }

    fn apply(governor: &str, lock_max: bool) {
        if !Self::is_tensor() {
            return;
        }
        if governor == "performance"
            && crate::mitigation::active("NO_PERFORMANCE_GOVERNOR")
        {
            return;
        }
        let nodes: &[&str] = if governor == "performance"
            && crate::mitigation::active("NO_DDR_TWEAK")
        {
            &["gpu", "mali"]
        } else {
            NODES
        };
        let count = crate::devfreq::apply(nodes, governor, lock_max);
        crate::log!(
            "[TENSOR] {} governor on {} devfreq node(s)",
            governor,
            count
        );
    }

    pub fn apply_performance() {
        Self::apply("performance", true);
    }

    pub fn apply_balance() {
        Self::apply("simple_ondemand", false);
    }

    pub fn apply_eco() {
        Self::apply("powersave", false);
    }
}
