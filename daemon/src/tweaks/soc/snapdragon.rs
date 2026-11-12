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

const KGSL_PATH: &str = "/sys/class/kgsl/kgsl-3d0";
const ADRENO_IDLER_PATH: &str = "/sys/module/adreno_idler/parameters/adreno_idler_active";

fn force_values(profile: SocProfile) -> (&'static str, &'static str) {
    match profile {
        SocProfile::Performance => ("1", "0"),
        SocProfile::Lite | SocProfile::Eco => ("0", "1"),
    }
}

fn write_kgsl(name: &str, value: &str) {
    state::write_tracked(&format!("{}/{}", KGSL_PATH, name), value, false);
}

pub fn apply(profile: SocProfile) -> usize {
    let applied = generic::apply(profile);
    let (force, idler) = force_values(profile);
    for node in ["force_clk_on", "force_bus_on", "force_rail_on", "force_no_nap"] {
        write_kgsl(node, force);
    }
    state::write_tracked(ADRENO_IDLER_PATH, idler, false);
    applied
}

#[cfg(test)]
mod tests {
    use super::{force_values, SocProfile};

    #[test]
    fn performance_forces_kgsl_rails_on() {
        assert_eq!(force_values(SocProfile::Performance), ("1", "0"));
    }

    #[test]
    fn lite_and_eco_release_kgsl_rails() {
        assert_eq!(force_values(SocProfile::Lite), ("0", "1"));
        assert_eq!(force_values(SocProfile::Eco), ("0", "1"));
    }
}
