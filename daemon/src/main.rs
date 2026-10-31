mod texture;
use texture::TextureController;
#[macro_use]
mod logger;
mod affinity;
mod detector;
mod display;
mod engine;
mod mediatek;
mod notifier;
mod renderer;

use std::collections::HashSet;
use std::process::Command;
use std::thread;
use std::time::{Duration, SystemTime};

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
    let _ = Command::new("setprop").args(["sys.lumina.profile", name]).status();
}

fn get_pkg_pid(pkg: &str) -> Option<i32> {
    affinity::get_pkg_pid(pkg)
}

struct EngineRuntime {
    current_profile: Option<Profile>,
    active_target_pkg: Option<String>,
    current_applied_rr: Option<i32>,
    current_applied_renderer: String,
    original_zen_mode: Option<String>,
    dnd_overridden: bool,
    topology: CpuTopology,
    tracked_tids: HashSet<i32>,
    current_target_pid: Option<i32>,
    last_config_mtime: Option<SystemTime>,
}

impl EngineRuntime {
    fn new() -> Self {
        set_system_profile_prop("Balanced");
        let topo = CpuTopology::detect();
        log!("[TOPOLOGY] Cores: {} | Little Mask: 0x{:X} | Fast Mask: 0x{:X}", 
             topo.total_cores, topo.little_mask, topo.fast_mask);

        TextureController::ensure_setup();

        Self {
            current_profile: None,
            active_target_pkg: None,
            current_applied_rr: display::get_saved_mode_id(),
            current_applied_renderer: renderer::get_saved_renderer(),
            original_zen_mode: None,
            dnd_overridden: false,
            topology: topo,
            tracked_tids: HashSet::new(),
            current_target_pid: None,
            last_config_mtime: None,
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
        let entering_performance = is_profile_changing
            && (target_profile == Profile::Performance || target_profile == Profile::PerformanceLite);

        if is_profile_changing {
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
                    affinity::restore_process_threads(&mut self.tracked_tids, &self.topology);
                    self.current_target_pid = None;
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
                    affinity::restore_process_threads(&mut self.tracked_tids, &self.topology);
                    self.current_target_pid = None;
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

        if target_profile == Profile::Performance || target_profile == Profile::PerformanceLite {
            if let Some(pkg) = target_pkg {
                if let Some(pid) = get_pkg_pid(pkg) {
                    if self.current_target_pid != Some(pid) {
                        affinity::restore_process_threads(&mut self.tracked_tids, &self.topology);
                        self.current_target_pid = Some(pid);
                    }
                    affinity::sync_process_threads(pid, &mut self.tracked_tids, &self.topology);

                    if entering_performance {
                        let topo = self.topology.clone();
                        thread::spawn(move || {
                            let mut burst_tracked = HashSet::new();
                            for _ in 0..3 {
                                thread::sleep(Duration::from_millis(200));
                                affinity::sync_process_threads(pid, &mut burst_tracked, &topo);
                            }
                        });
                    }
                }
            }
        } else if !self.tracked_tids.is_empty() {
            affinity::restore_process_threads(&mut self.tracked_tids, &self.topology);
            self.current_target_pid = None;
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
            affinity::restore_process_threads(&mut self.tracked_tids, &self.topology);
            self.current_target_pid = None;
            self.active_target_pkg = None;
            self.current_profile = Some(Profile::Balance);
            engine::balance_profile();
            set_system_profile_prop("Balanced");
            affinity::isolate_background(false, &self.topology);
            log!("[PROFILE] Target inactive -> Reverted to Balanced");

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
    let mut last_pkg_for_texture: Option<String> = None;

    loop {
        let mut config_just_changed = false;
        if let Ok(meta) = std::fs::metadata("/data/adb/luminatweaks/app_config.json") {
            if let Ok(mtime) = meta.modified() {
                if runtime.last_config_mtime != Some(mtime) {
                    runtime.last_config_mtime = Some(mtime);
                    config_just_changed = true;
                }
            }
        }

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
            runtime.reset_to_balance();
            if config_just_changed {
                TextureController::restore();
            }
        }

        thread::sleep(Duration::from_millis(1200));
    }
}
