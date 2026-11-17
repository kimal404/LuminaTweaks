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
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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

internal data class RecordableAppEntry(
    val packageName: String,
    val appName: String,
    val isInGameList: Boolean
)

internal data class SessionLoadResult(
    val sessions: List<GameSessionRecord>,
    val isMockData: Boolean
)

internal object SessionStatsRepository {
    private const val PREFS_NAME = "lumina_session_stats"
    private const val KEY_RECORD_PACKAGES = "record_packages_set"
    private const val KEY_MOCK_DISMISSED = "mock_dismissed"
    private const val DAEMON_SESSIONS_PATH = "/data/adb/.config/lumina/sessions.json"
    private const val DAEMON_RECORD_APPS_PATH = "/data/adb/.config/lumina/record_apps.json"
    private const val DAEMON_GAMELIST_PATH = "/data/adb/.config/lumina/gamelist.json"

    fun getRecordedPackages(context: Context): Set<String> {
        val daemonFile = File(DAEMON_RECORD_APPS_PATH)
        val localFile = File(context.filesDir, "record_apps.json")
        val source = when {
            daemonFile.exists() && daemonFile.canRead() -> daemonFile
            localFile.exists() && localFile.canRead() -> localFile
            else -> null
        }
        if (source != null) {
            val parsed = runCatching {
                val arr = JSONArray(source.readText().trim().ifEmpty { "[]" })
                buildSet {
                    for (i in 0 until arr.length()) {
                        val pkg = arr.optString(i).trim()
                        if (pkg.isNotEmpty()) add(pkg)
                    }
                }
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    suspend fun loadRecordableApps(context: Context): List<RecordableAppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val gamePackages = runCatching {
            val f = File(DAEMON_GAMELIST_PATH)
            if (f.exists() && f.canRead()) {
                val raw = f.readText().trim()
                if (raw.startsWith("[")) {
                    val arr = JSONArray(raw)
                    buildSet {
                        for (i in 0 until arr.length()) {
                            val item = arr.opt(i)
                            val pkg = when (item) {
                                is String -> item
                                is JSONObject -> item.optString("packageName", item.optString("package", ""))
                                else -> ""
                            }.trim()
                            if (pkg.isNotEmpty()) add(pkg)
                        }
                    }
                } else {
                    raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
                }
            } else {
                emptySet()
            }
        }.getOrDefault(emptySet())

        val installed = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        installed.mapNotNull { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystem && !isUpdatedSystem && appInfo.packageName !in gamePackages) {
                return@mapNotNull null
            }
            if (appInfo.packageName == context.packageName) return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(appInfo.packageName)
            RecordableAppEntry(
                packageName = appInfo.packageName,
                appName = label,
                isInGameList = appInfo.packageName in gamePackages
            )
        }.sortedWith(
            compareByDescending<RecordableAppEntry> { it.isInGameList }
                .thenBy { it.appName.lowercase(Locale.getDefault()) }
        )
    }

    suspend fun loadSessions(context: Context): SessionLoadResult = withContext(Dispatchers.IO) {
        val daemonFile = File(DAEMON_SESSIONS_PATH)
        val localFile = File(context.filesDir, "sessions.json")
        val sourceFile = when {
            daemonFile.exists() && daemonFile.canRead() -> daemonFile
            localFile.exists() && localFile.canRead() -> localFile
            else -> null
        }

        if (sourceFile != null) {
            val parsed = runCatching { parseSessionsJson(sourceFile.readText()) }.getOrNull()
            if (parsed != null) {
                return@withContext SessionLoadResult(sessions = parsed, isMockData = false)
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MOCK_DISMISSED, false)) {
            return@withContext SessionLoadResult(sessions = emptyList(), isMockData = false)
        }

        SessionLoadResult(sessions = defaultMockSessions(), isMockData = true)
    }

    suspend fun exportSessionCsv(session: GameSessionRecord): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadsDir, "LuminaTweaks")
            if (!exportDir.exists()) exportDir.mkdirs()

            val safeName = session.appName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(28)
            val outFile = File(exportDir, "lumina_session_${safeName}_${session.id}.csv")
            val recordedAtStr = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
                .format(Date(session.startTimeMs))

            val sb = StringBuilder()
            sb.appendLine("LUMINA TWEAKS SESSION REPORT")
            sb.appendLine("Game Title,${escapeCsv(session.appName)}")
            sb.appendLine("Package Name,${escapeCsv(session.packageName)}")
            sb.appendLine("Profile,${escapeCsv(session.profile)}")
            sb.appendLine("Display Refresh Rate (Hz),${session.displayHz}")
            sb.appendLine("Stopped,${escapeCsv(session.stopReason)}")
            sb.appendLine("Recorded At,${escapeCsv(recordedAtStr)}")
            sb.appendLine("Duration (Seconds),${session.durationSec}")
            sb.appendLine("Total Samples,${session.samples.size}")
            sb.appendLine(String.format(Locale.US, "Average FPS,%.1f", session.avgFps))
            sb.appendLine("Min FPS,${session.minFps}")
            sb.appendLine("Peak FPS,${session.maxFps}")
            sb.appendLine(String.format(Locale.US, "1%% Low FPS,%.1f", session.onePercentLowFps))
            sb.appendLine(String.format(Locale.US, "0.1%% Low FPS,%.1f", session.zeroPointOnePercentLowFps))
            sb.appendLine(String.format(Locale.US, "Frame Pacing StdDev (ms),%.2f", session.framePacingStdDevMs))
            sb.appendLine("Total Jank Frames,${session.jankCount}")
            sb.appendLine(String.format(Locale.US, "Max CPU Temp (°C),%.1f", session.maxCpuTempC))
            sb.appendLine(String.format(Locale.US, "Start Battery Temp (°C),%.1f", session.startBatteryTempC))
            sb.appendLine(String.format(Locale.US, "Peak Battery Temp (°C),%.1f", session.peakBatteryTempC))
            sb.appendLine("Average Power (mW),${session.avgPowerMw}")
            sb.appendLine()
            sb.appendLine("Timestamp Offset (ms),Average FPS,Min FPS,1% Low,0.1% Low,Jank Frames,CPU Temp (°C),Battery Temp (°C),Power (mW)")
            for (pt in session.samples) {
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "%d,%.1f,%.1f,%.1f,%.1f,%d,%.1f,%.1f,%d",
                        pt.offsetSec * 1000L,
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
        isMock: Boolean
    ): List<GameSessionRecord> = withContext(Dispatchers.IO) {
        val updated = current.filterNot { it.id == id }
        if (isMock) {
            if (updated.isEmpty()) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_MOCK_DISMISSED, true)
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

    suspend fun clearAll(context: Context, isMock: Boolean) = withContext(Dispatchers.IO) {
        if (isMock) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MOCK_DISMISSED, true)
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

    suspend fun restoreMockPreview(context: Context): List<GameSessionRecord> = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MOCK_DISMISSED, false)
            .apply()
        defaultMockSessions()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun parseSessionsJson(raw: String): List<GameSessionRecord> {
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
            list.add(
                GameSessionRecord(
                    id = obj.optString("id", i.toString()),
                    packageName = obj.optString("packageName", "unknown.app"),
                    appName = obj.optString("appName", obj.optString("packageName", "Game")),
                    profile = obj.optString("profile", "Balanced"),
                    displayHz = obj.optInt("displayHz", 120),
                    stopReason = obj.optString("stopReason", "manual"),
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

    private fun defaultMockSessions(): List<GameSessionRecord> {
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
                profile = "performance",
                displayHz = 120,
                stopReason = "manual",
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
                profile = "performance",
                displayHz = 90,
                stopReason = "auto",
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
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(startTimeMs))
}

@Composable
internal fun SessionStatsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var recordedPackages by remember { mutableStateOf(SessionStatsRepository.getRecordedPackages(context)) }
    var sessions by remember { mutableStateOf<List<GameSessionRecord>>(emptyList()) }
    var isMockData by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<GameSessionRecord?>(null) }
    var showAppToggleManager by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        recordedPackages = SessionStatsRepository.getRecordedPackages(context)
        val result = SessionStatsRepository.loadSessions(context)
        sessions = result.sessions
        isMockData = result.isMockData
    }

    if (showAppToggleManager) {
        BackHandler { showAppToggleManager = false }
        PerAppRecordToggleScreen(
            recordedPackages = recordedPackages,
            onToggleApp = { pkg, enabled ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    recordedPackages = SessionStatsRepository.setAppRecordEnabled(context, pkg, enabled)
                }
            },
            onBack = { showAppToggleManager = false }
        )
        return
    }

    val activeDetail = selectedSession
    if (activeDetail != null) {
        BackHandler { selectedSession = null }
        SessionDetailScreen(
            session = activeDetail,
            isMockData = isMockData,
            onBack = { selectedSession = null },
            onExportCsv = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    val isIndo = Locale.getDefault().language in setOf("in", "id")
                    val res = SessionStatsRepository.exportSessionCsv(activeDetail)
                    res.onSuccess { file ->
                        val msg = if (isIndo) {
                            "Tersimpan: Download/LuminaTweaks/${file.name}"
                        } else {
                            "Saved: Download/LuminaTweaks/${file.name}"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }.onFailure { err ->
                        val msg = if (isIndo) {
                            "Gagal ekspor CSV: ${err.message}"
                        } else {
                            "CSV export failed: ${err.message}"
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
                        isMock = isMockData
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
                            SessionStatsRepository.clearAll(context, isMockData)
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
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)
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
                        if (isMockData && sessions.isNotEmpty()) {
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
                            imageVector = Icons.Rounded.ArrowBack,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "per_app_control") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.FiberManualRecord,
                    headerTitle = localizedLabel("Perekaman Per Aplikasi", "Per-App Recording")
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (recordedPackages.isEmpty()) {
                                            localizedLabel("Belum ada aplikasi dipilih", "No apps selected")
                                        } else {
                                            localizedLabel(
                                                "${recordedPackages.size} aplikasi diaktifkan",
                                                "${recordedPackages.size} apps enabled"
                                            )
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = localizedLabel(
                                            "Pilih masing-masing aplikasi yang ingin direkam sesinya",
                                            "Toggle session recording individually per application"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showAppToggleManager = true
                                },
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = localizedLabel("Atur Toggle Per Aplikasi", "Configure Per-App Toggles"),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

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
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = localizedLabel("Belum Ada Sesi Terekam", "No Sessions Recorded"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = localizedLabel(
                                    "Aktifkan toggle rekam pada aplikasi pilihan lalu jalankan permainannya.",
                                    "Enable the record toggle on chosen apps and play a session."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        sessions = SessionStatsRepository.restoreMockPreview(context)
                                        isMockData = true
                                    }
                                }
                            ) {
                                Text(localizedLabel("Muat Contoh Data", "Load Sample Data"))
                            }
                        }
                    }
                }
            } else {
                item(key = "history_header") {
                    Text(
                        text = localizedLabel("RIWAYAT SESI (${sessions.size})", "SESSION HISTORY (${sessions.size})"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

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
private fun PerAppRecordToggleScreen(
    recordedPackages: Set<String>,
    onToggleApp: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<RecordableAppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allApps = SessionStatsRepository.loadRecordableApps(context)
    }

    val filteredApps = remember(allApps, query, recordedPackages) {
        val q = query.trim().lowercase(Locale.getDefault())
        allApps.filter {
            q.isEmpty() || it.appName.lowercase(Locale.getDefault()).contains(q) ||
                it.packageName.lowercase(Locale.getDefault()).contains(q)
        }.sortedWith(
            compareByDescending<RecordableAppEntry> { it.packageName in recordedPackages }
                .thenByDescending { it.isInGameList }
                .thenBy { it.appName.lowercase(Locale.getDefault()) }
        )
    }

    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)
    val bottomPadding = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = localizedLabel("Toggle Rekam Per App", "Per-App Record Toggles"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = null)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "search_bar") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    shape = CircleShape,
                    placeholder = {
                        Text(localizedLabel("Cari aplikasi atau paket...", "Search app or package..."))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(filteredApps, key = { it.packageName }) { app ->
                val isEnabled = app.packageName in recordedPackages
                val appIcon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            drawableToImageBitmap(context.packageManager.getApplicationIcon(app.packageName))
                        }.getOrNull()
                    }
                }

                Surface(
                    shape = RoundedCornerShape(innerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(innerRadius))
                        .clickable { onToggleApp(app.packageName, !isEnabled) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon!!,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SportsEsports,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (app.isInGameList) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "GAME",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked -> onToggleApp(app.packageName, checked) }
                        )
                    }
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
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
                            text = "Average FPS",
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
                            text = "${session.avgPowerMw} mW",
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
    isMockData: Boolean,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit
) {
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)
    val bottomPadding = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var scrubbedIndex by remember { mutableIntStateOf(-1) }

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
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExportCsv) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = localizedLabel("Simpan CSV", "Export CSV"),
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
            item(key = "session_meta_card") {
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
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = session.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val modeTag = if (isMockData) "${session.stopReason} · sample" else session.stopReason
                        Text(
                            text = "duration ${formatClockDuration(session.durationSec)} · profile ${session.profile} · display ${session.displayHz} Hz · stopped: $modeTag",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "eight_metrics_grid") {
                val deltaTemp = session.peakBatteryTempC - session.startBatteryTempC
                val deltaFormatted = String.format(
                    Locale.US,
                    "Δ %s%.1f",
                    if (deltaTemp >= 0f) "+" else "",
                    deltaTemp
                )

                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.DeveloperBoard,
                    headerTitle = localizedLabel("Ringkasan Telemetri Sesi", "Session Telemetry Summary")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SessionDetailTile(
                            icon = Icons.Rounded.Speed,
                            label = "Average FPS",
                            value = String.format(Locale.US, "%.1f", session.avgFps),
                            supporting = "Peak ${session.maxFps} FPS",
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        SessionDetailTile(
                            icon = Icons.Rounded.Insights,
                            label = "Min FPS",
                            value = "${session.minFps}",
                            supporting = String.format(Locale.US, "StdDev %.2f ms", session.framePacingStdDevMs),
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SessionDetailTile(
                            icon = Icons.Rounded.Timeline,
                            label = "1% low",
                            value = String.format(Locale.US, "%.0f", session.onePercentLowFps),
                            supporting = "99th percentile",
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        SessionDetailTile(
                            icon = Icons.Rounded.Timeline,
                            label = "0.1% low",
                            value = String.format(Locale.US, "%.0f", session.zeroPointOnePercentLowFps),
                            supporting = "Worst frame floor",
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SessionDetailTile(
                            icon = Icons.Rounded.CheckCircle,
                            label = "Jank (frames)",
                            value = "${session.jankCount}",
                            supporting = localizedLabel("Frame melewati target", "Missed frame budget"),
                            accent = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        SessionDetailTile(
                            icon = Icons.Rounded.Thermostat,
                            label = "Max CPU temp",
                            value = String.format(Locale.US, "%.1f °C", session.maxCpuTempC),
                            supporting = localizedLabel("Suhu puncak SoC", "Peak SoC junction"),
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SessionDetailTile(
                            icon = Icons.Rounded.Thermostat,
                            label = "Battery temp ($deltaFormatted)",
                            value = String.format(Locale.US, "%.0f → %.0f °C", session.startBatteryTempC, session.peakBatteryTempC),
                            supporting = localizedLabel("Awal → Puncak", "Start → Peak"),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        SessionDetailTile(
                            icon = Icons.Rounded.Bolt,
                            label = "Average power",
                            value = "${session.avgPowerMw} mW",
                            supporting = String.format(Locale.US, "%.2f W rata-rata", session.avgPowerMw / 1000f),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (session.samples.size >= 2) {
                item(key = "interactive_fps_chart") {
                    val avgColor = MaterialTheme.colorScheme.primary
                    val minColor = MaterialTheme.colorScheme.tertiary
                    val activePoint = session.samples.getOrNull(scrubbedIndex)

                    NestedTweakCardGroup(
                        headerIcon = Icons.Rounded.Timeline,
                        headerTitle = "FPS"
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
                                Text(
                                    text = localizedLabel(
                                        "Whole session · sentuh atau geser grafik untuk detail",
                                        "Whole session · touch or drag the chart for details"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (activePoint != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
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
                                                text = formatClockDuration(activePoint.offsetSec),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = String.format(
                                                    Locale.US,
                                                    "Avg %.0f · Min %.0f FPS · CPU %.0f°C · Bat %.1f°C",
                                                    activePoint.avgFps,
                                                    activePoint.minFps,
                                                    activePoint.cpuTempC,
                                                    activePoint.batteryTempC
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
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ChartLegendItem(
                                            color = avgColor,
                                            title = "Average",
                                            value = "avg ${session.avgFps.roundToInt()} FPS",
                                            dashed = false
                                        )
                                        ChartLegendItem(
                                            color = minColor,
                                            title = "Min",
                                            value = "min ${session.minFps} FPS",
                                            dashed = false
                                        )
                                        ChartLegendItem(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            title = "Refresh rate",
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
                                        .height(172.dp)
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
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(
    color: Color,
    title: String,
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
                text = title,
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
                .height(172.dp)
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
                .height(172.dp)
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

            // Horizontal reference grid lines
            for (step in 0..2) {
                val y = size.height * (0.06f + step * 0.44f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Refresh rate dashed line
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

@Composable
private fun SessionDetailTile(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)
    Surface(
        shape = RoundedCornerShape(innerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
