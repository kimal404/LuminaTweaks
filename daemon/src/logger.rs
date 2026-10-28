use std::fs::OpenOptions;
use std::io::Write;
use std::sync::Mutex;

pub const LOG_FILE_PATH: &str = "/data/adb/luminatweaks/luminad.log";

static LOG_MUTEX: Mutex<()> = Mutex::new(());

pub fn write_subsystem_log(tag: &str, msg: &str) {
    // Tag dibuat seragam dengan lebar 10 karakter rata kiri
    let formatted_line = format!("[ {:<10} ] {}\n", tag, msg);

    let _guard = LOG_MUTEX.lock().unwrap();

    // Cetak ke terminal
    print!("{}", formatted_line);

    // Tulis ke file log persisten
    if let Ok(mut file) = OpenOptions::new()
        .create(true)
        .append(true)
        .open(LOG_FILE_PATH)
    {
        let _ = file.write_all(formatted_line.as_bytes());
    }
}

#[macro_export]
macro_rules! log_sub {
    ($tag:expr, $($arg:tt)*) => {
        $crate::logger::write_subsystem_log($tag, &format!($($arg)*))
    };
}
