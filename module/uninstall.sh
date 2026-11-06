#!/system/bin/sh
# Copyright (C) 2026 kimal

# 1. Hentikan proses daemon
pkill -9 luminad 2>/dev/null
killall -9 luminad 2>/dev/null

# 2. Hapus file runtime di RAM
rm -f /dev/socket/lumina.sock
rm -f /data/local/tmp/lumina.sock
rm -f /data/local/tmp/lumina.log*

# 3. Bersihkan sampah sisa versi lama jika pernah ada
rm -rf /data/adb/luminatweaks
rm -f /data/adb/service.d/luminad_boot.sh

exit 0
