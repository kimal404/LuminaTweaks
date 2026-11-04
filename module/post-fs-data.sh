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

MODDIR="${0%/*}"

ABI=$(getprop ro.product.cpu.abi)
case "$ABI" in
    arm64*|aarch64*)
        ;;
    *)
        exit 0
        ;;
esac

patch_sepolicy_rule() {
    rule="$1"
    if command -v magiskpolicy >/dev/null 2>&1; then
        magiskpolicy --live "$rule" 2>/dev/null || true
    elif command -v ksud >/dev/null 2>&1; then
        ksud sepolicy --apply "$rule" 2>/dev/null || true
    elif command -v supolicy >/dev/null 2>&1; then
        supolicy --live "$rule" 2>/dev/null || true
    fi
}

patch_sepolicy_rule "allow domain proc_type file { read open }"
patch_sepolicy_rule "allow domain proc_type dir { read open search }"
patch_sepolicy_rule "allow magisk su process { sched_setscheduler }"
patch_sepolicy_rule "allow * sysfs file mounton"
patch_sepolicy_rule "allow * sysfs_devices_system_cpu file mounton"
patch_sepolicy_rule "allow * proc file mounton"

exit 0
