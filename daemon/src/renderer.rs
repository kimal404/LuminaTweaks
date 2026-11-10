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
use std::path::Path;
use std::process::Command;
use std::sync::Mutex;
use std::time::{Duration, Instant};

const CONFIG_DIR: &str = "/data/adb/.config/lumina";
const MODULE_DIR: &str = "/data/adb/modules/luminatweaks";

#[derive(Clone, Copy, PartialEq, Eq)]
enum Backend {
    Vulkan,
    Gles,
    None,
}

struct RendererSpec {
    backend: Backend,
    threaded: bool,
    skia_threaded: bool,
}

impl RendererSpec {
    fn parse(name: &str) -> Option<Self> {
        let lower = name.trim().to_ascii_lowercase();
        if lower.is_empty() {
            return None;
        }

        let backend = if lower.contains("vulkan") || lower.contains("skiavk") || lower == "vk" {
            Backend::Vulkan
        } else if lower.contains("opengl") || lower.contains("gles") || lower.contains("skiagl") {
            Backend::Gles
        } else if lower == "software" {
            Backend::None
        } else {
            return None;
        };

        let threaded = lower.contains("threaded");
        let skia_threaded = threaded && lower.contains("skia");

        Some(Self {
            backend,
            threaded,
            skia_threaded,
        })
    }
}

fn run(tool: &str, args: &[&str]) {
    let _ = Command::new(tool).args(args).output();
}

fn set_prop(key: &str, value: &str) {
    run("setprop", &[key, value]);
}

fn set_reset_prop(key: &str, value: Option<&str>) {
    match value {
        Some(v) => run("resetprop", &[key, v]),
        None => run("resetprop", &["--delete", key]),
    }
}

fn clear_props() {
    for key in [
        "debug.hwui.renderer",
        "debug.renderengine.backend",
        "debug.hwui.render_thread",
        "debug.skia.threaded_mode",
    ] {
        set_prop(key, "");
    }
    set_reset_prop("ro.hwui.use_vulkan", None);
}

fn write_spec(spec: &RendererSpec) {
    match spec.backend {
        Backend::None => {
            clear_props();
            set_reset_prop("ro.hwui.use_vulkan", Some("false"));
        }
        Backend::Vulkan => {
            set_prop("debug.renderengine.backend", "vulkan");
            set_prop(
                "debug.hwui.render_thread",
                if spec.threaded { "true" } else { "false" },
            );
            set_prop(
                "debug.skia.threaded_mode",
                if spec.skia_threaded { "true" } else { "false" },
            );
            set_reset_prop("ro.hwui.use_vulkan", Some("true"));
        }
        Backend::Gles => {
            set_prop("debug.renderengine.backend", "gles");
            set_prop(
                "debug.hwui.render_thread",
                if spec.threaded { "true" } else { "false" },
            );
            set_prop(
                "debug.skia.threaded_mode",
                if spec.skia_threaded { "true" } else { "false" },
            );
            set_reset_prop("ro.hwui.use_vulkan", Some("false"));
        }
    }
}

fn config_path() -> String {
    format!("{}/current_renderer.txt", CONFIG_DIR)
}

fn module_path() -> String {
    format!("{}/current_renderer.txt", MODULE_DIR)
}

pub fn setrender(renderer: &str) {
    match RendererSpec::parse(renderer) {
        Some(spec) => {
            set_prop("debug.hwui.renderer", renderer);
            write_spec(&spec);
        }
        None => {
            clear_props();
            set_reset_prop("ro.hwui.use_vulkan", None);
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

    let mut value = String::new();
    for path in [config_path(), module_path()] {
        if let Ok(text) = fs::read_to_string(path) {
            let trimmed = text.trim();
            if !trimmed.is_empty() {
                value = trimmed.to_string();
                break;
            }
        }
    }

    if value.is_empty() {
        if let Ok(out) = Command::new("getprop")
            .arg("persist.lumina.renderer")
            .output()
        {
            let prop = String::from_utf8_lossy(&out.stdout).trim().to_string();
            if !prop.is_empty() {
                value = prop;
            }
        }
    }

    if value.is_empty() {
        value = "default".to_string();
    }

    if let Ok(mut guard) = RENDERER_CACHE.lock() {
        *guard = Some((Instant::now(), value.clone()));
    }
    value
}

pub fn save_renderer(renderer: &str) {
    if let Ok(mut guard) = RENDERER_CACHE.lock() {
        *guard = Some((Instant::now(), renderer.to_string()));
    }
    let _ = fs::write(config_path(), renderer);
    set_prop("persist.lumina.renderer", renderer);

    let module = module_path();
    if Path::new(&module).exists() {
        let _ = fs::write(module, renderer);
    }
}

pub fn apply_renderer(renderer: &str) {
    save_renderer(renderer);
    setrender(renderer);
}

pub fn init_renderer_subsystem() {
    let saved = get_saved_renderer();
    setrender(&saved);
}
