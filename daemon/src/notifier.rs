#![allow(dead_code)]
use std::process::Command;

pub struct NotificationPayload<'a> {
    pub title: &'a str,
    pub text: &'a str,
    pub toast: Option<&'a str>,
    pub is_profile: bool,
    pub chrono: bool,
    pub timeout_ms: u64,
}

impl<'a> Default for NotificationPayload<'a> {
    fn default() -> Self {
        Self {
            title: "LuminaTweaks",
            text: "",
            toast: None,
            is_profile: false,
            chrono: false,
            timeout_ms: 0,
        }
    }
}

fn is_toast_enabled() -> bool {
    Command::new("getprop")
        .arg("persist.lumina.showtoast")
        .output()
        .map(|out| String::from_utf8_lossy(&out.stdout).trim() == "1")
        .unwrap_or(false)
}

pub fn send_notification(payload: NotificationPayload) {
    let mut cmd = Command::new("am");
    cmd.arg("broadcast")
       .arg("-a").arg("com.luminatweaks.ACTION_NOTIFY")
       .arg("-p").arg("com.luminatweaks")
       .arg("--receiver-foreground")
       .arg("-f").arg("0x01000020")
       .arg("--es").arg("notifytitle").arg(payload.title)
       .arg("--es").arg("notifytext").arg(payload.text);

    if is_toast_enabled() {
        if let Some(toast) = payload.toast {
            cmd.arg("--es").arg("toasttext").arg(toast);
        }
    }

    if payload.is_profile {
        cmd.arg("--ez").arg("is_profile").arg("true");
    }

    if payload.chrono {
        cmd.arg("--ez").arg("chrono").arg("true");
    }

    if payload.timeout_ms > 0 {
        cmd.arg("--el").arg("timeout").arg(payload.timeout_ms.to_string());
    }

    let _ = cmd.spawn();
}

pub fn clear_all_notifications() {
    let _ = Command::new("am")
        .arg("broadcast")
        .arg("-a").arg("com.luminatweaks.ACTION_NOTIFY")
        .arg("-p").arg("com.luminatweaks")
        .arg("--receiver-foreground")
        .arg("-f").arg("0x01000020")
        .arg("--ez").arg("clearall").arg("true")
        .spawn();
}
