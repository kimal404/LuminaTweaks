use serde::Deserialize;
use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::sync::OnceLock;

const CONFIG_PATHS: &[&str] = &[
    "/data/adb/.config/lumina/device_mitigation.json",
    "/data/adb/modules/luminatweaks/config/device_mitigation.json",
    "/data/adb/modules/lumina_tweaks/config/device_mitigation.json",
];

const PROP_PATHS: &[&str] = &[
    "/system/build.prop",
    "/vendor/build.prop",
    "/product/build.prop",
    "/odm/build.prop",
];

#[derive(Deserialize, Default)]
struct ConfigFile {
    #[serde(default)]
    items: Vec<String>,
    #[serde(default)]
    device_rules: Vec<DeviceRule>,
}

#[derive(Deserialize, Default)]
struct DeviceRule {
    #[serde(default)]
    model_contains: Vec<String>,
    #[serde(default)]
    manufacturer_contains: Vec<String>,
    #[serde(default)]
    items: Vec<String>,
}

struct Store {
    items: HashSet<String>,
    model: String,
    manufacturer: String,
}

static STORE: OnceLock<Store> = OnceLock::new();

fn read_prop(key: &str) -> String {
    for path in PROP_PATHS {
        let Ok(content) = fs::read_to_string(path) else {
            continue;
        };
        for line in content.lines() {
            let Some((name, value)) = line.split_once('=') else {
                continue;
            };
            if name.trim() == key {
                return value.trim().to_string();
            }
        }
    }
    String::new()
}

fn matches(value: &str, needles: &[String]) -> bool {
    if needles.is_empty() {
        return false;
    }
    let value = value.to_ascii_lowercase();
    needles
        .iter()
        .any(|needle| value.contains(&needle.to_ascii_lowercase()))
}

fn load() -> Store {
    let model = [
        "ro.product.model",
        "ro.product.vendor.model",
        "ro.product.system.model",
    ]
    .iter()
    .map(|key| read_prop(key))
    .find(|value| !value.is_empty())
    .unwrap_or_default();
    let manufacturer = read_prop("ro.product.manufacturer");

    let mut items = HashSet::new();
    if let Some(path) = CONFIG_PATHS.iter().find(|path| Path::new(path).exists()) {
        if let Ok(content) = fs::read_to_string(path) {
            if let Ok(config) = serde_json::from_str::<ConfigFile>(&content) {
                for item in config.items {
                    items.insert(item);
                }
                for rule in config.device_rules {
                    let model_ok = matches(&model, &rule.model_contains);
                    let vendor_ok = matches(&manufacturer, &rule.manufacturer_contains);
                    let has_selector =
                        !rule.model_contains.is_empty() || !rule.manufacturer_contains.is_empty();
                    if has_selector && model_ok || has_selector && vendor_ok {
                        for item in rule.items {
                            items.insert(item);
                        }
                    }
                }
            }
        }
    }

    Store {
        items,
        model,
        manufacturer,
    }
}

fn store() -> &'static Store {
    STORE.get_or_init(load)
}

pub fn has(item: &str) -> bool {
    store().items.contains(item)
}

pub fn active_items() -> Vec<String> {
    let mut items: Vec<String> = store().items.iter().cloned().collect();
    items.sort();
    items
}

pub fn summary() -> String {
    format!(
        "{} {} | {}",
        store().manufacturer,
        store().model,
        active_items().join(",")
    )
    .trim()
    .to_string()
}

#[cfg(test)]
mod tests {
    use super::matches;

    #[test]
    fn matches_rule_without_case_sensitivity() {
        assert!(matches("SM-S928B", &["sm-s928".to_string()]));
        assert!(!matches("SM-S928B", &["pixel".to_string()]));
        assert!(!matches("SM-S928B", &[]));
    }
}
