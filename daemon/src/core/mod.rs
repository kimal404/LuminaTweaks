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

#[macro_use]
pub mod logger;
pub mod angle;
pub mod detector;
pub mod ipc;
pub mod perf;
pub mod watcher;

use std::sync::atomic::{AtomicBool, Ordering};

/// Set when the Manager rewrites `record_apps.json` through the bridge so the
/// recorder drops its mtime cache immediately instead of waiting for the next
/// config poll. Lives in the shared crate because the writer runs inside the
/// `lumina-binder` helper, not in `luminad` itself.
static RECORD_APPS_STALE: AtomicBool = AtomicBool::new(false);

pub fn mark_record_apps_stale() {
    RECORD_APPS_STALE.store(true, Ordering::Relaxed);
}

/// Returns `true` at most once per invalidation.
pub fn take_record_apps_stale() -> bool {
    RECORD_APPS_STALE.swap(false, Ordering::Relaxed)
}
