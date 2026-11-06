#!/system/bin/sh

[ "$ARCH" = "arm64" ] || abort "! Hanya mendukung arm64"

ui_print "- Memasang LuminaTweaks"

# Bersihkan sisa legacy
rm -rf /data/adb/luminatweaks 2>/dev/null
rm -rf "$MODPATH/webroot" 2>/dev/null
rm -rf "$MODPATH/system" 2>/dev/null

# Siapkan direktori konfigurasi resmi
mkdir -p /data/adb/.config/lumina

# Hentikan proses aktif
killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null

# Izin biner native
if [ -f "$MODPATH/luminad" ]; then
    set_perm "$MODPATH/luminad" 0 0 0755
else
    ui_print "! Biner luminad tidak ditemukan"
fi

# Pemasangan Manager
if [ -f "$MODPATH/manager.apk" ]; then
    ui_print "- Memasang Manager"

    TMP_APK="/data/local/tmp/lumina_manager.apk"
    cp -f "$MODPATH/manager.apk" "$TMP_APK"
    chmod 644 "$TMP_APK"
    chcon u:object_r:shell_data_file:s0 "$TMP_APK" 2>/dev/null

    install_out=$(pm install -r -d --user 0 "$TMP_APK" 2>&1)
    if echo "$install_out" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
        pm uninstall --user 0 com.luminatweaks >/dev/null 2>&1
        pm uninstall com.luminatweaks >/dev/null 2>&1
        pm install --user 0 "$TMP_APK" >/dev/null 2>&1
    elif ! echo "$install_out" | grep -iq "Success"; then
        cmd package install -r -d --user 0 "$TMP_APK" >/dev/null 2>&1
    fi

    rm -f "$TMP_APK"

    # Izin sistem & background execution
    pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
    pm grant com.luminatweaks android.permission.QUERY_ALL_PACKAGES >/dev/null 2>&1
    pm grant com.luminatweaks com.android.permission.GET_INSTALLED_APPS >/dev/null 2>&1

    appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
    appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
    appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
    dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1
fi

# Izin skrip modul
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

# Hot-start jika di-flash saat Android menyala
if [ "$(getprop sys.boot_completed)" = "1" ] && [ -f "$MODPATH/luminad" ]; then
    nohup "$MODPATH/luminad" >/dev/null 2>&1 &
fi

ui_print "- Selesai"
