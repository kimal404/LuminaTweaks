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

use std::fs::{self, File};
use std::io::{BufReader, Read};
use std::path::{Path, PathBuf};
use std::process::Command;

const MAX_PRELOAD_BYTES: u64 = 128 * 1024 * 1024;
const CHUNK_SIZE: usize = 64 * 1024;

fn is_enabled() -> bool {
    if let Some(json) = crate::detector::read_global_json() {
        if let Some(value) = json.get("game_preload") {
            if let Some(enabled) = value.as_bool() {
                return enabled;
            }
            if let Some(enabled) = value.as_i64() {
                return enabled == 1;
            }
        }
    }

    let prop = crate::detector::get_system_prop("persist.lumina.game_preload");
    prop == "1" || prop.eq_ignore_ascii_case("true")
}

fn package_files(package: &str) -> Vec<PathBuf> {
    let Ok(output) = Command::new("pm").args(["path", package]).output() else {
        return Vec::new();
    };
    let text = String::from_utf8_lossy(&output.stdout);
    text.lines()
        .filter_map(|line| line.trim().strip_prefix("package:"))
        .map(PathBuf::from)
        .filter(|path| path.is_file())
        .collect()
}

fn native_libraries(apk: &Path) -> Vec<PathBuf> {
    let Some(parent) = apk.parent() else {
        return Vec::new();
    };

    let mut libraries = Vec::new();
    for abi in ["arm64", "arm"] {
        let dir = parent.join("lib").join(abi);
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().is_some_and(|ext| ext == "so") && path.is_file() {
                libraries.push(path);
            }
        }
    }
    libraries
}

fn preload_file(path: &Path, budget: &mut u64) -> u64 {
    if *budget == 0 {
        return 0;
    }

    let Ok(file) = File::open(path) else {
        return 0;
    };
    let mut reader = BufReader::with_capacity(CHUNK_SIZE, file);
    let mut buffer = [0u8; CHUNK_SIZE];
    let mut total = 0;

    while *budget > 0 {
        let limit = (*budget).min(CHUNK_SIZE as u64) as usize;
        match reader.read(&mut buffer[..limit]) {
            Ok(0) => break,
            Ok(read) => {
                total += read as u64;
                *budget -= read as u64;
            }
            Err(_) => break,
        }
    }
    total
}

fn preload_package(package: &str) -> u64 {
    let mut budget = MAX_PRELOAD_BYTES;
    let mut total = 0;

    for apk in package_files(package) {
        if budget == 0 {
            break;
        }
        total += preload_file(&apk, &mut budget);
        for library in native_libraries(&apk) {
            if budget == 0 {
                break;
            }
            total += preload_file(&library, &mut budget);
        }
    }
    total
}

pub struct GamePreloader {
    last_package: Option<String>,
}

impl GamePreloader {
    pub fn new() -> Self {
        Self { last_package: None }
    }

    pub fn reset(&mut self) {
        self.last_package = None;
    }

    pub fn observe(&mut self, package: Option<&str>) {
        let Some(package) = package else {
            self.last_package = None;
            return;
        };

        if self.last_package.as_deref() == Some(package) {
            return;
        }
        self.last_package = Some(package.to_string());

        if !is_enabled() {
            return;
        }

        let bytes = preload_package(package);
        if bytes > 0 {
            crate::log!(
                "[PRELOAD] Warmed {} MiB for {}",
                bytes / (1024 * 1024),
                package
            );
        }
    }
}
