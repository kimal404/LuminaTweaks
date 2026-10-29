#!/system/bin/sh
MODDIR=${0%/*}

# 1. Tunggu sinyal sistem boot completed
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

# 2. Tunggu sampai Package Manager siap melayani command
while ! pm path android >/dev/null 2>&1; do
    sleep 1
done

sleep 1

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"

# Sinkronisasi biner daemon dari folder modul
if [ -f "$MODDIR/luminad" ]; then
    cp -f "$MODDIR/luminad" "$BASE_DIR/bin/luminad"
elif [ -f "$MODDIR/bin/luminad" ]; then
    cp -f "$MODDIR/bin/luminad" "$BASE_DIR/bin/luminad"
fi

chmod 755 "$BASE_DIR/bin/luminad"

# Broadcast notifikasi modul berhasil dimuat
am broadcast -a com.luminatweaks.ACTION_NOTIFY \
    -p com.luminatweaks \
    --receiver-foreground \
    -f 0x01000020 \
    --es notifytitle "LuminaTweaks" \
    --es notifytext "Module loaded successfully" \
    --ez is_profile false \
    --el timeout 8000 >/dev/null 2>&1

# Eksekusi biner daemon di background secara bersih
killall -9 luminad 2>/dev/null
nohup "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 &
