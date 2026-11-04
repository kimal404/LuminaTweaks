ui_print "************************************"
ui_print "       LuminaTweaks Installer       "
ui_print "************************************"

ABI=$(getprop ro.product.cpu.abi)
case "$ABI" in
    arm64*|aarch64*)
        ui_print "- Arsitektur didukung: $ABI"
        ;;
    *)
        abort "! Perangkat 32-bit ($ABI) tidak didukung."
        ;;
esac

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"
mkdir -p "$BASE_DIR/logs"

rm -rf "$MODPATH/webroot" 2>/dev/null
rm -rf "/data/adb/modules/luminatweaks/webroot" 2>/dev/null
rm -rf "$MODPATH/system" 2>/dev/null

ui_print "- Membersihkan daemon lama..."
killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null

DAEMON_SRC=""
if [ -f "$MODPATH/luminad" ]; then
    DAEMON_SRC="$MODPATH/luminad"
elif [ -f "$MODPATH/bin/luminad" ]; then
    DAEMON_SRC="$MODPATH/bin/luminad"
fi

if [ -n "$DAEMON_SRC" ]; then
    ui_print "- Memasang native luminad engine..."
    cp -f "$DAEMON_SRC" "$BASE_DIR/bin/luminad"
    set_perm "$BASE_DIR/bin/luminad" 0 0 0755
    set_perm "$DAEMON_SRC" 0 0 0755
fi

if [ -f "$MODPATH/manager.apk" ]; then
    ui_print "- Memasang Lumina Manager APK di latar belakang..."
    cp -f "$MODPATH/manager.apk" /data/local/tmp/lumina_manager.apk
    chmod 644 /data/local/tmp/lumina_manager.apk
    nohup sh -c '
        sleep 1
        pm install -r -d --user 0 /data/local/tmp/lumina_manager.apk >/dev/null 2>&1 || pm install -r -d /data/local/tmp/lumina_manager.apk >/dev/null 2>&1
        rm -f /data/local/tmp/lumina_manager.apk
        pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
        pm grant com.luminatweaks android.permission.QUERY_ALL_PACKAGES >/dev/null 2>&1
    ' >/dev/null 2>&1 &
fi

set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

[ -f "$BASE_DIR/fast_load" ] || echo "1" > "$BASE_DIR/fast_load"
[ -f "$BASE_DIR/aggressive_doze" ] || echo "0" > "$BASE_DIR/aggressive_doze"
[ -f "$BASE_DIR/gms_limit" ] || echo "0" > "$BASE_DIR/gms_limit"

if [ "$(getprop sys.boot_completed)" = "1" ] && [ -f "$BASE_DIR/bin/luminad" ]; then
    ui_print "- Menjalankan native daemon langsung..."
    killall -9 luminad 2>/dev/null
    pkill -9 -f luminad 2>/dev/null
    sleep 1
    nohup "$BASE_DIR/bin/luminad" >/dev/null 2>&1 &
fi

ui_print "- Instalasi selesai dengan sukses!"
