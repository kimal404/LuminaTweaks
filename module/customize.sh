SKIPUNZIP=1

ui_print "- Mengekstrak berkas LuminaTweaks..."
unzip -o "$ZIPFILE" 'module.prop' -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'service.sh' -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'common/*' -d "$MODPATH" >&2

# Siapkan direktori kerja daemon di /data/adb/
mkdir -p /data/adb/luminatweaks
cp -f "$MODPATH/common/luminad" /data/adb/luminatweaks/luminad
chmod 755 /data/adb/luminatweaks/luminad
chmod 755 "$MODPATH/service.sh"

# Pasang Manager APK
if [ -f "$MODPATH/common/manager.apk" ]; then
    ui_print "- Memasang LuminaTweaks Manager..."
    pm install -r "$MODPATH/common/manager.apk" >/dev/null 2>&1
fi

ui_print "- Instalasi selesai! Silakan reboot perangkat."
