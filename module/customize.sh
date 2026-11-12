#!/system/bin/sh
#
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

[ "$ARCH" = "arm64" ] || abort "! Hanya mendukung arm64"

ui_print "- Memasang LuminaTweaks"

rm -rf /data/adb/luminatweaks 2>/dev/null
rm -rf "$MODPATH/webroot" 2>/dev/null
rm -rf "$MODPATH/system" 2>/dev/null

mkdir -p /data/adb/.config/lumina
rm -f "$MODPATH/.bootcount" "$MODPATH/.bootloop_guard" "$MODPATH/disable"

SOC="generic"
if [ -f "$MODPATH/soc.sh" ]; then
    . "$MODPATH/soc.sh"
    SOC=$(detect_lumina_soc 2>/dev/null)
fi
case "$SOC" in
    mediatek|snapdragon|unisoc|generic) ;;
    *) SOC="generic" ;;
esac
printf '%s\n' "$SOC" > /data/adb/.config/lumina/soc.conf
chmod 644 /data/adb/.config/lumina/soc.conf
setprop persist.lumina.soc "$SOC" 2>/dev/null
case "$SOC" in
    mediatek) SOC_LABEL="MediaTek" ;;
    snapdragon) SOC_LABEL="Snapdragon" ;;
    unisoc) SOC_LABEL="Unisoc" ;;
    *) SOC_LABEL="Generic" ;;
esac
ui_print "- SoC terdeteksi: $SOC_LABEL"
ui_print "- Tweak SoC: ${SOC}.rs"

BASE_GAMELIST="$MODPATH/gamelist.txt"
GEN_GAMELIST="$MODPATH/gamelist.json"

if [ -f "$BASE_GAMELIST" ]; then
    ui_print "- Membuat gamelist.json"
    {
        echo "{"
        first=1
        while IFS= read -r pkg || [ -n "$pkg" ]; do
            pkg=$(printf '%s' "$pkg" | tr -d '[:space:]')
            [ -z "$pkg" ] && continue
            case "$pkg" in
                \#*) continue ;;
            esac
            pkg=${pkg%%:*}
            [ -z "$pkg" ] && continue
            if [ "$first" -eq 1 ]; then
                first=0
            else
                printf ',\n'
            fi
            printf '  "%s": {"lite_mode": "default", "dnd": "default"}' "$pkg"
        done < "$BASE_GAMELIST"
        printf '\n}\n'
    } > "$GEN_GAMELIST"
fi

killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null
killall -9 lumina-binder 2>/dev/null
pkill -9 -f lumina-binder 2>/dev/null

if [ -f "$MODPATH/luminad" ]; then
    set_perm "$MODPATH/luminad" 0 0 0755
else
    ui_print "! Biner luminad tidak ditemukan"
fi

if [ -f "$MODPATH/lumina-binder" ]; then
    set_perm "$MODPATH/lumina-binder" 0 0 0755
else
    ui_print "! Biner lumina-binder tidak ditemukan"
fi

if [ -f "$MODPATH/binder_resolver.apk" ]; then
    set_perm "$MODPATH/binder_resolver.apk" 0 0 0644
fi

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
set_perm "$MODPATH/soc.sh" 0 0 0755

ui_print "- Selesai"
