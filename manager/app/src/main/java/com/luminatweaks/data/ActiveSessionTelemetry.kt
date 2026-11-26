// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.luminatweaks.data

import android.content.Context
import com.luminatweaks.util.DeviceUtil
import java.io.File
import kotlin.math.abs

/**
 * Unprivileged telemetry collectors shared by the Game Deck.
 *
 * The FPS / frame-time feed is the same file and property `luminad` publishes
 * for the Floating HUD. Hardware readings come from world-readable procfs and
 * sysfs nodes, so callers never need to spawn `su`.
 */

data class LiveFeedSample(
    val fps: Float,
    val frametimeMs: Float,
    val epochMs: Long,
    val gameActive: Boolean,
    val packageName: String?,
    val cpuPercent: Float? = null,
    val gpuPercent: Float? = null
)

object LiveSessionFeed {
    private const val FRESH_AGE_MS = 2_500L
    private const val PUBLIC_FPS_PATH = "/data/local/tmp/lumina.fps"
    private const val LEGACY_FPS_PATH = "/data/adb/.config/lumina/fps.txt"
    private val PERCENT_NUMBER = Regex("""-?\d+(?:\.\d+)?""")

    fun read(context: Context): LiveFeedSample? {
        val candidates = ArrayList<LiveFeedSample>(4)
        parse(DeviceUtil.readSystemProperty("sys.lumina.fps"))?.let(candidates::add)
        for (path in listOf(
            File(context.filesDir, "lumina.fps").absolutePath,
            PUBLIC_FPS_PATH,
            LEGACY_FPS_PATH
        )) {
            parse(readText(path))?.let(candidates::add)
        }
        val newest = candidates.maxByOrNull { it.epochMs } ?: return null
        val fresh = System.currentTimeMillis() - newest.epochMs <= FRESH_AGE_MS
        if (!fresh) return null
        val cpu = newest.cpuPercent
            ?: parsePercent(DeviceUtil.readSystemProperty("sys.lumina.cpu"))
        val gpu = newest.gpuPercent
            ?: parsePercent(DeviceUtil.readSystemProperty("sys.lumina.gpu"))
        return newest.copy(cpuPercent = cpu, gpuPercent = gpu)
    }

    private fun parsePercent(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim().substringBefore(' ').substringBefore('@').toFloatOrNull()
            ?: PERCENT_NUMBER.find(raw)?.value?.toFloatOrNull()
        return value?.takeIf { it.isFinite() && it in 0f..100f }
    }

    private fun parse(raw: String?): LiveFeedSample? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        val fps = parts[0].toFloatOrNull() ?: return null
        val epochMs = parts[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val frametime = parts.getOrNull(2)?.toFloatOrNull()
            ?: if (fps > 0f) 1000f / fps else 0f
        val gameActive = when (parts.getOrNull(3)) {
            "1", "true", "TRUE" -> true
            "0", "false", "FALSE" -> false
            else -> fps > 0f
        }
        val packageName = parts.getOrNull(4)
            ?.substringBefore(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "-" }
        val cpuPercent = parts.getOrNull(5)
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..100f }
        val gpuPercent = parts.getOrNull(6)
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..100f }
        return LiveFeedSample(
            fps = if (fps > 0f) fps else 0f,
            frametimeMs = if (frametime > 0f) frametime else 0f,
            epochMs = epochMs,
            gameActive = gameActive,
            packageName = packageName,
            cpuPercent = cpuPercent,
            gpuPercent = gpuPercent
        )
    }

    private fun readText(path: String): String? = runCatching {
        File(path).takeIf { it.exists() && it.canRead() }?.readText()?.trim()
    }.getOrNull()
}

internal data class CpuTimes(val total: Long, val idle: Long)

/**
 * CPU/GPU utilisation, battery temperature and power. Every node is probed at
 * runtime; a missing sensor simply yields `null` so the UI can render `N/A`.
 */
internal class SysfsTelemetry {
    private var previousCpu: CpuTimes? = null

    fun cpuPercent(): Float? {
        val current = readCpuTimes() ?: return null
        val previous = previousCpu
        previousCpu = current
        if (previous == null) return null
        val total = current.total - previous.total
        val idle = current.idle - previous.idle
        if (total <= 0L) return null
        return (((total - idle).toDouble() / total.toDouble()) * 100.0)
            .toFloat()
            .coerceIn(0f, 100f)
    }

    fun gpuPercent(): Float? {
        var fallback: Float? = null
        for (source in GPU_SOURCES) {
            val value = if (source.pair) {
                readGpuBusyPair(source.path)
            } else {
                readPercent(source.path)
            }
            if (value != null) {
                if (value > 0f) return value
                fallback = value
            }
        }
        return fallback
    }

    fun batteryTempC(context: Context): Float? {
        val raw = DeviceUtil.getBatteryTemperature(context)
        return PERCENT_NUMBER.find(raw)?.value?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..100f }
    }

    fun powerMw(): Float? {
        val supplies = File("/sys/class/power_supply").listFiles().orEmpty()
        val batteries = supplies.filter { dir ->
            readText(File(dir, "type").absolutePath)?.lowercase()?.contains("battery") == true
        } + supplies.filter { dir ->
            dir.name.equals("battery", ignoreCase = true) || dir.name.equals("bms", ignoreCase = true)
        }
        for (dir in batteries.distinct()) {
            val direct = readText(File(dir, "power_now").absolutePath)?.toFloatOrNull()?.let(::abs)
            if (direct != null && direct > 0f) {
                return if (direct > 20_000f) direct / 1_000f else direct
            }
            val current = firstNumber(dir, listOf("current_now", "current_avg")) ?: continue
            val voltage = firstNumber(dir, listOf("voltage_now", "voltage_avg")) ?: continue
            if (current <= 0f || voltage <= 0f) continue
            val voltageMv = if (voltage > 100_000f) voltage / 1_000f else voltage
            return current * voltageMv / 1_000_000f
        }
        return null
    }

    private fun readCpuTimes(): CpuTimes? {
        val line = readText("/proc/stat")
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("cpu ") }
            ?: return null
        val values = line.trim().split(Regex("\\s+"))
            .drop(1)
            .mapNotNull { it.toLongOrNull() }
        if (values.size < 5) return null
        val idle = values[3] + values[4]
        return CpuTimes(total = values.sum(), idle = idle)
    }

    private fun readGpuBusyPair(path: String): Float? {
        val parts = readText(path)?.split(Regex("\\s+")) ?: return null
        if (parts.size < 2) return null
        val busy = parts[0].toFloatOrNull() ?: return null
        val total = parts[1].toFloatOrNull() ?: return null
        if (busy < 0f || total <= 0f) return null
        return (busy / total * 100f).coerceIn(0f, 100f)
    }

    private fun readPercent(path: String): Float? {
        val raw = readText(path) ?: return null
        return PERCENT_NUMBER.find(raw)?.value?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.coerceIn(0f, 100f)
    }

    private fun firstNumber(dir: File, names: List<String>): Float? {
        for (name in names) {
            readText(File(dir, name).absolutePath)?.toFloatOrNull()?.let { return abs(it) }
        }
        return null
    }

    private fun readText(path: String): String? = runCatching {
        File(path).takeIf { it.exists() && it.canRead() }?.readText()?.trim()
    }.getOrNull()

    private companion object {
        val PERCENT_NUMBER = Regex("""-?\d+(?:\.\d+)?""")
        val GPU_SOURCES: List<GpuProbe> = discoverGpuSources()

        fun discoverGpuSources(): List<GpuProbe> {
            val sources = ArrayList<GpuProbe>(8)
            val pairPath = "/sys/class/kgsl/kgsl-3d0/gpubusy"
            if (File(pairPath).exists()) {
                sources += GpuProbe(pairPath, pair = true)
            }
            val nodes = listOf(
                "/sys/kernel/gpu/gpu_busy",
                "/sys/class/misc/mali0/device/utilization",
                "/sys/class/misc/mali0/device/utilisation",
                "/proc/mali/utilization",
                "/sys/module/ged/parameters/gpu_loading",
                "/sys/kernel/ged/hal/gpu_utilization",
                "/sys/kernel/ged/hal/gpu_loading",
                "/sys/class/devfreq/gpufreq/gpu_busy_percentage",
                "/sys/class/devfreq/gpufreq/gpu_loading"
            )
            for (path in nodes) {
                if (File(path).exists()) {
                    sources += GpuProbe(path, pair = false)
                }
            }
            val devfreq = File("/sys/class/devfreq")
            for (entry in devfreq.listFiles().orEmpty()) {
                val name = entry.name.lowercase()
                if (!name.contains("gpu") && !name.contains("mali") && !name.contains("kgsl")) continue
                for (node in listOf("gpu_busy_percentage", "gpu_load", "utilisation", "utilization", "load")) {
                    val file = File(entry, node)
                    if (file.exists()) {
                        sources += GpuProbe(file.absolutePath, pair = false)
                    }
                }
            }
            if (sources.isEmpty()) {
                for (path in listOf(
                    "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_busy_percentage",
                    "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
                )) {
                    if (File(path).exists()) {
                        sources += GpuProbe(path, pair = false)
                    }
                }
            }
            return sources
        }
    }
}

private data class GpuProbe(val path: String, val pair: Boolean)

