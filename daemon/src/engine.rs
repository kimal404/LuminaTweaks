use crate::config::{AppProfile, GlobalConfig, LiteModeSetting};

pub struct PolicyEngine;

impl PolicyEngine {
    /// Menghitung apakah Lite Mode harus aktif untuk package aplikasi yang sedang berjalan
    pub fn calculate_effective_lite(
        current_pkg: &str,
        global: &GlobalConfig,
        apps: &[AppProfile],
    ) -> bool {
        // Cari profil spesifik aplikasi
        let profile = apps.iter().find(|item| item.pkg == current_pkg);

        match profile {
            Some(app) if app.saklar_utama => {
                // Jika saklar utama per-app aktif, evaluasi aturan spesifik
                match app.lite_mode {
                    LiteModeSetting::On => true,
                    LiteModeSetting::Off => false,
                    LiteModeSetting::Default => global.lite_mode_global,
                }
            }
            // Jika saklar utama per-app nonaktif atau profil belum terdaftar, ikuti setting global
            _ => global.lite_mode_global,
        }
    }

    /// Terapkan parameter kernel/sysfs sesuai nilai Lite Mode
    pub fn apply_lite_mode_state(is_active: bool) {
        if is_active {
            // Mode hemat daya / thermal headroom untuk gaming ringan
            println!("[LuminaD] Lite Mode AKTIF: Menyetel governor dan thermal throttling");
            // Contoh sysfs target:
            // let _ = std::fs::write("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq", "1800000");
        } else {
            // Mode performa normal / profil seimbang
            println!("[LuminaD] Lite Mode NONAKTIF: Mengembalikan konfigurasi default");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_effective_lite_mode_resolution() {
        let global_on = GlobalConfig { lite_mode_global: true };
        let global_off = GlobalConfig { lite_mode_global: false };

        let apps = vec![
            AppProfile {
                pkg: "com.mobile.legends".into(),
                saklar_utama: true,
                lite_mode: LiteModeSetting::On,
                dnd: false,
            },
            AppProfile {
                pkg: "com.miHoYo.GenshinImpact".into(),
                saklar_utama: true,
                lite_mode: LiteModeSetting::Off,
                dnd: true,
            },
            AppProfile {
                pkg: "com.pubg.krmobile".into(),
                saklar_utama: true,
                lite_mode: LiteModeSetting::Default,
                dnd: false,
            },
            AppProfile {
                pkg: "com.disabled.app".into(),
                saklar_utama: false,
                lite_mode: LiteModeSetting::On, // Harus diabaikan karena saklar_utama false
                dnd: false,
            },
        ];

        // Override ON
        assert!(PolicyEngine::calculate_effective_lite("com.mobile.legends", &global_off, &apps));
        // Override OFF
        assert!(!PolicyEngine::calculate_effective_lite("com.miHoYo.GenshinImpact", &global_on, &apps));
        // Default mengikuti global OFF
        assert!(!PolicyEngine::calculate_effective_lite("com.pubg.krmobile", &global_off, &apps));
        // Default mengikuti global ON
        assert!(PolicyEngine::calculate_effective_lite("com.pubg.krmobile", &global_on, &apps));
        // Saklar utama mati -> fallback ke global
        assert!(!PolicyEngine::calculate_effective_lite("com.disabled.app", &global_off, &apps));
        assert!(PolicyEngine::calculate_effective_lite("com.disabled.app", &global_on, &apps));
    }
}
