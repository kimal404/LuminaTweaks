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
