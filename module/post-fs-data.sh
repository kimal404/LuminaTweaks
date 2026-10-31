#!/system/bin/sh

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

start_daemon_persistent() {
    target_bin="$1"
    if [ -f "$target_bin" ]; then
        chmod 755 "$target_bin"
        (
            until [ "$(getprop sys.boot_completed)" = "1" ]; do
                sleep 2
            done
            sleep 1
            killall -9 luminad 2>/dev/null || true
            nohup "$target_bin" >/dev/null 2>&1 &
        ) &
    fi
}

if [ -f "$MODDIR/system/bin/luminad" ]; then
    start_daemon_persistent "$MODDIR/system/bin/luminad"
elif [ -f "/data/adb/luminatweaks/luminad" ]; then
    start_daemon_persistent "/data/adb/luminatweaks/luminad"
fi

exit 0
