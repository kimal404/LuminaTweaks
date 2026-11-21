use super::SocProfile;

const GPU_NEEDLES: &[&str] = &["gpu"];

fn gpu_devfreq() -> Option<String> {
    super::devfreq_matching(GPU_NEEDLES).into_iter().next()
}

pub fn apply(profile: SocProfile) -> usize {
    let Some(base) = gpu_devfreq() else {
        return 0;
    };
    match profile {
        SocProfile::Lite => super::devfreq_unlock(&base),
        SocProfile::Balance => super::devfreq_unlock(&base),
        SocProfile::Eco => super::devfreq_min_perf(&base),
        SocProfile::Maestro => super::devfreq_unlock(&base),
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn covers_unisoc_gpu_devfreq() {
        assert!(super::GPU_NEEDLES.contains(&"gpu"));
    }
}
