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

object DeviceUtil {
    private fun getSystemProperty(key: String): String? {
        return try {
            @SuppressLint("PrivateApi")
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            val result = get.invoke(null, key) as? String
            if (result.isNullOrBlank()) null else result.trim()
        } catch (_: Exception) {
            try {
                val process = ProcessBuilder("getprop", key).start()
                val value = process.inputStream.bufferedReader().use { it.readLine() }
                if (value.isNullOrBlank()) null else value.trim()
            } catch (_: Exception) {
                null
            }
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
        // Priority 1: Android BatteryManager sticky broadcast (bypasses SELinux restrictions)
        try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (rawTemp > 0) {
                val celsius = rawTemp / 10.0
                return String.format(Locale.US, "%.1f°C", celsius)
            }
        } catch (_: Exception) {
            // Fall through to sysfs check
        }

        // Priority 2: Direct sysfs paths fallback
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
            } catch (_: Exception) {
                // Ignore and inspect next path
            }
        }

        return "--.-°C"
    }

    fun getDaemonPid(): String {
        val pidFile = File("/data/local/tmp/lumina.pid")
        return if (pidFile.exists() && pidFile.canRead()) {
            pidFile.readText().trim()
        } else {
            "N/A"
        }
    }
}
