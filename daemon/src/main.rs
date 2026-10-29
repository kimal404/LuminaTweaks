#[macro_use]
mod logger;
mod detector;
mod engine;
mod notifier;

use std::thread;
use std::time::Duration;
use detector::{get_focused_package, is_app_enabled, is_low_power_active, is_master_enabled};
use notifier::{send_notification, NotificationPayload};

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum Profile {
    Eco,
    Balance,
    Performance,
}

fn main() {
    log!("[*] luminad daemon started successfully");

    let mut current_profile = Profile::Balance;

    loop {
        if is_master_enabled() {
            let focused = get_focused_package();
            let is_target_active = focused.as_deref().map_or(false, is_app_enabled);

            if is_target_active {
                if current_profile != Profile::Performance {
                    current_profile = Profile::Performance;
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
            } else if is_low_power_active() {
                if current_profile != Profile::Eco {
                    current_profile = Profile::Eco;
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
            } else if current_profile != Profile::Balance {
                current_profile = Profile::Balance;
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

        thread::sleep(Duration::from_millis(1200));
    }
}
