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

//! Zero-root bridge between the Manager app and the privileged helper.
//!
//! `/data/adb` is `0700 root:root` and `adb_data_file` is unreachable from
//! `untrusted_app`, so the app cannot open the daemon config files directly.
//!
//! The socket is hosted by the **daemon** under `/dev/socket`, the same layout
//! that is known to work on shipping Android 14/15 devices: a root domain may
//! bind a `sock_file` in `socket_device`, while the app only has to *connect*
//! as a client, which needs no extra privilege inside its own process.
//!
//! An earlier revision flipped this around and had the app host a
//! `LocalServerSocket` in `filesDir`. On device that server socket was never
//! created (no `Manager bridge linked` line ever appeared, and it still failed
//! with SELinux permissive), so every `SESSION_READ` silently fell back to an
//! empty local mirror. Hosting here removes that failure mode entirely.
//!
//! Requests are framed so multi-kilobyte JSON survives an arbitrary poll
//! boundary:
//!
//! ```text
//! request:  CMD <payload-bytes>\n<payload-bytes bytes>
//! response: OK <payload-bytes>\n<payload-bytes bytes>
//!           ERR <reason>\n
//! ```
//!
//! The bridge only exposes the two session files, validates that writes are
//! JSON arrays, and persists them atomically with `0644` permissions.

#![allow(dead_code)]

use std::fs;
use std::io::{BufRead, BufReader, ErrorKind, Read, Write};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::net::{UnixListener, UnixStream};
use std::path::Path;
use std::sync::Mutex;
use std::thread;
use std::time::Duration;

use crate::core::detector::CONFIG_DIR;

/// Primary endpoint: the standard socket namespace is reachable from both the
/// root daemon and `untrusted_app` once the module's sepolicy rules are in
/// place. The runtime-directory path is kept as a fallback for ROMs that keep
/// `/dev/socket` locked down.
const BRIDGE_SOCKETS: [&str; 2] = [
    "/dev/socket/lumina_bridge.sock",
    "/data/adb/.config/lumina/lumina_bridge.sock",
];
const SESSIONS_FILE: &str = "sessions.json";
const RECORD_APPS_FILE: &str = "record_apps.json";

const READ_TIMEOUT: Duration = Duration::from_secs(2);
// A SESSION_READ response can carry the whole sessions.json (hundreds of KB).
// Give the write side enough headroom that a busy app thread cannot turn a
// complete payload into a partial one.
const WRITE_TIMEOUT: Duration = Duration::from_secs(8);
const MAX_PAYLOAD: usize = 4 * 1024 * 1024;

static LISTEN_PATH: Mutex<Option<String>> = Mutex::new(None);

/// Starts the bridge thread once. `lumina-binder` calls this before entering
/// its blocking socket loop.
pub fn start() {
    let spawned = thread::Builder::new()
        .name("manager-bridge".to_string())
        .spawn(run);
    if let Err(error) = spawned {
        log!("[SYSTEM] Manager bridge thread failed: {}", error);
    }
}

/// Path the daemon ended up listening on, once bound.
pub fn listen_path() -> Option<String> {
    LISTEN_PATH.lock().ok().and_then(|path| path.clone())
}

fn bind_listener() -> Option<(UnixListener, String)> {
    for path in BRIDGE_SOCKETS {
        if let Some(parent) = Path::new(path).parent() {
            let _ = fs::create_dir_all(parent);
        }

        // A stale socket from a previous boot blocks bind(); remove it first.
        if let Ok(meta) = fs::symlink_metadata(path) {
            if !meta.is_dir() {
                let _ = fs::remove_file(path);
            }
        }

        match UnixListener::bind(path) {
            Ok(listener) => {
                // 0666 so a differently-policed app domain can still open the
                // node; SELinux, not the mode bits, is the real gate.
                let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o666));
                return Some((listener, path.to_string()));
            }
            Err(error) => {
                log!("[WARN] Manager bridge bind {} failed: {}", path, error);
            }
        }
    }
    None
}

fn run() {
    let (listener, path) = match bind_listener() {
        Some(bound) => bound,
        None => {
            log!("[WARN] Manager bridge unavailable: no socket path could be bound");
            return;
        }
    };

    if let Ok(mut slot) = LISTEN_PATH.lock() {
        *slot = Some(path.clone());
    }
    log!("[SYSTEM] Manager bridge listening on {}", path);

    for incoming in listener.incoming() {
        match incoming {
            Ok(stream) => {
                let _ = stream.set_read_timeout(Some(READ_TIMEOUT));
                let _ = stream.set_write_timeout(Some(WRITE_TIMEOUT));
                thread::spawn(move || {
                    if serve(stream) {
                        log!("[SYSTEM] Manager bridge linked");
                    }
                });
            }
            Err(error) => {
                log!("[SYSTEM] Manager bridge accept error: {}", error);
            }
        }
    }
}

fn serve(stream: UnixStream) -> bool {
    let Ok(reader_stream) = stream.try_clone() else {
        return false;
    };
    let mut reader = BufReader::new(reader_stream);
    let mut writer = stream;
    let mut header = String::new();
    let mut served_any = false;

    loop {
        match reader.read_line(&mut header) {
            Ok(0) => return served_any,
            Ok(_) => {}
            Err(error)
                if matches!(
                    error.kind(),
                    ErrorKind::WouldBlock | ErrorKind::TimedOut | ErrorKind::Interrupted
                ) =>
            {
                // An idle connection may sit between screen visits; a read
                // timeout is not a disconnect, so re-arm and keep any partial
                // header that is already buffered.
                continue;
            }
            Err(_) => return served_any,
        }

        let (command, payload_len) = {
            let line = header.trim_end();
            if line.is_empty() {
                header.clear();
                continue;
            }

            let mut parts = line.splitn(2, ' ');
            let command = parts.next().unwrap_or("").to_string();
            let payload_len = parts
                .next()
                .and_then(|value| value.trim().parse::<usize>().ok())
                .unwrap_or(0);
            (command, payload_len)
        };

        if payload_len > MAX_PAYLOAD {
            let _ = respond_error(&mut writer, "payload too large");
            return served_any;
        }

        let mut payload = vec![0u8; payload_len];
        if payload_len > 0 && reader.read_exact(&mut payload).is_err() {
            return served_any;
        }
        header.clear();

        let handled = match command.as_str() {
            "PING" => respond_ok(&mut writer, &[]),
            "SESSION_READ" => respond_file(&mut writer, SESSIONS_FILE),
            "RECORD_APPS_READ" => respond_file(&mut writer, RECORD_APPS_FILE),
            "SESSION_WRITE" => write_json(&mut writer, SESSIONS_FILE, &payload),
            "ANGLE_STATUS" => {
                let support = crate::core::angle::detect_angle_support();
                let json = serde_json::json!({
                    "libraries": support.libraries,
                    "declared": support.declared,
                    "package": support.package,
                    "settingsKeys": support.settings_keys,
                    "usable": support.usable(),
                    "detail": support.describe(),
                });
                respond_ok(&mut writer, json.to_string().as_bytes())
            }
            "RECORD_APPS_WRITE" => {
                // Drop the recorder's mtime cache so a freshly armed package is
                // picked up on the next tick (~400 ms) instead of after the
                // 2 s config poll.
                let written = write_json(&mut writer, RECORD_APPS_FILE, &payload);
                if written.is_ok() {
                    crate::core::mark_record_apps_stale();
                }
                written
            }
            other => {
                let reason = format!("unknown {}", other);
                respond_error(&mut writer, &reason)
            }
        };

        if handled.is_err() {
            return served_any;
        }
        served_any = true;
    }
}

fn config_path(name: &str) -> String {
    format!("{}/{}", CONFIG_DIR, name)
}

fn respond_file(writer: &mut UnixStream, name: &str) -> std::io::Result<()> {
    match fs::read(config_path(name)) {
        Ok(content) => respond_ok(writer, &content),
        // "Not found" is returned as an empty success so the Manager can fall
        // back to its local mirror instead of mistaking it for an empty file.
        Err(error) if error.kind() == ErrorKind::NotFound => respond_ok(writer, &[]),
        Err(error) => respond_error(writer, &error.to_string()),
    }
}

fn write_json(writer: &mut UnixStream, name: &str, payload: &[u8]) -> std::io::Result<()> {
    match serde_json::from_slice::<serde_json::Value>(payload) {
        Ok(serde_json::Value::Array(_)) => {}
        Ok(_) => return respond_error(writer, "expected a JSON array"),
        Err(_) => return respond_error(writer, "invalid JSON"),
    }

    match write_atomic(&config_path(name), payload) {
        Ok(()) => respond_ok(writer, &[]),
        Err(error) => respond_error(writer, &error.to_string()),
    }
}

fn respond_ok(writer: &mut UnixStream, payload: &[u8]) -> std::io::Result<()> {
    write!(writer, "OK {}\n", payload.len())?;
    if !payload.is_empty() {
        writer.write_all(payload)?;
    }
    writer.flush()
}

fn respond_error(writer: &mut UnixStream, reason: &str) -> std::io::Result<()> {
    writeln!(writer, "ERR {}", reason)?;
    writer.flush()
}

/// Mirrors the daemon's persistence rules: parent directory creation, a
/// `.tmp` sibling, `0644`, then an atomic rename. A half-written file can
/// therefore never be observed by the recorder or the UI.
fn write_atomic(path: &str, content: &[u8]) -> std::io::Result<()> {
    if let Some(parent) = Path::new(path).parent() {
        fs::create_dir_all(parent)?;
    }

    let temp = format!("{}.tmp", path);
    fs::write(&temp, content)?;
    let result = fs::set_permissions(&temp, fs::Permissions::from_mode(0o644))
        .and_then(|_| fs::rename(&temp, path));

    if result.is_err() {
        let _ = fs::remove_file(&temp);
    }
    result
}
