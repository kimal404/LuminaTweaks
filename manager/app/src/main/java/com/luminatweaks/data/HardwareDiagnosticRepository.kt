/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.luminatweaks.data

import com.luminatweaks.model.BatteryHealthInfo
import com.luminatweaks.model.HardwareDiagnosticState
import com.luminatweaks.model.StorageHealthInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object HardwareDiagnosticRepository {

    private val _state = MutableStateFlow(HardwareDiagnosticState())
    val state: StateFlow<HardwareDiagnosticState> = _state.asStateFlow()

    suspend fun refreshDiagnostic() = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isLoading = true)

        val probeScript = """
            for b_dir in /sys/class/power_supply/battery /sys/class/power_supply/bms; do
                if [ -d "$b_dir" ]; then
                    echo "B_DIR=$b_dir"
                    [ -f "$b_dir/charge_full" ] && echo "B_FULL=$(cat "$b_dir/charge_full")"
                    [ -f "$b_dir/charge_full_design" ] && echo "B_DESIGN=$(cat "$b_dir/charge_full_design")"
                    [ -f "$b_dir/cycle_count" ] && echo "B_CYCLE=$(cat "$b_dir/cycle_count")"
                    [ -f "$b_dir/temp" ] && echo "B_TEMP=$(cat "$b_dir/temp")"
                    [ -f "$b_dir/voltage_now" ] && echo "B_VOLT=$(cat "$b_dir/voltage_now")"
                    [ -f "$b_dir/status" ] && echo "B_STATUS=$(cat "$b_dir/status")"
                    break
                fi
            done

            UFS_FOUND=0
            for u_dir in /sys/class/block/sda/device /sys/block/sda/device /sys/devices/platform/soc/*.ufshc; do
                if [ -d "$u_dir" ]; then
                    echo "S_TYPE=UFS"
                    echo "S_DIR=$u_dir"
                    [ -f "$u_dir/model" ] && echo "S_MODEL=$(cat "$u_dir/model")"
                    [ -f "$u_dir/rev" ] && echo "S_REV=$(cat "$u_dir/rev")"
                    for target in life_time_estimation_a life_time_estimation_b pre_eol_info; do
                        node_path=$(find "$u_dir" -name "$target" -print -quit 2>/dev/null)
                        [ -n "$node_path" ] && echo "S_${target}=$(cat "$node_path")"
                    done
                    UFS_FOUND=1
                    break
                fi
            done

            if [ "$UFS_FOUND" -eq 0 ]; then
                for m_dir in /sys/class/block/mmcblk0/device /sys/block/mmcblk0/device; do
                    if [ -d "$m_dir" ]; then
                        echo "S_TYPE=eMMC"
                        echo "S_DIR=$m_dir"
                        [ -f "$m_dir/name" ] && echo "S_MODEL=$(cat "$m_dir/name")"
                        [ -f "$m_dir/rev" ] && echo "S_REV=$(cat "$m_dir/rev")"
                        [ -f "$m_dir/life_time" ] && echo "S_EMMC_LIFE=$(cat "$m_dir/life_time")"
                        [ -f "$m_dir/pre_eol_info" ] && echo "S_pre_eol_info=$(cat "$m_dir/pre_eol_info")"
                        break
                    fi
                done
            fi
        """.trimIndent()

        val rawOutput = executeSu(probeScript)
        val parsedState = parseProbeOutput(rawOutput)
        _state.value = parsedState.copy(isLoading = false)
    }

    private fun parseProbeOutput(lines: List<String>): HardwareDiagnosticState {
        val map = lines.mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim() else null
        }.toMap()

        var full = map["b_full"]?.toLongOrNull()
        var design = map["b_design"]?.toLongOrNull()
        val cycle = map["b_cycle"]?.toIntOrNull()
        val rawTemp = map["b_temp"]?.toFloatOrNull()
        val rawVolt = map["b_volt"]?.toFloatOrNull()
        val status = map["b_status"] ?: "Idle"

        if (full != null && full > 100000) full /= 1000
        if (design != null && design > 100000) design /= 1000

        val healthPct = if (full != null && design != null && design > 0) {
            ((full.toDouble() / design.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else null

        val temp = rawTemp?.let { if (it > 100) it / 10f else it }
        val volt = rawVolt?.let { if (it > 10000) it / 1000f else it }

        val batteryInfo = BatteryHealthInfo(
            healthPercentage = healthPct,
            currentCapacityMah = full?.toInt(),
            designCapacityMah = design?.toInt(),
            cycleCount = cycle,
            temperatureCelsius = temp,
            voltageMv = volt,
            status = status,
            isSupported = map.containsKey("b_dir")
        )

        val storageType = map["s_type"] ?: "Storage"
        val model = map["s_model"] ?: "$storageType Device"
        val rev = map["s_rev"] ?: ""

        val rawLifeA: String?
        val rawLifeB: String?

        if (map.containsKey("s_emmc_life")) {
            val tokens = map["s_emmc_life"]?.split("\\s+".toRegex()) ?: emptyList()
            rawLifeA = tokens.getOrNull(0)
            rawLifeB = tokens.getOrNull(1)
        } else {
            rawLifeA = map["s_life_time_estimation_a"]
            rawLifeB = map["s_life_time_estimation_b"]
        }

        val (wearAStr, wearAPct) = parseJedecWear(rawLifeA)
        val (wearBStr, wearBPct) = parseJedecWear(rawLifeB)
        val (preEol, isCritical) = parsePreEol(map["s_pre_eol_info"])

        val storageInfo = StorageHealthInfo(
            storageType = storageType,
            model = model,
            revision = rev,
            wearLevelA = wearAStr,
            wearLevelB = wearBStr,
            wearPercentageA = wearAPct,
            wearPercentageB = wearBPct,
            preEolStatus = preEol,
            isCritical = isCritical,
            isSupported = map.containsKey("s_dir")
        )

        return HardwareDiagnosticState(battery = batteryInfo, storage = storageInfo)
    }

    private fun parseJedecWear(hex: String?): Pair<String, Int> {
        val value = hex?.removePrefix("0x")?.trim()?.toIntOrNull(16) ?: return "Tidak didukung" to 0
        return when (value) {
            0x01 -> "0% - 10% (Sangat Baik)" to 10
            0x02 -> "10% - 20% (Normal)" to 20
            0x03 -> "20% - 30% (Normal)" to 30
            0x04 -> "30% - 40% (Normal)" to 40
            0x05 -> "40% - 50% (Normal)" to 50
            0x06 -> "50% - 60% (Pemakaian Sedang)" to 60
            0x07 -> "60% - 70% (Pemakaian Sedang)" to 70
            0x08 -> "70% - 80% (Perhatian)" to 80
            0x09 -> "80% - 90% (Perhatian)" to 90
            0x0A -> "90% - 100% (Mendekati EOL)" to 95
            0x0B -> "Melebihi Batas Garansi" to 100
            else -> "N/A ($hex)" to 0
        }
    }

    private fun parsePreEol(hex: String?): Pair<String, Boolean> {
        val value = hex?.removePrefix("0x")?.trim()?.toIntOrNull(16) ?: return "Normal" to false
        return when (value) {
            0x01 -> "Normal (Blok Cadangan Aman)" to false
            0x02 -> "Peringatan (Blok Cadangan Menipis)" to true
            0x03 -> "Kritis (Storage Terancam Read-Only)" to true
            else -> "Normal" to false
        }
    }

    private fun executeSu(script: String): List<String> {
        return try {
            val process = ProcessBuilder("su", "-c", script).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()
            process.waitFor()
            lines
        } catch (_: Exception) {
            emptyList()
        }
    }
}
