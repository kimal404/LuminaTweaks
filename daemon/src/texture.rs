use std::process::Command;
use crate::log;

pub struct TextureController;

impl TextureController {
    pub fn apply(pkg: &str, enabled: bool, detail: i32, sharp: bool, hard: bool) {
        if !enabled {
            Self::restore();
            return;
        }

        let _ = Command::new("setprop")
            .args(["debug.lumina.gfx.enabled", "1"])
            .status();
        let _ = Command::new("setprop")
            .args(["debug.lumina.gfx.detail", &detail.to_string()])
            .status();
        let _ = Command::new("setprop")
            .args(["debug.lumina.gfx.sharp", if sharp { "1" } else { "0" }])
            .status();
        let _ = Command::new("setprop")
            .args(["debug.lumina.gfx.hard", if hard { "1" } else { "0" }])
            .status();

        let _ = Command::new("settings")
            .args(["put", "global", "enable_gpu_debug_layers", "1"])
            .status();
        let _ = Command::new("settings")
            .args(["put", "global", "gpu_debug_app", pkg])
            .status();
        let _ = Command::new("settings")
            .args(["put", "global", "gpu_debug_layers_gles", "libluminagfx.so"])
            .status();

        log!(
            "[TEXTURE] Hook active -> App: {} | Detail: {} | Sharp: {} | Hard: {}",
            pkg, detail, sharp, hard
        );
    }

    pub fn restore() {
        let _ = Command::new("setprop")
            .args(["debug.lumina.gfx.enabled", "0"])
            .status();
        let _ = Command::new("settings")
            .args(["put", "global", "enable_gpu_debug_layers", "0"])
            .status();
        let _ = Command::new("settings")
            .args(["delete", "global", "gpu_debug_app"])
            .status();
        let _ = Command::new("settings")
            .args(["delete", "global", "gpu_debug_layers_gles"])
            .status();

        log!("[TEXTURE] GPU Debug Layers restored to normal");
    }
}
