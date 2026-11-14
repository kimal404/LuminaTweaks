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

detect_lumina_soc() {
    for prop in ro.soc.manufacturer ro.soc.model ro.board.platform ro.hardware ro.product.board; do
        value=$(getprop "$prop" 2>/dev/null | tr '[:upper:]' '[:lower:]')
        [ -n "$value" ] || continue
        case "$value" in
            *mediatek*|*mtk*|*mt[0-9]*)
                echo "mediatek"
                return 0
                ;;
            *qualcomm*|*snapdragon*|*qcom*|*sm[0-9]*|*msm[0-9]*|*sdm[0-9]*)
                echo "snapdragon"
                return 0
                ;;
            *kalama*|*lahaina*|*taro*|*waipio*|*cape*|*crow*|*pineapple*|*sun*|*volcano*|*yupik*)
                echo "snapdragon"
                return 0
                ;;
            *unisoc*|*spreadtrum*|*sprd*|*ums[0-9]*|*sc9[0-9]*)
                echo "unisoc"
                return 0
                ;;
            *exynos*|*samsung*|*s5e*|*universal[0-9]*|*erd[0-9]*)
                echo "exynos"
                return 0
                ;;
            *tensor*|*google*|*gs[0-9]*)
                echo "tensor"
                return 0
                ;;
            *tegra*|*nvidia*)
                echo "tegra"
                return 0
                ;;
            *kirin*|*huawei*|*hi[0-9]*)
                echo "kirin"
                return 0
                ;;
        esac
    done

    if [ -e /sys/class/kgsl ] || [ -e /sys/module/adreno_idler ]; then
        echo "snapdragon"
        return 0
    fi

    if [ -e /proc/gpufreq ] || [ -e /sys/kernel/ged ] || [ -e /sys/module/ged ]; then
        echo "mediatek"
        return 0
    fi

    if ls /sys/class/devfreq/sprd-* >/dev/null 2>&1 || [ -e /sys/module/sprd_gpu ]; then
        echo "unisoc"
        return 0
    fi

    if [ -e /sys/kernel/tegra_gpu ]; then
        echo "tegra"
        return 0
    fi

    if [ -e /sys/kernel/gpu ]; then
        echo "exynos"
        return 0
    fi

    if [ -e /sys/class/devfreq/gpufreq ] || [ -e /sys/devices/platform/gpufreq ]; then
        echo "kirin"
        return 0
    fi

    value=$(grep -iE '^(Hardware|Processor|model name)' /proc/cpuinfo 2>/dev/null | head -n 1 | tr '[:upper:]' '[:lower:]')
    case "$value" in
        *mediatek*|*mtk*|*mt[0-9]*)
            echo "mediatek"
            ;;
        *qualcomm*|*snapdragon*|*qcom*|*sm[0-9]*|*msm[0-9]*|*sdm[0-9]*)
            echo "snapdragon"
            ;;
        *unisoc*|*spreadtrum*|*sprd*|*ums[0-9]*|*sc9[0-9]*)
            echo "unisoc"
            ;;
        *exynos*|*samsung*|*s5e*|*universal[0-9]*|*erd[0-9]*)
            echo "exynos"
            ;;
        *tensor*|*google*|*gs[0-9]*)
            echo "tensor"
            ;;
        *tegra*|*nvidia*)
            echo "tegra"
            ;;
        *kirin*|*huawei*|*hi[0-9]*)
            echo "kirin"
            ;;
        *)
            echo "generic"
            ;;
    esac
}