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
use std::sync::{Mutex, Once};

static LOG_LOCK: Mutex<()> = Mutex::new(());
static INIT_LOGGER_ONCE: Once = Once::new();

const LOG_FILE: &str = "/data/local/tmp/luminad.log";
const LOG_FILE_OLD: &str = "/data/local/tmp/luminad.log.old";
const MAX_BYTES: u64 = 1024 * 1024;

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

fn get_timestamp() -> String {
    unsafe {
        let mut now: i64 = 0;
        time(&mut now);
        let mut tm = std::mem::zeroed::<Tm>();
        if !localtime_r(&now, &mut tm).is_null() {
            return format!("{:02}:{:02}:{:02}", tm.tm_hour, tm.tm_min, tm.tm_sec);
        }
    }
    String::new()
}

pub fn init_logger() {
    INIT_LOGGER_ONCE.call_once(|| {
        let _guard = LOG_LOCK.lock().unwrap_or_else(|error| error.into_inner());
        if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(LOG_FILE) {
            let _ = writeln!(file, "{} [SYSTEM] Session started", get_timestamp());
        }
    });
}

type LogWrite =
    unsafe extern "C" fn(i32, *const std::os::raw::c_char, *const std::os::raw::c_char) -> i32;

static LOGCAT: std::sync::OnceLock<Option<LogWrite>> = std::sync::OnceLock::new();

fn logcat_write(msg: &str) {
    let writer = LOGCAT.get_or_init(|| unsafe {
        let handle = libc::dlopen(
            b"liblog.so\0".as_ptr() as *const std::os::raw::c_char,
            libc::RTLD_NOW,
        );
        if handle.is_null() {
            return None;
        }
        let symbol = libc::dlsym(
            handle,
            b"__android_log_write\0".as_ptr() as *const std::os::raw::c_char,
        );
        if symbol.is_null() {
            return None;
        }
        Some(std::mem::transmute_copy(&symbol))
    });

    let Some(writer) = writer else {
        return;
    };
    let Ok(text) = std::ffi::CString::new(msg) else {
        return;
    };
    let tag = std::ffi::CString::new("luminad").unwrap_or_default();
    unsafe {
        writer(4, tag.as_ptr(), text.as_ptr());
    }
}

pub fn write_log(msg: &str) {
    init_logger();

    let timestamp = get_timestamp();
    let formatted = if timestamp.is_empty() {
        msg.to_string()
    } else {
        format!("{} {}", timestamp, msg)
    };

    println!("{}", formatted);

    logcat_write(&formatted);

    let _guard = LOG_LOCK.lock().unwrap_or_else(|error| error.into_inner());
    if let Ok(meta) = fs::metadata(LOG_FILE) {
        if meta.len() >= MAX_BYTES {
            if Path::new(LOG_FILE_OLD).exists() {
                let _ = fs::remove_file(LOG_FILE_OLD);
            }
            let _ = fs::rename(LOG_FILE, LOG_FILE_OLD);
        }
    }

    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(LOG_FILE) {
        let _ = writeln!(file, "{}", formatted);
    }
}

#[macro_export]
macro_rules! log {
    ($($arg:tt)*) => {
        $crate::logger::write_log(&format!($($arg)*))
    };
}
