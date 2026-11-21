use super::SocProfile;
use std::fs;
use std::path::Path;

const KGSL_CANDIDATES: &[&str] = &["/sys/class/kgsl/kgsl-3d0", "/sys/class/kgsl/kgsl-2d0"];
const DDR_NEEDLES: &[&str] = &["memlat", "latfloor", "ddr-lat"];
const BUS_COMPONENTS: &[&str] = &["DDR", "LLCC", "L3"];

fn kgsl_base() -> Option<&'static str> {
    KGSL_CANDIDATES.iter().copied().find(|base| {
        Path::new(base).is_dir()
            && Path::new(base).join("devfreq").is_dir()
            && Path::new(base).join("num_pwrlevels").exists()
    })
}

fn kgsl_devfreq() -> Option<String> {
    KGSL_CANDIDATES
        .iter()
        .copied()
        .find(|base| Path::new(base).join("devfreq").is_dir())
        .map(|base| format!("{}/devfreq", base))
}

fn kgsl_node(name: &str) -> Option<String> {
    let base = kgsl_base()?;
    let path = format!("{}/{}", base, name);
    Path::new(&path).exists().then_some(path)
}

fn set_pwrlevels(performance: bool) -> usize {
    let Some(base) = kgsl_base() else {
        return 0;
    };
    let Some(count) = fs::read_to_string(format!("{}/num_pwrlevels", base))
        .ok()
        .and_then(|value| value.trim().parse::<u64>().ok())
    else {
        return 0;
    };
    let min_value = if performance { 0 } else { count };
    let mut applied = 0;
    if super::apply_node(&format!("{}/min_pwrlevel", base), &min_value.to_string()) {
        applied += 1;
    }
    if super::apply_node(&format!("{}/max_pwrlevel", base), "0") {
        applied += 1;
    }
    applied
}

fn kgsl_flags(bus_split: &str, force_clk_on: &str) -> usize {
    let mut applied = 0;
    if let Some(path) = kgsl_node("bus_split") {
        if super::apply_node(&path, bus_split) {
            applied += 1;
        }
    }
    if let Some(path) = kgsl_node("force_clk_on") {
        if super::apply_node(&path, force_clk_on) {
            applied += 1;
        }
    }
    applied
}

fn ddr_profile(lite: bool) -> usize {
    if crate::tweaks::mitigation::has("DISABLE_DDR_TWEAK") {
        return 0;
    }
    let mut applied = 0;
    for base in super::devfreq_matching(DDR_NEEDLES) {
        applied += if lite {
            super::devfreq_mid_perf(&base)
        } else {
            super::devfreq_max_perf(&base)
        };
    }
    for component in BUS_COMPONENTS {
        let base = format!("/sys/devices/system/cpu/bus_dcvs/{}", component);
        if !Path::new(&base).is_dir() {
            continue;
        }
        applied += if lite {
            super::apply_profile_cpudcvs(&base, SocProfile::Lite)
        } else {
            super::apply_profile_cpudcvs(&base, SocProfile::Maestro)
        };
    }
    applied
}

fn ddr_balance() -> usize {
    let mut applied = 0;
    for base in super::devfreq_matching(DDR_NEEDLES) {
        applied += super::devfreq_unlock(&base);
    }
    for component in BUS_COMPONENTS {
        let base = format!("/sys/devices/system/cpu/bus_dcvs/{}", component);
        if Path::new(&base).is_dir() {
            applied += super::apply_profile_cpudcvs(&base, SocProfile::Balance);
        }
    }
    applied
}

fn lite_profile() -> usize {
    let mut applied = ddr_profile(true);
    if let Some(base) = kgsl_devfreq() {
        applied += super::devfreq_unlock(&base);
    }
    applied += set_pwrlevels(false);
    applied += kgsl_flags("1", "0");
    applied
}

fn maestro_profile() -> usize {
    let mut applied = ddr_profile(false);
    if let Some(base) = kgsl_devfreq() {
        applied += super::devfreq_unlock(&base);
    }
    applied += set_pwrlevels(false);
    applied += kgsl_flags("1", "0");
    applied
}

fn performance_profile() -> usize {
    let mut applied = ddr_profile(false);
    if let Some(base) = kgsl_devfreq() {
        applied += super::devfreq_max_perf(&base);
    }
    applied += set_pwrlevels(true);
    applied += kgsl_flags("0", "1");
    applied
}

fn balance_profile() -> usize {
    let mut applied = ddr_balance();
    if let Some(base) = kgsl_devfreq() {
        applied += super::devfreq_unlock(&base);
    }
    applied += set_pwrlevels(false);
    applied += kgsl_flags("1", "0");
    applied
}

fn powersave_profile() -> usize {
    if crate::tweaks::mitigation::has("QCOM_NO_GPU_POWERSAVE") {
        return 0;
    }
    let Some(base) = kgsl_devfreq() else {
        return 0;
    };
    super::devfreq_min_perf(&base)
}

pub(crate) fn frame_boost() -> usize {
    let mut applied = ddr_profile(false);
    if let Some(base) = kgsl_devfreq() {
        applied += super::devfreq_max_perf(&base);
    }
    applied
}

pub(crate) fn frame_relax() -> usize {
    let mut applied = ddr_balance();
    if let Some(base) = kgsl_devfreq() {
        applied += super::devfreq_unlock(&base);
    }
    applied
}

pub fn apply(profile: SocProfile) -> usize {
    match profile {
        SocProfile::Lite => lite_profile(),
        SocProfile::Balance => balance_profile(),
        SocProfile::Eco => powersave_profile(),
        SocProfile::Maestro => maestro_profile(),
        SocProfile::Performance => performance_profile(),
    }
}

#[cfg(test)]
mod tests {
    use super::{BUS_COMPONENTS, DDR_NEEDLES};

    #[test]
    fn covers_qualcomm_bus_components() {
        assert!(BUS_COMPONENTS.contains(&"DDR"));
        assert!(BUS_COMPONENTS.contains(&"LLCC"));
        assert!(BUS_COMPONENTS.contains(&"L3"));
    }

    #[test]
    fn covers_qualcomm_latency_nodes() {
        assert!(DDR_NEEDLES.contains(&"memlat"));
        assert!(DDR_NEEDLES.contains(&"latfloor"));
        assert!(DDR_NEEDLES.contains(&"ddr-lat"));
    }
}
