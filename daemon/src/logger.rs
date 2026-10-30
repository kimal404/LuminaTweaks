use std::fs::{self, OpenOptions};
use std::io::Write;
use std::process::Command;
use std::sync::Mutex;

static LOG_LOCK: Mutex<()> = Mutex::new(());
const LOG_FILE: &str = "/data/adb/luminatweaks/luminad.log";
const MAX_BYTES: u64 = 512 * 1024;

pub fn write_log(msg: &str) {
    if msg.contains("write_lock OK") || msg.contains("write_unlock OK") {
        return;
    }

    println!("{}", msg);

    let _ = Command::new("log")
        .args(["-t", "luminad", "-p", "i", msg])
        .status();

    let _guard = LOG_LOCK.lock().unwrap();
    let _ = fs::create_dir_all("/data/adb/luminatweaks");
    if let Ok(meta) = fs::metadata(LOG_FILE) {
        if meta.len() >= MAX_BYTES {
            let _ = fs::rename(LOG_FILE, format!("{}.old", LOG_FILE));
        }
    }

    let timestamp = Command::new("date")
        .args(["+%Y-%m-%d %H:%M:%S"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default();

    let formatted = if timestamp.is_empty() {
        msg.to_string()
    } else {
        format!("[{}] {}", timestamp, msg)
    };

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
