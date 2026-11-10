#!/system/bin/sh
# Copyright (C) 2026 kimal

[ "$(getprop ro.product.cpu.abi)" = "arm64-v8a" ] || [ "$ARCH" = "arm64" ] || exit 0

MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR="/data/adb/modules/luminatweaks"

# Disable the module after three consecutive boots without a successful boot completion.
BOOTCOUNT_FILE="$MODDIR/.bootcount"
BOOTCOUNT=0
[ -f "$BOOTCOUNT_FILE" ] && BOOTCOUNT=$(cat "$BOOTCOUNT_FILE" 2>/dev/null)
case "$BOOTCOUNT" in
    ''|*[!0-9]*) BOOTCOUNT=0 ;;
esac

if [ -f "$MODDIR/.bootloop_guard" ]; then
    rm -f "$MODDIR/.bootloop_guard"
    BOOTCOUNT=0
fi

BOOTCOUNT=$((BOOTCOUNT + 1))
echo "$BOOTCOUNT" > "$BOOTCOUNT_FILE"

if [ "$BOOTCOUNT" -gt 2 ]; then
    touch "$MODDIR/disable" "$MODDIR/.bootloop_guard"
    sed -i "s|^description=.*|description=Disabled after $BOOTCOUNT failed boots. Remove 'disable' to retry.|" "$MODDIR/module.prop"
    log -t LuminaTweaks "Bootloop guard tripped after $BOOTCOUNT boots"
    exit 0
fi

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
