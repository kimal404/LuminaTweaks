/*
 * Original work Copyright (c) 2024-2026 Rem01Gaming (Encore Tweaks)
 * https://github.com/Rem01Gaming/encore
 * Licensed under the Apache License, Version 2.0.
 *
 * Minimal libbinder_ndk FFI for LuminaTweaks. The function signatures mirror
 * the NDK binder surface used by Encore's BinderNDK/BinderMonitor.
 *
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

use std::ffi::CString;
use std::os::raw::{c_char, c_int, c_void};
use std::sync::OnceLock;

pub const STATUS_OK: i32 = 0;
pub const STATUS_UNKNOWN_ERROR: i32 = i32::MIN;

const RTLD_NOW: c_int = 2;

#[repr(C)]
pub struct AIBinder {
    _private: [u8; 0],
}

#[repr(C)]
pub struct AIBinderClass {
    _private: [u8; 0],
}

#[repr(C)]
pub struct AParcel {
    _private: [u8; 0],
}

#[repr(C)]
pub struct AStatus {
    _private: [u8; 0],
}

pub type OnCreate = unsafe extern "C" fn(*mut c_void) -> *mut c_void;
pub type OnDestroy = unsafe extern "C" fn(*mut c_void);
pub type OnTransact = unsafe extern "C" fn(*mut AIBinder, u32, *const AParcel, *mut AParcel) -> i32;
pub type OnBinderDied = unsafe extern "C" fn(*mut c_void);

macro_rules! binder_symbols {
    ($(($name:ident, $symbol:literal, $ty:ty)),* $(,)?) => {
        pub struct Api {
            $(pub $name: $ty,)*
            pub write_interface_token: Option<unsafe extern "C" fn(*mut AParcel, *const c_char) -> i32>,
            pub write_string: Option<unsafe extern "C" fn(*mut AParcel, *const c_char, i32) -> i32>,
            pub death_new: Option<unsafe extern "C" fn(OnBinderDied) -> *mut c_void>,
            pub death_delete: Option<unsafe extern "C" fn(*mut c_void)>,
            pub link_to_death: Option<unsafe extern "C" fn(*mut AIBinder, *mut c_void, *mut c_void) -> i32>,
            pub unlink_from_death: Option<unsafe extern "C" fn(*mut AIBinder, *mut c_void, *mut c_void) -> i32>,
            pub is_alive: Option<unsafe extern "C" fn(*const AIBinder) -> bool>,
        }

        unsafe fn load() -> Option<Api> {
            let library = CString::new("libbinder_ndk.so").ok()?;
            let handle = dlopen(library.as_ptr(), RTLD_NOW);
            if handle.is_null() {
                crate::log!("[SYSTEM] Binder dlopen(libbinder_ndk.so) failed: {}", last_dl_error());
                return None;
            }
            let mut api = Api {
                $($name: resolve(handle, $symbol)?,)*
                write_interface_token: None,
                write_string: None,
                death_new: None,
                death_delete: None,
                link_to_death: None,
                unlink_from_death: None,
                is_alive: None,
            };
            // Removed from libbinder_ndk on newer Android; AIBinder_prepareTransaction
            // writes the interface header automatically there.
            api.write_interface_token = resolve_optional(handle, "AParcel_writeInterfaceToken");
            api.write_string = resolve_optional(handle, "AParcel_writeString");
            api.death_new = resolve_optional(handle, "AIBinder_DeathRecipient_new");
            api.death_delete = resolve_optional(handle, "AIBinder_DeathRecipient_delete");
            api.link_to_death = resolve_optional(handle, "AIBinder_linkToDeath");
            api.unlink_from_death = resolve_optional(handle, "AIBinder_unlinkToDeath");
            api.is_alive = resolve_optional(handle, "AIBinder_isAlive");
            Some(api)
        }
    };
}

binder_symbols! {
    (get_service, "AServiceManager_getService", unsafe extern "C" fn(*const c_char) -> *mut AIBinder),
    (class_define, "AIBinder_Class_define", unsafe extern "C" fn(
        *const c_char,
        Option<OnCreate>,
        Option<OnDestroy>,
        OnTransact,
    ) -> *mut AIBinderClass),
    (binder_new, "AIBinder_new", unsafe extern "C" fn(*const AIBinderClass, *mut c_void) -> *mut AIBinder),
    (associate_class, "AIBinder_associateClass", unsafe extern "C" fn(*mut AIBinder, *const AIBinderClass) -> bool),
    (prepare_transaction, "AIBinder_prepareTransaction", unsafe extern "C" fn(*mut AIBinder, *mut *mut AParcel) -> i32),
    (transact, "AIBinder_transact", unsafe extern "C" fn(
        *mut AIBinder,
        u32,
        *mut *mut AParcel,
        *mut *mut AParcel,
        u32,
    ) -> i32),
    (write_strong_binder, "AParcel_writeStrongBinder", unsafe extern "C" fn(*mut AParcel, *mut AIBinder) -> i32),
    (write_int32, "AParcel_writeInt32", unsafe extern "C" fn(*mut AParcel, i32) -> i32),
    (write_int64, "AParcel_writeInt64", unsafe extern "C" fn(*mut AParcel, i64) -> i32),
    (read_int32, "AParcel_readInt32", unsafe extern "C" fn(*const AParcel, *mut i32) -> i32),
    (read_status_header, "AParcel_readStatusHeader", unsafe extern "C" fn(*const AParcel, *mut *mut AStatus) -> i32),
    (status_is_ok, "AStatus_isOk", unsafe extern "C" fn(*const AStatus) -> bool),
    (status_get_description, "AStatus_getDescription", unsafe extern "C" fn(*const AStatus) -> *const c_char),
    (status_delete_description, "AStatus_deleteDescription", unsafe extern "C" fn(*const c_char)),
    (status_delete, "AStatus_delete", unsafe extern "C" fn(*mut AStatus)),
    (read_bool, "AParcel_readBool", unsafe extern "C" fn(*const AParcel, *mut u8) -> i32),
    (parcel_delete, "AParcel_delete", unsafe extern "C" fn(*mut AParcel)),
    (start_thread_pool, "ABinderProcess_startThreadPool", unsafe extern "C" fn()),
    (dec_strong, "AIBinder_decStrong", unsafe extern "C" fn(*mut AIBinder)),
}

extern "C" {
    fn dlopen(filename: *const c_char, flag: c_int) -> *mut c_void;
    fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
    fn dlerror() -> *mut c_char;
}

fn last_dl_error() -> String {
    unsafe {
        let message = dlerror();
        if message.is_null() {
            return String::new();
        }
        std::ffi::CStr::from_ptr(message)
            .to_string_lossy()
            .into_owned()
    }
}

static API: OnceLock<Option<Api>> = OnceLock::new();

pub fn api() -> Option<&'static Api> {
    API.get_or_init(|| unsafe { load() }).as_ref()
}

pub fn interface_token(name: &str) -> Option<CString> {
    CString::new(name).ok()
}

pub fn service_name(name: &str) -> Option<CString> {
    CString::new(name).ok()
}

unsafe fn resolve<T: Copy>(handle: *mut c_void, name: &str) -> Option<T> {
    let symbol = CString::new(name).ok()?;
    let pointer = dlsym(handle, symbol.as_ptr());
    if pointer.is_null() {
        crate::log!(
            "[SYSTEM] Binder Missing libbinder_ndk symbol: {} ({})",
            name,
            last_dl_error()
        );
        None
    } else {
        Some(std::mem::transmute_copy(&pointer))
    }
}

unsafe fn resolve_optional<T: Copy>(handle: *mut c_void, name: &str) -> Option<T> {
    let symbol = CString::new(name).ok()?;
    let pointer = dlsym(handle, symbol.as_ptr());
    if pointer.is_null() {
        None
    } else {
        Some(std::mem::transmute_copy(&pointer))
    }
}

pub fn wait_for_service(api: &Api, name: &str) -> *mut AIBinder {
    let Some(service) = service_name(name) else {
        return std::ptr::null_mut();
    };

    if let Some(wait) = WAIT_FOR_SERVICE
        .get_or_init(|| unsafe { load_wait_for_service() })
        .as_ref()
    {
        return unsafe { (*wait)(service.as_ptr()) };
    }

    for _ in 0..100 {
        let binder = unsafe { (api.get_service)(service.as_ptr()) };
        if !binder.is_null() {
            return binder;
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
    std::ptr::null_mut()
}

type WaitForService = unsafe extern "C" fn(*const c_char) -> *mut AIBinder;

static WAIT_FOR_SERVICE: OnceLock<Option<WaitForService>> = OnceLock::new();

unsafe fn load_wait_for_service() -> Option<WaitForService> {
    let library = CString::new("libbinder_ndk.so").ok()?;
    let handle = dlopen(library.as_ptr(), RTLD_NOW);
    if handle.is_null() {
        return None;
    }
    resolve(handle, "AServiceManager_waitForService")
}
