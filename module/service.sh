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

# Broadcast notification when module starts up after flash/boot
am broadcast -a com.luminatweaks.ACTION_NOTIFY \
    -p com.luminatweaks \
    --receiver-foreground \
    -f 0x01000020 \
    --es notifytitle "LuminaTweaks Installed" \
    --es notifytext "Module installed successfully. Daemon will start shortly." \
    --ez is_profile false >/dev/null 2>&1

killall -9 luminad 2>/dev/null
nohup "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 &
