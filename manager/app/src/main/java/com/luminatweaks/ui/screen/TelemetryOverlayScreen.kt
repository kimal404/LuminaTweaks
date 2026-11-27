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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.luminatweaks.ui.component.luminaHazeHeader
import com.luminatweaks.R
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.component.rememberSliderHaptics
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class FloatingFpsConfig(
    val enabled: Boolean = false,
    val autoGame: Boolean = true,
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
            autoGame = sp.getBoolean("auto_game", true),
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
            .putBoolean("auto_game", config.autoGame)
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

    fun loadPosition(context: Context): Pair<Int, Int>? {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!sp.contains("pos_x") || !sp.contains("pos_y")) return null
        return sp.getInt("pos_x", 0) to sp.getInt("pos_y", 0)
    }

    fun savePosition(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt("pos_x", x)
            .putInt("pos_y", y)
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
    val profileLabel: String = "Balanced"
)

@Composable
private fun rememberSimulatedFpsTelemetry(
    targetHz: Int,
    intervalMs: Int,
    active: Boolean = true
): SimulatedFpsTelemetry {
    val safeTarget = targetHz.coerceAtLeast(60)
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(active, intervalMs, safeTarget) {
        if (!active) return@LaunchedEffect
        while (isActive) {
            delay(intervalMs.toLong())
            tick += 1
        }
    }

    val pattern = intArrayOf(0, 0, -1, 0, 0, -2, 0, 0, -1, 0, 0, 0)
    val delta = pattern[tick % pattern.size]
    val currentFps = (safeTarget + delta).coerceIn(1, safeTarget)
    val currentMs = 1000f / currentFps.toFloat()

    return SimulatedFpsTelemetry(
        fps = currentFps,
        targetHz = safeTarget,
        frametimeMs = currentMs
    )
}

@Composable
private fun FpsViewportStage(
    config: FloatingFpsConfig,
    telemetry: SimulatedFpsTelemetry
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clipToBounds()
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 18.dp.toPx()
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
                .align(Alignment.Center)
                .horizontalScroll(rememberScrollState())
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
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = config.bgAlpha)
    val textColor = MaterialTheme.colorScheme.onSurface

    val items = remember(config, telemetry) {
        buildList {
            if (config.showFrametime) add(String.format(Locale.US, "%.1f ms", telemetry.frametimeMs))
            if (config.showProfile) add(telemetry.profileLabel)
            if (config.showCpuCluster) add(telemetry.cpuClusterLabel)
            if (config.showTemp) add(telemetry.tempLabel)
            if (config.showRam) add(telemetry.ramLabel)
        }
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (config.showFps) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${telemetry.fps}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "FPS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor.copy(alpha = 0.85f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            items.forEachIndexed { index, itemText ->
                if (index > 0 || config.showFps) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(10.dp)
                            .background(textColor.copy(alpha = 0.22f))
                    )
                }
                Text(
                    text = itemText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
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
    var scaleDraft by remember(config.scale) { mutableFloatStateOf(config.scale) }
    var alphaDraft by remember(config.bgAlpha) { mutableFloatStateOf(config.bgAlpha) }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier.luminaHazeHeader(
                    hazeState = hazeState,
                    surfaceColor = MaterialTheme.colorScheme.background,
                    overlappedFraction = scrollBehavior.state.overlappedFraction
                )
            ) {
                TopAppBar(
                    navigationIcon = {
                        LuminaBackButton(
                            onBack = onBack,
                            contentDescription = stringResource(R.string.setup_back)
                        )
                    },
                    title = {
                        Text(
                            text = "Floating HUD",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = 28.dp + navInset,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "group_main") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(connectedContainerShape(index = 0, count = 2))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                            .padding(12.dp)
                    ) {
                        FpsViewportStage(
                            config = config,
                            telemetry = telemetry
                        )
                    }

                    HudSwitchRow(
                        index = 1,
                        count = 2,
                        icon = Icons.Rounded.Speed,
                        title = localizedLabel("Aktifkan Floating HUD", "Enable Floating HUD"),
                        subtitle = if (config.enabled) {
                            localizedLabel("Overlay aktif di atas layar", "Overlay active on screen")
                        } else {
                            localizedLabel("Nonaktif", "Disabled")
                        },
                        checked = config.enabled,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onConfigChange(config.copy(enabled = enabled))
                        }
                    )
                }
            }

            item(key = "group_behavior") {
                HudSwitchRow(
                    index = 0,
                    count = 1,
                    icon = Icons.Rounded.SportsEsports,
                    title = localizedLabel("Deteksi Game Otomatis", "Automatic Game Detection"),
                    subtitle = localizedLabel(
                        "Muncul otomatis saat membuka game di daftar Lumina",
                        "Show automatically when launching games in Lumina list"
                    ),
                    checked = config.autoGame,
                    onCheckedChange = { autoGame ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onConfigChange(config.copy(autoGame = autoGame))
                    }
                )
            }

            item(key = "group_metrics") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(connectedContainerShape(index = 0, count = 4))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HudSquircleIcon(icon = Icons.Rounded.Palette)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = localizedLabel("Metrik Tampil", "Visible Metrics"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                label = { Text("Frametime") }
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

                    HudSliderRow(
                        index = 1,
                        count = 4,
                        label = localizedLabel("Skala HUD", "HUD Scale"),
                        valueText = "${(scaleDraft * 100).roundToInt()}%",
                        value = scaleDraft,
                        valueRange = 0.8f..1.3f,
                        onValueChange = { scaleDraft = it },
                        onValueChangeFinished = {
                            onConfigChange(config.copy(scale = scaleDraft))
                        }
                    )

                    HudSliderRow(
                        index = 2,
                        count = 4,
                        label = localizedLabel("Opasitas Latar", "Background Opacity"),
                        valueText = "${(alphaDraft * 100).roundToInt()}%",
                        value = alphaDraft,
                        valueRange = 0.25f..1.0f,
                        onValueChange = { alphaDraft = it },
                        onValueChangeFinished = {
                            onConfigChange(config.copy(bgAlpha = alphaDraft))
                        }
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(connectedContainerShape(index = 3, count = 4))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localizedLabel("Interval Sampling", "Sampling Interval"),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${config.intervalMs} ms",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        val intervals = listOf(250, 500, 1000)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                        fontWeight = FontWeight.SemiBold,
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

@Composable
private fun HudSquircleIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun HudSwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            HudSquircleIcon(icon = icon)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier.padding(start = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource,
                thumbContent = {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun HudSliderRow(
    index: Int,
    count: Int,
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val shape = connectedContainerShape(index = index, count = count)
    val sliderHaptics = rememberSliderHaptics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = valueText,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Slider(
            value = value,
            onValueChange = { raw ->
                sliderHaptics(raw, valueRange, 0)
                onValueChange(raw)
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange
        )
    }
}
