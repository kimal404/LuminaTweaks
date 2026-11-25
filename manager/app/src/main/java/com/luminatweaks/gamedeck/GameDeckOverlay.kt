@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.luminatweaks.gamedeck

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
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

internal const val DECK_RAIL_WIDTH_DP = 66f
internal const val DECK_RAIL_GAP_DP = 8f
internal const val DECK_CARD_WIDTH_DP = 376f
internal const val DECK_TOTAL_WIDTH_DP = DECK_RAIL_WIDTH_DP + DECK_RAIL_GAP_DP + DECK_CARD_WIDTH_DP

private data class DeckProfileOption(
    val shortLabel: String,
    val fullLabel: String,
    val command: String,
    val icon: ImageVector
)

private val deckProfileOptions = listOf(
    DeckProfileOption("Eco", "Eco", "eco", Icons.Rounded.Eco),
    DeckProfileOption("Balanced", "Balanced", "balanced", Icons.Rounded.Speed),
    DeckProfileOption("Performance", "Performance", "performance", Icons.Rounded.Bolt),
    DeckProfileOption("Lite", "Performance Lite", "performance_lite", Icons.Rounded.EnergySavingsLeaf)
)

private fun deckOverrideSelected(current: String, option: DeckProfileOption): Boolean {
    val trimmed = current.trim()
    return trimmed.equals(option.fullLabel, ignoreCase = true) ||
        trimmed.equals(option.shortLabel, ignoreCase = true)
}

@Composable
fun GameDeckRoot(
    state: State<GameDeckUiState>,
    isWindowExpanded: Boolean,
    isHandlePressed: Boolean,
    handleYOffsetPx: Int = 0,
    onCollapse: () -> Unit,
    onProfileSelected: (String?) -> Unit,
    onAutoModeToggle: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit,
    onCollapseSettled: () -> Unit
) {
    val current by state
    val isLeft = current.side == GameDeckSide.LEFT
    val openProgress = remember {
        Animatable(if (current.expanded) 1f else 0f)
    }

    LaunchedEffect(current.expanded) {
        if (current.expanded) {
            openProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 400f
                )
            )
        } else {
            if (openProgress.value > 0f) {
                openProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 185,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            onCollapseSettled()
        }
    }

    if (!isWindowExpanded) {
        EdgeDeckHandle(
            isLeft = isLeft,
            isPressed = isHandlePressed
        )
    } else {
        val density = LocalDensity.current
        val slideOffset = with(density) { 24.dp.toPx() }
        val screenHeightPx = LocalContext.current.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val panelHeightPx = (screenHeightPx * 0.85f)
            .coerceIn(with(density) { 316.dp.toPx() }, with(density) { 344.dp.toPx() })
            .coerceAtLeast(1f)
        val pivotY = (0.5f + (handleYOffsetPx.toFloat() / panelHeightPx)).coerceIn(0.08f, 0.92f)
        val p = openProgress.value.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (p * 1.25f).coerceIn(0f, 1f)
                    scaleX = 0.90f + 0.10f * p
                    scaleY = 0.92f + 0.08f * p
                    translationX = if (isLeft) -(1f - p) * slideOffset else (1f - p) * slideOffset
                    transformOrigin = TransformOrigin(if (isLeft) 0f else 1f, pivotY)
                }
        ) {
            GameDeckWorkspace(
                state = current,
                onCollapse = onCollapse,
                onProfileSelected = onProfileSelected,
                onAutoModeToggle = onAutoModeToggle,
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
    isPressed: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    var hinting by remember { mutableStateOf(true) }

    val handleWidth by animateDpAsState(
        targetValue = when {
            isPressed -> 10.dp
            hinting -> 8.dp
            else -> 5.dp
        },
        animationSpec = motion.defaultSpatialSpec(),
        label = "deckHandleWidth"
    )
    val handleHeight by animateDpAsState(
        targetValue = when {
            isPressed -> 68.dp
            hinting -> 64.dp
            else -> 48.dp
        },
        animationSpec = motion.defaultSpatialSpec(),
        label = "deckHandleHeight"
    )
    val handleAlpha by animateFloatAsState(
        targetValue = if (isPressed || hinting) 0.96f else 0.62f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "deckHandleAlpha"
    )

    LaunchedEffect(Unit) {
        delay(2_000L)
        hinting = false
    }

    Box(
        modifier = Modifier.fillMaxSize(),
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
            contentColor = scheme.onPrimary,
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(11.dp)
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
    onProfileSelected: (String?) -> Unit,
    onAutoModeToggle: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isLeft = state.side == GameDeckSide.LEFT
    val closeThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val screenWidthDp = with(LocalDensity.current) {
        LocalContext.current.resources.displayMetrics.widthPixels.toDp()
    }
    val maxWorkspaceWidth = (screenWidthDp - 24.dp).coerceAtLeast(320.dp)
    val cardWidth: Dp = (maxWorkspaceWidth - DECK_RAIL_WIDTH_DP.dp - DECK_RAIL_GAP_DP.dp)
        .coerceIn(260.dp, DECK_CARD_WIDTH_DP.dp)

    Row(
        modifier = Modifier
            .fillMaxSize()
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
        horizontalArrangement = Arrangement.spacedBy(DECK_RAIL_GAP_DP.dp),
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
                cardWidth = cardWidth,
                selectedTab = selectedTab,
                onProfileSelected = onProfileSelected,
                onAutoModeToggle = onAutoModeToggle,
                onRefreshRateSelected = onRefreshRateSelected,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
        } else {
            DeckCard(
                state = state,
                cardWidth = cardWidth,
                selectedTab = selectedTab,
                onProfileSelected = onProfileSelected,
                onAutoModeToggle = onAutoModeToggle,
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
    val deckOpacity = opacity.coerceIn(0.45f, 1f)

    Box(
        modifier = Modifier
            .width(DECK_RAIL_WIDTH_DP.dp)
            .fillMaxHeight()
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerHighest.copy(alpha = deckOpacity),
                        scheme.surfaceContainerHigh.copy(alpha = deckOpacity)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
            }

            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(24.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(scheme.outlineVariant.copy(alpha = 0.55f))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (quickApps.isEmpty()) {
                    Surface(
                        onClick = onOpenManager,
                        modifier = Modifier.size(44.dp),
                        shape = MaterialShapes.Cookie4Sided.toShape(),
                        color = scheme.tertiaryContainer
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Tambah aplikasi",
                                tint = scheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
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
}

@Composable
private fun DockRailNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val pillBg by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "railPillBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "railIconTint"
    )
    val labelTint by animateColorAsState(
        targetValue = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "railLabelTint"
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 20.dp else 12.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "railPillCorner"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 48.dp else 44.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "railPillWidth"
    )
    val indicatorHeight by animateDpAsState(
        targetValue = if (selected) 34.dp else 30.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "railPillHeight"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(indicatorHeight)
                .clip(RoundedCornerShape(corner))
                .background(pillBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(if (selected) 20.dp else 18.dp)
            )
        }
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = labelTint,
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
        modifier = Modifier.size(44.dp),
        shape = MaterialShapes.Cookie4Sided.toShape(),
        color = scheme.surfaceContainerHighest.copy(alpha = 0.9f)
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
                        .size(28.dp)
                        .clip(CircleShape)
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
    cardWidth: Dp,
    selectedTab: Int,
    onProfileSelected: (String?) -> Unit,
    onAutoModeToggle: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val deckOpacity = state.opacity.coerceIn(0.45f, 1f)
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .width(cardWidth)
            .fillMaxHeight()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerLow.copy(alpha = deckOpacity),
                        scheme.surfaceContainerLowest.copy(alpha = deckOpacity)
                    )
                )
            )
    ) {
        if (selectedTab == 0) {
            DashboardDeckContent(
                state = state,
                onProfileSelected = onProfileSelected,
                onAutoModeToggle = onAutoModeToggle,
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
    onProfileSelected: (String?) -> Unit,
    onAutoModeToggle: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val fps = state.fps.coerceAtLeast(0)
    val targetHz = state.targetHz.coerceAtLeast(1)
    val fpsRatio = fps.toFloat() / targetHz.toFloat()
    val rawFpsColor = when {
        fps <= 0 -> scheme.onSurfaceVariant
        fpsRatio >= 0.90f -> scheme.primary
        fpsRatio >= 0.65f -> scheme.tertiary
        else -> scheme.error
    }
    val fpsColor by animateColorAsState(
        targetValue = rawFpsColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "deckFpsColor"
    )
    val frametimeText = if (fps > 0 && state.frametimeMs > 0f) {
        String.format(Locale.US, "%.1f ms", state.frametimeMs)
    } else {
        "Menunggu frame"
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Outer Card 1: Telemetry & Device Vitals (Bento 2-Column Nested Sub-Items)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = scheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nested Sub-Item Left: FPS & Frametime
                    Surface(
                        modifier = Modifier.weight(0.43f),
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = if (fps > 0) fps.toString() else "--",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = 28.sp,
                                        lineHeight = 30.sp,
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = fpsColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "FPS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Text(
                                text = if (fps > 0) "$frametimeText · ${state.targetHz} Hz" else frametimeText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Nested Sub-Item Right: Thermal, Battery, CPU & GPU
                    Surface(
                        modifier = Modifier.weight(0.57f),
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MetricChip(
                                    icon = Icons.Rounded.Thermostat,
                                    label = state.tempLabel
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                MetricChip(
                                    icon = Icons.Rounded.BatteryFull,
                                    label = "${state.batteryPercent}%"
                                )
                            }
                            MiniMetric(
                                label = "CPU",
                                percent = state.cpuPercent,
                                accent = scheme.primary
                            )
                            MiniMetric(
                                label = "GPU",
                                percent = state.gpuPercent,
                                accent = scheme.tertiary
                            )
                        }
                    }
                }
            }

            // Outer Card 2: Controls (Nested Profile & Expandable Refresh Rate Sub-Items)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = scheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileNestedCard(
                        state = state,
                        onProfileSelected = onProfileSelected,
                        onAutoModeToggle = onAutoModeToggle
                    )
                    RefreshRateExpandableCard(
                        modes = state.rrModes,
                        selectedHz = state.selectedRrHz,
                        liveHz = state.targetHz,
                        onSelect = onRefreshRateSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: ImageVector,
    label: String
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun MiniMetric(
    label: String,
    percent: Int,
    accent: Color
) {
    val scheme = MaterialTheme.colorScheme
    val safePercent = percent.coerceIn(0, 100)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$safePercent%",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1,
                softWrap = false
            )
        }
        LinearWavyProgressIndicator(
            progress = { safePercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = accent,
            trackColor = accent.copy(alpha = 0.16f)
        )
    }
}

@Composable
private fun ProfileNestedCard(
    state: GameDeckUiState,
    onProfileSelected: (String?) -> Unit,
    onAutoModeToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val autoMode = state.profileAutoMode

    val autoChipBg by animateColorAsState(
        targetValue = if (autoMode) scheme.primary else scheme.surfaceContainerHighest,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "autoChipBg"
    )
    val autoChipContent by animateColorAsState(
        targetValue = if (autoMode) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "autoChipContent"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = scheme.surfaceContainerHighest
                ) {
                    Text(
                        text = state.profile,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    onClick = onAutoModeToggle,
                    shape = CircleShape,
                    color = autoChipBg,
                    contentColor = autoChipContent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (autoMode) "Auto ON" else "Auto OFF",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                deckProfileOptions.forEach { option ->
                    val selected = !autoMode && deckOverrideSelected(state.profileOverride, option)
                    val isCurrentActive = state.profile.trim().equals(option.fullLabel, ignoreCase = true)
                    val tileBg by animateColorAsState(
                        targetValue = when {
                            selected -> scheme.primary
                            autoMode && isCurrentActive -> scheme.secondaryContainer.copy(alpha = 0.72f)
                            else -> scheme.surfaceContainerHighest.copy(alpha = if (autoMode) 0.55f else 0.92f)
                        },
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "profileTileBg"
                    )
                    val tileContent by animateColorAsState(
                        targetValue = when {
                            selected -> scheme.onPrimary
                            autoMode && isCurrentActive -> scheme.onSecondaryContainer
                            autoMode -> scheme.onSurfaceVariant.copy(alpha = 0.65f)
                            else -> scheme.onSurface
                        },
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "profileTileContent"
                    )
                    val tileCorner by animateDpAsState(
                        targetValue = if (selected || (autoMode && isCurrentActive)) 18.dp else 13.dp,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "profileTileCorner"
                    )

                    Surface(
                        onClick = {
                            if (!autoMode) {
                                onProfileSelected(option.command)
                            } else {
                                onProfileSelected(null)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(tileCorner),
                        color = tileBg,
                        contentColor = tileContent,
                        border = if (autoMode && isCurrentActive) {
                            BorderStroke(1.dp, scheme.primary.copy(alpha = 0.55f))
                        } else {
                            null
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.fullLabel,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = option.shortLabel,
                                fontSize = 9.5.sp,
                                fontWeight = if (selected || isCurrentActive) FontWeight.Bold else FontWeight.Medium,
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

@Composable
private fun RefreshRateExpandableCard(
    modes: List<Int>,
    selectedHz: Int?,
    liveHz: Int,
    onSelect: (Int?) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "rrChevronRotation"
    )
    val options: List<Int?> = remember(modes) {
        buildList {
            add(null)
            addAll(modes.distinct().sorted())
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 420f
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refresh Rate",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (selectedHz == null) "Auto · $liveHz Hz" else "$selectedHz Hz",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(
                    color = scheme.outlineVariant.copy(alpha = 0.28f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { hz ->
                        val isSelected = selectedHz == hz
                        val pillBg by animateColorAsState(
                            targetValue = if (isSelected) scheme.primary else scheme.surfaceContainerHighest,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "rrOptionBg"
                        )
                        val pillText by animateColorAsState(
                            targetValue = if (isSelected) scheme.onPrimary else scheme.onSurface,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "rrOptionText"
                        )

                        Surface(
                            onClick = {
                                onSelect(hz)
                                expanded = false
                            },
                            shape = CircleShape,
                            color = pillBg,
                            contentColor = pillText
                        ) {
                            Text(
                                text = hz?.let { "$it Hz" } ?: "Auto",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
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
    val dndText = if (state.dndEnabled) "Notifikasi dibisukan" else "Nonaktif"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = scheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tools",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    maxLines = 1
                )

                ToggleButton(
                    checked = state.dndEnabled,
                    onCheckedChange = { onToggleDnd() },
                    buttonSize = ToggleButtonSize.ExtraSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Jangan Ganggu",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = dndText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = if (state.dndEnabled) "ON" else "OFF",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        FilledTonalButton(
            onClick = onOpenManager,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shapes = ButtonDefaults.shapes()
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Buka Lumina Manager",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
