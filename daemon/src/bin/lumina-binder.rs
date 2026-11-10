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

use std::fs::OpenOptions;
use std::os::fd::AsRawFd;
use std::sync::Arc;

use luminad_core::binder::producer::Shared;
use luminad_core::binder::BinderMonitor;
use luminad_core::binder_ipc;

const LOCK_PATH: &str = "/data/local/tmp/lumina-binder.lock";

fn acquire_lock() -> bool {
    let Ok(file) = OpenOptions::new()
        .create(true)
        .read(true)
        .write(true)
        .truncate(false)
        .open(LOCK_PATH)
    else {
        return true;
    };
    let result = unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) };
    if result != 0 {
        return false;
    }
    std::mem::forget(file);
    true
}

fn main() {
    if !acquire_lock() {
        eprintln!("lumina-binder is already running");
        std::process::exit(0);
    }

    luminad_core::log!("[BINDER] helper starting (pid={})", std::process::id());

    let shared = Arc::new(Shared::new());

    let screen = luminad_core::detector::get_system_prop("debug.tracing.screen_state");
    if !screen.is_empty() {
        shared.set_initial_screen(screen == "2");
    }

    if let Some(monitor) = BinderMonitor::start(Arc::clone(&shared)) {
        std::mem::forget(monitor);
        luminad_core::log!("[BINDER] observer thread started");
    } else {
        luminad_core::log!("[BINDER] observer thread failed to start");
    }

    binder_ipc::serve(shared);
}
