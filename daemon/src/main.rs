mod config;
mod engine;

use config::{ConfigManager, APP_CONFIG_PATH, GLOBAL_CONFIG_PATH};
use engine::PolicyEngine;
use std::thread::sleep;
use std::time::Duration;

fn main() {
    println!("[LuminaD] Daemon started with PID: {}", std::process::id());

    let mut last_effective_state: Option<bool> = None;

    loop {
        // Muat konfigurasi terbaru (real-time reload)
        let global_config = ConfigManager::load_global_config(GLOBAL_CONFIG_PATH);
        let app_configs = ConfigManager::load_app_configs(APP_CONFIG_PATH).unwrap_or_default();

        // Placeholder deteksi foreground package (nanti diisi via dump window/usage_stats/proc)
        let current_foreground_pkg = "com.mobile.legends";

        let effective_lite = PolicyEngine::calculate_effective_lite(
            current_foreground_pkg,
            &global_config,
            &app_configs,
        );

        // Hanya jalankan penulisan sysfs jika ada perubahan status
        if last_effective_state != Some(effective_lite) {
            println!(
                "[LuminaD] Target: {} | Lite Mode status berubah -> {}",
                current_foreground_pkg, effective_lite
            );
            PolicyEngine::apply_lite_mode_state(effective_lite);
            last_effective_state = Some(effective_lite);
        }

        sleep(Duration::from_millis(1500));
    }
}
