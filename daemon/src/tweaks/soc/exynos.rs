use super::SocProfile;
use std::path::Path;

const GPU_DIRS: &[&str] = &["/sys/kernel/gpu", "/sys/devices/platform/gpu"];
const FREQ_TABLES: &[&str] = &["gpu_available_frequencies", "gpu_freq_table"];
const MIF_NEEDLES: &[&str] = &["devfreq_mif"];

fn gpu_dir() -> Option<&'static str> {
    GPU_DIRS
        .iter()
        .copied()
        .find(|dir| Path::new(dir).is_dir())
}

fn freq_table(dir: &str) -> Option<String> {
    FREQ_TABLES.iter().find_map(|name| {
        let path = format!("{}/{}", dir, name);
        Path::new(&path).exists().then_some(path)
    })
}

fn write_clock(dir: &str, primary: &str, fallback: &str, value: u64) -> usize {
    let primary_path = format!("{}/{}", dir, primary);
    if Path::new(&primary_path).exists() {
        return usize::from(super::apply_node(&primary_path, &value.to_string()));
    }
    let fallback_path = format!("{}/{}", dir, fallback);
    usize::from(super::apply_node(&fallback_path, &value.to_string()))
}

fn apply_gpu(profile: SocProfile) -> usize {
    let Some(dir) = gpu_dir() else {
        return 0;
    };
    let Some(table) = freq_table(dir) else {
        return 0;
    };
    let values = super::read_frequencies(&table);
    let (Some(min), Some(max)) = (
        crate::tweaks::devfreq::min_frequency(&values),
        crate::tweaks::devfreq::max_frequency(&values),
    ) else {
        return 0;
    };
    let (max_target, min_target) = match profile {
        SocProfile::Performance => (max, max),
        SocProfile::Lite => (max, min),
        SocProfile::Balance => (max, min),
        SocProfile::Eco => (min, min),
    };

    let mut applied = write_clock(dir, "gpu_max_clock", "scaling_max_freq", max_target);
    applied += write_clock(dir, "gpu_min_clock", "scaling_min_freq", min_target);
    applied
}

fn mif(profile: SocProfile) -> usize {
    if !matches!(profile, SocProfile::Performance | SocProfile::Lite | SocProfile::Balance) {
        return 0;
    }
    let mut applied = 0;
    for base in super::devfreq_matching(MIF_NEEDLES) {
        applied += match profile {
            SocProfile::Performance => super::devfreq_max_perf(&base),
            SocProfile::Lite => super::devfreq_mid_perf(&base),
            _ => super::devfreq_unlock(&base),
        };
    }
    applied
}

pub fn apply(profile: SocProfile) -> usize {
    let mut applied = apply_gpu(profile);
    let policy = match profile {
        SocProfile::Performance | SocProfile::Lite => SocProfile::Performance,
        SocProfile::Balance => SocProfile::Balance,
        SocProfile::Eco => SocProfile::Eco,
    };
    applied += super::mali::apply_power_policy(policy);
    applied += mif(profile);
    applied
}

#[cfg(test)]
mod tests {
    use super::{FREQ_TABLES, GPU_DIRS};

    #[test]
    fn has_exynos_gpu_nodes() {
        assert!(GPU_DIRS.contains(&"/sys/kernel/gpu"));
        assert!(FREQ_TABLES.contains(&"gpu_available_frequencies"));
        assert!(FREQ_TABLES.contains(&"gpu_freq_table"));
    }
}
