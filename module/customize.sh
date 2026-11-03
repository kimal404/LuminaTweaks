ui_print "************************************"
ui_print "       LuminaTweaks Installer       "
ui_print "************************************"

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"
mkdir -p "$BASE_DIR/logs"

ui_print "- Cleaning conflicting legacy daemons..."
killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null
rm -f "$BASE_DIR/luminad"

if [ -f "$MODPATH/luminad" ]; then
    ui_print "- Installing native luminad engine..."
    cp -f "$MODPATH/luminad" "$BASE_DIR/bin/luminad"
    set_perm "$BASE_DIR/bin/luminad" 0 0 0755
    set_perm "$MODPATH/luminad" 0 0 0755
fi

if [ -f "$MODPATH/manager.apk" ]; then
    ui_print "- Installing Lumina Manager APK..."
    
    TMP_APK="/data/local/tmp/lumina_manager.apk"
    cp -f "$MODPATH/manager.apk" "$TMP_APK"
    chmod 644 "$TMP_APK"
    chcon u:object_r:shell_data_file:s0 "$TMP_APK" 2>/dev/null

    install_out=$(pm install -r -d --user 0 "$TMP_APK" 2>&1)

    if echo "$install_out" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
        ui_print "! Signature berbeda terdeteksi. Membersihkan instalasi lama..."
        pm uninstall --user 0 com.luminatweaks >/dev/null 2>&1
        pm uninstall com.luminatweaks >/dev/null 2>&1
        install_out=$(pm install --user 0 "$TMP_APK" 2>&1)
    elif ! echo "$install_out" | grep -iq "Success"; then
        install_out=$(cmd package install -r -d --user 0 "$TMP_APK" 2>&1)
    fi

    rm -f "$TMP_APK"

    if echo "$install_out" | grep -iq "Success"; then
        ui_print "- Manager APK successfully updated."
    else
        ui_print "! Gagal memasang Manager APK: $install_out"
    fi

    # Izin esensial & penghindaran pembatasan latar belakang
    pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
    pm grant com.luminatweaks android.permission.QUERY_ALL_PACKAGES >/dev/null 2>&1
    pm grant com.luminatweaks com.android.permission.GET_INSTALLED_APPS >/dev/null 2>&1

    appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
    appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
    appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
    appops set com.luminatweaks android:query_all_packages allow >/dev/null 2>&1
    appops set com.luminatweaks query_all_packages allow >/dev/null 2>&1
    appops set com.luminatweaks android:get_installed_apps allow >/dev/null 2>&1
    appops set com.luminatweaks GET_INSTALLED_APPS allow >/dev/null 2>&1
    appops set com.luminatweaks 10022 allow >/dev/null 2>&1

    # Cegah Doze / pembatasan baterai HyperOS
    dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1
fi

set_perm "$MODPATH/service.sh" 0 0 0755

if [ "$(getprop sys.boot_completed)" = "1" ]; then
    ui_print "- Starting native daemon immediately..."
    killall -9 luminad 2>/dev/null
    pkill -9 -f luminad 2>/dev/null
    sleep 1

    su 0 -c "setsid $BASE_DIR/bin/luminad </dev/null >/dev/null 2>&1 &" 2>/dev/null || \
        ( setsid "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 & ) 2>/dev/null || \
        ( nohup "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 & )
fi

ui_print "- Installation finished successfully!"
