package com.luminatweaks.gamedeck

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    DeckProfileItem("Balanced", "Balance", Icons.Rounded.Tune),
    DeckProfileItem("Maestro", "Maestro", Icons.Rounded.Bolt),
    DeckProfileItem("Performance", "Perf", Icons.Rounded.Speed)
)

@Composable
fun GameDeckRoot(
    state: State<GameDeckUiState>,
    isWindowExpanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onToggleAutoProfile: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    val current by state
    val motion = MaterialTheme.motionScheme
    val isLeft = current.side == GameDeckSide.LEFT
    val openProgress by animateFloatAsState(
        targetValue = if (current.expanded) 1f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "deckOpenProgress"
    )

    if (!isWindowExpanded && openProgress <= 0.01f) {
        EdgeDeckHandle(isLeft = isLeft, onExpand = onExpand)
    } else {
        val slideOffset = with(LocalDensity.current) { 20.dp.toPx() }
        Box(
            modifier = Modifier.graphicsLayer {
                val p = openProgress.coerceIn(0f, 1f)
                alpha = p
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.96f + 0.04f * p
                translationX = if (isLeft) -(1f - p) * slideOffset else (1f - p) * slideOffset
                transformOrigin = TransformOrigin(if (isLeft) 0f else 1f, 0.5f)
            }
        ) {
            GameDeckWorkspace(
                state = current,
                onCollapse = onCollapse,
                onToggleAutoProfile = onToggleAutoProfile,
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
    val motion = MaterialTheme.motionScheme
    val swipeThreshold = with(LocalDensity.current) { 10.dp.toPx() }
    var hinting by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }

    val handleWidth by animateDpAsState(
        targetValue = if (hinting || dragging) 10.dp else 6.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "deckHandleWidth"
    )
    val handleHeight by animateDpAsState(
        targetValue = if (hinting || dragging) 74.dp else 54.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "deckHandleHeight"
    )
    val handleAlpha by animateFloatAsState(
        targetValue = if (hinting || dragging) 1f else 0.78f,
        animationSpec = motion.fastEffectsSpec(),
        label = "deckHandleAlpha"
    )
    val fillColor by animateColorAsState(
        targetValue = if (hinting || dragging) scheme.primary else scheme.primary.copy(alpha = 0.82f),
        animationSpec = motion.defaultEffectsSpec(),
        label = "deckHandleColor"
    )

    LaunchedEffect(Unit) {
        delay(1_400L)
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
            color = fillColor,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, scheme.onPrimary.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = scheme.onPrimary.copy(alpha = 0.9f),
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
    onToggleAutoProfile: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val isLeft = state.side == GameDeckSide.LEFT
    val deckHeight = 250.dp

    Row(
        modifier = Modifier.height(deckHeight),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLeft) {
            DockRail(
                selectedTab = selectedTab,
                opacity = state.opacity,
                quickApps = state.quickApps,
                onSelectTab = { selectedTab = it },
                onQuickAppClick = onQuickAppClick
            )
            DeckCard(
                state = state,
                selectedTab = selectedTab,
                onCollapse = onCollapse,
                onToggleAutoProfile = onToggleAutoProfile,
                onProfileSelected = onProfileSelected,
                onRefreshRateSelected = onRefreshRateSelected,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
        } else {
            DeckCard(
                state = state,
                selectedTab = selectedTab,
                onCollapse = onCollapse,
                onToggleAutoProfile = onToggleAutoProfile,
                onProfileSelected = onProfileSelected,
                onRefreshRateSelected = onRefreshRateSelected,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
            DockRail(
                selectedTab = selectedTab,
                opacity = state.opacity,
                quickApps = state.quickApps,
                onSelectTab = { selectedTab = it },
                onQuickAppClick = onQuickAppClick
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
    onQuickAppClick: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .width(62.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(30.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = opacity.coerceIn(0.45f, 1f)),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DockRailItem(
                icon = Icons.Rounded.GridView,
                label = "Deck",
                selected = selectedTab == 0,
                onClick = { onSelectTab(0) }
            )
            DockRailItem(
                icon = Icons.Rounded.Build,
                label = "Tools",
                selected = selectedTab == 1,
                onClick = { onSelectTab(1) }
            )

            if (quickApps.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .width(24.dp)
                        .height(1.5.dp)
                        .clip(CircleShape)
                        .background(scheme.outlineVariant.copy(alpha = 0.6f))
                )
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
private fun DockRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val pillColor by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "railPillColor"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "railIconTint"
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 46.dp else 38.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "railPillWidth"
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 22.dp else 14.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "railPillCorner"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(30.dp)
                .clip(RoundedCornerShape(corner))
                .background(pillColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }
        Text(
            text = label,
            style = if (selected) {
                MaterialTheme.typography.labelSmallEmphasized
            } else {
                MaterialTheme.typography.labelSmall
            },
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
    val motion = MaterialTheme.motionScheme
    val icon = rememberAppIcon(app.packageName)
    var pressed by remember { mutableStateOf(false) }
    val corner by animateDpAsState(
        targetValue = if (pressed) 18.dp else 12.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "appIconCorner"
    )
    Surface(
        onClick = {
            pressed = !pressed
            onClick()
        },
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(corner),
        color = scheme.surfaceContainerHighest.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.3f))
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
                        .size(26.dp)
                        .clip(RoundedCornerShape(corner))
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelLargeEmphasized,
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
    onCollapse: () -> Unit,
    onToggleAutoProfile: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(286.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = state.opacity.coerceIn(0.45f, 1f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 8.dp
    ) {
        if (selectedTab == 0) {
            DashboardDeckContent(
                state = state,
                onCollapse = onCollapse,
                onToggleAutoProfile = onToggleAutoProfile,
                onProfileSelected = onProfileSelected,
                onRefreshRateSelected = onRefreshRateSelected
            )
        } else {
            ToolsDeckContent(
                state = state,
                onCollapse = onCollapse,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
        }
    }
}

@Composable
private fun DashboardDeckContent(
    state: GameDeckUiState,
    onCollapse: () -> Unit,
    onToggleAutoProfile: () -> Unit,
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
        "waiting for frame"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LUMINA DECK",
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = scheme.primary,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (fps > 0) fps.toString() else "--",
                        style = MaterialTheme.typography.displaySmallEmphasized,
                        color = fpsColor,
                        maxLines = 1
                    )
                    Text(
                        text = " fps",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Text(
                    text = frametimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AutoProfileToggle(
                    checked = state.autoProfile,
                    onToggle = onToggleAutoProfile
                )
                CollapseButton(onCollapse = onCollapse)
            }
        }

        QuickStatusRow(state = state)

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            LoadRow(
                label = "CPU",
                percent = state.cpuPercent,
                accent = scheme.primary
            )
            LoadRow(
                label = "GPU",
                percent = state.gpuPercent,
                accent = scheme.tertiary
            )
        }

        ProfileToggleRow(
            current = state.profile,
            onProfileSelected = onProfileSelected
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
private fun AutoProfileToggle(
    checked: Boolean,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = if (checked) scheme.tertiaryContainer else scheme.surfaceContainerHighest,
        border = BorderStroke(
            1.dp,
            if (checked) scheme.tertiary.copy(alpha = 0.5f) else scheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (checked) Icons.Rounded.AutoAwesome else Icons.Rounded.Tune,
                contentDescription = null,
                tint = if (checked) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = if (checked) "Auto" else "Manual",
                style = MaterialTheme.typography.labelSmallEmphasized,
                color = if (checked) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CollapseButton(onCollapse: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onCollapse,
        modifier = Modifier.size(26.dp),
        shape = CircleShape,
        color = scheme.surfaceContainerHighest
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Tutup",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun QuickStatusRow(state: GameDeckUiState) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatusChip(
            icon = Icons.Rounded.Thermostat,
            text = state.tempLabel,
            modifier = Modifier.weight(1f)
        )
        StatusChip(
            icon = Icons.Rounded.BatteryFull,
            text = "${state.batteryPercent}%",
            modifier = Modifier.weight(1f)
        )
        StatusChip(
            icon = Icons.Rounded.Speed,
            text = "${state.targetHz}Hz",
            modifier = Modifier.weight(1f),
            accent = scheme.primary
        )
    }
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LoadRow(
    label: String,
    percent: Int,
    accent: Color
) {
    val scheme = MaterialTheme.colorScheme
    val progress = percent.coerceIn(0, 100) / 100f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmallEmphasized,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = accent,
            trackColor = scheme.onSurface.copy(alpha = 0.10f)
        )
    }
}

@Composable
private fun ProfileToggleRow(
    current: String,
    onProfileSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        deckProfiles.forEachIndexed { index, item ->
            val selected = deckProfileMatches(current, item.value)
            ToggleButton(
                checked = selected,
                onCheckedChange = { onProfileSelected(item.value) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    deckProfiles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .semantics { role = Role.RadioButton },
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
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
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmallEmphasized,
                color = scheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = if (selectedHz == null) "Auto · $liveHz Hz" else "$liveHz Hz",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            options.forEach { hz ->
                RefreshRatePill(
                    label = hz?.let { "${it}Hz" } ?: "Auto",
                    selected = selectedHz == hz,
                    onClick = { onSelect(hz) }
                )
            }
        }
    }
}

@Composable
private fun RefreshRatePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(
        targetValue = if (selected) 16.dp else 10.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "rrCorner"
    )
    val bg by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "rrBg"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(26.dp)
            .widthIn(min = 46.dp),
        shape = RoundedCornerShape(corner),
        color = bg
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = if (selected) {
                    MaterialTheme.typography.labelSmallEmphasized
                } else {
                    MaterialTheme.typography.labelSmall
                },
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ToolsDeckContent(
    state: GameDeckUiState,
    onCollapse: () -> Unit,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOOLS",
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = scheme.primary,
                    maxLines = 1
                )
                Text(
                    text = "Quick toggles",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = scheme.onSurface,
                    maxLines = 1
                )
            }
            CollapseButton(onCollapse = onCollapse)
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = scheme.surfaceContainerHighest.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
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
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = scheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = if (state.dndEnabled) "Notifikasi dibisukan" else "Nonaktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
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
            shape = RoundedCornerShape(20.dp),
            color = scheme.surfaceContainerHighest.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Session",
                    style = MaterialTheme.typography.labelSmallEmphasized,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(
                        icon = Icons.Rounded.Speed,
                        text = "${state.fps} fps",
                        modifier = Modifier.weight(1f),
                        accent = scheme.primary
                    )
                    StatusChip(
                        icon = Icons.Rounded.Thermostat,
                        text = state.tempLabel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Button(
            onClick = onOpenManager,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shapes = ButtonDefaults.shapes()
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(56.dp))
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(56.dp)))
            Text(
                text = "Buka Lumina Manager",
                style = ButtonDefaults.textStyleFor(56.dp),
                maxLines = 1
            )
        }
    }
}

internal fun deckProfileMatches(current: String, option: String): Boolean {
    val value = current.lowercase(Locale.ROOT)
    return when (option) {
        "Eco" -> value.contains("eco") || value.contains("power") || value.contains("hemat")
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
