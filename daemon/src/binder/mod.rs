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

//! Event-driven foreground app detection through ActivityManager's
//! `IProcessObserver` binder interface.
//!
//! The cgroup polling path in `detector.rs` stays untouched and remains the
//! fallback source of truth. This module only provides a faster foreground
//! hint; if libbinder, the resolver APK, or the transaction fails, Lumina
//! keeps running with polling.

mod ffi;
mod resolver;

use std::ffi::CString;
use std::os::raw::c_void;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Condvar, Mutex, OnceLock};
use std::time::{Duration, Instant};

use crate::detector::{is_ignored_package, sanitize_pkg_name};

use ffi::{AIBinder, AParcel, AStatus};

const RESOLVER_CANDIDATES: [&str; 4] = [
    "/data/adb/modules/luminatweaks/binder_resolver.apk",
    "/data/adb/modules/lumina_tweaks/binder_resolver.apk",
    "/data/adb/.config/lumina/binder_resolver.apk",
    "/data/local/tmp/binder_resolver.apk",
];

const ACTIVITY_DESCRIPTOR: &str = "android.app.IActivityManager";
const OBSERVER_DESCRIPTOR: &str = "android.app.IProcessObserver";
const FOCUS_TTL: Duration = Duration::from_secs(2);

static TX_FOREGROUND: AtomicU32 = AtomicU32::new(0);
static TX_DIED: AtomicU32 = AtomicU32::new(0);
static NOTIFIER: OnceLock<Arc<Notifier>> = OnceLock::new();

#[derive(Default)]
struct FocusState {
    package: Option<String>,
    pid: i32,
    last_event: Option<Instant>,
    event_seq: u64,
    consumed_seq: u64,
}

#[derive(Default)]
struct Notifier {
    state: Mutex<FocusState>,
    changed: Condvar,
}

pub struct BinderMonitor {
    notifier: Arc<Notifier>,
    running: Arc<AtomicBool>,
}

impl BinderMonitor {
    /// Starts the binder observer if the bundled resolver APK is present.
    /// Returns `None` when binder monitoring is unavailable; callers must keep
    /// using cgroup polling in that case.
    pub fn start() -> Option<Self> {
        let resolver = find_resolver()?;

        let notifier = Arc::new(Notifier::default());
        if NOTIFIER.set(Arc::clone(&notifier)).is_err() {
            return None;
        }

        let running = Arc::new(AtomicBool::new(true));
        let thread_running = Arc::clone(&running);

        std::thread::Builder::new()
            .name("binder-monitor".to_string())
            .spawn(move || run_monitor(resolver, thread_running))
            .ok()?;

        Some(Self { notifier, running })
    }

    /// Most recent binder-reported foreground package, validated against the
    /// live process. Returns `None` if the hint is stale or binder is idle.
    pub fn focused_package(&self) -> Option<String> {
        let mut state = lock_state(&self.notifier);

        let fresh = matches!(state.last_event, Some(at) if at.elapsed() < FOCUS_TTL);
        if !fresh {
            return None;
        }

        let package = state.package.clone()?;

        if !pid_matches(state.pid, &package) {
            state.package = None;
            state.pid = 0;
            return None;
        }

        Some(package)
    }

    /// Blocks until a binder event arrives or the timeout expires. Used by the
    /// daemon loop to react to foreground switches without extra polling.
    pub fn wait_for_event(&self, timeout: Duration) {
        let mut state = lock_state(&self.notifier);
        if state.event_seq != state.consumed_seq {
            state.consumed_seq = state.event_seq;
            return;
        }

        let (mut state, _) = match self.notifier.changed.wait_timeout(state, timeout) {
            Ok(result) => result,
            Err(poisoned) => poisoned.into_inner(),
        };

        if state.event_seq != state.consumed_seq {
            state.consumed_seq = state.event_seq;
        }
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::Relaxed);
        self.notifier.changed.notify_all();
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

fn run_monitor(resolver: PathBuf, running: Arc<AtomicBool>) {
    while running.load(Ordering::Relaxed) {
        let codes = match resolver::resolve(&resolver) {
            Some(codes) => codes,
            None => {
                crate::log!("[BINDER] Resolver unavailable, keeping cgroup polling");
                sleep_while_running(&running, Duration::from_secs(30));
                continue;
            }
        };

        crate::log!(
            "[BINDER] Transaction codes resolved (register={}, foreground={}, died={})",
            codes.register,
            codes.foreground,
            codes.died
        );

        if start_observer(&codes) {
            return;
        }

        crate::log!("[BINDER] Observer registration failed, retrying in 30s");
        sleep_while_running(&running, Duration::from_secs(30));
    }
}

fn sleep_while_running(running: &AtomicBool, total: Duration) {
    let step = Duration::from_millis(250);
    let mut waited = Duration::ZERO;
    while waited < total && running.load(Ordering::Relaxed) {
        std::thread::sleep(step);
        waited += step;
    }
}

fn start_observer(codes: &resolver::Codes) -> bool {
    let api = match ffi::api() {
        Some(api) => api,
        None => {
            crate::log!("[BINDER] libbinder_ndk.so unavailable");
            return false;
        }
    };

    let activity = ffi::wait_for_service(api, "activity");
    if activity.is_null() {
        crate::log!("[BINDER] ActivityManager service not found");
        return false;
    }

    let activity_descriptor = match CString::new(ACTIVITY_DESCRIPTOR) {
        Ok(value) => value,
        Err(_) => return false,
    };

    let activity_class = unsafe {
        (api.class_define)(
            activity_descriptor.as_ptr(),
            Some(on_create),
            Some(on_destroy),
            on_transact,
        )
    };
    if activity_class.is_null() {
        crate::log!("[BINDER] Failed to define IActivityManager class");
        return false;
    }
    if !unsafe { (api.associate_class)(activity, activity_class) } {
        crate::log!("[BINDER] Failed to associate IActivityManager class");
        return false;
    }

    let descriptor = match CString::new(OBSERVER_DESCRIPTOR) {
        Ok(value) => value,
        Err(_) => return false,
    };

    TX_FOREGROUND.store(codes.foreground, Ordering::Relaxed);
    TX_DIED.store(codes.died, Ordering::Relaxed);

    let class = unsafe {
        (api.class_define)(
            descriptor.as_ptr(),
            Some(on_create),
            Some(on_destroy),
            on_transact,
        )
    };
    if class.is_null() {
        crate::log!("[BINDER] Failed to define IProcessObserver class");
        return false;
    }

    let observer = unsafe { (api.binder_new)(class, std::ptr::null_mut()) };
    if observer.is_null() {
        crate::log!("[BINDER] Failed to allocate IProcessObserver binder");
        return false;
    }

    if !register_observer(api, activity, observer, codes.register) {
        crate::log!("[BINDER] registerProcessObserver transaction failed");
        return false;
    }

    crate::log!("[BINDER] IProcessObserver registered");
    unsafe {
        (api.start_thread_pool)();
        (api.join_thread_pool)();
    }
    true
}

fn register_observer(
    api: &ffi::Api,
    activity: *mut AIBinder,
    observer: *mut AIBinder,
    code: u32,
) -> bool {
    // Prefer the automatic header written by AIBinder_prepareTransaction.
    // Older libbinder_ndk builds require an explicit interface token instead.
    if try_register(api, activity, observer, code, false) {
        return true;
    }

    if api.write_interface_token.is_some() {
        return try_register(api, activity, observer, code, true);
    }

    false
}

fn try_register(
    api: &ffi::Api,
    activity: *mut AIBinder,
    observer: *mut AIBinder,
    code: u32,
    manual_token: bool,
) -> bool {
    let mut input: *mut AParcel = std::ptr::null_mut();
    if unsafe { (api.prepare_transaction)(activity, &mut input) } != ffi::STATUS_OK
        || input.is_null()
    {
        return false;
    }

    if manual_token {
        let Some(write_token) = api.write_interface_token else {
            unsafe { (api.parcel_delete)(input) };
            return false;
        };
        let Some(descriptor) = ffi::interface_token(ACTIVITY_DESCRIPTOR) else {
            unsafe { (api.parcel_delete)(input) };
            return false;
        };
        if unsafe { write_token(input, descriptor.as_ptr()) } != ffi::STATUS_OK {
            unsafe { (api.parcel_delete)(input) };
            return false;
        }
    }

    if unsafe { (api.write_strong_binder)(input, observer) } != ffi::STATUS_OK {
        unsafe { (api.parcel_delete)(input) };
        return false;
    }

    let mut output: *mut AParcel = std::ptr::null_mut();
    let status = unsafe { (api.transact)(activity, code, &mut input, &mut output, 0) };
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
                    crate::log!("[BINDER] registerProcessObserver rejected");
                } else {
                    let text = unsafe { std::ffi::CStr::from_ptr(description) }.to_string_lossy();
                    crate::log!("[BINDER] registerProcessObserver rejected: {}", text);
                    unsafe { (api.status_delete_description)(description) };
                }
            }
            unsafe { (api.status_delete)(reply) };
        }
        unsafe { (api.parcel_delete)(output) };
    }

    ok
}

unsafe extern "C" fn on_create(args: *mut c_void) -> *mut c_void {
    args
}

unsafe extern "C" fn on_destroy(_: *mut c_void) {}

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

        handle_foreground(pid, uid, foreground != 0);
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

    // Other IProcessObserver events (foreground services, process started)
    // carry no state we need; acknowledge them so binder does not complain.
    ffi::STATUS_OK
}

fn handle_foreground(pid: i32, uid: i32, foreground: bool) {
    let Some(notifier) = NOTIFIER.get() else {
        return;
    };
    if pid <= 0 {
        return;
    }

    if !foreground {
        clear_if_pid(notifier, pid);
        return;
    }

    let Some(package) = read_package(pid) else {
        // Foreground moved to a package we do not track (launcher, SystemUI,
        // etc.). Drop the previous hint so the daemon can start its grace path.
        clear_current(notifier);
        return;
    };

    let mut state = lock_state(notifier);
    if state.pid == pid && state.package.as_deref() == Some(package.as_str()) {
        return;
    }

    state.package = Some(package.clone());
    state.pid = pid;
    state.last_event = Some(Instant::now());
    state.event_seq = state.event_seq.wrapping_add(1);
    crate::log!("[BINDER] Foreground: {} (pid={}, uid={})", package, pid, uid);
    notifier.changed.notify_all();
}

fn handle_died(pid: i32, _uid: i32) {
    let Some(notifier) = NOTIFIER.get() else {
        return;
    };
    if pid > 0 {
        clear_if_pid(notifier, pid);
    }
}

fn clear_if_pid(notifier: &Notifier, pid: i32) {
    let mut state = lock_state(notifier);
    if state.pid != pid {
        return;
    }
    clear_state(&mut state);
    notifier.changed.notify_all();
}

fn clear_current(notifier: &Notifier) {
    let mut state = lock_state(notifier);
    if state.package.is_none() {
        return;
    }
    clear_state(&mut state);
    notifier.changed.notify_all();
}

fn clear_state(state: &mut FocusState) {
    state.package = None;
    state.pid = 0;
    state.last_event = None;
    state.event_seq = state.event_seq.wrapping_add(1);
}

fn read_package(pid: i32) -> Option<String> {
    let bytes = std::fs::read(format!("/proc/{}/cmdline", pid)).ok()?;
    let end = bytes
        .iter()
        .position(|&byte| byte == 0)
        .unwrap_or(bytes.len());
    let raw = std::str::from_utf8(&bytes[..end]).ok()?;
    let package = sanitize_pkg_name(raw);

    if package.is_empty()
        || package.starts_with('/')
        || !package.contains('.')
        || is_ignored_package(package)
    {
        return None;
    }

    Some(package.to_string())
}

fn pid_matches(pid: i32, package: &str) -> bool {
    if pid <= 0 {
        return false;
    }

    match read_package(pid) {
        Some(current) => current == package,
        None => false,
    }
}

fn lock_state(notifier: &Notifier) -> std::sync::MutexGuard<'_, FocusState> {
    match notifier.state.lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    }
}
