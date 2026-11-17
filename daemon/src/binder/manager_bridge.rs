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
//! `untrusted_app`, so the app cannot open the daemon sockets directly and
//! historically shelled out to `su` for every read and write. Instead the
//! Manager hosts a `LocalServerSocket` inside its own private directory and
//! the helper (root) connects *out* to it. SELinux allows a privileged domain
//! to connect to an app-owned socket, while the app never needs any permission
//! it does not already have.
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
use std::os::unix::net::UnixStream;
use std::path::Path;
use std::thread;
use std::time::Duration;

use crate::core::detector::CONFIG_DIR;

const APP_SOCKETS: [&str; 2] = [
    "/data/user/0/com.luminatweaks/files/lumina_bridge.sock",
    "/data/data/com.luminatweaks/files/lumina_bridge.sock",
];
const SESSIONS_FILE: &str = "sessions.json";
const RECORD_APPS_FILE: &str = "record_apps.json";

const POLL_INTERVAL: Duration = Duration::from_millis(400);
const READ_TIMEOUT: Duration = Duration::from_secs(2);
const WRITE_TIMEOUT: Duration = Duration::from_secs(2);
const MAX_PAYLOAD: usize = 4 * 1024 * 1024;

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

fn run() {
    let mut connected_once = false;

    loop {
        let Some(stream) = connect() else {
            thread::sleep(POLL_INTERVAL);
            continue;
        };

        if !connected_once {
            connected_once = true;
            log!("[SYSTEM] Manager bridge linked");
        }

        if !serve(stream) {
            thread::sleep(POLL_INTERVAL);
        }
    }
}

fn connect() -> Option<UnixStream> {
    for path in APP_SOCKETS {
        if let Ok(stream) = UnixStream::connect(path) {
            let _ = stream.set_read_timeout(Some(READ_TIMEOUT));
            let _ = stream.set_write_timeout(Some(WRITE_TIMEOUT));
            return Some(stream);
        }
    }
    None
}

fn serve(stream: UnixStream) -> bool {
    let Ok(reader_stream) = stream.try_clone() else {
        return false;
    };
    let mut reader = BufReader::new(reader_stream);
    let mut writer = stream;
    let mut header = String::new();

    loop {
        match reader.read_line(&mut header) {
            Ok(0) => return false,
            Ok(_) => {}
            Err(error)
                if matches!(
                    error.kind(),
                    ErrorKind::WouldBlock | ErrorKind::TimedOut | ErrorKind::Interrupted
                ) =>
            {
                // An idle screen keeps the connection open; the app may send a
                // request at any moment. Re-arm the timeout implicitly by
                // looping instead of tearing the socket down. Keep the buffer
                // so a partially delivered header is not discarded.
                continue;
            }
            Err(_) => return false,
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
            return false;
        }

        let mut payload = vec![0u8; payload_len];
        if payload_len > 0 && reader.read_exact(&mut payload).is_err() {
            return false;
        }
        header.clear();

        let handled = match command.as_str() {
            "PING" => respond_ok(&mut writer, &[]),
            "SESSION_READ" => respond_file(&mut writer, SESSIONS_FILE),
            "RECORD_APPS_READ" => respond_file(&mut writer, RECORD_APPS_FILE),
            "SESSION_WRITE" => write_json(&mut writer, SESSIONS_FILE, &payload),
            "RECORD_APPS_WRITE" => write_json(&mut writer, RECORD_APPS_FILE, &payload),
            other => {
                let reason = format!("unknown {}", other);
                respond_error(&mut writer, &reason)
            }
        };

        if handled.is_err() {
            return false;
        }
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
