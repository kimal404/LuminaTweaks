#!/system/bin/sh
SKIPUNZIP=0

# ==========================================
# 1. CEK ARSITEKTUR PERANGKAT
# ==========================================
if [ "$ARCH" != "arm64" ]; then
  ui_print "! Arsitektur tidak didukung: $ARCH"
  ui_print "! LuminaTweaks hanya mendukung ARM64 (aarch64)"
  abort "Pemasangan dibatalkan."
fi

# ==========================================
# 2. PERSIAPAN DIREKTORI KERJA
# ==========================================
CONFDIR="/data/adb/luminatweaks"
mkdir -p "$CONFDIR/configs"

ui_print "- Memeriksa chipset perangkat..."

# ==========================================
# 3. DETEKSI SOC (METODE 1: GETPROP)
# ==========================================
SOC=0
SOC_NAME="Tidak Dikenali"

PROPS_LIST="
ro.board.platform
ro.soc.model
ro.hardware
ro.chipname
ro.hardware.chipname
ro.vendor.soc.model
ro.vendor.qti.soc_name
"

for prop in $PROPS_LIST; do
  val=$(getprop "$prop")
  if [ -n "$val" ]; then
    case "$val" in
      *mt*|*MT*|*Mediatek*|*mediatek*)
        SOC=1
        SOC_NAME="MediaTek"
        break
        ;;
      *sm*|*SM*|*qcom*|*QCOM*|*Qualcomm*|*qualcomm*)
        SOC=2
        SOC_NAME="Snapdragon"
        break
        ;;
      *exynos*|*EXYNOS*|*universal*|*s5e*)
        SOC=3
        SOC_NAME="Exynos"
        break
        ;;
      *Unisoc*|*unisoc*|*ums*|*SC*|*sc*)
        SOC=4
        SOC_NAME="Unisoc"
        break
        ;;
      *gs*|*GS*|*Tensor*|*tensor*)
        SOC=5
        SOC_NAME="Google Tensor"
        break
        ;;
    esac
  fi
done

# ==========================================
# 4. DETEKSI SOC (METODE 2: SYSFS FALLBACK)
# ==========================================
if [ "$SOC" -eq 0 ]; then
  if [ -e "/sys/kernel/ged/hal" ]; then
    SOC=1
    SOC_NAME="MediaTek (sysfs)"
  elif [ -e "/sys/class/kgsl/kgsl-3d0" ]; then
    SOC=2
    SOC_NAME="Snapdragon (sysfs)"
  elif [ -e "/sys/kernel/gpu" ]; then
    SOC=3
    SOC_NAME="Exynos (sysfs)"
  elif [ -e "/sys/kernel/tegra_gpu" ]; then
    SOC=6
    SOC_NAME="Tegra (sysfs)"
  fi
fi

# ==========================================
# 5. SIMPAN HASIL RECOGNITION
# ==========================================
echo "$SOC" > "$CONFDIR/soc_recognition"
chmod 644 "$CONFDIR/soc_recognition"

ui_print "- Chipset terdeteksi : $SOC_NAME (ID: $SOC)"

# Buat default config jika belum ada
if [ ! -f "$CONFDIR/master.conf" ]; then
  echo "1" > "$CONFDIR/master.conf"
  chmod 644 "$CONFDIR/master.conf"
fi

# ==========================================
# 6. ATUR IZIN EKSEKUSI (PERMISSIONS)
# ==========================================
ui_print "- Menyiapkan biner dan permissions..."

set_perm_recursive "$MODPATH" 0 0 0755 0644

# Pastikan biner daemon dan script punya akses eksekusi
[ -f "$MODPATH/luminad" ] && set_perm "$MODPATH/luminad" 0 0 0755
[ -f "$MODPATH/bin/luminad" ] && set_perm "$MODPATH/bin/luminad" 0 0 0755
[ -f "$MODPATH/service.sh" ] && set_perm "$MODPATH/service.sh" 0 0 0755

ui_print "- LuminaTweaks berhasil dipasang!"
