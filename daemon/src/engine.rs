use crate::config::{AppProfile, GlobalConfig, LiteModeSetting};
use crate::{log_debug, log_info, log_warn};
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;

pub struct PolicyEngine;

impl PolicyEngine {
    pub fn calculate_effective_lite(
        current_pkg: &str,
        global: &GlobalConfig,
        apps: &[AppProfile],
        should_log: bool,
    ) -> bool {
        let profile = apps.iter().find(|item| item.pkg == current_pkg);

        match profile {
            Some(app) => {
                if !app.saklar_utama {
                    if should_log {
                        log_debug!(
                            "Engine",
                            "App '{}' terdaftar tetapi Saklar Utama MATI -> Fallback ke global ({})",
                            current_pkg,
                            global.lite_mode_global
                        );
                    }
                    return global.lite_mode_global;
                }

                match app.lite_mode {
                    LiteModeSetting::On => {
                        if should_log {
                            log_debug!("Engine", "App '{}' Saklar Utama HIDUP | Mode: FORCE ON", current_pkg);
                        }
                        true
                    }
                    LiteModeSetting::Off => {
                        if should_log {
                            log_debug!("Engine", "App '{}' Saklar Utama HIDUP | Mode: FORCE OFF", current_pkg);
                        }
                        false
                    }
                    LiteModeSetting::Default => {
                        if should_log {
                            log_debug!(
                                "Engine",
                                "App '{}' Saklar Utama HIDUP | Mode: DEFAULT -> Mengikuti global ({})",
                                current_pkg,
                                global.lite_mode_global
                            );
                        }
                        global.lite_mode_global
                    }
                }
            }
            None => {
                if should_log {
                    log_debug!(
                        "Engine",
                        "App '{}' tidak terdaftar -> Fallback ke global ({})",
                        current_pkg,
                        global.lite_mode_global
                    );
                }
                global.lite_mode_global
            }
        }
    }

    pub fn apply_lite_mode_state(is_active: bool) {
        log_info!(
            "Kernel",
            "Menerapkan konfigurasi sysfs | Target Lite Mode: {}",
            if is_active { "AKTIF" } else { "NONAKTIF" }
        );

        let mut total_attempts = 0;
        let mut total_success = 0;

        // 1. CPU Frequency & EPP Scaling
        let cpufreq_dir = Path::new("/sys/devices/system/cpu/cpufreq");
        if cpufreq_dir.is_dir() {
            if let Ok(entries) = fs::read_dir(cpufreq_dir) {
                for entry in entries.flatten() {
                    let path = entry.path();
                    let name = path.file_name().unwrap_or_default().to_string_lossy();
                    if !name.starts_with("policy") {
                        continue;
                    }

                    let max_freq_node = path.join("scaling_max_freq");
                    let cpuinfo_max_node = path.join("cpuinfo_max_freq");
                    let epp_node = path.join("energy_performance_preference");

                    if is_active {
                        if let Ok(info_str) = fs::read_to_string(&cpuinfo_max_node) {
                            if let Ok(hw_max) = info_str.trim().parse::<u64>() {
                                let capped = (hw_max * 80) / 100;
                                total_attempts += 1;
                                match Self::write_sysfs(&max_freq_node, &capped.to_string()) {
                                    Ok(_) => {
                                        total_success += 1;
                                        log_debug!("Sysfs", "[{}] scaling_max_freq -> {} kHz (OK)", name, capped);
                                    }
                                    Err(err) => {
                                        log_warn!("Sysfs", "[{}] Gagal membatasi scaling_max_freq: {}", name, err);
                                    }
                                }
                            }
                        }

                        if epp_node.exists() {
                            total_attempts += 1;
                            match Self::write_sysfs(&epp_node, "balance_power") {
                                Ok(_) => {
                                    total_success += 1;
                                    log_debug!("Sysfs", "[{}] EPP -> balance_power (OK)", name);
                                }
                                Err(err) => {
                                    log_warn!("Sysfs", "[{}] Gagal menyetel EPP: {}", name, err);
                                }
                            }
                        }
                    } else {
                        if let Ok(info_str) = fs::read_to_string(&cpuinfo_max_node) {
                            total_attempts += 1;
                            match Self::write_sysfs(&max_freq_node, info_str.trim()) {
                                Ok(_) => {
                                    total_success += 1;
                                    log_debug!("Sysfs", "[{}] scaling_max_freq -> {} kHz (RESTORED)", name, info_str.trim());
                                }
                                Err(err) => {
                                    log_warn!("Sysfs", "[{}] Gagal me-restore scaling_max_freq: {}", name, err);
                                }
                            }
                        }

                        if epp_node.exists() {
                            total_attempts += 1;
                            match Self::write_sysfs(&epp_node, "default") {
                                Ok(_) => {
                                    total_success += 1;
                                    log_debug!("Sysfs", "[{}] EPP -> default (RESTORED)", name);
                                }
                                Err(err) => {
                                    log_warn!("Sysfs", "[{}] Gagal me-restore EPP: {}", name, err);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. EAS Cgroup Uclamp Tuning (Top-App)
        let cgroup_uclamps = [
            "/dev/cpuctl/top-app/cpu.uclamp.max",
            "/dev/cpuctl/cpu.uclamp.max",
        ];

        for node_str in &cgroup_uclamps {
            let p = Path::new(node_str);
            if p.exists() {
                total_attempts += 1;
                let target_val = if is_active { "80" } else { "max" };
                match Self::write_sysfs(p, target_val) {
                    Ok(_) => {
                        total_success += 1;
                        log_debug!("Sysfs", "{} -> {} (OK)", node_str, target_val);
                    }
                    Err(err) => {
                        if is_active && Self::write_sysfs(p, "800").is_ok() {
                            total_success += 1;
                            log_debug!("Sysfs", "{} -> 800 (Scale 1024 OK)", node_str);
                        } else {
                            log_warn!("Sysfs", "Gagal menyetel {}: {}", node_str, err);
                        }
                    }
                }
            }
        }

        log_info!(
            "Kernel",
            "Eksekusi sysfs selesai: {}/{} node berhasil diperbarui.",
            total_success,
            total_attempts
        );
    }

    fn write_sysfs<P: AsRef<Path>>(path: P, value: &str) -> Result<(), String> {
        let p = path.as_ref();

        if let Ok(metadata) = fs::metadata(p) {
            let mut permissions = metadata.permissions();
            if permissions.mode() & 0o200 == 0 {
                permissions.set_mode(0o644);
                let _ = fs::set_permissions(p, permissions);
            }
        }

        let mut file = OpenOptions::new()
            .write(true)
            .open(p)
            .map_err(|e| format!("Gagal membuka file {}: {}", p.display(), e))?;

        file.write_all(value.as_bytes())
            .map_err(|e| format!("Gagal menulis data ke {}: {}", p.display(), e))?;

        Ok(())
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
                lite_mode: LiteModeSetting::On,
                dnd: false,
            },
        ];

        assert!(PolicyEngine::calculate_effective_lite("com.mobile.legends", &global_off, &apps, false));
        assert!(!PolicyEngine::calculate_effective_lite("com.miHoYo.GenshinImpact", &global_on, &apps, false));
        assert!(!PolicyEngine::calculate_effective_lite("com.pubg.krmobile", &global_off, &apps, false));
        assert!(PolicyEngine::calculate_effective_lite("com.pubg.krmobile", &global_on, &apps, false));
        assert!(!PolicyEngine::calculate_effective_lite("com.disabled.app", &global_off, &apps, false));
        assert!(PolicyEngine::calculate_effective_lite("com.disabled.app", &global_on, &apps, false));
    }
}
