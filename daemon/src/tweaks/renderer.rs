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

use std::fs;
use std::process::Command;
use std::sync::Mutex;
use std::time::{Duration, Instant};

const CONFIG_DIR: &str = "/data/adb/.config/lumina";
const LEGACY_MODULE_RENDERER: &str = "/data/adb/modules/luminatweaks/current_renderer.txt";

#[derive(Clone, Copy, PartialEq, Eq)]
enum GlobalBackend {
    Default,
    SkiaGl,
    SkiaVk,
}

impl GlobalBackend {
    fn from_raw(name: &str) -> Self {
        let lower = name.trim().to_ascii_lowercase();
        if lower.contains("vulkan") || lower.contains("skiavk") || lower == "vk" {
            Self::SkiaVk
        } else if lower.contains("opengl") || lower.contains("gles") || lower.contains("skiagl") {
            Self::SkiaGl
        } else {
            Self::Default
        }
    }

    fn as_id(self) -> &'static str {
        match self {
            Self::Default => "default",
            Self::SkiaGl => "skiagl",
            Self::SkiaVk => "skiavk",
        }
    }
}

fn run(tool: &str, args: &[&str]) {
    let _ = Command::new(tool).args(args).output();
}

fn set_prop(key: &str, value: &str) {
    let _ = crate::core::sysprop::set(key, value);
}

fn clear_legacy_props() {
    set_prop("debug.hwui.render_thread", "");
    set_prop("debug.skia.threaded_mode", "");
    run("resetprop", &["--delete", "ro.hwui.use_vulkan"]);
}

fn config_path() -> String {
    format!("{}/current_renderer.txt", CONFIG_DIR)
}

pub fn setrender(renderer: &str) {
    clear_legacy_props();
    match GlobalBackend::from_raw(renderer) {
        GlobalBackend::SkiaVk => {
            set_prop("debug.hwui.renderer", "skiavk");
            set_prop("debug.renderengine.backend", "vulkan");
        }
        GlobalBackend::SkiaGl => {
            set_prop("debug.hwui.renderer", "skiagl");
            set_prop("debug.renderengine.backend", "gles");
        }
        GlobalBackend::Default => {
            set_prop("debug.hwui.renderer", "");
            set_prop("debug.renderengine.backend", "");
        }
    }
}

static RENDERER_CACHE: Mutex<Option<(Instant, String)>> = Mutex::new(None);
const RENDERER_CACHE_TTL: Duration = Duration::from_millis(1000);

pub fn get_saved_renderer() -> String {
    if let Ok(guard) = RENDERER_CACHE.lock() {
        if let Some((at, value)) = guard.as_ref() {
            if at.elapsed() < RENDERER_CACHE_TTL {
                return value.clone();
            }
        }
    }

    let mut raw = String::new();
    for path in [config_path(), LEGACY_MODULE_RENDERER.to_string()] {
        if let Ok(text) = fs::read_to_string(path) {
            let trimmed = text.trim();
            if !trimmed.is_empty() {
                raw = trimmed.to_string();
                break;
            }
        }
    }

    if raw.is_empty() {
        if let Ok(out) = Command::new("getprop")
            .arg("persist.lumina.renderer")
            .output()
        {
            let prop = String::from_utf8_lossy(&out.stdout).trim().to_string();
            if !prop.is_empty() {
                raw = prop;
            }
        }
    }

    let normalized = GlobalBackend::from_raw(&raw).as_id().to_string();
    if let Ok(mut guard) = RENDERER_CACHE.lock() {
        *guard = Some((Instant::now(), normalized.clone()));
    }
    normalized
}

pub fn save_renderer(renderer: &str) {
    let normalized = GlobalBackend::from_raw(renderer).as_id().to_string();
    if let Ok(mut guard) = RENDERER_CACHE.lock() {
        *guard = Some((Instant::now(), normalized.clone()));
    }
    let _ = fs::write(config_path(), &normalized);
    set_prop("persist.lumina.renderer", &normalized);
    let _ = fs::remove_file(LEGACY_MODULE_RENDERER);
}

pub fn apply_renderer(renderer: &str) {
    save_renderer(renderer);
    setrender(renderer);
}

pub fn init_renderer_subsystem() {
    let saved = get_saved_renderer();
    setrender(&saved);
}
