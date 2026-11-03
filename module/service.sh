#!/system/bin/sh
MODDIR=${0%/*}

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

while ! pm path android >/dev/null 2>&1; do
    sleep 1
done

while [ "$(pm is-user-unlocked 0 2>/dev/null)" != "true" ]; do
    sleep 2
done

sleep 2

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"
mkdir -p "$BASE_DIR/logs"

if [ -f "$MODDIR/luminad" ]; then
    cp -f "$MODDIR/luminad" "$BASE_DIR/bin/luminad"
elif [ -f "$MODDIR/bin/luminad" ]; then
    cp -f "$MODDIR/bin/luminad" "$BASE_DIR/bin/luminad"
fi
chmod 755 "$BASE_DIR/bin/luminad"

write_node() {
    local val="$1"
    local path="$2"
    if [ -f "$path" ]; then
        chmod 644 "$path" 2>/dev/null
        echo "$val" > "$path" 2>/dev/null
    fi
}

sysctl -w kernel.panic=0 >/dev/null 2>&1
sysctl -w kernel.panic_on_oops=0 >/dev/null 2>&1
sysctl -w kernel.softlockup_panic=0 >/dev/null 2>&1

for policy in /sys/class/thermal/thermal_zone*/policy; do
    if [ -f "$policy" ]; then
        avail=$(cat "${policy%/*}/available_policies" 2>/dev/null)
        if echo "$avail" | grep -q "step_wise"; then
            chmod 644 "$policy" 2>/dev/null
            echo "step_wise" > "$policy" 2>/dev/null
            chmod 444 "$policy" 2>/dev/null
        fi
    fi
done

fstrim -v /data >/dev/null 2>&1 &

for queue in /sys/block/*/queue; do
    if [ -d "$queue" ]; then
        write_node "0" "$queue/iostats"
        write_node "0" "$queue/add_random"
        write_node "128" "$queue/read_ahead_kb"
        write_node "2" "$queue/nomerges"
    fi
done

avail_tcp=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
if echo "$avail_tcp" | grep -q "bbr"; then
    sysctl -w net.ipv4.tcp_congestion_control=bbr >/dev/null 2>&1
elif echo "$avail_tcp" | grep -q "cubic"; then
    sysctl -w net.ipv4.tcp_congestion_control=cubic >/dev/null 2>&1
fi
sysctl -w net.ipv4.tcp_fastopen=3 >/dev/null 2>&1
sysctl -w net.ipv4.tcp_syncookies=1 >/dev/null 2>&1
sysctl -w net.ipv4.tcp_tw_reuse=1 >/dev/null 2>&1

GAME_LIBS="libunity.so,libreal_net.so,libUE4.so,libgodot_android.so,libmain.so,libil2cpp.so"
for sched_node in /proc/sys/kernel/sched_lib_name /proc/sys/walt/sched_lib_name; do
    write_node "$GAME_LIBS" "$sched_node"
done

sysctl -w vm.page-cluster=0 >/dev/null 2>&1
sysctl -w vm.compaction_proactiveness=0 >/dev/null 2>&1
sysctl -w vm.vfs_cache_pressure=100 >/dev/null 2>&1

pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
cmd appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
cmd appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
cmd appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1

killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null
sleep 1

( setsid "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 & ) 2>/dev/null || \
    nohup "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 &

am broadcast --user 0 \
    -n com.luminatweaks/com.luminatweaks.receiver.LuminaReceiver \
    -a com.luminatweaks.ACTION_NOTIFY \
    -f 0x11000020 \
    --es notifytitle "LuminaTweaks" \
    --es notifytext "Boot baseline applied & Engine active" \
    --ez is_profile false \
    --el timeout 8000 >/dev/null 2>&1
