#!/system/bin/sh
# Copyright (C) 2026 kimal

MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR="/data/adb/modules/luminatweaks"

# 1. Tunggu inisialisasi boot sistem selesai
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

# Beri jeda agar sistem stabil
sleep 3

# 2. Hak akses & whitelist Lumina Manager
pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
cmd appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
cmd appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
cmd appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1

# 3. Jalankan daemon langsung dari modul
killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null
sleep 1

DAEMON_BIN=""
if [ -f "$MODDIR/luminad" ]; then
    DAEMON_BIN="$MODDIR/luminad"
elif [ -f "/data/adb/modules/luminatweaks/luminad" ]; then
    DAEMON_BIN="/data/adb/modules/luminatweaks/luminad"
fi

if [ -n "$DAEMON_BIN" ]; then
    chmod 755 "$DAEMON_BIN"
    nohup "$DAEMON_BIN" >/dev/null 2>&1 &
fi

# 4. Sinkronisasi deskripsi realtime ke module.prop
LAST_PID=""
while true; do
    CURRENT_PID=$(pidof luminad | awk '{print $1}')
    if [ "$CURRENT_PID" != "$LAST_PID" ]; then
        if [ -n "$CURRENT_PID" ]; then
            sed -i "s|^description=.*|description=Daemon is running (PID ${CURRENT_PID}).|" "$MODDIR/module.prop"
        else
            sed -i "s|^description=.*|description=Daemon is stopped.|" "$MODDIR/module.prop"
        fi
        LAST_PID="$CURRENT_PID"
    fi
    sleep 5
done
