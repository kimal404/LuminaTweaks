package com.luminatweaks.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.luminatweaks.data.GameBarQuickApp
import com.luminatweaks.data.GameBarSide
import com.luminatweaks.data.GameBarUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class GameBarProfile(
    val value: String,
    val label: String,
    val icon: ImageVector
)

private val gameBarProfiles = listOf(
    GameBarProfile("Eco", "Eco", Icons.Rounded.Memory),
    GameBarProfile("Balanced", "Balanced", Icons.Rounded.Tune),
    GameBarProfile("Maestro", "Maestro", Icons.Rounded.Bolt),
    GameBarProfile("Performance", "Performance", Icons.Rounded.Speed)
)

private val edgeGestureWidth = 14.dp

@Composable
fun GameBarRoot(
    state: State<GameBarUiState>,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onBrightnessChanged: (Int) -> Unit,
    onQuickAppClick: (String) -> Unit,
    onToggleFpsOverlay: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleLiteMode: () -> Unit,
    onOpenManager: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit
) {
    val current by state
    AnimatedContent(
        targetState = current.expanded,
        transitionSpec = {
            (fadeIn(tween(140)) + scaleIn(initialScale = 0.96f, animationSpec = tween(140)))
                .togetherWith(
                    fadeOut(tween(90)) + scaleOut(targetScale = 0.98f, animationSpec = tween(90))
                )
        },
        modifier = Modifier.fillMaxSize(),
        label = "gameBarMode"
    ) { expanded ->
        if (expanded) {
            GameBarPanel(
                state = current,
                onCollapse = onCollapse,
                onProfileSelected = onProfileSelected,
                onVolumeChanged = onVolumeChanged,
                onBrightnessChanged = onBrightnessChanged,
                onQuickAppClick = onQuickAppClick,
                onToggleFpsOverlay = onToggleFpsOverlay,
                onToggleDnd = onToggleDnd,
                onToggleLiteMode = onToggleLiteMode,
                onOpenManager = onOpenManager,
                onRefreshRateSelected = onRefreshRateSelected
            )
        } else {
            EdgeSwipeGesture(
                side = current.side,
                onExpand = onExpand
            )
        }
    }
}

@Composable
private fun EdgeSwipeGesture(
    side: GameBarSide,
    onExpand: () -> Unit
) {
    val isLeft = side == GameBarSide.LEFT
    val swipeThreshold = with(LocalDensity.current) { edgeGestureWidth.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onExpand() })
            }
            .pointerInput(isLeft, swipeThreshold) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                    },
                    onDragEnd = {
                        val inward = if (isLeft) totalX else -totalX
                        if (abs(totalX) > abs(totalY) && inward > swipeThreshold) {
                            onExpand()
                        }
                    },
                    onDragCancel = {
                        totalX = 0f
                        totalY = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                }
            }
    )
}

@Composable
private fun GameBarPanel(
    state: GameBarUiState,
    onCollapse: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onBrightnessChanged: (Int) -> Unit,
    onQuickAppClick: (String) -> Unit,
    onToggleFpsOverlay: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleLiteMode: () -> Unit,
    onOpenManager: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val cardHeight = minOf(312.dp, (screenHeight - 24.dp).coerceAtLeast(220.dp))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = state.opacity.coerceIn(0.40f, 1f)
        ),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            GameBarHeader(onCollapse = onCollapse)
            MetricGaugeRow(state)
            InfoStrip(state)
            ProfileGrid(
                profile = state.profile,
                onProfileSelected = onProfileSelected
            )
            RefreshRateCard(
                modes = state.rrModes,
                currentId = state.currentRrId,
                liveHz = state.targetHz,
                onSelect = onRefreshRateSelected
            )
            QuickSliders(
                brightness = state.brightnessPercent,
                volume = state.volumePercent,
                onBrightnessChanged = onBrightnessChanged,
                onVolumeChanged = onVolumeChanged
            )
            QuickTools(
                state = state,
                onToggleFpsOverlay = onToggleFpsOverlay,
                onToggleLiteMode = onToggleLiteMode,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
            QuickAppsRow(
                apps = state.quickApps,
                onQuickAppClick = onQuickAppClick
            )
        }
    }
}

@Composable
private fun GameBarHeader(onCollapse: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(28.dp))
        Text(
            text = "Lumina Game",
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Surface(
            onClick = onCollapse,
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup Game Bar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricGaugeRow(state: GameBarUiState) {
    val fps = state.fps.coerceAtLeast(0)
    val targetHz = state.targetHz.coerceAtLeast(1)
    val cpuLoad = (state.cpuPercent ?: 0).coerceIn(0, 100)
    val gpuLoad = (state.gpuPercent ?: 0).coerceIn(0, 100)
    val fpsProgress = (fps.toFloat() / targetHz.toFloat()).coerceIn(0f, 1f)
    val frametime = if (state.frametimeMs > 0f) {
        String.format(Locale.US, "%.1f ms", state.frametimeMs)
    } else {
        null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularMetricGauge(
            size = 68.dp,
            progress = cpuLoad / 100f,
            primaryText = "$cpuLoad%",
            label = "CPU",
            highlighted = false
        )
        CircularMetricGauge(
            size = 84.dp,
            progress = fpsProgress,
            primaryText = if (fps > 0) fps.toString() else "--",
            label = "FPS",
            sublabel = frametime,
            highlighted = true
        )
        CircularMetricGauge(
            size = 68.dp,
            progress = gpuLoad / 100f,
            primaryText = "$gpuLoad%",
            label = "GPU",
            highlighted = false
        )
    }
}

@Composable
private fun CircularMetricGauge(
    size: Dp,
    progress: Float,
    primaryText: String,
    label: String,
    sublabel: String? = null,
    highlighted: Boolean
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val accentColor = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = (if (highlighted) 7.dp else 5.dp).toPx()
            val arcSize = Size(
                width = this.size.width - strokeWidth,
                height = this.size.height - strokeWidth
            )
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = primaryText,
                fontSize = if (highlighted) 24.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = label,
                fontSize = if (highlighted) 9.sp else 8.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            if (!sublabel.isNullOrBlank()) {
                Text(
                    text = sublabel,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun InfoStrip(state: GameBarUiState) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Thermostat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = state.tempLabel,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(11.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Icon(
                imageVector = Icons.Rounded.BatteryFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${state.batteryPercent}%",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ProfileGrid(
    profile: String,
    onProfileSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        gameBarProfiles.chunked(2).forEach { rowProfiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                rowProfiles.forEach { item ->
                    ProfileButton(
                        profile = item,
                        selected = profileMatches(profile, item.value),
                        modifier = Modifier.weight(1f),
                        onClick = { onProfileSelected(item.value) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileButton(
    profile: GameBarProfile,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = profile.icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = profile.label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RefreshRateCard(
    modes: List<Int>,
    currentId: Int?,
    liveHz: Int,
    onSelect: (Int?) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Refresh rate",
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "$liveHz Hz",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                RateCapsule(
                    label = "Auto",
                    selected = currentId == null,
                    modifier = Modifier.widthIn(min = 52.dp),
                    onClick = { onSelect(null) }
                )
                modes.forEach { hz ->
                    RateCapsule(
                        label = "$hz Hz",
                        selected = currentId != null && currentId == hz,
                        modifier = Modifier.widthIn(min = 54.dp),
                        onClick = { onSelect(hz) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RateCapsule(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(26.dp),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun QuickSliders(
    brightness: Int,
    volume: Int,
    onBrightnessChanged: (Int) -> Unit,
    onVolumeChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GameBarSlider(
            icon = Icons.Rounded.Brightness6,
            value = brightness,
            onValueChange = onBrightnessChanged
        )
        GameBarSlider(
            icon = Icons.Rounded.VolumeUp,
            value = volume,
            onValueChange = onVolumeChanged
        )
    }
}

@Composable
private fun GameBarSlider(
    icon: ImageVector,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Slider(
                value = value.coerceIn(0, 100).toFloat(),
                onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 100)) },
                valueRange = 0f..100f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
            )
            Text(
                text = "$value%",
                modifier = Modifier.width(32.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickTools(
    state: GameBarUiState,
    onToggleFpsOverlay: () -> Unit,
    onToggleLiteMode: () -> Unit,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolCard(
                icon = Icons.Rounded.Speed,
                label = "FPS Meter",
                active = state.fpsOverlay,
                modifier = Modifier.weight(1f),
                onClick = onToggleFpsOverlay
            )
            ToolCard(
                icon = Icons.Rounded.Bolt,
                label = "Mode Lite",
                active = state.liteMode,
                modifier = Modifier.weight(1f),
                onClick = onToggleLiteMode
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolCard(
                icon = Icons.Rounded.NotificationsOff,
                label = "Jangan Ganggu",
                active = state.dndEnabled,
                modifier = Modifier.weight(1f),
                onClick = onToggleDnd
            )
            ToolCard(
                icon = Icons.Rounded.Apps,
                label = "Lumina Manager",
                active = false,
                modifier = Modifier.weight(1f),
                onClick = onOpenManager
            )
        }
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
        },
        border = if (active) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickAppsRow(
    apps: List<GameBarQuickApp>,
    onQuickAppClick: (String) -> Unit
) {
    if (apps.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Quick Apps",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            apps.forEach { app ->
                QuickAppButton(
                    app = app,
                    onClick = { onQuickAppClick(app.packageName) }
                )
            }
        }
    }
}

@Composable
private fun QuickAppButton(
    app: GameBarQuickApp,
    onClick: () -> Unit
) {
    val icon = rememberAppIcon(app.packageName)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(Locale.ROOT),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun profileMatches(current: String, option: String): Boolean {
    val value = current.lowercase(Locale.ROOT)
    return when (option) {
        "Eco" -> value.contains("eco") || value.contains("power")
        "Balanced" -> value.contains("balanc")
        "Maestro" -> value.contains("maestro")
        "Performance" -> value.contains("perform")
        else -> value == option.lowercase(Locale.ROOT)
    }
}

@Composable
private fun rememberAppIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName.isNullOrBlank()) {
            null
        } else {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(96, 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
}
