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

#![allow(dead_code)]
use std::process::Command;

pub struct NotificationPayload {
    pub title: &'static str,
    pub text: &'static str,
    pub toast: Option<&'static str>,
    pub is_profile: bool,
    pub chrono: bool,
    pub timeout_ms: u64,
}

impl Default for NotificationPayload {
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
    if let Ok(val) = std::fs::read_to_string("/data/adb/.config/lumina/toast_enabled.txt") {
        return val.trim() == "1";
    }
    Command::new("getprop")
        .arg("persist.lumina.showtoast")
        .output()
        .map(|out| String::from_utf8_lossy(&out.stdout).trim() == "1")
        .unwrap_or(false)
}

pub fn send_notification(payload: NotificationPayload) {
    let mut cmd = Command::new("am");
    cmd.arg("broadcast")
        .arg("--user").arg("0")
        .arg("-n").arg("com.luminatweaks/com.luminatweaks.receiver.LuminaReceiver")
        .arg("-a").arg("com.luminatweaks.ACTION_NOTIFY")
        .arg("-f").arg("0x11000020")
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

    std::thread::spawn(move || {
        let _ = cmd.status();
    });
}

pub fn clear_all_notifications() {
    std::thread::spawn(|| {
        let _ = Command::new("am")
            .arg("broadcast")
            .arg("--user").arg("0")
            .arg("-n").arg("com.luminatweaks/com.luminatweaks.receiver.LuminaReceiver")
            .arg("-a").arg("com.luminatweaks.ACTION_NOTIFY")
            .arg("-f").arg("0x11000020")
            .arg("--ez").arg("clearall").arg("true")
            .status();
    });
}
