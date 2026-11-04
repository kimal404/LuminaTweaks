#!/bin/bash
# Copyright (C) 2026 kimal
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

TARGET="aarch64-linux-android"
DEST_DIR="manager/app/src/main/assets/bin"
TARGET_DIR="${CARGO_TARGET_DIR:-$HOME/.cargo_target/luminad}"

mkdir -p "$DEST_DIR"
mkdir -p "$TARGET_DIR"

echo "[*] Building daemon release binary..."
cd daemon
CARGO_TARGET_DIR="$TARGET_DIR" cargo build --target "$TARGET" --release

echo "[*] Copying binary to manager assets..."
cd ..
cp "$TARGET_DIR/$TARGET/release/luminad" "$DEST_DIR/luminad"
chmod +x "$DEST_DIR/luminad"

echo "[✓] Selesai: $DEST_DIR/luminad"
