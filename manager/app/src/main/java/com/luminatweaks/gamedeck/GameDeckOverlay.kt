@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.luminatweaks.gamedeck

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.model.AppItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal const val DECK_RAIL_WIDTH_DP = 78f
internal const val DECK_RAIL_GAP_DP = 10f
internal const val DECK_CARD_WIDTH_DP = 354f
internal const val DECK_TOTAL_WIDTH_DP = DECK_RAIL_WIDTH_DP + DECK_RAIL_GAP_DP + DECK_CARD_WIDTH_DP
internal const val MAX_DECK_QUICK_APPS = 8

private data class DeckProfileOption(
    val shortLabel: String,
    val fullLabel: String,
    val command: String
)

private val deckProfileOptions = listOf(
    DeckProfileOption("Eco", "Eco", "eco"),
    DeckProfileOption("Balanced", "Balanced", "balanced"),
    DeckProfileOption("Performance", "Performance", "performance"),
    DeckProfileOption("Lite", "Performance Lite", "performance_lite")
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
    onSetAutoMode: (Boolean) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onBrightnessChanged: (Int, Boolean) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleDnd: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onToggleQuickApp: (String) -> Unit,
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
        val panelHeightPx = (screenHeightPx * 0.86f)
            .coerceIn(with(density) { 316.dp.toPx() }, with(density) { 348.dp.toPx() })
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
                onSetAutoMode = onSetAutoMode,
                onRefreshRateSelected = onRefreshRateSelected,
                onBrightnessChanged = onBrightnessChanged,
                onVolumeChanged = onVolumeChanged,
                onToggleMute = onToggleMute,
                onToggleDnd = onToggleDnd,
                onQuickAppClick = onQuickAppClick,
                onToggleQuickApp = onToggleQuickApp,
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
    onSetAutoMode: (Boolean) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onBrightnessChanged: (Int, Boolean) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleDnd: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onToggleQuickApp: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAppPicker by remember { mutableStateOf(false) }
    val isLeft = state.side == GameDeckSide.LEFT
    val closeThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val screenWidthDp = with(LocalDensity.current) {
        LocalContext.current.resources.displayMetrics.widthPixels.toDp()
    }
    val maxWorkspaceWidth = (screenWidthDp - 24.dp).coerceAtLeast(320.dp)
    val cardWidth: Dp = (maxWorkspaceWidth - DECK_RAIL_WIDTH_DP.dp - DECK_RAIL_GAP_DP.dp)
        .coerceIn(260.dp, DECK_CARD_WIDTH_DP.dp)

    val handleTabSelect: (Int) -> Unit = { tab ->
        if (showAppPicker) {
            showAppPicker = false
            selectedTab = tab
        } else if (selectedTab == tab) {
            onCollapse()
        } else {
            selectedTab = tab
        }
    }

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
                selectedTab = if (showAppPicker) -1 else selectedTab,
                isAddActive = showAppPicker,
                opacity = state.opacity,
                quickApps = state.quickApps,
                onSelectTab = handleTabSelect,
                onQuickAppClick = onQuickAppClick,
                onAddAppClick = { showAppPicker = !showAppPicker }
            )
            DeckCard(
                state = state,
                cardWidth = cardWidth,
                selectedTab = selectedTab,
                showAppPicker = showAppPicker,
                onCloseAppPicker = { showAppPicker = false },
                onToggleQuickApp = onToggleQuickApp,
                onProfileSelected = onProfileSelected,
                onSetAutoMode = onSetAutoMode,
                onRefreshRateSelected = onRefreshRateSelected,
                onBrightnessChanged = onBrightnessChanged,
                onVolumeChanged = onVolumeChanged,
                onToggleMute = onToggleMute,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
        } else {
            DeckCard(
                state = state,
                cardWidth = cardWidth,
                selectedTab = selectedTab,
                showAppPicker = showAppPicker,
                onCloseAppPicker = { showAppPicker = false },
                onToggleQuickApp = onToggleQuickApp,
                onProfileSelected = onProfileSelected,
                onSetAutoMode = onSetAutoMode,
                onRefreshRateSelected = onRefreshRateSelected,
                onBrightnessChanged = onBrightnessChanged,
                onVolumeChanged = onVolumeChanged,
                onToggleMute = onToggleMute,
                onToggleDnd = onToggleDnd,
                onOpenManager = onOpenManager
            )
            DockRail(
                selectedTab = if (showAppPicker) -1 else selectedTab,
                isAddActive = showAppPicker,
                opacity = state.opacity,
                quickApps = state.quickApps,
                onSelectTab = handleTabSelect,
                onQuickAppClick = onQuickAppClick,
                onAddAppClick = { showAppPicker = !showAppPicker }
            )
        }
    }
}

@Composable
private fun DockRail(
    selectedTab: Int,
    isAddActive: Boolean,
    opacity: Float,
    quickApps: List<GameDeckQuickApp>,
    onSelectTab: (Int) -> Unit,
    onQuickAppClick: (String) -> Unit,
    onAddAppClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val deckOpacity = opacity.coerceIn(0.45f, 1f)

    Surface(
        modifier = Modifier
            .width(DECK_RAIL_WIDTH_DP.dp)
            .fillMaxHeight()
            .shadow(14.dp, CircleShape),
        shape = CircleShape,
        color = scheme.surfaceContainerLow.copy(alpha = deckOpacity)
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
                    .background(scheme.outlineVariant.copy(alpha = 0.35f))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickApps.forEach { app ->
                    DockRailAppIcon(
                        app = app,
                        onClick = { onQuickAppClick(app.packageName) }
                    )
                }

                if (quickApps.size < MAX_DECK_QUICK_APPS || isAddActive) {
                    DockRailAddButton(
                        active = isAddActive,
                        onClick = onAddAppClick
                    )
                }
            }
        }
    }
}

@Composable
private fun DockRailAddButton(
    active: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 180),
        label = "railAddBg"
    )
    val tint by animateColorAsState(
        targetValue = if (active) scheme.onPrimary else scheme.primary,
        animationSpec = tween(durationMillis = 180),
        label = "railAddTint"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = MaterialShapes.Cookie4Sided.toShape(),
        color = bg
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Tambah aplikasi",
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
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
    val corner by animateDpAsState(
        targetValue = if (selected) 20.dp else 12.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "railPillCorner"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 52.dp else 46.dp,
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
        modifier = Modifier.size(48.dp),
        shape = MaterialShapes.Cookie4Sided.toShape(),
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
                        .size(31.dp)
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
    showAppPicker: Boolean,
    onCloseAppPicker: () -> Unit,
    onToggleQuickApp: (String) -> Unit,
    onProfileSelected: (String?) -> Unit,
    onSetAutoMode: (Boolean) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onBrightnessChanged: (Int, Boolean) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val deckOpacity = state.opacity.coerceIn(0.45f, 1f)
    val shape = RoundedCornerShape(28.dp)

    Surface(
        modifier = Modifier
            .width(cardWidth)
            .fillMaxHeight()
            .shadow(14.dp, shape),
        shape = shape,
        color = scheme.surfaceContainerLow.copy(alpha = deckOpacity)
    ) {
        val pageKey = if (showAppPicker) -1 else selectedTab
        AnimatedContent(
            targetState = pageKey,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            },
            label = "deckCardPage"
        ) { target ->
            when (target) {
                -1 -> QuickAppPickerDeckContent(
                    selectedPackages = state.quickApps.map { it.packageName }.toSet(),
                    onTogglePackage = onToggleQuickApp,
                    onBack = onCloseAppPicker
                )
                0 -> DashboardDeckContent(
                    state = state,
                    onProfileSelected = onProfileSelected,
                    onSetAutoMode = onSetAutoMode,
                    onRefreshRateSelected = onRefreshRateSelected,
                    onBrightnessChanged = onBrightnessChanged,
                    onVolumeChanged = onVolumeChanged,
                    onToggleMute = onToggleMute
                )
                else -> ToolsDeckContent(
                    state = state,
                    onToggleDnd = onToggleDnd,
                    onOpenManager = onOpenManager
                )
            }
        }
    }
}

@Composable
private fun QuickAppPickerDeckContent(
    selectedPackages: Set<String>,
    onTogglePackage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            ConfigRepository.loadInstalledApps(context.packageManager)
                .filter { it.packageName != context.packageName }
                .sortedWith(
                    compareByDescending<AppItem> { it.packageName in selectedPackages }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )
        }
        apps = loaded
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = scheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Aplikasi Cepat",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer
            ) {
                Text(
                    text = "${selectedPackages.size}/$MAX_DECK_QUICK_APPS",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularWavyProgressIndicator(modifier = Modifier.size(34.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val checked = app.packageName in selectedPackages
                    val enabled = checked || selectedPackages.size < MAX_DECK_QUICK_APPS
                    val icon = rememberAppIcon(app.packageName)
                    val rowBg by animateColorAsState(
                        targetValue = if (checked) {
                            scheme.secondaryContainer.copy(alpha = 0.65f)
                        } else {
                            scheme.surfaceContainer
                        },
                        animationSpec = tween(durationMillis = 160),
                        label = "pickerRowBg"
                    )

                    Surface(
                        onClick = { if (enabled) onTogglePackage(app.packageName) },
                        shape = RoundedCornerShape(16.dp),
                        color = rowBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(scheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                if (icon != null) {
                                    Image(
                                        bitmap = icon,
                                        contentDescription = app.name,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = app.name.take(1).uppercase(Locale.ROOT),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = app.name,
                                fontSize = 12.sp,
                                fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = checked,
                                enabled = enabled,
                                onCheckedChange = { if (enabled) onTogglePackage(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardDeckContent(
    state: GameDeckUiState,
    onProfileSelected: (String?) -> Unit,
    onSetAutoMode: (Boolean) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onBrightnessChanged: (Int, Boolean) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleMute: () -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (fps > 0) fps.toString() else "--",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = fpsColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "FPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                    if (fps > 0 && state.frametimeMs > 0f) {
                        Text(
                            text = String.format(Locale.US, " · %.1f ms", state.frametimeMs),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveRadialGauge(
                        percent = state.cpuPercent,
                        label = "CPU",
                        activeColor = scheme.primary,
                        badgeContainerColor = scheme.primaryContainer,
                        badgeContentColor = scheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .height(114.dp)
                    )
                    ExpressiveRadialGauge(
                        percent = state.gpuPercent,
                        label = "GPU",
                        activeColor = scheme.tertiary,
                        badgeContainerColor = scheme.tertiaryContainer,
                        badgeContentColor = scheme.onTertiaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .height(114.dp)
                    )
                }
            }
        }

        ProfileSelectorCard(
            state = state,
            onProfileSelected = onProfileSelected,
            onToggleAutoMode = { onSetAutoMode(!state.profileAutoMode) }
        )

        RefreshRateCard(
            modes = state.rrModes,
            selectedHz = state.selectedRrHz,
            liveHz = state.targetHz,
            onSelect = onRefreshRateSelected
        )

        BrightnessVolumeCard(
            brightnessPercent = state.brightnessPercent,
            volumePercent = state.volumePercent,
            volumeMuted = state.volumeMuted,
            onBrightnessChanged = onBrightnessChanged,
            onVolumeChanged = onVolumeChanged,
            onToggleMute = onToggleMute
        )
    }
}

@Composable
private fun ExpressiveRadialGauge(
    percent: Int,
    label: String,
    activeColor: Color,
    badgeContainerColor: Color,
    badgeContentColor: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val safePercent = percent.coerceIn(0, 100)
    val animatedPercent by animateFloatAsState(
        targetValue = safePercent.toFloat(),
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 340f),
        label = "gaugeSweepPercent"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val inactiveStrokeColor = scheme.surfaceContainerHighest
            val stopDotColor = scheme.onSurfaceVariant.copy(alpha = 0.7f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val scaleX = size.width / 140f
                val scaleY = size.height / 132f
                val s = min(scaleX, scaleY)

                val cx = size.width / 2f
                val cy = size.height * 0.54f
                val radius = 52f * s
                val strokeW = 12f * s

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
                    val pillW = 5f * s
                    val pillH = 14f * s
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(cx - pillW / 2f, cy - radius - pillH / 2f),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(2.5f * s, 2.5f * s)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$safePercent",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = scheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "%",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeColor,
                        modifier = Modifier.padding(start = 1.5.dp, bottom = 2.5.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.height(2.5.dp))
                Surface(
                    shape = CircleShape,
                    color = badgeContainerColor
                ) {
                    Text(
                        text = label,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeContentColor,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleAutoManualButton(
    autoMode: Boolean,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val pillBg by animateColorAsState(
        targetValue = if (autoMode) scheme.primary else scheme.tertiaryContainer,
        animationSpec = tween(durationMillis = 200),
        label = "singleModePillBg"
    )
    val pillText by animateColorAsState(
        targetValue = if (autoMode) scheme.onPrimary else scheme.onTertiaryContainer,
        animationSpec = tween(durationMillis = 200),
        label = "singleModePillText"
    )

    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = pillBg,
        contentColor = pillText
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (autoMode) "Auto" else "Manual",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ExpressiveToggleTile(
    active: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = if (active) 22.dp else 15.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "toggleTileCorner"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            active && !dimmed -> scheme.primaryContainer
            active && dimmed -> scheme.secondaryContainer.copy(alpha = 0.85f)
            else -> scheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = 200),
        label = "toggleTileContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            active && !dimmed -> scheme.onPrimaryContainer
            active && dimmed -> scheme.onSecondaryContainer
            dimmed -> scheme.onSurfaceVariant.copy(alpha = 0.65f)
            else -> scheme.onSurface
        },
        animationSpec = tween(durationMillis = 200),
        label = "toggleTileContent"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "toggleTilePressScale"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
private fun ProfileSelectorCard(
    state: GameDeckUiState,
    onProfileSelected: (String?) -> Unit,
    onToggleAutoMode: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val autoMode = state.profileAutoMode

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh
                ) {
                    Text(
                        text = state.profile,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                SingleAutoManualButton(
                    autoMode = autoMode,
                    onToggle = onToggleAutoMode
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                deckProfileOptions.forEach { option ->
                    val manualSelected = !autoMode && deckOverrideSelected(state.profileOverride, option)
                    val autoActive = autoMode && state.profile.trim().equals(option.fullLabel, ignoreCase = true)
                    val isHighlighted = manualSelected || autoActive

                    ExpressiveToggleTile(
                        active = isHighlighted,
                        dimmed = autoMode,
                        onClick = {
                            if (!autoMode) {
                                onProfileSelected(option.command)
                            } else {
                                onProfileSelected(null)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option.shortLabel,
                                fontSize = 10.sp,
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
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
private fun RefreshRateCard(
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
        label = "rrCardChevronRotation"
    )
    val options: List<Int?> = remember(modes) {
        buildList {
            add(null)
            addAll(modes.distinct().sorted())
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = 400f
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refresh Rate",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (selectedHz == null) "Auto · $liveHz Hz" else "$selectedHz Hz",
                            fontSize = 10.5.sp,
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
                    color = scheme.outlineVariant.copy(alpha = 0.25f),
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { hz ->
                        val isSelected = selectedHz == hz
                        val pillBg by animateColorAsState(
                            targetValue = if (isSelected) scheme.primary else scheme.surfaceContainerHigh,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "rrCardOptionBg"
                        )
                        val pillText by animateColorAsState(
                            targetValue = if (isSelected) scheme.onPrimary else scheme.onSurface,
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "rrCardOptionText"
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
                                fontSize = 11.sp,
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
private fun ExpressiveLinearSlider(
    value: Int,
    min: Int,
    max: Int,
    accentColor: Color,
    onValueChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var isDragging by remember { mutableStateOf(false) }

    val handleWidth by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "md3eSliderHandleWidth"
    )
    val handleScaleY by animateFloatAsState(
        targetValue = if (isDragging) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "md3eSliderHandleScaleY"
    )

    val span = (max - min).coerceAtLeast(1)
    val fraction = ((value - min).toFloat() / span.toFloat()).coerceIn(0f, 1f)

    fun emitFraction(rawFraction: Float, commit: Boolean) {
        val clamped = rawFraction.coerceIn(0f, 1f)
        val mapped = (min + clamped * span).roundToInt().coerceIn(min, max)
        onValueChange(mapped, commit)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(min, max) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        emitFraction(offset.x / size.width.toFloat().coerceAtLeast(1f), false)
                        val released = tryAwaitRelease()
                        isDragging = false
                        if (released) {
                            emitFraction(offset.x / size.width.toFloat().coerceAtLeast(1f), true)
                        }
                    }
                )
            }
            .pointerInput(min, max) {
                var lastFraction = fraction
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        lastFraction = offset.x / size.width.toFloat().coerceAtLeast(1f)
                        emitFraction(lastFraction, false)
                    },
                    onDragEnd = {
                        isDragging = false
                        emitFraction(lastFraction, true)
                    },
                    onDragCancel = {
                        isDragging = false
                        emitFraction(lastFraction, true)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        lastFraction = change.position.x / size.width.toFloat().coerceAtLeast(1f)
                        emitFraction(lastFraction, false)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val reservedForHandle = handleWidth + 8.dp
        val trackAvailable = (totalWidth - reservedForHandle).coerceAtLeast(0.dp)
        val activeWidth = trackAvailable * fraction
        val inactiveWidth = trackAvailable * (1f - fraction)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(activeWidth)
                    .height(20.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 12.dp,
                            bottomStart = 12.dp,
                            topEnd = 4.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(accentColor)
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(handleWidth)
                    .height(32.dp)
                    .graphicsLayer { scaleY = handleScaleY }
                    .clip(CircleShape)
                    .background(accentColor)
            )

            Box(
                modifier = Modifier
                    .width(inactiveWidth)
                    .height(20.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp,
                            bottomStart = 4.dp,
                            topEnd = 12.dp,
                            bottomEnd = 12.dp
                        )
                    )
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (inactiveWidth >= 10.dp) {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(scheme.onSurfaceVariant.copy(alpha = 0.65f))
                    )
                }
            }
        }
    }
}

@Composable
private fun BrightnessVolumeCard(
    brightnessPercent: Int,
    volumePercent: Int,
    volumeMuted: Boolean,
    onBrightnessChanged: (Int, Boolean) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleMute: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val effectiveVol = if (volumeMuted) 0 else volumePercent

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BrightnessMedium,
                        contentDescription = "Brightness",
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Brightness",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$brightnessPercent%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                ExpressiveLinearSlider(
                    value = brightnessPercent,
                    min = 5,
                    max = 100,
                    accentColor = scheme.primary,
                    onValueChange = { pct, commit -> onBrightnessChanged(pct, commit) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (volumeMuted || effectiveVol == 0) {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        },
                        contentDescription = "Suara",
                        tint = if (volumeMuted || effectiveVol == 0) scheme.error else scheme.tertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onToggleMute)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Suara",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$effectiveVol%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (volumeMuted || effectiveVol == 0) scheme.error else scheme.tertiary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                ExpressiveLinearSlider(
                    value = effectiveVol,
                    min = 0,
                    max = 100,
                    accentColor = scheme.tertiary,
                    onValueChange = { pct, _ -> onVolumeChanged(pct) }
                )
            }
        }
    }
}

@Composable
private fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val trackColor by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.surfaceContainerHighest,
        animationSpec = tween(durationMillis = 220),
        label = "expressiveSwitchTrack"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (checked) 22.dp else 15.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "expressiveSwitchThumbSize"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "expressiveSwitchThumbOffset"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "expressiveSwitchThumbColor"
    )

    Box(
        modifier = modifier
            .requiredSize(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCheckedChange
            )
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(13.dp)
                )
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
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tools",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1
                )

                ExpressiveToggleTile(
                    active = state.dndEnabled,
                    dimmed = false,
                    onClick = onToggleDnd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Jangan Ganggu",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = dndText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        ExpressiveSwitch(
                            checked = state.dndEnabled,
                            onCheckedChange = onToggleDnd
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
                .height(46.dp),
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
                fontSize = 12.sp,
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
