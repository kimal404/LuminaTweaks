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

use std::fs;
use std::io::{BufRead, BufReader, Write};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::net::{UnixListener, UnixStream};
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;

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

const PRIMARY_SOCKET: &str = "/dev/socket/lumina.sock";
const FALLBACK_SOCKET: &str = "/data/local/tmp/lumina.sock";

pub struct IpcServer;

impl IpcServer {
    fn get_bound_listener() -> (UnixListener, &'static str) {
        let _ = fs::remove_file(PRIMARY_SOCKET);
        if let Ok(listener) = UnixListener::bind(PRIMARY_SOCKET) {
            let _ = fs::set_permissions(PRIMARY_SOCKET, fs::Permissions::from_mode(0o666));
            return (listener, PRIMARY_SOCKET);
        }

        let _ = fs::remove_file(FALLBACK_SOCKET);
        let listener = UnixListener::bind(FALLBACK_SOCKET)
            .expect("Fatal: Gagal mengikat IPC socket pada /dev/socket maupun /data/local/tmp");
        let _ = fs::set_permissions(FALLBACK_SOCKET, fs::Permissions::from_mode(0o666));
        (listener, FALLBACK_SOCKET)
    }

    pub fn start(state: Arc<SharedState>) {
        thread::spawn(move || {
            let (listener, active_path) = Self::get_bound_listener();
            crate::log!("[IPC] Unix Domain Socket aktif di {}", active_path);

            for stream in listener.incoming().flatten() {
                let state_clone = Arc::clone(&state);
                thread::spawn(move || {
                    Self::handle_client(stream, state_clone);
                });
            }
        });
    }

    fn handle_client(mut stream: UnixStream, state: Arc<SharedState>) {
        let mut reader = BufReader::new(stream.try_clone().unwrap());
        let mut line = String::new();

        if reader.read_line(&mut line).is_ok() {
            let cmd = line.trim();
            let response = match cmd {
                "PING" => "PONG\n".to_string(),
                "GET_STATUS" => {
                    let prof = state.current_profile.lock().unwrap().clone();
                    let pkg = state.active_package.lock().unwrap().clone();
                    let pkg_json = match pkg {
                        Some(p) => format!(r#""{}""#, p),
                        None => "null".to_string(),
                    };
                    format!(
                        r#"{{"status":"OK","daemon_pid":{},"profile":"{}","active_package":{}}}"#,
                        std::process::id(),
                        prof,
                        pkg_json
                    ) + "\n"
                }
                "RELOAD_CONFIG" => {
                    state.reload_flag.store(true, Ordering::Relaxed);
                    r#"{"status":"OK","message":"Config reload scheduled"}"#.to_string() + "\n"
                }
                _ => r#"{"status":"ERROR","message":"Perintah tidak dikenal"}"#.to_string() + "\n",
            };

            let _ = stream.write_all(response.as_bytes());
            let _ = stream.flush();
        }
    }
}
