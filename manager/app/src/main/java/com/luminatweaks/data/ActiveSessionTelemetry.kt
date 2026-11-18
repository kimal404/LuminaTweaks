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
import android.os.Build
import com.luminatweaks.util.DeviceUtil
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Unprivileged telemetry collectors for the Active Session screen.
 *
 * The FPS / frame-time feed is the same file and property `luminad` already
 * publishes for the Floating HUD. Everything else is read straight from
 * world-readable procfs/sysfs nodes, so this screen never spawns `su` and each
 * refresh costs only a handful of small file reads.
 */

data class LiveFeedSample(
    val fps: Float,
    val frametimeMs: Float,
    val epochMs: Long,
    val gameActive: Boolean,
    val packageName: String?,
    val sessionStartedAtMs: Long?
)

object LiveSessionFeed {
    private const val FRESH_AGE_MS = 2_500L
    private const val PUBLIC_FPS_PATH = "/data/local/tmp/lumina.fps"
    private const val LEGACY_FPS_PATH = "/data/adb/.config/lumina/fps.txt"

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
        return if (fresh) newest else null
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
        val sessionStartedAtMs = parts.getOrNull(5)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
        return LiveFeedSample(
            fps = if (fps > 0f) fps else 0f,
            frametimeMs = if (frametime > 0f) frametime else 0f,
            epochMs = epochMs,
            gameActive = gameActive,
            packageName = packageName,
            sessionStartedAtMs = sessionStartedAtMs
        )
    }

    private fun readText(path: String): String? = runCatching {
        File(path).takeIf { it.exists() && it.canRead() }?.readText()?.trim()
    }.getOrNull()
}

data class SessionDeviceContext(
    val platform: String,
    val model: String,
    val osVersion: String,
    val profile: String
)

object SessionDeviceInfo {
    fun read(context: Context): SessionDeviceContext {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        } else {
            null
        }
        val platform = soc
            ?: Build.HARDWARE.replace('_', ' ').trim()
                .replaceFirstChar { it.uppercase() }
                .ifBlank { Build.BOARD.ifBlank { "Unknown" } }
        return SessionDeviceContext(
            platform = platform,
            model = DeviceUtil.getMarketDeviceName(context),
            osVersion = "Android ${Build.VERSION.RELEASE}",
            profile = DeviceUtil.readSystemProperty("sys.lumina.profile") ?: "Balanced"
        )
    }
}

object SessionNoteStore {
    private const val PREFS = "lumina_active_session_notes"

    fun read(context: Context, key: String): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, "")
            .orEmpty()
    }

    fun write(context: Context, key: String, note: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, note.trim())
            .apply()
    }
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
        readGpuBusyPair("/sys/class/kgsl/kgsl-3d0/gpubusy")?.let { return it }
        for (path in GPU_PERCENT_NODES) {
            readPercent(path)?.let { return it }
        }
        val devfreq = File("/sys/class/devfreq")
        for (entry in devfreq.listFiles().orEmpty()) {
            val name = entry.name.lowercase()
            if (!name.contains("gpu") && !name.contains("mali") && !name.contains("kgsl")) continue
            for (node in listOf("gpu_busy_percentage", "gpu_load", "utilisation", "utilization", "load")) {
                readPercent(File(entry, node).absolutePath)?.let { return it }
            }
        }
        return null
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
        val GPU_PERCENT_NODES = listOf(
            "/sys/kernel/gpu/gpu_busy",
            "/sys/class/misc/mali0/device/utilization",
            "/sys/module/ged/parameters/gpu_loading",
            "/sys/kernel/ged/hal/gpu_utilization",
            "/sys/class/devfreq/gpufreq/gpu_busy_percentage"
        )
    }
}

data class ActiveSessionPoint(
    val elapsedSec: Int,
    val fps: Float,
    val frametimeMs: Float,
    val tempC: Float?,
    val cpuPct: Float?,
    val gpuPct: Float?,
    val powerW: Float?
)

data class ActiveSessionSnapshot(
    val hasFeed: Boolean = false,
    val gameActive: Boolean = false,
    val packageName: String? = null,
    val startedAtMs: Long? = null,
    val elapsedSec: Int = 0,
    val maxFps: Float = 0f,
    val minFps: Float = 0f,
    val avgFps: Float = 0f,
    val varianceFps: Float = 0f,
    val smoothnessPct: Float = 0f,
    val fivePercentLowFps: Float? = null,
    val maxTempC: Float? = null,
    val avgPowerW: Float? = null,
    val points: List<ActiveSessionPoint> = emptyList()
)

/**
 * Rolling 1 Hz sampler for the Active Session screen. Charts and aggregates
 * mutate in place and publish one immutable snapshot per tick, which keeps
 * recomposition bounded to roughly one frame per second.
 */
class ActiveSessionMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val sysfs = SysfsTelemetry()
    private val points = ArrayList<ActiveSessionPoint>(MAX_POINTS)

    private var startedAtMs: Long? = null
    private var sessionPackage: String? = null
    private var lastFeedEpochMs = 0L

    fun poll(): ActiveSessionSnapshot {
        val feed = LiveSessionFeed.read(appContext)
        val now = System.currentTimeMillis()
        val active = feed != null && feed.gameActive && feed.fps > 0f

        if (active) {
            val packageChanged = feed!!.packageName != null &&
                sessionPackage != null &&
                feed.packageName != sessionPackage
            if (startedAtMs == null || packageChanged) {
                reset(feed.packageName, feed.sessionStartedAtMs ?: now)
            } else if (sessionPackage == null) {
                sessionPackage = feed.packageName
            }
            val recordedStart = feed.sessionStartedAtMs
            if (recordedStart != null && (startedAtMs == null || recordedStart < startedAtMs!!)) {
                startedAtMs = recordedStart
            }
            if (feed.epochMs != lastFeedEpochMs) {
                lastFeedEpochMs = feed.epochMs
            }
        }

        val temp = sysfs.batteryTempC(appContext)
        val cpu = sysfs.cpuPercent()
        val gpu = sysfs.gpuPercent()
        val powerMw = sysfs.powerMw()

        if (active && startedAtMs != null && feed != null) {
            if (feed.epochMs != lastFeedEpochMs) {
                lastFeedEpochMs = feed.epochMs
                val elapsed = ((now - startedAtMs!!) / 1_000L).toInt().coerceAtLeast(0)
                val frametime = feed.frametimeMs.takeIf { it > 0f } ?: (1_000f / feed.fps)
                points.add(
                    ActiveSessionPoint(
                        elapsedSec = elapsed,
                        fps = feed.fps,
                        frametimeMs = frametime,
                        tempC = temp,
                        cpuPct = cpu,
                        gpuPct = gpu,
                        powerW = powerMw?.let { it / 1_000f }
                    )
                )
                if (points.size > MAX_POINTS) {
                    points.removeAt(0)
                }
            }
        }

        return buildSnapshot(feed, active, now)
    }

    private fun reset(packageName: String?, now: Long) {
        points.clear()
        startedAtMs = now
        sessionPackage = packageName
        lastFeedEpochMs = 0L
    }

    private fun buildSnapshot(
        feed: LiveFeedSample?,
        active: Boolean,
        now: Long
    ): ActiveSessionSnapshot {
        val fpsValues = points.map { it.fps }
        val count = fpsValues.size
        val average = if (count > 0) fpsValues.average().toFloat() else 0f
        val maxFps = fpsValues.maxOrNull() ?: 0f
        val minFps = fpsValues.minOrNull() ?: 0f
        val variance = if (count > 1) {
            fpsValues.sumOf { value ->
                val delta = (value - average).toDouble()
                delta * delta
            }.div(count.toDouble()).toFloat()
        } else {
            0f
        }
        val smoothness = if (count > 0) {
            fpsValues.count { it >= 45f } * 100f / count
        } else {
            0f
        }
        val fivePercentLow = fivePercentLowFps()
        val temps = points.mapNotNull { it.tempC }
        val powers = points.mapNotNull { it.powerW }

        return ActiveSessionSnapshot(
            hasFeed = feed != null,
            gameActive = active,
            packageName = sessionPackage ?: feed?.packageName,
            startedAtMs = startedAtMs,
            elapsedSec = startedAtMs?.let { ((now - it) / 1_000L).toInt().coerceAtLeast(0) } ?: 0,
            maxFps = maxFps,
            minFps = minFps,
            avgFps = average,
            varianceFps = variance,
            smoothnessPct = smoothness,
            fivePercentLowFps = fivePercentLow,
            maxTempC = temps.maxOrNull(),
            avgPowerW = if (powers.isNotEmpty()) powers.average().toFloat() else null,
            points = points.toList()
        )
    }

    private fun fivePercentLowFps(): Float? {
        if (points.isEmpty()) return null
        val slowest = points.map { it.frametimeMs }
            .filter { it > 0f }
            .sortedDescending()
        if (slowest.isEmpty()) return null
        val take = ceil(slowest.size * 0.05).toInt().coerceIn(1, slowest.size)
        val averageFrameMs = slowest.take(take).average()
        if (averageFrameMs <= 0.0) return null
        return (1_000.0 / averageFrameMs).toFloat()
    }

    private companion object {
        const val MAX_POINTS = 1_800
    }
}