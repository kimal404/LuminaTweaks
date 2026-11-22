package com.luminatweaks.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.DisplaySettings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.luminatweaks.data.GameBarQuickApp
import com.luminatweaks.data.GameBarSide
import com.luminatweaks.data.GameBarUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private data class GameBarProfile(
    val value: String,
    val label: String,
    val icon: ImageVector
)

private data class GameBarNavTab(
    val label: String,
    val icon: ImageVector
)

private val gameBarProfiles = listOf(
    GameBarProfile("Eco", "Eco", Icons.Rounded.Memory),
    GameBarProfile("Balanced", "Balanced", Icons.Rounded.Tune),
    GameBarProfile("Maestro", "Maestro", Icons.Rounded.Bolt),
    GameBarProfile("Performance", "Performance", Icons.Rounded.Speed)
)

private val gameBarNavTabs = listOf(
    GameBarNavTab("Performa", Icons.Rounded.Speed),
    GameBarNavTab("Layar", Icons.Rounded.DisplaySettings),
    GameBarNavTab("Alat", Icons.Rounded.Tune)
)

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
            (fadeIn(tween(130)) + scaleIn(initialScale = 0.96f, animationSpec = tween(130)))
                .togetherWith(
                    fadeOut(tween(80)) + scaleOut(targetScale = 0.98f, animationSpec = tween(80))
                )
        },
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
            EdgeTriggerPill(
                side = current.side,
                onExpand = onExpand
            )
        }
    }
}

@Composable
private fun EdgeTriggerPill(
    side: GameBarSide,
    onExpand: () -> Unit
) {
    val isLeft = side == GameBarSide.LEFT
    val swipeThreshold = with(LocalDensity.current) { 10.dp.toPx() }
    var targetAlpha by remember { mutableFloatStateOf(0.46f) }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(400),
        label = "edgePillAlpha"
    )

    LaunchedEffect(Unit) {
        delay(3_000L)
        targetAlpha = 0.22f
    }

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
            },
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .width(4.5.dp)
                .height(42.dp)
                .graphicsLayer { alpha = animatedAlpha },
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(0.8.dp, Color.Black.copy(alpha = 0.35f)),
            shadowElevation = 2.dp
        ) {}
    }
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
    var selectedTab by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
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
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            GameBarHeader(onCollapse = onCollapse)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> PerformanceTabContent(
                        state = state,
                        onProfileSelected = onProfileSelected
                    )
                    1 -> DisplayAudioTabContent(
                        state = state,
                        onRefreshRateSelected = onRefreshRateSelected,
                        onBrightnessChanged = onBrightnessChanged,
                        onVolumeChanged = onVolumeChanged
                    )
                    else -> ToolsTabContent(
                        state = state,
                        onToggleFpsOverlay = onToggleFpsOverlay,
                        onToggleLiteMode = onToggleLiteMode,
                        onToggleDnd = onToggleDnd,
                        onOpenManager = onOpenManager,
                        onQuickAppClick = onQuickAppClick
                    )
                }
            }

            GameBarBottomNav(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it }
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
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = "Lumina Game",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Surface(
            onClick = onCollapse,
            modifier = Modifier.size(24.dp),
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
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PerformanceTabContent(
    state: GameBarUiState,
    onProfileSelected: (String) -> Unit
) {
    val cpuLoad = (state.cpuPercent ?: 0).coerceIn(0, 100)
    val gpuLoad = (state.gpuPercent ?: 0).coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            ExpressiveRadialGauge(
                percent = cpuLoad,
                label = "CPU",
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            ExpressiveRadialGauge(
                percent = gpuLoad,
                label = "GPU",
                activeColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }
        InfoStrip(state)
        ProfileGrid(
            profile = state.profile,
            onProfileSelected = onProfileSelected
        )
    }
}

@Composable
private fun ExpressiveRadialGauge(
    percent: Int,
    label: String,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val animatedPercent by animateFloatAsState(
        targetValue = percent.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "gaugePercent"
    )
    val inactiveStrokeColor = scheme.surfaceContainerHighest
    val stopDotColor = scheme.onSurfaceVariant.copy(alpha = 0.65f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHighest.copy(alpha = 0.55f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .width(74.dp)
                    .aspectRatio(140f / 118f)
            ) {
                val scaleX = size.width / 140f
                val scaleY = size.height / 118f
                val s = min(scaleX, scaleY)

                val cx = size.width / 2f
                val cy = 62f * s
                val radius = 48f * s
                val strokeW = 11f * s

                val totalSweepLen = 217.82f
                val gapLength = 14f
                val clamped = animatedPercent.coerceIn(4f, 96f)
                val activeLen = max(4f, (clamped / 100f) * totalSweepLen - gapLength / 2f)
                val inactiveLen = max(4f, ((100f - clamped) / 100f) * totalSweepLen - gapLength / 2f)
                val inactiveStartLen = activeLen + gapLength

                val activeSweepDeg = (activeLen / totalSweepLen) * 240f
                val inactiveSweepDeg = (inactiveLen / totalSweepLen) * 240f
                val inactiveStartDeg = 150f + (inactiveStartLen / totalSweepLen) * 240f
                val pillAngle = -120f + (clamped / 100f) * 240f

                val arcTopLeft = Offset(cx - radius, cy - radius)
                val arcSize = Size(radius * 2f, radius * 2f)

                drawArc(
                    color = inactiveStrokeColor,
                    startAngle = inactiveStartDeg,
                    sweepAngle = inactiveSweepDeg,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                drawArc(
                    color = activeColor,
                    startAngle = 150f,
                    sweepAngle = activeSweepDeg,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
                rotate(degrees = 120f, pivot = Offset(cx, cy)) {
                    drawCircle(
                        color = stopDotColor,
                        radius = 2.2f * s,
                        center = Offset(cx, cy - radius)
                    )
                }
                rotate(degrees = pillAngle, pivot = Offset(cx, cy)) {
                    val pillW = 4.5f * s
                    val pillH = 13f * s
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(cx - pillW / 2f, cy - radius - pillH / 2f),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(2.2f * s, 2.2f * s)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$percent",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeColor,
                        modifier = Modifier.padding(start = 1.dp, bottom = 1.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = label,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
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
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Thermostat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = state.tempLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Rounded.BatteryFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${state.batteryPercent}%",
                fontSize = 10.sp,
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
        modifier = modifier.height(33.dp),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
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
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = profile.label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DisplayAudioTabContent(
    state: GameBarUiState,
    onRefreshRateSelected: (Int?) -> Unit,
    onBrightnessChanged: (Int) -> Unit,
    onVolumeChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        RefreshRateCard(
            modes = state.rrModes,
            currentId = state.currentRrId,
            liveHz = state.targetHz,
            onSelect = onRefreshRateSelected
        )
        GameBarSlider(
            icon = Icons.Rounded.Brightness6,
            value = state.brightnessPercent,
            onValueChange = onBrightnessChanged
        )
        GameBarSlider(
            icon = Icons.Rounded.VolumeUp,
            value = state.volumePercent,
            onValueChange = onVolumeChanged
        )
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
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Refresh rate",
                    modifier = Modifier.weight(1f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "$liveHz Hz",
                    fontSize = 10.5.sp,
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
                    modifier = Modifier.widthIn(min = 48.dp),
                    onClick = { onSelect(null) }
                )
                modes.forEach { hz ->
                    RateCapsule(
                        label = "$hz Hz",
                        selected = currentId != null && currentId == hz,
                        modifier = Modifier.widthIn(min = 50.dp),
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
        modifier = modifier.height(25.dp),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
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
                .height(32.dp)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
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
                modifier = Modifier.width(30.dp),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ToolsTabContent(
    state: GameBarUiState,
    onToggleFpsOverlay: () -> Unit,
    onToggleLiteMode: () -> Unit,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit,
    onQuickAppClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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
                label = "Manager",
                active = false,
                modifier = Modifier.weight(1f),
                onClick = onOpenManager
            )
        }
        if (state.quickApps.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                state.quickApps.forEach { app ->
                    QuickAppButton(
                        app = app,
                        onClick = { onQuickAppClick(app.packageName) }
                    )
                }
            }
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
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(13.dp),
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
                .padding(horizontal = 8.dp),
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
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
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
private fun QuickAppButton(
    app: GameBarQuickApp,
    onClick: () -> Unit
) {
    val icon = rememberAppIcon(app.packageName)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
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
                    modifier = Modifier.size(23.dp)
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(Locale.ROOT),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GameBarBottomNav(
    selectedTab: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            gameBarNavTabs.forEachIndexed { index, tab ->
                val selected = selectedTab == index
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tab.label,
                            fontSize = 9.5.sp,
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
