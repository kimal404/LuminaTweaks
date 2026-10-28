use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[allow(dead_code)]
pub const CONFIG_DIR: &str = "/data/adb/luminatweaks";
pub const GLOBAL_CONFIG_PATH: &str = "/data/adb/luminatweaks/config.json";
pub const APP_CONFIG_PATH: &str = "/data/adb/luminatweaks/app_config.json";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum LiteModeSetting {
    Default,
    On,
    Off,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GlobalConfig {
    pub lite_mode_global: bool,
}

impl Default for GlobalConfig {
    fn default() -> Self {
        Self {
            lite_mode_global: false,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppProfile {
    pub pkg: String,
    pub saklar_utama: bool,
    pub lite_mode: LiteModeSetting,
    pub dnd: bool,
}

pub struct ConfigManager;

impl ConfigManager {
    pub fn load_global_config<P: AsRef<Path>>(path: P) -> GlobalConfig {
        if !path.as_ref().exists() {
            return GlobalConfig::default();
        }

        fs::read_to_string(path)
            .ok()
            .and_then(|data| serde_json::from_str::<GlobalConfig>(&data).ok())
            .unwrap_or_default()
    }

    pub fn load_app_configs<P: AsRef<Path>>(path: P) -> Result<Vec<AppProfile>> {
        let p = path.as_ref();
        if !p.exists() {
            return Ok(Vec::new());
        }

        let content = fs::read_to_string(p)
            .with_context(|| format!("Gagal membaca file {}", p.display()))?;

        let profiles = serde_json::from_str::<Vec<AppProfile>>(&content)
            .with_context(|| "Format app_config.json tidak valid")?;

        Ok(profiles)
    }
}
