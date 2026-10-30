#[macro_use]
mod logger;
mod detector;
mod engine;
mod notifier;
mod display;
mod renderer;

use std::thread;
use std::time::Duration;
use detector::{
    get_app_config, get_focused_package, get_zen_mode, is_app_enabled, is_app_retained,
    is_lite_mode_active, is_low_power_active, is_master_enabled, set_zen_mode,
};
use notifier::{clear_all_notifications, send_notification, NotificationPayload};

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum Profile {
    Eco,
    Balance,
    Performance,
    PerformanceLite,
}

fn main() {
    log!("[*] luminad daemon started successfully");
    display::init_display_subsystem();
    renderer::init_renderer_subsystem();

    send_notification(NotificationPayload {
        title: "Initializing...",
        text: "Starting LuminaTweaks service...",
        is_profile: true,
        chrono: false,
        ..Default::default()
    });

    thread::sleep(Duration::from_millis(3000));

    send_notification(NotificationPayload {
        title: "LuminaTweaks Active",
        text: "Daemon running successfully",
        is_profile: true,
        timeout_ms: 60000,
        ..Default::default()
    });

    let mut current_profile: Option<Profile> = None;
    let mut active_target_pkg: Option<String> = None;
    let mut current_applied_rr: Option<i32> = display::get_saved_mode_id();
    let mut current_applied_renderer: String = renderer::get_saved_renderer();
    let mut original_zen_mode: Option<String> = None;
    let mut dnd_overridden = false;

    loop {
        if is_master_enabled() {
            let focused = get_focused_package();
            let is_global_lite = is_lite_mode_active();

            let newly_focused_target = focused.as_deref().filter(|pkg| is_app_enabled(pkg));

            let current_target = if let Some(pkg) = newly_focused_target {
                let pkg_str = pkg.to_string();
                active_target_pkg = Some(pkg_str.clone());
                Some(pkg_str)
            } else if let Some(ref saved_pkg) = active_target_pkg {
                if is_app_enabled(saved_pkg) && is_app_retained(saved_pkg) {
                    Some(saved_pkg.clone())
                } else {
                    active_target_pkg = None;
                    None
                }
            } else {
                None
            };

            let target_cfg = current_target.as_deref().and_then(get_app_config);

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

            if current_profile != Some(target_profile) {
                match target_profile {
                    Profile::Performance => {
                        engine::performance_profile();
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
                        send_notification(NotificationPayload {
                            title: "Lumina: Balanced",
                            text: "System is now at Dynamic State",
                            toast: Some("Balanced Profile Restored"),
                            is_profile: true,
                            chrono: true,
                            ..Default::default()
                        });
                    }
                }
                current_profile = Some(target_profile);
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

            if desired_rr != current_applied_rr {
                if let Some(id) = desired_rr {
                    display::apply_refresh_rate_id(id);
                    log!("[*] Refresh rate applied: mode id {}", id);
                }
                current_applied_rr = desired_rr;
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

            if desired_renderer != current_applied_renderer {
                renderer::setrender(&desired_renderer);
                log!("[*] Graphics renderer applied: {}", desired_renderer);
                current_applied_renderer = desired_renderer;
            }

            if let Some(ref cfg) = target_cfg {
                match cfg.dnd.as_str() {
                    "on" => {
                        if !dnd_overridden {
                            original_zen_mode = get_zen_mode();
                            set_zen_mode("1");
                            dnd_overridden = true;
                            log!("[*] DND enabled for target app");
                        }
                    }
                    "off" => {
                        if !dnd_overridden {
                            original_zen_mode = get_zen_mode();
                            set_zen_mode("0");
                            dnd_overridden = true;
                            log!("[*] DND disabled for target app");
                        }
                    }
                    _ => {
                        if dnd_overridden {
                            if let Some(ref orig) = original_zen_mode {
                                set_zen_mode(orig);
                            }
                            dnd_overridden = false;
                            original_zen_mode = None;
                        }
                    }
                }
            } else if dnd_overridden {
                if let Some(ref orig) = original_zen_mode {
                    set_zen_mode(orig);
                    log!("[*] DND restored to original mode: {}", orig);
                }
                dnd_overridden = false;
                original_zen_mode = None;
            }
        } else if current_profile != Some(Profile::Balance) {
            active_target_pkg = None;
            current_profile = Some(Profile::Balance);
            engine::balance_profile();

            let global_rr = display::get_saved_mode_id();
            if global_rr != current_applied_rr {
                if let Some(id) = global_rr {
                    display::apply_refresh_rate_id(id);
                }
                current_applied_rr = global_rr;
            }

            let global_renderer = renderer::get_saved_renderer();
            if global_renderer != current_applied_renderer {
                renderer::setrender(&global_renderer);
                current_applied_renderer = global_renderer;
            }

            if dnd_overridden {
                if let Some(ref orig) = original_zen_mode {
                    set_zen_mode(orig);
                }
                dnd_overridden = false;
                original_zen_mode = None;
            }

            clear_all_notifications();
        }

        thread::sleep(Duration::from_millis(1200));
    }
}
