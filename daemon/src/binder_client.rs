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

use std::io::{BufRead, BufReader, Write};
use std::os::unix::net::UnixStream;
use std::time::{Duration, Instant};

use crate::binder_protocol;

const CONNECT_RETRY: Duration = Duration::from_secs(5);
const INITIAL_RETRY: Duration = Duration::from_secs(2);

pub struct BinderClient {
    stream: Option<UnixStream>,
    reader: Option<BufReader<UnixStream>>,
    next_retry: Instant,
    connected_once: bool,
}

impl BinderClient {
    pub fn new() -> Self {
        Self {
            stream: None,
            reader: None,
            next_retry: Instant::now(),
            connected_once: false,
        }
    }

    pub fn ping(&mut self) -> bool {
        matches!(
            self.request("PING", Duration::from_millis(300)).as_deref(),
            Some("PONG")
        )
    }

    pub fn focused_package(&mut self) -> Option<String> {
        let response = self.request("FOCUS", Duration::from_millis(300))?;
        response
            .strip_prefix("FOCUS ")
            .map(|package| package.trim().to_string())
            .filter(|package| !package.is_empty())
    }

    pub fn wait_for_event(&mut self, timeout: Duration) -> bool {
        let millis = timeout.as_millis().min(5000) as u64;
        let command = format!("WAIT {}", millis);
        self.request(&command, timeout + Duration::from_millis(150))
            .is_some()
    }

    fn request(&mut self, command: &str, timeout: Duration) -> Option<String> {
        if self.stream.is_none() && !self.connect() {
            return None;
        }

        let stream = self.stream.as_mut()?;
        if stream.set_read_timeout(Some(timeout)).is_err() {
            self.stream = None;
            self.reader = None;
            return None;
        }
        let _ = stream.set_write_timeout(Some(Duration::from_millis(300)));

        let message = format!("{}\n", command);
        if stream.write_all(message.as_bytes()).is_err() || stream.flush().is_err() {
            self.stream = None;
            self.reader = None;
            return None;
        }

        let Some(reader) = self.reader.as_mut() else {
            self.stream = None;
            return None;
        };
        let mut response = String::new();

        match reader.read_line(&mut response) {
            Ok(0) | Err(_) => {
                self.stream = None;
                self.reader = None;
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
            if let Ok(stream) = UnixStream::connect(&path) {
                let _ = stream.set_read_timeout(Some(Duration::from_millis(300)));
                let _ = stream.set_write_timeout(Some(Duration::from_millis(300)));
                let Ok(reader_stream) = stream.try_clone() else {
                    continue;
                };
                self.stream = Some(stream);
                self.reader = Some(BufReader::new(reader_stream));
                self.connected_once = true;
                return true;
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
