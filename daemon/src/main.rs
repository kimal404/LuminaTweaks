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
mod fast_load;
mod mediatek;
mod notifier;
mod renderer;
mod snapdragon;
mod unisoc;

use fast_load::FastLoadController;
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::{Duration, SystemTime};

use detector::{
    get_app_config, get_focused_package, get_zen_mode, is_app_enabled, is_app_retained,
    is_auto_dnd_active, is_lite_mode_active, is_low_power_active, is_master_enabled, set_zen_mode,
};
use engine::apply_thermal_kill;
use notifier::{clear_all_notifications, send_notification, NotificationPayload};

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

fn set_system_profile_prop(name: &str) {
    let _ = Command::new("setprop").args(["sys.lumina.profile", name]).status();
}

struct EngineRuntime {
    current_profile: Option<Profile>,
    active_target_pkg: Option<String>,
    current_applied_rr: Option<i32>,
    current_applied_renderer: String,
    original_zen_mode: Option<String>,
    dnd_overridden: bool,
    current_applied_zen_mode: Option<String>,
    last_app_config_mtime: Option<SystemTime>,
    last_global_config_mtime: Option<SystemTime>,
}

impl EngineRuntime {
    fn new() -> Self {
        set_system_profile_prop("Balanced");
        TextureController::ensure_setup();

        Self {
            current_profile: None,
            active_target_pkg: None,
            current_applied_rr: display::get_saved_mode_id(),
            current_applied_renderer: renderer::get_saved_renderer(),
            original_zen_mode: None,
            dnd_overridden: false,
            current_applied_zen_mode: None,
            last_app_config_mtime: None,
            last_global_config_mtime: None,
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
                    send_notification(NotificationPayload {
                        title: "Lumina: Performance",
                        text: "System is now at Maximum State",
                        toast: Some("Performance Profile Applied"),
                        is_profile: true,
                        chrono: true,
                        ..Default::default()
                    });
                }
                Profile::PerformanceLite => {
                    engine::performance_lite_profile();
                    set_system_profile_prop("Performance Lite");
                    log!("[PROFILE] Switched to Performance Lite -> App: {:?}", target_pkg);
                    send_notification(NotificationPayload {
                        title: "Lumina: Performance Lite",
                        text: "System is now at Optimized State",
                        toast: Some("Performance Lite Profile Applied"),
                        is_profile: true,
                        chrono: true,
                        ..Default::default()
                    });
                }
                Profile::Eco => {
                    engine::eco_profile();
                    set_system_profile_prop("Eco");
                    log!("[PROFILE] Switched to Eco (Power Save Active)");
                    send_notification(NotificationPayload {
                        title: "Lumina: Eco",
                        text: "System is now at Endurance State",
                        toast: Some("Applying Eco Mode"),
                        is_profile: true,
                        chrono: true,
                        ..Default::default()
                    });
                }
                Profile::Balance => {
                    engine::balance_profile();
                    set_system_profile_prop("Balanced");
                    log!("[PROFILE] Switched to Balanced (Dynamic Idle)");
                    clear_all_notifications();
                    send_notification(NotificationPayload {
                        title: "Lumina: Balance",
                        text: "System is now at Dynamic State",
                        toast: Some("Balanced Profile Applied"),
                        is_profile: true,
                        timeout_ms: 3000,
                        ..Default::default()
                    });
                }
            }
            self.current_profile = Some(target_profile);
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

        let auto_dnd = is_auto_dnd_active();
        let desired_dnd = if let Some(ref cfg) = target_cfg {
            match cfg.dnd.as_str() {
                "on" => Some(true),
                "off" => Some(false),
                _ => if auto_dnd && (target_profile == Profile::Performance || target_profile == Profile::PerformanceLite) {
                    Some(true)
                } else {
                    None
                }
            }
        } else if auto_dnd && (target_profile == Profile::Performance || target_profile == Profile::PerformanceLite) {
            Some(true)
        } else {
            None
        };

        match desired_dnd {
            Some(true) => {
                if !self.dnd_overridden {
                    self.original_zen_mode = get_zen_mode();
                    self.dnd_overridden = true;
                }
                if self.current_applied_zen_mode.as_deref() != Some("1") {
                    set_zen_mode("1");
                    self.current_applied_zen_mode = Some("1".to_string());
                    log!("[DND] Zen mode enabled (target or auto-dnd)");
                }
            }
            Some(false) => {
                if !self.dnd_overridden {
                    self.original_zen_mode = get_zen_mode();
                    self.dnd_overridden = true;
                }
                if self.current_applied_zen_mode.as_deref() != Some("0") {
                    set_zen_mode("0");
                    self.current_applied_zen_mode = Some("0".to_string());
                    log!("[DND] Zen mode disabled for target application");
                }
            }
            None => {
                if self.dnd_overridden {
                    if let Some(ref orig) = self.original_zen_mode {
                        set_zen_mode(orig);
                    }
                    self.dnd_overridden = false;
                    self.original_zen_mode = None;
                    self.current_applied_zen_mode = None;
                    log!("[DND] Zen mode restored");
                }
            }
        }
    }

    fn reset_to_balance(&mut self) {
        if self.current_profile != Some(Profile::Balance) {
            self.active_target_pkg = None;
            self.current_profile = Some(Profile::Balance);
            engine::balance_profile();
            set_system_profile_prop("Balanced");
            log!("[PROFILE] Target inactive -> Reverted to Balanced");

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

            if self.dnd_overridden {
                if let Some(ref orig) = self.original_zen_mode {
                    set_zen_mode(orig);
                }
                self.dnd_overridden = false;
                self.original_zen_mode = None;
                self.current_applied_zen_mode = None;
            }

            clear_all_notifications();
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

    let mut fast_loader = FastLoadController::new();

    send_notification(NotificationPayload {
        title: "Lumina: Active",
        text: "System is now at Dynamic State",
        toast: Some("LuminaTweaks Subsystem Active"),
        is_profile: true,
        timeout_ms: 5000,
        ..Default::default()
    });

    log!("[DAEMON] Subsystems online, entering main event loop");
    let mut runtime = EngineRuntime::new();
    let mut last_pkg_for_texture: Option<String> = None;

    loop {
        if SHUTDOWN.load(Ordering::Relaxed) {
            log!("[DAEMON] Shutdown signal caught, restoring system baseline...");
            runtime.reset_to_balance();
            engine::restore_balance();
            apply_thermal_kill(false);
            fast_loader.restore();
            TextureController::restore();
            break;
        }

        let mut config_just_changed = false;
        if let Ok(meta) = std::fs::metadata("/data/adb/luminatweaks/app_config.json") {
            if let Ok(mtime) = meta.modified() {
                if runtime.last_app_config_mtime != Some(mtime) {
                    runtime.last_app_config_mtime = Some(mtime);
                    config_just_changed = true;
                }
            }
        }
        if let Ok(meta) = std::fs::metadata("/data/adb/luminatweaks/config.json") {
            if let Ok(mtime) = meta.modified() {
                if runtime.last_global_config_mtime != Some(mtime) {
                    runtime.last_global_config_mtime = Some(mtime);
                    config_just_changed = true;
                }
            }
        }

        if config_just_changed {
            engine::sync_global_tweaks();
        }

        let focused = get_focused_package();

        if is_master_enabled() {
            if let Some(ref current_pkg) = focused {
                let is_game = detector::is_app_enabled(current_pkg);
                fast_loader.tick(current_pkg, is_game);
            } else {
                fast_loader.tick("", false);
            }

            let newly_focused_target = focused.as_deref().filter(|pkg| is_app_enabled(pkg));

            let current_target = if let Some(pkg) = newly_focused_target {
                let pkg_str = pkg.to_string();
                runtime.active_target_pkg = Some(pkg_str.clone());
                Some(pkg_str)
            } else if let Some(ref saved_pkg) = runtime.active_target_pkg {
                if is_app_enabled(saved_pkg) && is_app_retained(saved_pkg) {
                    Some(saved_pkg.clone())
                } else {
                    runtime.active_target_pkg = None;
                    None
                }
            } else {
                None
            };

            if config_just_changed || current_target != last_pkg_for_texture {
                if let Some(ref pkg) = current_target {
                    TextureController::apply_foreground(pkg);
                } else {
                    TextureController::sync_config(None);
                }
                last_pkg_for_texture = current_target.clone();
            }

            runtime.apply_target(current_target.as_deref());
        } else {
            fast_loader.restore();
            runtime.reset_to_balance();
            if config_just_changed {
                TextureController::restore();
            }
            last_pkg_for_texture = None;
        }

        thread::sleep(Duration::from_millis(1200));
    }
}
