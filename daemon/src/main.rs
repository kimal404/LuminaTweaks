// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

mod texture;
use texture::TextureController;
#[macro_use]
mod logger;
mod detector;
mod display;
mod engine;
mod ipc;
mod mediatek;
mod renderer;
mod snapdragon;
mod unisoc;
mod utility;

use ipc::{IpcServer, SharedState};
use utility::UtilityController;
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant, SystemTime};

use detector::{
    get_app_config, get_focused_package, is_app_enabled, is_process_alive,
    is_lite_mode_active, is_low_power_active, is_master_enabled,
};
use engine::apply_thermal_kill;

static SHUTDOWN: AtomicBool = AtomicBool::new(false);

extern "C" {
    fn signal(signum: i32, handler: extern "C" fn(i32)) -> usize;
}

const SIGINT: i32 = 2;
const SIGTERM: i32 = 15;

extern "C" fn handle_signal(_: i32) {
    SHUTDOWN.store(true, Ordering::Relaxed);
}

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum Profile {
    Eco,
    Balance,
    Performance,
    PerformanceLite,
}

impl Profile {
    pub fn as_str(&self) -> &'static str {
        match self {
            Profile::Eco => "Eco",
            Profile::Balance => "Balanced",
            Profile::Performance => "Performance",
            Profile::PerformanceLite => "Performance Lite",
        }
    }
}

fn set_system_profile_prop(name: &str) {
    let _ = Command::new("setprop").args(["sys.lumina.profile", name]).status();
}

struct EngineRuntime {
    current_profile: Option<Profile>,
    active_target_pkg: Option<String>,
    current_applied_rr: Option<i32>,
    current_applied_renderer: String,
    last_app_config_mtime: Option<SystemTime>,
    last_global_config_mtime: Option<SystemTime>,
    left_foreground_at: Option<Instant>,
    shared_state: Arc<SharedState>,
}

impl EngineRuntime {
    fn new(shared_state: Arc<SharedState>) -> Self {
        set_system_profile_prop("Balanced");
        TextureController::ensure_setup();

        Self {
            current_profile: None,
            active_target_pkg: None,
            current_applied_rr: display::get_saved_mode_id(),
            current_applied_renderer: renderer::get_saved_renderer(),
            last_app_config_mtime: None,
            last_global_config_mtime: None,
            left_foreground_at: None,
            shared_state,
        }
    }

    fn update_shared_state(&self, profile: Profile, target_pkg: Option<&str>) {
        if let Ok(mut p) = self.shared_state.current_profile.lock() {
            *p = profile.as_str().to_string();
        }
        if let Ok(mut pkg) = self.shared_state.active_package.lock() {
            *pkg = target_pkg.map(|s| s.to_string());
        }
    }

    fn apply_target(&mut self, target_pkg: Option<&str>) {
        let is_global_lite = is_lite_mode_active();
        let target_cfg = target_pkg.and_then(get_app_config);

        let target_profile = if let Some(ref cfg) = target_cfg {
            let is_lite = match cfg.lite_mode.as_str() {
                "on" => true,
                "off" => false,
                _ => is_global_lite,
            };
            if is_lite {
                Profile::PerformanceLite
            } else {
                Profile::Performance
            }
        } else if is_low_power_active() {
            Profile::Eco
        } else {
            Profile::Balance
        };

        let is_profile_changing = self.current_profile != Some(target_profile);

        if is_profile_changing {
            match target_profile {
                Profile::Performance => {
                    engine::performance_profile();
                    set_system_profile_prop("Performance");
                    log!("[PROFILE] Switched to Performance -> App: {:?}", target_pkg);
                }
                Profile::PerformanceLite => {
                    engine::performance_lite_profile();
                    set_system_profile_prop("Performance Lite");
                    log!("[PROFILE] Switched to Performance Lite -> App: {:?}", target_pkg);
                }
                Profile::Eco => {
                    engine::eco_profile();
                    set_system_profile_prop("Eco");
                    log!("[PROFILE] Switched to Eco (Power Save Active)");
                }
                Profile::Balance => {
                    engine::balance_profile();
                    set_system_profile_prop("Balanced");
                    log!("[PROFILE] Switched to Balanced (Dynamic Idle)");
                }
            }
            self.current_profile = Some(target_profile);
            self.update_shared_state(target_profile, target_pkg);
        }

        if !is_profile_changing && target_pkg.is_none() {
            return;
        }

        let global_rr = display::get_saved_mode_id();
        let desired_rr = if let Some(ref cfg) = target_cfg {
            if cfg.refresh_rate != "default" && !cfg.refresh_rate.is_empty() {
                display::resolve_mode_id(&cfg.refresh_rate).or(global_rr)
            } else {
                global_rr
            }
        } else {
            global_rr
        };

        if desired_rr != self.current_applied_rr {
            if let Some(id) = desired_rr {
                display::apply_refresh_rate_id(id);
                log!("[DISPLAY] Refresh rate applied: Mode ID {}", id);
            } else {
                display::apply_refresh_rate_id(-1);
                log!("[DISPLAY] Refresh rate reset to system default");
            }
            self.current_applied_rr = desired_rr;
        }

        let global_renderer = renderer::get_saved_renderer();
        let desired_renderer = if let Some(ref cfg) = target_cfg {
            if cfg.renderer != "default" && !cfg.renderer.is_empty() {
                cfg.renderer.clone()
            } else {
                global_renderer
            }
        } else {
            global_renderer
        };

        if desired_renderer != self.current_applied_renderer {
            renderer::setrender(&desired_renderer);
            log!("[RENDERER] Graphics backend applied: {}", desired_renderer);
            self.current_applied_renderer = desired_renderer;
        }
    }

    fn reset_to_balance(&mut self) {
        if self.current_profile != Some(Profile::Balance) {
            self.active_target_pkg = None;
            self.left_foreground_at = None;
            self.current_profile = Some(Profile::Balance);
            engine::balance_profile();
            set_system_profile_prop("Balanced");
            log!("[PROFILE] Target inactive -> Reverted to Balanced");
            self.update_shared_state(Profile::Balance, None);

            let global_rr = display::get_saved_mode_id();
            if global_rr != self.current_applied_rr {
                if let Some(id) = global_rr {
                    display::apply_refresh_rate_id(id);
                } else {
                    display::apply_refresh_rate_id(-1);
                }
                self.current_applied_rr = global_rr;
            }

            let global_renderer = renderer::get_saved_renderer();
            if global_renderer != self.current_applied_renderer {
                renderer::setrender(&global_renderer);
                self.current_applied_renderer = global_renderer;
            }
        }
    }
}

fn main() {
    unsafe {
        signal(SIGINT, handle_signal);
        signal(SIGTERM, handle_signal);
    }

    log!("[DAEMON] Initializing luminad subsystem...");
    display::init_display_subsystem();
    renderer::init_renderer_subsystem();
    engine::sync_global_tweaks();
    engine::apply_boot_profile();

    let shared_state = Arc::new(SharedState::new());
    IpcServer::start(Arc::clone(&shared_state));

    let mut utility_ctrl = UtilityController::new();

    log!("[DAEMON] Subsystems online, entering main event loop");
    let mut runtime = EngineRuntime::new(Arc::clone(&shared_state));
    let mut last_pkg_for_texture: Option<String> = None;

    loop {
        if SHUTDOWN.load(Ordering::Relaxed) {
            log!("[DAEMON] Shutdown signal caught, restoring system baseline...");
            runtime.reset_to_balance();
            engine::restore_balance();
            apply_thermal_kill(false);
            utility_ctrl.restore();
            TextureController::restore();
            break;
        }

        utility_ctrl.tick();

        let mut config_just_changed = shared_state.reload_flag.swap(false, Ordering::Relaxed);

        for path in [
            "/data/adb/.config/lumina/app_config.json",
            "/data/adb/modules/luminatweaks/app_config.json",
        ] {
            if let Ok(meta) = std::fs::metadata(path) {
                if let Ok(mtime) = meta.modified() {
                    if runtime.last_app_config_mtime != Some(mtime) {
                        runtime.last_app_config_mtime = Some(mtime);
                        config_just_changed = true;
                    }
                }
            }
        }

        for path in [
            "/data/adb/.config/lumina/config.json",
            "/data/adb/modules/luminatweaks/config.json",
        ] {
            if let Ok(meta) = std::fs::metadata(path) {
                if let Ok(mtime) = meta.modified() {
                    if runtime.last_global_config_mtime != Some(mtime) {
                        runtime.last_global_config_mtime = Some(mtime);
                        config_just_changed = true;
                    }
                }
            }
        }

        if config_just_changed {
            log!("[CONFIG] Reloading system tweaks...");
            engine::sync_global_tweaks();
        }

        let focused = get_focused_package();

        let current_target = if is_master_enabled() {
            let newly_focused_target = focused.as_deref().filter(|pkg| is_app_enabled(pkg));

            let target = if let Some(pkg) = newly_focused_target {
                let pkg_str = pkg.to_string();
                if runtime.left_foreground_at.is_some() {
                    log!("[SESSION] Focus restored to {}. Grace period cleared.", pkg_str);
                    runtime.left_foreground_at = None;
                }
                runtime.active_target_pkg = Some(pkg_str.clone());
                Some(pkg_str)
            } else if let Some(ref saved_pkg) = runtime.active_target_pkg {
                if !is_process_alive(saved_pkg) {
                    log!("[SESSION] Process for {} died. Reverting to Balance immediately.", saved_pkg);
                    runtime.active_target_pkg = None;
                    runtime.left_foreground_at = None;
                    None
                } else if is_app_enabled(saved_pkg) {
                    if runtime.left_foreground_at.is_none() {
                        log!("[SESSION] Focus lost from {}. 3s grace period engaged.", saved_pkg);
                        runtime.left_foreground_at = Some(Instant::now());
                    }

                    if let Some(left_time) = runtime.left_foreground_at {
                        if left_time.elapsed() < Duration::from_secs(3) {
                            Some(saved_pkg.clone())
                        } else {
                            log!("[SESSION] Grace period 3s expired for {}. Dropping target.", saved_pkg);
                            runtime.active_target_pkg = None;
                            runtime.left_foreground_at = None;
                            None
                        }
                    } else {
                        None
                    }
                } else {
                    runtime.active_target_pkg = None;
                    runtime.left_foreground_at = None;
                    None
                }
            } else {
                None
            };

            if config_just_changed || target != last_pkg_for_texture {
                if let Some(ref pkg) = target {
                    TextureController::apply_foreground(pkg);
                } else {
                    TextureController::sync_config(None);
                }
                last_pkg_for_texture = target.clone();
            }

            runtime.apply_target(target.as_deref());
            target
        } else {
            runtime.reset_to_balance();
            if config_just_changed {
                TextureController::restore();
            }
            last_pkg_for_texture = None;
            None
        };

        let sleep_ms = if current_target.is_some() {
            400
        } else {
            1200
        };

        thread::sleep(Duration::from_millis(sleep_ms));
    }
}
