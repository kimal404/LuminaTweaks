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

package com.luminatweaks.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object DeviceUtil {
    private fun runCmdWithTimeout(cmd: Array<String>, timeoutSeconds: Long = 3): String {
        return try {
            val process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ""
            }
            process.inputStream.bufferedReader().use { it.readText() }.trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun readNodeOrSu(path: String): String {
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                val text = file.readText().trim()
                if (text.isNotEmpty()) return text
            }
        } catch (_: Exception) {}

        return runCmdWithTimeout(arrayOf("su", "-c", "cat $path 2>/dev/null"), 3)
    }

    private fun getSystemProperty(key: String, allowGetpropFallback: Boolean = true): String? {
        return try {
            @SuppressLint("PrivateApi")
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            val result = get.invoke(null, key) as? String
            if (result.isNullOrBlank()) null else result.trim()
        } catch (_: Exception) {
            if (!allowGetpropFallback) return null
            val value = runCmdWithTimeout(arrayOf("getprop", key), 2)
            value.takeIf { it.isNotBlank() }
        }
    }

    fun getMarketDeviceName(context: Context): String {
        val marketProps = listOf(
            "ro.product.marketname",
            "ro.product.vendor.marketname",
            "ro.product.odm.marketname",
            "ro.product.device.marketname",
            "ro.vendor.oplus.market.name",
            "ro.config.marketing_name"
        )

        for (prop in marketProps) {
            val name = getSystemProperty(prop)
            if (!name.isNullOrBlank()) {
                return name
            }
        }

        try {
            val settingsName = Settings.Global.getString(context.contentResolver, "device_name")
            if (!settingsName.isNullOrBlank()) {
                return settingsName
            }
        } catch (_: Exception) {}

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun getBatteryTemperature(context: Context): String {
        try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (rawTemp > 0) {
                val celsius = rawTemp / 10.0
                return String.format(Locale.US, "%.1f°C", celsius)
            }
        } catch (_: Exception) {}

        return getBatteryTemperatureFallback()
    }

    fun getBatteryTemperature(): String {
        return getBatteryTemperatureFallback()
    }

    private fun getBatteryTemperatureFallback(): String {
        val paths = listOf(
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/bms/temp",
            "/sys/class/thermal/thermal_zone0/temp"
        )
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val raw = file.readText().trim().toIntOrNull()
                    if (raw != null) {
                        val celsius = if (raw > 1000) raw / 1000.0 else raw / 10.0
                        return String.format(Locale.US, "%.1f°C", celsius)
                    }
                }
            } catch (_: Exception) {}
        }
        return "--.-°C"
    }

    fun getSelinuxStatus(): String {
        val daemonValue = getSystemProperty("sys.lumina.selinux", allowGetpropFallback = false)
        if (!daemonValue.isNullOrBlank()) {
            return normalizeSelinuxStatus(daemonValue)
        }

        try {
            val enforceFile = File("/sys/fs/selinux/enforce")
            if (enforceFile.exists() && enforceFile.canRead()) {
                return when (enforceFile.readText().trim()) {
                    "1" -> "Enforcing"
                    "0" -> "Permissive"
                    else -> "Unknown"
                }
            }
        } catch (_: Exception) {}

        val bootValue = getSystemProperty("ro.boot.selinux", allowGetpropFallback = false)
        if (!bootValue.isNullOrBlank()) {
            return normalizeSelinuxStatus(bootValue)
        }

        return "Unknown"
    }

    private fun normalizeSelinuxStatus(raw: String): String {
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "1", "enforcing" -> "Enforcing"
            "0", "permissive" -> "Permissive"
            "disabled" -> "Disabled"
            else -> "Unknown"
        }
    }

    fun getTotalRamGb(context: Context): Int {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
            kotlin.math.ceil(totalGb).toInt().coerceAtLeast(2)
        } catch (_: Exception) {
            8
        }
    }

    fun getCurrentZramDisksizeGb(): Int {
        val raw = readNodeOrSu("/sys/block/zram0/disksize")
        val bytes = raw.toLongOrNull() ?: return 8
        val gb = (bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).let { kotlin.math.round(it).toInt() }
        return gb.coerceAtLeast(1)
    }

    fun getSwappiness(): Int {
        val raw = readNodeOrSu("/proc/sys/vm/swappiness")
        return raw.toIntOrNull()?.coerceIn(0, 200) ?: 100
    }

    fun getActiveZramAlgorithm(): String {
        val raw = readNodeOrSu("/sys/block/zram0/comp_algorithm")
        val active = raw.substringAfter('[', "").substringBefore(']', "")
        return if (active.isNotBlank()) active else "zstd"
    }

    fun getAvailableZramAlgorithms(): List<String> {
        val raw = readNodeOrSu("/sys/block/zram0/comp_algorithm")
        val cleaned = raw.replace("[", "").replace("]", "").trim()
        val list = cleaned.split(" ", "\t", "\n").filter { it.isNotBlank() }
        return if (list.isNotEmpty()) list else listOf("zstd", "lz4", "lzo-rle", "lzo")
    }

    fun getAvailableIoSchedulers(): List<String> {
        val schedulers = linkedSetOf<String>()

        fun parse(raw: String) {
            raw.replace('[', ' ')
                .replace(']', ' ')
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .forEach { schedulers.add(it) }
        }

        try {
            File("/sys/block").listFiles()?.forEach { device ->
                try {
                    val schedulerFile = File(device, "queue/scheduler")
                    if (schedulerFile.exists() && schedulerFile.canRead()) {
                        parse(schedulerFile.readText())
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        parse(
            runCmdWithTimeout(
                arrayOf(
                    "su",
                    "-c",
                    "for f in /sys/block/*/queue/scheduler; do cat \"\$f\" 2>/dev/null; echo; done"
                ),
                4
            )
        )

        return schedulers.toList()
    }
}
