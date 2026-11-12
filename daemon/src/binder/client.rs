/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::collections::VecDeque;
use std::io::{BufRead, BufReader, Write};
use std::os::unix::net::UnixStream;
use std::time::{Duration, Instant};

use crate::binder::protocol;

const CONNECT_RETRY: Duration = Duration::from_secs(5);
const INITIAL_RETRY: Duration = Duration::from_secs(2);
const HANDSHAKE_TIMEOUT: Duration = Duration::from_millis(800);
const READ_TIMEOUT: Duration = Duration::from_millis(120);
const WRITE_TIMEOUT: Duration = Duration::from_millis(300);
const LINE_LIMIT: usize = 512;

#[derive(Debug, Clone)]
pub struct Focus {
    pub package: String,
    pub pid: i32,
    pub uid: i32,
}

#[derive(Debug, Clone)]
pub struct Died {
    pub pid: i32,
    pub uid: i32,
    pub package: Option<String>,
}

pub struct BinderClient {
    stream: Option<UnixStream>,
    reader: Option<BufReader<UnixStream>>,
    next_retry: Instant,
    connected_once: bool,
    push: bool,
    last_seq: u64,
    focus: Option<Focus>,
    screen: Option<bool>,
    died: VecDeque<Died>,
}

impl BinderClient {
    pub fn new() -> Self {
        Self {
            stream: None,
            reader: None,
            next_retry: Instant::now(),
            connected_once: false,
            push: false,
            last_seq: 0,
            focus: None,
            screen: None,
            died: VecDeque::new(),
        }
    }

    pub fn is_push(&self) -> bool {
        self.push
    }

    pub fn is_connected(&self) -> bool {
        self.stream.is_some()
    }

    pub fn ping(&mut self) -> bool {
        if self.push {
            return self.stream.is_some();
        }
        matches!(
            self.request("PING", Duration::from_millis(300)).as_deref(),
            Some("PONG")
        )
    }

    pub fn focused_package(&mut self) -> Option<String> {
        if self.push {
            return self.focus.as_ref().map(|focus| focus.package.clone());
        }
        let response = self.request("FOCUS", Duration::from_millis(300))?;
        response
            .strip_prefix("FOCUS ")
            .map(|package| package.trim().to_string())
            .filter(|package| !package.is_empty())
    }

    pub fn active_focus(&self) -> Option<&Focus> {
        self.focus.as_ref()
    }

    pub fn screen_state(&self) -> Option<bool> {
        self.screen
    }

    pub fn take_died(&mut self) -> Option<Died> {
        self.died.pop_front()
    }

    pub fn poll(&mut self, timeout: Duration) -> bool {
        if self.stream.is_none() && !self.connect() {
            return false;
        }

        if self.push {
            self.poll_push(timeout)
        } else {
            self.poll_legacy(timeout)
        }
    }

    fn poll_push(&mut self, timeout: Duration) -> bool {
        let deadline = Instant::now() + timeout;
        let mut had_event = false;

        loop {
            let Some(reader) = self.reader.as_mut() else {
                self.disconnect();
                return had_event;
            };

            let wait = if had_event {
                Duration::from_millis(1)
            } else {
                let remaining = deadline.saturating_duration_since(Instant::now());
                if remaining.is_zero() {
                    return false;
                }
                remaining
            };
            let _ = reader.get_ref().set_read_timeout(Some(wait));

            let mut line = String::new();
            match reader.read_line(&mut line) {
                Ok(0) => {
                    self.disconnect();
                    return had_event;
                }
                Ok(_) => {
                    if self.handle_line(line.trim()) {
                        had_event = true;
                    }
                }
                Err(error)
                    if matches!(
                        error.kind(),
                        std::io::ErrorKind::WouldBlock | std::io::ErrorKind::TimedOut
                    ) =>
                {
                    return had_event;
                }
                Err(_) => {
                    self.disconnect();
                    return had_event;
                }
            }

            if Instant::now() >= deadline {
                return had_event;
            }
        }
    }

    fn poll_legacy(&mut self, timeout: Duration) -> bool {
        let millis = timeout.as_millis().clamp(1, 5000) as u64;
        let command = format!("WAIT {}", millis);
        let Some(response) = self.request(&command, timeout + Duration::from_millis(150)) else {
            return false;
        };

        if let Some(package) = response.strip_prefix("FOCUS ") {
            let package = package.trim();
            if !package.is_empty() && package != "-" {
                self.focus = Some(Focus {
                    package: package.to_string(),
                    pid: 0,
                    uid: 0,
                });
                return true;
            }
        }

        if response == "EVENT" {
            return true;
        }

        false
    }

    fn handle_line(&mut self, line: &str) -> bool {
        if line.is_empty() || line.len() > LINE_LIMIT {
            return false;
        }

        let mut fields = line.split(' ');
        match fields.next() {
            Some("APP_FOCUS") => {
                let (Some(package), Some(pid), Some(uid), Some(seq)) =
                    (fields.next(), fields.next(), fields.next(), fields.next())
                else {
                    return false;
                };
                let (Ok(pid), Ok(uid), Ok(seq)) =
                    (pid.parse::<i32>(), uid.parse::<i32>(), seq.parse::<u64>())
                else {
                    return false;
                };
                if !self.accept_seq(seq) || !valid_package(package) || pid <= 0 {
                    return false;
                }
                self.focus = Some(Focus {
                    package: package.to_string(),
                    pid,
                    uid,
                });
                true
            }
            Some("APP_DIED") => {
                let (Some(pid), Some(uid), Some(package), Some(seq)) =
                    (fields.next(), fields.next(), fields.next(), fields.next())
                else {
                    return false;
                };
                let (Ok(pid), Ok(uid), Ok(seq)) =
                    (pid.parse::<i32>(), uid.parse::<i32>(), seq.parse::<u64>())
                else {
                    return false;
                };
                if !self.accept_seq(seq) || pid <= 0 {
                    return false;
                }
                let package = if package == "-" || !valid_package(package) {
                    None
                } else {
                    Some(package.to_string())
                };
                if self
                    .focus
                    .as_ref()
                    .map(|focus| focus.pid == pid)
                    .unwrap_or(false)
                {
                    self.focus = None;
                }
                self.died.push_back(Died { pid, uid, package });
                true
            }
            Some("SCREEN") => {
                let (Some(state), Some(seq)) = (fields.next(), fields.next()) else {
                    return false;
                };
                let Ok(seq) = seq.parse::<u64>() else {
                    return false;
                };
                let on = match state {
                    "on" => true,
                    "off" => false,
                    _ => return false,
                };
                if !self.accept_seq(seq) {
                    return false;
                }
                let changed = self.screen != Some(on);
                self.screen = Some(on);
                changed
            }
            Some("PONG") | Some("EVENT") | Some("NONE") | Some("OK") => false,
            _ => false,
        }
    }

    fn accept_seq(&mut self, seq: u64) -> bool {
        if seq == 0 || seq <= self.last_seq {
            return false;
        }
        self.last_seq = seq;
        true
    }

    fn disconnect(&mut self) {
        self.stream = None;
        self.reader = None;
        self.focus = None;
        self.screen = None;
        self.next_retry = Instant::now() + CONNECT_RETRY;
    }

    fn request(&mut self, command: &str, timeout: Duration) -> Option<String> {
        if self.stream.is_none() && !self.connect() {
            return None;
        }

        let stream = self.stream.as_mut()?;
        if stream.set_read_timeout(Some(timeout)).is_err() {
            self.disconnect();
            return None;
        }
        let _ = stream.set_write_timeout(Some(WRITE_TIMEOUT));

        let message = format!("{}\n", command);
        if stream.write_all(message.as_bytes()).is_err() || stream.flush().is_err() {
            self.disconnect();
            return None;
        }

        let Some(reader) = self.reader.as_mut() else {
            self.disconnect();
            return None;
        };
        let mut response = String::new();

        match reader.read_line(&mut response) {
            Ok(0) | Err(_) => {
                self.disconnect();
                None
            }
            Ok(_) => Some(response.trim_end().to_string()),
        }
    }

    fn connect(&mut self) -> bool {
        if Instant::now() < self.next_retry {
            return false;
        }

        for path in binder_protocol::socket_candidates() {
            let Ok(mut stream) = UnixStream::connect(&path) else {
                continue;
            };
            let _ = stream.set_read_timeout(Some(HANDSHAKE_TIMEOUT));
            let _ = stream.set_write_timeout(Some(WRITE_TIMEOUT));
            let Ok(reader_stream) = stream.try_clone() else {
                continue;
            };
            let mut reader = BufReader::new(reader_stream);

            let mut first = String::new();
            match reader.read_line(&mut first) {
                Ok(0) => continue,
                Ok(_) if first.trim_start().starts_with("HELLO") => {
                    if stream.write_all(b"OK 1\n").is_err() || stream.flush().is_err() {
                        continue;
                    }
                    let version = first
                        .trim()
                        .split(' ')
                        .nth(1)
                        .and_then(|value| value.parse::<u32>().ok())
                        .unwrap_or(0);
                    if version != 1 {
                        crate::log!("[SYSTEM] Binder Unsupported helper protocol {}", version);
                        continue;
                    }
                    let _ = stream.set_read_timeout(Some(READ_TIMEOUT));
                    self.push = true;
                    self.last_seq = 0;
                    self.focus = None;
                    self.screen = None;
                    self.stream = Some(stream);
                    self.reader = Some(reader);
                    self.connected_once = true;
                    return true;
                }
                Ok(_) => {
                    let _ = stream.set_read_timeout(Some(Duration::from_millis(300)));
                    self.push = false;
                    self.stream = Some(stream);
                    self.reader = Some(reader);
                    self.connected_once = true;
                    return true;
                }
                Err(error)
                    if matches!(
                        error.kind(),
                        std::io::ErrorKind::WouldBlock | std::io::ErrorKind::TimedOut
                    ) =>
                {
                    let _ = stream.set_read_timeout(Some(Duration::from_millis(300)));
                    self.push = false;
                    self.stream = Some(stream);
                    self.reader = Some(reader);
                    self.connected_once = true;
                    return true;
                }
                Err(_) => continue,
            }
        }

        let delay = if self.connected_once {
            CONNECT_RETRY
        } else {
            INITIAL_RETRY
        };
        self.next_retry = Instant::now() + delay;
        false
    }
}

fn valid_package(package: &str) -> bool {
    !package.is_empty()
        && package.len() <= 255
        && package
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_'))
}
