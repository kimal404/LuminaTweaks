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

MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR="/data/adb/modules/luminatweaks"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

sleep 3

pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
cmd appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
cmd appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
cmd appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1

killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null
sleep 1

DAEMON_BIN=""
if [ -f "$MODDIR/luminad" ]; then
    DAEMON_BIN="$MODDIR/luminad"
elif [ -f "/data/adb/modules/luminatweaks/luminad" ]; then
    DAEMON_BIN="/data/adb/modules/luminatweaks/luminad"
fi

if [ -n "$DAEMON_BIN" ]; then
    chmod 755 "$DAEMON_BIN"
    nohup "$DAEMON_BIN" >/dev/null 2>&1 &
fi

LAST_PID=""
while true; do
    CURRENT_PID=$(pidof luminad | awk '{print $1}')
    if [ "$CURRENT_PID" != "$LAST_PID" ]; then
        if [ -n "$CURRENT_PID" ]; then
            sed -i "s|^description=.*|description=Daemon is running (PID ${CURRENT_PID}).|" "$MODDIR/module.prop"
        else
            sed -i "s|^description=.*|description=Daemon is stopped.|" "$MODDIR/module.prop"
        fi
        LAST_PID="$CURRENT_PID"
    fi
    sleep 5
done
