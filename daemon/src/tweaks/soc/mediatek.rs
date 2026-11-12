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

use super::{generic, SocProfile};
use crate::tweaks::state;

const GED_PARAMS_PATH: &str = "/sys/module/ged/parameters";

fn boost_values(profile: SocProfile) -> (&'static str, &'static str, &'static str) {
    match profile {
        SocProfile::Performance => ("1", "1", "1"),
        SocProfile::Lite | SocProfile::Eco => ("0", "0", "0"),
    }
}

fn write_param(name: &str, value: &str) {
    state::write_tracked(
        &format!("{}/{}", GED_PARAMS_PATH, name),
        value,
        false,
    );
}

pub fn apply(profile: SocProfile) -> usize {
    let applied = generic::apply(profile);
    let (boost_gpu, ged_boost, smart_boost) = boost_values(profile);
    write_param("boost_gpu_enable", boost_gpu);
    write_param("ged_boost_enable", ged_boost);
    write_param("ged_smart_boost", smart_boost);
    applied
}

#[cfg(test)]
mod tests {
    use super::{boost_values, SocProfile};

    #[test]
    fn performance_enables_all_ged_boosts() {
        assert_eq!(boost_values(SocProfile::Performance), ("1", "1", "1"));
    }

    #[test]
    fn lite_and_eco_disable_ged_boosts() {
        assert_eq!(boost_values(SocProfile::Lite), ("0", "0", "0"));
        assert_eq!(boost_values(SocProfile::Eco), ("0", "0", "0"));
    }
}
