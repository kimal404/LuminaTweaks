#!/system/bin/sh
MODDIR=${0%/*}

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

sleep 3

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"

if [ -f "$MODDIR/luminad" ]; then
    cp "$MODDIR/luminad" "$BASE_DIR/bin/luminad"
elif [ -f "$MODDIR/bin/luminad" ]; then
    cp "$MODDIR/bin/luminad" "$BASE_DIR/bin/luminad"
fi

chmod 755 "$BASE_DIR/bin/luminad"

killall -9 luminad 2>/dev/null
nohup "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 &
