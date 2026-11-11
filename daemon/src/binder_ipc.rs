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

//! Event producer socket hosted by the `lumina-binder` helper.
//!
//! One active client (the `luminad` daemon) is allowed. New clients receive
//! pushed `APP_FOCUS`, `APP_DIED`, and `SCREEN` events. Legacy clients that
//! open with `PING` keep the old request/response `FOCUS`/`WAIT` behavior
//! until the daemon parser migrates.

use std::ffi::CString;
use std::fs;
use std::io::{BufRead, BufReader, ErrorKind, Write};
use std::os::fd::{AsRawFd, RawFd};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::net::{UnixListener, UnixStream};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::Duration;

use crate::binder::producer::{monotonic_ms, Event, Focus, Shared, PROTO_VERSION};
use crate::binder_protocol;

const LINE_LIMIT: usize = 512;
const WRITE_TIMEOUT: Duration = Duration::from_millis(5);
const HANDSHAKE_TIMEOUT: Duration = Duration::from_millis(800);
const CLASSIFY_TIMEOUT: Duration = Duration::from_millis(60);
const READ_TIMEOUT: Duration = Duration::from_millis(250);
const WRITER_TICK: Duration = Duration::from_millis(100);

pub fn serve(shared: Arc<Shared>) -> ! {
    let (listener, path) = bind_listener();
    crate::log!("[SYSTEM] Binder socket listening on {}", path);

    let current: Arc<Mutex<Option<Session>>> = Arc::new(Mutex::new(None));

    for stream in listener.incoming() {
        match stream {
            Ok(stream) => {
                if !authorized(&stream) {
                    let _ = stream.shutdown(std::net::Shutdown::Both);
                    continue;
                }

                let shared = Arc::clone(&shared);
                let current = Arc::clone(&current);
                thread::spawn(move || {
                    let session = match Session::start(stream, shared) {
                        Some(session) => session,
                        None => return,
                    };

                    let previous = current
                        .lock()
                        .unwrap_or_else(|poisoned| poisoned.into_inner())
                        .replace(session.clone());
                    if let Some(previous) = previous {
                        previous.stop();
                        previous.join();
                    }

                    session.wait();

                    let mut guard = current
                        .lock()
                        .unwrap_or_else(|poisoned| poisoned.into_inner());
                    if guard.as_ref().map(Session::id) == Some(session.id()) {
                        guard.take();
                    }
                });
            }
            Err(error) => {
                if error.kind() != ErrorKind::Interrupted {
                    crate::log!("[SYSTEM] Binder accept failed: {}", error);
                }
            }
        }
    }

    std::process::exit(1)
}

struct Inner {
    writer: Mutex<UnixStream>,
    stop: AtomicBool,
    handles: Mutex<Vec<JoinHandle<()>>>,
}

#[derive(Clone)]
struct Session {
    id: usize,
    inner: Arc<Inner>,
}

impl Session {
    fn start(stream: UnixStream, shared: Arc<Shared>) -> Option<Self> {
        let reader_stream = stream.try_clone().ok()?;
        let mut reader = BufReader::new(reader_stream);
        let _ = reader.get_ref().set_read_timeout(Some(CLASSIFY_TIMEOUT));

        let mut first = String::new();
        let mode = match reader.read_line(&mut first) {
            Ok(0) => return None,
            Ok(_) => {
                let line = first.trim();
                if line.starts_with("HELLO") {
                    Mode::ModernClient
                } else {
                    Mode::Legacy(Some(line.to_string()))
                }
            }
            Err(error) if matches!(error.kind(), ErrorKind::WouldBlock | ErrorKind::TimedOut) => {
                Mode::ModernServer
            }
            Err(_) => return None,
        };
        let _ = reader.get_ref().set_read_timeout(None);

        let inner = Arc::new(Inner {
            writer: Mutex::new(stream.try_clone().ok()?),
            stop: AtomicBool::new(false),
            handles: Mutex::new(Vec::new()),
        });

        match mode {
            Mode::Legacy(first) => {
                shared.begin_session();
                let legacy_inner = Arc::clone(&inner);
                let legacy_shared = Arc::clone(&shared);
                let handle = thread::Builder::new()
                    .name("binder-legacy".to_string())
                    .spawn(move || legacy_loop(reader, legacy_inner, legacy_shared, first))
                    .ok()?;
                if let Ok(mut handles) = inner.handles.lock() {
                    handles.push(handle);
                }
                crate::log!("[SYSTEM] Binder legacy client connected");
            }
            Mode::ModernClient | Mode::ModernServer => {
                if !handshake(&stream, &mut reader, matches!(mode, Mode::ModernClient)) {
                    crate::log!("[SYSTEM] Binder handshake failed");
                    let _ = stream.shutdown(std::net::Shutdown::Both);
                    return None;
                }
                shared.begin_session();

                let writer_inner = Arc::clone(&inner);
                let writer_shared = Arc::clone(&shared);
                let writer_handle = thread::Builder::new()
                    .name("binder-writer".to_string())
                    .spawn(move || writer_loop(writer_inner, writer_shared))
                    .ok()?;

                let reader_inner = Arc::clone(&inner);
                let reader_shared = Arc::clone(&shared);
                let reader_handle = thread::Builder::new()
                    .name("binder-reader".to_string())
                    .spawn(move || reader_loop(reader, reader_inner, reader_shared))
                    .ok()?;

                if let Ok(mut handles) = inner.handles.lock() {
                    handles.push(writer_handle);
                    handles.push(reader_handle);
                }
                crate::log!("[SYSTEM] Binder daemon connected");
            }
        }

        Some(Self {
            id: Arc::as_ptr(&inner) as usize,
            inner,
        })
    }

    fn id(&self) -> usize {
        self.id
    }

    fn stop(&self) {
        self.inner.stop.store(true, Ordering::Relaxed);
        if let Ok(stream) = self.inner.writer.lock() {
            let _ = stream.shutdown(std::net::Shutdown::Both);
        }
    }

    fn join(&self) {
        let mut guard = self
            .inner
            .handles
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        let handles = std::mem::take(&mut *guard);
        for handle in handles {
            let _ = handle.join();
        }
    }

    fn wait(&self) {
        while !self.inner.stop.load(Ordering::Relaxed) {
            thread::sleep(Duration::from_millis(250));
        }
        self.join();
    }
}

enum Mode {
    Legacy(Option<String>),
    ModernClient,
    ModernServer,
}

fn bind_listener() -> (UnixListener, String) {
    let mut errors = Vec::new();

    for path in binder_protocol::socket_candidates() {
        remove_stale(&path);
        match UnixListener::bind(&path) {
            Ok(listener) => {
                let _ = fs::set_permissions(&path, fs::Permissions::from_mode(0o600));
                if let Ok(name) = CString::new(path.as_str()) {
                    let _ = unsafe { libc::chown(name.as_ptr(), 0, 0) };
                }
                return (listener, path);
            }
            Err(error) => errors.push(format!("{}: {}", path, error)),
        }
    }

    crate::log!("[SYSTEM] Binder bind failed: {}", errors.join("; "));
    std::process::exit(1)
}

fn remove_stale(path: &str) {
    if let Ok(meta) = fs::symlink_metadata(path) {
        if !meta.is_dir() {
            let _ = fs::remove_file(path);
        }
    }
}

fn authorized(stream: &UnixStream) -> bool {
    match peer_cred(stream.as_raw_fd()) {
        Some((_, 0)) => true,
        Some((pid, uid)) => {
            crate::log!("[SYSTEM] Binder rejected peer pid={} uid={}", pid, uid);
            false
        }
        None => false,
    }
}

fn peer_cred(fd: RawFd) -> Option<(i32, u32)> {
    let mut cred = libc::ucred {
        pid: 0,
        uid: 0,
        gid: 0,
    };
    let mut len = std::mem::size_of::<libc::ucred>() as libc::socklen_t;
    let result = unsafe {
        libc::getsockopt(
            fd,
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            &mut cred as *mut libc::ucred as *mut libc::c_void,
            &mut len,
        )
    };
    if result == 0 {
        Some((cred.pid, cred.uid))
    } else {
        None
    }
}

fn handshake(stream: &UnixStream, reader: &mut BufReader<UnixStream>, client_hello: bool) -> bool {
    if client_hello {
        let line = format!("OK {}\n", PROTO_VERSION);
        if write_direct(stream, &line).is_err() {
            return false;
        }
        return true;
    }

    let pid = std::process::id();
    let line = format!("HELLO {} {} {}\n", PROTO_VERSION, pid, monotonic_ms());
    if write_direct(stream, &line).is_err() {
        return false;
    }

    let _ = reader.get_ref().set_read_timeout(Some(HANDSHAKE_TIMEOUT));
    let mut response = String::new();
    let result = reader.read_line(&mut response);
    let _ = reader.get_ref().set_read_timeout(None);

    if result.is_err() {
        return false;
    }

    let ok = response.trim() == format!("OK {}", PROTO_VERSION);
    if !ok {
        crate::log!(
            "[SYSTEM] Binder unexpected handshake reply: {}",
            response.trim()
        );
    }
    ok
}

fn write_direct(stream: &UnixStream, line: &str) -> std::io::Result<()> {
    if stream.set_write_timeout(Some(WRITE_TIMEOUT)).is_err() {
        return Err(std::io::Error::new(
            ErrorKind::WouldBlock,
            "write timeout unavailable",
        ));
    }
    let mut writer = stream;
    writer.write_all(line.as_bytes())?;
    writer.flush()
}

fn write_shared(inner: &Inner, line: &str) -> std::io::Result<()> {
    let stream = inner
        .writer
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    write_direct(&stream, line)
}

fn writer_loop(inner: Arc<Inner>, shared: Arc<Shared>) {
    if send_snapshot(&inner, &shared).is_err() {
        inner.stop.store(true, Ordering::Relaxed);
        return;
    }

    loop {
        if inner.stop.load(Ordering::Relaxed) {
            break;
        }

        let Some(event) = shared.pop_event(WRITER_TICK) else {
            continue;
        };

        if send_event(&inner, &shared, event).is_err() {
            inner.stop.store(true, Ordering::Relaxed);
            break;
        }
    }

    if let Ok(stream) = inner.writer.lock() {
        let _ = stream.shutdown(std::net::Shutdown::Both);
    }
}

fn send_snapshot(inner: &Inner, shared: &Shared) -> std::io::Result<()> {
    shared.wait_screen_ready(Duration::from_millis(1200));
    let (screen_on, focus) = shared.snapshot();
    let seq = shared.next_seq();
    let line = format!(
        "SCREEN {} {} {}\n",
        if screen_on { "on" } else { "off" },
        seq,
        monotonic_ms()
    );
    write_shared(inner, &line)?;

    if let Some(focus) = focus {
        let seq = shared.next_seq();
        let line = format!(
            "APP_FOCUS {} {} {} {} {}\n",
            focus.package,
            focus.pid,
            focus.uid,
            seq,
            monotonic_ms()
        );
        write_shared(inner, &line)?;
    }
    Ok(())
}

fn send_event(inner: &Inner, shared: &Shared, event: Event) -> std::io::Result<()> {
    let seq = shared.next_seq();
    let ts = monotonic_ms();
    let line = match event {
        Event::Focus(Focus { package, pid, uid }) => {
            if !valid_package(&package) {
                return Ok(());
            }
            format!("APP_FOCUS {} {} {} {} {}\n", package, pid, uid, seq, ts)
        }
        Event::Died(death) => {
            let package = death
                .package
                .filter(|value| valid_package(value))
                .unwrap_or_else(|| "-".to_string());
            crate::log!(
                "[SYSTEM] APP_DIED pid={} uid={} pkg={}",
                death.pid,
                death.uid,
                package
            );
            format!(
                "APP_DIED {} {} {} {} {}\n",
                death.pid, death.uid, package, seq, ts
            )
        }
        Event::Screen(on) => format!("SCREEN {} {} {}\n", if on { "on" } else { "off" }, seq, ts),
    };
    write_shared(inner, &line)
}

fn valid_package(package: &str) -> bool {
    !package.is_empty()
        && package.len() <= 255
        && package
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_'))
}

fn reader_loop(mut reader: BufReader<UnixStream>, inner: Arc<Inner>, shared: Arc<Shared>) {
    let _ = reader.get_ref().set_read_timeout(Some(READ_TIMEOUT));
    let mut line = String::new();

    while !inner.stop.load(Ordering::Relaxed) {
        line.clear();
        match reader.read_line(&mut line) {
            Ok(0) => {
                inner.stop.store(true, Ordering::Relaxed);
                return;
            }
            Ok(_) => {
                let command = line.trim();
                if command.is_empty() || command.len() > LINE_LIMIT {
                    continue;
                }
                if let Some(response) = respond(command, &shared) {
                    if write_shared(&inner, &response).is_err() {
                        inner.stop.store(true, Ordering::Relaxed);
                        return;
                    }
                }
            }
            Err(error) if matches!(error.kind(), ErrorKind::WouldBlock | ErrorKind::TimedOut) => {}
            Err(_) => {
                inner.stop.store(true, Ordering::Relaxed);
                return;
            }
        }
    }
}

fn respond(command: &str, shared: &Shared) -> Option<String> {
    match command {
        "PING" => Some("PONG\n".to_string()),
        "SYNC" => Some(state_line(shared)),
        other => Some(format!("ERR 1 unknown {}\n", other)),
    }
}

fn state_line(shared: &Shared) -> String {
    let (screen_on, focus) = shared.snapshot();
    let screen = if screen_on { "on" } else { "off" };
    match focus {
        Some(focus) => format!("STATE {} {} {}\n", focus.package, focus.pid, screen),
        None => format!("STATE - 0 {}\n", screen),
    }
}

fn legacy_loop(
    mut reader: BufReader<UnixStream>,
    inner: Arc<Inner>,
    shared: Arc<Shared>,
    first: Option<String>,
) {
    let _ = reader.get_ref().set_read_timeout(Some(READ_TIMEOUT));
    let mut pending = first;
    let mut line = String::new();

    while !inner.stop.load(Ordering::Relaxed) {
        let command = match pending.take() {
            Some(command) => command,
            None => {
                line.clear();
                match reader.read_line(&mut line) {
                    Ok(0) => break,
                    Ok(_) => line.trim().to_string(),
                    Err(error)
                        if matches!(error.kind(), ErrorKind::WouldBlock | ErrorKind::TimedOut) =>
                    {
                        continue;
                    }
                    Err(_) => break,
                }
            }
        };

        if command.is_empty() || command.len() > LINE_LIMIT {
            continue;
        }

        let response = legacy_response(&command, &shared);
        if write_shared(&inner, &response).is_err() {
            break;
        }
    }

    inner.stop.store(true, Ordering::Relaxed);
}

fn legacy_response(command: &str, shared: &Shared) -> String {
    if command == "PING" {
        return "PONG\n".to_string();
    }

    if command == "FOCUS" {
        return match shared.legacy_focus() {
            Some(focus) => format!("FOCUS {}\n", focus.package),
            None => "NONE\n".to_string(),
        };
    }

    if let Some(rest) = command.strip_prefix("WAIT") {
        let millis = rest.trim().parse::<u64>().unwrap_or(1200).min(5000);
        shared.wait_legacy(Duration::from_millis(millis));
        return match shared.legacy_focus() {
            Some(focus) => format!("FOCUS {}\n", focus.package),
            None => "EVENT\n".to_string(),
        };
    }

    "ERROR\n".to_string()
}
