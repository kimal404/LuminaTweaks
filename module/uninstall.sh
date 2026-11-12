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

WATCHDOG_PID_FILE="/data/local/tmp/lumina_watchdog.pid"
if [ -f "$WATCHDOG_PID_FILE" ]; then
    WD_PID=$(cat "$WATCHDOG_PID_FILE" 2>/dev/null)
    [ -n "$WD_PID" ] && kill -9 "$WD_PID" 2>/dev/null || true
    rm -f "$WATCHDOG_PID_FILE"
fi

killall -15 luminad 2>/dev/null || pkill -15 -x luminad 2>/dev/null || true
killall -15 lumina-binder 2>/dev/null || pkill -15 -x lumina-binder 2>/dev/null || true

WAIT_COUNT=0
while [ "$WAIT_COUNT" -lt 5 ]; do
    if ! pidof luminad >/dev/null 2>&1 && ! pidof lumina-binder >/dev/null 2>&1; then
        break
    fi
    sleep 0.5
    WAIT_COUNT=$((WAIT_COUNT + 1))
done

killall -9 luminad 2>/dev/null || pkill -9 -x luminad 2>/dev/null || true
killall -9 lumina-binder 2>/dev/null || pkill -9 -x lumina-binder 2>/dev/null || true

rm -f /dev/socket/lumina.sock
rm -f /dev/socket/lumina_ipc.sock
rm -f /dev/socket/lumina_binder.sock
rm -f /dev/socket/luminad.lock
rm -f /data/local/tmp/lumina.sock
rm -f /data/local/tmp/lumina_ipc.sock
rm -f /data/local/tmp/lumina_binder.sock
rm -f /data/local/tmp/luminad.lock
rm -f /data/local/tmp/lumina-binder.lock
rm -f /data/local/tmp/lumina.pid
rm -f /data/local/tmp/lumina.alive
rm -f /data/local/tmp/lumina.log*
rm -f /data/local/tmp/luminad.log*
rm -f /data/local/tmp/lumina-binder.log*

setprop sys.lumina.profile Balanced 2>/dev/null || true

MODDIR=${0%/*}
RESTORE_BIN=""
if [ -f "$MODDIR/luminad" ]; then
    RESTORE_BIN="$MODDIR/luminad"
elif [ -f "/data/adb/modules/luminatweaks/luminad" ]; then
    RESTORE_BIN="/data/adb/modules/luminatweaks/luminad"
fi
if [ -n "$RESTORE_BIN" ]; then
    chmod 755 "$RESTORE_BIN" 2>/dev/null || true
    "$RESTORE_BIN" --restore-all >/dev/null 2>&1 || true
fi

rm -rf /data/adb/luminatweaks
rm -rf /data/adb/.config/lumina
rm -f /data/adb/service.d/luminad_boot.sh
rm -f "${0%/*}/.bootcount" "${0%/*}/.bootloop_guard" "${0%/*}/disable"

exit 0
