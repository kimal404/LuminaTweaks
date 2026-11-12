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

#[macro_use]
mod core;
mod binder;
mod tweaks;

use binder::client as binder_client;
use core::detector::{
    get_app_config, get_focused_package, is_app_enabled, is_auto_dnd_enabled, is_lite_mode_active,
    is_low_power_active, is_master_enabled, is_process_alive,
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
use tweaks::{display, engine, fps, renderer, sched};

static SHUTDOWN: AtomicBool = AtomicBool::new(false);

extern "C" {
    fn signal(signum: i32, handler: extern "C" fn(i32)) -> usize;
    fn flock(fd: i32, operation: i32) -> i32;
}

const SIGINT: i32 = 2;
const SIGTERM: i32 = 15;
const LOCK_EX: i32 = 2;
const LOCK_NB: i32 = 4;

const PID_FILE_PATH: &str = "/data/local/tmp/lumina.pid";
const HEARTBEAT_FILE_PATH: &str = "/data/local/tmp/lumina.alive";
const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(2);

extern "C" fn handle_signal(_: i32) {
    SHUTDOWN.store(true, Ordering::Relaxed);
}

fn acquire_instance_lock() -> Option<File> {
    for lock_path in ["/dev/socket/luminad.lock", "/data/local/tmp/luminad.lock"] {
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

fn write_runtime_file(path: &str, value: &str) -> bool {
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
    if !write_runtime_file(PID_FILE_PATH, &format!("{}\n", std::process::id())) {
        log!("[WARN] PID file unavailable; manager may not detect daemon");
    }
}

fn publish_heartbeat() {
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_millis())
        .unwrap_or(0);
    let _ = write_runtime_file(HEARTBEAT_FILE_PATH, &timestamp.to_string());
}

fn cleanup_runtime_files() {
    for path in [PID_FILE_PATH, HEARTBEAT_FILE_PATH] {
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
    shared_state: Arc<SharedState>,
}

impl EngineRuntime {
    fn new(shared_state: Arc<SharedState>) -> Self {
        set_system_profile_prop("Balanced");
        TextureController::ensure_setup();

        Self {
            current_profile: None,
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
            engine::set_dnd_mode("priority");
            self.dnd_override = true;
            log!("[SYSTEM] DND ON (prev zen_mode={})", self.prev_zen_mode);
        } else if !desired_dnd && self.dnd_override {
            let restore = engine::zen_to_dnd_arg(&self.prev_zen_mode);
            engine::set_dnd_mode(restore);
            self.dnd_override = false;
            log!("[SYSTEM] DND restored -> {}", restore);
        }

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
                    log!(
                        "[BOOST]  {} ({}) \u{2794} FULL_PERF [CPU: 100% | Sched: 80%]",
                        target_pkg.unwrap_or("system"),
                        self.active_target_pid
                    );
                }
                Profile::PerformanceLite => {
                    engine::performance_lite_profile();
                    set_system_profile_prop("Performance Lite");
                    log!(
                        "[BOOST]  {} ({}) \u{2794} FULL_PERF_LITE [CPU: 72% | Sched: 0%]",
                        target_pkg.unwrap_or("system"),
                        self.active_target_pid
                    );
                }
                Profile::Eco => {
                    engine::eco_profile();
                    set_system_profile_prop("Eco");
                    log!("[POWER] ECO \u{2794} CPU powersave");
                }
                Profile::Balance => {
                    engine::balance_profile();
                    set_system_profile_prop("Balanced");
                    log!(
                        "[RESET]  {} ({}) \u{2794} BALANCED [Trigger: Inactive]",
                        target_pkg.unwrap_or("system"),
                        self.active_target_pid
                    );
                }
            }
            self.current_profile = Some(target_profile);
            self.update_shared_state(target_profile, target_pkg);
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

    fn reset_to_balance(&mut self) {
        self.reset_to_balance_triggered("Inactive");
    }

    fn reset_to_balance_triggered(&mut self, trigger: &str) {
        if self.dnd_override {
            let restore = engine::zen_to_dnd_arg(&self.prev_zen_mode);
            engine::set_dnd_mode(restore);
            self.dnd_override = false;
            log!("[SYSTEM] DND restored -> {}", restore);
        }

        if self.current_profile != Some(Profile::Balance) {
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
        self.left_foreground_at = None;
        self.skipped_renderer_notice_pkg = None;
        self.active_target_pkg = None;
        self.active_target_pid = 0;
        self.target_game_pid = 0;
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
        "[SYSTEM] Online | Kernel {} | Binder Socket pending",
        kernel_release()
    );
    sched::tune_daemon();
    display::init_display_subsystem();
    renderer::init_renderer_subsystem();
    tweaks::state::restore_all();
    engine::sync_global_tweaks();
    engine::apply_boot_profile();

    let shared_state = Arc::new(SharedState::new());
    IpcServer::start(Arc::clone(&shared_state));

    let mut utility_ctrl = UtilityController::new();

    log!("[DAEMON] Subsystems online, entering main event loop");
    let mut runtime = EngineRuntime::new(Arc::clone(&shared_state));
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
    let mut last_heartbeat = Instant::now();

    loop {
        if SHUTDOWN.load(Ordering::Relaxed) {
            log!("[SYSTEM] Shutdown signal | Restoring baseline");
            runtime.reset_to_balance();
            engine::restore_balance();
            utility_ctrl.restore();
            TextureController::restore();
            fps_monitor.stop();
            core::ipc::cleanup_socket();
            cleanup_runtime_files();
            break;
        }

        if last_heartbeat.elapsed() >= HEARTBEAT_INTERVAL {
            publish_heartbeat();
            last_heartbeat = Instant::now();
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
                fps_monitor.observe(None);
            }
            last_screen_state = Some(screen_awake);
        }
        utility_ctrl.tick(screen_awake);

        let reload_requested = shared_state.reload_flag.swap(false, Ordering::Relaxed);
        let files_changed = config_watcher.poll_changed();

        if reload_requested
            || files_changed
            || runtime.last_config_poll.elapsed() >= Duration::from_secs(2)
        {
            runtime.last_config_poll = Instant::now();
            let hash = config_hash();
            if reload_requested || runtime.last_config_hash != Some(hash) {
                runtime.last_config_hash = Some(hash);
                log!("[SYSTEM] Config reload applied");
                detector::invalidate_config_cache();
                engine::sync_global_tweaks();
            }
        }

        let active_pid = binder_client
            .active_focus()
            .map(|focus| focus.pid)
            .unwrap_or(0);
        runtime.active_target_pid = active_pid;
        let active_pkg = runtime.active_target_pkg.clone();
        let mut suppress_fallback = false;

        while let Some(died) = binder_client.take_died() {
            let matches_target = active_pkg.as_deref().is_some()
                && (active_pkg.as_deref() == died.package.as_deref()
                    || (runtime.target_game_pid > 0 && runtime.target_game_pid == died.pid)
                    || (active_pid > 0 && active_pid == died.pid));

            if matches_target {
                log!(
                    "[RESET]  {} ({}) \u{2794} BALANCED [Trigger: Died 0ms | uid {}]",
                    active_pkg.as_deref().or(died.package.as_deref()).unwrap_or("-"),
                    died.pid,
                    died.uid
                );
                runtime.left_foreground_at = None;
                runtime.target_game_pid = 0;
                runtime.reset_to_balance_triggered("Died 0ms");
                fps_monitor.observe(None);
                TextureController::sync_config(None);
                last_pkg_for_texture = None;
                suppress_fallback = true;
            } else {
                log!(
                    "[SYSTEM] APP_DIED {} ({}) uid {} ignored",
                    died.package.as_deref().unwrap_or("-"),
                    died.pid,
                    died.uid
                );
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
                        let pid = binder_client
                            .active_focus()
                            .map(|focus| focus.pid)
                            .unwrap_or(0);
                        let uid = binder_client
                            .active_focus()
                            .map(|focus| focus.uid)
                            .unwrap_or(0);
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

        let current_target = if is_master_enabled() {
            let newly_focused_target = focused.as_deref().filter(|pkg| is_app_enabled(pkg));

            let target = if let Some(pkg) = newly_focused_target {
                let pkg_str = pkg.to_string();
                if runtime.left_foreground_at.is_some() {
                    log!("[SYSTEM] Focus restored -> {}", pkg_str);
                    runtime.left_foreground_at = None;
                }
                runtime.active_target_pkg = Some(pkg_str.clone());
                if active_pid > 0 {
                    runtime.target_game_pid = active_pid;
                }
                Some(pkg_str)
            } else if let Some(ref saved_pkg) = runtime.active_target_pkg {
                if !is_process_alive(saved_pkg) {
                    log!("[SYSTEM] Process {} gone -> BALANCED", saved_pkg);
                    runtime.active_target_pkg = None;
                    runtime.target_game_pid = 0;
                    runtime.left_foreground_at = None;
                    None
                } else if is_app_enabled(saved_pkg) {
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
            } else {
                None
            };

            if target != last_pkg_for_texture {
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
            TextureController::restore();
            last_pkg_for_texture = None;
            None
        };

        fps_monitor.observe(current_target.as_deref());

        let sleep_ms = if binder_client.is_push() {
            250
        } else if current_target.is_some() {
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
