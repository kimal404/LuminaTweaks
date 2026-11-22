/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#![allow(dead_code)]

#[macro_use]
mod core;
mod binder;
mod session;
mod tweaks;

use binder::client as binder_client;
use core::detector::{
    get_app_config, get_focused_package, is_app_enabled, is_auto_dnd_enabled, is_lite_mode_active,
    is_low_power_active, is_master_enabled,
};
use core::ipc::{IpcServer, SharedState};
use core::{detector, perf, watcher};
use std::collections::hash_map::DefaultHasher;
use std::fs::{File, OpenOptions};
use std::hash::{Hash, Hasher};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::io::AsRawFd;
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tweaks::texture::TextureController;
use tweaks::utility::UtilityController;
use tweaks::{display, engine, fas, fps, renderer, sched, soc, thermal};

static SHUTDOWN: AtomicBool = AtomicBool::new(false);

extern "C" {
    fn signal(signum: i32, handler: extern "C" fn(i32)) -> usize;
    fn flock(fd: i32, operation: i32) -> i32;
}

const SIGINT: i32 = 2;
const SIGTERM: i32 = 15;
const LOCK_EX: i32 = 2;
const LOCK_NB: i32 = 4;

const RUNTIME_DIR: &str = "/data/adb/.config/lumina";
const PID_FILE_PATH: &str = "/data/adb/.config/lumina/luminad.pid";
const HEARTBEAT_FILE_PATH: &str = "/data/adb/.config/lumina/luminad.alive";
const LEGACY_PID_FILE_PATH: &str = "/data/local/tmp/lumina.pid";
const LEGACY_HEARTBEAT_FILE_PATH: &str = "/data/local/tmp/lumina.alive";
const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(2);
const MANUAL_PROFILE_PATH: &str = "/data/adb/.config/lumina/profile.conf";

extern "C" fn handle_signal(_: i32) {
    SHUTDOWN.store(true, Ordering::Relaxed);
}

fn acquire_instance_lock() -> Option<File> {
    let _ = std::fs::create_dir_all(RUNTIME_DIR);
    for lock_path in [
        "/data/adb/.config/lumina/luminad.lock",
        "/data/local/tmp/luminad.lock",
    ] {
        if let Ok(file) = OpenOptions::new()
            .create(true)
            .read(true)
            .write(true)
            .open(lock_path)
        {
            let rc = unsafe { flock(file.as_raw_fd(), LOCK_EX | LOCK_NB) };
            if rc == 0 {
                return Some(file);
            }
            return None;
        }
    }
    None
}

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum Profile {
    Eco,
    Balance,
    Maestro,
    Performance,
}

impl Profile {
    pub fn as_str(&self) -> &'static str {
        match self {
            Profile::Eco => "Eco",
            Profile::Balance => "Balanced",
            Profile::Maestro => "Maestro",
            Profile::Performance => "Performance",
        }
    }

    pub fn display_name(&self, lite: bool) -> &'static str {
        match (self, lite) {
            (Profile::Performance, true) => "Performance Lite",
            _ => self.as_str(),
        }
    }

    pub fn from_name(name: &str) -> Option<Profile> {
        match name.trim().to_ascii_lowercase().as_str() {
            "eco" | "powersave" | "power save" | "hemat daya" => Some(Profile::Eco),
            "balanced" | "balance" | "seimbang" => Some(Profile::Balance),
            "performance" | "performa" | "performance lite" | "performance_lite"
            | "performance-lite" => Some(Profile::Performance),
            "maestro" => Some(Profile::Maestro),
            _ => None,
        }
    }
}

fn apply_profile(profile: Profile, lite: bool) {
    match profile {
        Profile::Eco => engine::eco_profile(),
        Profile::Balance => engine::balance_profile(),
        Profile::Maestro => engine::maestro_profile(lite),
        Profile::Performance => engine::performance_profile(lite),
    }
}

fn read_saved_manual_profile() -> Option<Profile> {
    std::fs::read_to_string(MANUAL_PROFILE_PATH)
        .ok()
        .and_then(|value| Profile::from_name(&value))
}

fn persist_manual_profile(profile: Profile) {
    let _ = write_runtime_file(MANUAL_PROFILE_PATH, &format!("{}\n", profile.as_str()));
}

fn config_hash() -> u64 {
    let mut hasher = DefaultHasher::new();
    for path in [
        "/data/adb/.config/lumina/config.json",
        "/data/adb/modules/luminatweaks/config.json",
        "/data/adb/modules/lumina_tweaks/config.json",
        "/data/adb/.config/lumina/app_config.json",
        "/data/adb/modules/luminatweaks/app_config.json",
        "/data/adb/modules/lumina_tweaks/app_config.json",
    ] {
        if let Ok(content) = std::fs::read(path) {
            content.hash(&mut hasher);
        }
    }
    hasher.finish()
}

fn kernel_release() -> String {
    std::fs::read_to_string("/proc/sys/kernel/osrelease")
        .map(|value| value.trim().to_string())
        .unwrap_or_else(|_| "unknown".to_string())
}

fn set_system_profile_prop(name: &str) {
    let _ = Command::new("setprop")
        .args(["sys.lumina.profile", name])
        .status();
}

fn set_dnd(binder: &mut binder_client::BinderClient, mode: &str) {
    if !binder.set_dnd(mode) {
        engine::set_dnd_mode(mode);
    }
}

fn write_runtime_file(path: &str, value: &str) -> bool {
    if let Some(parent) = std::path::Path::new(path).parent() {
        if std::fs::create_dir_all(parent).is_err() {
            return false;
        }
    }
    let temp_path = format!("{}.tmp", path);
    if std::fs::write(&temp_path, value).is_err() {
        let _ = std::fs::remove_file(&temp_path);
        return false;
    }
    let _ = std::fs::set_permissions(&temp_path, std::fs::Permissions::from_mode(0o644));
    match std::fs::rename(&temp_path, path) {
        Ok(()) => true,
        Err(_) => {
            let _ = std::fs::remove_file(&temp_path);
            false
        }
    }
}

fn publish_pid() {
    let pid = format!("{}\n", std::process::id());
    if !write_runtime_file(PID_FILE_PATH, &pid) {
        log!("[WARN] PID file unavailable; manager may not detect daemon");
    }
    let _ = write_runtime_file(LEGACY_PID_FILE_PATH, &pid);
}

fn publish_heartbeat() {
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis())
        .unwrap_or(0)
        .to_string();
    let _ = write_runtime_file(HEARTBEAT_FILE_PATH, &timestamp);
    let _ = write_runtime_file(LEGACY_HEARTBEAT_FILE_PATH, &timestamp);
    let _ = std::process::Command::new("setprop")
        .args(["sys.lumina.alive", &timestamp])
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .status();
}

fn cleanup_runtime_files() {
    for path in [
        PID_FILE_PATH,
        HEARTBEAT_FILE_PATH,
        LEGACY_PID_FILE_PATH,
        LEGACY_HEARTBEAT_FILE_PATH,
        "/data/adb/.config/lumina/fps.txt",
        "/data/local/tmp/lumina.fps",
        "/data/data/com.luminatweaks/files/lumina.fps",
    ] {
        let _ = std::fs::remove_file(path);
        let _ = std::fs::remove_file(format!("{}.tmp", path));
    }
}

fn publish_selinux_status() {
    let status = match std::fs::read_to_string("/sys/fs/selinux/enforce") {
        Ok(value) => match value.trim() {
            "1" => "Enforcing",
            "0" => "Permissive",
            _ => "Unknown",
        },
        Err(_) => "Disabled",
    };
    let _ = Command::new("setprop")
        .args(["sys.lumina.selinux", status])
        .status();
}

struct EngineRuntime {
    current_profile: Option<Profile>,
    current_lite: bool,
    manual_profile: Option<Profile>,
    active_target_pkg: Option<String>,
    active_target_pid: i32,
    target_game_pid: i32,
    current_applied_rr: Option<i32>,
    current_applied_renderer: String,
    skipped_renderer_notice_pkg: Option<String>,
    last_config_hash: Option<u64>,
    left_foreground_at: Option<Instant>,
    dnd_override: bool,
    prev_zen_mode: String,
    last_config_poll: Instant,
    low_power: bool,
    shared_state: Arc<SharedState>,
}

impl EngineRuntime {
    fn new(shared_state: Arc<SharedState>) -> Self {
        set_system_profile_prop("Balanced");
        TextureController::ensure_setup();

        Self {
            current_profile: None,
            current_lite: false,
            manual_profile: read_saved_manual_profile(),
            active_target_pkg: None,
            active_target_pid: 0,
            target_game_pid: 0,
            current_applied_rr: display::get_saved_mode_id(),
            current_applied_renderer: renderer::get_saved_renderer(),
            skipped_renderer_notice_pkg: None,
            last_config_hash: Some(config_hash()),
            left_foreground_at: None,
            dnd_override: false,
            prev_zen_mode: "0".to_string(),
            last_config_poll: Instant::now(),
            low_power: is_low_power_active(),
            shared_state,
        }
    }

    fn update_shared_state(&self, profile: Profile, lite: bool, target_pkg: Option<&str>) {
        if let Ok(mut p) = self.shared_state.current_profile.lock() {
            *p = profile.display_name(lite).to_string();
        }
        if let Ok(mut pkg) = self.shared_state.active_package.lock() {
            *pkg = target_pkg.map(|s| s.to_string());
        }
    }

    fn apply_target(
        &mut self,
        target_pkg: Option<&str>,
        binder: &mut binder_client::BinderClient,
        update_profile: bool,
    ) {
        let is_global_lite = is_lite_mode_active();
        let target_cfg = target_pkg.and_then(get_app_config);

        let desired_dnd = match target_cfg.as_ref() {
            Some(cfg) => match cfg.dnd.as_str() {
                "on" => true,
                "off" => false,
                _ => target_pkg.is_some() && is_auto_dnd_enabled(),
            },
            None => target_pkg.is_some() && is_auto_dnd_enabled(),
        };

        if desired_dnd && !self.dnd_override {
            self.prev_zen_mode = engine::get_zen_mode();
            set_dnd(binder, "priority");
            self.dnd_override = true;
            log!("[SYSTEM] DND ON (prev zen_mode={})", self.prev_zen_mode);
        } else if !desired_dnd && self.dnd_override {
            let restore = engine::zen_to_dnd_arg(&self.prev_zen_mode);
            set_dnd(binder, restore);
            self.dnd_override = false;
            log!("[SYSTEM] DND restored -> {}", restore);
        }

        if update_profile {
            let selected = target_cfg
                .as_ref()
                .and_then(|cfg| Profile::from_name(&cfg.profile));
            let target_profile = if let Some(profile) = selected {
                profile
            } else if target_cfg.is_some() {
                Profile::Maestro
            } else if self.low_power {
                Profile::Eco
            } else {
                Profile::Balance
            };
            let target_lite = match target_cfg.as_ref() {
                Some(cfg) => match cfg.lite_mode.as_str() {
                    "on" => true,
                    "off" => false,
                    _ => is_global_lite,
                },
                None => is_global_lite,
            };

            let is_profile_changing =
                self.current_profile != Some(target_profile) || self.current_lite != target_lite;

            if is_profile_changing {
                apply_profile(target_profile, target_lite);
                set_system_profile_prop(target_profile.display_name(target_lite));
                match target_profile {
                    Profile::Eco => {
                        log!("[POWER] ECO \u{2794} CPU powersave");
                    }
                    Profile::Balance => {
                        log!(
                            "[RESET]  {} ({}) \u{2794} BALANCED [Trigger: Inactive]",
                            target_pkg.unwrap_or("system"),
                            self.active_target_pid
                        );
                    }
                    Profile::Maestro => {
                        log!(
                            "[BOOST]  {} ({}) \u{2794} MAESTRO [EAS: Dynamic | Frame-Pacing: VSYNC{}]",
                            target_pkg.unwrap_or("system"),
                            self.active_target_pid,
                            if target_lite { " | Lite" } else { "" }
                        );
                    }
                    Profile::Performance => {
                        log!(
                            "[BOOST]  {} ({}) \u{2794} PERFORMANCE{} [Static Peak | Thermal Guard]",
                            target_pkg.unwrap_or("system"),
                            self.active_target_pid,
                            if target_lite { " LITE" } else { "" }
                        );
                    }
                }
                self.current_profile = Some(target_profile);
                self.current_lite = target_lite;
                self.update_shared_state(target_profile, target_lite, target_pkg);
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
            } else {
                display::apply_refresh_rate_id(-1);
                log!("[DISPLAY] Refresh rate reset to system default");
            }
            self.current_applied_rr = desired_rr;
        }

        let angle_active = target_pkg
            .map(TextureController::is_angle_active_for)
            .unwrap_or(false);

        let global_renderer = renderer::get_saved_renderer();
        let desired_renderer = if let Some(ref cfg) = target_cfg {
            if angle_active {
                if cfg.renderer != "default"
                    && !cfg.renderer.is_empty()
                    && self.skipped_renderer_notice_pkg.as_deref() != target_pkg
                {
                    log!(
                        "[RENDERER] Skipped custom renderer '{}' for {} (Texture ANGLE active)",
                        cfg.renderer,
                        target_pkg.unwrap_or("system")
                    );
                    self.skipped_renderer_notice_pkg = target_pkg.map(|s| s.to_string());
                }
                global_renderer
            } else if cfg.renderer != "default" && !cfg.renderer.is_empty() {
                self.skipped_renderer_notice_pkg = None;
                cfg.renderer.clone()
            } else {
                self.skipped_renderer_notice_pkg = None;
                global_renderer
            }
        } else {
            self.skipped_renderer_notice_pkg = None;
            global_renderer
        };

        if desired_renderer != self.current_applied_renderer {
            renderer::setrender(&desired_renderer);
            log!("[RENDERER] Graphics backend applied: {}", desired_renderer);
            self.current_applied_renderer = desired_renderer;
        }
    }

    fn apply_manual_profile(&mut self, trigger: &str) {
        let profile = self.manual_profile.unwrap_or(Profile::Balance);
        let lite = is_lite_mode_active();
        if self.current_profile == Some(profile) && self.current_lite == lite {
            return;
        }
        apply_profile(profile, lite);
        self.current_profile = Some(profile);
        self.current_lite = lite;
        set_system_profile_prop(profile.display_name(lite));
        self.update_shared_state(profile, lite, self.active_target_pkg.as_deref());
        log!(
            "[PROFILE] Manual mode -> {} [{}]",
            profile.display_name(lite),
            trigger
        );
    }

    fn reset_to_balance(&mut self, binder: &mut binder_client::BinderClient) {
        self.reset_to_balance_triggered("Inactive", binder);
    }

    fn reset_to_balance_triggered(
        &mut self,
        trigger: &str,
        binder: &mut binder_client::BinderClient,
    ) {
        if self.dnd_override {
            let restore = engine::zen_to_dnd_arg(&self.prev_zen_mode);
            set_dnd(binder, restore);
            self.dnd_override = false;
            log!("[SYSTEM] DND restored -> {}", restore);
        }

        if self.current_profile != Some(Profile::Balance) || self.current_lite {
            engine::balance_profile();
            set_system_profile_prop("Balanced");
            log!(
                "[RESET]  {} ({}) \u{2794} BALANCED [Trigger: {}]",
                self.active_target_pkg.as_deref().unwrap_or("system"),
                self.active_target_pid,
                trigger
            );
        }

        self.current_profile = Some(Profile::Balance);
        self.current_lite = false;
        self.left_foreground_at = None;
        self.skipped_renderer_notice_pkg = None;
        self.active_target_pkg = None;
        self.active_target_pid = 0;
        self.target_game_pid = 0;
        self.update_shared_state(Profile::Balance, false, None);

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

fn main() {
    if std::env::args_os().any(|arg| arg == "--restore-all") {
        tweaks::state::restore_all();
        engine::restore_boot_profile();
        return;
    }
    if std::env::args_os().any(|arg| arg == "--restore-boot") {
        engine::restore_boot_profile();
        return;
    }

    let _instance_lock = match acquire_instance_lock() {
        Some(lock) => lock,
        None => {
            log!("[SYSTEM] Another luminad instance is already active, exiting");
            return;
        }
    };

    unsafe {
        signal(SIGINT, handle_signal);
        signal(SIGTERM, handle_signal);
    }

    publish_pid();
    publish_heartbeat();
    publish_selinux_status();

    log!(
        "[SYSTEM] Online | v{} build {} | Kernel {} | Binder Socket pending",
        env!("CARGO_PKG_VERSION"),
        option_env!("LUMINA_BUILD_ID").unwrap_or("dev"),
        kernel_release()
    );
    sched::tune_daemon();
    display::init_display_subsystem();
    renderer::init_renderer_subsystem();
    tweaks::state::restore_all();
    engine::init_baseline_governor();
    engine::sync_global_tweaks();
    engine::apply_boot_profile();
    soc::vendor();
    if !crate::tweaks::mitigation::active_items().is_empty() {
        log!("[MITIGATION] {}", crate::tweaks::mitigation::summary());
    }

    let shared_state = Arc::new(SharedState::new());
    IpcServer::start(Arc::clone(&shared_state));

    let mut utility_ctrl = UtilityController::new();

    log!("[DAEMON] Subsystems online, entering main event loop");
    let mut runtime = EngineRuntime::new(Arc::clone(&shared_state));
    if !detector::is_auto_profile_enabled() {
        if let Some(profile) = runtime.manual_profile {
            let lite = is_lite_mode_active();
            apply_profile(profile, lite);
            runtime.current_profile = Some(profile);
            runtime.current_lite = lite;
            runtime.update_shared_state(profile, lite, None);
            set_system_profile_prop(profile.display_name(lite));
            log!(
                "[PROFILE] Manual mode active -> {}",
                profile.display_name(lite)
            );
        }
    }
    let mut binder_client = binder_client::BinderClient::new();
    let _ = binder_client.poll(Duration::from_millis(200));
    if binder_client.is_push() {
        log!("[SYSTEM] Binder Socket Linked | Event stream active");
    } else if binder_client.ping() {
        log!("[SYSTEM] Binder Socket Linked | Legacy protocol");
    } else {
        log!("[SYSTEM] Binder Socket Unavailable | cgroup fallback");
    }
    let mut config_watcher = watcher::ConfigWatcher::new();
    let mut last_pkg_for_texture: Option<String> = None;
    let mut fallback_blocked_until = Instant::now();
    let mut last_screen_state: Option<bool> = None;
    let mut fps_monitor = fps::FpsMonitor::new();
    let mut fas_controller = fas::FasController::new(fps_monitor.tap());
    let mut thermal_monitor = thermal::ThermalMonitor::new();
    let mut performance_guard = false;
    let mut session_recorder = session::SessionRecorder::new(fps_monitor.tap());
    let mut last_heartbeat = Instant::now();
    let mut reload_pending_at: Option<Instant> = None;
    let mut reload_force = false;

    loop {
        if SHUTDOWN.load(Ordering::Relaxed) {
            log!("[SYSTEM] Shutdown signal | Restoring baseline");
            runtime.reset_to_balance(&mut binder_client);
            engine::restore_balance();
            utility_ctrl.restore();
            TextureController::restore();
            session_recorder.shutdown();
            fps_monitor.stop();
            core::ipc::cleanup_socket();
            cleanup_runtime_files();
            break;
        }

        if last_heartbeat.elapsed() >= HEARTBEAT_INTERVAL {
            publish_heartbeat();
            last_heartbeat = Instant::now();
        }

        let auto_profile = detector::is_auto_profile_enabled();
        if let Some(name) = shared_state.take_requested_profile() {
            if let Some(profile) = Profile::from_name(&name) {
                let lite = is_lite_mode_active();
                apply_profile(profile, lite);
                runtime.manual_profile = Some(profile);
                runtime.current_profile = Some(profile);
                runtime.current_lite = lite;
                persist_manual_profile(profile);
                set_system_profile_prop(profile.display_name(lite));
                runtime.update_shared_state(profile, lite, runtime.active_target_pkg.as_deref());
                log!(
                    "[PROFILE] Manual selection -> {}",
                    profile.display_name(lite)
                );
            }
        }

        let loop_started = Instant::now();
        let screen_awake = binder_client
            .screen_state()
            .unwrap_or_else(UtilityController::is_screen_on);
        if last_screen_state != Some(screen_awake) {
            if screen_awake {
                match display::saved_mode_hz() {
                    Some(hz) => log!("[POWER]  Screen ON ({}Hz) \u{2794} Workers resumed", hz),
                    None => log!("[POWER]  Screen ON \u{2794} Workers resumed"),
                }
            } else {
                log!("[POWER]  Screen OFF \u{2794} Background workers paused");
                fps_monitor.observe(None, false, 0, None);
            }
            last_screen_state = Some(screen_awake);
        }
        utility_ctrl.tick(screen_awake);
        runtime.low_power = binder_client
            .power_save_state()
            .unwrap_or_else(is_low_power_active);

        let reload_requested = shared_state.reload_flag.swap(false, Ordering::Relaxed);
        let files_changed = config_watcher.poll_changed();
        if reload_requested || files_changed {
            reload_pending_at = Some(Instant::now() + Duration::from_millis(300));
            reload_force |= reload_requested;
        }

        let reload_due = reload_pending_at
            .map(|deadline| Instant::now() >= deadline)
            .unwrap_or(false);

        if reload_due {
            reload_pending_at = None;
            runtime.last_config_poll = Instant::now();
            let hash = config_hash();
            if reload_force || runtime.last_config_hash != Some(hash) {
                runtime.last_config_hash = Some(hash);
                log!("[SYSTEM] Config reload applied");
                detector::invalidate_config_cache();
                engine::sync_global_tweaks();
                if !detector::is_auto_profile_enabled() {
                    runtime.apply_manual_profile("Config reload");
                }
            }
            reload_force = false;
        } else if reload_pending_at.is_none()
            && runtime.last_config_poll.elapsed() >= Duration::from_secs(2)
        {
            runtime.last_config_poll = Instant::now();
            let hash = config_hash();
            if runtime.last_config_hash != Some(hash) {
                runtime.last_config_hash = Some(hash);
                log!("[SYSTEM] Config reload applied");
                detector::invalidate_config_cache();
                engine::sync_global_tweaks();
                if !detector::is_auto_profile_enabled() {
                    runtime.apply_manual_profile("Config reload");
                }
            }
        }

        let focus_now = binder_client.active_focus().cloned();
        let active_pid = focus_now.as_ref().map(|focus| focus.pid).unwrap_or(0);
        let active_pkg = runtime.active_target_pkg.clone();
        let mut suppress_fallback = false;

        while let Some(died) = binder_client.take_died() {
            let matches_target = active_pkg.as_deref().is_some()
                && (active_pkg.as_deref() == died.package.as_deref()
                    || (runtime.target_game_pid > 0 && runtime.target_game_pid == died.pid)
                    || (active_pid > 0 && active_pid == died.pid));

            let confirmed_dead = died.pid <= 0
                || !detector::pid_matches_package(
                    died.pid,
                    died.package.as_deref().unwrap_or(""),
                );

            if matches_target && confirmed_dead {
                if auto_profile {
                    log!(
                        "[RESET]  {} ({}) \u{2794} BALANCED [Trigger: Died 0ms | uid {}]",
                        active_pkg.as_deref().or(died.package.as_deref()).unwrap_or("-"),
                        died.pid,
                        died.uid
                    );
                    runtime.reset_to_balance_triggered("Died 0ms", &mut binder_client);
                } else {
                    runtime.left_foreground_at = None;
                    runtime.target_game_pid = 0;
                }
                fps_monitor.observe(None, false, 0, None);
                TextureController::sync_config(None);
                last_pkg_for_texture = None;
                suppress_fallback = true;
            }
        }

        if suppress_fallback {
            fallback_blocked_until = Instant::now() + Duration::from_millis(1500);
        }
        if Instant::now() < fallback_blocked_until {
            suppress_fallback = true;
        }

        let binder_focus = binder_client.focused_package();
        let focused = if screen_awake {
            match binder_focus {
                Some(package) if is_app_enabled(&package) => {
                    if runtime.active_target_pkg.as_deref() != Some(package.as_str()) {
                        let (pid, uid) = focus_now
                            .as_ref()
                            .map(|focus| (focus.pid, focus.uid))
                            .unwrap_or((0, 0));
                        log!("[SYSTEM] APP_FOCUS {} (pid={}, uid={})", package, pid, uid);
                    }
                    Some(package)
                }
                Some(_) | None if suppress_fallback => None,
                Some(_) | None => get_focused_package(),
            }
        } else {
            None
        };
        let focused = focused
            .map(|package| detector::sanitize_pkg_name(&package).to_string())
            .filter(|package| !package.is_empty());

        let current_target = if is_master_enabled() {
            let newly_focused_target = focused.as_deref().filter(|pkg| is_app_enabled(pkg));

            let target = if let Some(pkg) = newly_focused_target {
                let pkg_str = pkg.to_string();
                if runtime.left_foreground_at.is_some() {
                    log!("[SYSTEM] Focus restored -> {}", pkg_str);
                    runtime.left_foreground_at = None;
                }
                if active_pid > 0 {
                    runtime.active_target_pid = active_pid;
                }
                runtime.active_target_pkg = Some(pkg_str.clone());
                if active_pid > 0 {
                    runtime.target_game_pid = active_pid;
                }
                Some(pkg_str)
            } else if let Some(ref saved_pkg) = runtime.active_target_pkg {
                let verified_pid = if runtime.target_game_pid > 0
                    && detector::pid_matches_package(runtime.target_game_pid, saved_pkg)
                {
                    Some(runtime.target_game_pid)
                } else {
                    detector::find_pid_for_pkg(saved_pkg)
                };

                if let Some(pid) = verified_pid {
                    runtime.target_game_pid = pid;
                    if is_app_enabled(saved_pkg) {
                        if runtime.left_foreground_at.is_none() {
                            log!("[SYSTEM] Focus lost -> {} grace 15s", saved_pkg);
                            runtime.left_foreground_at = Some(Instant::now());
                        }

                        if let Some(left_time) = runtime.left_foreground_at {
                            if left_time.elapsed() < Duration::from_secs(15) {
                                Some(saved_pkg.clone())
                            } else {
                                log!("[SYSTEM] Grace expired -> {}", saved_pkg);
                                runtime.active_target_pkg = None;
                                runtime.target_game_pid = 0;
                                runtime.left_foreground_at = None;
                                None
                            }
                        } else {
                            None
                        }
                    } else {
                        runtime.active_target_pkg = None;
                        runtime.target_game_pid = 0;
                        runtime.left_foreground_at = None;
                        None
                    }
                } else if detector::process_exists(runtime.target_game_pid) {
                    if runtime.left_foreground_at.is_none() {
                        log!("[SYSTEM] Focus lost -> {} grace 15s", saved_pkg);
                        runtime.left_foreground_at = Some(Instant::now());
                    }

                    if let Some(left_time) = runtime.left_foreground_at {
                        if left_time.elapsed() < Duration::from_secs(15) {
                            Some(saved_pkg.clone())
                        } else {
                            log!("[SYSTEM] Grace expired -> {}", saved_pkg);
                            runtime.active_target_pkg = None;
                            runtime.target_game_pid = 0;
                            runtime.left_foreground_at = None;
                            None
                        }
                    } else {
                        None
                    }
                } else {
                    log!("[SYSTEM] Process {} gone -> BALANCED", saved_pkg);
                    runtime.active_target_pkg = None;
                    runtime.target_game_pid = 0;
                    runtime.left_foreground_at = None;
                    None
                }
            } else {
                None
            };

            if target != last_pkg_for_texture {
                fas_controller.reset(target.as_deref());
                if let Some(ref pkg) = target {
                    TextureController::apply_foreground(pkg);
                    if TextureController::is_angle_active_for(pkg) {
                        TextureController::verify_runtime_driver(pkg);
                    }
                } else {
                    TextureController::sync_config(None);
                }
                last_pkg_for_texture = target.clone();
            }

            runtime.apply_target(target.as_deref(), &mut binder_client, auto_profile);
            target
        } else {
            runtime.reset_to_balance(&mut binder_client);
            TextureController::restore();
            last_pkg_for_texture = None;
            None
        };

        let preview_active = screen_awake && shared_state.fps_preview_active();
        let record_candidate =
            session_recorder.candidate(screen_awake, focused.as_deref());
        let fps_package = if screen_awake {
            current_target
                .clone()
                .or_else(|| record_candidate.clone())
                .or_else(|| {
                    if preview_active {
                        focused.clone()
                    } else {
                        None
                    }
                })
        } else {
            None
        };
        let fps_game_active =
            screen_awake && (current_target.is_some() || record_candidate.is_some());
        let fps_pid = if fps_game_active {
            fps_package
                .as_deref()
                .map(|pkg| {
                    if runtime.target_game_pid > 0
                        && detector::pid_matches_package(runtime.target_game_pid, pkg)
                    {
                        runtime.target_game_pid
                    } else {
                        detector::find_pid_for_pkg(pkg).unwrap_or(0)
                    }
                })
                .unwrap_or(0)
        } else {
            0
        };

        let profile_label = runtime
            .current_profile
            .unwrap_or(Profile::Balance)
            .display_name(runtime.current_lite);
        session_recorder.tick(screen_awake, focused.as_deref(), profile_label);
        let session_start_ms = session_recorder
            .active_start_ms()
            .filter(|(package, _)| fps_package.as_deref() == Some(package.as_str()))
            .map(|(_, start)| start);
        fps_monitor.observe(
            fps_package.as_deref(),
            fps_game_active,
            fps_pid,
            session_start_ms,
        );

        let thermal_pressure = thermal_monitor.sample();
        if runtime.current_profile == Some(Profile::Performance) {
            match thermal_pressure {
                thermal::ThermalPressure::Critical if !performance_guard => {
                    engine::performance_safety_relax();
                    performance_guard = true;
                    log!("[THERMAL] PERFORMANCE guard engaged | clocks relaxed");
                }
                thermal::ThermalPressure::Normal if performance_guard => {
                    apply_profile(Profile::Performance, runtime.current_lite);
                    performance_guard = false;
                    log!("[THERMAL] PERFORMANCE guard released | peak restored");
                }
                _ => {}
            }
        } else {
            performance_guard = false;
        }
        fas_controller.tick(
            runtime.current_profile == Some(Profile::Maestro),
            screen_awake && current_target.is_some(),
            display::saved_mode_hz().filter(|hz| *hz >= 30),
            runtime.current_lite,
            thermal_pressure,
        );

        let sleep_ms = if binder_client.is_push() {
            250
        } else if current_target.is_some() || record_candidate.is_some() {
            400
        } else {
            1200
        };

        perf::record(loop_started.elapsed());

        let timeout = Duration::from_millis(sleep_ms);
        let poll_started = Instant::now();
        let had_event = binder_client.poll(timeout);
        if !had_event && !binder_client.is_push() {
            let elapsed = poll_started.elapsed();
            if elapsed < timeout {
                thread::sleep(timeout - elapsed);
            }
        }
    }
}

#[cfg(test)]
mod profile_tests {
    use super::Profile;

    #[test]
    fn separates_performance_from_maestro() {
        assert_eq!(Profile::from_name("Performance"), Some(Profile::Performance));
        assert_eq!(Profile::from_name("performance"), Some(Profile::Performance));
        assert_eq!(
            Profile::from_name("Performance Lite"),
            Some(Profile::Performance)
        );
        assert_eq!(Profile::from_name("Maestro"), Some(Profile::Maestro));
        assert_eq!(Profile::Performance.as_str(), "Performance");
        assert_eq!(Profile::Performance.display_name(true), "Performance Lite");
    }
}
