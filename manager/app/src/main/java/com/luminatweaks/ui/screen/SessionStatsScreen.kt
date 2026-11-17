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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

data class GameSessionRecord(
    val id: String,
    val packageName: String,
    val appName: String,
    val profile: String,
    val startTimeMs: Long,
    val durationSec: Int,
    val avgFps: Float,
    val onePercentLowFps: Float,
    val minFps: Int,
    val maxFps: Int,
    val stabilityPercent: Int,
    val jankCount: Int,
    val startTempC: Float,
    val peakTempC: Float,
    val avgPowerW: Float,
    val peakCpuGhz: Float,
    val fpsTimeline: List<Int>
)

private data class SessionLoadResult(
    val sessions: List<GameSessionRecord>,
    val isMockData: Boolean
)

private object SessionStatsRepository {
    private const val PREFS_NAME = "lumina_session_stats"
    private const val KEY_AUTO_RECORD = "auto_record_enabled"
    private const val KEY_MOCK_DISMISSED = "mock_dismissed"
    private const val DAEMON_SESSIONS_PATH = "/data/adb/.config/lumina/sessions.json"
    private const val DAEMON_FLAG_PATH = "/data/adb/.config/lumina/session_record.enabled"

    fun isAutoRecordEnabled(context: Context): Boolean {
        val localFlag = File(context.filesDir, "session_record.enabled")
        if (localFlag.exists()) {
            return localFlag.readText().trim() != "0"
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RECORD, true)
    }

    suspend fun setAutoRecordEnabled(context: Context, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_RECORD, enabled)
                .apply()
            val value = if (enabled) "1" else "0"
            runCatching {
                File(context.filesDir, "session_record.enabled").writeText("$value\n")
            }
            runCatching {
                val proc = ProcessBuilder(
                    "su", "-c",
                    "mkdir -p /data/adb/.config/lumina && printf '%s\\n' '$value' > $DAEMON_FLAG_PATH && chmod 644 $DAEMON_FLAG_PATH"
                ).redirectErrorStream(true).start()
                proc.waitFor()
            }
        }
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

    suspend fun deleteSession(context: Context, id: String, current: List<GameSessionRecord>, isMock: Boolean): List<GameSessionRecord> {
        return withContext(Dispatchers.IO) {
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
    }

    suspend fun clearAll(context: Context, isMock: Boolean) {
        withContext(Dispatchers.IO) {
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
    }

    suspend fun restoreMockPreview(context: Context): List<GameSessionRecord> = withContext(Dispatchers.IO) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MOCK_DISMISSED, false)
            .apply()
        defaultMockSessions()
    }

    private fun parseSessionsJson(raw: String): List<GameSessionRecord> {
        val arr = JSONArray(raw.trim().ifEmpty { "[]" })
        val list = ArrayList<GameSessionRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val tlArr = obj.optJSONArray("fpsTimeline")
            val timeline = if (tlArr != null) {
                List(tlArr.length()) { idx -> tlArr.optInt(idx, 60) }
            } else {
                emptyList()
            }
            list.add(
                GameSessionRecord(
                    id = obj.optString("id", i.toString()),
                    packageName = obj.optString("packageName", "unknown.app"),
                    appName = obj.optString("appName", obj.optString("packageName", "Game")),
                    profile = obj.optString("profile", "Balanced"),
                    startTimeMs = obj.optLong("startTimeMs", System.currentTimeMillis()),
                    durationSec = obj.optInt("durationSec", 0),
                    avgFps = obj.optDouble("avgFps", 0.0).toFloat(),
                    onePercentLowFps = obj.optDouble("onePercentLowFps", 0.0).toFloat(),
                    minFps = obj.optInt("minFps", 0),
                    maxFps = obj.optInt("maxFps", 0),
                    stabilityPercent = obj.optInt("stabilityPercent", 0),
                    jankCount = obj.optInt("jankCount", 0),
                    startTempC = obj.optDouble("startTempC", 35.0).toFloat(),
                    peakTempC = obj.optDouble("peakTempC", 38.0).toFloat(),
                    avgPowerW = obj.optDouble("avgPowerW", 0.0).toFloat(),
                    peakCpuGhz = obj.optDouble("peakCpuGhz", 0.0).toFloat(),
                    fpsTimeline = timeline
                )
            )
        }
        return list.sortedByDescending { it.startTimeMs }
    }

    private fun serializeSessionsJson(list: List<GameSessionRecord>): String {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("packageName", item.packageName)
                put("appName", item.appName)
                put("profile", item.profile)
                put("startTimeMs", item.startTimeMs)
                put("durationSec", item.durationSec)
                put("avgFps", item.avgFps.toDouble())
                put("onePercentLowFps", item.onePercentLowFps.toDouble())
                put("minFps", item.minFps)
                put("maxFps", item.maxFps)
                put("stabilityPercent", item.stabilityPercent)
                put("jankCount", item.jankCount)
                put("startTempC", item.startTempC.toDouble())
                put("peakTempC", item.peakTempC.toDouble())
                put("avgPowerW", item.avgPowerW.toDouble())
                put("peakCpuGhz", item.peakCpuGhz.toDouble())
                put("fpsTimeline", JSONArray(item.fpsTimeline))
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun defaultMockSessions(): List<GameSessionRecord> {
        val now = System.currentTimeMillis()
        return listOf(
            GameSessionRecord(
                id = "mock_mlbb_1",
                packageName = "com.mobile.legends",
                appName = "Mobile Legends: Bang Bang",
                profile = "Performance",
                startTimeMs = now - 2_400_000L,
                durationSec = 1480,
                avgFps = 117.6f,
                onePercentLowFps = 94.0f,
                minFps = 82,
                maxFps = 120,
                stabilityPercent = 96,
                jankCount = 9,
                startTempC = 35.4f,
                peakTempC = 40.8f,
                avgPowerW = 4.18f,
                peakCpuGhz = 3.25f,
                fpsTimeline = listOf(119, 120, 118, 120, 119, 117, 115, 118, 120, 116, 114, 117, 119, 118, 116, 118, 119, 117)
            ),
            GameSessionRecord(
                id = "mock_pubg_2",
                packageName = "com.tencent.ig",
                appName = "PUBG Mobile",
                profile = "Performance",
                startTimeMs = now - 14_400_000L,
                durationSec = 1920,
                avgFps = 88.4f,
                onePercentLowFps = 71.0f,
                minFps = 62,
                maxFps = 90,
                stabilityPercent = 91,
                jankCount = 18,
                startTempC = 36.1f,
                peakTempC = 42.3f,
                avgPowerW = 4.82f,
                peakCpuGhz = 3.25f,
                fpsTimeline = listOf(90, 89, 90, 88, 87, 89, 86, 88, 85, 87, 89, 88, 86, 89, 90, 88, 87, 89)
            ),
            GameSessionRecord(
                id = "mock_ppsspp_3",
                packageName = "org.ppsspp.ppsspp",
                appName = "PPSSPP",
                profile = "Balanced",
                startTimeMs = now - 86_400_000L,
                durationSec = 2640,
                avgFps = 59.8f,
                onePercentLowFps = 56.0f,
                minFps = 52,
                maxFps = 60,
                stabilityPercent = 99,
                jankCount = 2,
                startTempC = 34.8f,
                peakTempC = 38.4f,
                avgPowerW = 2.74f,
                peakCpuGhz = 2.60f,
                fpsTimeline = listOf(60, 60, 59, 60, 60, 60, 59, 60, 60, 60, 58, 60, 60, 60, 60, 59, 60, 60)
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

private fun formatDurationLabel(durationSec: Int): String {
    val totalMins = (durationSec / 60).coerceAtLeast(1)
    val hours = totalMins / 60
    val mins = totalMins % 60
    return if (hours > 0) "${hours}j ${mins}m" else "${totalMins} mnt"
}

private fun formatSessionDate(startTimeMs: Long): String {
    val sdf = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault())
    return sdf.format(Date(startTimeMs))
}

@Composable
internal fun SessionStatsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var autoRecord by remember { mutableStateOf(SessionStatsRepository.isAutoRecordEnabled(context)) }
    var sessions by remember { mutableStateOf<List<GameSessionRecord>>(emptyList()) }
    var isMockData by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<GameSessionRecord?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = SessionStatsRepository.loadSessions(context)
        sessions = result.sessions
        isMockData = result.isMockData
    }

    val activeDetail = selectedSession
    if (activeDetail != null) {
        BackHandler { selectedSession = null }
        SessionDetailScreen(
            session = activeDetail,
            isMockData = isMockData,
            onBack = { selectedSession = null },
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
                                    text = "PREVIEW",
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
            item(key = "recorder_control") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.Insights,
                    headerTitle = localizedLabel("Perekam Sesi", "Session Recorder")
                ) {
                    NestedTweakSwitchItem(
                        icon = Icons.Rounded.FiberManualRecord,
                        title = localizedLabel("Rekam Sesi Otomatis", "Auto-Record Sessions"),
                        subtitle = localizedLabel(
                            "Simpan telemetri FPS, suhu & daya saat game berjalan",
                            "Log FPS, thermal & power telemetry while playing games"
                        ),
                        checked = autoRecord,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            autoRecord = enabled
                            scope.launch {
                                SessionStatsRepository.setAutoRecordEnabled(context, enabled)
                            }
                        }
                    )

                    val avgStability = if (sessions.isNotEmpty()) {
                        "${sessions.map { it.stabilityPercent }.average().roundToInt()}%"
                    } else {
                        "--"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(innerRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = "${sessions.size}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = localizedLabel("Total Sesi", "Recorded Sessions"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(innerRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    text = avgStability,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = localizedLabel("Rata-rata Stabil", "Avg Stability"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    "Riwayat otomatis muncul setelah memainkan aplikasi dalam daftar game.",
                                    "History appears automatically after playing apps in your game list."
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
                                Text(localizedLabel("Muat Contoh Data UI", "Load Sample UI Data"))
                            }
                        }
                    }
                }
            } else {
                item(key = "history_header") {
                    Text(
                        text = localizedLabel("RIWAYAT PERMAINAN", "SESSION HISTORY"),
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
                        text = "${formatSessionDate(session.startTimeMs)} · ${formatDurationLabel(session.durationSec)}",
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
                        text = session.profile,
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
                            text = "Avg FPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${session.onePercentLowFps.roundToInt()} FPS",
                            style = MaterialTheme.typography.titleSmall,
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
                            text = String.format(Locale.US, "%.1f°C", session.peakTempC),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = localizedLabel("Suhu Puncak", "Peak Temp"),
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
    onDelete: () -> Unit
) {
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)
    val bottomPadding = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = session.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
            item(key = "detail_hero") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(outerRadius),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatSessionDate(session.startTimeMs)} · ${formatDurationLabel(session.durationSec)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = if (isMockData) "${session.profile} · Preview" else session.profile,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", session.avgFps),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AVG FPS",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${session.onePercentLowFps.roundToInt()} FPS",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "1% Low FPS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                )
                            }
                        }
                    }
                }
            }

            if (session.fpsTimeline.size >= 2) {
                item(key = "timeline_card") {
                    NestedTweakCardGroup(
                        headerIcon = Icons.Rounded.Timeline,
                        headerTitle = localizedLabel("Kurva FPS Sesi", "Session FPS Curve")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(innerRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SessionTimelineChart(
                                    samples = session.fpsTimeline,
                                    lineColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(116.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "0m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Min ${session.minFps} · Max ${session.maxFps} FPS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formatDurationLabel(session.durationSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "metrics_grid") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.DeveloperBoard,
                    headerTitle = localizedLabel("Metrik Perangkat & Frame", "Hardware & Frame Metrics")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SessionDetailTile(
                            icon = Icons.Rounded.Speed,
                            label = localizedLabel("Stabilitas", "Stability"),
                            value = "${session.stabilityPercent}%",
                            supporting = "${session.jankCount} frame drop",
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        SessionDetailTile(
                            icon = Icons.Rounded.Insights,
                            label = localizedLabel("Rentang FPS", "FPS Range"),
                            value = "${session.minFps}–${session.maxFps}",
                            supporting = "Min – Max FPS",
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val deltaTemp = session.peakTempC - session.startTempC
                        val deltaSign = if (deltaTemp >= 0f) "+" else ""
                        SessionDetailTile(
                            icon = Icons.Rounded.Thermostat,
                            label = localizedLabel("Delta Suhu", "Thermal Delta"),
                            value = String.format(Locale.US, "%.1f°C", session.peakTempC),
                            supporting = String.format(Locale.US, "%.1f°C (%s%.1f°C)", session.startTempC, deltaSign, deltaTemp),
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        SessionDetailTile(
                            icon = Icons.Rounded.Bolt,
                            label = localizedLabel("Daya & CPU", "Power & CPU"),
                            value = String.format(Locale.US, "%.2f W", session.avgPowerW),
                            supporting = String.format(Locale.US, "Peak %.2f GHz", session.peakCpuGhz),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
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

@Composable
private fun SessionTimelineChart(
    samples: List<Int>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas
        val minVal = ((samples.minOrNull() ?: 30) - 8).coerceAtLeast(0).toFloat()
        val maxVal = ((samples.maxOrNull() ?: 120) + 6).toFloat()
        val span = (maxVal - minVal).coerceAtLeast(10f)
        val stepX = size.width / (samples.size - 1).toFloat()

        val gridColor = lineColor.copy(alpha = 0.14f)
        for (i in 1..2) {
            val y = size.height * (i / 3f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val linePath = Path()
        val fillPath = Path()
        samples.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = ((value - minVal) / span).coerceIn(0f, 1f)
            val y = size.height * (1f - normalized * 0.84f - 0.08f)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
