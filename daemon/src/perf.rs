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

use std::sync::Mutex;
use std::time::Duration;

const SLOW_LOOP: Duration = Duration::from_millis(50);
const REPORT_EVERY: u64 = 300;

static STATS: Mutex<Stats> = Mutex::new(Stats {
    loops: 0,
    slow: 0,
    max: Duration::ZERO,
});

struct Stats {
    loops: u64,
    slow: u64,
    max: Duration,
}

pub fn record(elapsed: Duration) {
    let Ok(mut stats) = STATS.lock() else {
        return;
    };

    stats.loops += 1;
    if elapsed > stats.max {
        stats.max = elapsed;
    }
    if elapsed > SLOW_LOOP {
        stats.slow += 1;
    }

    if stats.loops % REPORT_EVERY == 0 {
        crate::log!(
            "[PERF] loops={} slow={} max={}ms",
            stats.loops,
            stats.slow,
            stats.max.as_millis()
        );
        stats.max = Duration::ZERO;
        stats.slow = 0;
    }
}
