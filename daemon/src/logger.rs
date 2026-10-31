use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;
use std::process::Command;
use std::sync::Mutex;

static LOG_LOCK: Mutex<()> = Mutex::new(());
const LOG_FILE: &str = "/data/adb/luminatweaks/luminad.log";
const LOG_FILE_OLD: &str = "/data/adb/luminatweaks/luminad.log.old";
const LOG_LEVEL_FILE: &str = "/data/adb/luminatweaks/log_level.txt";
const MAX_BYTES: u64 = 2 * 1024 * 1024;

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

pub fn get_configured_log_level() -> LogLevel {
    if let Ok(content) = fs::read_to_string(LOG_LEVEL_FILE) {
        match content.trim() {
            "0" => LogLevel::Info,
            "1" => LogLevel::Debug,
            "2" => LogLevel::Verbose,
            _ => LogLevel::Debug,
        }
    } else {
        LogLevel::Debug
    }
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

pub fn write_log_with_level(level: LogLevel, msg: &str) {
    let current_level = get_configured_log_level();
    if level > current_level {
        return;
    }

    println!("{}", msg);

    let priority = match level {
        LogLevel::Info => "i",
        LogLevel::Debug => "d",
        LogLevel::Verbose => "v",
    };

    let _ = Command::new("log")
        .args(["-t", "luminad", "-p", priority, msg])
        .status();

    let _guard = LOG_LOCK.lock().unwrap();
    let _ = fs::create_dir_all("/data/adb/luminatweaks");

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
    let level = if msg.contains("write_lock") || msg.contains("write_unlock") {
        LogLevel::Debug
    } else if msg.starts_with("[VERBOSE]") || (msg.contains("TID ") && msg.contains("bound to mask")) {
        LogLevel::Verbose
    } else {
        LogLevel::Info
    };

    write_log_with_level(level, msg);
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

#[macro_export]
macro_rules! log_verbose {
    ($($arg:tt)*) => {
        $crate::logger::write_log_with_level($crate::logger::LogLevel::Verbose, &format!($($arg)*))
    };
}
