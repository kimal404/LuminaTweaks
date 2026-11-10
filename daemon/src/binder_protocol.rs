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

pub const PRIMARY_SOCKET: &str = "/dev/socket/lumina_binder.sock";
pub const FALLBACK_SOCKET: &str = "/data/local/tmp/lumina_binder.sock";
pub const SOCKET_ENV: &str = "LUMINA_BINDER_SOCKET";

pub fn socket_candidates() -> Vec<String> {
    if let Ok(path) = std::env::var(SOCKET_ENV) {
        if !path.is_empty() {
            return vec![path];
        }
    }

    vec![PRIMARY_SOCKET.to_string(), FALLBACK_SOCKET.to_string()]
}
