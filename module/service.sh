#!/system/bin/sh
MODDIR=${0%/*}

# 1. Tunggu sinyal sistem boot completed
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 1
done

# 2. Tunggu Package Manager siap
while ! pm path android >/dev/null 2>&1; do
    sleep 1
done

# 3. Tunggu sampai user membuka kunci layar pertama kali (CE Storage Decrypted)
while [ "$(pm is-user-unlocked 0 2>/dev/null)" != "true" ]; do
    sleep 2
done

sleep 2

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"

if [ -f "$MODDIR/luminad" ]; then
    cp -f "$MODDIR/luminad" "$BASE_DIR/bin/luminad"
elif [ -f "$MODDIR/bin/luminad" ]; then
    cp -f "$MODDIR/bin/luminad" "$BASE_DIR/bin/luminad"
fi

chmod 755 "$BASE_DIR/bin/luminad"

# Pastikan izin notifikasi tetap aktif pasca-boot
pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
cmd appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1

# Broadcast notifikasi modul berhasil dimuat ke User 0
am broadcast --user 0 -a com.luminatweaks.ACTION_NOTIFY \
    -p com.luminatweaks \
    --receiver-foreground \
    --include-stopped-packages \
    -f 0x01000020 \
    --es notifytitle "LuminaTweaks" \
    --es notifytext "Module loaded successfully" \
    --ez is_profile false \
    --el timeout 8000 >/dev/null 2>&1

killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null
sleep 1
( setsid "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 & ) 2>/dev/null || \
    nohup "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 &
