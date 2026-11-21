pub mod exynos;
pub mod generic;
pub mod kirin;
pub mod mali;
pub mod mediatek;
pub mod snapdragon;
pub mod tegra;
pub mod tensor;
pub mod unisoc;

use crate::tweaks::devfreq;
use crate::tweaks::state;
use std::fs;
use std::path::Path;
use std::sync::OnceLock;

const SOC_CONFIG_PATHS: &[&str] = &[
    "/data/adb/.config/lumina/soc.conf",
    "/data/adb/modules/luminatweaks/config/soc.conf",
    "/data/adb/modules_update/luminatweaks/config/soc.conf",
    "/data/adb/modules/luminatweaks/soc.conf",
    "/data/adb/modules/lumina_tweaks/soc.conf",
];

const SNAPDRAGON_PLATFORMS: &[&str] = &[
    "bengal",
    "blair",
    "cape",
    "chora",
    "cliffs",
    "crow",
    "holi",
    "kalama",
    "khaje",
    "kodiak",
    "kona",
    "lahaina",
    "lemans",
    "lito",
    "malabar",
    "orchid",
    "parrot",
    "pineapple",
    "pitti",
    "ravelin",
    "scuba",
    "shima",
    "sun",
    "taro",
    "ukee",
    "volcano",
    "waipio",
    "yupik",
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Vendor {
    Mediatek,
    Snapdragon,
    Exynos,
    Unisoc,
    Tensor,
    Tegra,
    Kirin,
    Generic,
}

impl Vendor {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Mediatek => "MediaTek",
            Self::Snapdragon => "Snapdragon",
            Self::Exynos => "Exynos",
            Self::Unisoc => "Unisoc",
            Self::Tensor => "Tensor",
            Self::Tegra => "Tegra",
            Self::Kirin => "Kirin",
            Self::Generic => "Generic",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SocProfile {
    Performance,
    Lite,
    Balance,
    Eco,
    Maestro,
}

pub fn apply(profile: SocProfile) -> usize {
    let vendor = vendor();
    let applied = match vendor {
        Vendor::Mediatek => mediatek::apply(profile),
        Vendor::Snapdragon => snapdragon::apply(profile),
        Vendor::Exynos => exynos::apply(profile),
        Vendor::Unisoc => unisoc::apply(profile),
        Vendor::Tensor => tensor::apply(profile),
        Vendor::Tegra => tegra::apply(profile),
        Vendor::Kirin => kirin::apply(profile),
        Vendor::Generic => generic::apply(profile),
    };
    log!(
        "[SOC] {} profile {:?} applied to {} node(s)",
        vendor.as_str(),
        profile,
        applied
    );
    applied
}

pub(crate) fn apply_node(path: &str, value: &str) -> bool {
    if !Path::new(path).exists() {
        return false;
    }
    state::write_tracked(path, value, true)
}

pub(crate) fn write_node(path: &str, value: &str) -> bool {
    if !Path::new(path).exists() {
        return false;
    }
    state::write_tracked(path, value, false)
}

pub(crate) fn read_node(path: &str) -> Option<String> {
    fs::read_to_string(path)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
}

pub(crate) fn read_frequencies(path: &str) -> Vec<u64> {
    fs::read_to_string(path)
        .unwrap_or_default()
        .split_whitespace()
        .filter_map(|value| value.parse::<u64>().ok())
        .collect()
}

pub(crate) fn devfreq_matching(needles: &[&str]) -> Vec<String> {
    let mut found = Vec::new();
    let Ok(entries) = fs::read_dir("/sys/class/devfreq") else {
        return found;
    };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
        if !needles.iter().any(|needle| name.contains(needle)) {
            continue;
        }
        found.push(format!(
            "/sys/class/devfreq/{}",
            entry.file_name().to_string_lossy()
        ));
    }
    found
}

pub(crate) fn find_devfreq(needles: &[&str]) -> Option<String> {
    devfreq_matching(needles).into_iter().next()
}

pub(crate) fn platform_matching(suffix: &str) -> Vec<String> {
    let mut found = Vec::new();
    let Ok(entries) = fs::read_dir("/sys/devices/platform") else {
        return found;
    };
    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
        if !name.contains(suffix) {
            continue;
        }
        found.push(format!(
            "/sys/devices/platform/{}",
            entry.file_name().to_string_lossy()
        ));
    }
    found
}

pub(crate) fn devfreq_max_perf(base: &str) -> usize {
    let values = read_frequencies(&format!("{}/available_frequencies", base));
    let Some(max) = devfreq::max_frequency(&values) else {
        return 0;
    };
    let value = max.to_string();
    usize::from(apply_node(&format!("{}/max_freq", base), &value))
        + usize::from(apply_node(&format!("{}/min_freq", base), &value))
}

pub(crate) fn devfreq_mid_perf(base: &str) -> usize {
    let values = read_frequencies(&format!("{}/available_frequencies", base));
    let (Some(max), Some(mid)) = (
        devfreq::max_frequency(&values),
        devfreq::mid_frequency(&values),
    ) else {
        return 0;
    };
    usize::from(apply_node(&format!("{}/max_freq", base), &max.to_string()))
        + usize::from(apply_node(&format!("{}/min_freq", base), &mid.to_string()))
}

pub(crate) fn devfreq_unlock(base: &str) -> usize {
    let values = read_frequencies(&format!("{}/available_frequencies", base));
    let (Some(max), Some(min)) = (
        devfreq::max_frequency(&values),
        devfreq::min_frequency(&values),
    ) else {
        return 0;
    };
    usize::from(write_node(&format!("{}/max_freq", base), &max.to_string()))
        + usize::from(write_node(&format!("{}/min_freq", base), &min.to_string()))
}

pub(crate) fn devfreq_min_perf(base: &str) -> usize {
    let values = read_frequencies(&format!("{}/available_frequencies", base));
    let Some(min) = devfreq::min_frequency(&values) else {
        return 0;
    };
    let value = min.to_string();
    usize::from(apply_node(&format!("{}/min_freq", base), &value))
        + usize::from(apply_node(&format!("{}/max_freq", base), &value))
}

pub(crate) fn cpudcvs_max_perf(base: &str) -> usize {
    let values = read_frequencies(&format!("{}/available_frequencies", base));
    let Some(max) = devfreq::max_frequency(&values) else {
        return 0;
    };
    let value = max.to_string();
    usize::from(apply_node(&format!("{}/hw_max_freq", base), &value))
        + usize::from(apply_node(&format!("{}/hw_min_freq", base), &value))
}

pub(crate) fn cpudcvs_mid_perf(base: &str) -> usize {
    let values = read_frequencies(&format!("{}/available_frequencies", base));
    let (Some(max), Some(mid)) = (
        devfreq::max_frequency(&values),
        devfreq::mid_frequency(&values),
    ) else {
        return 0;
    };
    usize::from(apply_node(
        &format!("{}/hw_max_freq", base),
        &max.to_string(),
    )) + usize::from(apply_node(
        &format!("{}/hw_min_freq", base),
        &mid.to_string(),
    ))
}

pub(crate) fn cpudcvs_unlock(base: &str) -> usize {
    let values = read_frequencies(&format!("{}/available_frequencies", base));
    let (Some(max), Some(min)) = (
        devfreq::max_frequency(&values),
        devfreq::min_frequency(&values),
    ) else {
        return 0;
    };
    usize::from(write_node(&format!("{}/hw_max_freq", base), &max.to_string()))
        + usize::from(write_node(
            &format!("{}/hw_min_freq", base),
            &min.to_string(),
        ))
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum DevfreqMode {
    Max,
    Mid,
    Unlock,
}

fn cpudcvs_mode(base: &str, mode: DevfreqMode) -> usize {
    match mode {
        DevfreqMode::Max => cpudcvs_max_perf(base),
        DevfreqMode::Mid => cpudcvs_mid_perf(base),
        DevfreqMode::Unlock => cpudcvs_unlock(base),
    }
}

fn profile_devfreq_mode(profile: SocProfile) -> Option<DevfreqMode> {
    match profile {
        SocProfile::Performance => Some(DevfreqMode::Max),
        SocProfile::Lite => Some(DevfreqMode::Mid),
        SocProfile::Balance => Some(DevfreqMode::Unlock),
        SocProfile::Eco => None,
        SocProfile::Maestro => Some(DevfreqMode::Max),
    }
}

pub(crate) fn apply_profile_cpudcvs(base: &str, profile: SocProfile) -> usize {
    match profile_devfreq_mode(profile) {
        Some(mode) => cpudcvs_mode(base, mode),
        None => 0,
    }
}

pub(crate) fn mali_dir() -> Option<String> {
    for dir in ["/sys/devices/platform"] {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
            if !name.contains("mali") {
                continue;
            }
            let base = entry.path().to_string_lossy().into_owned();
            if Path::new(&base).join("power_policy").exists() {
                return Some(base);
            }
        }
    }
    None
}

pub(crate) fn mali_power_policy(profile: SocProfile) -> usize {
    let Some(base) = mali_dir() else {
        return 0;
    };
    let path = format!("{}/power_policy", base);
    let available = read_node(&path).unwrap_or_default();
    let value = match profile {
        SocProfile::Performance => "always_on",
        SocProfile::Balance | SocProfile::Eco => {
            if available.contains("adaptive") {
                "adaptive"
            } else {
                "coarse_demand"
            }
        }
        SocProfile::Lite | SocProfile::Maestro => {
            if available.contains("adaptive") {
                "adaptive"
            } else {
                "coarse_demand"
            }
        }
    };
    usize::from(apply_node(&path, value))
}

static DETECTED_VENDOR: OnceLock<Vendor> = OnceLock::new();

pub fn vendor() -> Vendor {
    *DETECTED_VENDOR.get_or_init(|| {
        if let Some(vendor) = configured_vendor() {
            crate::log!("[SOC] Detected {} from flash config", vendor.as_str());
            return vendor;
        }

        let vendor = live_vendor();
        crate::log!("[SOC] Detected {} from live probe", vendor.as_str());
        vendor
    })
}

fn configured_vendor() -> Option<Vendor> {
    for path in SOC_CONFIG_PATHS {
        if let Ok(content) = fs::read_to_string(path) {
            if let Some(vendor) = parse_vendor(content.trim()) {
                return Some(vendor);
            }
        }
    }

    let value = crate::core::detector::get_system_prop("persist.lumina.soc");
    parse_vendor(value.trim())
}

fn parse_vendor(value: &str) -> Option<Vendor> {
    match value.trim().to_ascii_lowercase().as_str() {
        "" => None,
        "mediatek" | "mtk" => Some(Vendor::Mediatek),
        "snapdragon" | "qualcomm" | "qcom" => Some(Vendor::Snapdragon),
        "exynos" | "samsung" => Some(Vendor::Exynos),
        "unisoc" | "sprd" | "spreadtrum" => Some(Vendor::Unisoc),
        "tensor" | "google" => Some(Vendor::Tensor),
        "tegra" | "nvidia" => Some(Vendor::Tegra),
        "kirin" | "huawei" | "hi3" => Some(Vendor::Kirin),
        "generic" | "unknown" => Some(Vendor::Generic),
        value => classify(value),
    }
}

fn live_vendor() -> Vendor {
    for prop in [
        "ro.soc.manufacturer",
        "ro.soc.model",
        "ro.board.platform",
        "ro.hardware",
        "ro.product.board",
    ] {
        let value = crate::core::detector::get_system_prop(prop);
        if let Some(vendor) = classify(&value) {
            return vendor;
        }
    }

    if has_any_path(&["/sys/class/kgsl", "/sys/module/adreno_idler"]) {
        return Vendor::Snapdragon;
    }
    if has_any_path(&["/proc/gpufreq", "/sys/kernel/ged", "/sys/module/ged"]) {
        return Vendor::Mediatek;
    }
    if has_sprd_devfreq() || Path::new("/sys/module/sprd_gpu").exists() {
        return Vendor::Unisoc;
    }
    if Path::new("/sys/kernel/tegra_gpu").exists() {
        return Vendor::Tegra;
    }
    if Path::new("/sys/kernel/gpu").exists() && Path::new("/sys/kernel/debug/tracing").exists() {
        if has_any_path(&[
            "/sys/devices/platform/17000000.gpu",
            "/sys/class/devfreq/gpufreq",
        ]) {
            return Vendor::Tensor;
        }
    }
    if has_any_path(&["/sys/class/devfreq/gpufreq", "/sys/devices/platform/gpufreq"]) {
        return Vendor::Kirin;
    }

    if let Ok(cpuinfo) = fs::read_to_string("/proc/cpuinfo") {
        if let Some(vendor) = classify_cpuinfo(&cpuinfo) {
            return vendor;
        }
    }

    Vendor::Generic
}

fn has_any_path(paths: &[&str]) -> bool {
    paths.iter().any(|path| Path::new(path).exists())
}

fn has_sprd_devfreq() -> bool {
    fs::read_dir("/sys/class/devfreq")
        .map(|entries| {
            entries.flatten().any(|entry| {
                entry
                    .file_name()
                    .to_string_lossy()
                    .to_ascii_lowercase()
                    .contains("sprd")
            })
        })
        .unwrap_or(false)
}

fn classify_cpuinfo(content: &str) -> Option<Vendor> {
    content.lines().find_map(|line| {
        let value = line.split_once(':').map(|(_, value)| value).unwrap_or(line);
        classify(value)
    })
}

fn classify(value: &str) -> Option<Vendor> {
    let value = value.trim().to_ascii_lowercase();
    if value.is_empty() {
        return None;
    }

    if value.contains("mediatek") || value.contains("mtk") || value.starts_with("mt") {
        return Some(Vendor::Mediatek);
    }

    if value.contains("qualcomm")
        || value.contains("snapdragon")
        || value.contains("qcom")
        || value.starts_with("sm")
        || value.starts_with("msm")
        || value.starts_with("sdm")
        || SNAPDRAGON_PLATFORMS
            .iter()
            .any(|platform| value.contains(platform))
    {
        return Some(Vendor::Snapdragon);
    }

    if value.contains("exynos")
        || value.contains("samsung")
        || value.starts_with("s5e")
        || value.starts_with("universal")
        || value.starts_with("erd")
    {
        return Some(Vendor::Exynos);
    }

    if value.contains("google") || value.contains("tensor") || value.starts_with("gs") {
        return Some(Vendor::Tensor);
    }

    if value.contains("tegra") || value.contains("nvidia") {
        return Some(Vendor::Tegra);
    }

    if value.contains("kirin") || value.contains("huawei") || value.starts_with("hi3") {
        return Some(Vendor::Kirin);
    }

    if value.contains("unisoc")
        || value.contains("spreadtrum")
        || value.starts_with("sprd")
        || value.starts_with("ums")
        || value.starts_with("sc98")
        || value.starts_with("sc99")
        || value.starts_with("t6")
    {
        return Some(Vendor::Unisoc);
    }

    None
}

#[cfg(test)]
mod tests {
    use super::{classify, classify_cpuinfo, parse_vendor, Vendor};

    #[test]
    fn detects_mediatek_from_soc_model() {
        assert_eq!(classify("MT6983"), Some(Vendor::Mediatek));
        assert_eq!(classify("MediaTek MT6789"), Some(Vendor::Mediatek));
    }

    #[test]
    fn detects_snapdragon_from_soc_model() {
        assert_eq!(classify("SM8550"), Some(Vendor::Snapdragon));
        assert_eq!(
            classify("Qualcomm Technologies, Inc SM8550"),
            Some(Vendor::Snapdragon)
        );
        assert_eq!(classify("msm8953"), Some(Vendor::Snapdragon));
        assert_eq!(classify("sdm660"), Some(Vendor::Snapdragon));
    }

    #[test]
    fn detects_snapdragon_platform_codename() {
        assert_eq!(classify("kalama"), Some(Vendor::Snapdragon));
        assert_eq!(classify("lahaina"), Some(Vendor::Snapdragon));
    }

    #[test]
    fn detects_unisoc_from_soc_model() {
        assert_eq!(classify("Unisoc UMS9230"), Some(Vendor::Unisoc));
        assert_eq!(classify("ums512"), Some(Vendor::Unisoc));
        assert_eq!(classify("T606"), Some(Vendor::Unisoc));
        assert_eq!(classify("SC9863A"), Some(Vendor::Unisoc));
    }

    #[test]
    fn parses_flash_config_vendor() {
        assert_eq!(parse_vendor("mediatek"), Some(Vendor::Mediatek));
        assert_eq!(parse_vendor("Snapdragon"), Some(Vendor::Snapdragon));
        assert_eq!(parse_vendor("unisoc"), Some(Vendor::Unisoc));
        assert_eq!(parse_vendor("generic"), Some(Vendor::Generic));
        assert_eq!(parse_vendor(""), None);
    }

    #[test]
    fn detects_exynos_tensor_tegra_kirin() {
        assert_eq!(classify("exynos2100"), Some(Vendor::Exynos));
        assert_eq!(classify("Exynos 2200"), Some(Vendor::Exynos));
        assert_eq!(classify("gs101"), Some(Vendor::Tensor));
        assert_eq!(classify("Tensor G3"), Some(Vendor::Tensor));
        assert_eq!(classify("tegra210"), Some(Vendor::Tegra));
        assert_eq!(classify("Kirin 9000"), Some(Vendor::Kirin));
    }

    #[test]
    fn parses_new_vendor_config() {
        assert_eq!(parse_vendor("exynos"), Some(Vendor::Exynos));
        assert_eq!(parse_vendor("tensor"), Some(Vendor::Tensor));
        assert_eq!(parse_vendor("tegra"), Some(Vendor::Tegra));
        assert_eq!(parse_vendor("kirin"), Some(Vendor::Kirin));
    }

    #[test]
    fn leaves_unknown_platform_generic() {
        assert_eq!(classify("unknownchip"), None);
        assert_eq!(classify(""), None);
    }

    #[test]
    fn detects_vendor_from_cpuinfo() {
        assert_eq!(
            classify_cpuinfo("processor\t: 0\nHardware\t: MT6789\n"),
            Some(Vendor::Mediatek)
        );
        assert_eq!(
            classify_cpuinfo("processor\t: 0\nHardware\t: Qualcomm Technologies, Inc SM8550\n"),
            Some(Vendor::Snapdragon)
        );
        assert_eq!(
            classify_cpuinfo("processor\t: 0\nHardware\t: Exynos 2200\n"),
            Some(Vendor::Exynos)
        );
        assert_eq!(classify_cpuinfo("processor\t: 0\n"), None);
    }
}
