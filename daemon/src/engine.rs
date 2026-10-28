use crate::config::{AppProfile, GlobalConfig, LiteModeSetting};
use crate::log_sub;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::process::Command;

pub struct PolicyEngine;

impl PolicyEngine {
    pub fn calculate_effective_lite(
        current_pkg: &str,
        global: &GlobalConfig,
        apps: &[AppProfile],
    ) -> bool {
        let profile = apps.iter().find(|item| item.pkg == current_pkg);

        match profile {
            Some(app) => {
                if !app.saklar_utama {
                    return global.lite_mode_global;
                }
                match app.lite_mode {
                    LiteModeSetting::On => true,
                    LiteModeSetting::Off => false,
                    LiteModeSetting::Default => global.lite_mode_global,
                }
            }
            None => global.lite_mode_global,
        }
    }

    pub fn calculate_effective_dnd(current_pkg: &str, apps: &[AppProfile]) -> bool {
        apps.iter()
            .find(|item| item.pkg == current_pkg)
            .map(|item| item.saklar_utama && item.dnd)
            .unwrap_or(false)
    }

    pub fn apply_dnd_state(is_active: bool) {
        let status = if is_active { "on" } else { "off" };
        let zen_val = if is_active { "1" } else { "0" };

        let res = Command::new("cmd")
            .args(["notification", "set_dnd", status])
            .output();

        if res.is_err() || !res.as_ref().unwrap().status.success() {
            let _ = Command::new("settings")
                .args(["put", "global", "zen_mode", zen_val])
                .status();
        }

        log_sub!(
            "DND",
            "Subsystem state update: ZEN_MODE -> {} ({})",
            zen_val,
            if is_active { "ACTIVE" } else { "RESTORED" }
        );
    }

    pub fn apply_lite_mode_state(is_active: bool) {
        // 1. CPU Frequency & EPP Scaling
        let mut freq_summaries = Vec::new();
        let cpufreq_dir = Path::new("/sys/devices/system/cpu/cpufreq");

        if cpufreq_dir.is_dir() {
            if let Ok(entries) = fs::read_dir(cpufreq_dir) {
                let mut sorted_entries: Vec<_> = entries.flatten().collect();
                sorted_entries.sort_by_key(|e| e.file_name());

                for entry in sorted_entries {
                    let path = entry.path();
                    let name = path.file_name().unwrap_or_default().to_string_lossy().to_string();
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
                                if Self::write_sysfs(&max_freq_node, &capped.to_string()).is_ok() {
                                    freq_summaries.push(format!("{}: {}MHz", name, capped / 1000));
                                }
                            }
                        }

                        if epp_node.exists() {
                            let _ = Self::write_sysfs(&epp_node, "balance_power");
                        }
                    } else {
                        if let Ok(info_str) = fs::read_to_string(&cpuinfo_max_node) {
                            let max_val = info_str.trim();
                            if Self::write_sysfs(&max_freq_node, max_val).is_ok() {
                                if let Ok(mhz) = max_val.parse::<u64>() {
                                    freq_summaries.push(format!("{}: {}MHz", name, mhz / 1000));
                                }
                            }
                        }

                        if epp_node.exists() {
                            let _ = Self::write_sysfs(&epp_node, "default");
                        }
                    }
                }
            }
        }

        if !freq_summaries.is_empty() {
            if is_active {
                log_sub!(
                    "CPU_FREQ",
                    "CPU max limit set (80%): [{}]",
                    freq_summaries.join(", ")
                );
            } else {
                log_sub!(
                    "CPU_FREQ",
                    "Hardware limits restored (100%): [{}]",
                    freq_summaries.join(", ")
                );
            }
        }

        // 2. EAS Cgroup Uclamp Tuning
        let cgroup_uclamps = [
            "/dev/cpuctl/top-app/cpu.uclamp.max",
            "/dev/cpuctl/cpu.uclamp.max",
        ];

        for node_str in &cgroup_uclamps {
            let p = Path::new(node_str);
            if p.exists() {
                let target_val = if is_active { "80" } else { "max" };
                if Self::write_sysfs(p, target_val).is_ok() {
                    log_sub!("UCLAMP", "Top-app max clamp set: {}", target_val);
                    break;
                }
            }
        }
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
            .map_err(|e| format!("{}: {}", p.display(), e))?;

        file.write_all(value.as_bytes())
            .map_err(|e| format!("{}: {}", p.display(), e))?;

        Ok(())
    }
}
