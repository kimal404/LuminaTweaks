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
    log_sub!("CORE", "LuminaCore Daemon initialized (PID: {})", std::process::id());
    log_sub!("CORE", "Logger attached: {}", logger::LOG_FILE_PATH);

    let mut last_pkg: Option<String> = None;
    let mut last_effective_lite: Option<bool> = None;
    let mut last_dnd_state: Option<bool> = None;

    loop {
        let current_pkg = ForegroundDetector::get_foreground_package()
            .unwrap_or_else(|| "unknown".to_string());

        let global_config = ConfigManager::load_global_config(GLOBAL_CONFIG_PATH);
        let app_configs = ConfigManager::load_app_configs(APP_CONFIG_PATH).unwrap_or_default();

        let effective_lite = PolicyEngine::calculate_effective_lite(
            &current_pkg,
            &global_config,
            &app_configs,
        );

        let effective_dnd = PolicyEngine::calculate_effective_dnd(
            &current_pkg,
            &app_configs,
        );

        let pkg_changed = last_pkg.as_deref() != Some(&current_pkg);

        if pkg_changed {
            log_sub!(
                "SESSION",
                "Foreground app active: \"{}\" - Lite Mode: {}, DnD: {}",
                current_pkg,
                if effective_lite { "ON" } else { "OFF" },
                if effective_dnd { "ON" } else { "OFF" }
            );
            last_pkg = Some(current_pkg.clone());
        }

        if last_effective_lite != Some(effective_lite) {
            PolicyEngine::apply_lite_mode_state(effective_lite);
            last_effective_lite = Some(effective_lite);
        }

        if last_dnd_state != Some(effective_dnd) {
            PolicyEngine::apply_dnd_state(effective_dnd);
            last_dnd_state = Some(effective_dnd);
        }

        sleep(Duration::from_millis(1200));
    }
}
