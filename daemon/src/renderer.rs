use std::fs;
use std::process::Command;

fn setprop(key: &str, val: &str) {
    let _ = Command::new("setprop").args([key, val]).output();
}

fn resetprop(key: &str, val: &str) {
    if val.is_empty() {
        let _ = Command::new("resetprop").args(["--delete", key]).output();
    } else {
        let _ = Command::new("resetprop").args([key, val]).output();
    }
}

pub fn setrender(renderer: &str) {
    if renderer == "default" || renderer.is_empty() {
        setprop("debug.hwui.renderer", "");
        setprop("debug.renderengine.backend", "");
        setprop("debug.hwui.render_thread", "");
        setprop("debug.skia.threaded_mode", "");
        resetprop("ro.hwui.use_vulkan", "");
        return;
    }

    setprop("debug.hwui.renderer", renderer);

    if renderer.contains("threaded") {
        setprop("debug.hwui.render_thread", "true");
        if renderer.contains("skia") {
            setprop("debug.skia.threaded_mode", "true");
        } else {
            setprop("debug.skia.threaded_mode", "false");
        }
    } else {
        setprop("debug.hwui.render_thread", "false");
        setprop("debug.skia.threaded_mode", "false");
    }

    match renderer {
        "skiavk" | "skiavkthreaded" | "vulkan" => {
            setprop("debug.renderengine.backend", "vulkan");
            resetprop("ro.hwui.use_vulkan", "true");
        }
        "skiagl" | "skiaglthreaded" | "gles" | "opengl" | "openglthreaded" => {
            setprop("debug.renderengine.backend", "gles");
            resetprop("ro.hwui.use_vulkan", "false");
        }
        "software" => {
            setprop("debug.renderengine.backend", "");
            resetprop("ro.hwui.use_vulkan", "false");
        }
        _ => {
            if renderer.contains("vk") || renderer.contains("vulkan") {
                setprop("debug.renderengine.backend", "vulkan");
                resetprop("ro.hwui.use_vulkan", "true");
            } else if renderer.contains("gl") || renderer.contains("gles") {
                setprop("debug.renderengine.backend", "gles");
                resetprop("ro.hwui.use_vulkan", "false");
            }
        }
    }
}

pub fn get_saved_renderer() -> String {
    for path in [
        "/data/adb/luminatweaks/current_renderer.txt",
        "/data/adb/modules/luminatweaks/current_renderer.txt",
    ] {
        if let Ok(content) = fs::read_to_string(path) {
            let trimmed = content.trim();
            if !trimmed.is_empty() {
                return trimmed.to_string();
            }
        }
    }
    let output = Command::new("getprop")
        .arg("persist.lumina.renderer")
        .output();
    if let Ok(out) = output {
        let val = String::from_utf8_lossy(&out.stdout).trim().to_string();
        if !val.is_empty() {
            return val;
        }
    }
    "default".to_string()
}

pub fn save_renderer(renderer: &str) {
    let _ = fs::create_dir_all("/data/adb/luminatweaks");
    let _ = fs::write("/data/adb/luminatweaks/current_renderer.txt", renderer);
    setprop("persist.lumina.renderer", renderer);
    let mod_path = "/data/adb/modules/luminatweaks";
    if std::path::Path::new(mod_path).exists() {
        let _ = fs::write(format!("{}/current_renderer.txt", mod_path), renderer);
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