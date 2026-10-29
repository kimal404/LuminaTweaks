use std::fs::{self, OpenOptions};
use std::io::Write;
use std::sync::Mutex;

static LOG_LOCK: Mutex<()> = Mutex::new(());
const LOG_FILE: &str = "/data/adb/luminatweaks/luminad.log";
const MAX_BYTES: u64 = 512 * 1024;

pub fn write_log(msg: &str) {
    println!("{}", msg);

    let _guard = LOG_LOCK.lock().unwrap();
    if let Ok(meta) = fs::metadata(LOG_FILE) {
        if meta.len() >= MAX_BYTES {
            let _ = fs::rename(LOG_FILE, format!("{}.old", LOG_FILE));
        }
    }

    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(LOG_FILE) {
        let _ = writeln!(file, "{}", msg);
    }
}

#[macro_export]
macro_rules! log {
    ($($arg:tt)*) => {
        $crate::logger::write_log(&format!($($arg)*))
    };
}
