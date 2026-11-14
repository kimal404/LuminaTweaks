// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

pub mod generic;
pub mod exynos;
pub mod kirin;
pub mod mali;
pub mod mediatek;
pub mod snapdragon;
pub mod tegra;
pub mod tensor;
pub mod unisoc;

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
    Eco,
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
        "[SOC] {} profile applied to {} devfreq node(s)",
        vendor.as_str(),
        applied
    );
    applied
}

pub fn optimize_storage(profile: SocProfile) -> usize {
    let applied = match vendor() {
        Vendor::Mediatek => mediatek::optimize_storage(profile),
        Vendor::Snapdragon => snapdragon::optimize_storage(profile),
        Vendor::Exynos => exynos::optimize_storage(profile),
        Vendor::Unisoc => unisoc::optimize_storage(profile),
        Vendor::Tensor => tensor::optimize_storage(profile),
        Vendor::Tegra => tegra::optimize_storage(profile),
        Vendor::Kirin => kirin::optimize_storage(profile),
        Vendor::Generic => crate::tweaks::devfreq::apply(
            &["ufs", "mmc"],
            match profile {
                SocProfile::Performance => "performance",
                SocProfile::Lite => "simple_ondemand",
                SocProfile::Eco => "powersave",
            },
            profile == SocProfile::Performance,
        ),
    };
    if applied > 0 {
        log!("[SOC] Storage tuned on {} devfreq node(s)", applied);
    }
    applied
}

pub(crate) fn apply_devfreq(profile: SocProfile, needles: &[&str]) -> usize {
    match profile {
        SocProfile::Performance => crate::tweaks::devfreq::apply(needles, "performance", true),
        SocProfile::Lite => crate::tweaks::devfreq::apply_capped(needles, "simple_ondemand", 85),
        SocProfile::Eco => crate::tweaks::devfreq::apply(needles, "powersave", false),
    }
}

static DETECTED_VENDOR: OnceLock<Vendor> = OnceLock::new();

pub fn vendor() -> Vendor {
    *DETECTED_VENDOR.get_or_init(|| {
        if let Some(vendor) = configured_vendor() {
            crate::log!(
                "[SOC] Detected {} from flash config",
                vendor.as_str()
            );
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
        if has_any_path(&["/sys/devices/platform/17000000.gpu", "/sys/class/devfreq/gpufreq"]) {
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

    if value.contains("google")
        || value.contains("tensor")
        || value.starts_with("gs")
    {
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
