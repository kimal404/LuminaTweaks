#[macro_use]
mod logger;
mod detector;
mod engine;
mod notifier;

use std::thread;
use std::time::Duration;
use detector::{
    get_focused_package, is_app_enabled, is_lite_mode_active, is_low_power_active,
    is_master_enabled,
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

    let mut current_profile = Profile::Balance;

    loop {
        if is_master_enabled() {
            let focused = get_focused_package();
            let is_target_active = focused.as_deref().map_or(false, is_app_enabled);
            let is_lite = is_lite_mode_active();

            let target_profile = if is_target_active {
                if is_lite {
                    Profile::PerformanceLite
                } else {
                    Profile::Performance
                }
            } else if is_low_power_active() || is_lite {
                Profile::Eco
            } else {
                Profile::Balance
            };

            if current_profile != target_profile {
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
                current_profile = target_profile;
            }
        } else if current_profile != Profile::Balance {
            current_profile = Profile::Balance;
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
