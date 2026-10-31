use std::process::Command;
use std::path::Path;
use crate::log;

pub struct TextureController;

impl TextureController {
    pub fn ensure_setup() {
        let target_dir = "/data/local/debug/gles";
        let target_so = "/data/local/debug/gles/libluminagfx.so";

        let _ = Command::new("mkdir").args(["-p", target_dir]).status();
        let _ = Command::new("chmod").args(["777", target_dir]).status();

        for source in [
            "/data/adb/modules/luminatweaks/system/lib64/libluminagfx.so",
            "/system/lib64/libluminagfx.so",
        ] {
            if Path::new(source).exists() {
                let _ = Command::new("cp").args(["-f", source, target_so]).status();
                let _ = Command::new("chmod").args(["755", target_so]).status();
                break;
            }
        }
    }

    pub fn apply(pkg: &str, enabled: bool, detail: i32, sharp: bool, hard: bool) {
        Self::ensure_setup();

        if !enabled {
            Self::restore();
            return;
        }

        let _ = Command::new("setprop").args(["debug.lumina.gfx.enabled", "1"]).status();
        let _ = Command::new("setprop").args(["debug.lumina.gfx.detail", &detail.to_string()]).status();
        let _ = Command::new("setprop").args(["debug.lumina.gfx.sharp", if sharp { "1" } else { "0" }]).status();
        let _ = Command::new("setprop").args(["debug.lumina.gfx.hard", if hard { "1" } else { "0" }]).status();

        let _ = Command::new("settings").args(["put", "global", "enable_gpu_debug_layers", "1"]).status();
        let _ = Command::new("settings").args(["put", "global", "gpu_debug_app", pkg]).status();
        let _ = Command::new("settings").args(["put", "global", "gpu_debug_layers_gles", "libluminagfx.so"]).status();

        log!("[TEXTURE] Auto-hook armed for '{}' (detail: {}, sharp: {}, hard: {})", pkg, detail, sharp, hard);
    }

    pub fn restore() {
        let _ = Command::new("setprop").args(["debug.lumina.gfx.enabled", "0"]).status();
        let _ = Command::new("settings").args(["put", "global", "enable_gpu_debug_layers", "0"]).status();
        let _ = Command::new("settings").args(["delete", "global", "gpu_debug_app"]).status();
        let _ = Command::new("settings").args(["delete", "global", "gpu_debug_layers_gles"]).status();

        log!("[TEXTURE] GPU Debug Layers disarmed");
    }
}
