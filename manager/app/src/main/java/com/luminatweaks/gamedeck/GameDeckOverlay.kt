@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.luminatweaks.gamedeck

import android.graphics.Bitmap
import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.luminatweaks.ui.screen.FloatingFpsConfig
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent

internal const val DECK_RAIL_WIDTH_DP = 78f
internal const val DECK_RAIL_GAP_DP = 10f
internal const val DECK_CARD_WIDTH_DP = 354f
internal const val DECK_TOTAL_WIDTH_DP = DECK_RAIL_WIDTH_DP + DECK_RAIL_GAP_DP + DECK_CARD_WIDTH_DP
internal const val MAX_DECK_QUICK_APPS = 8
private val DECK_SLIDER_THUMB_RADIUS = 22.dp
private val DECK_TAP_SLOP = 12.dp

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
    onFloatingHudChange: (FloatingFpsConfig) -> Unit = {},
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
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = 460f
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
        var lastBanner by remember { mutableStateOf("") }
        var lastBannerIcon by remember { mutableStateOf("check_circle") }
        LaunchedEffect(current.statusBannerKey) {
            current.statusBanner?.let {
                lastBanner = it
                lastBannerIcon = current.statusBannerIcon
            }
        }
        val bannerText = current.statusBanner ?: lastBanner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = openProgress.value.coerceIn(0f, 1f)
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
                onFloatingHudChange = onFloatingHudChange,
                onOpenManager = onOpenManager
            )
            AnimatedVisibility(
                visible = current.statusBanner != null,
                enter = fadeIn(tween(200)) + slideInVertically(
                    animationSpec = tween(200),
                    initialOffsetY = { it / 2 }
                ),
                exit = fadeOut(tween(200)) + slideOutVertically(
                    animationSpec = tween(200),
                    targetOffsetY = { it / 2 }
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                StatusBannerPill(text = bannerText, iconName = lastBannerIcon)
            }
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
    onFloatingHudChange: (FloatingFpsConfig) -> Unit,
    onOpenManager: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    LaunchedEffect(Unit) { pagerState.scrollToPage(0) }
    val coroutineScope = rememberCoroutineScope()
    var showAppPicker by remember { mutableStateOf(false) }
    var isWidgetEditorMounted by remember { mutableStateOf(false) }
    var isWidgetEditorClosing by remember { mutableStateOf(false) }
    val widgetEditorProgress = remember { Animatable(0f) }

    LaunchedEffect(showAppPicker) {
        onAppPickerVisibilityChanged(showAppPicker)
    }

    val openWidgetEditor: () -> Unit = {
        if (!isWidgetEditorMounted && !isWidgetEditorClosing) {
            onWidgetEditorVisibilityChanged(true)
            isWidgetEditorMounted = true
            coroutineScope.launch {
                widgetEditorProgress.snapTo(0f)
                widgetEditorProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = 420f
                    )
                )
            }
        }
    }

    val closeWidgetEditor: () -> Unit = {
        if (isWidgetEditorMounted && !isWidgetEditorClosing) {
            isWidgetEditorClosing = true
            coroutineScope.launch {
                widgetEditorProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    )
                )
                isWidgetEditorMounted = false
                isWidgetEditorClosing = false
                onWidgetEditorVisibilityChanged(false)
            }
        }
    }

    val isLeft = state.side == GameDeckSide.LEFT
    val density = LocalDensity.current
    val closeThreshold = with(density) { 24.dp.toPx() }
    val dm = LocalContext.current.resources.displayMetrics
    val screenWidthDp = with(density) { dm.widthPixels.toDp() }
    val screenHeightDp = with(density) { dm.heightPixels.toDp() }
    val maxWorkspaceWidth = (screenWidthDp - 24.dp).coerceAtLeast(320.dp)
    val panelHeightDp = (screenHeightDp * 0.86f)
        .coerceIn(316.dp, 348.dp)
        .coerceAtMost((screenHeightDp - 20.dp).coerceAtLeast(280.dp))
    val cardWidth: Dp = (maxWorkspaceWidth - DECK_RAIL_WIDTH_DP.dp - DECK_RAIL_GAP_DP.dp)
        .coerceIn(260.dp, DECK_CARD_WIDTH_DP.dp)

    val handleTabSelect: (Int) -> Unit = { tab ->
        if (showAppPicker || isWidgetEditorMounted) {
            showAppPicker = false
            closeWidgetEditor()
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

    val editorP = widgetEditorProgress.value.coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isWidgetEditorMounted) {
                    detectTapGestures {
                        if (!isWidgetEditorMounted) {
                            onCollapse()
                        }
                    }
                }
        )

        Row(
            modifier = Modifier
                .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(
                    start = if (isLeft) 12.dp else 0.dp,
                    end = if (isLeft) 0.dp else 12.dp
                )
                .width(maxWorkspaceWidth)
                .height(panelHeightDp)
                .graphicsLayer {
                    val s = 1f - 0.05f * editorP
                    scaleX = s
                    scaleY = s
                    alpha = (1f - 2.4f * editorP).coerceIn(0f, 1f)
                },
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
                        closeWidgetEditor()
                        showAppPicker = !showAppPicker
                    }
                )
                DeckCard(
                    state = state,
                    cardWidth = cardWidth,
                    pagerState = pagerState,
                    showAppPicker = showAppPicker,
                    onOpenWidgetEditor = openWidgetEditor,
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
                    onFloatingHudChange = onFloatingHudChange,
                    onOpenManager = onOpenManager
                )
            } else {
                DeckCard(
                    state = state,
                    cardWidth = cardWidth,
                    pagerState = pagerState,
                    showAppPicker = showAppPicker,
                    onOpenWidgetEditor = openWidgetEditor,
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
                    onFloatingHudChange = onFloatingHudChange,
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
                        closeWidgetEditor()
                        showAppPicker = !showAppPicker
                    }
                )
            }
        }

        if (isWidgetEditorMounted) {
            FullScreenWidgetEditorOverlay(
                state = state,
                progress = editorP,
                onToggleWidget = onToggleDashboardWidget,
                onMoveWidget = onMoveDashboardWidget,
                onResetWidgets = onResetDashboardWidgets,
                onDone = closeWidgetEditor
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
    onFloatingHudChange: (FloatingFpsConfig) -> Unit,
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
                (fadeIn(tween(220)) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                )) togetherWith (fadeOut(tween(150)) + scaleOut(targetScale = 0.97f))
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
                            onToggleKeepScreenOn = onToggleKeepScreenOn,
                            onToggleDnd = onToggleDnd,
                            onToggleAutoBrightnessLock = onToggleAutoBrightnessLock,
                            onToggleRotationLock = onToggleRotationLock,
                            onClearRam = onClearRam,
                            onTakeScreenshot = onTakeScreenshot,
                            onFloatingHudChange = onFloatingHudChange,
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
    progress: Float,
    onToggleWidget: (String) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onResetWidgets: () -> Unit,
    onDone: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

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
    var settlingKey by remember { mutableStateOf<String?>(null) }
    var lastSwapMs by remember { mutableLongStateOf(0L) }
    val settlingOffset = remember { Animatable(0f) }

    val defaultStepPx = with(density) { 82.dp.toPx() }
    val itemSpacingPx = with(density) { 12.dp.toPx() }
    val autoScrollMarginPx = with(density) { 54.dp.toPx() }
    val enterSlidePx = with(density) { 26.dp.toPx() }
    val activeListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()

    val currentActiveItems by rememberUpdatedState(activeItems)
    val currentOrderedKeys by rememberUpdatedState(orderedKeys)
    val currentDragOffsetY by rememberUpdatedState(dragOffsetY)

    fun processReorderStep(activeKey: String) {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastSwapMs < 110L) return

        val listVis = currentActiveItems
        val listGlob = currentOrderedKeys
        val curVisIdx = listVis.indexOfFirst { it.key == activeKey }
        val curGlobIdx = listGlob.indexOf(activeKey)
        if (curVisIdx < 0 || curGlobIdx < 0) return

        val visibleMap = activeListState.layoutInfo.visibleItemsInfo.associateBy { it.key }
        val currentSize = visibleMap[activeKey]?.size?.toFloat() ?: defaultStepPx
        val anchorIdx = activeListState.firstVisibleItemIndex
        val anchorOffset = activeListState.firstVisibleItemScrollOffset

        if (dragOffsetY > 0f && curVisIdx < listVis.lastIndex) {
            val nextItem = listVis[curVisIdx + 1]
            val nextSize = visibleMap[nextItem.key]?.size?.toFloat() ?: currentSize
            val threshold = (nextSize + itemSpacingPx) * 0.56f
            if (dragOffsetY >= threshold) {
                val targetGlobIdx = listGlob.indexOf(nextItem.key)
                if (targetGlobIdx >= 0) {
                    lastSwapMs = nowMs
                    onMoveWidget(curGlobIdx, targetGlobIdx)
                    if (curVisIdx == anchorIdx) {
                        coroutineScope.launch {
                            activeListState.scrollToItem(anchorIdx, anchorOffset)
                        }
                    }
                    dragOffsetY -= (nextSize + itemSpacingPx)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        } else if (dragOffsetY < 0f && curVisIdx > 0) {
            val prevItem = listVis[curVisIdx - 1]
            val prevSize = visibleMap[prevItem.key]?.size?.toFloat() ?: currentSize
            val threshold = (prevSize + itemSpacingPx) * 0.56f
            if (dragOffsetY <= -threshold) {
                val targetGlobIdx = listGlob.indexOf(prevItem.key)
                if (targetGlobIdx >= 0) {
                    lastSwapMs = nowMs
                    onMoveWidget(curGlobIdx, targetGlobIdx)
                    if (curVisIdx - 1 == anchorIdx) {
                        coroutineScope.launch {
                            activeListState.scrollToItem(anchorIdx, anchorOffset)
                        }
                    }
                    dragOffsetY += (prevSize + itemSpacingPx)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    fun releaseDraggedCard(activeKey: String) {
        if (draggedKey != activeKey) return
        val releaseDelta = dragOffsetY
        draggedKey = null
        dragOffsetY = 0f
        settlingKey = activeKey
        coroutineScope.launch {
            settlingOffset.snapTo(releaseDelta)
            settlingOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.80f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            if (settlingKey == activeKey) {
                settlingKey = null
            }
        }
    }

    LaunchedEffect(draggedKey) {
        val activeKey = draggedKey ?: return@LaunchedEffect
        while (draggedKey == activeKey) {
            delay(16L)
            val layoutInfo = activeListState.layoutInfo
            val vpH = layoutInfo.viewportSize.height.toFloat()
            val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == activeKey }
            if (vpH <= 0f || visibleItem == null) continue
            val visualTop = visibleItem.offset.toFloat() + currentDragOffsetY
            val visualBottom = visualTop + visibleItem.size.toFloat()
            val scrollDelta = when {
                currentDragOffsetY > 16f && visualBottom > vpH - autoScrollMarginPx && activeListState.canScrollForward -> 9f
                currentDragOffsetY < -16f && visualTop < autoScrollMarginPx && activeListState.canScrollBackward -> -9f
                else -> 0f
            }
            if (scrollDelta != 0f) {
                val consumed = activeListState.scrollBy(scrollDelta)
                if (consumed != 0f) {
                    dragOffsetY += consumed
                    processReorderStep(activeKey)
                }
            }
        }
    }

    val p = progress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f * p))
            .displayCutoutPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = ((p - 0.10f) / 0.90f).coerceIn(0f, 1f)
                    val s = 0.93f + 0.07f * p
                    scaleX = s
                    scaleY = s
                    translationY = (1f - p) * enterSlidePx
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = scheme.surfaceContainerHigh,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(scheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Atur Widget",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Tahan kartu atau tarik = untuk urutkan",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onResetWidgets()
                        },
                        enabled = !isDefaultConfig,
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDefaultConfig) scheme.surfaceContainerHighest else scheme.secondaryContainer,
                        contentColor = if (isDefaultConfig) {
                            scheme.onSurfaceVariant.copy(alpha = 0.45f)
                        } else {
                            scheme.onSecondaryContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset susunan widget",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Reset",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDone()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.primary,
                        contentColor = scheme.onPrimary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(28.dp),
                    color = scheme.surfaceContainer
                ) {
                    if (activeItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Minimal 1 widget aktif",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = activeListState,
                            userScrollEnabled = draggedKey == null,
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = activeItems,
                                key = { it.key }
                            ) { item ->
                                val isDragging = draggedKey == item.key
                                val isSettling = settlingKey == item.key
                                val isElevated = isDragging || isSettling

                                val cardScale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.015f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.76f,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "fullEditorDragScale"
                                )
                                val cardElevation by animateDpAsState(
                                    targetValue = if (isDragging) 14.dp else if (isSettling) 4.dp else 0.dp,
                                    animationSpec = spring(
                                        dampingRatio = 0.78f,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "fullEditorDragElev"
                                )
                                val cardBg by animateColorAsState(
                                    targetValue = if (isElevated) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
                                    animationSpec = tween(durationMillis = 180),
                                    label = "fullEditorCardBg"
                                )
                                val activeTranslationY = when {
                                    isDragging -> dragOffsetY
                                    isSettling -> settlingOffset.value
                                    else -> 0f
                                }

                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = cardBg,
                                    shadowElevation = cardElevation,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (!isDragging) {
                                                Modifier.animateItem(
                                                    fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                    placementSpec = spring(
                                                        dampingRatio = 0.82f,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    ),
                                                    fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .zIndex(
                                            when {
                                                isDragging -> 10f
                                                isSettling -> 5f
                                                else -> 0f
                                            }
                                        )
                                        .graphicsLayer {
                                            translationY = activeTranslationY
                                            scaleX = cardScale
                                            scaleY = cardScale
                                        }
                                        .pointerInput(item.key) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    settlingKey = null
                                                    draggedKey = item.key
                                                    dragOffsetY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragEnd = {
                                                    releaseDraggedCard(item.key)
                                                },
                                                onDragCancel = {
                                                    releaseDraggedCard(item.key)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    processReorderStep(item.key)
                                                }
                                            )
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isElevated) {
                                                            scheme.primary
                                                        } else {
                                                            scheme.secondaryContainer
                                                        }
                                                    )
                                                    .pointerInput(item.key) {
                                                        detectVerticalDragGestures(
                                                            onDragStart = {
                                                                settlingKey = null
                                                                draggedKey = item.key
                                                                dragOffsetY = 0f
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            },
                                                            onDragEnd = {
                                                                releaseDraggedCard(item.key)
                                                            },
                                                            onDragCancel = {
                                                                releaseDraggedCard(item.key)
                                                            },
                                                            onVerticalDrag = { change, dragAmount ->
                                                                change.consume()
                                                                dragOffsetY += dragAmount
                                                                processReorderStep(item.key)
                                                            }
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DragHandle,
                                                    contentDescription = "Geser urutan",
                                                    tint = if (isElevated) scheme.onPrimary else scheme.onSecondaryContainer,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                            Text(
                                                text = item.title,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = scheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onToggleWidget(item.key)
                                                },
                                                enabled = activeItems.size > 1,
                                                modifier = Modifier.size(32.dp),
                                                shape = RoundedCornerShape(11.dp),
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
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(scheme.surfaceContainerLow)
                                                .padding(8.dp)
                                        ) {
                                            LiveDashboardWidgetPreview(widgetKey = item.key, state = state)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(28.dp),
                    color = scheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pustaka Widget",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = scheme.primaryContainer
                            ) {
                                Text(
                                    text = "${activeItems.size}/${allItems.size} Aktif",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
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
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(scheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = scheme.onPrimaryContainer,
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }
                                    Text(
                                        text = "Semua widget aktif",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Ketuk tombol - pada kartu aktif untuk menyimpannya di sini.",
                                        fontSize = 11.sp,
                                        color = scheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = libraryListState,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(
                                    items = libraryItems,
                                    key = { it.key }
                                ) { item ->
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onToggleWidget(item.key)
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        color = scheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(
                                                fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                                placementSpec = spring(
                                                    dampingRatio = 0.82f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(12.dp))
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
                                                Text(
                                                    text = item.title,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = scheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(11.dp))
                                                        .background(scheme.primary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Add,
                                                        contentDescription = "Pasang ${item.title}",
                                                        tint = scheme.onPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(scheme.surfaceContainerLow)
                                                    .padding(8.dp)
                                            ) {
                                                LiveDashboardWidgetPreview(widgetKey = item.key, state = state)
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
private fun LiveDashboardWidgetPreview(
    widgetKey: String,
    state: GameDeckUiState
) {
    when (widgetKey) {
        "telemetry" -> TelemetryWidgetCard(state = state)
        "profile" -> ProfileSelectorCard(
            state = state,
            onProfileSelected = {},
            onToggleAutoMode = {},
            interactive = false
        )
        "music" -> MusicPlayerWidgetCard(
            title = state.musicTitle,
            artist = state.musicArtist,
            packageName = state.musicPackage,
            albumArt = state.musicArt,
            isPlaying = state.isMusicPlaying,
            positionMs = state.musicPositionMs,
            durationMs = state.musicDurationMs,
            onPrevious = {},
            onPlayPause = {},
            onNext = {},
            onSeek = {},
            onOpenApp = {},
            interactive = false
        )
        "refresh_rate" -> RefreshRateCard(
            modes = state.rrModes,
            selectedHz = state.selectedRrHz,
            liveHz = state.targetHz,
            gameScoped = state.gamePackage != null,
            perApp = state.rrPerApp,
            onSelect = {},
            interactive = false
        )
        "sliders" -> BrightnessVolumeCard(
            brightnessPercent = state.brightnessPercent,
            volumePercent = state.volumePercent,
            volumeMuted = state.volumeMuted,
            onBrightnessChanged = { _, _ -> },
            onVolumeChanged = {},
            onToggleMute = {},
            interactive = false
        )
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
    onOpenApp: () -> Unit,
    interactive: Boolean = true
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
                        .clickable(enabled = interactive && !packageName.isNullOrBlank()) { onOpenApp() },
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
                        .clickable(enabled = interactive && !packageName.isNullOrBlank()) { onOpenApp() },
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
                    if (interactive) {
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
                    } else {
                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = CircleShape,
                            color = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = playBtnBg,
                            contentColor = playBtnTint
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = CircleShape,
                            color = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
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
                        enabled = interactive && durationMs > 0L,
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
private fun TelemetryWidgetCard(state: GameDeckUiState) {
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
                        TelemetryWidgetCard(state = state)
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
                            gameScoped = state.gamePackage != null,
                            perApp = state.rrPerApp,
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
    onToggle: () -> Unit,
    interactive: Boolean = true
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
    val pillContent: @Composable () -> Unit = {
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

    if (interactive) {
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = pillBg,
            contentColor = pillText,
            content = pillContent
        )
    } else {
        Surface(
            shape = CircleShape,
            color = pillBg,
            contentColor = pillText,
            content = pillContent
        )
    }
}

@Composable
private fun ExpressiveToggleTile(
    active: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
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
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
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
    onToggleAutoMode: () -> Unit,
    interactive: Boolean = true
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
                    onToggle = onToggleAutoMode,
                    interactive = interactive
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
                        interactive = interactive,
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
    gameScoped: Boolean = false,
    perApp: Boolean = false,
    onSelect: (Int?) -> Unit,
    interactive: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }
    val sortedModes = remember(modes) { modes.distinct().sorted() }
    val displayLiveHz = if (liveHz > 0) liveHz else (selectedHz ?: 60)
    val pillLabel = when {
        selectedHz != null && perApp -> "Game · $selectedHz Hz"
        selectedHz != null -> "$selectedHz Hz"
        gameScoped -> "Global · $displayLiveHz Hz"
        else -> "Auto · $displayLiveHz Hz"
    }
    val pillContainer = if (selectedHz != null) {
        scheme.primaryContainer
    } else {
        scheme.secondaryContainer
    }
    val pillContentColor = if (selectedHz != null) {
        scheme.onPrimaryContainer
    } else {
        scheme.onSecondaryContainer
    }
    val pillInner: @Composable () -> Unit = {
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
                if (interactive) {
                    Surface(
                        onClick = { menuExpanded = true },
                        shape = CircleShape,
                        color = pillContainer,
                        contentColor = pillContentColor,
                        content = pillInner
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (gameScoped) {
                                        "Ikut Global ($displayLiveHz Hz)"
                                    } else {
                                        "Auto ($displayLiveHz Hz)"
                                    },
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
                } else {
                    Surface(
                        shape = CircleShape,
                        color = pillContainer,
                        contentColor = pillContentColor,
                        content = pillInner
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckExpressiveSlider(
    value: Int,
    min: Int,
    max: Int,
    accentColor: Color,
    onValueChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val span = (max - min).coerceAtLeast(1)
    var lastTick by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var latestValue by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) {
        latestValue = value
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(enabled, min, max) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Main
                    )
                    val reach = DECK_SLIDER_THUMB_RADIUS.toPx()
                    val usable = size.width - reach * 2f
                    if (usable <= 0f) return@awaitEachGesture
                    val fraction = ((down.position.x - reach) / usable).coerceIn(0f, 1f)
                    val tapped = (min + fraction * span).roundToInt().coerceIn(min, max)
                    val slop = DECK_TAP_SLOP.toPx()
                    var dragged = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) break
                        if ((change.position - down.position).getDistance() > slop) {
                            dragged = true
                        }
                    }
                    if (!dragged) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueChange(tapped, false)
                        onValueChange(tapped, true)
                    }
                }
            }
    ) {
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val mapped = raw.roundToInt().coerceIn(min, max)
                if (mapped != lastTick) {
                    lastTick = mapped
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                latestValue = mapped
                onValueChange(mapped, false)
            },
            onValueChangeFinished = {
                onValueChange(latestValue, true)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = min.toFloat()..max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                activeTickColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.24f)
            )
        )
    }
}

@Composable
private fun BrightnessVolumeCard(
    brightnessPercent: Int,
    volumePercent: Int,
    volumeMuted: Boolean,
    onBrightnessChanged: (Int, Boolean) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleMute: () -> Unit,
    interactive: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    var brightnessDragging by remember { mutableStateOf(false) }
    var brightnessLocal by remember { mutableIntStateOf(brightnessPercent.coerceIn(5, 100)) }
    LaunchedEffect(brightnessPercent) {
        if (!brightnessDragging) {
            brightnessLocal = brightnessPercent.coerceIn(5, 100)
        }
    }
    var volumeDragging by remember { mutableStateOf(false) }
    var volumeLocal by remember {
        mutableIntStateOf(if (volumeMuted) 0 else volumePercent.coerceIn(0, 100))
    }
    LaunchedEffect(volumePercent, volumeMuted) {
        if (!volumeDragging) {
            volumeLocal = if (volumeMuted) 0 else volumePercent.coerceIn(0, 100)
        }
    }

    val brightnessFraction = (brightnessLocal / 100f).coerceIn(0.05f, 1f)
    val volumeFraction = (volumeLocal / 100f).coerceIn(0f, 1f)
    val brightnessIcon = when {
        brightnessFraction < 0.33f -> Icons.Rounded.BrightnessLow
        brightnessFraction < 0.66f -> Icons.Rounded.BrightnessMedium
        else -> Icons.Rounded.BrightnessHigh
    }
    val volumeIcon = when {
        volumeMuted || volumeFraction <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeMute
        volumeFraction < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = brightnessIcon,
                            contentDescription = "Brightness",
                            tint = scheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Brightness",
                            style = TextStyle(
                                fontSize = 13.5.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            color = scheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$brightnessLocal%",
                        style = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        color = scheme.tertiary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DeckExpressiveSlider(
                    value = brightnessLocal,
                    min = 5,
                    max = 100,
                    accentColor = scheme.tertiary,
                    enabled = interactive,
                    onValueChange = { pct, commit ->
                        brightnessDragging = !commit
                        brightnessLocal = pct
                        onBrightnessChanged(pct, commit)
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = if (interactive) {
                            Modifier.clickable(onClick = onToggleMute)
                        } else {
                            Modifier
                        }
                    ) {
                        Icon(
                            imageVector = volumeIcon,
                            contentDescription = "Suara",
                            tint = if (volumeMuted || volumeLocal == 0) scheme.error else scheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Suara",
                            style = TextStyle(
                                fontSize = 13.5.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            color = scheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$volumeLocal%",
                        style = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        color = if (volumeMuted || volumeLocal == 0) scheme.error else scheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DeckExpressiveSlider(
                    value = volumeLocal,
                    min = 0,
                    max = 100,
                    accentColor = scheme.primary,
                    enabled = interactive,
                    onValueChange = { pct, commit ->
                        volumeDragging = !commit
                        volumeLocal = pct
                        onVolumeChanged(pct)
                    }
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
    onToggleKeepScreenOn: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleAutoBrightnessLock: () -> Unit,
    onToggleRotationLock: () -> Unit,
    onClearRam: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onFloatingHudChange: (FloatingFpsConfig) -> Unit,
    onOpenManager: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ToolCategoryCard(
            title = "Overlay & Layar",
            icon = Icons.Rounded.Visibility
        ) {
            FloatingHudDeckCard(
                config = state.floatingHud,
                onChange = onFloatingHudChange
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactToolBentoTile(
                    icon = Icons.Rounded.Visibility,
                    title = "Layar Nyala",
                    subtitle = if (state.keepScreenOn) "Aktif" else "Nonaktif",
                    active = state.keepScreenOn,
                    onClick = onToggleKeepScreenOn,
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
            CompactToolBentoTile(
                icon = Icons.Rounded.ScreenLockRotation,
                title = "Kunci Rotasi",
                subtitle = if (state.rotationLocked) "Terkunci" else "Bebas",
                active = state.rotationLocked,
                onClick = onToggleRotationLock,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ToolCategoryCard(
            title = "Fokus & Utilitas",
            icon = Icons.Rounded.Bolt
        ) {
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
                    icon = Icons.Rounded.CleaningServices,
                    title = "Bersihkan RAM",
                    subtitle = state.ramStatusLabel,
                    active = false,
                    onClick = onClearRam,
                    modifier = Modifier.weight(1f)
                )
            }
            CompactToolBentoTile(
                icon = Icons.Rounded.Screenshot,
                title = "Screenshot",
                subtitle = "Tangkap layar",
                active = false,
                onClick = onTakeScreenshot,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ToolCategoryCard(
            title = "Navigasi",
            icon = Icons.Rounded.Apps
        ) {
            WideToolButton(
                icon = Icons.Rounded.Apps,
                title = "Lumina Manager",
                subtitle = "Buka aplikasi utama",
                onClick = onOpenManager
            )
        }
    }
}

@Composable
private fun ToolCategoryCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = scheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            content()
        }
    }
}

@Composable
private fun FloatingHudDeckCard(
    config: FloatingFpsConfig,
    onChange: (FloatingFpsConfig) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "floatingHudArrow"
    )
    val canDisableMetric = config.activeModuleCount > 1

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Floating HUD",
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        color = scheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (config.enabled) "Aktif" else "Nonaktif",
                        style = TextStyle(
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Medium,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        color = if (config.enabled) scheme.primary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            expanded = !expanded
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Tutup pengaturan" else "Buka pengaturan",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotation)
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { enabled ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(config.copy(enabled = enabled))
                    },
                    thumbContent = {
                        Icon(
                            imageVector = if (config.enabled) {
                                Icons.Rounded.Check
                            } else {
                                Icons.Rounded.Close
                            },
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = scheme.primary,
                        checkedIconColor = scheme.primary,
                        uncheckedThumbColor = scheme.onSurfaceVariant,
                        uncheckedTrackColor = scheme.surfaceContainerHighest,
                        uncheckedIconColor = scheme.surface,
                        uncheckedBorderColor = scheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(160)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
                ) + fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HudSectionLabel(text = "Metrik Tampil")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudConfigChip(label = "FPS", selected = config.showFps) {
                            if (config.showFps && !canDisableMetric) return@HudConfigChip
                            onChange(config.copy(showFps = !config.showFps))
                        }
                        HudConfigChip(label = "Frametime", selected = config.showFrametime) {
                            if (config.showFrametime && !canDisableMetric) return@HudConfigChip
                            onChange(config.copy(showFrametime = !config.showFrametime))
                        }
                        HudConfigChip(label = "CPU", selected = config.showCpuCluster) {
                            if (config.showCpuCluster && !canDisableMetric) return@HudConfigChip
                            onChange(config.copy(showCpuCluster = !config.showCpuCluster))
                        }
                        HudConfigChip(label = "Suhu", selected = config.showTemp) {
                            if (config.showTemp && !canDisableMetric) return@HudConfigChip
                            onChange(config.copy(showTemp = !config.showTemp))
                        }
                        HudConfigChip(label = "RAM", selected = config.showRam) {
                            if (config.showRam && !canDisableMetric) return@HudConfigChip
                            onChange(config.copy(showRam = !config.showRam))
                        }
                        HudConfigChip(label = "Profil", selected = config.showProfile) {
                            if (config.showProfile && !canDisableMetric) return@HudConfigChip
                            onChange(config.copy(showProfile = !config.showProfile))
                        }
                    }

                    HudSectionLabel(text = "Ukuran Teks")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudConfigChip(label = "Kecil", selected = config.scale <= 0.9f) {
                            onChange(config.copy(scale = 0.85f))
                        }
                        HudConfigChip(label = "Normal", selected = config.scale > 0.9f && config.scale < 1.1f) {
                            onChange(config.copy(scale = 1f))
                        }
                        HudConfigChip(label = "Besar", selected = config.scale >= 1.1f) {
                            onChange(config.copy(scale = 1.2f))
                        }
                    }

                    HudSectionLabel(text = "Opasitas Latar")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudConfigChip(label = "55%", selected = config.bgAlpha < 0.65f) {
                            onChange(config.copy(bgAlpha = 0.55f))
                        }
                        HudConfigChip(label = "75%", selected = config.bgAlpha >= 0.65f && config.bgAlpha < 0.85f) {
                            onChange(config.copy(bgAlpha = 0.75f))
                        }
                        HudConfigChip(label = "90%", selected = config.bgAlpha >= 0.85f) {
                            onChange(config.copy(bgAlpha = 0.9f))
                        }
                    }

                    HudSectionLabel(text = "Interval")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudConfigChip(label = "250 ms", selected = config.intervalMs == 250) {
                            onChange(config.copy(intervalMs = 250))
                        }
                        HudConfigChip(label = "500 ms", selected = config.intervalMs == 500) {
                            onChange(config.copy(intervalMs = 500))
                        }
                        HudConfigChip(label = "1 s", selected = config.intervalMs == 1000) {
                            onChange(config.copy(intervalMs = 1000))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudSectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HudConfigChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    FilterChip(
        selected = selected,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        label = {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
private fun WideToolButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = scheme.onSurface,
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
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StatusBannerPill(text: String, iconName: String) {
    val scheme = MaterialTheme.colorScheme
    val icon = when (iconName) {
        "error" -> Icons.Rounded.Error
        "info" -> Icons.Rounded.Info
        else -> Icons.Rounded.CheckCircle
    }
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurface,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (iconName == "error") scheme.error else scheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false,
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
