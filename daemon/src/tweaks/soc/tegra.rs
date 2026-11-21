use super::SocProfile;
use std::path::Path;

const GPU_DIRS: &[&str] = &["/sys/kernel/tegra_gpu", "/sys/devices/platform/tegra_gpu"];

fn gpu_dir() -> Option<&'static str> {
    GPU_DIRS
        .iter()
        .copied()
        .find(|dir| Path::new(dir).join("available_frequencies").exists())
}

fn set_rate(dir: &str, node: &str, value: u64) -> usize {
    usize::from(super::apply_node(
        &format!("{}/{}", dir, node),
        &value.to_string(),
    ))
}

pub fn apply(profile: SocProfile) -> usize {
    let Some(dir) = gpu_dir() else {
        return 0;
    };
    let values = super::read_frequencies(&format!("{}/available_frequencies", dir));
    let (Some(min), Some(max)) = (
        crate::tweaks::devfreq::min_frequency(&values),
        crate::tweaks::devfreq::max_frequency(&values),
    ) else {
        return 0;
    };
    let (floor, cap) = match profile {
        SocProfile::Lite => (min, max),
        SocProfile::Balance => (min, max),
        SocProfile::Eco => (min, min),
        SocProfile::Maestro => (min, max),
    };

    let mut applied = set_rate(dir, "gpu_cap_rate", cap);
    applied += set_rate(dir, "gpu_floor_rate", floor);
    applied
}

#[cfg(test)]
mod tests {
    use super::GPU_DIRS;

    #[test]
    fn has_tegra_gpu_node() {
        assert!(GPU_DIRS.contains(&"/sys/kernel/tegra_gpu"));
    }
}
