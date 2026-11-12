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
pub mod mediatek;
pub mod snapdragon;

use std::fs;
use std::path::Path;

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
    Generic,
}

impl Vendor {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Mediatek => "MediaTek",
            Self::Snapdragon => "Snapdragon",
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
        Vendor::Generic => generic::apply(profile),
    };
    log!(
        "[SOC] {} profile applied to {} devfreq node(s)",
        vendor.as_str(),
        applied
    );
    applied
}

pub fn vendor() -> Vendor {
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

    if Path::new("/sys/class/kgsl").exists() {
        return Vendor::Snapdragon;
    }
    if Path::new("/proc/gpufreq").exists() || Path::new("/sys/kernel/ged").exists() {
        return Vendor::Mediatek;
    }

    if let Ok(cpuinfo) = fs::read_to_string("/proc/cpuinfo") {
        if let Some(vendor) = classify_cpuinfo(&cpuinfo) {
            return vendor;
        }
    }

    Vendor::Generic
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

    None
}

#[cfg(test)]
mod tests {
    use super::{classify, classify_cpuinfo, Vendor};

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
    fn leaves_unknown_platform_generic() {
        assert_eq!(classify("exynos2100"), None);
        assert_eq!(classify("gs101"), None);
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
        assert_eq!(classify_cpuinfo("processor\t: 0\n"), None);
    }
}
