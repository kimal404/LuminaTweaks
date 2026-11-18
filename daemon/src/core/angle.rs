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

//! ROM-level ANGLE capability detection.
//!
//! Lives in the shared core crate so both `luminad` and the `lumina-binder`
//! helper (which serves the `ANGLE_STATUS` bridge command) can answer the same
//! question without duplicating the probe.

use crate::core::detector::get_system_prop;
use std::path::Path;
use std::process::Command;
use std::sync::Mutex;

#[derive(Debug, Clone, Default)]
pub struct AngleSupport {
    /// ANGLE libraries were found somewhere on the system/vendor partitions.
    pub libraries: bool,
    /// The ROM advertises ANGLE through `ro.gfx.angle.supported`.
    pub declared: bool,
    /// The updatable driver package (`com.android.angle`) is installed.
    pub package: bool,
    /// `Settings.Global` writes actually round-trip on this framework.
    pub settings_keys: bool,
}

impl AngleSupport {
    /// Only claim support when the libraries exist *and* the framework is able
    /// to persist per-app driver selection.
    pub fn usable(&self) -> bool {
        self.libraries && self.settings_keys
    }

    pub fn describe(&self) -> String {
        format!(
            "libs={} declared={} package={} settings={}",
            self.libraries, self.declared, self.package, self.settings_keys
        )
    }
}

fn angle_lib_present() -> bool {
    const LIB_DIRS: [&str; 6] = [
        "/system/lib64",
        "/system/lib64/egl",
        "/vendor/lib64",
        "/vendor/lib64/egl",
        "/system/lib",
        "/vendor/lib",
    ];
    const ANGLE_LIBS: [&str; 3] = [
        "libGLESv2_angle.so",
        "libEGL_angle.so",
        "libGLESv1_CM_angle.so",
    ];

    LIB_DIRS
        .iter()
        .any(|dir| ANGLE_LIBS.iter().any(|lib| Path::new(dir).join(lib).exists()))
}

fn settings_writable() -> bool {
    // `settings get` succeeds even for unknown keys, so prove the provider
    // actually stores values by writing a sentinel and reading it back.
    const PROBE_KEY: &str = "lumina_angle_probe";

    let put_ok = matches!(
        Command::new("settings")
            .args(["put", "global", PROBE_KEY, "1"])
            .status(),
        Ok(status) if status.success()
    );

    let readback = Command::new("settings")
        .args(["get", "global", PROBE_KEY])
        .output()
        .ok()
        .filter(|out| out.status.success())
        .map(|out| String::from_utf8_lossy(&out.stdout).trim().to_string());

    let _ = Command::new("settings")
        .args(["delete", "global", PROBE_KEY])
        .status();

    put_ok && readback.as_deref() == Some("1")
}

static CACHE: Mutex<Option<AngleSupport>> = Mutex::new(None);

/// Probes the device once and caches the outcome for the process lifetime.
pub fn detect_angle_support() -> AngleSupport {
    if let Ok(cache) = CACHE.lock() {
        if let Some(cached) = cache.as_ref() {
            return cached.clone();
        }
    }

    let support = AngleSupport {
        libraries: angle_lib_present(),
        declared: !get_system_prop("ro.gfx.angle.supported").is_empty(),
        package: get_system_prop("ro.gfx.driver.0").eq_ignore_ascii_case("com.android.angle"),
        settings_keys: settings_writable(),
    };

    if let Ok(mut cache) = CACHE.lock() {
        *cache = Some(support.clone());
    }

    support
}