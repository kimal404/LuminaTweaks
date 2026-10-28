#!/bin/bash
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
