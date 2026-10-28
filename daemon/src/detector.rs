use crate::log_sub;
use std::fs;
use std::path::Path;
use std::process::Command;

pub struct ForegroundDetector;

impl ForegroundDetector {
    const TOP_APP_CGROUP_PATHS: &'static [&'static str] = &[
        "/dev/cpuset/top-app/cgroup.procs",
        "/sys/fs/cgroup/top-app/cgroup.procs",
        "/dev/cpuctl/top-app/cgroup.procs",
        "/sys/fs/cgroup/cpuset/top-app/cgroup.procs",
    ];

    pub fn get_foreground_package() -> Option<String> {
        if let Some(pkg) = Self::detect_via_cgroup() {
            return Some(pkg);
        }

        Self::detect_via_dumpsys()
    }

    fn detect_via_cgroup() -> Option<String> {
        for &path_str in Self::TOP_APP_CGROUP_PATHS {
            let path = Path::new(path_str);
            if !path.exists() {
                continue;
            }

            if let Ok(content) = fs::read_to_string(path) {
                let mut pids: Vec<i32> = content
                    .lines()
                    .filter_map(|line| line.trim().parse::<i32>().ok())
                    .collect();

                pids.reverse();

                for pid in pids {
                    if let Some(pkg) = Self::get_package_from_pid(pid) {
                        if Self::is_ignored_process(&pkg) {
                            continue;
                        }
                        return Some(pkg);
                    }
                }
            }
        }
        None
    }

    fn is_ignored_process(pkg: &str) -> bool {
        let lower = pkg.to_lowercase();
        lower.contains(".inputmethod.")
            || lower.contains(".ime")
            || lower.contains("keyboard")
            || lower.ends_with(".latin")
            || lower == "com.touchtype.swiftkey"
            || lower == "com.android.systemui"
            || lower == "com.miui.personalassistant"
            || lower == "com.miui.voiceassist"
            || lower == "com.google.android.googlequicksearchbox"
    }

    fn get_package_from_pid(pid: i32) -> Option<String> {
        let status_path = format!("/proc/{}/status", pid);
        let status_content = fs::read_to_string(&status_path).ok()?;

        let uid_line = status_content.lines().find(|l| l.starts_with("Uid:"))?;
        let real_uid = uid_line
            .split_whitespace()
            .nth(1)?
            .parse::<u32>()
            .ok()?;

        let app_id = real_uid % 100000;
        if app_id < 10000 {
            return None;
        }

        let cmdline_path = format!("/proc/{}/cmdline", pid);
        let cmdline_bytes = fs::read(&cmdline_path).ok()?;
        if cmdline_bytes.is_empty() {
            return None;
        }

        let raw_cmd = String::from_utf8_lossy(&cmdline_bytes);
        let first_token = raw_cmd.split('\0').next()?.trim();
        if first_token.is_empty() {
            return None;
        }

        let base_pkg = first_token.split(':').next()?.trim();
        let clean_pkg = if base_pkg.starts_with('/') {
            if let Some(pos) = base_pkg.find("/com.") {
                let sub = &base_pkg[pos + 1..];
                sub.split('/').next().unwrap_or(base_pkg)
            } else {
                return None;
            }
        } else {
            base_pkg
        };

        if clean_pkg.contains('.') {
            return Some(clean_pkg.to_string());
        }

        None
    }

    fn detect_via_dumpsys() -> Option<String> {
        let output = Command::new("dumpsys").args(["window"]).output().ok()?;
        if !output.status.success() {
            return None;
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        for line in stdout.lines() {
            if line.contains("mCurrentFocus") || line.contains("mFocusedApp") {
                if let Some(pos) = line.find(" u0 ") {
                    let substr = &line[pos + 4..];
                    if let Some(end_pos) = substr.find('/') {
                        let pkg = substr[..end_pos].trim();
                        if !pkg.is_empty() && pkg.contains('.') && !Self::is_ignored_process(pkg) {
                            return Some(pkg.to_string());
                        }
                    }
                }
            }
        }
        None
    }
}
