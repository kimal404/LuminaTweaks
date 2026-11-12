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

WATCHDOG_PID_FILE="/data/local/tmp/lumina_watchdog.pid"
HEARTBEAT_FILE="/data/local/tmp/lumina.alive"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

sleep 3

echo 0 > "$MODDIR/.bootcount"
rm -f "$MODDIR/.bootloop_guard"

cmd appops set com.luminatweaks AUTO_START allow >/dev/null 2>&1
cmd appops set com.luminatweaks RUN_IN_BACKGROUND allow >/dev/null 2>&1
dumpsys deviceidle whitelist +com.luminatweaks >/dev/null 2>&1

# Stop previous watchdog subshell before restarting daemons
if [ -f "$WATCHDOG_PID_FILE" ]; then
    OLD_WD_PID=$(cat "$WATCHDOG_PID_FILE" 2>/dev/null)
    if [ -n "$OLD_WD_PID" ] && [ "$OLD_WD_PID" != "$$" ]; then
        kill -9 "$OLD_WD_PID" 2>/dev/null || true
    fi
    rm -f "$WATCHDOG_PID_FILE"
fi

# Graceful shutdown (SIGTERM + wait up to 2.5s so luminad unlocks 0444 nodes and restores balance)
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

rm -f /dev/socket/lumina.sock /dev/socket/lumina_ipc.sock /dev/socket/lumina_binder.sock
rm -f /data/local/tmp/lumina.sock /data/local/tmp/lumina_ipc.sock /data/local/tmp/lumina_binder.sock
rm -f /data/local/tmp/lumina.pid
rm -f /data/local/tmp/lumina.alive

DAEMON_BIN=""
if [ -f "$MODDIR/luminad" ]; then
    DAEMON_BIN="$MODDIR/luminad"
elif [ -f "/data/adb/modules/luminatweaks/luminad" ]; then
    DAEMON_BIN="/data/adb/modules/luminatweaks/luminad"
fi

BINDER_BIN=""
if [ -f "$MODDIR/lumina-binder" ]; then
    BINDER_BIN="$MODDIR/lumina-binder"
elif [ -f "/data/adb/modules/luminatweaks/lumina-binder" ]; then
    BINDER_BIN="/data/adb/modules/luminatweaks/lumina-binder"
fi

if [ -n "$BINDER_BIN" ]; then
    chmod 755 "$BINDER_BIN"
    nohup "$BINDER_BIN" >/dev/null 2>&1 &
    sleep 1
fi

if [ -n "$DAEMON_BIN" ]; then
    chmod 755 "$DAEMON_BIN"
    nohup "$DAEMON_BIN" >/dev/null 2>&1 &
fi

if [ -f "$MODDIR/module.prop" ]; then
    sed -i "s|^description=.*|description=Daemon is starting.|" "$MODDIR/module.prop"
fi

(
    LAST_PID=""
    while true; do
        if [ -n "$BINDER_BIN" ] && ! pidof lumina-binder >/dev/null 2>&1; then
            nohup "$BINDER_BIN" >/dev/null 2>&1 &
        fi

        if [ -n "$DAEMON_BIN" ] && ! pidof luminad >/dev/null 2>&1; then
            nohup "$DAEMON_BIN" >/dev/null 2>&1 &
        elif [ -n "$DAEMON_BIN" ] && [ -f "$HEARTBEAT_FILE" ]; then
            NOW=$(date +%s 2>/dev/null)
            MTIME=$(stat -c %Y "$HEARTBEAT_FILE" 2>/dev/null)
            if [ -n "$NOW" ] && [ -n "$MTIME" ] && [ $((NOW - MTIME)) -gt 20 ]; then
                sleep 5
                NOW=$(date +%s 2>/dev/null)
                MTIME=$(stat -c %Y "$HEARTBEAT_FILE" 2>/dev/null)
                if [ -n "$NOW" ] && [ -n "$MTIME" ] && [ $((NOW - MTIME)) -gt 20 ]; then
                    killall -15 luminad 2>/dev/null || pkill -15 -x luminad 2>/dev/null || true
                    sleep 5
                    if pidof luminad >/dev/null 2>&1; then
                        killall -9 luminad 2>/dev/null || pkill -9 -x luminad 2>/dev/null || true
                    fi
                fi
            fi
        fi

        CURRENT_PID=$(pidof luminad | awk '{print $1}')
        if [ "$CURRENT_PID" != "$LAST_PID" ]; then
            if [ -n "$CURRENT_PID" ]; then
                sed -i "s|^description=.*|description=Daemon is running (PID ${CURRENT_PID}).|" "$MODDIR/module.prop"
            else
                sed -i "s|^description=.*|description=Daemon is stopped.|" "$MODDIR/module.prop"
            fi
            LAST_PID="$CURRENT_PID"
        fi
        sleep 10
    done
) >/dev/null 2>&1 &

echo $! > "$WATCHDOG_PID_FILE"

exit 0
