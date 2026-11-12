/*
 * Original work Copyright (c) 2024-2026 Rem01Gaming (Encore Tweaks)
 * https://github.com/Rem01Gaming/encore
 *
 * Binder `IProcessObserver` monitoring adapted for LuminaTweaks.
 * The observer registration approach and runtime transaction-code resolution
 * follow Encore Tweaks' BinderMonitor. Licensed under the Apache License 2.0.
 *
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

//! Event-driven foreground, process-death, and display monitoring through
//! Android's binder interfaces.
//!
//! Binder callbacks only update `producer::Shared` and enqueue events. The
//! socket writer in `binder_ipc.rs` performs all I/O on its own thread. The
//! cgroup polling path in `detector.rs` remains the fallback when binder is
//! unavailable.

mod ffi;
pub mod producer;
mod resolver;
pub mod client;
pub mod ipc;
pub mod protocol;

use std::ffi::CString;
use std::os::raw::c_void;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, OnceLock};
use std::time::{Duration, Instant};

use crate::core::detector::sanitize_pkg_name;

use ffi::{AIBinder, AParcel, AStatus};
use producer::{Death, Event, Focus, Shared};

const RESOLVER_CANDIDATES: [&str; 4] = [
    "/data/adb/modules/luminatweaks/binder_resolver.apk",
    "/data/adb/modules/lumina_tweaks/binder_resolver.apk",
    "/data/adb/.config/lumina/binder_resolver.apk",
    "/data/local/tmp/binder_resolver.apk",
];

const ACTIVITY_DESCRIPTOR: &str = "android.app.IActivityManager";
const OBSERVER_DESCRIPTOR: &str = "android.app.IProcessObserver";
const DISPLAY_DESCRIPTOR: &str = "android.hardware.display.IDisplayManager";
const DISPLAY_CALLBACK_DESCRIPTOR: &str = "android.hardware.display.IDisplayManagerCallback";
const POWER_DESCRIPTOR: &str = "android.os.IPowerManager";

const INITIAL_BACKOFF: Duration = Duration::from_secs(5);
const MAX_BACKOFF: Duration = Duration::from_secs(60);
const DEATH_POLL: Duration = Duration::from_millis(250);

static TX_FOREGROUND: AtomicU32 = AtomicU32::new(0);
static TX_DIED: AtomicU32 = AtomicU32::new(0);
static TX_DISPLAY_EVENT: AtomicU32 = AtomicU32::new(0);
static TX_INTERACTIVE: AtomicU32 = AtomicU32::new(0);
static SHARED: OnceLock<Arc<Shared>> = OnceLock::new();
static THREAD_POOL_STARTED: AtomicBool = AtomicBool::new(false);
static SCREEN_GENERATION: AtomicU64 = AtomicU64::new(0);

pub struct BinderMonitor {
    shared: Arc<Shared>,
    running: Arc<AtomicBool>,
}

impl BinderMonitor {
    /// Starts the binder monitor thread. The caller owns `shared` so the
    /// socket producer can start even while binder is still resolving.
    pub fn start(shared: Arc<Shared>) -> Option<Self> {
        let _ = SHARED.set(Arc::clone(&shared));

        let running = Arc::new(AtomicBool::new(true));
        let thread_running = Arc::clone(&running);
        let thread_shared = Arc::clone(&shared);

        std::thread::Builder::new()
            .name("binder-monitor".to_string())
            .spawn(move || run_monitor(thread_shared, thread_running))
            .ok()?;

        Some(Self { shared, running })
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::Relaxed);
        self.shared.wake();
    }
}

fn find_resolver() -> Option<PathBuf> {
    for candidate in RESOLVER_CANDIDATES {
        let path = Path::new(candidate);
        if path.is_file() {
            return Some(path.to_path_buf());
        }
    }

    if let Ok(exe) = std::fs::read_link("/proc/self/exe") {
        if let Some(dir) = exe.parent() {
            let sibling = dir.join("binder_resolver.apk");
            if sibling.is_file() {
                return Some(sibling);
            }
        }
    }

    None
}

fn run_monitor(shared: Arc<Shared>, running: Arc<AtomicBool>) {
    let mut backoff = INITIAL_BACKOFF;

    while running.load(Ordering::Relaxed) {
        let Some(resolver) = find_resolver() else {
            crate::log!(
                "[SYSTEM] Binder Resolver APK unavailable, retrying in {}s",
                backoff.as_secs()
            );
            sleep_while_running(&running, backoff);
            backoff = next_backoff(backoff);
            continue;
        };

        let codes = match resolver::resolve(&resolver) {
            Some(codes) => codes,
            None => {
                crate::log!(
                    "[SYSTEM] Binder Transaction codes unavailable, retrying in {}s",
                    backoff.as_secs()
                );
                sleep_while_running(&running, backoff);
                backoff = next_backoff(backoff);
                continue;
            }
        };

        crate::log!(
            "[SYSTEM] Binder Transaction codes resolved (register={}, foreground={}, died={}, display={})",
            codes.register,
            codes.foreground,
            codes.died,
            codes.display_register
        );

        shared.clear_dead();
        match attach(&codes, &shared) {
            Ok(handles) => {
                backoff = INITIAL_BACKOFF;
                crate::log!("[SYSTEM] Binder Observer registered");

                wait_for_death(&shared, &running, &handles);
                drop(handles);

                if running.load(Ordering::Relaxed) {
                    crate::log!("[SYSTEM] Binder Service died, re-resolving transaction codes");
                    shared.clear_focus_state();
                }
            }
            Err(error) => {
                crate::log!(
                    "[SYSTEM] Binder Attach failed: {}. Retrying in {}s",
                    error,
                    backoff.as_secs()
                );
                sleep_while_running(&running, backoff);
                backoff = next_backoff(backoff);
            }
        }
    }
}

fn next_backoff(current: Duration) -> Duration {
    let doubled = current.saturating_mul(2);
    if doubled > MAX_BACKOFF {
        MAX_BACKOFF
    } else {
        doubled
    }
}

fn sleep_while_running(running: &AtomicBool, total: Duration) {
    let deadline = Instant::now() + total;
    while Instant::now() < deadline && running.load(Ordering::Relaxed) {
        std::thread::sleep(Duration::from_millis(250));
    }
}

fn wait_for_death(shared: &Shared, running: &AtomicBool, handles: &Handles) {
    let alive = handles.api.is_alive;
    while running.load(Ordering::Relaxed) && !shared.is_dead() {
        std::thread::sleep(DEATH_POLL);
        if let Some(is_alive) = alive {
            let activity_ok = unsafe { is_alive(handles.activity) };
            let display_ok = handles.display.is_null() || unsafe { is_alive(handles.display) };
            let power_ok = handles.power.is_null() || unsafe { is_alive(handles.power) };
            if !activity_ok || !display_ok || !power_ok {
                crate::log!("[SYSTEM] Binder Service liveness check failed");
                shared.mark_dead();
                break;
            }
        }
    }
}

struct Handles {
    api: &'static ffi::Api,
    activity: *mut AIBinder,
    display: *mut AIBinder,
    power: *mut AIBinder,
    observer: *mut AIBinder,
    display_callback: *mut AIBinder,
    death: *mut c_void,
}

unsafe impl Send for Handles {}

impl Handles {
    fn new(api: &'static ffi::Api) -> Self {
        Self {
            api,
            activity: std::ptr::null_mut(),
            display: std::ptr::null_mut(),
            power: std::ptr::null_mut(),
            observer: std::ptr::null_mut(),
            display_callback: std::ptr::null_mut(),
            death: std::ptr::null_mut(),
        }
    }
}

impl Drop for Handles {
    fn drop(&mut self) {
        SCREEN_GENERATION.fetch_add(1, Ordering::Relaxed);
        if let Some(shared) = SHARED.get() {
            shared.request_screen();
        }
        unsafe {
            if !self.death.is_null() {
                if let Some(unlink) = self.api.unlink_from_death {
                    for binder in [self.activity, self.display, self.power] {
                        if !binder.is_null() {
                            let _ = unlink(binder, self.death, std::ptr::null_mut());
                        }
                    }
                }
            }
            for binder in [self.observer, self.display_callback] {
                if !binder.is_null() {
                    (self.api.dec_strong)(binder);
                }
            }
            for binder in [self.activity, self.display, self.power] {
                if !binder.is_null() {
                    (self.api.dec_strong)(binder);
                }
            }
            if !self.death.is_null() {
                if let Some(delete) = self.api.death_delete {
                    delete(self.death);
                }
            }
        }
    }
}

fn attach(codes: &resolver::Codes, shared: &Shared) -> Result<Handles, String> {
    let api = ffi::api().ok_or_else(|| "libbinder_ndk.so unavailable".to_string())?;
    let mut handles = Handles::new(api);

    handles.activity = ffi::wait_for_service(api, "activity");
    handles.display = ffi::wait_for_service(api, "display");
    handles.power = ffi::wait_for_service(api, "power");
    if handles.activity.is_null() {
        return Err("activity service missing".to_string());
    }
    if handles.display.is_null() {
        crate::log!("[SYSTEM] Binder display service missing; screen events use fallback");
    }
    if handles.power.is_null() {
        crate::log!("[SYSTEM] Binder power service missing; screen events use fallback");
    }

    if let (Some(new_death), Some(link)) = (api.death_new, api.link_to_death) {
        let death = unsafe { new_death(on_binder_died) };
        if death.is_null() {
            return Err("failed to allocate death recipient".to_string());
        }
        handles.death = death;
        for binder in [handles.activity, handles.display, handles.power] {
            if binder.is_null() {
                continue;
            }
            let status = unsafe { link(binder, death, std::ptr::null_mut()) };
            if status != ffi::STATUS_OK {
                crate::log!("[SYSTEM] Binder linkToDeath failed with status {}", status);
            }
        }
    } else {
        crate::log!("[SYSTEM] Binder death recipient API unavailable; relying on re-resolve");
    }

    TX_FOREGROUND.store(codes.foreground, Ordering::Relaxed);
    TX_DIED.store(codes.died, Ordering::Relaxed);
    TX_DISPLAY_EVENT.store(codes.display_event, Ordering::Relaxed);
    TX_INTERACTIVE.store(codes.is_interactive, Ordering::Relaxed);

    associate(api, handles.activity, ACTIVITY_DESCRIPTOR)?;
    if !handles.display.is_null() {
        associate(api, handles.display, DISPLAY_DESCRIPTOR)?;
    }
    if !handles.power.is_null() {
        associate(api, handles.power, POWER_DESCRIPTOR)?;
    }

    handles.observer = new_binder(api, OBSERVER_DESCRIPTOR, on_transact)?;
    if !register(
        api,
        handles.activity,
        ACTIVITY_DESCRIPTOR,
        handles.observer,
        codes.register,
    ) {
        return Err("registerProcessObserver rejected".to_string());
    }

    if !THREAD_POOL_STARTED.swap(true, Ordering::Relaxed) {
        unsafe { (api.start_thread_pool)() };
    }

    let mut display_registered = false;
    if !handles.display.is_null() && codes.display_register != 0 && codes.display_event != 0 {
        match new_binder(api, DISPLAY_CALLBACK_DESCRIPTOR, on_display_transact) {
            Ok(callback) => {
                if register(
                    api,
                    handles.display,
                    DISPLAY_DESCRIPTOR,
                    callback,
                    codes.display_register,
                ) {
                    handles.display_callback = callback;
                    display_registered = true;
                    crate::log!("[SYSTEM] Binder Display callback registered");
                } else {
                    crate::log!(
                        "[SYSTEM] Binder Display callback unavailable; polling screen state"
                    );
                    unsafe { (api.dec_strong)(callback) };
                }
            }
            Err(error) => {
                crate::log!(
                    "[SYSTEM] Binder Display callback allocation failed: {}; polling screen state",
                    error
                );
            }
        }
    }

    if codes.is_interactive != 0 {
        if !display_registered {
            crate::log!("[SYSTEM] Binder Screen state polled through IPowerManager");
        }
        start_screen_worker(api, codes.is_interactive, !display_registered);
    }

    if !handles.power.is_null() {
        let interactive = transact_read_int32(
            api,
            handles.power,
            codes.is_interactive,
            POWER_DESCRIPTOR,
            -1,
        );
        if interactive >= 0 {
            let on = interactive != 0;
            if shared.update_screen(on) {
                shared.push_event(Event::Screen(on));
            }
        }
    }

    Ok(handles)
}

fn associate(
    api: &'static ffi::Api,
    binder: *mut AIBinder,
    descriptor: &str,
) -> Result<(), String> {
    let name = CString::new(descriptor).map_err(|_| "descriptor contains NUL".to_string())?;
    let class = unsafe {
        (api.class_define)(
            name.as_ptr(),
            Some(on_create),
            Some(on_destroy),
            on_transact,
        )
    };
    if class.is_null() {
        return Err(format!("failed to define class {}", descriptor));
    }
    if !unsafe { (api.associate_class)(binder, class) } {
        return Err(format!("failed to associate class {}", descriptor));
    }
    Ok(())
}

fn new_binder(
    api: &'static ffi::Api,
    descriptor: &str,
    transact: ffi::OnTransact,
) -> Result<*mut AIBinder, String> {
    let name = CString::new(descriptor).map_err(|_| "descriptor contains NUL".to_string())?;
    let class =
        unsafe { (api.class_define)(name.as_ptr(), Some(on_create), Some(on_destroy), transact) };
    if class.is_null() {
        return Err(format!("failed to define class {}", descriptor));
    }
    let binder = unsafe { (api.binder_new)(class, std::ptr::null_mut()) };
    if binder.is_null() {
        return Err(format!("failed to allocate binder {}", descriptor));
    }
    Ok(binder)
}

fn register(
    api: &'static ffi::Api,
    service: *mut AIBinder,
    descriptor: &str,
    callback: *mut AIBinder,
    code: u32,
) -> bool {
    if try_register(api, service, descriptor, callback, code, false) {
        return true;
    }
    if api.write_interface_token.is_some() {
        return try_register(api, service, descriptor, callback, code, true);
    }
    false
}

fn try_register(
    api: &'static ffi::Api,
    service: *mut AIBinder,
    descriptor: &str,
    callback: *mut AIBinder,
    code: u32,
    manual_token: bool,
) -> bool {
    let mut input: *mut AParcel = std::ptr::null_mut();
    if unsafe { (api.prepare_transaction)(service, &mut input) } != ffi::STATUS_OK
        || input.is_null()
    {
        return false;
    }

    if manual_token {
        let Some(write_token) = api.write_interface_token else {
            unsafe { (api.parcel_delete)(input) };
            return false;
        };
        let Some(token) = ffi::interface_token(descriptor) else {
            unsafe { (api.parcel_delete)(input) };
            return false;
        };
        if unsafe { write_token(input, token.as_ptr()) } != ffi::STATUS_OK {
            unsafe { (api.parcel_delete)(input) };
            return false;
        }
    }

    if unsafe { (api.write_strong_binder)(input, callback) } != ffi::STATUS_OK {
        unsafe { (api.parcel_delete)(input) };
        return false;
    }

    let mut output: *mut AParcel = std::ptr::null_mut();
    let status = unsafe { (api.transact)(service, code, &mut input, &mut output, 0) };
    let mut ok = status == ffi::STATUS_OK;

    if !output.is_null() {
        let mut reply: *mut AStatus = std::ptr::null_mut();
        if unsafe { (api.read_status_header)(output, &mut reply) } == ffi::STATUS_OK
            && !reply.is_null()
        {
            if !unsafe { (api.status_is_ok)(reply) } {
                ok = false;
                let description = unsafe { (api.status_get_description)(reply) };
                if description.is_null() {
                    crate::log!("[SYSTEM] Binder {} registration rejected", descriptor);
                } else {
                    let text = unsafe { std::ffi::CStr::from_ptr(description) }.to_string_lossy();
                    crate::log!(
                        "[SYSTEM] Binder {} registration rejected: {}",
                        descriptor,
                        text
                    );
                    unsafe { (api.status_delete_description)(description) };
                }
            }
            unsafe { (api.status_delete)(reply) };
        }
        unsafe { (api.parcel_delete)(output) };
    }

    ok
}

fn transact_read_int32(
    api: &'static ffi::Api,
    service: *mut AIBinder,
    code: u32,
    descriptor: &str,
    on_error: i32,
) -> i32 {
    if code == 0 || service.is_null() {
        return on_error;
    }

    let mut input: *mut AParcel = std::ptr::null_mut();
    if unsafe { (api.prepare_transaction)(service, &mut input) } != ffi::STATUS_OK
        || input.is_null()
    {
        return on_error;
    }

    if let (Some(write_token), Some(token)) =
        (api.write_interface_token, ffi::interface_token(descriptor))
    {
        let _ = unsafe { write_token(input, token.as_ptr()) };
    }

    let mut output: *mut AParcel = std::ptr::null_mut();
    let status = unsafe { (api.transact)(service, code, &mut input, &mut output, 0) };
    if status != ffi::STATUS_OK {
        if !output.is_null() {
            unsafe { (api.parcel_delete)(output) };
        }
        return on_error;
    }

    let mut value = on_error;
    if !output.is_null() {
        let mut reply: *mut AStatus = std::ptr::null_mut();
        if unsafe { (api.read_status_header)(output, &mut reply) } == ffi::STATUS_OK
            && !reply.is_null()
        {
            if unsafe { (api.status_is_ok)(reply) } {
                let _ = unsafe { (api.read_int32)(output, &mut value) };
            }
            unsafe { (api.status_delete)(reply) };
        }
        unsafe { (api.parcel_delete)(output) };
    }
    value
}

fn start_screen_worker(api: &'static ffi::Api, code: u32, poll: bool) {
    if code == 0 {
        return;
    }
    let Some(shared) = SHARED.get().cloned() else {
        return;
    };

    let generation = SCREEN_GENERATION.fetch_add(1, Ordering::Relaxed) + 1;
    let _ = std::thread::Builder::new()
        .name("binder-screen".to_string())
        .spawn(move || {
            while SCREEN_GENERATION.load(Ordering::Relaxed) == generation && !shared.is_dead() {
                if poll {
                    shared.wait_screen_request(Duration::from_secs(2));
                } else {
                    shared.wait_screen_request(Duration::from_secs(5));
                }
                if SCREEN_GENERATION.load(Ordering::Relaxed) != generation || shared.is_dead() {
                    break;
                }

                let power = ffi::wait_for_service(api, "power");
                if power.is_null() {
                    continue;
                }
                let value = transact_read_int32(api, power, code, POWER_DESCRIPTOR, -1);
                unsafe { (api.dec_strong)(power) };
                if value < 0 {
                    continue;
                }
                let on = value != 0;
                if shared.update_screen(on) {
                    shared.push_event(Event::Screen(on));
                }
            }
        });
}

unsafe extern "C" fn on_create(args: *mut c_void) -> *mut c_void {
    args
}

unsafe extern "C" fn on_destroy(_: *mut c_void) {}

unsafe extern "C" fn on_binder_died(_cookie: *mut c_void) {
    if let Some(shared) = SHARED.get() {
        shared.mark_dead();
    }
}

unsafe extern "C" fn on_transact(
    _binder: *mut AIBinder,
    code: u32,
    input: *const AParcel,
    _output: *mut AParcel,
) -> i32 {
    let api = match ffi::api() {
        Some(api) => api,
        None => return ffi::STATUS_UNKNOWN_ERROR,
    };
    if input.is_null() {
        return ffi::STATUS_UNKNOWN_ERROR;
    }

    if code == TX_FOREGROUND.load(Ordering::Relaxed) {
        let mut pid = -1i32;
        let mut uid = -1i32;
        let mut foreground = 0u8;

        if unsafe { (api.read_int32)(input, &mut pid) } != ffi::STATUS_OK
            || unsafe { (api.read_int32)(input, &mut uid) } != ffi::STATUS_OK
            || unsafe { (api.read_bool)(input, &mut foreground) } != ffi::STATUS_OK
        {
            return ffi::STATUS_OK;
        }

        if foreground != 0 {
            handle_foreground(pid, uid);
        }
        return ffi::STATUS_OK;
    }

    if code == TX_DIED.load(Ordering::Relaxed) {
        let mut pid = -1i32;
        let mut uid = -1i32;

        if unsafe { (api.read_int32)(input, &mut pid) } == ffi::STATUS_OK
            && unsafe { (api.read_int32)(input, &mut uid) } == ffi::STATUS_OK
        {
            handle_died(pid, uid);
        }
        return ffi::STATUS_OK;
    }

    ffi::STATUS_OK
}

unsafe extern "C" fn on_display_transact(
    _binder: *mut AIBinder,
    code: u32,
    _input: *const AParcel,
    _output: *mut AParcel,
) -> i32 {
    let expected = TX_DISPLAY_EVENT.load(Ordering::Relaxed);
    if expected == 0 || code != expected {
        return ffi::STATUS_UNKNOWN_ERROR;
    }

    if let Some(shared) = SHARED.get() {
        shared.request_screen();
    }
    ffi::STATUS_OK
}

fn handle_foreground(pid: i32, uid: i32) {
    let Some(shared) = SHARED.get() else {
        return;
    };
    if pid <= 0 {
        return;
    }

    let Some(package) = read_package(pid) else {
        return;
    };

    let focus = Focus { package, pid, uid };
    if !shared.set_focus(focus.clone()) {
        return;
    }

    shared.push_event(Event::Focus(focus));
}

fn handle_died(pid: i32, uid: i32) {
    let Some(shared) = SHARED.get() else {
        return;
    };
    if pid <= 0 {
        return;
    }

    let package = shared.clear_focus(pid).map(|focus| focus.package);

    shared.push_event(Event::Died(Death { pid, uid, package }));
}

fn read_package(pid: i32) -> Option<String> {
    let bytes = std::fs::read(format!("/proc/{}/cmdline", pid)).ok()?;
    let end = bytes
        .iter()
        .position(|&byte| byte == 0)
        .unwrap_or(bytes.len());
    let raw = std::str::from_utf8(&bytes[..end]).ok()?;
    let package = sanitize_pkg_name(raw);

    if package.is_empty() || package.starts_with('/') || !package.contains('.') {
        return None;
    }

    Some(package.to_string())
}
