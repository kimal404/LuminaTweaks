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

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private data class CpuClusterInfo(
    val index: Int,
    val curKhz: Long,
    val maxKhz: Long
) {
    val curGhzText: String
        get() = String.format(Locale.US, "%.2f GHz", curKhz / 1_000_000f)

    val maxGhzText: String
        get() = if (maxKhz > 0L) String.format(Locale.US, "%.2f GHz", maxKhz / 1_000_000f) else "--"

    val ratio: Float
        get() = if (maxKhz > 0L) (curKhz.toFloat() / maxKhz.toFloat()).coerceIn(0.05f, 1f) else 0f
}

private data class MonitorSnapshot(
    val fps: Int = 0,
    val frametimeMs: Float = 0f,
    val gameActive: Boolean = false,
    val targetHz: Int = 120,
    val profile: String = "Balanced",
    val batteryTempText: String = "--°C",
    val batteryTempC: Float = 36f,
    val powerWattsText: String = "-- W",
    val currentMaText: String = "-- mA",
    val voltageVoltsText: String = "-- V",
    val chargingStateText: String = "Idle",
    val clusters: List<CpuClusterInfo> = emptyList(),
    val ramUsedText: String = "-- / -- GB",
    val ramRatio: Float = 0f,
    val zramUsedText: String = "--",
    val frametimeHistory: List<Float> = List(20) { 8.3f }
)

private fun monitorSysProp(key: String): String? {
    return try {
        val spClass = Class.forName("android.os.SystemProperties")
        val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
        val value = getMethod.invoke(null, key, "") as? String
        value?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

private data class RawFpsFeed(
    val fps: Float,
    val epochMs: Long,
    val frametimeMs: Float,
    val gameActive: Boolean
)

private fun parseRawFpsFeed(raw: String?): RawFpsFeed? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.trim().split(Regex("\s+"))
    if (parts.size < 2) return null
    val fps = parts[0].toFloatOrNull() ?: return null
    val epochMs = parts[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
    val ft = parts.getOrNull(2)?.toFloatOrNull() ?: if (fps > 0f) 1000f / fps else 0f
    val active = parts.getOrNull(3)?.let { it == "1" || it.equals("true", true) } ?: (fps > 0f)
    return RawFpsFeed(fps, epochMs, ft, active)
}

@Suppress("DEPRECATION")
private fun readMonitorDisplayHz(context: Context): Int {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { context.display }.getOrNull() ?: wm?.defaultDisplay
    } else {
        wm?.defaultDisplay
    }
    return (display?.refreshRate?.roundToInt() ?: 60).coerceAtLeast(30)
}

private fun sampleMonitorSnapshot(
    context: Context,
    previousHistory: List<Float>
): MonitorSnapshot {
    val now = System.currentTimeMillis()
    val candidates = ArrayList<RawFpsFeed>(3)
    parseRawFpsFeed(monitorSysProp("sys.lumina.fps"))?.let { candidates.add(it) }
    val appMirror = File(context.filesDir, "lumina.fps").absolutePath
    for (path in listOf(appMirror, "/data/local/tmp/lumina.fps", "/data/adb/.config/lumina/fps.txt")) {
        try {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                parseRawFpsFeed(f.readText())?.let { candidates.add(it) }
            }
        } catch (_: Exception) {}
    }

    val latestFeed = candidates.maxByOrNull { it.epochMs }
    val isFresh = latestFeed != null && now - latestFeed.epochMs <= 1_500L
    val liveFps = if (isFresh && latestFeed != null && latestFeed.fps > 0f) {
        latestFeed.fps.roundToInt().coerceIn(1, 240)
    } else {
        0
    }
    val liveFt = if (liveFps > 0 && latestFeed != null) {
        latestFeed.frametimeMs.takeIf { it > 0f } ?: (1000f / liveFps.toFloat())
    } else {
        0f
    }
    val gameActive = isFresh && latestFeed?.gameActive == true && liveFps > 0
    val nextHistory = if (liveFt > 0f) {
        (previousHistory.drop(1) + liveFt.coerceIn(1f, 200f))
    } else {
        previousHistory
    }

    val tempText = DeviceUtil.getBatteryTemperature(context)
    val tempNumeric = tempText.replace("°C", "").trim().toFloatOrNull() ?: 36f
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val rawCurrent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
    val batIntent = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()
    val rawVoltage = batIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
    val statusCode = batIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        ?: BatteryManager.BATTERY_STATUS_UNKNOWN

    val statusLabel = when (statusCode) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
        BatteryManager.BATTERY_STATUS_FULL -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Bypass / Idle"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        else -> "Active"
    }

    var currentMaText = "-- mA"
    var voltageVoltsText = "-- V"
    var powerWattsText = "-- W"

    val amps: Double? = if (rawCurrent != Int.MIN_VALUE && rawCurrent != 0) {
        val absVal = abs(rawCurrent.toLong())
        val a = if (absVal >= 10_000L) absVal / 1_000_000.0 else absVal / 1_000.0
        currentMaText = "${(a * 1000.0).roundToInt()} mA"
        a
    } else {
        null
    }

    val volts: Double? = if (rawVoltage > 0) {
        val v = if (rawVoltage > 100_000) rawVoltage / 1_000_000.0 else rawVoltage / 1_000.0
        voltageVoltsText = String.format(Locale.US, "%.2f V", v)
        v
    } else {
        null
    }

    if (amps != null && volts != null) {
        val watts = amps * volts
        if (watts in 0.05..180.0) {
            powerWattsText = String.format(Locale.US, "%.2f W", watts)
        }
    }

    val clusters = try {
        val base = File("/sys/devices/system/cpu/cpufreq")
        val policies = base.listFiles { file ->
            file.isDirectory && file.name.startsWith("policy")
        }?.sortedBy { file ->
            file.name.removePrefix("policy").toIntOrNull() ?: Int.MAX_VALUE
        }.orEmpty()

        policies.mapIndexedNotNull { idx, dir ->
            val cur = runCatching {
                File(dir, "scaling_cur_freq").takeIf { it.canRead() }?.readText()?.trim()
                    ?: File(dir, "cpuinfo_cur_freq").takeIf { it.canRead() }?.readText()?.trim()
            }.getOrNull()?.toLongOrNull()?.takeIf { it > 0L } ?: return@mapIndexedNotNull null

            val maxFreq = runCatching {
                File(dir, "cpuinfo_max_freq").takeIf { it.canRead() }?.readText()?.trim()
                    ?: File(dir, "scaling_max_freq").takeIf { it.canRead() }?.readText()?.trim()
            }.getOrNull()?.toLongOrNull() ?: cur

            CpuClusterInfo(index = idx, curKhz = cur, maxKhz = max(maxFreq, cur))
        }
    } catch (_: Exception) {
        emptyList()
    }

    var ramUsedText = "-- / -- GB"
    var ramRatio = 0f
    try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        if (am != null) {
            am.getMemoryInfo(info)
            if (info.totalMem > 0L) {
                val usedBytes = (info.totalMem - info.availMem).coerceAtLeast(0L)
                val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
                val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
                ramRatio = (usedBytes.toFloat() / info.totalMem.toFloat()).coerceIn(0f, 1f)
                ramUsedText = String.format(Locale.US, "%.1f / %.1f GB (%d%%)", usedGb, totalGb, (ramRatio * 100f).roundToInt())
            }
        }
    } catch (_: Exception) {}

    var zramUsedText = "--"
    try {
        val meminfo = File("/proc/meminfo")
        if (meminfo.canRead()) {
            var swapTotalKb = 0L
            var swapFreeKb = 0L
            meminfo.forEachLine { line ->
                when {
                    line.startsWith("SwapTotal:") ->
                        swapTotalKb = line.filter(Char::isDigit).toLongOrNull() ?: 0L
                    line.startsWith("SwapFree:") ->
                        swapFreeKb = line.filter(Char::isDigit).toLongOrNull() ?: 0L
                }
            }
            if (swapTotalKb > 0L) {
                val usedKb = (swapTotalKb - swapFreeKb).coerceAtLeast(0L)
                zramUsedText = String.format(
                    Locale.US,
                    "%.1f / %.1f GB",
                    usedKb / (1024.0 * 1024.0),
                    swapTotalKb / (1024.0 * 1024.0)
                )
            }
        }
    } catch (_: Exception) {}

    return MonitorSnapshot(
        fps = liveFps,
        frametimeMs = liveFt,
        gameActive = gameActive,
        targetHz = readMonitorDisplayHz(context),
        profile = monitorSysProp("sys.lumina.profile") ?: "Balanced",
        batteryTempText = tempText,
        batteryTempC = tempNumeric,
        powerWattsText = powerWattsText,
        currentMaText = currentMaText,
        voltageVoltsText = voltageVoltsText,
        chargingStateText = statusLabel,
        clusters = clusters,
        ramUsedText = ramUsedText,
        ramRatio = ramRatio,
        zramUsedText = zramUsedText,
        frametimeHistory = nextHistory
    )
}

@Composable
fun MonitorScreen(
    pagerState: PagerState? = null,
    onOpenOverlaySettings: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isResumed by remember { mutableStateOf(true) }
    var overlayEnabled by remember { mutableStateOf(FloatingFpsPrefs.load(context).enabled) }
    var snapshot by remember { mutableStateOf(MonitorSnapshot()) }

    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        if (owner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        isResumed = true
                        overlayEnabled = FloatingFpsPrefs.load(context).enabled
                    }
                    Lifecycle.Event.ON_PAUSE -> isResumed = false
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }

    LaunchedEffect(pagerState?.currentPage, isResumed) {
        val isVisiblePage = pagerState == null || pagerState.currentPage == 2
        if (!isVisiblePage || !isResumed) return@LaunchedEffect
        overlayEnabled = FloatingFpsPrefs.load(context).enabled

        while (isActive) {
            if (pagerState?.isScrollInProgress == true) {
                delay(150L)
                continue
            }
            val next = withContext(Dispatchers.IO) {
                sampleMonitorSnapshot(context, snapshot.frametimeHistory)
            }
            snapshot = next
            delay(1000L)
        }
    }

    val isFloating = LocalFloatingNavBar.current
    val bottomPadding = if (isFloating) {
        108.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = localizedLabel("Monitor", "Monitor"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenOverlaySettings()
                        },
                        shape = CircleShape,
                        color = if (overlayEnabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (overlayEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                            )
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                tint = if (overlayEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Overlay",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (overlayEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "adaptive_hero") {
                MonitorAdaptiveHeroCard(snapshot = snapshot)
            }

            item(key = "power_thermal") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.Thermostat,
                    headerTitle = localizedLabel("Kelistrikan & Termal", "Power & Thermal")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MonitorBentoTile(
                            icon = Icons.Rounded.Thermostat,
                            label = localizedLabel("Suhu Baterai", "Battery Temp"),
                            value = snapshot.batteryTempText,
                            supporting = when {
                                snapshot.batteryTempC >= 43f -> localizedLabel("Tinggi", "High")
                                snapshot.batteryTempC >= 39f -> localizedLabel("Hangat", "Warm")
                                else -> localizedLabel("Optimal", "Optimal")
                            },
                            accentColor = when {
                                snapshot.batteryTempC >= 43f -> MaterialTheme.colorScheme.error
                                snapshot.batteryTempC >= 39f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MonitorBentoTile(
                            icon = Icons.Rounded.Bolt,
                            label = localizedLabel("Daya Riil", "Board Power"),
                            value = snapshot.powerWattsText,
                            supporting = snapshot.chargingStateText,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MonitorBentoTile(
                            icon = Icons.Rounded.BatteryChargingFull,
                            label = localizedLabel("Arus Listrik", "Current"),
                            value = snapshot.currentMaText,
                            supporting = localizedLabel("Laju arus baterai", "Battery current flow"),
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        MonitorBentoTile(
                            icon = Icons.Rounded.Speed,
                            label = localizedLabel("Tegangan", "Voltage"),
                            value = snapshot.voltageVoltsText,
                            supporting = localizedLabel("Potensial sel", "Cell potential"),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item(key = "cpu_memory") {
                val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.DeveloperBoard,
                    headerTitle = localizedLabel("Kluster CPU & Memori", "CPU Clusters & Memory")
                ) {
                    if (snapshot.clusters.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(innerRadius),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = localizedLabel(
                                    "Node cpufreq tidak terbaca pada perangkat ini",
                                    "cpufreq policy nodes unavailable on this device"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        snapshot.clusters.forEach { cluster ->
                            val animatedRatio by animateFloatAsState(
                                targetValue = cluster.ratio,
                                animationSpec = tween(durationMillis = 320),
                                label = "clusterRatio_${cluster.index}"
                            )
                            Surface(
                                shape = RoundedCornerShape(innerRadius),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Cluster ${cluster.index}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${cluster.curGhzText} / ${cluster.maxGhzText}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animatedRatio)
                                                .fillMaxHeight()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val animatedRam by animateFloatAsState(
                        targetValue = snapshot.ramRatio,
                        animationSpec = tween(durationMillis = 320),
                        label = "ramRatio"
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "RAM",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = snapshot.ramUsedText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedRam)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ZRAM Swap",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = snapshot.zramUsedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
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

@Composable
private fun MonitorAdaptiveHeroCard(snapshot: MonitorSnapshot) {
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)
    val isLiveGame = snapshot.gameActive && snapshot.fps > 0

    val containerColor by animateColorAsState(
        targetValue = if (isLiveGame) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "monitorHeroContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isLiveGame) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "monitorHeroContent"
    )

    val waveTransition = rememberInfiniteTransition(label = "monitorWave")
    val wavePhase by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "monitorWavePhase"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(outerRadius),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!isLiveGame) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(30.dp)
                ) {
                    if (size.width <= 0f || size.height <= 0f) return@Canvas
                    val path = Path()
                    val amplitude = size.height * 0.22f
                    val step = size.width / 72f
                    val baseline = size.height * 0.72f
                    var x = 0f
                    while (x <= size.width) {
                        val envelope = sin((x / size.width) * PI.toFloat())
                        val angle = (x / size.width) * 2.2f * 2f * PI.toFloat() + wavePhase * 2f * PI.toFloat()
                        val y = baseline + sin(angle) * amplitude * envelope
                        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                        x += step
                    }
                    drawPath(
                        path = path,
                        color = contentColor.copy(alpha = 0.22f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = if (isLiveGame) 20.dp else 32.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isLiveGame) {
                            localizedLabel("STREAM FRAME AKTIF", "LIVE FRAME STREAM")
                        } else {
                            localizedLabel("RINGKASAN SISTEM", "SYSTEM TELEMETRY")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.75f)
                    )
                    Surface(
                        shape = CircleShape,
                        color = contentColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = snapshot.profile,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                }

                if (isLiveGame) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${snapshot.fps}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FPS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.1f ms", snapshot.frametimeMs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    MonitorSparkline(
                        samples = snapshot.frametimeHistory,
                        lineColor = contentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    )
                } else {
                    val peakCluster = snapshot.clusters.maxByOrNull { it.curKhz }?.curGhzText ?: "-- GHz"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = snapshot.powerWattsText,
                                style = MaterialTheme.typography.headlineSmallEmphasized,
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor,
                                maxLines = 1
                            )
                            Text(
                                text = localizedLabel("Konsumsi Daya", "Board Power"),
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.72f)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = peakCluster,
                                style = MaterialTheme.typography.headlineSmallEmphasized,
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor,
                                maxLines = 1
                            )
                            Text(
                                text = localizedLabel("Frekuensi Puncak CPU", "Peak CPU Clock"),
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.72f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorBentoTile(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String,
    accentColor: Color,
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
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
private fun MonitorSparkline(
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
                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)
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
