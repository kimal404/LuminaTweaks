@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.luminatweaks.gamedeck

import android.graphics.Bitmap
import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.model.AppItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.Brush

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
    onMusicPlayPause: () -> Unit = {},
    onMusicNext: () -> Unit = {},
    onMusicPrevious: () -> Unit = {},
    onMusicSeek: (Long) -> Unit = {},
    onOpenMusicApp: () -> Unit = {},
    onToggleDashboardWidget: (String) -> Unit = {},
    onResetDashboardWidgets: () -> Unit = {},
    onMoveDashboardWidget: (Int, Int) -> Unit = { _, _ -> },
    onToggleKeepScreenOn: () -> Unit = {},
    onToggleDnd: () -> Unit,
    onToggleAutoBrightnessLock: () -> Unit,
    onToggleRotationLock: () -> Unit,
    onClearRam: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onToggleQuickApp: (String) -> Unit,
    onAppPickerVisibilityChanged: (Boolean) -> Unit = {},
    onWidgetEditorVisibilityChanged: (Boolean) -> Unit = {},
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
                onMusicPlayPause = onMusicPlayPause,
                onMusicNext = onMusicNext,
                onMusicPrevious = onMusicPrevious,
                onMusicSeek = onMusicSeek,
                onOpenMusicApp = onOpenMusicApp,
                onToggleDashboardWidget = onToggleDashboardWidget,
                onResetDashboardWidgets = onResetDashboardWidgets,
                onMoveDashboardWidget = onMoveDashboardWidget,
                onToggleKeepScreenOn = onToggleKeepScreenOn,
                onToggleDnd = onToggleDnd,
                onToggleAutoBrightnessLock = onToggleAutoBrightnessLock,
                onToggleRotationLock = onToggleRotationLock,
                onClearRam = onClearRam,
                onTakeScreenshot = onTakeScreenshot,
                onQuickAppClick = onQuickAppClick,
                onToggleQuickApp = onToggleQuickApp,
                onAppPickerVisibilityChanged = onAppPickerVisibilityChanged,
                onWidgetEditorVisibilityChanged = onWidgetEditorVisibilityChanged,
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
    onMusicPlayPause: () -> Unit,
    onMusicNext: () -> Unit,
    onMusicPrevious: () -> Unit,
    onMusicSeek: (Long) -> Unit,
    onOpenMusicApp: () -> Unit,
    onToggleDashboardWidget: (String) -> Unit,
    onResetDashboardWidgets: () -> Unit,
    onMoveDashboardWidget: (Int, Int) -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleAutoBrightnessLock: () -> Unit,
    onToggleRotationLock: () -> Unit,
    onClearRam: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onQuickAppClick: (String) -> Unit,
    onToggleQuickApp: (String) -> Unit,
    onAppPickerVisibilityChanged: (Boolean) -> Unit,
    onWidgetEditorVisibilityChanged: (Boolean) -> Unit,
    onOpenManager: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var showAppPicker by remember { mutableStateOf(false) }
    var showWidgetEditor by remember { mutableStateOf(false) }
    LaunchedEffect(showAppPicker) {
        onAppPickerVisibilityChanged(showAppPicker)
    }
    LaunchedEffect(showWidgetEditor) {
        onWidgetEditorVisibilityChanged(showWidgetEditor)
    }

    val isLeft = state.side == GameDeckSide.LEFT
    val closeThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    val screenWidthDp = with(LocalDensity.current) {
        LocalContext.current.resources.displayMetrics.widthPixels.toDp()
    }
    val maxWorkspaceWidth = (screenWidthDp - 24.dp).coerceAtLeast(320.dp)
    val cardWidth: Dp = (maxWorkspaceWidth - DECK_RAIL_WIDTH_DP.dp - DECK_RAIL_GAP_DP.dp)
        .coerceIn(260.dp, DECK_CARD_WIDTH_DP.dp)

    val handleTabSelect: (Int) -> Unit = { tab ->
        if (showAppPicker || showWidgetEditor) {
            showAppPicker = false
            showWidgetEditor = false
            coroutineScope.launch { pagerState.scrollToPage(tab) }
        } else if (pagerState.currentPage == tab) {
            onCollapse()
        } else {
            coroutineScope.launch { pagerState.animateScrollToPage(tab) }
        }
    }

    val railSwipeModifier = Modifier.pointerInput(isLeft, closeThreshold) {
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
    }

    if (showWidgetEditor) {
        FullScreenWidgetEditorOverlay(
            state = state,
            onToggleWidget = onToggleDashboardWidget,
            onMoveWidget = onMoveDashboardWidget,
            onResetWidgets = onResetDashboardWidgets,
            onDone = { showWidgetEditor = false }
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(DECK_RAIL_GAP_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLeft) {
            DockRail(
                modifier = railSwipeModifier,
                selectedTab = if (showAppPicker) -1 else pagerState.targetPage,
                isAddActive = showAppPicker,
                opacity = state.opacity,
                quickApps = state.quickApps,
                onSelectTab = handleTabSelect,
                onQuickAppClick = onQuickAppClick,
                onAddAppClick = {
                    showWidgetEditor = false
                    showAppPicker = !showAppPicker
                }
            )
            DeckCard(
                state = state,
                cardWidth = cardWidth,
                pagerState = pagerState,
                showAppPicker = showAppPicker,
                onOpenWidgetEditor = { showWidgetEditor = true },
                onCloseAppPicker = { showAppPicker = false },
                onToggleQuickApp = onToggleQuickApp,
                onProfileSelected = onProfileSelected,
                onSetAutoMode = onSetAutoMode,
                onRefreshRateSelected = onRefreshRateSelected,
                onBrightnessChanged = onBrightnessChanged,
                onVolumeChanged = onVolumeChanged,
                onToggleMute = onToggleMute,
                onMusicPlayPause = onMusicPlayPause,
                onMusicNext = onMusicNext,
                onMusicPrevious = onMusicPrevious,
                onMusicSeek = onMusicSeek,
                onOpenMusicApp = onOpenMusicApp,
                onToggleKeepScreenOn = onToggleKeepScreenOn,
                onToggleDnd = onToggleDnd,
                onToggleAutoBrightnessLock = onToggleAutoBrightnessLock,
                onToggleRotationLock = onToggleRotationLock,
                onClearRam = onClearRam,
                onTakeScreenshot = onTakeScreenshot,
                onOpenManager = onOpenManager
            )
        } else {
            DeckCard(
                state = state,
                cardWidth = cardWidth,
                pagerState = pagerState,
                showAppPicker = showAppPicker,
                onOpenWidgetEditor = { showWidgetEditor = true },
                onCloseAppPicker = { showAppPicker = false },
                onToggleQuickApp = onToggleQuickApp,
                onProfileSelected = onProfileSelected,
                onSetAutoMode = onSetAutoMode,
                onRefreshRateSelected = onRefreshRateSelected,
                onBrightnessChanged = onBrightnessChanged,
                onVolumeChanged = onVolumeChanged,
                onToggleMute = onToggleMute,
                onMusicPlayPause = onMusicPlayPause,
                onMusicNext = onMusicNext,
                onMusicPrevious = onMusicPrevious,
                onMusicSeek = onMusicSeek,
                onOpenMusicApp = onOpenMusicApp,
                onToggleKeepScreenOn = onToggleKeepScreenOn,
                onToggleDnd = onToggleDnd,
                onToggleAutoBrightnessLock = onToggleAutoBrightnessLock,
                onToggleRotationLock = onToggleRotationLock,
                onClearRam = onClearRam,
                onTakeScreenshot = onTakeScreenshot,
                onOpenManager = onOpenManager
            )
            DockRail(
                modifier = railSwipeModifier,
                selectedTab = if (showAppPicker) -1 else pagerState.targetPage,
                isAddActive = showAppPicker,
                opacity = state.opacity,
                quickApps = state.quickApps,
                onSelectTab = handleTabSelect,
                onQuickAppClick = onQuickAppClick,
                onAddAppClick = {
                    showWidgetEditor = false
                    showAppPicker = !showAppPicker
                }
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
    onAddAppClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val deckOpacity = opacity.coerceIn(0.45f, 1f)

    Surface(
        modifier = modifier
            .width(DECK_RAIL_WIDTH_DP.dp)
            .fillMaxHeight()
            .shadow(14.dp, CircleShape),
        shape = CircleShape,
        color = scheme.surfaceContainerLow
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
    pagerState: PagerState,
    showAppPicker: Boolean,
    onOpenWidgetEditor: () -> Unit,
    onCloseAppPicker: () -> Unit,
    onToggleQuickApp: (String) -> Unit,
    onProfileSelected: (String?) -> Unit,
    onSetAutoMode: (Boolean) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    onBrightnessChanged: (Int, Boolean) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onMusicPlayPause: () -> Unit,
    onMusicNext: () -> Unit,
    onMusicPrevious: () -> Unit,
    onMusicSeek: (Long) -> Unit,
    onOpenMusicApp: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleAutoBrightnessLock: () -> Unit,
    onToggleRotationLock: () -> Unit,
    onClearRam: () -> Unit,
    onTakeScreenshot: () -> Unit,
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
        color = scheme.surfaceContainerLow
    ) {
        val modalMode = if (showAppPicker) 1 else 0
        AnimatedContent(
            targetState = modalMode,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
            },
            label = "deckCardModalMode"
        ) { mode ->
            when (mode) {
                1 -> QuickAppPickerDeckContent(
                    selectedPackages = state.quickApps.map { it.packageName }.toSet(),
                    onTogglePackage = onToggleQuickApp,
                    onBack = onCloseAppPicker
                )
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { it }
                ) { page ->
                    if (page == 0) {
                        DashboardDeckContent(
                            state = state,
                            onProfileSelected = onProfileSelected,
                            onSetAutoMode = onSetAutoMode,
                            onRefreshRateSelected = onRefreshRateSelected,
                            onBrightnessChanged = onBrightnessChanged,
                            onVolumeChanged = onVolumeChanged,
                            onToggleMute = onToggleMute,
                            onMusicPlayPause = onMusicPlayPause,
                            onMusicNext = onMusicNext,
                            onMusicPrevious = onMusicPrevious,
                            onMusicSeek = onMusicSeek,
                            onOpenMusicApp = onOpenMusicApp,
                            onToggleKeepScreenOn = onToggleKeepScreenOn,
                            onOpenWidgetEditor = onOpenWidgetEditor
                        )
                    } else {
                        ToolsDeckContent(
                            state = state,
                            onToggleDnd = onToggleDnd,
                            onToggleAutoBrightnessLock = onToggleAutoBrightnessLock,
                            onToggleRotationLock = onToggleRotationLock,
                            onClearRam = onClearRam,
                            onTakeScreenshot = onTakeScreenshot,
                            onOpenManager = onOpenManager
                        )
                    }
                }
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
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val systemMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        val loaded = withContext(Dispatchers.IO) {
            ConfigRepository.loadInstalledApps(pm)
                .filter { item ->
                    if (item.packageName == selfPkg) return@filter false
                    if (item.packageName in selectedPackages) return@filter true
                    if (pm.getLaunchIntentForPackage(item.packageName) == null) return@filter false
                    val info = runCatching { pm.getApplicationInfo(item.packageName, 0) }.getOrNull()
                        ?: return@filter false
                    (info.flags and systemMask) == 0
                }
                .sortedWith(
                    compareByDescending<AppItem> { it.packageName in selectedPackages }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )
        }
        apps = loaded
        loading = false
    }

    val filteredApps = remember(apps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = scheme.surfaceContainer
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
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = CircleShape,
                color = scheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Cari aplikasi...",
                                fontSize = 11.5.sp,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                color = scheme.onSurface,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(scheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Hapus",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer
            ) {
                Text(
                    text = "${selectedPackages.size}/$MAX_DECK_QUICK_APPS",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainer
        ) {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(34.dp))
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "Aplikasi tidak ditemukan" else "Tidak ada aplikasi pengguna",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val checked = app.packageName in selectedPackages
                        val enabled = checked || selectedPackages.size < MAX_DECK_QUICK_APPS
                        QuickAppGridTile(
                            app = app,
                            checked = checked,
                            enabled = enabled,
                            onClick = { onTogglePackage(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAppGridTile(
    app: AppItem,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val icon = rememberAppIcon(app.packageName)

    val cornerRadius by animateDpAsState(
        targetValue = if (checked) 22.dp else 15.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "appGridTileCorner"
    )
    val tileBg by animateColorAsState(
        targetValue = if (checked) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 180),
        label = "appGridTileBg"
    )
    val tileText by animateColorAsState(
        targetValue = if (checked) scheme.onPrimaryContainer else scheme.onSurface,
        animationSpec = tween(durationMillis = 180),
        label = "appGridTileText"
    )
    val badgeBg by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.surfaceContainerHighest.copy(alpha = 0.75f),
        animationSpec = tween(durationMillis = 180),
        label = "appGridBadgeBg"
    )

    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(cornerRadius),
        color = tileBg,
        contentColor = tileText,
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.42f }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (checked) {
                                scheme.surfaceContainerLowest.copy(alpha = 0.35f)
                            } else {
                                scheme.surfaceContainerHighest
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = app.name,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Text(
                            text = app.name.take(1).uppercase(Locale.ROOT),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = tileText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = app.name,
                    fontSize = 10.5.sp,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                    color = tileText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryCapsulePill(
    percent: Int,
    isCharging: Boolean,
    isBatterySaver: Boolean,
    isEcoProfile: Boolean
) {
    val safePercent = percent.coerceIn(0, 100)
    val fillFraction by animateFloatAsState(
        targetValue = (safePercent / 100f).coerceIn(0.08f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "batteryPillFill"
    )
    val isLow = !isCharging && safePercent <= 20
    val isSaver = !isCharging && !isLow && (isBatterySaver || isEcoProfile)

    val targetFillColor = when {
        isCharging -> Color(0xFF22C55E)
        isLow -> Color(0xFFEF4444)
        isSaver -> Color(0xFFF97316)
        else -> Color(0xFFFFFFFF)
    }
    val fillColor by animateColorAsState(
        targetValue = targetFillColor,
        animationSpec = tween(durationMillis = 220),
        label = "batteryPillColor"
    )
    val textMeasurer = rememberTextMeasurer()
    val pillTextStyle = remember {
        TextStyle(
            fontSize = 8.5.sp,
            lineHeight = 8.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.3).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    }
    val percentLabel = safePercent.toString()
    val textLayout = remember(percentLabel) {
        textMeasurer.measure(percentLabel, style = pillTextStyle)
    }

    Canvas(
        modifier = Modifier
            .width(if (isCharging) 29.dp else 24.dp)
            .height(12.dp)
    ) {
        val capsuleW = 24.dp.toPx()
        val capsuleH = size.height
        val radiusPx = 3.8.dp.toPx()
        val capsulePath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = capsuleW,
                    bottom = capsuleH,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            )
        }

        clipPath(capsulePath) {
            drawRect(
                color = Color(0xFF9CA3AF),
                size = Size(capsuleW, capsuleH)
            )
            drawRect(
                color = fillColor,
                size = Size(capsuleW * fillFraction, capsuleH)
            )
            val tx = (capsuleW - textLayout.size.width) / 2f
            val ty = (capsuleH - textLayout.size.height) / 2f
            drawText(
                textLayoutResult = textLayout,
                color = Color(0xFF101318),
                topLeft = Offset(tx, ty)
            )
        }

        if (isCharging) {
            val bx = capsuleW - 1.6.dp.toPx()
            val by = 1.2.dp.toPx()
            val bw = 6.dp.toPx()
            val bh = 9.6.dp.toPx()
            val boltPath = Path().apply {
                moveTo(bx + bw * 0.62f, by)
                lineTo(bx, by + bh * 0.56f)
                lineTo(bx + bw * 0.44f, by + bh * 0.56f)
                lineTo(bx + bw * 0.34f, by + bh)
                lineTo(bx + bw, by + bh * 0.42f)
                lineTo(bx + bw * 0.55f, by + bh * 0.42f)
                close()
            }
            drawPath(
                path = boltPath,
                color = Color(0xFF141218),
                style = Stroke(width = 1.8.dp.toPx(), join = StrokeJoin.Round)
            )
            drawPath(
                path = boltPath,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FullScreenWidgetEditorOverlay(
    state: GameDeckUiState,
    onToggleWidget: (String) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onResetWidgets: () -> Unit,
    onDone: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    data class WidgetEditorItem(
        val key: String,
        val title: String,
        val icon: ImageVector,
        val enabled: Boolean
    )

    val orderedKeys = state.widgetOrder.ifEmpty { DEFAULT_DASHBOARD_WIDGET_ORDER }
    val allItems = orderedKeys.mapNotNull { key ->
        when (key) {
            "telemetry" -> WidgetEditorItem(key, "Telemetri & CPU/GPU", Icons.Rounded.Speed, state.showTelemetryWidget)
            "profile" -> WidgetEditorItem(key, "Profil Performa", Icons.Rounded.Tune, state.showProfileWidget)
            "music" -> WidgetEditorItem(key, "Pemutar Musik", Icons.Rounded.MusicNote, state.showMusicWidget)
            "refresh_rate" -> WidgetEditorItem(key, "Refresh Rate", Icons.Rounded.Refresh, state.showRefreshRateWidget)
            "sliders" -> WidgetEditorItem(key, "Brightness & Suara", Icons.Rounded.BrightnessMedium, state.showSlidersWidget)
            else -> null
        }
    }
    val activeItems = allItems.filter { it.enabled }
    val libraryItems = allItems.filter { !it.enabled }
    val isDefaultConfig = libraryItems.isEmpty() && orderedKeys == DEFAULT_DASHBOARD_WIDGET_ORDER

    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val stepThresholdPx = with(LocalDensity.current) { 78.dp.toPx() }
    val currentActiveItems by rememberUpdatedState(activeItems)
    val currentOrderedKeys by rememberUpdatedState(orderedKeys)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Atur Widget",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Seret untuk ubah urutan atau tambah dari pustaka",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    onClick = onDone,
                    shape = CircleShape,
                    color = scheme.primary,
                    contentColor = scheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Selesai",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(0.57f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(24.dp),
                    color = scheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    if (activeItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Minimal 1 widget aktif",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            activeItems.forEach { item ->
                                val isDragging = draggedKey == item.key
                                val dragScale by animateFloatAsState(
                                    targetValue = if (isDragging) 0.99f else 1f,
                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                                    label = "fullEditorDragScale"
                                )
                                val dragElevation by animateDpAsState(
                                    targetValue = if (isDragging) 10.dp else 0.dp,
                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                                    label = "fullEditorDragElev"
                                )
                                val onReorderDelta: (Float) -> Unit = { deltaY ->
                                    dragOffsetY += deltaY
                                    val listVis = currentActiveItems
                                    val listGlob = currentOrderedKeys
                                    val curVisIdx = listVis.indexOfFirst { it.key == item.key }
                                    val curGlobIdx = listGlob.indexOf(item.key)
                                    if (dragOffsetY > stepThresholdPx && curVisIdx in 0 until listVis.lastIndex) {
                                        val targetGlobIdx = listGlob.indexOf(listVis[curVisIdx + 1].key)
                                        if (curGlobIdx >= 0 && targetGlobIdx >= 0) {
                                            onMoveWidget(curGlobIdx, targetGlobIdx)
                                            dragOffsetY -= stepThresholdPx
                                        }
                                    } else if (dragOffsetY < -stepThresholdPx && curVisIdx > 0) {
                                        val targetGlobIdx = listGlob.indexOf(listVis[curVisIdx - 1].key)
                                        if (curGlobIdx >= 0 && targetGlobIdx >= 0) {
                                            onMoveWidget(curGlobIdx, targetGlobIdx)
                                            dragOffsetY += stepThresholdPx
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isDragging) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
                                    shadowElevation = dragElevation,
                                    border = BorderStroke(
                                        width = if (isDragging) 1.5.dp else 1.dp,
                                        color = if (isDragging) scheme.primary else scheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragging) 2f else 0f)
                                        .graphicsLayer {
                                            translationY = if (isDragging) dragOffsetY else 0f
                                            scaleX = dragScale
                                            scaleY = dragScale
                                        }
                                        .pointerInput(item.key) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedKey = item.key
                                                    dragOffsetY = 0f
                                                },
                                                onDragEnd = {
                                                    draggedKey = null
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    draggedKey = null
                                                    dragOffsetY = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    onReorderDelta(dragAmount.y)
                                                }
                                            )
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(11.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(end = 30.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isDragging) {
                                                                scheme.primary.copy(alpha = 0.2f)
                                                            } else {
                                                                scheme.surfaceContainer
                                                            }
                                                        )
                                                        .pointerInput(item.key) {
                                                            detectVerticalDragGestures(
                                                                onDragStart = {
                                                                    draggedKey = item.key
                                                                    dragOffsetY = 0f
                                                                },
                                                                onDragEnd = {
                                                                    draggedKey = null
                                                                    dragOffsetY = 0f
                                                                },
                                                                onDragCancel = {
                                                                    draggedKey = null
                                                                    dragOffsetY = 0f
                                                                },
                                                                onVerticalDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    onReorderDelta(dragAmount)
                                                                }
                                                            )
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DragHandle,
                                                        contentDescription = "Geser urutan",
                                                        tint = if (isDragging) scheme.primary else scheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Text(
                                                    text = item.title,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = scheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            WidgetBlueprintPreview(widgetKey = item.key)
                                        }

                                        Surface(
                                            onClick = { onToggleWidget(item.key) },
                                            enabled = activeItems.size > 1,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(26.dp),
                                            shape = CircleShape,
                                            color = if (activeItems.size > 1) {
                                                scheme.errorContainer
                                            } else {
                                                scheme.surfaceContainer
                                            },
                                            contentColor = if (activeItems.size > 1) {
                                                scheme.onErrorContainer
                                            } else {
                                                scheme.onSurfaceVariant.copy(alpha = 0.35f)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Remove,
                                                    contentDescription = "Sembunyikan ${item.title}",
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(0.43f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(24.dp),
                    color = scheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pustaka Widget",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = onResetWidgets,
                                modifier = Modifier.size(30.dp),
                                shape = CircleShape,
                                color = if (isDefaultConfig) scheme.surfaceContainer else scheme.secondaryContainer,
                                contentColor = if (isDefaultConfig) scheme.onSurfaceVariant else scheme.onSecondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestartAlt,
                                        contentDescription = "Reset susunan widget",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (libraryItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(scheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = scheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = "Semua widget aktif",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Ketuk tombol - pada widget aktif untuk menyimpannya di sini.",
                                        fontSize = 10.sp,
                                        color = scheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                libraryItems.forEach { item ->
                                    Surface(
                                        onClick = { onToggleWidget(item.key) },
                                        shape = RoundedCornerShape(18.dp),
                                        color = scheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(scheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = null,
                                                    tint = scheme.onPrimaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = scheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Ketuk + untuk pasang",
                                                    fontSize = 9.5.sp,
                                                    color = scheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(scheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = "Pasang ${item.title}",
                                                    tint = scheme.onPrimary,
                                                    modifier = Modifier.size(16.dp)
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
        }
    }
}

@Composable
private fun WidgetBlueprintPreview(widgetKey: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        when (widgetKey) {
            "telemetry" -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "120",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = scheme.primary
                    )
                    Text(
                        text = "FPS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "CPU",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.tertiaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "GPU",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onTertiaryContainer
                    )
                }
            }

            "profile" -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Eco", "Balanced", "Perf", "Lite").forEachIndexed { idx, label ->
                    val active = idx == 2
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (active) scheme.primaryContainer else scheme.surfaceContainerHigh)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 8.5.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            "music" -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurface.copy(alpha = 0.72f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.36f)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(scheme.onSurfaceVariant.copy(alpha = 0.5f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Pause, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(12.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(scheme.primary)
                        )
                    }
                    Icon(Icons.Rounded.SkipNext, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                }
            }

            "refresh_rate" -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IosRecentAppsIcon(tint = scheme.primary, containerColor = scheme.surfaceContainer, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Refresh Rate",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "120 Hz",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer
                    )
                }
            }

            else -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.BrightnessMedium, null, tint = scheme.primary, modifier = Modifier.size(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(scheme.primary)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = scheme.tertiary, modifier = Modifier.size(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.48f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(scheme.tertiary)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTrackDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

@Composable
private fun ExpressiveWavySeekbar(
    progress: Float,
    isPlaying: Boolean,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val waveAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "wavySeekAmp"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "wavySeekPhase")
    val phaseRad by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavySeekPhaseRad"
    )
    var lastDragFraction by remember { mutableFloatStateOf(clampedProgress) }

    Canvas(
        modifier = modifier
            .height(20.dp)
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                onSeekFraction((offset.x / w).coerceIn(0f, 1f))
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    lastDragFraction = (offset.x / w).coerceIn(0f, 1f)
                                    onSeekFraction(lastDragFraction)
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    lastDragFraction = (change.position.x / w).coerceIn(0f, 1f)
                                    onSeekFraction(lastDragFraction)
                                }
                            )
                        }
                } else {
                    Modifier
                }
            )
    ) {
        val w = size.width
        val centerY = size.height / 2f
        val thumbRadius = 4.8.dp.toPx()
        val strokeW = 2.8.dp.toPx()
        val usableStart = thumbRadius
        val usableEnd = (w - thumbRadius).coerceAtLeast(usableStart + 1f)
        val usableSpan = usableEnd - usableStart
        val thumbX = usableStart + usableSpan * clampedProgress

        val inactiveStart = (thumbX + thumbRadius + 2.5.dp.toPx()).coerceAtMost(usableEnd)
        if (inactiveStart < usableEnd) {
            drawLine(
                color = inactiveColor,
                start = Offset(inactiveStart, centerY),
                end = Offset(usableEnd, centerY),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }

        val activeEnd = (thumbX - thumbRadius - 1.5.dp.toPx()).coerceAtLeast(usableStart)
        if (activeEnd > usableStart) {
            val maxAmp = 2.3.dp.toPx() * waveAmplitude
            val wavelengthPx = 15.dp.toPx()
            val path = Path()
            var x = usableStart
            val step = 1.8.dp.toPx()
            var first = true
            while (x <= activeEnd) {
                val angle: Float = ((x - usableStart) / wavelengthPx) * (2f * PI.toFloat()) - phaseRad
                val y: Float = centerY + sin(angle).toFloat() * maxAmp
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }
            val endAngle: Float = ((activeEnd - usableStart) / wavelengthPx) * (2f * PI.toFloat()) - phaseRad
            path.lineTo(activeEnd, centerY + sin(endAngle).toFloat() * maxAmp)

            drawPath(
                path = path,
                color = activeColor,
                style = Stroke(
                    width = strokeW,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = Offset(thumbX, centerY)
        )
    }
}

@Composable
private fun MusicPlayerWidgetCard(
    title: String,
    artist: String,
    packageName: String?,
    albumArt: Bitmap?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenApp: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val appIcon = rememberAppIcon(packageName)
    val artBitmap = remember(albumArt) {
        albumArt?.takeIf { !it.isRecycled }?.asImageBitmap()
    }
    val displayTitle = title.ifBlank { "Tidak ada musik diputar" }
    val displaySubtitle = when {
        artist.isNotBlank() -> artist
        title.isNotBlank() -> "Media aktif"
        else -> "Putar musik di aplikasi pemutar"
    }

    var livePosMs by remember(positionMs, durationMs) { mutableLongStateOf(positionMs) }
    var isSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, positionMs, durationMs) {
        livePosMs = positionMs
        if (isPlaying && durationMs > 0L) {
            while (true) {
                delay(500L)
                if (!isSeeking) {
                    livePosMs = (livePosMs + 500L).coerceAtMost(durationMs)
                }
            }
        }
    }

    val progressFraction = if (durationMs > 0L) {
        (livePosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val playBtnBg by animateColorAsState(
        targetValue = if (isPlaying) scheme.primary else scheme.secondaryContainer,
        animationSpec = tween(durationMillis = 180),
        label = "musicPlayBtnBg"
    )
    val playBtnTint by animateColorAsState(
        targetValue = if (isPlaying) scheme.onPrimary else scheme.onSecondaryContainer,
        animationSpec = tween(durationMillis = 180),
        label = "musicPlayBtnTint"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceContainerHigh)
                        .clickable(enabled = !packageName.isNullOrBlank()) { onOpenApp() },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = artBitmap,
                        animationSpec = tween(durationMillis = 240),
                        label = "compactAlbumArtFade"
                    ) { bmp ->
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (appIcon != null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = appIcon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = if (isPlaying) scheme.primary else scheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    if (artBitmap != null && appIcon != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(scheme.surfaceContainerLowest.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !packageName.isNullOrBlank()) { onOpenApp() },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = displayTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displaySubtitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isPlaying) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onPrevious,
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                        color = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Sebelumnya",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onPlayPause,
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = playBtnBg,
                        contentColor = playBtnTint
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda" else "Putar",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onNext,
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                        color = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Berikutnya",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            if (durationMs > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTrackDuration(livePosMs),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    ExpressiveWavySeekbar(
                        progress = progressFraction,
                        isPlaying = isPlaying,
                        enabled = durationMs > 0L,
                        activeColor = scheme.primary,
                        inactiveColor = scheme.surfaceContainerHighest,
                        onSeekFraction = { frac ->
                            val target = (frac * durationMs).toLong()
                            livePosMs = target
                            onSeek(target)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatTrackDuration(durationMs),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
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
    onToggleMute: () -> Unit,
    onMusicPlayPause: () -> Unit,
    onMusicNext: () -> Unit,
    onMusicPrevious: () -> Unit,
    onMusicSeek: (Long) -> Unit,
    onOpenMusicApp: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onOpenWidgetEditor: () -> Unit
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
        val orderedKeys = state.widgetOrder.ifEmpty { DEFAULT_DASHBOARD_WIDGET_ORDER }
        orderedKeys.forEach { widgetKey ->
            when (widgetKey) {
                "telemetry" -> {
                if (state.showTelemetryWidget) {
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
                                    modifier = Modifier.size(19.dp)
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
                                Spacer(modifier = Modifier.width(8.dp))
                                BatteryCapsulePill(
                                    percent = state.batteryPercent,
                                    isCharging = state.isCharging,
                                    isBatterySaver = state.isBatterySaver,
                                    isEcoProfile = state.profile.trim().equals("Eco", ignoreCase = true)
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
                }
                }
                "profile" -> {
                if (state.showProfileWidget) {
                    ProfileSelectorCard(
                        state = state,
                        onProfileSelected = onProfileSelected,
                        onToggleAutoMode = { onSetAutoMode(!state.profileAutoMode) }
                    )
                }
                }
                "music" -> {
                if (state.showMusicWidget) {
                    MusicPlayerWidgetCard(
                        title = state.musicTitle,
                        artist = state.musicArtist,
                        packageName = state.musicPackage,
                        albumArt = state.musicArt,
                        isPlaying = state.isMusicPlaying,
                        positionMs = state.musicPositionMs,
                        durationMs = state.musicDurationMs,
                        onPrevious = onMusicPrevious,
                        onPlayPause = onMusicPlayPause,
                        onNext = onMusicNext,
                        onSeek = onMusicSeek,
                        onOpenApp = onOpenMusicApp
                    )
                }
                }
                "refresh_rate" -> {
                    if (state.showRefreshRateWidget) {
                        RefreshRateCard(
                            modes = state.rrModes,
                            selectedHz = state.selectedRrHz,
                            liveHz = state.targetHz,
                            onSelect = onRefreshRateSelected
                        )
                    }
                }
                "sliders" -> {
                if (state.showSlidersWidget) {
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
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                onClick = onOpenWidgetEditor,
                shape = CircleShape,
                color = scheme.surfaceContainer,
                contentColor = scheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Atur Widget Dashboard",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
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
private fun IosRecentAppsIcon(
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokePx = 1.45.dp.toPx()

        fun drawCard(leftF: Float, topF: Float, rightF: Float, bottomF: Float, alpha: Float) {
            val tl = Offset(w * leftF, h * topF)
            val sz = Size(w * (rightF - leftF), h * (bottomF - topF))
            val cr = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            drawRoundRect(color = containerColor, topLeft = tl, size = sz, cornerRadius = cr)
            drawRoundRect(color = tint.copy(alpha = alpha * 0.24f), topLeft = tl, size = sz, cornerRadius = cr)
            drawRoundRect(
                color = tint.copy(alpha = alpha),
                topLeft = tl,
                size = sz,
                cornerRadius = cr,
                style = Stroke(width = strokePx)
            )
        }

        drawCard(0.06f, 0.22f, 0.44f, 0.78f, 0.50f)
        drawCard(0.28f, 0.15f, 0.68f, 0.85f, 0.75f)
        drawCard(0.50f, 0.08f, 0.94f, 0.92f, 1.00f)
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
    var menuExpanded by remember { mutableStateOf(false) }
    val sortedModes = remember(modes) { modes.distinct().sorted() }
    val displayLiveHz = if (liveHz > 0) liveHz else (selectedHz ?: 60)
    val pillLabel = if (selectedHz == null) {
        "Auto · $displayLiveHz Hz"
    } else {
        "$selectedHz Hz"
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosRecentAppsIcon(
                tint = scheme.primary,
                containerColor = scheme.surfaceContainer
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
            Box {
                Surface(
                    onClick = { menuExpanded = true },
                    shape = CircleShape,
                    color = if (selectedHz != null) {
                        scheme.primaryContainer
                    } else {
                        scheme.secondaryContainer
                    },
                    contentColor = if (selectedHz != null) {
                        scheme.onPrimaryContainer
                    } else {
                        scheme.onSecondaryContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = pillLabel,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Auto ($displayLiveHz Hz)",
                                fontWeight = if (selectedHz == null) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onSelect(null)
                        }
                    )
                    sortedModes.forEach { hz ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "$hz Hz",
                                    fontWeight = if (selectedHz == hz) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onSelect(hz)
                            }
                        )
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
private fun CompactToolBentoTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    ExpressiveToggleTile(
        active = active,
        dimmed = false,
        onClick = onClick,
        modifier = modifier.height(76.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) {
                                scheme.primary.copy(alpha = 0.22f)
                            } else {
                                scheme.surfaceContainerHighest
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (active) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (active) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = if (active) {
                        scheme.onPrimaryContainer.copy(alpha = 0.85f)
                    } else {
                        scheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ToolsDeckContent(
    state: GameDeckUiState,
    onToggleDnd: () -> Unit,
    onToggleAutoBrightnessLock: () -> Unit,
    onToggleRotationLock: () -> Unit,
    onClearRam: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onOpenManager: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tools",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    maxLines = 1
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactToolBentoTile(
                        icon = Icons.Rounded.NotificationsOff,
                        title = "Jangan Ganggu",
                        subtitle = if (state.dndEnabled) "Aktif" else "Nonaktif",
                        active = state.dndEnabled,
                        onClick = onToggleDnd,
                        modifier = Modifier.weight(1f)
                    )
                    CompactToolBentoTile(
                        icon = Icons.Rounded.BrightnessAuto,
                        title = "Kunci Cahaya",
                        subtitle = if (state.autoBrightnessLocked) "Terkunci" else "Otomatis",
                        active = state.autoBrightnessLocked,
                        onClick = onToggleAutoBrightnessLock,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactToolBentoTile(
                        icon = Icons.Rounded.ScreenLockRotation,
                        title = "Kunci Rotasi",
                        subtitle = if (state.rotationLocked) "Terkunci" else "Bebas",
                        active = state.rotationLocked,
                        onClick = onToggleRotationLock,
                        modifier = Modifier.weight(1f)
                    )
                    CompactToolBentoTile(
                        icon = Icons.Rounded.CleaningServices,
                        title = "Bersihkan RAM",
                        subtitle = state.ramStatusLabel,
                        active = false,
                        onClick = onClearRam,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactToolBentoTile(
                        icon = Icons.Rounded.Screenshot,
                        title = "Screenshot",
                        subtitle = "Tangkap layar",
                        active = false,
                        onClick = onTakeScreenshot,
                        modifier = Modifier.weight(1f)
                    )
                    CompactToolBentoTile(
                        icon = Icons.Rounded.Apps,
                        title = "Lumina Manager",
                        subtitle = "Buka aplikasi",
                        active = false,
                        onClick = onOpenManager,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
