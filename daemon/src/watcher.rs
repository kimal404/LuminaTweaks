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

use std::ffi::CString;
use std::os::raw::{c_char, c_int, c_void};
use std::path::Path;

use crate::detector::CONFIG_DIR;

const IN_NONBLOCK: c_int = 0o4000;
const IN_CLOEXEC: c_int = 0o2000000;

const IN_CLOSE_WRITE: u32 = 0x0000_0008;
const IN_MOVED_TO: u32 = 0x0000_0080;
const IN_CREATE: u32 = 0x0000_0100;
const IN_DELETE: u32 = 0x0000_0200;
const IN_DELETE_SELF: u32 = 0x0000_0400;
const IN_IGNORED: u32 = 0x0000_8000;
const IN_Q_OVERFLOW: u32 = 0x0000_4000;

const WATCH_MASK: u32 = IN_CLOSE_WRITE | IN_MOVED_TO | IN_CREATE | IN_DELETE;

const EVENT_HEADER_SIZE: usize = 16;

extern "C" {
    fn inotify_init1(flags: c_int) -> c_int;
    fn inotify_add_watch(fd: c_int, pathname: *const c_char, mask: u32) -> c_int;
    #[link_name = "read"]
    fn c_read(fd: c_int, buf: *mut c_void, count: usize) -> isize;
    #[link_name = "close"]
    fn c_close(fd: c_int) -> c_int;
}

pub struct ConfigWatcher {
    fd: c_int,
    watching: bool,
}

impl ConfigWatcher {
    pub fn new() -> Self {
        let fd = unsafe { inotify_init1(IN_NONBLOCK | IN_CLOEXEC) };
        let mut watcher = Self {
            fd,
            watching: false,
        };
        watcher.ensure_watch();
        watcher
    }

    fn ensure_watch(&mut self) {
        if self.fd < 0 || self.watching {
            return;
        }
        if !Path::new(CONFIG_DIR).exists() {
            return;
        }
        let cpath = match CString::new(CONFIG_DIR) {
            Ok(path) => path,
            Err(_) => return,
        };
        let wd = unsafe { inotify_add_watch(self.fd, cpath.as_ptr(), WATCH_MASK) };
        if wd >= 0 {
            self.watching = true;
        }
    }

    pub fn poll_changed(&mut self) -> bool {
        if self.fd < 0 {
            return false;
        }
        if !self.watching {
            self.ensure_watch();
            return false;
        }

        let mut buf = [0u8; 4096];
        let mut changed = false;
        let mut watch_lost = false;

        loop {
            let n = unsafe { c_read(self.fd, buf.as_mut_ptr() as *mut c_void, buf.len()) };
            if n <= 0 {
                break;
            }

            let total = n as usize;
            let mut offset = 0usize;

            while offset + EVENT_HEADER_SIZE <= total {
                let mask = match buf
                    .get(offset + 4..offset + 8)
                    .and_then(|bytes| <[u8; 4]>::try_from(bytes).ok())
                {
                    Some(bytes) => u32::from_ne_bytes(bytes),
                    None => break,
                };

                let len = match buf
                    .get(offset + 12..offset + 16)
                    .and_then(|bytes| <[u8; 4]>::try_from(bytes).ok())
                {
                    Some(bytes) => u32::from_ne_bytes(bytes) as usize,
                    None => break,
                };

                if mask & (IN_IGNORED | IN_DELETE_SELF) != 0 {
                    watch_lost = true;
                }
                if mask & (WATCH_MASK | IN_Q_OVERFLOW) != 0 {
                    changed = true;
                }

                offset += EVENT_HEADER_SIZE + len;
            }
        }

        if watch_lost {
            self.watching = false;
        }

        changed
    }
}

impl Drop for ConfigWatcher {
    fn drop(&mut self) {
        if self.fd >= 0 {
            unsafe {
                c_close(self.fd);
            }
        }
    }
}
