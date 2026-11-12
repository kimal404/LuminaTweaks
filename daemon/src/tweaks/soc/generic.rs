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

const NEEDLES: &[&str] = &["gpu", "mali", "kgsl", "ddr", "bus", "memlat"];

pub fn apply(profile: SocProfile) -> usize {
    match profile {
        SocProfile::Performance => crate::tweaks::devfreq::apply(NEEDLES, "performance", true),
        SocProfile::Lite => crate::tweaks::devfreq::apply_capped(NEEDLES, "simple_ondemand", 85),
        SocProfile::Eco => crate::tweaks::devfreq::apply(NEEDLES, "powersave", false),
    }
}
