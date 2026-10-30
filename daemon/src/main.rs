#[macro_use]
mod logger;
mod affinity;
mod mediatek;
mod detector;
mod display;
mod engine;
mod notifier;
mod renderer;

use std::process::Command;
use std::thread;
use std::time::Duration;

use affinity::CpuTopology;
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

fn set_system_profile_prop(name: &str) {
    let _ = Command::new("setprop")
        .args(["sys.lumina.profile", name])
        .status();
}

fn get_pkg_pid(pkg: &str) -> Option<i32> {
    let output = Command::new("pidof")
        .arg(pkg)
        .output()
        .ok()?;

    let text = String::from_utf8_lossy(&output.stdout);
    text.split_whitespace()
        .next()?
        .parse::<i32>()
        .ok()
}

struct EngineRuntime {
    current_profile: Option<Profile>,
    active_target_pkg: Option<String>,
    current_applied_rr: Option<i32>,
    current_applied_renderer: String,
    original_zen_mode: Option<String>,
    dnd_overridden: bool,
    topology: CpuTopology,
}

impl EngineRuntime {
    fn new() -> Self {
        set_system_profile_prop("Balanced");
        let topo = CpuTopology::detect();
        log!("[TOPOLOGY] Cores: {} | Little Mask: 0x{:X} | Fast Mask: 0x{:X}", 
             topo.total_cores, topo.little_mask, topo.fast_mask);

        Self {
            current_profile: None,
            active_target_pkg: None,
            current_applied_rr: display::get_saved_mode_id(),
            current_applied_renderer: renderer::get_saved_renderer(),
            original_zen_mode: None,
            dnd_overridden: false,
            topology: topo,
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

        if self.current_profile != Some(target_profile) {
            match target_profile {
                Profile::Performance => {
                    engine::performance_profile();
                    set_system_profile_prop("Performance");
                    affinity::isolate_background(true, &self.topology);
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
                    affinity::isolate_background(true, &self.topology);
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
                    affinity::isolate_background(false, &self.topology);
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
                    affinity::isolate_background(false, &self.topology);
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

        if let Some(pkg) = target_pkg {
            if target_profile == Profile::Performance || target_profile == Profile::PerformanceLite {
                if let Some(pid) = get_pkg_pid(pkg) {
                    affinity::tune_game_threads(pid, &self.topology);
                }
            }
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

        if let Some(ref cfg) = target_cfg {
            match cfg.dnd.as_str() {
                "on" => {
                    if !self.dnd_overridden {
                        self.original_zen_mode = get_zen_mode();
                        set_zen_mode("1");
                        self.dnd_overridden = true;
                        log!("[DND] Zen mode enabled for target application");
                    }
                }
                "off" => {
                    if !self.dnd_overridden {
                        self.original_zen_mode = get_zen_mode();
                        set_zen_mode("0");
                        self.dnd_overridden = true;
                    }
                }
                _ => {
                    if self.dnd_overridden {
                        if let Some(ref orig) = self.original_zen_mode {
                            set_zen_mode(orig);
                        }
                        self.dnd_overridden = false;
                        self.original_zen_mode = None;
                        log!("[DND] Zen mode restored");
                    }
                }
            }
        } else if self.dnd_overridden {
            if let Some(ref orig) = self.original_zen_mode {
                set_zen_mode(orig);
            }
            self.dnd_overridden = false;
            self.original_zen_mode = None;
            log!("[DND] Zen mode restored");
        }
    }

    fn reset_to_balance(&mut self) {
        if self.current_profile != Some(Profile::Balance) {
            self.active_target_pkg = None;
            self.current_profile = Some(Profile::Balance);
            engine::balance_profile();
            set_system_profile_prop("Balanced");
            affinity::isolate_background(false, &self.topology);
            log!("[PROFILE] Master disabled -> Reverted to Balanced");

            let global_rr = display::get_saved_mode_id();
            if global_rr != self.current_applied_rr {
                if let Some(id) = global_rr {
                    display::apply_refresh_rate_id(id);
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
            }

            clear_all_notifications();
        }
    }
}

fn main() {
    log!("[DAEMON] Initializing luminad subsystem...");
    display::init_display_subsystem();
    renderer::init_renderer_subsystem();

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

    loop {
        if is_master_enabled() {
            let focused = get_focused_package();
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

            runtime.apply_target(current_target.as_deref());
        } else {
            runtime.reset_to_balance();
        }

        thread::sleep(Duration::from_millis(1200));
    }
}
