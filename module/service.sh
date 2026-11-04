#!/system/bin/sh
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

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

while ! pm path android >/dev/null 2>&1; do
    sleep 1
done

while [ "$(pm is-user-unlocked 0 2>/dev/null)" != "true" ]; do
    sleep 2
done

sleep 2

BASE_DIR="/data/adb/luminatweaks"
mkdir -p "$BASE_DIR/bin"
mkdir -p "$BASE_DIR/configs"
mkdir -p "$BASE_DIR/logs"

if [ -f "$MODDIR/system/bin/luminad" ]; then
    cp -f "$MODDIR/system/bin/luminad" "$BASE_DIR/bin/luminad"
elif [ -f "$MODDIR/bin/luminad" ]; then
    cp -f "$MODDIR/bin/luminad" "$BASE_DIR/bin/luminad"
elif [ -f "$MODDIR/luminad" ]; then
    cp -f "$MODDIR/luminad" "$BASE_DIR/bin/luminad"
fi
chmod 755 "$BASE_DIR/bin/luminad"

pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
cmd appops set com.luminatweaks POST_NOTIFICATION allow >/dev/null 2>&1
cmd appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
cmd appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1

killall -9 luminad 2>/dev/null
pkill -9 -f luminad 2>/dev/null
sleep 1

if [ -f "$BASE_DIR/bin/luminad" ]; then
    nohup "$BASE_DIR/bin/luminad" >/dev/null 2>&1 &
fi
