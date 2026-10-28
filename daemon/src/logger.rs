use std::fs::OpenOptions;
use std::io::Write;
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};

pub const LOG_FILE_PATH: &str = "/data/adb/luminatweaks/luminad.log";

static LOG_MUTEX: Mutex<()> = Mutex::new(());

pub fn write_log(level: &str, tag: &str, msg: &str) {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default();
    
    // Offset UTC+7 (WIB)
    let total_secs = now.as_secs() + (7 * 3600);
    let hours = (total_secs / 3600) % 24;
    let mins = (total_secs / 60) % 60;
    let secs = total_secs % 60;
    let millis = now.subsec_millis();

    let formatted_line = format!(
        "[{:02}:{:02}:{:02}.{:03}] [{}] [{}] {}\n",
        hours, mins, secs, millis, level, tag, msg
    );

    let _guard = LOG_MUTEX.lock().unwrap();

    // Print langsung ke terminal aktif
    print!("{}", formatted_line);

    // Simpan ke file log persisten
    if let Ok(mut file) = OpenOptions::new()
        .create(true)
        .append(true)
        .open(LOG_FILE_PATH)
    {
        let _ = file.write_all(formatted_line.as_bytes());
    }
}

#[macro_export]
macro_rules! log_info {
    ($tag:expr, $($arg:tt)*) => {
        $crate::logger::write_log("INFO", $tag, &format!($($arg)*))
    };
}

#[macro_export]
macro_rules! log_debug {
    ($tag:expr, $($arg:tt)*) => {
        $crate::logger::write_log("DEBUG", $tag, &format!($($arg)*))
    };
}

#[macro_export]
macro_rules! log_warn {
    ($tag:expr, $($arg:tt)*) => {
        $crate::logger::write_log("WARN", $tag, &format!($($arg)*))
    };
}

#[macro_export]
macro_rules! log_error {
    ($tag:expr, $($arg:tt)*) => {
        $crate::logger::write_log("ERROR", $tag, &format!($($arg)*))
    };
}
