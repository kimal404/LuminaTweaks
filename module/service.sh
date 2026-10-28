#!/system/bin/sh
MODDIR=${0%/*}

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done
sleep 3

DAEMON_BIN="/data/adb/luminatweaks/luminad"
if [ -f "$DAEMON_BIN" ]; then
    chmod 755 "$DAEMON_BIN"
    "$DAEMON_BIN" </dev/null >/dev/null 2>&1 &
fi
