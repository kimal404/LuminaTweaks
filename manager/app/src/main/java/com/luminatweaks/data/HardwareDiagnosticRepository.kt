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

import com.luminatweaks.model.DiagnosticLogEntry
import com.luminatweaks.model.HardwareDiagnosticState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object HardwareDiagnosticRepository {

    private val _state = MutableStateFlow(HardwareDiagnosticState())
    val state: StateFlow<HardwareDiagnosticState> = _state.asStateFlow()

    suspend fun runDiagnosticScan() = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isScanning = true, hasScanned = true, logs = emptyList())

        val initialLogs = mutableListOf(
            DiagnosticLogEntry("CORE", "Memulai inisialisasi telemetri sysfs kernel...", isHeader = true)
        )
        _state.value = _state.value.copy(logs = initialLogs.toList())
        delay(250)

        val probeScript = """
            for b_dir in /sys/class/power_supply/battery /sys/class/power_supply/bms; do
                if [ -d "${'$'}b_dir" ]; then
                    echo "B_DIR=${'$'}b_dir"
                    [ -f "${'$'}b_dir/capacity" ] && echo "B_CAPACITY=${'$'}(cat "${'$'}b_dir/capacity")"
                    [ -f "${'$'}b_dir/charge_full" ] && echo "B_FULL=${'$'}(cat "${'$'}b_dir/charge_full")"
                    [ -f "${'$'}b_dir/charge_full_design" ] && echo "B_DESIGN=${'$'}(cat "${'$'}b_dir/charge_full_design")"
                    [ -f "${'$'}b_dir/cycle_count" ] && echo "B_CYCLE=${'$'}(cat "${'$'}b_dir/cycle_count")"
                    [ -f "${'$'}b_dir/temp" ] && echo "B_TEMP=${'$'}(cat "${'$'}b_dir/temp")"
                    [ -f "${'$'}b_dir/voltage_now" ] && echo "B_VOLT=${'$'}(cat "${'$'}b_dir/voltage_now")"
                    [ -f "${'$'}b_dir/status" ] && echo "B_STATUS=${'$'}(cat "${'$'}b_dir/status")"
                    break
                fi
            done

            if [ -d /sys/class/block/sda ]; then
                echo "S_TYPE=UFS"
                echo "S_SUPPORTED=1"
                [ -f /sys/class/block/sda/device/model ] && echo "S_MODEL=${'$'}(cat /sys/class/block/sda/device/model)"
                [ -f /sys/class/block/sda/device/rev ] && echo "S_REV=${'$'}(cat /sys/class/block/sda/device/rev)"

                UFS_LIFE_A=${'$'}(find /sys/devices -path "*ufs*" \( -name "life_time_estimation_a" -o -name "device_life_time_est_a" -o -name "*life_time_a*" \) -print -quit 2>/dev/null)
                [ -n "${'$'}UFS_LIFE_A" ] && echo "S_LIFE_A=${'$'}(cat "${'$'}UFS_LIFE_A")"

                UFS_LIFE_B=${'$'}(find /sys/devices -path "*ufs*" \( -name "life_time_estimation_b" -o -name "device_life_time_est_b" -o -name "*life_time_b*" \) -print -quit 2>/dev/null)
                [ -n "${'$'}UFS_LIFE_B" ] && echo "S_LIFE_B=${'$'}(cat "${'$'}UFS_LIFE_B")"

                UFS_EOL=${'$'}(find /sys/devices -path "*ufs*" \( -name "eol_info" -o -name "pre_eol_info" -o -name "*eol*" \) -print -quit 2>/dev/null)
                [ -n "${'$'}UFS_EOL" ] && echo "S_PRE_EOL_INFO=${'$'}(cat "${'$'}UFS_EOL")"

            elif [ -d /sys/class/block/mmcblk0 ]; then
                echo "S_TYPE=eMMC"
                echo "S_SUPPORTED=1"
                [ -f /sys/class/block/mmcblk0/device/name ] && echo "S_MODEL=${'$'}(cat /sys/class/block/mmcblk0/device/name)"
                [ -f /sys/class/block/mmcblk0/device/rev ] && echo "S_REV=${'$'}(cat /sys/class/block/mmcblk0/device/rev)"

                EMMC_LIFE=${'$'}(find /sys/devices -path "*mmc*" -name "life_time" -print -quit 2>/dev/null)
                [ -n "${'$'}EMMC_LIFE" ] && echo "S_EMMC_LIFE=${'$'}(cat "${'$'}EMMC_LIFE")"

                EMMC_EOL=${'$'}(find /sys/devices -path "*mmc*" \( -name "pre_eol_info" -o -name "*eol*" \) -print -quit 2>/dev/null)
                [ -n "${'$'}EMMC_EOL" ] && echo "S_PRE_EOL_INFO=${'$'}(cat "${'$'}EMMC_EOL")"
            fi
        """.trimIndent()

        val rawOutput = executeSu(probeScript)
        val map = rawOutput.mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim() else null
        }.toMap()

        val capacity = map["b_capacity"]?.toIntOrNull()
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

        val newLogs = mutableListOf<DiagnosticLogEntry>()
        newLogs.add(DiagnosticLogEntry("CORE", "Memulai inisialisasi telemetri sysfs kernel...", isHeader = true))

        if (map.containsKey("b_dir")) {
            if (capacity != null) {
                newLogs.add(DiagnosticLogEntry("LEVEL", "$capacity%"))
            }
            newLogs.add(DiagnosticLogEntry("HEALTH", "${healthPct ?: 100}%"))
            newLogs.add(DiagnosticLogEntry("MAH", "${full ?: "-"} / ${design ?: "-"} mAh"))
            newLogs.add(DiagnosticLogEntry("CYCLE", "${cycle ?: "-"}"))
            newLogs.add(DiagnosticLogEntry("TEMP", "${temp ?: "-"} °C"))
            newLogs.add(DiagnosticLogEntry("VOLT", "${volt ?: "-"} mV"))
            newLogs.add(DiagnosticLogEntry("STATUS", status))
        } else {
            newLogs.add(DiagnosticLogEntry("BATTRE", "Node sysfs baterai tidak ditemukan", isWarning = true))
        }

        val storageType = map["s_type"] ?: "STORAGE"
        val model = map["s_model"] ?: "$storageType Device"
        val rev = map["s_rev"] ?: ""

        val rawLifeA: String?
        val rawLifeB: String?

        if (map.containsKey("s_emmc_life")) {
            val tokens = map["s_emmc_life"]?.split("\\s+".toRegex()) ?: emptyList()
            rawLifeA = tokens.getOrNull(0)
            rawLifeB = tokens.getOrNull(1)
        } else {
            rawLifeA = map["s_life_a"]
            rawLifeB = map["s_life_b"]
        }

        val (wearAStr, _) = parseJedecWear(rawLifeA)
        val (wearBStr, _) = parseJedecWear(rawLifeB)
        val (preEol, isCritical) = parsePreEol(map["s_pre_eol_info"])

        if (map.containsKey("s_supported")) {
            newLogs.add(DiagnosticLogEntry("STORAGE", "$storageType $model ${if (rev.isNotEmpty()) "($rev)" else ""}"))
            newLogs.add(DiagnosticLogEntry("PRE-EOL", preEol, isWarning = isCritical))
            newLogs.add(DiagnosticLogEntry("WEAR-A", wearAStr))
            newLogs.add(DiagnosticLogEntry("WEAR-B", wearBStr))
        } else {
            newLogs.add(DiagnosticLogEntry("STORAGE", "Node penyimpanan tidak terdeteksi", isWarning = true))
        }

        newLogs.add(DiagnosticLogEntry("KERNEL", "Diagnostik selesai dieksekusi", isHeader = true))

        _state.value = _state.value.copy(
            logs = newLogs,
            isScanning = false
        )
    }

    private fun parseJedecWear(hex: String?): Pair<String, Int> {
        val clean = hex?.removePrefix("0x")?.removePrefix("0X")?.trim()
        val value = clean?.toIntOrNull(16) ?: return "Tidak didukung" to 0
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
        val clean = hex?.removePrefix("0x")?.removePrefix("0X")?.trim()
        val value = clean?.toIntOrNull(16) ?: return "Tidak didukung" to false
        return when (value) {
            0x01 -> "Normal (Blok Cadangan Aman)" to false
            0x02 -> "Peringatan (Blok Cadangan Menipis)" to true
            0x03 -> "Kritis (Storage Terancam Read-Only)" to true
            else -> "Tidak didukung" to false
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
