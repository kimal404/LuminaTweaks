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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.theme.LocalCornerRadius
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class FpsOverlayStyle(val key: String) {
    PILL("pill"),
    GRAPH("graph"),
    MINIMAL("minimal");

    companion object {
        fun fromKey(raw: String?): FpsOverlayStyle =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: PILL
    }
}

enum class FpsOverlayCorner(val key: String, val alignment: Alignment) {
    TOP_LEFT("top_left", Alignment.TopStart),
    TOP_RIGHT("top_right", Alignment.TopEnd),
    BOTTOM_LEFT("bottom_left", Alignment.BottomStart),
    BOTTOM_RIGHT("bottom_right", Alignment.BottomEnd);

    companion object {
        fun fromKey(raw: String?): FpsOverlayCorner =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: TOP_RIGHT
    }
}

data class FloatingFpsConfig(
    val enabled: Boolean = false,
    val style: FpsOverlayStyle = FpsOverlayStyle.PILL,
    val corner: FpsOverlayCorner = FpsOverlayCorner.TOP_RIGHT,
    val autoGame: Boolean = true,
    val touchLock: Boolean = false,
    val showFps: Boolean = true,
    val showFrametime: Boolean = true,
    val showCpuCluster: Boolean = false,
    val showTemp: Boolean = false,
    val showRam: Boolean = false,
    val showProfile: Boolean = false,
    val scale: Float = 1.0f,
    val bgAlpha: Float = 0.88f,
    val intervalMs: Int = 500
) {
    val activeModuleCount: Int
        get() = listOf(
            showFps,
            showFrametime,
            showCpuCluster,
            showTemp,
            showRam,
            showProfile
        ).count { it }
}

object FloatingFpsPrefs {
    private const val PREF_NAME = "floating_fps_prefs"

    fun load(context: Context): FloatingFpsConfig {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return FloatingFpsConfig(
            enabled = sp.getBoolean("enabled", false),
            style = FpsOverlayStyle.fromKey(sp.getString("style", FpsOverlayStyle.PILL.key)),
            corner = FpsOverlayCorner.fromKey(sp.getString("corner", FpsOverlayCorner.TOP_RIGHT.key)),
            autoGame = sp.getBoolean("auto_game", true),
            touchLock = sp.getBoolean("touch_lock", false),
            showFps = sp.getBoolean("show_fps", true),
            showFrametime = sp.getBoolean("show_frametime", true),
            showCpuCluster = sp.getBoolean("show_cpu_cluster", false),
            showTemp = sp.getBoolean("show_temp", false),
            showRam = sp.getBoolean("show_ram", false),
            showProfile = sp.getBoolean("show_profile", false),
            scale = sp.getFloat("scale", 1.0f).coerceIn(0.8f, 1.3f),
            bgAlpha = sp.getFloat("bg_alpha", 0.88f).coerceIn(0.25f, 1.0f),
            intervalMs = sp.getInt("interval_ms", 500).takeIf { it in listOf(250, 500, 1000) } ?: 500
        )
    }

    fun save(context: Context, config: FloatingFpsConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", config.enabled)
            .putString("style", config.style.key)
            .putString("corner", config.corner.key)
            .putBoolean("auto_game", config.autoGame)
            .putBoolean("touch_lock", config.touchLock)
            .putBoolean("show_fps", config.showFps)
            .putBoolean("show_frametime", config.showFrametime)
            .putBoolean("show_cpu_cluster", config.showCpuCluster)
            .putBoolean("show_temp", config.showTemp)
            .putBoolean("show_ram", config.showRam)
            .putBoolean("show_profile", config.showProfile)
            .putFloat("scale", config.scale)
            .putFloat("bg_alpha", config.bgAlpha)
            .putInt("interval_ms", config.intervalMs)
            .apply()
    }
}

private data class SimulatedFpsTelemetry(
    val fps: Int = 120,
    val targetHz: Int = 120,
    val frametimeMs: Float = 8.3f,
    val cpuClusterLabel: String = "3.2 GHz",
    val tempLabel: String = "38.1°C",
    val ramLabel: String = "54%",
    val profileLabel: String = "Balanced",
    val history: List<Float> = listOf(8.3f, 8.4f, 8.2f, 8.3f, 8.5f, 8.3f, 8.2f, 8.3f, 8.4f, 8.3f, 8.2f, 8.3f)
)

@Composable
private fun rememberSimulatedFpsTelemetry(
    targetHz: Int,
    intervalMs: Int,
    active: Boolean = true
): SimulatedFpsTelemetry {
    val safeTarget = targetHz.coerceAtLeast(60)
    val baseFrameMs = 1000f / safeTarget.toFloat()
    var tick by remember { mutableIntStateOf(0) }
    var history by remember(safeTarget) {
        mutableStateOf(List(16) { baseFrameMs })
    }

    LaunchedEffect(active, intervalMs, safeTarget) {
        if (!active) return@LaunchedEffect
        val pattern = intArrayOf(0, 0, -1, 0, 0, -2, 0, 0, -1, 0, 0, 0)
        while (isActive) {
            delay(intervalMs.toLong())
            val delta = pattern[tick % pattern.size]
            val currentFps = (safeTarget + delta).coerceAtLeast(1)
            val currentMs = (1000f / currentFps.toFloat())
            history = (history.drop(1) + currentMs)
            tick += 1
        }
    }

    val latestMs = history.lastOrNull() ?: baseFrameMs
    val latestFps = (1000f / latestMs).roundToInt().coerceIn(1, safeTarget)
    return SimulatedFpsTelemetry(
        fps = latestFps,
        targetHz = safeTarget,
        frametimeMs = latestMs,
        history = history
    )
}

@Composable
internal fun fpsStyleLabel(style: FpsOverlayStyle): String = when (style) {
    FpsOverlayStyle.PILL -> localizedLabel("Kapsul", "Pill")
    FpsOverlayStyle.GRAPH -> localizedLabel("Grafik", "Graph")
    FpsOverlayStyle.MINIMAL -> localizedLabel("Minimalis", "Minimal")
}

@Composable
internal fun fpsCornerLabel(corner: FpsOverlayCorner): String = when (corner) {
    FpsOverlayCorner.TOP_LEFT -> localizedLabel("Kiri Atas", "Top Left")
    FpsOverlayCorner.TOP_RIGHT -> localizedLabel("Kanan Atas", "Top Right")
    FpsOverlayCorner.BOTTOM_LEFT -> localizedLabel("Kiri Bawah", "Bottom Left")
    FpsOverlayCorner.BOTTOM_RIGHT -> localizedLabel("Kanan Bawah", "Bottom Right")
}

@Composable
private fun FpsViewportStage(
    config: FloatingFpsConfig,
    telemetry: SimulatedFpsTelemetry,
    heightDp: Int
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clipToBounds()
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 20.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        Box(
            modifier = Modifier
                .align(config.corner.alignment)
                .graphicsLayer {
                    scaleX = config.scale
                    scaleY = config.scale
                }
        ) {
            FpsOverlayPreviewBadge(
                config = config,
                telemetry = telemetry
            )
        }
    }
}

@Composable
private fun FpsOverlayPreviewBadge(
    config: FloatingFpsConfig,
    telemetry: SimulatedFpsTelemetry
) {
    val ratio = telemetry.fps.toFloat() / telemetry.targetHz.coerceAtLeast(1).toFloat()
    val targetStatusColor = when {
        ratio >= 0.90f -> MaterialTheme.colorScheme.primary
        ratio >= 0.65f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val statusColor by animateColorAsState(
        targetValue = targetStatusColor,
        label = "fpsStatusColor"
    )
    val isMinimal = config.style == FpsOverlayStyle.MINIMAL
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
        alpha = if (isMinimal) (config.bgAlpha * 0.78f).coerceIn(0.25f, 0.92f) else config.bgAlpha
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isMinimal) 0.25f else 0.45f)
    val textColor = MaterialTheme.colorScheme.onSurface

    val lines = remember(config, telemetry) {
        buildList {
            if (config.showFrametime) add(String.format(Locale.US, "%.1f ms", telemetry.frametimeMs))
            if (config.showProfile) add(telemetry.profileLabel)
            if (config.showCpuCluster) add(telemetry.cpuClusterLabel)
            if (config.showTemp) add(telemetry.tempLabel)
            if (config.showRam) add(telemetry.ramLabel)
        }
    }

    val cardShape = RoundedCornerShape(if (isMinimal) 14.dp else 18.dp)

    Surface(
        shape = cardShape,
        color = containerColor,
        modifier = Modifier.border(1.dp, outlineColor, cardShape)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (isMinimal) 9.dp else 11.dp,
                vertical = if (isMinimal) 7.dp else 9.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (isMinimal) 2.dp else 4.dp)
        ) {
            if (config.showFps) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(if (isMinimal) 6.dp else 7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${telemetry.fps}",
                        style = if (isMinimal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        maxLines = 1
                    )
                }
            }

            if (config.style == FpsOverlayStyle.GRAPH) {
                FrametimeSparkline(
                    samples = telemetry.history,
                    lineColor = statusColor,
                    modifier = Modifier
                        .width(68.dp)
                        .height(18.dp)
                )
            }

            lines.forEach { itemText ->
                Text(
                    text = itemText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FrametimeSparkline(
    samples: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas
        val minVal = (samples.minOrNull() ?: 8f) - 1.5f
        val maxVal = (samples.maxOrNull() ?: 16f) + 1.5f
        val span = (maxVal - minVal).coerceAtLeast(1f)
        val stepX = size.width / (samples.size - 1).toFloat()

        val linePath = Path()
        val fillPath = Path()
        samples.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = ((value - minVal) / span).coerceIn(0f, 1f)
            val y = size.height * (1f - normalized * 0.82f - 0.09f)
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
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TelemetryOverlayScreen(
    config: FloatingFpsConfig,
    targetHz: Int,
    onConfigChange: (FloatingFpsConfig) -> Unit,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val telemetry = rememberSimulatedFpsTelemetry(
        targetHz = targetHz,
        intervalMs = config.intervalMs,
        active = true
    )
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Floating HUD",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 6.dp)
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.setup_back),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
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
                bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "preview_stage") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.Speed,
                    headerTitle = localizedLabel("Pratinjau & Gaya", "Preview & Style")
                ) {
                    FpsViewportStage(
                        config = config,
                        telemetry = telemetry,
                        heightDp = 156
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FpsOverlayStyle.entries.forEachIndexed { index, style ->
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                FpsOverlayStyle.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            ToggleButton(
                                checked = config.style == style,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onConfigChange(config.copy(style = style))
                                },
                                shapes = shapes,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = fpsStyleLabel(style),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    val previewContext = LocalContext.current
                    val previewScope = rememberCoroutineScope()

                    NestedTweakSwitchItem(
                        icon = Icons.Rounded.Speed,
                        title = localizedLabel("Aktifkan Floating HUD", "Enable Floating HUD"),
                        subtitle = if (config.enabled) {
                            localizedLabel("Overlay aktif di layar", "Overlay active on screen")
                        } else {
                            localizedLabel("Overlay nonaktif", "Overlay disabled")
                        },
                        checked = config.enabled,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onConfigChange(config.copy(enabled = enabled))
                        }
                    )

                    NestedTweakActionItem(
                        icon = Icons.Rounded.Widgets,
                        title = localizedLabel("Tes Overlay 5 Detik", "Test Overlay 5 Seconds"),
                        subtitle = localizedLabel(
                            "Tampilkan pratinjau langsung di atas aplikasi selama 5 detik",
                            "Show live floating overlay above apps for 5 seconds"
                        ),
                        onClick = {
                            previewScope.launch {
                                if (FloatingFpsService.ensureOverlayPermission(previewContext)) {
                                    FloatingFpsService.triggerPreviewBurst(previewContext)
                                }
                            }
                        }
                    )
                }
            }

            item(key = "automation_position") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.SportsEsports,
                    headerTitle = localizedLabel("Otomatisasi & Posisi", "Automation & Position")
                ) {
                    NestedTweakSwitchItem(
                        icon = Icons.Rounded.SportsEsports,
                        title = localizedLabel("Auto-Muncul di Game", "Auto-Show in Games"),
                        subtitle = localizedLabel(
                            "Otomatis aktif saat membuka paket di daftar game",
                            "Automatically appear when launching games in game list"
                        ),
                        checked = config.autoGame,
                        onCheckedChange = { autoGame ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onConfigChange(config.copy(autoGame = autoGame))
                        }
                    )

                    NestedTweakSwitchItem(
                        icon = Icons.Rounded.Lock,
                        title = localizedLabel("Kunci Sentuhan (Touch Pass-Through)", "Touch Pass-Through Lock"),
                        subtitle = localizedLabel(
                            "Sentuhan menembus overlay agar tidak mengganggu tombol game",
                            "Let touches pass through so overlay never blocks game controls"
                        ),
                        checked = config.touchLock,
                        onCheckedChange = { touchLock ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onConfigChange(config.copy(touchLock = touchLock))
                        }
                    )

                    Surface(
                        shape = RoundedCornerShape(innerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = localizedLabel("Posisi Sudut Awal", "Initial Corner Position"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FpsOverlayCorner.entries.forEach { corner ->
                                    FilterChip(
                                        selected = config.corner == corner,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onConfigChange(config.copy(corner = corner))
                                        },
                                        label = {
                                            Text(
                                                text = fpsCornerLabel(corner),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "appearance_metrics") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.Palette,
                    headerTitle = localizedLabel("Tampilan & Metrik", "Appearance & Metrics")
                ) {
                    Surface(
                        shape = RoundedCornerShape(innerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = localizedLabel("Modul Telemetri", "Telemetry Modules"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = config.showFps,
                                    onClick = {
                                        if (!config.showFps || config.activeModuleCount > 1) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onConfigChange(config.copy(showFps = !config.showFps))
                                        }
                                    },
                                    label = { Text("FPS") }
                                )
                                FilterChip(
                                    selected = config.showFrametime,
                                    onClick = {
                                        if (!config.showFrametime || config.activeModuleCount > 1) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onConfigChange(config.copy(showFrametime = !config.showFrametime))
                                        }
                                    },
                                    label = { Text("Frametime (ms)") }
                                )
                                FilterChip(
                                    selected = config.showCpuCluster,
                                    onClick = {
                                        if (!config.showCpuCluster || config.activeModuleCount > 1) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onConfigChange(config.copy(showCpuCluster = !config.showCpuCluster))
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.DeveloperBoard,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("CPU") }
                                )
                                FilterChip(
                                    selected = config.showTemp,
                                    onClick = {
                                        if (!config.showTemp || config.activeModuleCount > 1) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onConfigChange(config.copy(showTemp = !config.showTemp))
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Thermostat,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(localizedLabel("Suhu", "Temp")) }
                                )
                                FilterChip(
                                    selected = config.showRam,
                                    onClick = {
                                        if (!config.showRam || config.activeModuleCount > 1) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onConfigChange(config.copy(showRam = !config.showRam))
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Memory,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("RAM") }
                                )
                                FilterChip(
                                    selected = config.showProfile,
                                    onClick = {
                                        if (!config.showProfile || config.activeModuleCount > 1) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onConfigChange(config.copy(showProfile = !config.showProfile))
                                        }
                                    },
                                    label = { Text(localizedLabel("Profil", "Profile")) }
                                )
                            }
                        }
                    }

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
                            FpsSliderRow(
                                label = localizedLabel("Skala Ukuran", "Overlay Scale"),
                                valueText = "${(config.scale * 100).roundToInt()}%",
                                value = config.scale,
                                valueRange = 0.8f..1.3f,
                                onValueChange = { onConfigChange(config.copy(scale = it)) }
                            )

                            FpsSliderRow(
                                label = localizedLabel("Opasitas Latar", "Background Opacity"),
                                valueText = "${(config.bgAlpha * 100).roundToInt()}%",
                                value = config.bgAlpha,
                                valueRange = 0.25f..1.0f,
                                onValueChange = { onConfigChange(config.copy(bgAlpha = it)) }
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = localizedLabel("Interval Pembaruan", "Refresh Interval"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${config.intervalMs} ms",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                val intervals = listOf(250, 500, 1000)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    intervals.forEachIndexed { index, ms ->
                                        val shapes = when (index) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            intervals.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        }
                                        ToggleButton(
                                            checked = config.intervalMs == ms,
                                            onCheckedChange = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onConfigChange(config.copy(intervalMs = ms))
                                            },
                                            shapes = shapes,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "$ms ms",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
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
    }
}

@Composable
private fun FpsSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}
