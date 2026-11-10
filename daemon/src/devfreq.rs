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

use std::fs;
use std::path::Path;

fn write_governor(base: &str, governor: &str) {
    let path = format!("{}/governor", base);
    let available = fs::read_to_string(format!("{}/available_governors", base)).unwrap_or_default();
    if available.is_empty() || available.split_whitespace().any(|item| item == governor) {
        crate::engine::write_sysfs(&path, governor, false);
    }
}

fn write_frequency_limits(base: &str, lock_max: bool) {
    let Ok(content) = fs::read_to_string(format!("{}/available_frequencies", base)) else {
        return;
    };
    let frequencies: Vec<u64> = content
        .split_whitespace()
        .filter_map(|value| value.parse().ok())
        .collect();

    let (Some(min), Some(max)) = (frequencies.iter().min(), frequencies.iter().max()) else {
        return;
    };

    let min_target = if lock_max { *max } else { *min };
    crate::engine::write_sysfs(&format!("{}/min_freq", base), &min_target.to_string(), lock_max);
    crate::engine::write_sysfs(&format!("{}/max_freq", base), &max.to_string(), lock_max);
}

pub fn apply(needles: &[&str], governor: &str, lock_max: bool) -> usize {
    let Ok(entries) = fs::read_dir("/sys/class/devfreq") else {
        return 0;
    };

    let mut applied = 0;
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
        if !needles.iter().any(|needle| name.contains(needle)) {
            continue;
        }

        let base = format!("/sys/class/devfreq/{}", entry.file_name().to_string_lossy());
        if !Path::new(&base).exists() {
            continue;
        }

        write_governor(&base, governor);
        write_frequency_limits(&base, lock_max);
        applied += 1;
    }
    applied
}
