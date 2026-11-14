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

use std::fs;
use std::io::{BufRead, BufReader, Write};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::net::{UnixListener, UnixStream};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

/// `/dev/socket` is reserved for init on modern Android and SELinux denies
/// non-init daemons from binding there. The module runtime directory is
/// writable by both luminad and the Manager (through su), so it is the
/// primary location; `/data/local/tmp` remains as a last-resort fallback.
pub const IPC_SOCKET_PRIMARY: &str = "/data/adb/.config/lumina/lumina.sock";
pub const IPC_SOCKET_FALLBACK: &str = "/data/local/tmp/lumina_ipc.sock";

fn ensure_parent_dir(path: &str) {
    if let Some(parent) = std::path::Path::new(path).parent() {
        let _ = fs::create_dir_all(parent);
    }
}

pub struct SharedState {
    pub current_profile: std::sync::Mutex<String>,
    pub active_package: std::sync::Mutex<Option<String>>,
    pub reload_flag: AtomicBool,
}

impl SharedState {
    pub fn new() -> Self {
        Self {
            current_profile: std::sync::Mutex::new("Balanced".to_string()),
            active_package: std::sync::Mutex::new(None),
            reload_flag: AtomicBool::new(false),
        }
    }
}

pub struct IpcServer;

impl IpcServer {
    pub fn start(shared_state: Arc<SharedState>) {
        let spawned = thread::Builder::new()
            .name("lumina-ipc".to_string())
            .spawn(move || {
                run_server(shared_state);
            });
        if let Err(error) = spawned {
            crate::log!("[WARN] Failed to start IPC server: {}", error);
        }
    }
}

pub fn cleanup_socket() {
    for path in [IPC_SOCKET_PRIMARY, IPC_SOCKET_FALLBACK] {
        let _ = fs::remove_file(path);
    }
}

fn bind_socket() -> Option<(UnixListener, String)> {
    for path in [IPC_SOCKET_PRIMARY, IPC_SOCKET_FALLBACK] {
        ensure_parent_dir(path);

        if let Ok(meta) = fs::symlink_metadata(path) {
            if !meta.is_dir() {
                let _ = fs::remove_file(path);
            }
        }

        match UnixListener::bind(path) {
            Ok(listener) => {
                let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o600));
                return Some((listener, path.to_string()));
            }
            Err(error) => {
                crate::log!("[WARN] IPC bind {} failed: {}", path, error);
            }
        }
    }

    None
}

fn run_server(shared_state: Arc<SharedState>) {
    let Some((listener, active_path)) = bind_socket() else {
        crate::log!("[WARN] IPC unavailable: failed to bind any socket path");
        return;
    };
    crate::log!("[SYSTEM] IPC Server listening on {}", active_path);

    for incoming in listener.incoming() {
        match incoming {
            Ok(stream) => {
                let _ = stream.set_read_timeout(Some(Duration::from_millis(200)));
                let _ = stream.set_write_timeout(Some(Duration::from_millis(200)));
                let state_clone = Arc::clone(&shared_state);
                thread::spawn(move || {
                    handle_client(stream, state_clone);
                });
            }
            Err(error) => {
                crate::log!("[SYSTEM] IPC accept error: {}", error);
            }
        }
    }
}

fn handle_client(stream: UnixStream, shared_state: Arc<SharedState>) {
    let mut reader = BufReader::new(stream);
    let mut line = String::new();

    loop {
        line.clear();
        match reader.read_line(&mut line) {
            Ok(0) => break,
            Ok(_) => {
                let trimmed = line.trim();
                if trimmed.is_empty() {
                    continue;
                }

                if trimmed == "PING" {
                    let writer = reader.get_mut();
                    let _ = writer.write_all(b"PONG\n");
                    let _ = writer.flush();
                } else if trimmed == "GET_PROFILE" {
                    let current = shared_state
                        .current_profile
                        .lock()
                        .map(|p| p.clone())
                        .unwrap_or_else(|p| p.into_inner().clone());
                    let resp = format!("PROFILE {}\n", current);
                    let writer = reader.get_mut();
                    let _ = writer.write_all(resp.as_bytes());
                    let _ = writer.flush();
                } else if trimmed == "RELOAD" || trimmed == "RELOAD_CONFIG" {
                    shared_state.reload_flag.store(true, Ordering::Relaxed);
                    let writer = reader.get_mut();
                    let _ = writer.write_all(b"OK\n");
                    let _ = writer.flush();
                }
            }
            Err(e)
                if e.kind() == std::io::ErrorKind::WouldBlock
                    || e.kind() == std::io::ErrorKind::TimedOut =>
            {
                thread::sleep(Duration::from_millis(50));
            }
            Err(_) => break,
        }
    }
}
