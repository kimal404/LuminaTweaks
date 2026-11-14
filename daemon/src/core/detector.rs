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

use serde_json::Value;
use std::collections::{HashMap, HashSet};
use std::fs::{self, File};
use std::io::{BufRead, BufReader};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::Mutex;
use std::time::{Duration, Instant, SystemTime};

pub const CONFIG_DIR: &str = "/data/adb/.config/lumina";

const CONFIG_JSON_CANDIDATES: &[&str] = &[
    "/data/adb/.config/lumina/config.json",
    "/data/adb/modules/luminatweaks/config.json",
    "/data/adb/modules/lumina_tweaks/config.json",
];

const APP_CONFIG_JSON_CANDIDATES: &[&str] = &[
    "/data/adb/.config/lumina/app_config.json",
    "/data/adb/modules/luminatweaks/app_config.json",
    "/data/adb/modules/lumina_tweaks/app_config.json",
];

const GAMELIST_JSON_CANDIDATES: &[&str] = &[
    "/data/adb/.config/lumina/gamelist.json",
    "/data/adb/modules/luminatweaks/config/gamelist.json",
    "/data/adb/modules_update/luminatweaks/config/gamelist.json",
    "/data/adb/modules/luminatweaks/gamelist.json",
    "/data/adb/modules/lumina_tweaks/gamelist.json",
];

const GAMES_TXT_CANDIDATES: &[&str] = &[
    "/data/adb/.config/lumina/gamelist.txt",
    "/data/adb/modules/luminatweaks/config/gamelist.txt",
    "/data/adb/modules_update/luminatweaks/config/gamelist.txt",
    "/data/adb/modules/luminatweaks/gamelist.txt",
    "/data/adb/modules/lumina_tweaks/gamelist.txt",
    "/data/adb/.config/lumina/games.txt",
    "/data/adb/modules/luminatweaks/games.txt",
    "/data/adb/modules/lumina_tweaks/games.txt",
];

#[derive(Debug, Clone, Default, serde::Deserialize)]
#[serde(default)]
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
    #[serde(default)]
    pub disabled_games: HashSet<String>,
}

const CGROUP_TOP_APP_CANDIDATES: &[&str] = &[
    "/dev/cpuset/top-app/cgroup.procs",
    "/sys/fs/cgroup/top-app/cgroup.procs",
    "/sys/fs/cgroup/cpuset/top-app/cgroup.procs",
];

extern "C" {
    fn __system_property_get(
        name: *const std::os::raw::c_char,
        value: *mut std::os::raw::c_char,
    ) -> std::os::raw::c_int;
}

pub fn get_system_prop(name: &str) -> String {
    let mut buf = [0u8; 92];
    if let Ok(c_name) = std::ffi::CString::new(name) {
        let len = unsafe {
            __system_property_get(
                c_name.as_ptr(),
                buf.as_mut_ptr() as *mut std::os::raw::c_char,
            )
        };
        if len > 0 {
            return String::from_utf8_lossy(&buf[..len as usize])
                .trim()
                .to_string();
        }
    }
    String::new()
}

#[inline]
pub fn sanitize_pkg_name(raw: &str) -> &str {
    let clean = raw.trim_matches('\0').trim();
    clean
        .split_once(':')
        .map(|(base, _)| base)
        .unwrap_or(clean)
        .trim()
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
            | "com.google.android.apps.nexuslauncher"
            | "com.miui.home"
            | "com.mi.android.globallauncher"
            | "com.android.launcher"
            | "com.android.launcher3"
            | "org.lineageos.trebuchet"
            | "ch.deletescape.lawnchair"
            | "ch.deletescape.lawnchair.ci"
            | "com.teslacoilsw.launcher"
            | "com.sec.android.app.launcher"
    ) || pkg.contains("inputmethod")
        || pkg.contains("system_server")
        || pkg.contains("overlay")
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

const CONFIG_CACHE_TTL: Duration = Duration::from_millis(750);
const PROC_CACHE_TTL: Duration = Duration::from_secs(3);
const FOCUS_FALLBACK_TTL: Duration = Duration::from_millis(1500);

static GLOBAL_JSON_CACHE: Mutex<Option<(Instant, Value)>> = Mutex::new(None);
static APP_JSON_CACHE: Mutex<Option<(Instant, Value)>> = Mutex::new(None);
static PROC_CACHE: Mutex<Option<(Instant, HashSet<String>)>> = Mutex::new(None);
static FOCUS_FALLBACK_CACHE: Mutex<Option<(Instant, Option<String>)>> = Mutex::new(None);

pub fn invalidate_config_cache() {
    if let Ok(mut guard) = GLOBAL_JSON_CACHE.lock() {
        *guard = None;
    }
    if let Ok(mut guard) = APP_JSON_CACHE.lock() {
        *guard = None;
    }
    if let Ok(mut guard) = CACHED_MASTER.lock() {
        *guard = None;
    }
    if let Ok(mut guard) = CACHED_LITE.lock() {
        *guard = None;
    }
}

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
                        if let Some(v) = fields.get("lite_mode").or_else(|| fields.get("liteMode"))
                        {
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

fn refresh_whitelist(guard: &mut Option<WhitelistState>) -> &Option<WhitelistState> {
    let path = match find_gamelist_path() {
        Some(p) => p,
        None => {
            *guard = None;
            return guard;
        }
    };

    let current_mtime = fs::metadata(&path).ok().and_then(|m| m.modified().ok());

    let need_reload = match &*guard {
        Some(state) => {
            state.active_path.as_ref() != Some(&path) || state.last_mtime != current_mtime
        }
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
    get_focused_package_cached()
}

pub fn is_master_enabled() -> bool {
    if let Ok(guard) = CACHED_MASTER.lock() {
        if let Some((at, value)) = *guard {
            if at.elapsed() < CONFIG_CACHE_TTL {
                return value;
            }
        }
    }

    let value = read_master_enabled();
    if let Ok(mut guard) = CACHED_MASTER.lock() {
        *guard = Some((Instant::now(), value));
    }
    value
}

fn read_master_enabled() -> bool {
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
static CACHED_MASTER: Mutex<Option<(Instant, bool)>> = Mutex::new(None);
static CACHED_LITE: Mutex<Option<(Instant, bool)>> = Mutex::new(None);

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
    if let Ok(guard) = GLOBAL_JSON_CACHE.lock() {
        if let Some((at, json)) = guard.as_ref() {
            if at.elapsed() < CONFIG_CACHE_TTL {
                return Some(json.clone());
            }
        }
    }

    let mut json = None;
    for path in CONFIG_JSON_CANDIDATES {
        if let Ok(content) = std::fs::read_to_string(path) {
            if let Ok(value) = serde_json::from_str::<Value>(&content) {
                json = Some(value);
                break;
            }
        }
    }

    if let Some(value) = json {
        if let Ok(mut guard) = GLOBAL_JSON_CACHE.lock() {
            *guard = Some((Instant::now(), value.clone()));
        }
        Some(value)
    } else {
        None
    }
}

pub fn read_app_json_cached() -> Option<Value> {
    read_app_json()
}

fn read_app_json() -> Option<Value> {
    if let Ok(guard) = APP_JSON_CACHE.lock() {
        if let Some((at, json)) = guard.as_ref() {
            if at.elapsed() < CONFIG_CACHE_TTL {
                return Some(json.clone());
            }
        }
    }

    let mut json = None;
    for path in APP_CONFIG_JSON_CANDIDATES {
        if let Ok(content) = std::fs::read_to_string(path) {
            if let Ok(value) = serde_json::from_str::<Value>(&content) {
                json = Some(value);
                break;
            }
        }
    }

    if let Some(value) = json {
        if let Ok(mut guard) = APP_JSON_CACHE.lock() {
            *guard = Some((Instant::now(), value.clone()));
        }
        Some(value)
    } else {
        None
    }
}

pub fn is_lite_mode_active() -> bool {
    if let Ok(guard) = CACHED_LITE.lock() {
        if let Some((at, value)) = *guard {
            if at.elapsed() < CONFIG_CACHE_TTL {
                return value;
            }
        }
    }

    let value = read_lite_mode_active();
    if let Ok(mut guard) = CACHED_LITE.lock() {
        *guard = Some((Instant::now(), value));
    }
    value
}

fn read_lite_mode_active() -> bool {
    let p = format!("{}/lite_mode.conf", CONFIG_DIR);
    if let Ok(content) = std::fs::read_to_string(p) {
        let trimmed = content.trim();
        if trimmed == "1"
            || trimmed.eq_ignore_ascii_case("true")
            || trimmed.eq_ignore_ascii_case("on")
        {
            return true;
        } else if trimmed == "0"
            || trimmed.eq_ignore_ascii_case("false")
            || trimmed.eq_ignore_ascii_case("off")
        {
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

    let size_gb = json
        .get("zram_size_gb")
        .or_else(|| json.get("zram_size"))
        .or_else(|| json.get("zramSize"))
        .and_then(|v| {
            if let Some(n) = v.as_u64() {
                Some(n as u32)
            } else if let Some(s) = v.as_str() {
                s.replace("GB", "")
                    .replace("gb", "")
                    .trim()
                    .parse::<u32>()
                    .ok()
            } else {
                None
            }
        });

    let algo = json
        .get("zram_algorithm")
        .or_else(|| json.get("zramAlgorithm"))
        .or_else(|| json.get("zram_algo"))
        .and_then(|v| v.as_str().map(|s| s.trim().to_string()));

    let swappiness = json
        .get("swappiness")
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

fn parse_package_set(value: Option<&Value>) -> HashSet<String> {
    let Some(value) = value else {
        return HashSet::new();
    };

    match value {
        Value::Array(items) => items
            .iter()
            .filter_map(|item| item.as_str())
            .map(sanitize_pkg_name)
            .filter(|pkg| !pkg.is_empty())
            .map(str::to_string)
            .collect(),
        Value::String(text) => text
            .split(|ch: char| ch == ',' || ch.is_whitespace())
            .map(str::trim)
            .filter(|pkg| !pkg.is_empty())
            .map(sanitize_pkg_name)
            .map(str::to_string)
            .collect(),
        _ => HashSet::new(),
    }
}

fn parse_disabled_games(value: Option<&Value>) -> HashSet<String> {
    parse_package_set(value)
}

fn app_row_disabled(pkg: &str, json: &Value) -> bool {
    let clean = sanitize_pkg_name(pkg);

    if let Some(list) = json.as_array() {
        for item in list {
            let row_pkg = item
                .get("pkg")
                .or_else(|| item.get("package"))
                .or_else(|| item.get("packageName"))
                .and_then(|value| value.as_str());
            if row_pkg.map(sanitize_pkg_name) != Some(clean) {
                continue;
            }
            if item.get("disabled").map(is_truthy).unwrap_or(false) {
                return true;
            }
            if parse_disabled_games(item.get("disabled_games")).contains(clean) {
                return true;
            }
        }
    }

    if parse_disabled_games(json.get("disabled_games")).contains(clean) {
        return true;
    }

    false
}

pub fn is_game_disabled(pkg: &str) -> bool {
    let clean = sanitize_pkg_name(pkg);
    if clean.is_empty() {
        return false;
    }

    if let Some(json) = read_global_json() {
        if parse_disabled_games(json.get("disabled_games")).contains(clean) {
            return true;
        }
    }

    if let Some(json) = read_app_json() {
        if app_row_disabled(clean, &json) {
            return true;
        }
    }

    false
}

pub fn is_custom_game(pkg: &str) -> bool {
    let clean = sanitize_pkg_name(pkg);
    if clean.is_empty() {
        return false;
    }

    if let Some(json) = read_global_json() {
        let custom_set =
            parse_package_set(json.get("custom_games").or_else(|| json.get("customGames")));
        if custom_set.contains(clean) {
            return true;
        }
    }

    if let Some(json) = read_app_json() {
        let custom_set =
            parse_package_set(json.get("custom_games").or_else(|| json.get("customGames")));
        if custom_set.contains(clean) {
            return true;
        }

        if let Some(list) = json.as_array() {
            for item in list {
                let row_pkg = item
                    .get("pkg")
                    .or_else(|| item.get("package"))
                    .or_else(|| item.get("packageName"))
                    .and_then(|value| value.as_str());
                if row_pkg.map(sanitize_pkg_name) == Some(clean) {
                    let is_custom = item
                        .get("custom")
                        .or_else(|| item.get("saklar_utama"))
                        .or_else(|| item.get("saklarUtama"))
                        .or_else(|| item.get("enabled"))
                        .map(is_truthy)
                        .unwrap_or(false);
                    if is_custom {
                        return true;
                    }
                }
            }
        }

        if let Some(item) = json.get(clean) {
            let is_custom = item
                .get("custom")
                .or_else(|| item.get("saklar_utama"))
                .or_else(|| item.get("saklarUtama"))
                .or_else(|| item.get("enabled"))
                .map(is_truthy)
                .unwrap_or(false);
            if is_custom {
                return true;
            }
        }
    }

    false
}

fn extract_app_config(item: &Value) -> AppConfig {
    let mut disabled_games = parse_disabled_games(
        item.get("disabled_games")
            .or_else(|| item.get("disabledGames")),
    );
    if item.get("disabled").map(is_truthy).unwrap_or(false) {
        if let Some(pkg) = item
            .get("pkg")
            .or_else(|| item.get("package"))
            .or_else(|| item.get("packageName"))
            .and_then(|value| value.as_str())
        {
            let clean = sanitize_pkg_name(pkg);
            if !clean.is_empty() {
                disabled_games.insert(clean.to_string());
            }
        }
    }

    let enabled = item
        .get("saklar_utama")
        .or_else(|| item.get("saklarUtama"))
        .or_else(|| item.get("enabled"))
        .or_else(|| item.get("custom"))
        .map(is_truthy)
        .unwrap_or(false);

    let lite_mode = match item.get("lite_mode").or_else(|| item.get("liteMode")) {
        Some(Value::Bool(b)) => {
            if *b {
                "on".to_string()
            } else {
                "off".to_string()
            }
        }
        Some(Value::String(s)) => s.clone(),
        _ => "default".to_string(),
    };

    let dnd = match item.get("dnd") {
        Some(Value::Bool(b)) => {
            if *b {
                "on".to_string()
            } else {
                "off".to_string()
            }
        }
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

    let texture_enabled = item
        .get("texture_enabled")
        .or_else(|| item.get("textureEnabled"))
        .map(is_truthy)
        .unwrap_or(false);

    let texture_detail = match item
        .get("texture_detail")
        .or_else(|| item.get("textureDetail"))
    {
        Some(Value::Number(n)) => n.as_i64().unwrap_or(0) as i32,
        Some(Value::String(s)) => s.parse::<i32>().unwrap_or(0),
        _ => 0,
    };

    let pixel_sharp = item
        .get("pixel_sharp")
        .or_else(|| item.get("pixelSharp"))
        .map(is_truthy)
        .unwrap_or(false);

    let hard_distance = item
        .get("hard_distance")
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
        disabled_games,
    }
}

pub fn get_app_config(pkg: &str) -> Option<AppConfig> {
    let clean_pkg = sanitize_pkg_name(pkg);
    let global_disabled = read_global_json()
        .map(|json| parse_disabled_games(json.get("disabled_games")))
        .unwrap_or_default();

    if let Some(json) = read_app_json() {
        if let Some(list) = json.as_array() {
            for item in list {
                let matches_pkg = item.get("pkg").and_then(|v| v.as_str()) == Some(clean_pkg)
                    || item.get("package").and_then(|v| v.as_str()) == Some(clean_pkg)
                    || item.get("packageName").and_then(|v| v.as_str()) == Some(clean_pkg);

                if matches_pkg {
                    let mut config = extract_app_config(item);
                    config.disabled_games.extend(global_disabled);
                    return Some(config);
                }
            }
        }

        if let Some(app_obj) = json.get(clean_pkg) {
            let mut config = extract_app_config(app_obj);
            config.disabled_games.extend(global_disabled);
            return Some(config);
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
            disabled_games: global_disabled.clone(),
        });
    }

    if is_custom_game(clean_pkg) {
        return Some(AppConfig {
            enabled: true,
            lite_mode: "default".to_string(),
            dnd: "default".to_string(),
            refresh_rate: "default".to_string(),
            renderer: "default".to_string(),
            texture_enabled: false,
            texture_detail: 0,
            pixel_sharp: false,
            hard_distance: false,
            disabled_games: global_disabled,
        });
    }

    None
}

pub fn is_app_enabled(pkg: &str) -> bool {
    let clean_pkg = sanitize_pkg_name(pkg);
    if clean_pkg.is_empty() {
        return false;
    }

    if is_game_disabled(clean_pkg) {
        return false;
    }

    if is_game_whitelisted(clean_pkg) {
        return true;
    }

    if is_custom_game(clean_pkg) {
        return true;
    }

    get_app_config(clean_pkg)
        .map(|config| config.enabled && !config.disabled_games.contains(clean_pkg))
        .unwrap_or(false)
}

fn running_packages() -> HashSet<String> {
    if let Ok(guard) = PROC_CACHE.lock() {
        if let Some((at, set)) = guard.as_ref() {
            if at.elapsed() < PROC_CACHE_TTL {
                return set.clone();
            }
        }
    }

    let mut packages = HashSet::new();
    if let Ok(entries) = std::fs::read_dir("/proc") {
        for entry in entries.flatten() {
            let name = entry.file_name();
            let Some(pid) = name.to_str() else {
                continue;
            };
            if !pid.chars().all(|c| c.is_ascii_digit()) {
                continue;
            }

            let cmdline_path = entry.path().join("cmdline");
            let Ok(cmd) = std::fs::read(cmdline_path) else {
                continue;
            };
            let Some(first) = cmd.split(|&b| b == 0).next() else {
                continue;
            };
            let Ok(raw) = std::str::from_utf8(first) else {
                continue;
            };
            let clean = sanitize_pkg_name(raw);
            if clean.contains('.') && !clean.starts_with('/') {
                packages.insert(clean.to_string());
            }
        }
    }

    if let Ok(mut guard) = PROC_CACHE.lock() {
        *guard = Some((Instant::now(), packages.clone()));
    }
    packages
}

pub fn is_process_alive(pkg: &str) -> bool {
    let target = sanitize_pkg_name(pkg);
    running_packages().contains(target)
}

fn focused_package_uncached() -> Option<String> {
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

pub fn get_focused_package_cached() -> Option<String> {
    if let Ok(guard) = FOCUS_FALLBACK_CACHE.lock() {
        if let Some((at, pkg)) = guard.as_ref() {
            if at.elapsed() < FOCUS_FALLBACK_TTL {
                return pkg.clone();
            }
        }
    }

    let pkg = focused_package_uncached();
    if let Ok(mut guard) = FOCUS_FALLBACK_CACHE.lock() {
        *guard = Some((Instant::now(), pkg.clone()));
    }
    pkg
}
