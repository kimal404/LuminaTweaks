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

const QUERIES: [&str; 3] = [
    "android.app.IActivityManager.Stub::TRANSACTION_registerProcessObserver",
    "android.app.IProcessObserver.Stub::TRANSACTION_onForegroundActivitiesChanged",
    "android.app.IProcessObserver.Stub::TRANSACTION_onProcessDied",
];

#[derive(Debug, Clone, Copy)]
pub struct Codes {
    pub register: u32,
    pub foreground: u32,
    pub died: u32,
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
    let mut resolved: HashMap<String, u32> = HashMap::new();
    for line in BufReader::new(stdout).lines().map_while(Result::ok) {
        if let Some((query, value)) = line.rsplit_once(' ') {
            if let Ok(code) = value.trim().parse::<u32>() {
                resolved.insert(query.to_string(), code);
            }
        }
    }
    let _ = child.wait();

    let register = *resolved.get(QUERIES[0])?;
    let foreground = *resolved.get(QUERIES[1])?;
    let died = *resolved.get(QUERIES[2])?;

    if register == 0 || foreground == 0 || died == 0 {
        return None;
    }

    Some(Codes {
        register,
        foreground,
        died,
    })
}
