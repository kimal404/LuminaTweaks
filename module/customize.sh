ui_print "===================================="
ui_print "     LUMINATWEAKS CORE INSTALL      "
ui_print "===================================="

ABI=$(getprop ro.product.cpu.abi)
case "$ABI" in
    arm64*|aarch64*)
        ui_print "[+] Architecture verified: $ABI"
        ;;
    *)
        abort "[-] Fatal: 32-bit architecture ($ABI) is unsupported."
        ;;
esac

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"
mkdir -p "$BASE_DIR/logs"

rm -rf "$MODPATH/webroot" 2>/dev/null
rm -rf "/data/adb/modules/luminatweaks/webroot" 2>/dev/null
rm -rf "$MODPATH/system" 2>/dev/null

ui_print "[*] Purging legacy daemon processes..."
killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null

DAEMON_SRC=""
if [ -f "$MODPATH/luminad" ]; then
    DAEMON_SRC="$MODPATH/luminad"
elif [ -f "$MODPATH/bin/luminad" ]; then
    DAEMON_SRC="$MODPATH/bin/luminad"
fi

if [ -n "$DAEMON_SRC" ]; then
    ui_print "[+] Deploying native luminad engine..."
    cp -f "$DAEMON_SRC" "$BASE_DIR/bin/luminad"
    set_perm "$BASE_DIR/bin/luminad" 0 0 0755
    set_perm "$DAEMON_SRC" 0 0 0755
else
    ui_print "[!] Warning: Native binary not found in package."
fi

if [ -f "$MODPATH/manager.apk" ]; then
    ui_print "[+] Installing Lumina Manager interface..."

    TMP_APK="/data/local/tmp/lumina_manager.apk"
    cp -f "$MODPATH/manager.apk" "$TMP_APK"
    chmod 644 "$TMP_APK"
    chcon u:object_r:shell_data_file:s0 "$TMP_APK" 2>/dev/null

    install_out=$(pm install -r -d --user 0 "$TMP_APK" 2>&1)

    if echo "$install_out" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
        ui_print "[!] Signature conflict detected. Overriding installation..."
        pm uninstall --user 0 com.luminatweaks >/dev/null 2>&1
        pm uninstall com.luminatweaks >/dev/null 2>&1
        install_out=$(pm install --user 0 "$TMP_APK" 2>&1)
    elif ! echo "$install_out" | grep -iq "Success"; then
        install_out=$(cmd package install -r -d --user 0 "$TMP_APK" 2>&1)
    fi

    rm -f "$TMP_APK"

    if echo "$install_out" | grep -iq "Success"; then
        ui_print "[+] Manager package deployed successfully."
    else
        ui_print "[!] Package manager warning: $install_out"
    fi

    pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
    pm grant com.luminatweaks android.permission.QUERY_ALL_PACKAGES >/dev/null 2>&1
    pm grant com.luminatweaks com.android.permission.GET_INSTALLED_APPS >/dev/null 2>&1

    appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
    appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
    appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
    dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1
fi

set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

[ -f "$BASE_DIR/fast_load" ] || echo "1" > "$BASE_DIR/fast_load"
[ -f "$BASE_DIR/aggressive_doze" ] || echo "0" > "$BASE_DIR/aggressive_doze"
[ -f "$BASE_DIR/gms_limit" ] || echo "0" > "$BASE_DIR/gms_limit"

if [ "$(getprop sys.boot_completed)" = "1" ] && [ -f "$BASE_DIR/bin/luminad" ]; then
    ui_print "[+] Spawning native daemon runtime..."
    killall -9 luminad 2>/dev/null
    pkill -9 -f luminad 2>/dev/null
    sleep 1
    nohup "$BASE_DIR/bin/luminad" >/dev/null 2>&1 &
fi

ui_print "[✓] System deployment finished without errors."
