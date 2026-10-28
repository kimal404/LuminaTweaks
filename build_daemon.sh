#!/bin/bash
set -e

TARGET="aarch64-linux-android"
DEST_DIR="manager/app/src/main/assets/bin"

mkdir -p "$DEST_DIR"

echo "[*] Building daemon release binary..."
cd daemon
cargo build --target "$TARGET" --release

echo "[*] Copying binary to manager assets..."
cd ..
cp "daemon/target/$TARGET/release/luminad" "$DEST_DIR/luminad"
chmod +x "$DEST_DIR/luminad"

echo "[✓] Selesai: $DEST_DIR/luminad"
