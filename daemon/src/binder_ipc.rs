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
use std::sync::Arc;
use std::thread;
use std::time::Duration;

use crate::binder::BinderMonitor;
use crate::binder_protocol;

const DEFAULT_WAIT_MS: u64 = 1200;
const MAX_WAIT_MS: u64 = 5000;

pub fn serve(monitor: Arc<BinderMonitor>) -> ! {
    let (listener, path) = bind_listener();
    crate::log!("[BINDER-IPC] listening on {}", path);

    for stream in listener.incoming() {
        match stream {
            Ok(stream) => {
                let monitor = Arc::clone(&monitor);
                thread::spawn(move || handle_client(stream, monitor));
            }
            Err(error) => crate::log!("[BINDER-IPC] accept failed: {}", error),
        }
    }

    std::process::exit(1)
}

fn bind_listener() -> (UnixListener, String) {
    let mut errors = Vec::new();

    for path in binder_protocol::socket_candidates() {
        let _ = fs::remove_file(&path);
        match UnixListener::bind(&path) {
            Ok(listener) => {
                let _ = fs::set_permissions(&path, fs::Permissions::from_mode(0o666));
                return (listener, path);
            }
            Err(error) => errors.push(format!("{}: {}", path, error)),
        }
    }

    crate::log!("[BINDER-IPC] failed to bind socket: {}", errors.join("; "));
    std::process::exit(1);
}

fn handle_client(mut stream: UnixStream, monitor: Arc<BinderMonitor>) {
    let reader_stream = match stream.try_clone() {
        Ok(stream) => stream,
        Err(error) => {
            crate::log!("[BINDER-IPC] failed to clone client stream: {}", error);
            return;
        }
    };
    let mut reader = BufReader::new(reader_stream);

    loop {
        let mut line = String::new();
        match reader.read_line(&mut line) {
            Ok(0) => break,
            Ok(_) => {}
            Err(_) => break,
        }

        let command = line.trim();
        if command.is_empty() {
            continue;
        }

        if command.starts_with("WAIT") {
            let millis = command
                .strip_prefix("WAIT")
                .map(str::trim)
                .filter(|rest| !rest.is_empty())
                .and_then(|rest| rest.parse::<u64>().ok())
                .unwrap_or(DEFAULT_WAIT_MS)
                .min(MAX_WAIT_MS);
            let response = wait_response(&monitor, millis);
            if stream.write_all(response.as_bytes()).is_err() {
                break;
            }
            let _ = stream.flush();
            continue;
        }

        let response = match command {
            "PING" => "PONG\n".to_string(),
            "FOCUS" => match monitor.focused_package() {
                Some(package) => format!("FOCUS {}\n", package),
                None => "NONE\n".to_string(),
            },
            _ => "ERROR\n".to_string(),
        };

        if stream.write_all(response.as_bytes()).is_err() {
            break;
        }
        let _ = stream.flush();
    }
}

fn wait_response(monitor: &BinderMonitor, millis: u64) -> String {
    monitor.wait_for_event(Duration::from_millis(millis));
    match monitor.focused_package() {
        Some(package) => format!("FOCUS {}\n", package),
        None => "EVENT\n".to_string(),
    }
}
