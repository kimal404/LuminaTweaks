/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::collections::HashMap;
use std::fs::{self, File};
use std::io::{BufRead, BufReader};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::Mutex;
use std::time::{Duration, Instant, SystemTime};
use serde_json::Value;

pub const CONFIG_DIR: &str = "/data/adb/.config/lumina";

const GAMELIST_JSON_CANDIDATES: &[&str] = &[
    "/data/adb/.config/lumina/gamelist.json",
    "/data/adb/modules/luminatweaks/gamelist.json",
    "/data/adb/modules/lumina_tweaks/gamelist.json",
];

const GAMES_TXT_CANDIDATES: &[&str] = &[
    "/data/adb/.config/lumina/gamelist.txt",
    "/data/adb/modules/luminatweaks/gamelist.txt",
    "/data/adb/modules/lumina_tweaks/gamelist.txt",
    "/data/adb/.config/lumina/games.txt",
    "/data/adb/modules/luminatweaks/games.txt",
    "/data/adb/modules/lumina_tweaks/games.txt",
];

#[derive(Debug, Clone, Default)]
pub struct AppConfig {
    pub enabled: bool,
    pub lite_mode: String,
    pub dnd: String,
    pub refresh_rate: String,
    pub renderer: String,
    pub texture_enabled: bool,
    pub texture_detail: i32,
    pub pixel_sharp: bool,
    pub hard_distance: bool,
}

const CGROUP_TOP_APP_CANDIDATES: &[&str] = &[
    "/dev/cpuset/top-app/cgroup.procs",
    "/sys/fs/cgroup/top-app/cgroup.procs",
    "/sys/fs/cgroup/cpuset/top-app/cgroup.procs",
];

extern "C" {
    fn __system_property_get(name: *const std::os::raw::c_char, value: *mut std::os::raw::c_char) -> std::os::raw::c_int;
}

pub fn get_system_prop(name: &str) -> String {
    let mut buf = [0u8; 92];
    if let Ok(c_name) = std::ffi::CString::new(name) {
        let len = unsafe {
            __system_property_get(c_name.as_ptr(), buf.as_mut_ptr() as *mut std::os::raw::c_char)
        };
        if len > 0 {
            return String::from_utf8_lossy(&buf[..len as usize]).trim().to_string();
        }
    }
    String::new()
}

#[inline]
pub fn sanitize_pkg_name(raw: &str) -> &str {
    let clean = raw.trim_matches('\0').trim();
    clean.split_once(':').map(|(base, _)| base).unwrap_or(clean).trim()
}

pub(crate) fn is_ignored_package(pkg: &str) -> bool {
    matches!(
        pkg,
        "" | "com.android.systemui"
            | "com.miui.securityadd"
            | "com.miui.securitycenter"
            | "com.miui.powerkeeper"
            | "com.xiaomi.joyose"
            | "android"
            | "com.google.android.permissioncontroller"
            | "com.android.permissioncontroller"
            | "com.lbe.security.miui"
            | "com.miui.guardprovider"
            | "com.google.android.googlequicksearchbox"
            | "com.miui.home"
            | "com.mi.android.globallauncher"
            | "com.android.launcher"
            | "com.android.launcher3"
    ) || pkg.contains("inputmethod") || pkg.contains("system_server")
}

#[derive(Debug, Clone)]
pub struct GameMeta {
    pub lite_mode: String,
    pub dnd: String,
}

impl Default for GameMeta {
    fn default() -> Self {
        Self {
            lite_mode: "default".to_string(),
            dnd: "default".to_string(),
        }
    }
}

struct WhitelistState {
    games: HashMap<String, GameMeta>,
    last_mtime: Option<SystemTime>,
    active_path: Option<PathBuf>,
}

static WHITELIST_CACHE: Mutex<Option<WhitelistState>> = Mutex::new(None);

fn normalize_option(val: &Value) -> Option<String> {
    match val {
        Value::Bool(true) => Some("on".to_string()),
        Value::Bool(false) => Some("off".to_string()),
        Value::String(s) => match s.trim().to_ascii_lowercase().as_str() {
            "on" | "true" | "1" => Some("on".to_string()),
            "off" | "false" | "0" => Some("off".to_string()),
            "default" | "auto" => Some("default".to_string()),
            _ => None,
        },
        _ => None,
    }
}

fn parse_gamelist(path: &Path) -> HashMap<String, GameMeta> {
    let mut games = HashMap::new();
    let is_json = path
        .extension()
        .map(|e| e.to_string_lossy().eq_ignore_ascii_case("json"))
        .unwrap_or(false);

    if is_json {
        if let Ok(content) = fs::read_to_string(path) {
            if let Ok(Value::Object(obj)) = serde_json::from_str::<Value>(&content) {
                for (key, value) in obj {
                    let pkg = sanitize_pkg_name(&key);
                    if pkg.is_empty() {
                        continue;
                    }
                    let mut meta = GameMeta::default();
                    if let Value::Object(fields) = value {
                        if let Some(v) = fields.get("lite_mode").or_else(|| fields.get("liteMode")) {
                            if let Some(mode) = normalize_option(v) {
                                meta.lite_mode = mode;
                            }
                        }
                        if let Some(v) = fields.get("dnd") {
                            if let Some(mode) = normalize_option(v) {
                                meta.dnd = mode;
                            }
                        }
                    }
                    games.insert(pkg.to_string(), meta);
                }
            }
        }
        return games;
    }

    if let Ok(file) = File::open(path) {
        for line in BufReader::new(file).lines().flatten() {
            let trimmed = line.trim();
            if trimmed.is_empty() || trimmed.starts_with('#') {
                continue;
            }
            let pkg = sanitize_pkg_name(trimmed);
            if !pkg.is_empty() {
                games.insert(pkg.to_string(), GameMeta::default());
            }
        }
    }

    games
}

fn find_gamelist_path() -> Option<PathBuf> {
    GAMELIST_JSON_CANDIDATES
        .iter()
        .map(PathBuf::from)
        .find(|p| p.exists())
        .or_else(|| {
            GAMES_TXT_CANDIDATES
                .iter()
                .map(PathBuf::from)
                .find(|p| p.exists())
        })
}

fn refresh_whitelist<'a>(guard: &'a mut Option<WhitelistState>) -> &'a Option<WhitelistState> {
    let path = match find_gamelist_path() {
        Some(p) => p,
        None => {
            *guard = None;
            return guard;
        }
    };

    let current_mtime = fs::metadata(&path).ok().and_then(|m| m.modified().ok());

    let need_reload = match &*guard {
        Some(state) => state.active_path.as_ref() != Some(&path) || state.last_mtime != current_mtime,
        None => true,
    };

    if need_reload {
        *guard = Some(WhitelistState {
            games: parse_gamelist(&path),
            last_mtime: current_mtime,
            active_path: Some(path),
        });
    }

    guard
}

pub fn is_game_whitelisted(pkg: &str) -> bool {
    let clean = sanitize_pkg_name(pkg);
    let mut guard = match WHITELIST_CACHE.lock() {
        Ok(g) => g,
        Err(p) => p.into_inner(),
    };
    let state = refresh_whitelist(&mut guard);

    state
        .as_ref()
        .map(|s| s.games.contains_key(clean))
        .unwrap_or(false)
}

pub fn get_game_meta(pkg: &str) -> Option<GameMeta> {
    let clean = sanitize_pkg_name(pkg);
    let mut guard = match WHITELIST_CACHE.lock() {
        Ok(g) => g,
        Err(p) => p.into_inner(),
    };
    let state = refresh_whitelist(&mut guard);

    state.as_ref().and_then(|s| s.games.get(clean).cloned())
}

pub fn get_focused_package() -> Option<String> {
    for &cgroup_path in CGROUP_TOP_APP_CANDIDATES {
        if !Path::new(cgroup_path).exists() {
            continue;
        }

        if let Ok(content) = fs::read_to_string(cgroup_path) {
            for line in content.lines().rev() {
                let pid_str = line.trim();
                if pid_str.is_empty() {
                    continue;
                }

                let cmdline_path = format!("/proc/{}/cmdline", pid_str);
                if let Ok(bytes) = fs::read(cmdline_path) {
                    let raw_str = if let Some(pos) = bytes.iter().position(|&b| b == 0) {
                        std::str::from_utf8(&bytes[..pos]).ok()
                    } else {
                        std::str::from_utf8(&bytes).ok()
                    };

                    if let Some(raw) = raw_str {
                        let pkg = sanitize_pkg_name(raw);
                        if !pkg.is_empty()
                            && !pkg.starts_with('/')
                            && pkg.contains('.')
                            && !is_ignored_package(pkg)
                        {
                            return Some(pkg.to_string());
                        }
                    }
                }
            }
        }
    }
    None
}

pub fn is_master_enabled() -> bool {
    let p = format!("{}/master.conf", CONFIG_DIR);
    if let Ok(content) = std::fs::read_to_string(p) {
        let val = content.trim();
        if val == "0" || val == "false" {
            return false;
        } else if val == "1" || val == "true" {
            return true;
        }
    }

    let val = get_system_prop("persist.lumina.master");
    if val == "0" || val == "false" {
        return false;
    } else if val == "1" || val == "true" {
        return true;
    }

    true
}

static CACHED_LOW_POWER: Mutex<Option<(Instant, bool)>> = Mutex::new(None);

pub fn is_low_power_active() -> bool {
    if let Ok(guard) = CACHED_LOW_POWER.lock() {
        if let Some((instant, val)) = *guard {
            if instant.elapsed() < Duration::from_secs(5) {
                return val;
            }
        }
    }

    let prop_val = get_system_prop("low_power");
    if prop_val == "1" {
        if let Ok(mut guard) = CACHED_LOW_POWER.lock() {
            *guard = Some((Instant::now(), true));
        }
        return true;
    }

    let is_active = Command::new("settings")
        .args(["get", "global", "low_power"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim() == "1")
        .unwrap_or(false);

    if let Ok(mut guard) = CACHED_LOW_POWER.lock() {
        *guard = Some((Instant::now(), is_active));
    }
    is_active
}

fn is_truthy(val: &Value) -> bool {
    if let Some(b) = val.as_bool() {
        return b;
    }
    if let Some(s) = val.as_str() {
        let lower = s.trim().to_ascii_lowercase();
        return lower == "true" || lower == "1" || lower == "on";
    }
    if let Some(n) = val.as_i64() {
        return n == 1;
    }
    false
}

pub fn read_global_json() -> Option<Value> {
    let path = format!("{}/config.json", CONFIG_DIR);
    if let Ok(content) = std::fs::read_to_string(path) {
        if let Ok(json) = serde_json::from_str::<Value>(&content) {
            return Some(json);
        }
    }
    None
}

pub fn is_lite_mode_active() -> bool {
    let p = format!("{}/lite_mode.conf", CONFIG_DIR);
    if let Ok(content) = std::fs::read_to_string(p) {
        let trimmed = content.trim();
        if trimmed == "1" || trimmed.eq_ignore_ascii_case("true") || trimmed.eq_ignore_ascii_case("on") {
            return true;
        } else if trimmed == "0" || trimmed.eq_ignore_ascii_case("false") || trimmed.eq_ignore_ascii_case("off") {
            return false;
        }
    }

    if let Some(json) = read_global_json() {
        if let Some(val) = json.get("lite_mode_global") {
            return is_truthy(val);
        }
    }

    let val = get_system_prop("persist.lumina.litemode");
    if val == "1" || val == "true" {
        return true;
    } else if val == "0" || val == "false" {
        return false;
    }

    if is_low_power_active() {
        return true;
    }

    false
}

pub fn is_auto_dnd_enabled() -> bool {
    if let Some(json) = read_global_json() {
        if let Some(val) = json.get("auto_dnd") {
            return is_truthy(val);
        }
    }

    let val = get_system_prop("persist.lumina.autodnd");
    val == "1" || val.eq_ignore_ascii_case("true")
}

pub fn get_global_zram_config() -> (Option<u32>, Option<String>, Option<u32>) {
    let json = match read_global_json() {
        Some(j) => j,
        None => return (None, None, None),
    };

    let size_gb = json.get("zram_size_gb")
        .or_else(|| json.get("zram_size"))
        .or_else(|| json.get("zramSize"))
        .and_then(|v| {
            if let Some(n) = v.as_u64() {
                Some(n as u32)
            } else if let Some(s) = v.as_str() {
                s.replace("GB", "").replace("gb", "").trim().parse::<u32>().ok()
            } else {
                None
            }
        });

    let algo = json.get("zram_algorithm")
        .or_else(|| json.get("zramAlgorithm"))
        .or_else(|| json.get("zram_algo"))
        .and_then(|v| v.as_str().map(|s| s.trim().to_string()));

    let swappiness = json.get("swappiness")
        .or_else(|| json.get("vm_swappiness"))
        .and_then(|v| {
            if let Some(n) = v.as_u64() {
                Some(n as u32)
            } else if let Some(s) = v.as_str() {
                s.trim().parse::<u32>().ok()
            } else {
                None
            }
        });

    (size_gb, algo, swappiness)
}

pub fn get_global_io_scheduler() -> Option<String> {
    let json = read_global_json()?;
    json.get("io_scheduler")
        .or_else(|| json.get("ioScheduler"))
        .or_else(|| json.get("scheduler"))
        .and_then(|v| v.as_str().map(|s| s.trim().to_string()))
}

fn extract_app_config(item: &Value) -> AppConfig {
    let enabled = item.get("saklar_utama")
        .or_else(|| item.get("saklarUtama"))
        .or_else(|| item.get("enabled"))
        .map(is_truthy)
        .unwrap_or(false);

    let lite_mode = match item.get("lite_mode").or_else(|| item.get("liteMode")) {
        Some(Value::Bool(b)) => if *b { "on".to_string() } else { "off".to_string() },
        Some(Value::String(s)) => s.clone(),
        _ => "default".to_string(),
    };

    let dnd = match item.get("dnd") {
        Some(Value::Bool(b)) => if *b { "on".to_string() } else { "off".to_string() },
        Some(Value::String(s)) => match s.trim().to_ascii_lowercase().as_str() {
            "on" | "off" | "default" => s.trim().to_ascii_lowercase(),
            _ => "default".to_string(),
        },
        _ => "default".to_string(),
    };

    let refresh_rate = match item.get("refresh_rate").or_else(|| item.get("refreshRate")) {
        Some(Value::Number(n)) => n.to_string(),
        Some(Value::String(s)) => s.clone(),
        _ => "default".to_string(),
    };

    let renderer = match item.get("renderer") {
        Some(Value::String(s)) => s.clone(),
        _ => "default".to_string(),
    };

    let texture_enabled = item.get("texture_enabled")
        .or_else(|| item.get("textureEnabled"))
        .map(is_truthy)
        .unwrap_or(false);

    let texture_detail = match item.get("texture_detail").or_else(|| item.get("textureDetail")) {
        Some(Value::Number(n)) => n.as_i64().unwrap_or(0) as i32,
        Some(Value::String(s)) => s.parse::<i32>().unwrap_or(0),
        _ => 0,
    };

    let pixel_sharp = item.get("pixel_sharp")
        .or_else(|| item.get("pixelSharp"))
        .map(is_truthy)
        .unwrap_or(false);

    let hard_distance = item.get("hard_distance")
        .or_else(|| item.get("hardDistance"))
        .map(is_truthy)
        .unwrap_or(false);

    AppConfig {
        enabled,
        lite_mode,
        dnd,
        refresh_rate,
        renderer,
        texture_enabled,
        texture_detail,
        pixel_sharp,
        hard_distance,
    }
}

pub fn get_app_config(pkg: &str) -> Option<AppConfig> {
    let clean_pkg = sanitize_pkg_name(pkg);

    let cfg_path = format!("{}/app_config.json", CONFIG_DIR);
    if let Ok(content) = std::fs::read_to_string(cfg_path) {
        if let Ok(json) = serde_json::from_str::<Value>(&content) {
            if let Some(list) = json.as_array() {
                for item in list {
                    let matches_pkg = item.get("pkg").and_then(|v| v.as_str()) == Some(clean_pkg)
                        || item.get("package").and_then(|v| v.as_str()) == Some(clean_pkg)
                        || item.get("packageName").and_then(|v| v.as_str()) == Some(clean_pkg);

                    if matches_pkg {
                        return Some(extract_app_config(item));
                    }
                }
            }

            if let Some(app_obj) = json.get(clean_pkg) {
                return Some(extract_app_config(app_obj));
            }
        }
    }

    if let Some(meta) = get_game_meta(clean_pkg) {
        return Some(AppConfig {
            enabled: true,
            lite_mode: meta.lite_mode,
            dnd: meta.dnd,
            refresh_rate: "default".to_string(),
            renderer: "default".to_string(),
            texture_enabled: false,
            texture_detail: 0,
            pixel_sharp: false,
            hard_distance: false,
        });
    }

    None
}

pub fn is_app_enabled(pkg: &str) -> bool {
    let clean_pkg = sanitize_pkg_name(pkg);
    if is_game_whitelisted(clean_pkg) {
        return true;
    }
    get_app_config(clean_pkg).map(|c| c.enabled).unwrap_or(false)
}

pub fn is_process_alive(pkg: &str) -> bool {
    let target = sanitize_pkg_name(pkg);
    if let Ok(entries) = std::fs::read_dir("/proc") {
        for entry in entries.flatten() {
            let name = entry.file_name();
            if let Some(s) = name.to_str() {
                if s.chars().all(|c| c.is_ascii_digit()) {
                    let cmdline_path = entry.path().join("cmdline");
                    if let Ok(cmd) = std::fs::read(cmdline_path) {
                        let proc_str = if let Some(first) = cmd.split(|&b| b == 0).next() {
                            std::str::from_utf8(first).ok()
                        } else {
                            std::str::from_utf8(&cmd).ok()
                        };
                        if let Some(raw) = proc_str {
                            let clean = sanitize_pkg_name(raw);
                            if clean == target {
                                return true;
                            }
                        }
                    }
                }
            }
        }
    }
    false
}
