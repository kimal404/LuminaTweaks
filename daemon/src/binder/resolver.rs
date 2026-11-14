/*
 * Original work Copyright (c) 2026 Rem01Gaming (binder_resolver / Encore Tweaks)
 * https://github.com/Rem01Gaming/binder_resolver
 * Licensed under the Apache License, Version 2.0.
 *
 * Transaction-code resolver adapted for LuminaTweaks. The bundled
 * `binder_resolver.apk` is redistributed unmodified under Apache-2.0.
 *
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

use std::collections::HashMap;
use std::io::{BufRead, BufReader, Write};
use std::path::Path;
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

use crate::core::detector::get_system_prop;

const RESOLVE_TIMEOUT: Duration = Duration::from_secs(5);
const CACHE_SCHEMA: u32 = 2;
const CACHE_PATH: &str = "/data/adb/.config/lumina/binder_codes.json";
const CONFIG_DIR: &str = "/data/adb/.config/lumina";

const QUERIES: [&str; 10] = [
    "android.app.IActivityManager.Stub::TRANSACTION_registerProcessObserver",
    "android.app.IProcessObserver.Stub::TRANSACTION_onForegroundActivitiesChanged",
    "android.app.IProcessObserver.Stub::TRANSACTION_onProcessDied",
    "android.hardware.display.IDisplayManager.Stub::TRANSACTION_registerCallback",
    "android.hardware.display.IDisplayManagerCallback.Stub::TRANSACTION_onDisplayEvent",
    "android.os.IPowerManager.Stub::TRANSACTION_isInteractive",
    "android.os.IPowerManager.Stub::TRANSACTION_isPowerSaveMode",
    "android.app.INotificationManager.Stub::TRANSACTION_setInterruptionFilter",
    "android.app.INotificationManager.Stub::TRANSACTION_getInterruptionFilter",
    "android.hardware.display.IDisplayManager.Stub::TRANSACTION_registerCallbackWithEventMask",
];

#[derive(Debug, Clone, Copy, Default, serde::Serialize, serde::Deserialize)]
pub struct Codes {
    pub register: u32,
    pub foreground: u32,
    pub died: u32,
    pub display_register: u32,
    pub display_event: u32,
    pub is_interactive: u32,
    pub is_power_save: u32,
    pub set_interruption_filter: u32,
    pub get_interruption_filter: u32,
    /// `registerCallbackWithEventMask` (Android 14+). Zero on older builds.
    #[serde(default)]
    pub display_register_with_mask: u32,
}

impl Codes {
    fn essential_ok(&self) -> bool {
        self.register != 0 && self.foreground != 0 && self.died != 0
    }

    /// Only a table with every code used by event-driven monitoring is worth
    /// persisting. Power-save and DND codes may legitimately stay zero on some
    /// firmwares (both have userspace fallbacks), but a missing display or
    /// interactive code must not be cached forever.
    fn cacheable(&self) -> bool {
        self.essential_ok()
            && self.display_register != 0
            && self.display_event != 0
            && self.is_interactive != 0
    }
}

#[derive(serde::Serialize, serde::Deserialize)]
struct CacheFile {
    schema: u32,
    fingerprint: String,
    codes: Codes,
}

fn fingerprint() -> String {
    format!(
        "{}|{}",
        get_system_prop("ro.build.fingerprint"),
        get_system_prop("ro.build.version.sdk")
    )
}

/// Returns cached transaction codes when they were resolved for the exact
/// same firmware. The prebuilt resolver APK is therefore spawned at most once
/// per OTA/ROM change instead of on every daemon or binder restart.
pub fn load_cached() -> Option<Codes> {
    let raw = std::fs::read_to_string(CACHE_PATH).ok()?;
    let cache: CacheFile = serde_json::from_str(&raw).ok()?;
    if cache.schema != CACHE_SCHEMA || cache.fingerprint != fingerprint() {
        return None;
    }
    if !cache.codes.cacheable() {
        return None;
    }
    Some(cache.codes)
}

pub fn invalidate_cache() {
    let _ = std::fs::remove_file(CACHE_PATH);
}

fn store_cache(codes: &Codes) {
    let cache = CacheFile {
        schema: CACHE_SCHEMA,
        fingerprint: fingerprint(),
        codes: *codes,
    };
    let Ok(payload) = serde_json::to_string(&cache) else {
        return;
    };
    let _ = std::fs::create_dir_all(CONFIG_DIR);
    let temp = format!("{}.tmp", CACHE_PATH);
    if std::fs::write(&temp, payload).is_ok() {
        use std::os::unix::fs::PermissionsExt;
        let _ = std::fs::set_permissions(&temp, std::fs::Permissions::from_mode(0o644));
        if std::fs::rename(&temp, CACHE_PATH).is_err() {
            let _ = std::fs::remove_file(&temp);
        }
    }
}

pub fn resolve(apk: &Path) -> Option<Codes> {
    if !apk.is_file() {
        return None;
    }

    let classpath = format!("-Djava.class.path={}", apk.display());
    let mut child = Command::new("/system/bin/app_process")
        .arg(classpath)
        .arg("/")
        .arg("com.rem01gaming.binderresolver.MainKt")
        .env("ANDROID_ROOT", "/system")
        .env("ANDROID_DATA", "/data")
        .env("ANDROID_RUNTIME_ROOT", "/apex/com.android.runtime")
        .env("ANDROID_ART_ROOT", "/apex/com.android.art")
        .env("ANDROID_I18N_ROOT", "/apex/com.android.i18n")
        .env("ANDROID_TZDATA_ROOT", "/apex/com.android.tzdata")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .ok()?;

    if let Some(mut stdin) = child.stdin.take() {
        for query in QUERIES {
            let _ = writeln!(stdin, "{}", query);
        }
    }

    let stdout = child.stdout.take()?;
    let reader = std::thread::spawn(move || {
        let mut resolved: HashMap<String, u32> = HashMap::new();
        for line in BufReader::new(stdout).lines().map_while(Result::ok) {
            if let Some((query, value)) = line.rsplit_once(' ') {
                if let Ok(code) = value.trim().parse::<u32>() {
                    resolved.insert(query.to_string(), code);
                }
            }
        }
        resolved
    });

    let deadline = Instant::now() + RESOLVE_TIMEOUT;
    loop {
        match child.try_wait() {
            Ok(Some(_)) => break,
            Ok(None) if Instant::now() < deadline => {
                std::thread::sleep(Duration::from_millis(25));
            }
            Ok(None) => {
                let _ = child.kill();
                let _ = child.wait();
                let _ = reader.join();
                return None;
            }
            Err(_) => {
                let _ = child.kill();
                let _ = reader.join();
                return None;
            }
        }
    }

    let resolved = reader.join().ok()?;
    let codes = Codes {
        register: resolved.get(QUERIES[0]).copied().unwrap_or(0),
        foreground: resolved.get(QUERIES[1]).copied().unwrap_or(0),
        died: resolved.get(QUERIES[2]).copied().unwrap_or(0),
        display_register: resolved.get(QUERIES[3]).copied().unwrap_or(0),
        display_event: resolved.get(QUERIES[4]).copied().unwrap_or(0),
        is_interactive: resolved.get(QUERIES[5]).copied().unwrap_or(0),
        is_power_save: resolved.get(QUERIES[6]).copied().unwrap_or(0),
        set_interruption_filter: resolved.get(QUERIES[7]).copied().unwrap_or(0),
        get_interruption_filter: resolved.get(QUERIES[8]).copied().unwrap_or(0),
        display_register_with_mask: resolved.get(QUERIES[9]).copied().unwrap_or(0),
    };

    if !codes.essential_ok() {
        return None;
    }

    if codes.cacheable() {
        store_cache(&codes);
    }
    Some(codes)
}