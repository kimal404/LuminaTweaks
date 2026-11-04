
MODDIR=${0%/*}

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

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"
mkdir -p "$BASE_DIR/logs"

if [ -f "$MODDIR/system/bin/luminad" ]; then
    cp -f "$MODDIR/system/bin/luminad" "$BASE_DIR/bin/luminad"
elif [ -f "$MODDIR/bin/luminad" ]; then
    cp -f "$MODDIR/bin/luminad" "$BASE_DIR/bin/luminad"
elif [ -f "$MODDIR/luminad" ]; then
    cp -f "$MODDIR/luminad" "$BASE_DIR/bin/luminad"
fi
chmod 755 "$BASE_DIR/bin/luminad"

pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
cmd appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
cmd appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
cmd appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1

killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null
sleep 1

if [ -f "$BASE_DIR/bin/luminad" ]; then
    nohup "$BASE_DIR/bin/luminad" >/dev/null 2>&1 &
fi
