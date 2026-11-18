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

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.luminatweaks.data.ActiveSessionMonitor
import com.luminatweaks.data.ActiveSessionPoint
import com.luminatweaks.data.ActiveSessionSnapshot
import com.luminatweaks.data.SessionDeviceContext
import com.luminatweaks.data.SessionDeviceInfo
import com.luminatweaks.ui.theme.LocalCornerRadius
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private enum class TelemetrySeries(val label: String) {
    FPS("FPS"),
    TEMP("TEMP(°C)"),
    CPU("CPU(%)"),
    GPU("GPU(%)")
}

private data class AppBadge(val icon: ImageBitmap?, val label: String)

@Composable
internal fun ActiveSessionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val localView = LocalView.current

    var snapshot by remember { mutableStateOf(ActiveSessionSnapshot()) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val monitor = remember { ActiveSessionMonitor(context) }
    val device = remember { SessionDeviceInfo.read(context) }
    val refreshHz = remember {
        localView.display?.refreshRate?.roundToInt()?.coerceIn(24, 240) ?: 60
    }
    val idleLabel = localizedLabel("Aplikasi aktif", "Active app")
    val badge by produceState(initialValue = AppBadge(null, ""), snapshot.packageName) {
        val pkg = snapshot.packageName
        value = if (pkg == null) {
            AppBadge(null, idleLabel)
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val manager = context.packageManager
                    val icon = drawableToImageBitmap(manager.getApplicationIcon(pkg))
                    val label = manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString()
                    AppBadge(icon, label)
                }.getOrDefault(AppBadge(null, pkg))
            }
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            snapshot = withContext(Dispatchers.IO) { monitor.poll() }
            delay(POLL_INTERVAL_MS)
        }
    }

    BackHandler(onBack = onBack)

    val outerRadius = LocalCornerRadius.current.coerceAtLeast(22.dp)
    val bottomPadding = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier.onSizeChanged { viewSize = it },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = localizedLabel("Sesi Aktif", "Active Session"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        LiveBadge(active = snapshot.gameActive)
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
            item(key = "device_context") {
                DeviceContextSection(device = device)
            }

            item(key = "active_summary") {
                ActiveSummaryCard(
                    snapshot = snapshot,
                    badge = badge,
                    viewSize = viewSize,
                    outerRadius = outerRadius
                )
            }

            item(key = "telemetry_chart") {
                TelemetryChartCard(
                    points = snapshot.points,
                    elapsedSec = snapshot.elapsedSec
                )
            }

            item(key = "frametime_chart") {
                FrameTimeChartCard(
                    points = snapshot.points,
                    elapsedSec = snapshot.elapsedSec,
                    refreshHz = refreshHz
                )
            }
        }
    }
}

@Composable
private fun LiveBadge(active: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Text(
            text = if (active) "LIVE" else "IDLE",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun DeviceContextSection(device: SessionDeviceContext) {
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(22.dp)

    Surface(
        shape = RoundedCornerShape(outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ContextColumn(
                    icon = Icons.Rounded.DeveloperBoard,
                    label = "Platform",
                    value = device.platform,
                    modifier = Modifier.weight(1f)
                )
                ContextColumn(
                    icon = Icons.Rounded.Smartphone,
                    label = "Model",
                    value = device.model,
                    modifier = Modifier.weight(1f)
                )
                ContextColumn(
                    icon = Icons.Rounded.Android,
                    label = "OS",
                    value = device.osVersion,
                    modifier = Modifier.weight(1f)
                )
                ContextColumn(
                    icon = Icons.Rounded.Speed,
                    label = "Profile",
                    value = device.profile.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ContextColumn(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActiveSummaryCard(
    snapshot: ActiveSessionSnapshot,
    badge: AppBadge,
    viewSize: IntSize,
    outerRadius: androidx.compose.ui.unit.Dp
) {
    val innerRadius = (LocalCornerRadius.current * 0.75f).coerceIn(12.dp, 20.dp)
    val startText = snapshot.startedAtMs?.let { startedAt ->
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(startedAt))
    } ?: "--:--:--"

    val metrics = listOf(
        SummaryMetric("MAX", formatFps(snapshot.maxFps)),
        SummaryMetric("MIN", formatFps(snapshot.minFps)),
        SummaryMetric("AVG", formatFps(snapshot.avgFps)),
        SummaryMetric("VARIANCE", String.format(Locale.US, "%.1f", snapshot.varianceFps)),
        SummaryMetric("≥45FPS", String.format(Locale.US, "%.0f%%", snapshot.smoothnessPct)),
        SummaryMetric("5% LOW", snapshot.fivePercentLowFps?.let(::formatFps) ?: "N/A"),
        SummaryMetric("MAX", snapshot.maxTempC?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A"),
        SummaryMetric("AVG", snapshot.avgPowerW?.let { String.format(Locale.US, "%.2f W", it) } ?: "N/A")
    )

    Surface(
        shape = RoundedCornerShape(outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val iconBitmap = badge.icon
                if (iconBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = iconBitmap,
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
                        text = badge.label.ifBlank {
                            localizedLabel("Target sesi", "Session target")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$startText · ${formatElapsed(snapshot.elapsedSec)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "view: ${viewSize.width}×${viewSize.height}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            for (row in metrics.chunked(4)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (metric in row) {
                        MetricTile(
                            metric = metric,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(innerRadius)
                        )
                    }
                }
            }
        }
    }
}

private data class SummaryMetric(val label: String, val value: String)

@Composable
private fun MetricTile(
    metric: SummaryMetric,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TelemetryChartCard(
    points: List<ActiveSessionPoint>,
    elapsedSec: Int
) {
    val innerRadius = (LocalCornerRadius.current * 0.75f).coerceIn(14.dp, 22.dp)
    val fpsColor = MaterialTheme.colorScheme.primary
    val tempColor = MaterialTheme.colorScheme.tertiary
    val cpuColor = MaterialTheme.colorScheme.secondary
    val gpuColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    var visible by remember {
        mutableStateOf(TelemetrySeries.entries.toSet())
    }

    NestedTweakCardGroup(
        headerIcon = Icons.Rounded.Timeline,
        headerTitle = localizedLabel("Grafik Telemetri", "Telemetry Chart")
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
                if (points.size < 2) {
                    ChartPlaceholder(
                        height = 176.dp,
                        text = localizedLabel(
                            "Menunggu data sesi aktif…",
                            "Waiting for an active session…"
                        )
                    )
                } else {
                    MultiSeriesChart(
                        points = points,
                        visible = visible,
                        fpsColor = fpsColor,
                        tempColor = tempColor,
                        cpuColor = cpuColor,
                        gpuColor = gpuColor,
                        gridColor = gridColor,
                        axisColor = axisColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                    )
                    ChartTimeLabels(elapsedSec = elapsedSec)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SeriesChip(
                        series = TelemetrySeries.FPS,
                        color = fpsColor,
                        selected = TelemetrySeries.FPS in visible,
                        onToggle = {
                            visible = if (TelemetrySeries.FPS in visible) {
                                visible - TelemetrySeries.FPS
                            } else {
                                visible + TelemetrySeries.FPS
                            }
                        }
                    )
                    SeriesChip(
                        series = TelemetrySeries.TEMP,
                        color = tempColor,
                        selected = TelemetrySeries.TEMP in visible,
                        onToggle = {
                            visible = if (TelemetrySeries.TEMP in visible) {
                                visible - TelemetrySeries.TEMP
                            } else {
                                visible + TelemetrySeries.TEMP
                            }
                        }
                    )
                    SeriesChip(
                        series = TelemetrySeries.CPU,
                        color = cpuColor,
                        selected = TelemetrySeries.CPU in visible,
                        onToggle = {
                            visible = if (TelemetrySeries.CPU in visible) {
                                visible - TelemetrySeries.CPU
                            } else {
                                visible + TelemetrySeries.CPU
                            }
                        }
                    )
                    SeriesChip(
                        series = TelemetrySeries.GPU,
                        color = gpuColor,
                        selected = TelemetrySeries.GPU in visible,
                        onToggle = {
                            visible = if (TelemetrySeries.GPU in visible) {
                                visible - TelemetrySeries.GPU
                            } else {
                                visible + TelemetrySeries.GPU
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesChip(
    series: TelemetrySeries,
    color: Color,
    selected: Boolean,
    onToggle: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = {
            Text(
                text = series.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (selected) color else color.copy(alpha = 0.35f))
            )
        }
    )
}

@Composable
private fun ChartPlaceholder(height: androidx.compose.ui.unit.Dp, text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartTimeLabels(elapsedSec: Int) {
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
            text = formatElapsed(elapsedSec / 2),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatElapsed(elapsedSec),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MultiSeriesChart(
    points: List<ActiveSessionPoint>,
    visible: Set<TelemetrySeries>,
    fpsColor: Color,
    tempColor: Color,
    cpuColor: Color,
    gpuColor: Color,
    gridColor: Color,
    axisColor: Color,
    modifier: Modifier = Modifier
) {
    val step = max(1, points.size / MAX_CHART_POINTS)
    val sampled = remember(points, step) {
        if (step == 1) {
            points
        } else {
            points.filterIndexed { index, _ -> index % step == 0 || index == points.lastIndex }
        }
    }
    val leftLabels = listOf("120", "90", "60", "30")
    val rightLabels = listOf("45.0°", "41.7°", "38.3°", "35.0°")

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(0.12f),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            for (label in leftLabels) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = axisColor
                )
            }
        }

        Canvas(
            modifier = Modifier
                .weight(0.76f)
                .fillMaxSize()
                .padding(horizontal = 6.dp)
        ) {
            if (sampled.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas

            val pad = 4.dp.toPx()
            val usableHeight = (size.height - pad * 2f).coerceAtLeast(1f)
            val lastSec = sampled.last().elapsedSec
            val totalSec = max(1, lastSec)

            fun xAt(index: Int, point: ActiveSessionPoint): Float {
                val fraction = if (lastSec <= 1) {
                    index.toFloat() / (sampled.size - 1).toFloat()
                } else {
                    point.elapsedSec.toFloat() / totalSec.toFloat()
                }
                return (fraction.coerceIn(0f, 1f)) * size.width
            }

            fun yFraction(normalized: Float): Float {
                return pad + usableHeight * (1f - normalized.coerceIn(0f, 1f))
            }

            for (row in 0..3) {
                val y = pad + usableHeight * (row / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            fun seriesPath(
                value: (ActiveSessionPoint) -> Float?,
                normalize: (Float) -> Float
            ): Path? {
                val path = Path()
                var started = false
                sampled.forEachIndexed { index, point ->
                    val raw = value(point) ?: return@forEachIndexed
                    val x = xAt(index, point)
                    val y = yFraction(normalize(raw))
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                }
                return if (started) path else null
            }

            if (TelemetrySeries.FPS in visible) {
                val path = seriesPath({ it.fps }) { ((it - 30f) / 90f) }
                if (path != null) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(xAt(sampled.lastIndex, sampled.last()), size.height)
                        lineTo(xAt(0, sampled.first()), size.height)
                        close()
                    }
                    drawPath(
                        path = fill,
                        brush = Brush.verticalGradient(
                            colors = listOf(fpsColor.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = path,
                        color = fpsColor,
                        style = Stroke(
                            width = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            if (TelemetrySeries.TEMP in visible) {
                seriesPath({ it.tempC }) { ((it - 35f) / 10f) }?.let { path ->
                    drawPath(
                        path = path,
                        color = tempColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            if (TelemetrySeries.CPU in visible) {
                seriesPath({ it.cpuPct }) { it / 100f }?.let { path ->
                    drawPath(
                        path = path,
                        color = cpuColor.copy(alpha = 0.9f),
                        style = Stroke(
                            width = 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            if (TelemetrySeries.GPU in visible) {
                seriesPath({ it.gpuPct }) { it / 100f }?.let { path ->
                    drawPath(
                        path = path,
                        color = gpuColor.copy(alpha = 0.85f),
                        style = Stroke(
                            width = 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(0.12f),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            for (label in rightLabels) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = axisColor
                )
            }
        }
    }
}

@Composable
private fun FrameTimeChartCard(
    points: List<ActiveSessionPoint>,
    elapsedSec: Int,
    refreshHz: Int
) {
    val innerRadius = (LocalCornerRadius.current * 0.75f).coerceIn(14.dp, 22.dp)
    val lineColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    NestedTweakCardGroup(
        headerIcon = Icons.Rounded.Speed,
        headerTitle = localizedLabel("Frame Time (ms)", "Frame Time (ms)")
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
                if (points.size < 2) {
                    ChartPlaceholder(
                        height = 150.dp,
                        text = localizedLabel(
                            "Belum ada sampel frame time",
                            "No frame time samples yet"
                        )
                    )
                } else {
                    FrameTimeChart(
                        points = points,
                        refreshHz = refreshHz,
                        lineColor = lineColor,
                        gridColor = gridColor,
                        axisColor = axisColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                    ChartTimeLabels(elapsedSec = elapsedSec)
                }
            }
        }
    }
}

@Composable
private fun FrameTimeChart(
    points: List<ActiveSessionPoint>,
    refreshHz: Int,
    lineColor: Color,
    gridColor: Color,
    axisColor: Color,
    modifier: Modifier = Modifier
) {
    val step = max(1, points.size / MAX_CHART_POINTS)
    val sampled = remember(points, step) {
        if (step == 1) {
            points
        } else {
            points.filterIndexed { index, _ -> index % step == 0 || index == points.lastIndex }
        }
    }
    val observedMax = sampled.maxOfOrNull { it.frametimeMs } ?: 0f
    val ceiling = max(33.4f, observedMax.coerceAtMost(200f)) * 1.12f
    val budgetMs = 1_000f / refreshHz.coerceAtLeast(24).toFloat()
    val midLabel = String.format(Locale.US, "%.1f", ceiling / 2f)
    val maxLabel = String.format(Locale.US, "%.1f", ceiling)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(0.14f),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            for (label in listOf(maxLabel, midLabel, "0")) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = axisColor
                )
            }
        }

        Canvas(
            modifier = Modifier
                .weight(0.86f)
                .fillMaxSize()
                .padding(start = 6.dp)
        ) {
            if (sampled.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas

            val pad = 4.dp.toPx()
            val usableHeight = (size.height - pad * 2f).coerceAtLeast(1f)
            val lastSec = sampled.last().elapsedSec
            val totalSec = max(1, lastSec)

            fun xAt(index: Int, point: ActiveSessionPoint): Float {
                val fraction = if (lastSec <= 1) {
                    index.toFloat() / (sampled.size - 1).toFloat()
                } else {
                    point.elapsedSec.toFloat() / totalSec.toFloat()
                }
                return fraction.coerceIn(0f, 1f) * size.width
            }

            fun yAt(ms: Float): Float {
                return pad + usableHeight * (1f - (ms / ceiling).coerceIn(0f, 1f))
            }

            for (row in 0..2) {
                val y = pad + usableHeight * (row / 2f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (budgetMs <= ceiling) {
                val y = yAt(budgetMs)
                drawLine(
                    color = gridColor.copy(alpha = 0.9f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
                )
            }

            val path = Path()
            sampled.forEachIndexed { index, point ->
                val x = xAt(index, point)
                val y = yAt(point.frametimeMs)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(xAt(sampled.lastIndex, sampled.last()), size.height)
                lineTo(xAt(0, sampled.first()), size.height)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.15f), Color.Transparent)
                )
            )
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private fun formatFps(value: Float): String {
    return if (value.isFinite() && value > 0f) {
        String.format(Locale.US, "%.1f", value)
    } else {
        "0.0"
    }
}

private fun formatElapsed(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}

private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int = 96): ImageBitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap.asImageBitmap()
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}

private const val POLL_INTERVAL_MS = 1_000L
private const val MAX_CHART_POINTS = 240