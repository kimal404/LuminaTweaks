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
use std::io::{BufRead, BufReader, Read, Write};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::net::{UnixListener, UnixStream};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::thread;
use std::time::Duration;

pub const IPC_SOCKET_PRIMARY: &str = "/data/adb/.config/lumina/lumina.sock";
pub const IPC_SOCKET_FALLBACK: &str = "/data/local/tmp/lumina_ipc.sock";
const MAX_IPC_LINE_BYTES: u64 = 1024;

fn ensure_parent_dir(path: &str) {
    if let Some(parent) = std::path::Path::new(path).parent() {
        let _ = fs::create_dir_all(parent);
    }
}

pub struct SharedState {
    pub current_profile: std::sync::Mutex<String>,
    pub active_package: std::sync::Mutex<Option<String>>,
    pub requested_profile: std::sync::Mutex<Option<String>>,
    pub reload_flag: AtomicBool,
    pub fps_preview_until_ms: AtomicU64,
}

impl SharedState {
    pub fn new() -> Self {
        Self {
            current_profile: std::sync::Mutex::new("Balanced".to_string()),
            active_package: std::sync::Mutex::new(None),
            requested_profile: std::sync::Mutex::new(None),
            reload_flag: AtomicBool::new(false),
            fps_preview_until_ms: AtomicU64::new(0),
        }
    }

    pub fn request_profile(&self, name: &str) {
        if let Ok(mut slot) = self.requested_profile.lock() {
            *slot = Some(name.trim().to_string());
        }
    }

    pub fn take_requested_profile(&self) -> Option<String> {
        self.requested_profile
            .lock()
            .ok()
            .and_then(|mut slot| slot.take())
    }

    pub fn request_fps_preview(&self, seconds: u64) {
        let seconds = seconds.clamp(1, 3600);
        let until = now_ms().saturating_add(seconds.saturating_mul(1000));
        self.fps_preview_until_ms.store(until, Ordering::Relaxed);
    }

    pub fn stop_fps_preview(&self) {
        self.fps_preview_until_ms.store(0, Ordering::Relaxed);
    }

    pub fn fps_preview_active(&self) -> bool {
        self.fps_preview_until_ms.load(Ordering::Relaxed) > now_ms()
    }
}

fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis() as u64)
        .unwrap_or(0)
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
    for path in [
        "/dev/socket/lumina_bridge.sock",
        "/data/adb/.config/lumina/lumina_bridge.sock",
    ] {
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
        let read = reader
            .by_ref()
            .take(MAX_IPC_LINE_BYTES)
            .read_line(&mut line);
        match read {
            Ok(0) => break,
            Ok(bytes) => {
                if bytes as u64 >= MAX_IPC_LINE_BYTES && !line.ends_with('\n') {
                    crate::log!(
                        "[WARN] IPC request exceeded {} bytes; closing client",
                        MAX_IPC_LINE_BYTES
                    );
                    break;
                }
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
                } else if trimmed == "FPS_PREVIEW_STOP" {
                    shared_state.stop_fps_preview();
                    let writer = reader.get_mut();
                    let _ = writer.write_all(b"OK\n");
                    let _ = writer.flush();
                } else if let Some(rest) = trimmed.strip_prefix("FPS_PREVIEW") {
                    let seconds = rest.trim().parse::<u64>().unwrap_or(0);
                    if seconds == 0 {
                        shared_state.stop_fps_preview();
                    } else {
                        shared_state.request_fps_preview(seconds);
                    }
                    let writer = reader.get_mut();
                    let _ = writer.write_all(b"OK\n");
                    let _ = writer.flush();
                } else if let Some(rest) = trimmed.strip_prefix("SET_PROFILE ") {
                    let name = rest.trim();
                    if !name.is_empty()
                        && name
                            .chars()
                            .all(|c| c.is_ascii_alphanumeric() || c == ' ' || c == '_')
                    {
                        shared_state.request_profile(name);
                        let writer = reader.get_mut();
                        let _ = writer.write_all(b"OK\n");
                        let _ = writer.flush();
                    } else {
                        let writer = reader.get_mut();
                        let _ = writer.write_all(b"ERR invalid profile\n");
                        let _ = writer.flush();
                    }
                } else if trimmed == "GET_AUTO_PROFILE" {
                    let enabled = crate::core::detector::is_auto_profile_enabled();
                    let resp = format!("AUTO_PROFILE {}\n", enabled);
                    let writer = reader.get_mut();
                    let _ = writer.write_all(resp.as_bytes());
                    let _ = writer.flush();
                } else if let Some(rest) = trimmed.strip_prefix("SET_AUTO_PROFILE ") {
                    let enabled = matches!(
                        rest.trim().to_ascii_lowercase().as_str(),
                        "1" | "on" | "true" | "enable" | "enabled"
                    );
                    let ok = crate::core::detector::set_auto_profile(enabled);
                    let writer = reader.get_mut();
                    let response = if ok { "OK\n" } else { "ERR write failed\n" };
                    let _ = writer.write_all(response.as_bytes());
                    let _ = writer.flush();
                } else if trimmed == "RELOAD" || trimmed == "RELOAD_CONFIG" {
                    shared_state.reload_flag.store(true, Ordering::Relaxed);
                    let writer = reader.get_mut();
                    let _ = writer.write_all(b"OK\n");
                    let _ = writer.flush();
                } else if trimmed == "CAPABILITY_REPORT" {
                    let report = crate::core::capability::report_json();
                    let writer = reader.get_mut();
                    let _ = writer.write_all(report.as_bytes());
                    let _ = writer.write_all(b"\n");
                    let _ = writer.flush();
                } else if trimmed == "CAPABILITY_REPROBE" {
                    crate::core::capability::probe_all();
                    let writer = reader.get_mut();
                    let _ = writer.write_all(b"OK\n");
                    let _ = writer.flush();
                } else if let Some(rest) = trimmed.strip_prefix("CAPABILITY_MODE") {
                    let ok = crate::core::capability::set_mode(rest.trim());
                    let writer = reader.get_mut();
                    let _ = writer.write_all(if ok { b"OK\n" } else { b"ERR\n" });
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
