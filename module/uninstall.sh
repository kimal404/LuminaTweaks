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

pkill -TERM luminad 2>/dev/null || killall -TERM luminad 2>/dev/null
pkill -TERM lumina-binder 2>/dev/null || killall -TERM lumina-binder 2>/dev/null
sleep 1
pkill -9 luminad 2>/dev/null || killall -9 luminad 2>/dev/null
pkill -9 lumina-binder 2>/dev/null || killall -9 lumina-binder 2>/dev/null

rm -f /dev/socket/lumina.sock
rm -f /dev/socket/lumina_ipc.sock
rm -f /dev/socket/lumina_binder.sock
rm -f /data/local/tmp/lumina.sock
rm -f /data/local/tmp/lumina_ipc.sock
rm -f /data/local/tmp/lumina_binder.sock
rm -f /data/local/tmp/luminad.lock
rm -f /data/local/tmp/lumina-binder.lock
rm -f /data/local/tmp/lumina.log*
rm -f /data/local/tmp/luminad.log*
rm -f /data/local/tmp/lumina-binder.log*

rm -rf /data/adb/luminatweaks
rm -rf /data/adb/.config/lumina
rm -f /data/adb/service.d/luminad_boot.sh
rm -f "${0%/*}/.bootcount" "${0%/*}/.bootloop_guard" "${0%/*}/disable"

exit 0
