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

use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

const FAST_LOAD_FLAG: &str = "/data/adb/luminatweaks/fast_load";
const BOOST_DURATION_SECS: u64 = 20;

fn is_system_overlay(pkg: &str) -> bool {
    pkg.contains(".inputmethod.")
        || pkg.contains(".latin")
        || pkg.contains(".ime.")
        || pkg == "com.miui.home"
        || pkg == "com.android.launcher3"
        || pkg.contains(".launcher")
}

fn write_lock(path: &Path, value: &str) {
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o644));
    let _ = fs::write(path, value);
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o444));
}

fn write_unlock(path: &Path, value: &str) {
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o644));
    let _ = fs::write(path, value);
}

pub struct FastLoadController {
    is_active: bool,
    boost_start_time: Option<Instant>,
    last_game_package: String,
    original_dirty_ratio: String,
    original_dirty_background_ratio: String,
    read_ahead_paths: Vec<PathBuf>,
    cpu_min_freq_paths: Vec<PathBuf>,
    original_min_freqs: Vec<String>,
}

impl FastLoadController {
    pub fn new() -> Self {
        let read_ahead_paths = Self::discover_read_ahead_nodes();
        let cpu_min_freq_paths = Self::discover_cpu_freq_nodes();

        Self {
            is_active: false,
            boost_start_time: None,
            last_game_package: String::new(),
            original_dirty_ratio: String::new(),
            original_dirty_background_ratio: String::new(),
            read_ahead_paths,
            cpu_min_freq_paths,
            original_min_freqs: Vec::new(),
        }
    }

    fn discover_read_ahead_nodes() -> Vec<PathBuf> {
        let mut paths = Vec::new();
        if let Ok(entries) = fs::read_dir("/sys/block") {
            for entry in entries.flatten() {
                let node = entry.path().join("queue/read_ahead_kb");
                if node.exists() {
                    paths.push(node);
                }
            }
        }
        paths
    }

    fn discover_cpu_freq_nodes() -> Vec<PathBuf> {
        let mut paths = Vec::new();
        if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("cpu") && name_str[3..].chars().all(|c| c.is_ascii_digit()) {
                    let min_node = entry.path().join("cpufreq/scaling_min_freq");
                    if min_node.exists() {
                        paths.push(min_node);
                    }
                }
            }
        }
        paths
    }

    pub fn tick(&mut self, current_pkg: &str, is_game: bool) {
        let is_enabled = self.is_flag_enabled();

        if !is_enabled {
            if self.is_active {
                self.restore();
            }
            return;
        }

        if is_game && current_pkg != self.last_game_package {
            self.last_game_package = current_pkg.to_string();
            self.apply();
            return;
        }

        if self.is_active {
            let timer_expired = self
                .boost_start_time
                .map(|t| t.elapsed() >= Duration::from_secs(BOOST_DURATION_SECS))
                .unwrap_or(false);

            let real_app_switch =
                !is_system_overlay(current_pkg) && current_pkg != self.last_game_package;

            if timer_expired || real_app_switch {
                self.restore();
            }
        }
    }

    fn is_flag_enabled(&self) -> bool {
        fs::read_to_string(FAST_LOAD_FLAG)
            .map(|s| s.trim() == "1")
            .unwrap_or(false)
    }

    fn apply(&mut self) {
        self.original_dirty_ratio = fs::read_to_string("/proc/sys/vm/dirty_ratio")
            .unwrap_or_else(|_| "20".to_string())
            .trim()
            .to_string();

        self.original_dirty_background_ratio =
            fs::read_to_string("/proc/sys/vm/dirty_background_ratio")
                .unwrap_or_else(|_| "10".to_string())
                .trim()
                .to_string();

        for node in &self.read_ahead_paths {
            let _ = fs::write(node, "1024");
        }

        let _ = fs::write("/proc/sys/vm/dirty_ratio", "10");
        let _ = fs::write("/proc/sys/vm/dirty_background_ratio", "5");

        self.original_min_freqs.clear();
        for node in &self.cpu_min_freq_paths {
            let current = fs::read_to_string(node).unwrap_or_default().trim().to_string();
            self.original_min_freqs.push(current);

            let max_node = node.with_file_name("scaling_max_freq");
            if let Ok(max_freq) = fs::read_to_string(&max_node) {
                write_lock(node, max_freq.trim());
            }
        }

        self.is_active = true;
        self.boost_start_time = Some(Instant::now());
    }

    pub fn restore(&mut self) {
        if !self.is_active {
            return;
        }

        for node in &self.read_ahead_paths {
            let _ = fs::write(node, "128");
        }

        if !self.original_dirty_ratio.is_empty() {
            let _ = fs::write("/proc/sys/vm/dirty_ratio", &self.original_dirty_ratio);
        }
        if !self.original_dirty_background_ratio.is_empty() {
            let _ = fs::write(
                "/proc/sys/vm/dirty_background_ratio",
                &self.original_dirty_background_ratio,
            );
        }

        for (i, node) in self.cpu_min_freq_paths.iter().enumerate() {
            if let Some(orig_freq) = self.original_min_freqs.get(i) {
                if !orig_freq.is_empty() {
                    write_unlock(node, orig_freq);
                }
            }
        }

        self.is_active = false;
        self.boost_start_time = None;
    }
}
