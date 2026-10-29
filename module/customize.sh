ui_print "************************************"
ui_print "       LuminaTweaks Installer       "
ui_print "************************************"

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"

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
    ui_print "- Installing Lumina Manager APK (please wait)..."
    pm install -r "$MODPATH/manager.apk" >/dev/null 2>&1
    ui_print "- Manager APK successfully installed."
    
    # Auto-grant izin notifikasi & autostart HyperOS agar broadcast langsung tembus
    pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
    cmd appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
    cmd appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
    cmd appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
fi

set_perm "$MODPATH/service.sh" 0 0 0755

if [ "$(getprop sys.boot_completed)" = "1" ]; then
    ui_print "- Starting native daemon immediately..."
    killall -9 luminad 2>/dev/null
    pkill -9 -f luminad 2>/dev/null
    sleep 1

    # Jalankan daemon terlepas total dari session installer Magisk (PID 1 / setsid)
    su 0 -c "setsid $BASE_DIR/bin/luminad </dev/null >/dev/null 2>&1 &" 2>/dev/null || \
        ( setsid "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 & ) 2>/dev/null || \
        ( nohup "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 & )
fi

ui_print "- Installation finished successfully!"
