use std::fs;
use std::path::Path;

const COOLING_DIRS: &[&str] = &["/sys/class/thermal", "/sys/devices/virtual/thermal"];

pub fn apply_kernel_governor() -> usize {
    let mut applied = 0;
    for dir in COOLING_DIRS {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.starts_with("thermal_zone") {
                continue;
            }
            let path = format!("{}/{}/policy", dir, name);
            if Path::new(&path).exists()
                && crate::tweaks::engine::write_sysfs(&path, "step_wise", true)
            {
                applied += 1;
            }
        }
    }
    applied
}
