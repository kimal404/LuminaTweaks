package com.luminatweaks.gamedeck

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

private data class DeckProfileItem(
    val value: String,
    val label: String,
    val icon: ImageVector
)

private val deckProfiles = listOf(
    DeckProfileItem("Eco", "Eco", Icons.Rounded.Memory),
    DeckProfileItem("Balanced", "Balanced", Icons.Rounded.Tune),
    DeckProfileItem("Performance", "Performance", Icons.Rounded.Speed)
)

@Composable
fun GameDeckRoot(
    state: State<GameDeckUiState>,
    isWindowExpanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    val current by state
    val isLeft = current.side == GameDeckSide.LEFT
    val openProgress by animateFloatAsState(
        targetValue = if (current.expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 175, easing = FastOutSlowInEasing),
        label = "deckOpenProgress"
    )

    if (!isWindowExpanded && openProgress <= 0.01f) {
        EdgeDeckHandle(isLeft = isLeft, onExpand = onExpand)
    } else {
        val slideOffset = with(LocalDensity.current) { 18.dp.toPx() }
        Box(
            modifier = Modifier.graphicsLayer {
                val p = openProgress.coerceIn(0f, 1f)
                alpha = p
                scaleX = 0.93f + 0.07f * p
                scaleY = 0.95f + 0.05f * p
                translationX = if (isLeft) -(1f - p) * slideOffset else (1f - p) * slideOffset
                transformOrigin = TransformOrigin(if (isLeft) 0f else 1f, 0.5f)
            }
        ) {
            GameDeckWorkspace(
                state = current,
                onCollapse = onCollapse,
                onProfileSelected = onProfileSelected,
                onRefreshRateSelected = onRefreshRateSelected,
                onToggleDnd = onToggleDnd,
                onQuickAppClick = onQuickAppClick,
                onOpenManager = onOpenManager
            )
        }
    }
}

@Composable
private fun EdgeDeckHandle(
    isLeft: Boolean,
    onExpand: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val swipeThreshold = with(LocalDensity.current) { 10.dp.toPx() }
    var hinting by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }

    val handleWidth by animateDpAsState(
        targetValue = if (hinting || dragging) 8.dp else 5.dp,
        animationSpec = tween(220),
        label = "deckHandleWidth"
    )
    val handleHeight by animateDpAsState(
        targetValue = if (hinting || dragging) 64.dp else 48.dp,
        animationSpec = tween(220),
        label = "deckHandleHeight"
    )
    val handleAlpha by animateFloatAsState(
        targetValue = if (hinting || dragging) 0.92f else 0.55f,
        animationSpec = tween(300),
        label = "deckHandleAlpha"
    )

    LaunchedEffect(Unit) {
        delay(2_200L)
        hinting = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onExpand() })
            }
            .pointerInput(isLeft, swipeThreshold) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = {
                        dx = 0f
                        dy = 0f
                        dragging = true
                    },
                    onDragEnd = {
                        dragging = false
                        val inward = if (isLeft) dx else -dx
                        if (abs(dx) > abs(dy) && inward > swipeThreshold) {
                            onExpand()
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        dx = 0f
                        dy = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dx += dragAmount.x
                    dy += dragAmount.y
                }
            },
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .width(handleWidth)
                .height(handleHeight)
                .graphicsLayer { alpha = handleAlpha },
            shape = CircleShape,
            color = scheme.primary,
            shadowElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { scaleX = if (isLeft) 1f else -1f }
                )
            }
        }
    }
}

@Composable
private fun GameDeckWorkspace(
    state: GameDeckUiState,
    onCollapse: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isLeft = state.side == GameDeckSide.LEFT
    val deckHeight = 258.dp
    val closeThreshold = with(LocalDensity.current) { 24.dp.toPx() }

    Row(
        modifier = Modifier
            .height(deckHeight)
            .pointerInput(isLeft, closeThreshold) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = {
                        dx = 0f
                        dy = 0f
                    },
                    onDragEnd = {
                        val outward = if (isLeft) -dx else dx
                        if (abs(dx) > abs(dy) && outward > closeThreshold) {
                            onCollapse()
                        }
                    },
                    onDragCancel = {
                        dx = 0f
                        dy = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    dx += dragAmount.x
                    dy += dragAmount.y
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLeft) {
            DockRail(
                selectedTab = selectedTab,
                opacity = state.opacity,
                quickApps = state.quickApps,
                onSelectTab = { tab ->
                    if (selectedTab == tab) onCollapse() else selectedTab = tab
                },
                onQuickAppClick = onQuickAppClick,
                onOpenManager = onOpenManager
            )
            DeckCard(
                state = state,
                selectedTab = selectedTab,
                onProfileSelected = onProfileSelected,
                onRefreshRateSelected = onRefreshRateSelected,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
        } else {
            DeckCard(
                state = state,
                selectedTab = selectedTab,
                onProfileSelected = onProfileSelected,
                onRefreshRateSelected = onRefreshRateSelected,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
            DockRail(
                selectedTab = selectedTab,
                opacity = state.opacity,
                quickApps = state.quickApps,
                onSelectTab = { tab ->
                    if (selectedTab == tab) onCollapse() else selectedTab = tab
                },
                onQuickAppClick = onQuickAppClick,
                onOpenManager = onOpenManager
            )
        }
    }
}

@Composable
private fun DockRail(
    selectedTab: Int,
    opacity: Float,
    quickApps: List<GameDeckQuickApp>,
    onSelectTab: (Int) -> Unit,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .width(52.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = opacity.coerceIn(0.45f, 1f)),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            DockRailNavItem(
                icon = Icons.Rounded.GridView,
                label = "Dashboard",
                selected = selectedTab == 0,
                onClick = { onSelectTab(0) }
            )
            DockRailNavItem(
                icon = Icons.Rounded.Build,
                label = "Tools",
                selected = selectedTab == 1,
                onClick = { onSelectTab(1) }
            )

            Box(
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .width(22.dp)
                    .height(1.5.dp)
                    .clip(CircleShape)
                    .background(scheme.outlineVariant.copy(alpha = 0.5f))
            )

            if (quickApps.isEmpty()) {
                Surface(
                    onClick = onOpenManager,
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = scheme.surfaceContainerHighest.copy(alpha = 0.75f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Tambah aplikasi",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                quickApps.forEach { app ->
                    DockRailAppIcon(
                        app = app,
                        onClick = { onQuickAppClick(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DockRailNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val pillBg by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "railPillBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "railIconTint"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(pillBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun DockRailAppIcon(
    app: GameDeckQuickApp,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val icon = rememberAppIcon(app.packageName)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        shape = RoundedCornerShape(11.dp),
        color = scheme.surfaceContainerHighest.copy(alpha = 0.8f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(23.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(Locale.ROOT),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DeckCard(
    state: GameDeckUiState,
    selectedTab: Int,
    onProfileSelected: (String) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .width(284.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = state.opacity.coerceIn(0.45f, 1f)),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp
    ) {
        if (selectedTab == 0) {
            DashboardDeckContent(
                state = state,
                onProfileSelected = onProfileSelected,
                onRefreshRateSelected = onRefreshRateSelected
            )
        } else {
            ToolsDeckContent(
                state = state,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
        }
    }
}

@Composable
private fun DashboardDeckContent(
    state: GameDeckUiState,
    onProfileSelected: (String) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val fps = state.fps.coerceAtLeast(0)
    val targetHz = state.targetHz.coerceAtLeast(1)
    val fpsRatio = fps.toFloat() / targetHz.toFloat()
    val fpsColor = when {
        fps <= 0 -> scheme.onSurfaceVariant
        fpsRatio >= 0.90f -> scheme.primary
        fpsRatio >= 0.65f -> scheme.tertiary
        else -> scheme.error
    }
    val frametimeText = if (fps > 0 && state.frametimeMs > 0f) {
        String.format(Locale.US, "%.1f ms", state.frametimeMs)
    } else {
        "FPS"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularLoadGauge(
                size = 58.dp,
                percent = state.cpuPercent.coerceIn(0, 100),
                label = "CPU",
                accentColor = scheme.primary
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (fps > 0) fps.toString() else "--",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = fpsColor,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = frametimeText,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            CircularLoadGauge(
                size = 58.dp,
                percent = state.gpuPercent.coerceIn(0, 100),
                label = "GPU",
                accentColor = scheme.tertiary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Thermostat,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = state.tempLabel,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.BatteryFull,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "${state.batteryPercent}%",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            deckProfiles.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    rowItems.forEach { item ->
                        val selected = deckProfileMatches(state.profile, item.value)
                        Surface(
                            onClick = { onProfileSelected(item.value) },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            shape = RoundedCornerShape(11.dp),
                            color = if (selected) scheme.primary else scheme.surfaceContainerHighest
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) scheme.onPrimary else scheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        DynamicRefreshRateStrip(
            modes = state.rrModes,
            selectedHz = state.selectedRrHz,
            liveHz = state.targetHz,
            onSelect = onRefreshRateSelected
        )
    }
}

@Composable
private fun CircularLoadGauge(
    size: Dp,
    percent: Int,
    label: String,
    accentColor: Color
) {
    val scheme = MaterialTheme.colorScheme
    val trackColor = scheme.onSurface.copy(alpha = 0.12f)
    val animatedProgress by animateFloatAsState(
        targetValue = (percent.coerceIn(0, 100) / 100f),
        animationSpec = tween(250),
        label = "gaugeAnim"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.5.dp.toPx()
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
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = label,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DynamicRefreshRateStrip(
    modes: List<Int>,
    selectedHz: Int?,
    liveHz: Int,
    onSelect: (Int?) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val options: List<Int?> = remember(modes) {
        buildList {
            add(null)
            addAll(modes.distinct().sorted())
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerHighest.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Refresh rate",
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = if (selectedHz == null) "Auto ($liveHz Hz)" else "$liveHz Hz",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (options.size <= 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEach { hz ->
                        RrOptionPill(
                            label = hz?.let { "${it}Hz" } ?: "Auto",
                            selected = selectedHz == hz,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(hz) }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    options.forEach { hz ->
                        RrOptionPill(
                            label = hz?.let { "${it}Hz" } ?: "Auto",
                            selected = selectedHz == hz,
                            modifier = Modifier.widthIn(min = 48.dp),
                            onClick = { onSelect(hz) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RrOptionPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(24.dp),
        shape = CircleShape,
        color = if (selected) scheme.primary else scheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 9.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ToolsDeckContent(
    state: GameDeckUiState,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Tools",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            maxLines = 1
        )

        Surface(
            onClick = onToggleDnd,
            shape = RoundedCornerShape(16.dp),
            color = if (state.dndEnabled) scheme.primaryContainer else scheme.surfaceContainerHighest.copy(alpha = 0.65f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = if (state.dndEnabled) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Jangan Ganggu",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.dndEnabled) scheme.onPrimaryContainer else scheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = if (state.dndEnabled) "Notifikasi dibisukan" else "Nonaktif",
                        fontSize = 10.sp,
                        color = if (state.dndEnabled) {
                            scheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            scheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
                Switch(
                    checked = state.dndEnabled,
                    onCheckedChange = { onToggleDnd() }
                )
            }
        }

        Surface(
            onClick = onOpenManager,
            shape = RoundedCornerShape(16.dp),
            color = scheme.surfaceContainerHighest.copy(alpha = 0.65f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Buka Lumina Manager",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun deckProfileMatches(current: String, option: String): Boolean {
    val value = current.lowercase(Locale.ROOT)
    return when (option) {
        "Eco" -> value.contains("eco") || value.contains("power") || value.contains("hemat")
        "Balanced" -> value.contains("balanc")
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
