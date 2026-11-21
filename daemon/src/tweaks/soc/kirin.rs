use super::SocProfile;
use std::path::Path;

const GPU_DIRS: &[&str] = &[
    "/sys/class/devfreq/gpufreq",
    "/sys/devices/platform/gpufreq",
    "/sys/kernel/gpu",
];
const GPU_NEEDLES: &[&str] = &["gpu", "mali"];

fn gpu_devfreq() -> Option<String> {
    for dir in GPU_DIRS {
        if Path::new(dir).join("available_frequencies").exists() {
            return Some((*dir).to_string());
        }
    }
    super::devfreq_matching(GPU_NEEDLES).into_iter().next()
}

pub(crate) fn frame_boost() -> usize {
    gpu_devfreq()
        .map(|base| super::devfreq_max_perf(&base))
        .unwrap_or(0)
}

pub(crate) fn frame_relax() -> usize {
    gpu_devfreq()
        .map(|base| super::devfreq_unlock(&base))
        .unwrap_or(0)
}

pub fn apply(profile: SocProfile) -> usize {
    let Some(base) = gpu_devfreq() else {
        return 0;
    };
    match profile {
        SocProfile::Lite => super::devfreq_mid_perf(&base),
        SocProfile::Balance => super::devfreq_unlock(&base),
        SocProfile::Eco => super::devfreq_min_perf(&base),
        SocProfile::Maestro => super::devfreq_unlock(&base),
        SocProfile::Performance => super::devfreq_max_perf(&base),
    }
}

#[cfg(test)]
mod tests {
    use super::GPU_NEEDLES;

    #[test]
    fn covers_kirin_gpu_nodes() {
        assert!(GPU_NEEDLES.contains(&"gpu"));
    }
}
