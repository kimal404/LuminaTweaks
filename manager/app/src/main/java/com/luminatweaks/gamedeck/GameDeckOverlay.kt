@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.luminatweaks.gamedeck

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.material3.toShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

private data class DeckProfileOption(
    val label: String,
    val command: String
)

private val deckProfileOptions = listOf(
    DeckProfileOption("Eco", "eco"),
    DeckProfileOption("Balanced", "balanced"),
    DeckProfileOption("Performance", "performance"),
    DeckProfileOption("Performance Lite", "performance_lite")
)

private fun deckOverrideSelected(current: String, label: String): Boolean =
    current.trim().equals(label, ignoreCase = true)

@Composable
fun GameDeckRoot(
    state: State<GameDeckUiState>,
    isWindowExpanded: Boolean,
    isHandlePressed: Boolean,
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
                    dampingRatio = 0.84f,
                    stiffness = 380f
                )
            )
        } else {
            if (openProgress.value > 0f) {
                openProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 190,
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
        val slideOffset = with(LocalDensity.current) { 24.dp.toPx() }
        val p = openProgress.value.coerceIn(0f, 1f)
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = (p * 1.25f).coerceIn(0f, 1f)
                scaleX = 0.88f + 0.12f * p
                scaleY = 0.90f + 0.10f * p
                translationX = if (isLeft) -(1f - p) * slideOffset else (1f - p) * slideOffset
                transformOrigin = TransformOrigin(if (isLeft) 0f else 1f, 0.5f)
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
    var hinting by remember { mutableStateOf(true) }

    val handleWidth by animateDpAsState(
        targetValue = when {
            isPressed -> 9.dp
            hinting -> 7.dp
            else -> 5.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "deckHandleWidth"
    )
    val handleHeight by animateDpAsState(
        targetValue = when {
            isPressed -> 64.dp
            hinting -> 58.dp
            else -> 48.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "deckHandleHeight"
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else if (hinting) 0.65f else 0.42f,
        animationSpec = tween(180),
        label = "deckHandleAlpha"
    )

    LaunchedEffect(Unit) {
        delay(1_800L)
        hinting = false
    }

    val pillShape = if (isLeft) {
        RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp)
    } else {
        RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .width(handleWidth)
                .height(handleHeight)
                .border(
                    width = 1.5.dp,
                    color = scheme.primary.copy(alpha = 0.90f),
                    shape = pillShape
                )
                .background(
                    color = scheme.primary.copy(alpha = fillAlpha),
                    shape = pillShape
                )
        )
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
    val screenHeightPx = LocalContext.current.resources.displayMetrics.heightPixels
    val deckHeight = with(LocalDensity.current) {
        (screenHeightPx * 0.86f).toDp().coerceIn(318.dp, 720.dp)
    }
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                onAutoModeToggle = onAutoModeToggle,
                onRefreshRateSelected = onRefreshRateSelected,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
        } else {
            DeckCard(
                state = state,
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
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .width(76.dp)
            .fillMaxHeight()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerLow.copy(alpha = deckOpacity),
                        scheme.surfaceContainer.copy(alpha = deckOpacity)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 10.dp),
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

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(scheme.outlineVariant.copy(alpha = 0.55f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(max = 170.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (quickApps.isEmpty()) {
                    Surface(
                        onClick = onOpenManager,
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.surfaceContainerHigh
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Tambah aplikasi",
                                tint = scheme.onSurface,
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
        targetValue = if (selected) scheme.secondaryContainer else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "railPillBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "railIconTint"
    )
    val labelTint by animateColorAsState(
        targetValue = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "railLabelTint"
    )
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "railPillScale"
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
                .width(48.dp)
                .height(28.dp)
                .graphicsLayer {
                    scaleX = pillScale
                    scaleY = pillScale
                }
                .clip(CircleShape)
                .background(pillBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
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
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerHigh
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
                        .clip(RoundedCornerShape(8.dp))
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
    onProfileSelected: (String?) -> Unit,
    onAutoModeToggle: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val deckOpacity = state.opacity.coerceIn(0.45f, 1f)
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .width(354.dp)
            .fillMaxHeight()
            .shadow(14.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerLow.copy(alpha = deckOpacity),
                        scheme.surfaceContainer.copy(alpha = deckOpacity)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DashboardTopRow(state)
        PerformanceHero(
            state = state,
            fpsColor = fpsColor,
            frametimeText = frametimeText
        )
        ProfileSection(
            state = state,
            onProfileSelected = onProfileSelected,
            onAutoModeToggle = onAutoModeToggle
        )
        RefreshRateSection(
            modes = state.rrModes,
            selectedHz = state.selectedRrHz,
            liveHz = state.targetHz,
            onSelect = onRefreshRateSelected
        )
    }
}

@Composable
private fun DashboardTopRow(state: GameDeckUiState) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveBadge(active = state.fps > 0)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "GAME DECK",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.weight(1f))
        MetricChip(
            icon = Icons.Rounded.Thermostat,
            label = state.tempLabel
        )
        Spacer(modifier = Modifier.width(10.dp))
        MetricChip(
            icon = Icons.Rounded.BatteryFull,
            label = "${state.batteryPercent}%"
        )
    }
}

@Composable
private fun LiveBadge(active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (active) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "liveBadgeContainer"
    )
    val content by animateColorAsState(
        targetValue = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "liveBadgeContent"
    )

    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        if (active) scheme.primary else scheme.outline,
                        MaterialShapes.Sunny.toShape()
                    )
            )
            Text(
                text = if (active) "LIVE" else "IDLE",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = content,
                maxLines = 1,
                softWrap = false
            )
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
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PerformanceHero(
    state: GameDeckUiState,
    fpsColor: Color,
    frametimeText: String
) {
    val scheme = MaterialTheme.colorScheme
    val fps = state.fps.coerceAtLeast(0)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "FRAME RATE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (fps > 0) fps.toString() else "--",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 32.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = fpsColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "FPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = if (fps > 0) "$frametimeText · ${state.targetHz} Hz" else frametimeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.width(126.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
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
private fun ProfileSection(
    state: GameDeckUiState,
    onProfileSelected: (String?) -> Unit,
    onAutoModeToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val autoMode = state.profileAutoMode

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.weight(1f))
            ProfileModeBadge(
                autoMode = autoMode,
                activeProfile = state.profile
            )
        }

        deckProfileOptions.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    val selected = !autoMode &&
                        deckOverrideSelected(state.profileOverride, option.label)
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = { checked ->
                            if (checked && !autoMode) {
                                onProfileSelected(option.command)
                            }
                        },
                        enabled = !autoMode,
                        buttonSize = ToggleButtonSize.ExtraSmall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = option.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        ToggleButton(
            checked = autoMode,
            onCheckedChange = { onAutoModeToggle() },
            buttonSize = ToggleButtonSize.ExtraSmall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto mode",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (autoMode) "ON" else "OFF",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ProfileModeBadge(
    autoMode: Boolean,
    activeProfile: String
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (autoMode) scheme.primaryContainer else scheme.tertiaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "profileModeContainer"
    )
    val content by animateColorAsState(
        targetValue = if (autoMode) scheme.onPrimaryContainer else scheme.onTertiaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "profileModeContent"
    )

    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content
    ) {
        Text(
            text = if (autoMode) "AUTO · $activeProfile" else "MANUAL",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RefreshRateSection(
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

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Refresh rate",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (selectedHz == null) "Auto · $liveHz Hz" else "$liveHz Hz",
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
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                options.forEachIndexed { index, hz ->
                    RrToggle(
                        label = hz?.let { "${it}Hz" } ?: "Auto",
                        selected = selectedHz == hz,
                        index = index,
                        lastIndex = options.lastIndex,
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
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                options.forEachIndexed { index, hz ->
                    RrToggle(
                        label = hz?.let { "${it}Hz" } ?: "Auto",
                        selected = selectedHz == hz,
                        index = index,
                        lastIndex = options.lastIndex,
                        modifier = Modifier.widthIn(min = 56.dp),
                        onClick = { onSelect(hz) }
                    )
                }
            }
        }
    }
}

@Composable
private fun connectedShapes(index: Int, lastIndex: Int) = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

@Composable
private fun RrToggle(
    label: String,
    selected: Boolean,
    index: Int,
    lastIndex: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { checked ->
            if (checked) onClick()
        },
        buttonSize = ToggleButtonSize.ExtraSmall,
        shapes = connectedShapes(index, lastIndex),
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
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
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Tools",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = scheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "Kontrol cepat khusus sesi game",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ToggleButton(
            checked = state.dndEnabled,
            onCheckedChange = { onToggleDnd() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Jangan Ganggu",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = dndText,
                        fontSize = 10.sp,
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

        Spacer(modifier = Modifier.weight(1f))

        FilledTonalButton(
            onClick = onOpenManager,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shapes = ButtonDefaults.shapes()
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
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
