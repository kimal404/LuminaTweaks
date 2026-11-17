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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.ui.theme.LocalCornerRadius
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SessionSamplePoint(
    val offsetSec: Int,
    val avgFps: Float,
    val minFps: Float,
    val onePercentLow: Float,
    val zeroPointOnePercentLow: Float,
    val jankFrames: Int,
    val cpuTempC: Float,
    val batteryTempC: Float,
    val powerMw: Int
)

data class GameSessionRecord(
    val id: String,
    val packageName: String,
    val appName: String,
    val profile: String,
    val displayHz: Int,
    val stopReason: String,
    val startTimeMs: Long,
    val durationSec: Int,
    val avgFps: Float,
    val minFps: Int,
    val maxFps: Int,
    val onePercentLowFps: Float,
    val zeroPointOnePercentLowFps: Float,
    val framePacingStdDevMs: Float,
    val jankCount: Int,
    val maxCpuTempC: Float,
    val startBatteryTempC: Float,
    val peakBatteryTempC: Float,
    val avgPowerMw: Int,
    val samples: List<SessionSamplePoint>
)

internal data class SessionLoadResult(
    val sessions: List<GameSessionRecord>,
    val isSampleData: Boolean
)

internal object SessionStatsRepository {
    private const val PREFS_NAME = "lumina_session_stats"
    private const val KEY_RECORD_PACKAGES = "record_packages_set"
    private const val KEY_SAMPLE_DISMISSED = "sample_dismissed"
    private const val DAEMON_SESSIONS_PATH = "/data/adb/.config/lumina/sessions.json"
    private const val DAEMON_RECORD_APPS_PATH = "/data/adb/.config/lumina/record_apps.json"
    private const val ROOT_READ_TIMEOUT_MS = 3_000L
    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    /**
     * `/data/adb` is `0700 root:root`, so the untrusted Manager process can
     * never open files below it directly.  Read through `su` and keep a local
     * copy as the fallback for devices where root is momentarily unavailable.
     */
    private fun readTextWithRoot(path: String): String? {
        val process = runCatching {
            ProcessBuilder("su", "-c", "cat '$path'")
                .redirectErrorStream(false)
                .start()
        }.getOrNull() ?: return null

        var output: String? = null
        val reader = Thread {
            output = runCatching {
                process.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull()
        }
        reader.isDaemon = true
        reader.start()
        reader.join(ROOT_READ_TIMEOUT_MS)

        if (reader.isAlive) {
            runCatching { process.destroyForcibly() }
            return null
        }
        val exited = runCatching {
            process.waitFor(500, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!exited) {
            runCatching { process.destroyForcibly() }
            return null
        }
        if (runCatching { process.exitValue() }.getOrDefault(-1) != 0) {
            return null
        }
        return output?.takeIf { it.isNotBlank() }
    }

    private fun readLocalOrRoot(context: Context, rootPath: String, localName: String): String? {
        readTextWithRoot(rootPath)?.let { return it }
        val localFile = File(context.filesDir, localName)
        if (!localFile.exists() || !localFile.canRead()) return null
        return runCatching { localFile.readText().takeIf { it.isNotBlank() } }.getOrNull()
    }

    suspend fun getRecordedPackages(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val raw = readLocalOrRoot(context, DAEMON_RECORD_APPS_PATH, "record_apps.json")
        if (raw != null) {
            val parsed = runCatching {
                val arr = JSONArray(raw.trim().ifEmpty { "[]" })
                buildSet {
                    for (i in 0 until arr.length()) {
                        val pkg = arr.optString(i).trim()
                        if (pkg.isNotEmpty()) add(pkg)
                    }
                }
            }.getOrNull()
            if (parsed != null) return@withContext parsed
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_RECORD_PACKAGES, emptySet())
            .orEmpty()
    }

    suspend fun setAppRecordEnabled(context: Context, packageName: String, enabled: Boolean): Set<String> {
        return withContext(Dispatchers.IO) {
            val current = getRecordedPackages(context).toMutableSet()
            if (enabled) current.add(packageName) else current.remove(packageName)
            val snapshot = current.toSet()

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_RECORD_PACKAGES, snapshot)
                .apply()

            val json = JSONArray(snapshot.sorted()).toString()
            runCatching {
                File(context.filesDir, "record_apps.json").writeText("$json\n")
            }
            runCatching {
                val escaped = json.replace("'", "'\\''")
                val proc = ProcessBuilder(
                    "su", "-c",
                    "mkdir -p /data/adb/.config/lumina && printf '%s\\n' '$escaped' > $DAEMON_RECORD_APPS_PATH.tmp && chmod 644 $DAEMON_RECORD_APPS_PATH.tmp && mv $DAEMON_RECORD_APPS_PATH.tmp $DAEMON_RECORD_APPS_PATH"
                ).redirectErrorStream(true).start()
                proc.waitFor()
            }
            snapshot
        }
    }

    suspend fun loadSessions(context: Context): SessionLoadResult = withContext(Dispatchers.IO) {
        val raw = readLocalOrRoot(context, DAEMON_SESSIONS_PATH, "sessions.json")
        if (raw != null) {
            val parsed = runCatching { parseSessionsJson(context, raw) }.getOrNull()
            if (parsed != null) {
                return@withContext SessionLoadResult(sessions = parsed, isSampleData = false)
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SAMPLE_DISMISSED, false)) {
            return@withContext SessionLoadResult(sessions = emptyList(), isSampleData = false)
        }

        SessionLoadResult(sessions = defaultSampleSessions(), isSampleData = true)
    }

    suspend fun exportSessionCsv(session: GameSessionRecord): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadsDir, "LuminaTweaks")
            if (!exportDir.exists()) exportDir.mkdirs()

            val safeName = session.appName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(28)
            val outFile = File(exportDir, "lumina_session_${safeName}_${session.id}.csv")
            val recordedAtStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(Date(session.startTimeMs))

            val sb = StringBuilder()
            sb.appendLine("LUMINA TWEAKS TELEMETRY SESSION")
            sb.appendLine("App Name,${escapeCsv(session.appName)}")
            sb.appendLine("Package,${escapeCsv(session.packageName)}")
            sb.appendLine("Profile,${escapeCsv(session.profile)}")
            sb.appendLine("Display Hz,${session.displayHz}")
            sb.appendLine("Stop Mode,${escapeCsv(session.stopReason)}")
            sb.appendLine("Recorded At,${escapeCsv(recordedAtStr)}")
            sb.appendLine("Duration Seconds,${session.durationSec}")
            sb.appendLine(String.format(Locale.US, "Average FPS,%.1f", session.avgFps))
            sb.appendLine("Min FPS,${session.minFps}")
            sb.appendLine("Max FPS,${session.maxFps}")
            sb.appendLine(String.format(Locale.US, "1%% Low FPS,%.1f", session.onePercentLowFps))
            sb.appendLine(String.format(Locale.US, "0.1%% Low FPS,%.1f", session.zeroPointOnePercentLowFps))
            sb.appendLine(String.format(Locale.US, "Frame Pacing StdDev (ms),%.2f", session.framePacingStdDevMs))
            sb.appendLine("Jank Frames,${session.jankCount}")
            sb.appendLine(String.format(Locale.US, "Peak CPU Temp (C),%.1f", session.maxCpuTempC))
            sb.appendLine(String.format(Locale.US, "Start Battery Temp (C),%.1f", session.startBatteryTempC))
            sb.appendLine(String.format(Locale.US, "Peak Battery Temp (C),%.1f", session.peakBatteryTempC))
            sb.appendLine("Average Power (mW),${session.avgPowerMw}")
            sb.appendLine()
            sb.appendLine("Offset (s),Avg FPS,Min FPS,1% Low,0.1% Low,Jank Frames,CPU Temp (C),Battery Temp (C),Power (mW)")
            for (pt in session.samples) {
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%d,%.1f,%.1f,%.1f,%.1f,%d,%.1f,%.1f,%d",
                        pt.offsetSec,
                        pt.avgFps,
                        pt.minFps,
                        pt.onePercentLow,
                        pt.zeroPointOnePercentLow,
                        pt.jankFrames,
                        pt.cpuTempC,
                        pt.batteryTempC,
                        pt.powerMw
                    )
                )
            }
            outFile.writeText(sb.toString(), Charsets.UTF_8)
            outFile
        }
    }

    suspend fun deleteSession(
        context: Context,
        id: String,
        current: List<GameSessionRecord>,
        isSample: Boolean
    ): List<GameSessionRecord> = withContext(Dispatchers.IO) {
        val updated = current.filterNot { it.id == id }
        if (isSample) {
            if (updated.isEmpty()) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_SAMPLE_DISMISSED, true)
                    .apply()
            }
            return@withContext updated
        }
        val json = serializeSessionsJson(updated)
        runCatching { File(context.filesDir, "sessions.json").writeText(json) }
        runCatching {
            val escaped = json.replace("'", "'\\''")
            val proc = ProcessBuilder(
                "su", "-c",
                "printf '%s' '$escaped' > $DAEMON_SESSIONS_PATH.tmp && chmod 644 $DAEMON_SESSIONS_PATH.tmp && mv $DAEMON_SESSIONS_PATH.tmp $DAEMON_SESSIONS_PATH"
            ).redirectErrorStream(true).start()
            proc.waitFor()
        }
        updated
    }

    suspend fun clearAll(context: Context, isSample: Boolean) = withContext(Dispatchers.IO) {
        if (isSample) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SAMPLE_DISMISSED, true)
                .apply()
            return@withContext
        }
        runCatching { File(context.filesDir, "sessions.json").writeText("[]\n") }
        runCatching {
            val proc = ProcessBuilder(
                "su", "-c",
                "printf '[]\\n' > $DAEMON_SESSIONS_PATH && chmod 644 $DAEMON_SESSIONS_PATH"
            ).redirectErrorStream(true).start()
            proc.waitFor()
        }
    }

    suspend fun restoreSamplePreview(context: Context): List<GameSessionRecord> = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SAMPLE_DISMISSED, false)
            .apply()
        defaultSampleSessions()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun resolveAppLabel(context: Context, packageName: String, rawLabel: String): String {
        val label = rawLabel.trim()
        val needsResolution = label.isEmpty() ||
            label == packageName ||
            PACKAGE_NAME_REGEX.matches(label)
        if (!needsResolution) return label

        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(if (label.isNotEmpty()) label else packageName)
    }

    private fun parseSessionsJson(context: Context, raw: String): List<GameSessionRecord> {
        val arr = JSONArray(raw.trim().ifEmpty { "[]" })
        val list = ArrayList<GameSessionRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val samplesArr = obj.optJSONArray("samples")
            val samples = if (samplesArr != null) {
                List(samplesArr.length()) { idx ->
                    val s = samplesArr.optJSONObject(idx)
                    SessionSamplePoint(
                        offsetSec = s?.optInt("offsetSec", idx * 10) ?: (idx * 10),
                        avgFps = s?.optDouble("avgFps", 60.0)?.toFloat() ?: 60f,
                        minFps = s?.optDouble("minFps", 50.0)?.toFloat() ?: 50f,
                        onePercentLow = s?.optDouble("onePercentLow", 45.0)?.toFloat() ?: 45f,
                        zeroPointOnePercentLow = s?.optDouble("zeroPointOnePercentLow", 35.0)?.toFloat() ?: 35f,
                        jankFrames = s?.optInt("jankFrames", 0) ?: 0,
                        cpuTempC = s?.optDouble("cpuTempC", 55.0)?.toFloat() ?: 55f,
                        batteryTempC = s?.optDouble("batteryTempC", 38.0)?.toFloat() ?: 38f,
                        powerMw = s?.optInt("powerMw", 3000) ?: 3000
                    )
                }
            } else {
                emptyList()
            }
            val packageName = obj.optString("packageName", "unknown.app")
                .substringBefore(':')
                .trim()
                .ifEmpty { "unknown.app" }
            list.add(
                GameSessionRecord(
                    id = obj.optString("id", i.toString()),
                    packageName = packageName,
                    appName = resolveAppLabel(
                        context,
                        packageName,
                        obj.optString("appName", "")
                    ),
                    profile = obj.optString("profile", "Balanced"),
                    displayHz = obj.optInt("displayHz", 120),
                    stopReason = obj.optString("stopReason", "auto"),
                    startTimeMs = obj.optLong("startTimeMs", System.currentTimeMillis()),
                    durationSec = obj.optInt("durationSec", 0),
                    avgFps = obj.optDouble("avgFps", 0.0).toFloat(),
                    minFps = obj.optInt("minFps", 0),
                    maxFps = obj.optInt("maxFps", 0),
                    onePercentLowFps = obj.optDouble("onePercentLowFps", 0.0).toFloat(),
                    zeroPointOnePercentLowFps = obj.optDouble("zeroPointOnePercentLowFps", 0.0).toFloat(),
                    framePacingStdDevMs = obj.optDouble("framePacingStdDevMs", 0.0).toFloat(),
                    jankCount = obj.optInt("jankCount", 0),
                    maxCpuTempC = obj.optDouble("maxCpuTempC", 55.0).toFloat(),
                    startBatteryTempC = obj.optDouble("startBatteryTempC", 36.0).toFloat(),
                    peakBatteryTempC = obj.optDouble("peakBatteryTempC", 39.0).toFloat(),
                    avgPowerMw = obj.optInt("avgPowerMw", 0),
                    samples = samples
                )
            )
        }
        return list.sortedByDescending { it.startTimeMs }
    }

    private fun serializeSessionsJson(list: List<GameSessionRecord>): String {
        val arr = JSONArray()
        for (item in list) {
            val sampleArr = JSONArray()
            for (pt in item.samples) {
                sampleArr.put(
                    JSONObject().apply {
                        put("offsetSec", pt.offsetSec)
                        put("avgFps", pt.avgFps.toDouble())
                        put("minFps", pt.minFps.toDouble())
                        put("onePercentLow", pt.onePercentLow.toDouble())
                        put("zeroPointOnePercentLow", pt.zeroPointOnePercentLow.toDouble())
                        put("jankFrames", pt.jankFrames)
                        put("cpuTempC", pt.cpuTempC.toDouble())
                        put("batteryTempC", pt.batteryTempC.toDouble())
                        put("powerMw", pt.powerMw)
                    }
                )
            }
            val obj = JSONObject().apply {
                put("id", item.id)
                put("packageName", item.packageName)
                put("appName", item.appName)
                put("profile", item.profile)
                put("displayHz", item.displayHz)
                put("stopReason", item.stopReason)
                put("startTimeMs", item.startTimeMs)
                put("durationSec", item.durationSec)
                put("avgFps", item.avgFps.toDouble())
                put("minFps", item.minFps)
                put("maxFps", item.maxFps)
                put("onePercentLowFps", item.onePercentLowFps.toDouble())
                put("zeroPointOnePercentLowFps", item.zeroPointOnePercentLowFps.toDouble())
                put("framePacingStdDevMs", item.framePacingStdDevMs.toDouble())
                put("jankCount", item.jankCount)
                put("maxCpuTempC", item.maxCpuTempC.toDouble())
                put("startBatteryTempC", item.startBatteryTempC.toDouble())
                put("peakBatteryTempC", item.peakBatteryTempC.toDouble())
                put("avgPowerMw", item.avgPowerMw)
                put("samples", sampleArr)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun defaultSampleSessions(): List<GameSessionRecord> {
        val now = System.currentTimeMillis()
        val mlbbAvg = listOf(115f, 118f, 112f, 119f, 116f, 108f, 114f, 117f, 104f, 111f, 118f, 115f, 98f, 109f, 116f, 118f, 112f, 117f, 106f, 114f, 119f, 116f, 110f, 115f)
        val mlbbMin = listOf(96f, 104f, 88f, 108f, 94f, 76f, 92f, 101f, 64f, 84f, 103f, 95f, 52f, 79f, 98f, 105f, 86f, 99f, 72f, 91f, 107f, 97f, 81f, 94f)
        val mlbbSamples = mlbbAvg.indices.map { i ->
            SessionSamplePoint(
                offsetSec = i * 50,
                avgFps = mlbbAvg[i],
                minFps = mlbbMin[i],
                onePercentLow = (mlbbMin[i] - 8f).coerceAtLeast(35f),
                zeroPointOnePercentLow = (mlbbMin[i] - 18f).coerceAtLeast(22f),
                jankFrames = if (mlbbMin[i] < 75f) 18 else 4,
                cpuTempC = 64f + (i * 0.9f),
                batteryTempC = 38.2f + (i * 0.16f),
                powerMw = 3850 + (i * 42)
            )
        }

        val pubgAvg = listOf(90f, 89f, 90f, 88f, 87f, 89f, 86f, 88f, 84f, 87f, 89f, 88f, 85f, 89f, 90f, 87f, 86f, 88f, 89f, 88f)
        val pubgMin = listOf(84f, 82f, 85f, 79f, 77f, 83f, 74f, 80f, 68f, 76f, 82f, 80f, 71f, 81f, 84f, 78f, 75f, 80f, 82f, 79f)
        val pubgSamples = pubgAvg.indices.map { i ->
            SessionSamplePoint(
                offsetSec = i * 95,
                avgFps = pubgAvg[i],
                minFps = pubgMin[i],
                onePercentLow = (pubgMin[i] - 6f).coerceAtLeast(45f),
                zeroPointOnePercentLow = (pubgMin[i] - 14f).coerceAtLeast(35f),
                jankFrames = if (pubgMin[i] < 72f) 11 else 2,
                cpuTempC = 68f + (i * 1.1f),
                batteryTempC = 37.5f + (i * 0.21f),
                powerMw = 4420 + (i * 55)
            )
        }

        return listOf(
            GameSessionRecord(
                id = "1794594124635",
                packageName = "com.mobile.legends",
                appName = "Mobile Legends: Bang Bang",
                profile = "Performance",
                displayHz = 120,
                stopReason = "Otomatis",
                startTimeMs = now - 3_600_000L,
                durationSec = 1157,
                avgFps = 113.4f,
                minFps = 52,
                maxFps = 120,
                onePercentLowFps = 64.0f,
                zeroPointOnePercentLowFps = 41.0f,
                framePacingStdDevMs = 1.84f,
                jankCount = 148,
                maxCpuTempC = 84.7f,
                startBatteryTempC = 38.2f,
                peakBatteryTempC = 41.9f,
                avgPowerMw = 4320,
                samples = mlbbSamples
            ),
            GameSessionRecord(
                id = "1794594209677",
                packageName = "com.tencent.ig",
                appName = "PUBG Mobile",
                profile = "Performance",
                displayHz = 90,
                stopReason = "Otomatis",
                startTimeMs = now - 18_000_000L,
                durationSec = 1900,
                avgFps = 87.9f,
                minFps = 68,
                maxFps = 90,
                onePercentLowFps = 71.0f,
                zeroPointOnePercentLowFps = 56.0f,
                framePacingStdDevMs = 1.12f,
                jankCount = 62,
                maxCpuTempC = 88.9f,
                startBatteryTempC = 37.5f,
                peakBatteryTempC = 41.5f,
                avgPowerMw = 4940,
                samples = pubgSamples
            )
        )
    }
}

private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = 96): ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}

private fun formatClockDuration(durationSec: Int): String {
    val safeSec = durationSec.coerceAtLeast(0)
    val mins = safeSec / 60
    val secs = safeSec % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

private fun formatSessionDate(startTimeMs: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
    return sdf.format(Date(startTimeMs))
}

@Composable
internal fun SessionStatsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<GameSessionRecord>>(emptyList()) }
    var isSampleData by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<GameSessionRecord?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = SessionStatsRepository.loadSessions(context)
        sessions = result.sessions
        isSampleData = result.isSampleData
    }

    val activeDetail = selectedSession
    if (activeDetail != null) {
        BackHandler { selectedSession = null }
        SessionDetailScreen(
            session = activeDetail,
            isSampleData = isSampleData,
            onBack = { selectedSession = null },
            onExportCsv = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    val isIndo = Locale.getDefault().language in setOf("in", "id")
                    val res = SessionStatsRepository.exportSessionCsv(activeDetail)
                    res.onSuccess { file ->
                        val msg = if (isIndo) {
                            "Tersimpan di Download/LuminaTweaks/${file.name}"
                        } else {
                            "Saved to Download/LuminaTweaks/${file.name}"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }.onFailure { err ->
                        val msg = if (isIndo) {
                            "Gagal menyimpan CSV: ${err.message}"
                        } else {
                            "Failed to save CSV: ${err.message}"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    sessions = SessionStatsRepository.deleteSession(
                        context = context,
                        id = activeDetail.id,
                        current = sessions,
                        isSample = isSampleData
                    )
                    selectedSession = null
                }
            }
        )
        return
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = localizedLabel("Hapus Semua Riwayat?", "Clear All History?"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = localizedLabel(
                        "Seluruh catatan sesi permainan yang tersimpan akan dihapus.",
                        "All saved gameplay session records will be removed."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            SessionStatsRepository.clearAll(context, isSampleData)
                            sessions = emptyList()
                        }
                    }
                ) {
                    Text(
                        text = localizedLabel("Hapus", "Clear"),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(localizedLabel("Batal", "Cancel"))
                }
            }
        )
    }

    val outerRadius = LocalCornerRadius.current.coerceAtLeast(24.dp)
    val bottomPadding = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = localizedLabel("Statistik Sesi", "Session Stats"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSampleData && sessions.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "SAMPLE",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = localizedLabel("Hapus riwayat", "Clear history")
                            )
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (sessions.isEmpty()) {
                item(key = "empty_state") {
                    Surface(
                        shape = RoundedCornerShape(outerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Text(
                                text = localizedLabel("Belum Ada Hasil Rekaman", "No Recorded Sessions"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = localizedLabel(
                                    "Aktifkan sakelar rekam di halaman Profil Aplikasi pilihan Anda, lalu mainkan permainannya.",
                                    "Enable the recording toggle inside your chosen App Profile, then play a session."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        sessions = SessionStatsRepository.restoreSamplePreview(context)
                                        isSampleData = true
                                    }
                                }
                            ) {
                                Text(localizedLabel("Tampilkan Contoh Data", "Show Sample Data"))
                            }
                        }
                    }
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionHistoryCard(
                        session = session,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedSession = session
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: GameSessionRecord,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(24.dp)
    val innerRadius = (LocalCornerRadius.current * 0.75f).coerceIn(14.dp, 22.dp)

    val appIcon by produceState<ImageBitmap?>(initialValue = null, session.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(session.packageName)
                drawableToImageBitmap(drawable)
            }.getOrNull()
        }
    }

    val fpsColor = when {
        session.avgFps >= 85f -> MaterialTheme.colorScheme.primary
        session.avgFps >= 50f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(outerRadius))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon!!,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatSessionDate(session.startTimeMs)} · ${formatClockDuration(session.durationSec)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = session.profile.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(innerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = String.format(Locale.US, "%.1f", session.avgFps),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = fpsColor
                        )
                        Text(
                            text = localizedLabel("Rata-rata FPS", "Average FPS"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${session.onePercentLowFps.roundToInt()} / ${session.minFps}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "1% Low / Min",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(Locale.US, "%.2f W", session.avgPowerMw / 1000f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "Bat %.1f°C", session.peakBatteryTempC),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDetailScreen(
    session: GameSessionRecord,
    isSampleData: Boolean,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)
    val bottomPadding = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var scrubbedIndex by remember { mutableIntStateOf(-1) }

    val appIcon by produceState<ImageBitmap?>(initialValue = null, session.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                drawableToImageBitmap(context.packageManager.getApplicationIcon(session.packageName))
            }.getOrNull()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatSessionDate(session.startTimeMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExportCsv) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = localizedLabel("Simpan CSV", "Save CSV"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = localizedLabel("Hapus sesi", "Delete session"),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Identitas Sesi (Ringkas & Bersih)
            item(key = "session_identity_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(outerRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (appIcon != null) {
                                Image(
                                    bitmap = appIcon!!,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SportsEsports,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.appName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = session.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SessionTagChip(
                                text = session.profile.replaceFirstChar { it.uppercase() },
                                highlighted = true
                            )
                            SessionTagChip(text = "${session.displayHz} Hz")
                            SessionTagChip(text = formatClockDuration(session.durationSec))
                            if (isSampleData) {
                                SessionTagChip(text = "Sample")
                            }
                        }
                    }
                }
            }

            // 2. KARTU UTAMA ATAS: Grafik Performa FPS (Ditempatkan di atas Ringkasan Telemetri)
            if (session.samples.size >= 2) {
                item(key = "fps_performance_chart") {
                    val avgColor = MaterialTheme.colorScheme.primary
                    val minColor = MaterialTheme.colorScheme.tertiary
                    val activePoint = session.samples.getOrNull(scrubbedIndex)

                    NestedTweakCardGroup(
                        headerIcon = Icons.Rounded.Timeline,
                        headerTitle = localizedLabel("Performa Frame", "Frame Performance")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(innerRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (activePoint != null) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = localizedLabel(
                                                    "Menit ${formatClockDuration(activePoint.offsetSec)}",
                                                    "At ${formatClockDuration(activePoint.offsetSec)}"
                                                ),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = String.format(
                                                    Locale.US,
                                                    "Avg %.0f · Min %.0f FPS · CPU %.0f°C",
                                                    activePoint.avgFps,
                                                    activePoint.minFps,
                                                    activePoint.cpuTempC
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ChartLegendPill(
                                            color = avgColor,
                                            label = localizedLabel("Rata-rata", "Average"),
                                            value = String.format(Locale.US, "%.1f FPS", session.avgFps),
                                            dashed = false
                                        )
                                        ChartLegendPill(
                                            color = minColor,
                                            label = localizedLabel("Terendah", "Minimum"),
                                            value = "${session.minFps} FPS",
                                            dashed = false
                                        )
                                        ChartLegendPill(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            label = localizedLabel("Layar", "Display"),
                                            value = "${session.displayHz} Hz",
                                            dashed = true
                                        )
                                    }
                                }

                                InteractiveDualFpsChart(
                                    samples = session.samples,
                                    targetHz = session.displayHz,
                                    avgColor = avgColor,
                                    minColor = minColor,
                                    gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    scrubbedIndex = scrubbedIndex,
                                    onScrubIndexChange = { scrubbedIndex = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(176.dp)
                                )

                                val midSec = session.durationSec / 2
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "0:00",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatClockDuration(midSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatClockDuration(session.durationSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = localizedLabel(
                                        "Geser jari di atas grafik untuk melihat detail tiap titik waktu",
                                        "Slide finger across the chart to inspect individual timestamps"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. KARTU BAWAH FPS: Ringkasan Telemetri (Baris Bento MD3E Penuh Tanpa Teks Terpotong)
            item(key = "telemetry_summary_section") {
                val deltaTemp = session.peakBatteryTempC - session.startBatteryTempC
                val deltaText = String.format(
                    Locale.US,
                    "%s%.1f°C",
                    if (deltaTemp >= 0f) "+" else "",
                    deltaTemp
                )

                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.DeveloperBoard,
                    headerTitle = localizedLabel("Ringkasan Telemetri", "Telemetry Summary")
                ) {
                    // Blok A: 3 Kolom Utama FPS (Tanpa ikon di dalam kolom sehingga label 100% utuh)
                    Surface(
                        shape = RoundedCornerShape(innerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = String.format(Locale.US, "%.1f", session.avgFps),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = localizedLabel("Rata-rata FPS", "Average FPS"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${session.onePercentLowFps.roundToInt()} FPS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "1% Low",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${session.zeroPointOnePercentLowFps.roundToInt()} FPS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "0.1% Low",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Blok B: Rincian Frame & Pacing
                    Surface(
                        shape = RoundedCornerShape(innerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TelemetryMetricRow(
                                icon = Icons.Rounded.Speed,
                                label = localizedLabel("Rentang FPS", "FPS Range"),
                                value = "${session.minFps} – ${session.maxFps} FPS"
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            TelemetryMetricRow(
                                icon = Icons.Rounded.Timeline,
                                label = localizedLabel("Frame Tersendat", "Stutter Frames"),
                                value = "${session.jankCount} frame · ${String.format(Locale.US, "%.2f ms", session.framePacingStdDevMs)}"
                            )
                        }
                    }

                    // Blok C: Rincian Termal & Konsumsi Daya
                    Surface(
                        shape = RoundedCornerShape(innerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TelemetryMetricRow(
                                icon = Icons.Rounded.Thermostat,
                                label = localizedLabel("Suhu Puncak CPU", "Peak CPU Temp"),
                                value = String.format(Locale.US, "%.1f °C", session.maxCpuTempC)
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            TelemetryMetricRow(
                                icon = Icons.Rounded.Thermostat,
                                label = localizedLabel("Suhu Baterai", "Battery Temp"),
                                value = String.format(
                                    Locale.US,
                                    "%.1f → %.1f °C (%s)",
                                    session.startBatteryTempC,
                                    session.peakBatteryTempC,
                                    deltaText
                                )
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            TelemetryMetricRow(
                                icon = Icons.Rounded.Bolt,
                                label = localizedLabel("Rata-rata Daya", "Average Power"),
                                value = String.format(
                                    Locale.US,
                                    "%.2f W (%d mW)",
                                    session.avgPowerMw / 1000f,
                                    session.avgPowerMw
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionTagChip(
    text: String,
    highlighted: Boolean = false
) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun TelemetryMetricRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChartLegendPill(
    color: Color,
    label: String,
    value: String,
    dashed: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 14.dp, height = 4.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InteractiveDualFpsChart(
    samples: List<SessionSamplePoint>,
    targetHz: Int,
    avgColor: Color,
    minColor: Color,
    gridColor: Color,
    scrubbedIndex: Int,
    onScrubIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val minObserved = samples.minOfOrNull { it.minFps } ?: 40f
    val maxObserved = maxOf(samples.maxOfOrNull { it.avgFps } ?: 120f, targetHz.toFloat())
    val floorFps = ((minObserved - 8f).coerceAtLeast(0f) / 10f).toInt() * 10f
    val ceilFps = (((maxObserved + 6f) / 10f).toInt() + 1) * 10f
    val span = (ceilFps - floorFps).coerceAtLeast(20f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .height(176.dp)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${ceilFps.roundToInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${((ceilFps + floorFps) / 2f).roundToInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${floorFps.roundToInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(176.dp)
                .pointerInput(samples.size) {
                    detectTapGestures(
                        onPress = { offset ->
                            val idx = ((offset.x / size.width.coerceAtLeast(1)) * (samples.size - 1))
                                .roundToInt()
                                .coerceIn(0, samples.lastIndex)
                            onScrubIndexChange(idx)
                            tryAwaitRelease()
                            onScrubIndexChange(-1)
                        }
                    )
                }
                .pointerInput(samples.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val idx = ((offset.x / size.width.coerceAtLeast(1)) * (samples.size - 1))
                                .roundToInt()
                                .coerceIn(0, samples.lastIndex)
                            onScrubIndexChange(idx)
                        },
                        onHorizontalDrag = { change, _ ->
                            val idx = ((change.position.x / size.width.coerceAtLeast(1)) * (samples.size - 1))
                                .roundToInt()
                                .coerceIn(0, samples.lastIndex)
                            onScrubIndexChange(idx)
                        },
                        onDragEnd = { onScrubIndexChange(-1) },
                        onDragCancel = { onScrubIndexChange(-1) }
                    )
                }
        ) {
            if (samples.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas

            fun yForFps(fps: Float): Float {
                val norm = ((fps - floorFps) / span).coerceIn(0f, 1f)
                return size.height * (1f - norm * 0.88f - 0.06f)
            }

            for (step in 0..2) {
                val y = size.height * (0.06f + step * 0.44f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val hzY = yForFps(targetHz.toFloat())
            drawLine(
                color = gridColor.copy(alpha = 0.9f),
                start = Offset(0f, hzY),
                end = Offset(size.width, hzY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )

            val stepX = size.width / (samples.size - 1).toFloat()
            val avgPath = Path()
            val minPath = Path()
            val avgFill = Path()

            samples.forEachIndexed { index, pt ->
                val x = index * stepX
                val yAvg = yForFps(pt.avgFps)
                val yMin = yForFps(pt.minFps)
                if (index == 0) {
                    avgPath.moveTo(x, yAvg)
                    minPath.moveTo(x, yMin)
                    avgFill.moveTo(x, size.height)
                    avgFill.lineTo(x, yAvg)
                } else {
                    avgPath.lineTo(x, yAvg)
                    minPath.lineTo(x, yMin)
                    avgFill.lineTo(x, yAvg)
                }
            }
            avgFill.lineTo(size.width, size.height)
            avgFill.close()

            drawPath(
                path = avgFill,
                brush = Brush.verticalGradient(
                    colors = listOf(avgColor.copy(alpha = 0.18f), Color.Transparent)
                )
            )
            drawPath(
                path = minPath,
                color = minColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = avgPath,
                color = avgColor,
                style = Stroke(
                    width = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            if (scrubbedIndex in samples.indices) {
                val sx = scrubbedIndex * stepX
                val pt = samples[scrubbedIndex]
                drawLine(
                    color = avgColor.copy(alpha = 0.6f),
                    start = Offset(sx, 0f),
                    end = Offset(sx, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawCircle(
                    color = avgColor,
                    radius = 4.5.dp.toPx(),
                    center = Offset(sx, yForFps(pt.avgFps))
                )
                drawCircle(
                    color = minColor,
                    radius = 4.dp.toPx(),
                    center = Offset(sx, yForFps(pt.minFps))
                )
            }
        }
    }
}
