#[macro_use]
mod logger;
mod detector;
mod engine;
mod notifier;

use std::thread;
use std::time::Duration;
use detector::{
    get_focused_package, is_app_enabled, is_app_lite_mode, is_lite_mode_active,
    is_low_power_active, is_master_enabled,
};
use notifier::{send_notification, NotificationPayload};

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum Profile {
    Eco,
    Balance,
    Performance,
    PerformanceLite,
}

fn main() {
    log!("[*] luminad daemon started successfully");

    // Sequence Notifikasi Startup
    send_notification(NotificationPayload {
        title: "Initializing...",
        text: "Starting LuminaTweaks service...",
        is_profile: true,
        chrono: false,
        ..Default::default()
    });

    thread::sleep(Duration::from_millis(3000));

    send_notification(NotificationPayload {
        title: "LuminaTweaks Active",
        text: "Daemon running successfully",
        is_profile: true,
        timeout_ms: 60000,
        ..Default::default()
    });

    // Inisialisasi awal None agar profil pertama langsung trigger apply & notifikasi
    let mut current_profile: Option<Profile> = None;

    loop {
        if is_master_enabled() {
            let focused = get_focused_package();
            let is_target_active = focused.as_deref().map_or(false, is_app_enabled);
            let is_global_lite = is_lite_mode_active();
            let is_per_app_lite = focused.as_deref().map_or(false, is_app_lite_mode);

            let is_lite = is_global_lite || is_per_app_lite;

            let target_profile = if is_target_active {
                if is_lite {
                    Profile::PerformanceLite
                } else {
                    Profile::Performance
                }
            } else if is_low_power_active() || is_global_lite {
                Profile::Eco
            } else {
                Profile::Balance
            };

            if current_profile != Some(target_profile) {
                match target_profile {
                    Profile::Performance => {
                        engine::performance_profile();
                        send_notification(NotificationPayload {
                            title: "Lumina: Performance",
                            text: "Target App Active • Boost Engaged",
                            toast: Some("Performance Profile Applied"),
                            is_profile: true,
                            chrono: true,
                            ..Default::default()
                        });
                    }
                    Profile::PerformanceLite => {
                        engine::performance_lite_profile();
                        send_notification(NotificationPayload {
                            title: "Lumina: Performance Lite",
                            text: "Target App Active • Lite Boost (No SoC Lock)",
                            toast: Some("Performance Lite Profile Applied"),
                            is_profile: true,
                            chrono: true,
                            ..Default::default()
                        });
                    }
                    Profile::Eco => {
                        engine::eco_profile();
                        send_notification(NotificationPayload {
                            title: "ECO Mode",
                            text: "System is now at Endurance state",
                            toast: Some("Applying Eco Mode"),
                            is_profile: true,
                            chrono: true,
                            ..Default::default()
                        });
                    }
                    Profile::Balance => {
                        engine::balance_profile();
                        send_notification(NotificationPayload {
                            title: "Lumina: Balanced",
                            text: "System is now at Dynamic State",
                            toast: Some("Balanced Profile Restored"),
                            is_profile: true,
                            chrono: true,
                            ..Default::default()
                        });
                    }
                }
                current_profile = Some(target_profile);
            }
        } else if current_profile != Some(Profile::Balance) {
            current_profile = Some(Profile::Balance);
            engine::balance_profile();
            send_notification(NotificationPayload {
                title: "Lumina: Disabled",
                text: "Master Switch Off • Reverted to Balance",
                toast: Some("Lumina Master Switch Disabled"),
                is_profile: true,
                chrono: true,
                ..Default::default()
            });
        }

        thread::sleep(Duration::from_millis(1200));
    }
}
