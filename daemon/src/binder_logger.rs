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

use std::fs::OpenOptions;
use std::io::Write;
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

static LOG_LOCK: Mutex<()> = Mutex::new(());
const LOG_FILE: &str = "/data/local/tmp/lumina-binder.log";
const MAX_BYTES: u64 = 256 * 1024;

pub fn write_log(msg: &str) {
    let _guard = LOG_LOCK.lock().unwrap_or_else(|error| error.into_inner());

    if let Ok(meta) = std::fs::metadata(LOG_FILE) {
        if meta.len() >= MAX_BYTES {
            let _ = std::fs::remove_file(LOG_FILE);
        }
    }

    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs())
        .unwrap_or(0);

    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(LOG_FILE) {
        let _ = writeln!(file, "[{}] {}", timestamp, msg);
    }
}

#[macro_export]
macro_rules! log {
    ($($arg:tt)*) => {
        $crate::logger::write_log(&format!($($arg)*))
    };
}
