#!/system/bin/sh
# Copyright (C) 2026 kimal

MODDIR=${0%/*}

# 1. Tunggu inisialisasi boot sistem selesai[span_0](start_span)[span_0](end_span)
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

while ! pm path android >/dev/null 2>&1; do
    sleep 1
done

while [ "$(pm is-user-unlocked 0 2>/dev/null)" != "true" ]; do
    sleep 2
done

sleep 2

# 2. Hak akses & whitelist Lumina Manager[span_1](start_span)[span_1](end_span)
pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
cmd appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
cmd appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
cmd appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1

# 3. Jalankan daemon langsung dari direktori modul[span_2](start_span)[span_2](end_span)
killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null
sleep 1

if [ -f "$MODDIR/luminad" ]; then
    chmod 755 "$MODDIR/luminad"
    nohup "$MODDIR/luminad" >/dev/null 2>&1 &
fi

# 4. Sinkronisasi deskripsi realtime (Opsi A)
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
    sleep 6
done
