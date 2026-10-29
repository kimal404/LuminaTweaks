#[macro_use]
mod logger;
mod detector;
mod engine;
mod notifier;
mod display;

use std::thread;
use std::time::Duration;
use detector::{
    get_focused_package, is_app_enabled, is_app_lite_mode, is_app_retained,
    is_lite_mode_active, is_low_power_active, is_master_enabled,
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
    display::init_display_subsystem();

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

    let mut current_profile: Option<Profile> = None;
    let mut active_target_pkg: Option<String> = None;

    loop {
        if is_master_enabled() {
            let focused = get_focused_package();
            let is_global_lite = is_lite_mode_active();

            // 1. Cek apakah foreground saat ini adalah aplikasi target dengan saklar ON
            let newly_focused_target = focused.as_deref().filter(|pkg| is_app_enabled(pkg));

            let current_target = if let Some(pkg) = newly_focused_target {
                // Game target aktif di layar utama: kunci sesi ke game ini
                let pkg_str = pkg.to_string();
                active_target_pkg = Some(pkg_str.clone());
                Some(pkg_str)
            } else if let Some(ref saved_pkg) = active_target_pkg {
                // Sedang membuka floating app atau ke home: cek apakah game tadi masih ada di Recents & berjalan
                if is_app_enabled(saved_pkg) && is_app_retained(saved_pkg) {
                    // Masih hidup di background/recents: pertahankan profil game!
                    Some(saved_pkg.clone())
                } else {
                    // Pengguna menghapus game dari Recent Apps: sesi game berakhir
                    active_target_pkg = None;
                    None
                }
            } else {
                None
            };

            // 2. Tentukan target profil
            let target_profile = if let Some(ref target_pkg) = current_target {
                let is_per_app_lite = is_app_lite_mode(target_pkg);
                let is_lite = is_global_lite || is_per_app_lite;

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

            // 3. Terapkan engine & notifikasi HANYA jika terjadi transisi profil
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
            active_target_pkg = None;
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
