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

const NEEDLES: &[&str] = &[
    "gpu", "mali", "ddr", "bus", "memlat", "sprd", "unisoc", "scene",
];

pub fn apply(profile: SocProfile) -> usize {
    super::apply_devfreq(profile, NEEDLES)
}

#[cfg(test)]
mod tests {
    use super::NEEDLES;

    #[test]
    fn uses_unisoc_specific_devfreq_needles() {
        assert!(NEEDLES.contains(&"sprd"));
        assert!(NEEDLES.contains(&"unisoc"));
    }
}