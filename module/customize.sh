ui_print "************************************"
ui_print "       LuminaTweaks Installer       "
ui_print "************************************"

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"

if [ -f "$MODPATH/luminad" ]; then
    ui_print "- Installing native luminad daemon..."
    cp "$MODPATH/luminad" "$BASE_DIR/bin/luminad"
    set_perm "$BASE_DIR/bin/luminad" 0 0 0755
    set_perm "$MODPATH/luminad" 0 0 0755
fi

if [ -f "$MODPATH/manager.apk" ]; then
    ui_print "- Installing Lumina Manager App..."
    pm install -r "$MODPATH/manager.apk" >/dev/null 2>&1
fi

set_perm "$MODPATH/service.sh" 0 0 0755

# Jika instalasi dilakukan saat sistem sudah booting, aktifkan daemon sekarang juga
if [ "$(getprop sys.boot_completed)" = "1" ]; then
    ui_print "- Starting native daemon immediately..."
    killall -9 luminad 2>/dev/null
    nohup "$BASE_DIR/bin/luminad" </dev/null >/dev/null 2>&1 &
fi

ui_print "- Installation finished successfully!"
