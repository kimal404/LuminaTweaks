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

use std::sync::Arc;
use std::thread;
use std::time::Duration;

use luminad_core::binder::BinderMonitor;
use luminad_core::binder_ipc;

fn main() {
    luminad_core::log!("[BINDER] helper starting (pid={})", std::process::id());

    let monitor = loop {
        if let Some(monitor) = BinderMonitor::start() {
            break Arc::new(monitor);
        }
        luminad_core::log!("[BINDER] observer unavailable, retrying in 30s");
        thread::sleep(Duration::from_secs(30));
    };

    binder_ipc::serve(monitor);
}
