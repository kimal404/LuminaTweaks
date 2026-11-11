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
use std::sync::{Arc, Mutex, OnceLock};
use std::thread;
use std::time::Duration;

pub const IPC_SOCKET_PRIMARY: &str = "/dev/socket/lumina_ipc.sock";
pub const IPC_SOCKET_FALLBACK: &str = "/data/local/tmp/lumina_ipc.sock";

static CLIENTS: OnceLock<Arc<Mutex<Vec<UnixStream>>>> = OnceLock::new();

fn get_clients() -> &'static Arc<Mutex<Vec<UnixStream>>> {
    CLIENTS.get_or_init(|| Arc::new(Mutex::new(Vec::new())))
}

pub struct SharedState {
    pub current_profile: Mutex<String>,
    pub active_package: Mutex<Option<String>>,
    pub reload_flag: AtomicBool,
}

impl SharedState {
    pub fn new() -> Self {
        Self {
            current_profile: Mutex::new("Balanced".to_string()),
            active_package: Mutex::new(None),
            reload_flag: AtomicBool::new(false),
        }
    }
}

pub struct IpcServer;

impl IpcServer {
    pub fn start(shared_state: Arc<SharedState>) {
        thread::Builder::new()
            .name("lumina-ipc".to_string())
            .spawn(move || {
                run_server(shared_state);
            })
            .expect("Failed to spawn IPC thread");
    }
}

pub fn broadcast_toast(message: &str) {
    let clients_arc = get_clients();
    let mut guard = match clients_arc.lock() {
        Ok(g) => g,
        Err(p) => p.into_inner(),
    };

    let payload = format!("TOAST {}\n", message);
    let bytes = payload.as_bytes();

    guard.retain_mut(|client| {
        let _ = client.set_write_timeout(Some(Duration::from_millis(50)));
        client.write_all(bytes).is_ok() && client.flush().is_ok()
    });
}

fn bind_socket() -> (UnixListener, String) {
    for path in [IPC_SOCKET_PRIMARY, IPC_SOCKET_FALLBACK] {
        if let Ok(meta) = fs::symlink_metadata(path) {
            if !meta.is_dir() {
                let _ = fs::remove_file(path);
            }
        }

        if let Ok(listener) = UnixListener::bind(path) {
            let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o666));
            return (listener, path.to_string());
        }
    }

    panic!("Failed to bind Lumina IPC UnixListener on all candidate paths");
}

fn run_server(shared_state: Arc<SharedState>) {
    let (listener, active_path) = bind_socket();
    crate::log!("[SYSTEM] IPC Server listening on {}", active_path);

    let clients_arc = get_clients();

    for incoming in listener.incoming() {
        match incoming {
            Ok(stream) => {
                let _ = stream.set_read_timeout(Some(Duration::from_millis(200)));
                let _ = stream.set_write_timeout(Some(Duration::from_millis(200)));

                if let Ok(clone_writer) = stream.try_clone() {
                    let mut guard = match clients_arc.lock() {
                        Ok(g) => g,
                        Err(p) => p.into_inner(),
                    };
                    guard.push(clone_writer);
                }

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
                } else if trimmed == "RELOAD" {
                    shared_state.reload_flag.store(true, Ordering::Relaxed);
                    let writer = reader.get_mut();
                    let _ = writer.write_all(b"OK\n");
                    let _ = writer.flush();
                }
            }
            Err(e) if e.kind() == std::io::ErrorKind::WouldBlock || e.kind() == std::io::ErrorKind::TimedOut => {
                thread::sleep(Duration::from_millis(50));
            }
            Err(_) => break,
        }
    }
}
