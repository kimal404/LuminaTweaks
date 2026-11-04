# Copyright (C) 2026 kimal
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

ui_print "************************************"
ui_print "       LuminaTweaks Installer       "
ui_print "************************************"

ABI=$(getprop ro.product.cpu.abi)
case "$ABI" in
    arm64*|aarch64*)
        ui_print "- Arsitektur didukung: $ABI"
        ;;
    *)
        abort "! Perangkat 32-bit ($ABI) tidak didukung oleh luminad engine."
        ;;
esac

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"
mkdir -p "$BASE_DIR/logs"

# Bersihkan sisa webroot agar KernelSU tidak menampilkan tombol WebUI
rm -rf "$MODPATH/webroot" 2>/dev/null
rm -rf "/data/adb/modules/luminatweaks/webroot" 2>/dev/null

ui_print "- Membersihkan daemon lama..."
killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null

DAEMON_SRC=""
if [ -f "$MODPATH/system/bin/luminad" ]; then
    DAEMON_SRC="$MODPATH/system/bin/luminad"
elif [ -f "$MODPATH/bin/luminad" ]; then
    DAEMON_SRC="$MODPATH/bin/luminad"
elif [ -f "$MODPATH/luminad" ]; then
    DAEMON_SRC="$MODPATH/luminad"
fi

if [ -n "$DAEMON_SRC" ]; then
    ui_print "- Memasang native luminad engine..."
    cp -f "$DAEMON_SRC" "$BASE_DIR/bin/luminad"
    set_perm "$BASE_DIR/bin/luminad" 0 0 0755
    set_perm "$DAEMON_SRC" 0 0 0755
else
    ui_print "! Peringatan: Binary luminad tidak ditemukan di dalam paket modul."
fi

if [ -f "$MODPATH/manager.apk" ]; then
    ui_print "- Memasang Lumina Manager APK..."

    TMP_APK="/data/local/tmp/lumina_manager.apk"
    cp -f "$MODPATH/manager.apk" "$TMP_APK"
    chmod 644 "$TMP_APK"
    chcon u:object_r:shell_data_file:s0 "$TMP_APK" 2>/dev/null

    install_out=$(pm install -r -d --user 0 "$TMP_APK" 2>&1)

    if echo "$install_out" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
        ui_print "! Signature berbeda. Membersihkan instalasi lama..."
        pm uninstall --user 0 com.luminatweaks >/dev/null 2>&1
        pm uninstall com.luminatweaks >/dev/null 2>&1
        install_out=$(pm install --user 0 "$TMP_APK" 2>&1)
    elif ! echo "$install_out" | grep -iq "Success"; then
        install_out=$(cmd package install -r -d --user 0 "$TMP_APK" 2>&1)
    fi

    rm -f "$TMP_APK"

    if echo "$install_out" | grep -iq "Success"; then
        ui_print "- Manager APK berhasil dipasang/diperbarui."
    else
        ui_print "! Peringatan pemasangan APK: $install_out"
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
    ui_print "- Menjalankan native daemon langsung..."
    killall -9 luminad 2>/dev/null
    pkill -9 -f luminad 2>/dev/null
    sleep 1
    nohup "$BASE_DIR/bin/luminad" >/dev/null 2>&1 &
fi

ui_print "- Instalasi selesai dengan sukses!"
