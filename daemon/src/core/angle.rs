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
    /// A ROM can honour per-app ANGLE overrides as long as an ANGLE driver is
    /// actually shipped. The `settings` round-trip probe is deliberately NOT
    /// part of this check: it only reports whether the current shell context
    /// can write that provider, and a failed probe would otherwise veto a ROM
    /// that does have working ANGLE (false negative).
    pub fn usable(&self) -> bool {
        self.libraries || self.package || self.declared
    }

    pub fn describe(&self) -> String {
        format!(
            "libs={} declared={} package={} settings={}",
            self.libraries, self.declared, self.package, self.settings_keys
        )
    }
}

fn angle_lib_present() -> bool {
    const LIB_DIRS: [&str; 10] = [
        "/system/lib64",
        "/system/lib64/egl",
        "/system/lib64/hw",
        "/vendor/lib64",
        "/vendor/lib64/egl",
        "/vendor/lib64/hw",
        "/system/lib",
        "/system/lib/egl",
        "/vendor/lib",
        "/vendor/lib/egl",
    ];
    // Different ROMs bundle ANGLE under slightly different names; any of them
    // means the driver is shipped.
    const ANGLE_LIBS: [&str; 5] = [
        "libGLESv2_angle.so",
        "libEGL_angle.so",
        "libGLESv1_CM_angle.so",
        "libGLESv2_angle.so.1",
        "libEGL_angle.so.1",
    ];

    let direct = LIB_DIRS
        .iter()
        .any(|dir| ANGLE_LIBS.iter().any(|lib| Path::new(dir).join(lib).exists()));

    if direct {
        return true;
    }

    // Updatable / APEX-provided ANGLE (Android 14 AOSP layout).
    const EXTRA_LIBS: [&str; 5] = [
        "/apex/com.android.angle/lib64/libGLESv2_angle.so",
        "/apex/com.android.angle/lib64/libEGL_angle.so",
        "/system/apex/com.android.angle/lib64/libGLESv2_angle.so",
        "/vendor/lib64/libGLESv2_angle.so",
        "/odm/lib64/libGLESv2_angle.so",
    ];
    EXTRA_LIBS.iter().any(|path| Path::new(path).exists())
}

fn angle_package_installed() -> bool {
    // The updatable driver ships inside the `com.android.angle` package; the
    // property is set on most OEM ROMs that bundle ANGLE this way.
    if get_system_prop("ro.gfx.driver.0").eq_ignore_ascii_case("com.android.angle") {
        return true;
    }

    Command::new("pm")
        .args(["path", "com.android.angle"])
        .output()
        .ok()
        .filter(|out| out.status.success())
        .map(|out| {
            let text = String::from_utf8_lossy(&out.stdout);
            text.contains("package:")
        })
        .unwrap_or(false)
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
        package: angle_package_installed(),
        settings_keys: settings_writable(),
    };

    if let Ok(mut cache) = CACHE.lock() {
        *cache = Some(support.clone());
    }

    support
}