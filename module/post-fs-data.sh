#!/system/bin/sh
# LuminaTweaks post-fs-data initialization script

MODDIR="${0%/*}"

# 1. ABI Check (Skip execution if not 64-bit ARM)
ABI=$(getprop ro.product.cpu.abi)
case "$ABI" in
    arm64*|aarch64*)
        ;;
    *)
        exit 0
        ;;
esac

# Helper to patch SELinux policy rules gracefully
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

# 2. SELinux Policy Patching for task affinity & sysfs access
patch_sepolicy_rule "allow domain proc_type file { read open }"
patch_sepolicy_rule "allow domain proc_type dir { read open search }"
patch_sepolicy_rule "allow magisk su process { sched_setscheduler }"
patch_sepolicy_rule "allow * sysfs file mounton"
patch_sepolicy_rule "allow * sysfs_devices_system_cpu file mounton"
patch_sepolicy_rule "allow * proc file mounton"

# 3. KamiGO-style one-shot initialization with 30-second watchdog
execute_with_timeout() {
    target_bin="$1"
    if [ -f "$target_bin" ]; then
        chmod 755 "$target_bin"
        (
            "$target_bin" &
            BIN_PID=$!
            ELAPSED=0
            while kill -0 "$BIN_PID" 2>/dev/null; do
                if [ "$ELAPSED" -ge 30 ]; then
                    kill -9 "$BIN_PID" 2>/dev/null || true
                    break
                fi
                sleep 1
                ELAPSED=$((ELAPSED + 1))
            done
        ) &
    fi
}

if [ -f "$MODDIR/system/bin/luminad" ]; then
    execute_with_timeout "$MODDIR/system/bin/luminad"
elif [ -f "/data/adb/luminatweaks/luminad" ]; then
    execute_with_timeout "/data/adb/luminatweaks/luminad"
fi

# 4. Graceful exit to never block system boot
exit 0
