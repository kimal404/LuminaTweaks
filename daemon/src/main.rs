mod config;
mod detector;
mod engine;
mod logger;

use config::{ConfigManager, APP_CONFIG_PATH, GLOBAL_CONFIG_PATH};
use detector::ForegroundDetector;
use engine::PolicyEngine;
use std::thread::sleep;
use std::time::Duration;

fn main() {
    log_info!("Daemon", "=== Luminad Core Daemon Dimulai (PID: {}) ===", std::process::id());
    log_info!("Daemon", "File log tersimpan di: {}", logger::LOG_FILE_PATH);

    let mut last_pkg: Option<String> = None;
    let mut last_effective_state: Option<bool> = None;

    loop {
        let current_pkg = ForegroundDetector::get_foreground_package()
            .unwrap_or_else(|| "unknown".to_string());

        let global_config = ConfigManager::load_global_config(GLOBAL_CONFIG_PATH);
        let app_configs = ConfigManager::load_app_configs(APP_CONFIG_PATH).unwrap_or_default();

        let pkg_changed = last_pkg.as_deref() != Some(&current_pkg);

        // Hanya cetak log evaluasi engine jika aplikasi berpindah
        let effective_lite = PolicyEngine::calculate_effective_lite(
            &current_pkg,
            &global_config,
            &app_configs,
            pkg_changed,
        );

        if pkg_changed {
            log_info!(
                "Daemon",
                "Perpindahan Aplikasi -> '{}' | Evaluasi Lite Mode: {}",
                current_pkg,
                if effective_lite { "AKTIF" } else { "NONAKTIF" }
            );
            last_pkg = Some(current_pkg.clone());
        }

        if last_effective_state != Some(effective_lite) {
            PolicyEngine::apply_lite_mode_state(effective_lite);
            last_effective_state = Some(effective_lite);
        }

        sleep(Duration::from_millis(1200));
    }
}
