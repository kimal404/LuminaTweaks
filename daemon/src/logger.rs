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

use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;
use std::process::Command;
use std::sync::{Mutex, Once};

static LOG_LOCK: Mutex<()> = Mutex::new(());
static INIT_LOGGER_ONCE: Once = Once::new();

const LOG_FILE: &str = "/data/local/tmp/lumina.log";
const LOG_FILE_OLD: &str = "/data/local/tmp/lumina.log.old";
const MAX_BYTES: u64 = 1 * 1024 * 1024;

#[repr(C)]
struct Tm {
    tm_sec: i32,
    tm_min: i32,
    tm_hour: i32,
    tm_mday: i32,
    tm_mon: i32,
    tm_year: i32,
    tm_wday: i32,
    tm_yday: i32,
    tm_isdst: i32,
    tm_gmtoff: std::os::raw::c_long,
    tm_zone: *const std::os::raw::c_char,
}

extern "C" {
    fn time(tloc: *mut i64) -> i64;
    fn localtime_r(timep: *const i64, result: *mut Tm) -> *mut Tm;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum LogLevel {
    Info = 0,
    Debug = 1,
    Verbose = 2,
}

fn get_timestamp() -> String {
    unsafe {
        let mut now: i64 = 0;
        time(&mut now);
        let mut tm = std::mem::zeroed::<Tm>();
        if !localtime_r(&now, &mut tm).is_null() {
            return format!(
                "{:04}-{:02}-{:02} {:02}:{:02}:{:02}",
                tm.tm_year + 1900,
                tm.tm_mon + 1,
                tm.tm_mday,
                tm.tm_hour,
                tm.tm_min,
                tm.tm_sec
            );
        }
    }
    Command::new("date")
        .args(["+%Y-%m-%d %H:%M:%S"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default()
}

pub fn init_logger() {
    INIT_LOGGER_ONCE.call_once(|| {
        let _guard = LOG_LOCK.lock().unwrap();
        if Path::new(LOG_FILE).exists() {
            if Path::new(LOG_FILE_OLD).exists() {
                let _ = fs::remove_file(LOG_FILE_OLD);
            }
            let _ = fs::rename(LOG_FILE, LOG_FILE_OLD);
        }

        if let Ok(mut file) = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .open(LOG_FILE)
        {
            let ts = get_timestamp();
            let _ = writeln!(file, "[{}] === Luminad Session Started ===", ts);
        }
    });
}

pub fn write_log_with_level(level: LogLevel, msg: &str) {
    init_logger();
    println!("{}", msg);

    if level == LogLevel::Info {
        let _ = Command::new("log")
            .args(["-t", "luminad", "-p", "i", msg])
            .status();
    }

    let _guard = LOG_LOCK.lock().unwrap();
    if let Ok(meta) = fs::metadata(LOG_FILE) {
        if meta.len() >= MAX_BYTES {
            if Path::new(LOG_FILE_OLD).exists() {
                let _ = fs::remove_file(LOG_FILE_OLD);
            }
            let _ = fs::rename(LOG_FILE, LOG_FILE_OLD);
        }
    }

    let timestamp = get_timestamp();
    let formatted = if timestamp.is_empty() {
        msg.to_string()
    } else {
        format!("[{}] {}", timestamp, msg)
    };

    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(LOG_FILE) {
        let _ = writeln!(file, "{}", formatted);
    }
}

pub fn write_log(msg: &str) {
    write_log_with_level(LogLevel::Info, msg);
}

#[macro_export]
macro_rules! log {
    ($($arg:tt)*) => {
        $crate::logger::write_log(&format!($($arg)*))
    };
}

#[macro_export]
macro_rules! log_info {
    ($($arg:tt)*) => {
        $crate::logger::write_log_with_level($crate::logger::LogLevel::Info, &format!($($arg)*))
    };
}

#[macro_export]
macro_rules! log_debug {
    ($($arg:tt)*) => {
        $crate::logger::write_log_with_level($crate::logger::LogLevel::Debug, &format!($($arg)*))
    };
}
