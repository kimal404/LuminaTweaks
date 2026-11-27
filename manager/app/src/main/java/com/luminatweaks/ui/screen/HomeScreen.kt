@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)

package com.luminatweaks.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.luminatweaks.BuildConfig
import com.luminatweaks.R
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.component.ExpressiveInfoTile
import com.luminatweaks.ui.component.LuminaBottomBar
import com.luminatweaks.ui.component.TouchOriginDialog
import com.luminatweaks.ui.component.bouncyClickable
import com.luminatweaks.ui.component.luminaHazeHeader
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.component.rememberDialogOrigin
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.ui.theme.LocalPageTransition
import com.luminatweaks.ui.theme.PageTransition
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.RootUtil
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ContainerPosition {
    Top, Middle, Bottom, Single
}

@Composable
fun getConnectedContainerShape(
    position: ContainerPosition,
    outerRadius: Dp = LocalCornerRadius.current,
    innerRadius: Dp = if (outerRadius <= 4.dp) outerRadius else 4.dp
): RoundedCornerShape {
    return when (position) {
        ContainerPosition.Top -> RoundedCornerShape(
            topStart = outerRadius,
            topEnd = outerRadius,
            bottomStart = innerRadius,
            bottomEnd = innerRadius
        )
        ContainerPosition.Middle -> RoundedCornerShape(innerRadius)
        ContainerPosition.Bottom -> RoundedCornerShape(
            topStart = innerRadius,
            topEnd = innerRadius,
            bottomStart = outerRadius,
            bottomEnd = outerRadius
        )
        ContainerPosition.Single -> RoundedCornerShape(outerRadius)
    }
}

@Composable
private fun homeLocalizedLabel(id: String, en: String): String {
    val isIndonesian = remember {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        lang == "in" || lang == "id"
    }
    return if (isIndonesian) id else en
}

@Composable
fun HomeScreen(onOpenSetupWizard: () -> Unit = {}) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val tabScope = rememberCoroutineScope()
    var targetTab by remember { mutableIntStateOf(0) }
    var isNavigating by remember { mutableStateOf(false) }
    var navigationToken by remember { mutableIntStateOf(0) }
    val onTabSelected: (Int) -> Unit = { index ->
        targetTab = index
        if (pagerState.currentPage != index) {
            navigationToken += 1
            val token = navigationToken
            tabScope.launch {
                isNavigating = true
                try {
                    val current = pagerState.currentPage
                    if (abs(index - current) > 1) {
                        pagerState.scrollToPage(
                            if (index > current) index - 1 else index + 1
                        )
                    }
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(
                            durationMillis = 260,
                            easing = FastOutSlowInEasing
                        )
                    )
                } finally {
                    if (navigationToken == token) isNavigating = false
                }
            }
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (!isNavigating) targetTab = pagerState.currentPage
    }
    var currentSubscreen by remember { mutableStateOf<String?>(null) }
    var renderedSubscreen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentSubscreen) {
        if (currentSubscreen != null) {
            renderedSubscreen = currentSubscreen
        }
    }

    val unknownLabel = stringResource(R.string.status_unknown)
    val kernelVersion = remember(unknownLabel) { System.getProperty("os.version") ?: unknownLabel }
    val deviceModel = remember { DeviceUtil.getMarketDeviceName(context) }

    var batteryTemp by remember { mutableStateOf(DeviceUtil.getBatteryTemperature(context)) }
    var isChecking by remember { mutableStateOf(true) }
    var daemonPid by remember { mutableStateOf<String?>(null) }
    var isModuleInstalled by remember { mutableStateOf(true) }
    var isRebootRequired by remember { mutableStateOf(false) }
    var currentProfile by remember {
        mutableStateOf(DaemonManager.readCurrentProfile() ?: unknownLabel)
    }
    var showRebootDialog by remember { mutableStateOf(false) }
    var isScreenResumed by remember { mutableStateOf(true) }
    var resumeToken by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        isScreenResumed = true
                        resumeToken += 1
                    }
                    Lifecycle.Event.ON_PAUSE -> isScreenResumed = false
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(Unit) {
        val needsReboot = DaemonManager.isRebootRequired()
        val moduleInstalled = DaemonManager.isModuleInstalled()
        isRebootRequired = needsReboot
        isModuleInstalled = moduleInstalled

        if (needsReboot) {
            showRebootDialog = true
        } else if (moduleInstalled) {
            DaemonManager.startDaemon(context)
        }

        withContext(Dispatchers.IO) {
            val initialTemp = DeviceUtil.getBatteryTemperature(context)
            val initialStatus = DaemonManager.getStatus()

            withContext(Dispatchers.Main) {
                batteryTemp = initialTemp
                daemonPid = initialStatus.pid
                if (initialStatus.pid != null) {
                    currentProfile = initialStatus.profile
                }
                isChecking = false
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, isScreenResumed, currentSubscreen, resumeToken) {
        if (pagerState.currentPage != 0 || !isScreenResumed || currentSubscreen != null) {
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            while (isActive) {
                val resolved = DaemonManager.readCurrentProfile() ?: unknownLabel
                withContext(Dispatchers.Main) {
                    if (resolved != currentProfile) {
                        currentProfile = resolved
                    }
                }
                delay(200L)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, isScreenResumed, currentSubscreen, resumeToken) {
        if (pagerState.currentPage != 0 || !isScreenResumed || currentSubscreen != null) {
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            while (pagerState.isScrollInProgress && isActive) {
                delay(100L)
            }

            var fallbackChecks = 0
            while (isActive) {
                if (pagerState.isScrollInProgress) {
                    delay(200L)
                    continue
                }
                val currentTemp = DeviceUtil.getBatteryTemperature(context)
                val status = DaemonManager.getStatus()
                val checkFallback = status.pid == null && fallbackChecks % 4 == 0
                fallbackChecks++
                val rebootNeeded = when {
                    checkFallback -> DaemonManager.isRebootRequired()
                    status.pid == null -> isRebootRequired
                    else -> false
                }
                val installed = when {
                    checkFallback -> DaemonManager.isModuleInstalled()
                    status.pid == null -> isModuleInstalled
                    else -> true
                }

                withContext(Dispatchers.Main) {
                    batteryTemp = currentTemp
                    daemonPid = status.pid
                    isRebootRequired = rebootNeeded
                    isModuleInstalled = installed
                }
                delay(5000L)
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    TouchOriginDialog(
        visible = showRebootDialog,
        onDismiss = { showRebootDialog = false }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.dialog_reboot_required_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dialog_reboot_required_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text(
                        text = stringResource(R.string.action_later),
                        fontWeight = FontWeight.Medium
                    )
                }
                TextButton(
                    onClick = {
                        showRebootDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_rebooting_device),
                            Toast.LENGTH_SHORT
                        ).show()
                        coroutineScope.launch {
                            RootUtil.rebootDevice("")
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_reboot_now),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    val isFloating = LocalFloatingNavBar.current

    val restartAction: suspend () -> Unit = {
        daemonPid = null
        DaemonManager.restartDaemon(context)
        val updated = DaemonManager.getStatus()
        daemonPid = updated.pid
        if (updated.pid == null) {
            isRebootRequired = DaemonManager.isRebootRequired()
            isModuleInstalled = DaemonManager.isModuleInstalled()
        }
    }

    var overlayConfig by remember { mutableStateOf(FloatingFpsPrefs.load(context)) }
    val homeScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isFloating) {
            Box(modifier = Modifier.fillMaxSize()) {
                LuminaTabPager(
                    pagerState = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = currentSubscreen == null
                ) { tab ->
                    when (tab) {
                        0 -> HomeDashboardContent(
                            batteryTemp = batteryTemp,
                            daemonPid = daemonPid,
                            isChecking = isChecking,
                            isModuleInstalled = isModuleInstalled,
                            isRebootRequired = isRebootRequired,
                            currentProfile = currentProfile,
                            kernelVersion = kernelVersion,
                            deviceModel = deviceModel,
                            onRequestRebootDialog = { showRebootDialog = true },
                            onRestartDaemon = restartAction
                        )
                        1 -> AppsScreen(
                            pagerState = pagerState
                        )
                        2 -> TweaksScreen(
                            pagerState = pagerState,
                            onOpenFloatingHud = {
                                overlayConfig = FloatingFpsPrefs.load(context)
                                currentSubscreen = "telemetry_overlay"
                            }
                        )
                        3 -> SettingsScreen(
                            onNavigateToAbout = { currentSubscreen = "about" },
                            onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" },
                            onNavigateToCompatibility = { currentSubscreen = "compatibility" },
                            onNavigateToTweaks = { onTabSelected(2) },
                            onOpenSetupWizard = onOpenSetupWizard
                        )
                    }
                }

                val barSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
                val barEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedVisibility(
                    visible = currentSubscreen == null,
                    enter = slideInVertically(animationSpec = barSpatial) { it } + fadeIn(animationSpec = barEffects),
                    exit = slideOutVertically(animationSpec = barSpatial) { it } + fadeOut(animationSpec = barEffects),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    LuminaBottomBar(
                        selectedTab = targetTab,
                        onTabSelected = onTabSelected
                    )
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
                bottomBar = {
                    val barSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
                    val barEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                    AnimatedVisibility(
                        visible = currentSubscreen == null,
                        enter = slideInVertically(animationSpec = barSpatial) { it } + fadeIn(animationSpec = barEffects),
                        exit = slideOutVertically(animationSpec = barSpatial) { it } + fadeOut(animationSpec = barEffects)
                    ) {
                        LuminaBottomBar(
                            selectedTab = targetTab,
                            onTabSelected = onTabSelected
                        )
                    }
                }
            ) { innerPadding ->
                LuminaTabPager(
                    pagerState = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    userScrollEnabled = currentSubscreen == null
                ) { tab ->
                    when (tab) {
                        0 -> HomeDashboardContent(
                            batteryTemp = batteryTemp,
                            daemonPid = daemonPid,
                            isChecking = isChecking,
                            isModuleInstalled = isModuleInstalled,
                            isRebootRequired = isRebootRequired,
                            currentProfile = currentProfile,
                            kernelVersion = kernelVersion,
                            deviceModel = deviceModel,
                            onRequestRebootDialog = { showRebootDialog = true },
                            onRestartDaemon = restartAction
                        )
                        1 -> AppsScreen(
                            pagerState = pagerState
                        )
                        2 -> TweaksScreen(
                            pagerState = pagerState,
                            onOpenFloatingHud = {
                                overlayConfig = FloatingFpsPrefs.load(context)
                                currentSubscreen = "telemetry_overlay"
                            }
                        )
                        3 -> SettingsScreen(
                            onNavigateToAbout = { currentSubscreen = "about" },
                            onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" },
                            onNavigateToCompatibility = { currentSubscreen = "compatibility" },
                            onNavigateToTweaks = { onTabSelected(2) },
                            onOpenSetupWizard = onOpenSetupWizard
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = currentSubscreen != null,
            enter = slideInHorizontally(
                animationSpec = tween(320, easing = DrillEmphasizedDecelerate)
            ) { it } + fadeIn(animationSpec = tween(220)),
            exit = slideOutHorizontally(
                animationSpec = tween(280, easing = DrillEmphasizedAccelerate)
            ) { it } + fadeOut(animationSpec = tween(220)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (renderedSubscreen) {
                    "personalisasi" -> PersonalisasiScreen(
                        onBackClick = { currentSubscreen = null }
                    )
                    "compatibility" -> CompatibilityScreen(
                        onBackClick = { currentSubscreen = null }
                    )
                    "about" -> AboutScreen(
                        onBackClick = { currentSubscreen = null }
                    )
                    "telemetry_overlay" -> {
                        BackHandler { currentSubscreen = null }
                        TelemetryOverlayScreen(
                            config = overlayConfig,
                            targetHz = 120,
                            onConfigChange = { updated ->
                                val wasEnabled = overlayConfig.enabled
                                overlayConfig = updated
                                FloatingFpsPrefs.save(context, updated)
                                if (updated.enabled && !wasEnabled) {
                                    homeScope.launch {
                                        val granted = FloatingFpsService.ensureOverlayPermission(context)
                                        if (granted) {
                                            FloatingFpsService.sync(context, updated)
                                        } else {
                                            val reverted = updated.copy(enabled = false)
                                            overlayConfig = reverted
                                            FloatingFpsPrefs.save(context, reverted)
                                        }
                                    }
                                } else {
                                    FloatingFpsService.sync(context, updated)
                                }
                            },
                            onBack = { currentSubscreen = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LuminaTabPager(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    pageContent: @Composable (Int) -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
        pageSpacing = 12.dp,
        userScrollEnabled = userScrollEnabled,
        key = { it }
    ) { page ->
        Box(modifier = Modifier.fillMaxSize()) {
            pageContent(page)
        }
    }
}

@Composable
internal fun LuminaTabTransition(
    selectedTab: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit
) {
    val pageTransition = LocalPageTransition.current
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val spatialFloat = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsFloat = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    AnimatedContent(
        targetState = selectedTab,
        modifier = modifier,
        transitionSpec = {
            val forward = targetState > initialState
            val transform = when (pageTransition) {
                PageTransition.Vertical -> {
                    (slideInVertically(animationSpec = spatialOffset) { h ->
                        if (forward) (h * 0.06f).toInt() else -(h * 0.06f).toInt()
                    } + fadeIn(animationSpec = effectsFloat)) togetherWith
                        (slideOutVertically(animationSpec = spatialOffset) { h ->
                            if (forward) -(h * 0.06f).toInt() else (h * 0.06f).toInt()
                        } + fadeOut(animationSpec = effectsFloat))
                }
                PageTransition.Horizontal -> {
                    (slideInHorizontally(animationSpec = spatialOffset) { w ->
                        if (forward) (w * 0.14f).toInt() else -(w * 0.14f).toInt()
                    } + fadeIn(animationSpec = effectsFloat)) togetherWith
                        (slideOutHorizontally(animationSpec = spatialOffset) { w ->
                            if (forward) -(w * 0.14f).toInt() else (w * 0.14f).toInt()
                        } + fadeOut(animationSpec = effectsFloat))
                }
                PageTransition.CrossAxis -> {
                    (slideInHorizontally(animationSpec = spatialOffset) { w ->
                        if (forward) (w * 0.08f).toInt() else -(w * 0.08f).toInt()
                    } + fadeIn(animationSpec = effectsFloat) + scaleIn(
                        initialScale = 0.975f,
                        animationSpec = spatialFloat
                    )) togetherWith
                        (slideOutHorizontally(animationSpec = spatialOffset) { w ->
                            if (forward) -(w * 0.08f).toInt() else (w * 0.08f).toInt()
                        } + fadeOut(animationSpec = effectsFloat) + scaleOut(
                            targetScale = 0.975f,
                            animationSpec = spatialFloat
                        ))
                }
            }
            transform.using(SizeTransform(clip = false))
        },
        label = "mainTabTransition"
    ) { tab ->
        content(tab)
    }
}

private val DrillEmphasizedDecelerate = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)
private val DrillEmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val DRILL_TRANSITION_DURATION = 420

@Composable
internal fun <T> LuminaDrillTransition(
    targetState: T,
    isForward: (initial: T, target: T) -> Boolean,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any = { it ?: "root_state" },
    content: @Composable (T) -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.background

    AnimatedContent(
        targetState = targetState,
        modifier = modifier.background(backgroundColor),
        contentKey = contentKey,
        transitionSpec = {
            val forward = isForward(initialState, targetState)
            val transform = if (forward) {
                slideInHorizontally(
                    animationSpec = tween(DRILL_TRANSITION_DURATION, easing = DrillEmphasizedDecelerate)
                ) { it } togetherWith ExitTransition.None
            } else {
                EnterTransition.None togetherWith
                    slideOutHorizontally(
                        animationSpec = tween(DRILL_TRANSITION_DURATION, easing = DrillEmphasizedAccelerate)
                    ) { it }
            }
            transform
        },
        label = "subscreenDrillTransition"
    ) { state ->
        val isTopLayer = isForward(targetState, state)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (isTopLayer) 1f else 0f)
                .background(backgroundColor)
        ) {
            content(state)
        }
    }
}

@Composable
fun HomeDashboardContent(
    batteryTemp: String,
    daemonPid: String?,
    isChecking: Boolean = false,
    isModuleInstalled: Boolean = true,
    isRebootRequired: Boolean = false,
    currentProfile: String,
    kernelVersion: String,
    deviceModel: String,
    isRootGranted: Boolean = false,
    selinuxStatus: String = "",
    onRequestRebootDialog: () -> Unit,
    onRestartDaemon: suspend () -> Unit,
    onNavigateToPersonalisasi: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    var isRestarting by remember { mutableStateOf(false) }
    var showPowerDialog by remember { mutableStateOf(false) }
    val isRunning = daemonPid != null

    val restartScope = rememberCoroutineScope()
    val launchRestart: () -> Unit = {
        if (!isRestarting) {
            isRestarting = true
            Toast.makeText(context, context.getString(R.string.toast_restarting_service), Toast.LENGTH_SHORT).show()
            restartScope.launch {
                onRestartDaemon()
                isRestarting = false
            }
        }
    }

    var powerDialogOriginPoint by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val heroOrigin = rememberDialogOrigin()
    val topBarPowerOrigin = rememberDialogOrigin()

    TouchOriginDialog(
        visible = showPowerDialog,
        onDismiss = { showPowerDialog = false },
        origin = powerDialogOriginPoint
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeSquircleIcon(icon = Icons.Rounded.PowerSettingsNew)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.power_menu_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PowerActionRow(
                index = 0,
                count = 3,
                icon = Icons.Rounded.Refresh,
                title = stringResource(R.string.power_action_reboot),
                onClick = {
                    showPowerDialog = false
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, context.getString(R.string.toast_rebooting_device), Toast.LENGTH_SHORT).show()
                    restartScope.launch { rootReboot("") }
                }
            )
            PowerActionRow(
                index = 1,
                count = 3,
                icon = Icons.Rounded.Memory,
                title = stringResource(R.string.power_action_recovery),
                onClick = {
                    showPowerDialog = false
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, context.getString(R.string.toast_rebooting_recovery), Toast.LENGTH_SHORT).show()
                    restartScope.launch { rootReboot("recovery") }
                }
            )
            PowerActionRow(
                index = 2,
                count = 3,
                icon = Icons.Rounded.PowerSettingsNew,
                title = stringResource(R.string.power_action_restart_service),
                onClick = {
                    showPowerDialog = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    launchRestart()
                }
            )
        }
    }

    val isFloating = LocalFloatingNavBar.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (isFloating) 112.dp + navInset else 28.dp + navInset
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val fadeSurface = scheme.background
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = scheme.background,
        topBar = {
            Box(
                modifier = Modifier.luminaHazeHeader(
                    hazeState = hazeState,
                    surfaceColor = fadeSurface,
                    overlappedFraction = scrollBehavior.state.overlappedFraction
                )
            ) {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(
                            modifier = topBarPowerOrigin.trackedModifier(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                powerDialogOriginPoint = topBarPowerOrigin.origin
                                showPowerDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PowerSettingsNew,
                                contentDescription = stringResource(R.string.power_menu_title)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero_bento") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(248.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DaemonStatusBentoTile(
                            modifier = Modifier
                                .weight(0.55f)
                                .then(heroOrigin.trackedModifier()),
                            isChecking = isChecking,
                            isRestarting = isRestarting,
                            isRunning = isRunning,
                            isModuleInstalled = isModuleInstalled,
                            isRebootRequired = isRebootRequired,
                            daemonPid = daemonPid,
                            onClick = {
                                when {
                                    isChecking || isRestarting -> Unit
                                    isRunning -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        powerDialogOriginPoint = heroOrigin.origin
                                        showPowerDialog = true
                                    }
                                    isRebootRequired -> onRequestRebootDialog()
                                    else -> launchRestart()
                                }
                            }
                        )

                        ActiveProfileBentoTile(
                            modifier = Modifier.weight(0.45f),
                            label = stringResource(R.string.home_profile),
                            profileName = currentProfile
                        )
                    }

                    TemperatureHeroTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        batteryTemp = batteryTemp
                    )
                }
            }

            item(key = "device_specs_card") {
                DeviceSpecsBentoCard(
                    deviceLabel = stringResource(R.string.home_device_model),
                    deviceValue = deviceModel,
                    kernelLabel = stringResource(R.string.home_kernel_version),
                    kernelValue = kernelVersion,
                    managerLabel = stringResource(R.string.home_manager_version),
                    managerValue = stringResource(
                        R.string.home_version_release_format,
                        BuildConfig.VERSION_NAME.removeSuffix(".0")
                    )
                )
            }

            item(key = "support_tiles_pair") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveInfoTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Favorite,
                        title = stringResource(R.string.home_support_us),
                        subtitle = stringResource(R.string.home_support_us_desc),
                        accentColor = scheme.tertiary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uriHandler.openUri("https://sociabuzz.com/kimal404")
                        }
                    )

                    ExpressiveInfoTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.MenuBook,
                        title = stringResource(R.string.home_learn_more),
                        subtitle = stringResource(R.string.home_learn_more_desc),
                        accentColor = scheme.primary,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uriHandler.openUri("https://github.com/kimal404/LuminaTweaks")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DaemonStatusBentoTile(
    isChecking: Boolean,
    isRestarting: Boolean,
    isRunning: Boolean,
    isModuleInstalled: Boolean,
    isRebootRequired: Boolean,
    daemonPid: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isAlive = isRunning && !isRestarting
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.965f)
    val containerColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> scheme.surfaceContainerHighest
            isAlive -> scheme.secondaryContainer
            isRebootRequired -> scheme.tertiaryContainer
            else -> scheme.errorContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "daemonBentoContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> scheme.onSurfaceVariant
            isAlive -> scheme.onSecondaryContainer
            isRebootRequired -> scheme.onTertiaryContainer
            else -> scheme.onErrorContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "daemonBentoContent"
    )
    val statusText = when {
        isChecking -> "CHECKING"
        isRestarting -> "STARTING"
        isAlive -> "ALIVE"
        isRebootRequired -> stringResource(R.string.dialog_reboot_required_title)
        !isModuleInstalled -> stringResource(R.string.home_core_module_inactive)
        else -> "SLEEP"
    }
    val cleanPid = daemonPid?.takeIf { it.all(Char::isDigit) }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(26.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .animateContentSize(animationSpec = spring())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            when {
                isChecking || isRestarting -> CircularWavyProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.24f)
                )
                isAlive -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp)
                )
                isRebootRequired -> Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp)
                )
                !isModuleInstalled -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp)
                )
                else -> Icon(
                    imageVector = Icons.Rounded.HourglassEmpty,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (isAlive && cleanPid != null) {
                        "PID: $cleanPid"
                    } else {
                        homeLocalizedLabel("Ketuk untuk kelola", "Tap to manage")
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = contentColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActiveProfileBentoTile(
    label: String,
    profileName: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedContent(
                targetState = profileName,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(180)) + slideInVertically { it / 3 }) togetherWith
                        (fadeOut(animationSpec = tween(140)) + slideOutVertically { -it / 3 })
                },
                label = "activeProfileTransition"
            ) { targetProfile ->
                Text(
                    text = targetProfile,
                    style = MaterialTheme.typography.titleSmall.copy(
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
}

@Composable
private fun TemperatureHeroTile(
    batteryTemp: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val tempCelsius = remember(batteryTemp) {
        val normalized = batteryTemp.replace(',', '.')
        Regex("""-?\d+(\.\d+)?""").find(normalized)?.value?.toFloatOrNull() ?: 35f
    }
    val tempProgressTarget = ((tempCelsius - 25f) / 25f).coerceIn(0.08f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = tempProgressTarget,
        animationSpec = spring(stiffness = 300f),
        label = "tempProgress"
    )
    val thermalAccent by animateColorAsState(
        targetValue = when {
            tempCelsius >= 44f -> scheme.error
            tempCelsius >= 40f -> scheme.tertiary
            else -> scheme.primary
        },
        label = "thermalAccent"
    )
    val thermalStateLabel = when {
        tempCelsius >= 44f -> homeLocalizedLabel("Tinggi", "High")
        tempCelsius >= 40f -> homeLocalizedLabel("Hangat", "Warm")
        else -> homeLocalizedLabel("Normal", "Normal")
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = homeLocalizedLabel("Suhu", "Thermal"),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(thermalAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Thermostat,
                    contentDescription = null,
                    tint = thermalAccent,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = batteryTemp,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Black,
                    fontFeatureSettings = "tnum",
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = thermalStateLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = thermalAccent,
                maxLines = 1,
                softWrap = false
            )
        }

        ExpressiveWavyThermalBar(
            progress = animatedProgress,
            activeColor = thermalAccent,
            trackColor = thermalAccent.copy(alpha = 0.2f),
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        )
    }
}

@Composable
private fun ExpressiveWavyThermalBar(
    progress: Float,
    activeColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 3.2.dp.toPx()
        val halfStroke = strokeWidth / 2f
        val centerY = size.height / 2f
        val usableWidth = (size.width - strokeWidth).coerceAtLeast(1f)
        val clamped = progress.coerceIn(0f, 1f)
        val activeEnd = halfStroke + usableWidth * clamped
        val waveLength = 14.dp.toPx()
        val amplitude = 2.4.dp.toPx()

        if (activeEnd < size.width - halfStroke) {
            val trackStart = (activeEnd + 4.dp.toPx()).coerceAtMost(size.width - halfStroke)
            drawLine(
                color = trackColor,
                start = Offset(trackStart, centerY),
                end = Offset(size.width - halfStroke, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        if (clamped > 0f) {
            val wavePath = Path()
            var x = halfStroke
            var isFirst = true
            while (x <= activeEnd) {
                val angle = ((x - halfStroke) / waveLength) * 2f * PI.toFloat()
                val y = centerY + sin(angle) * amplitude
                if (isFirst) {
                    wavePath.moveTo(x, y)
                    isFirst = false
                } else {
                    wavePath.lineTo(x, y)
                }
                x += 2f
            }
            val endAngle = ((activeEnd - halfStroke) / waveLength) * 2f * PI.toFloat()
            wavePath.lineTo(activeEnd, centerY + sin(endAngle) * amplitude)

            drawPath(
                path = wavePath,
                color = activeColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun DeviceSpecsBentoCard(
    deviceLabel: String,
    deviceValue: String,
    kernelLabel: String,
    kernelValue: String,
    managerLabel: String,
    managerValue: String
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeEditorialSpecItem(
            label = deviceLabel,
            value = deviceValue
        )
        HomeEditorialSpecItem(
            label = kernelLabel,
            value = kernelValue
        )
        HomeEditorialSpecItem(
            label = managerLabel,
            value = managerValue
        )
    }
}

@Composable
private fun HomeEditorialSpecItem(
    label: String,
    value: String
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            color = scheme.primary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
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

private suspend fun rootReboot(mode: String) {
    RootUtil.rebootDevice(mode)
}

@Composable
private fun HomeSquircleIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = tint.copy(alpha = 0.12f)
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun PowerActionRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.97f)
    val shape = connectedContainerShape(
        index = index,
        count = count,
        outerRadius = 20.dp,
        innerRadius = 4.dp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeSquircleIcon(icon = icon)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConnectedCardRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: ContainerPosition,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(getConnectedContainerShape(position, outerRadius = 26.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            HomeSquircleIcon(icon = icon, tint = iconTint, containerColor = iconContainer)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ConnectedActionCardRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: ContainerPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(getConnectedContainerShape(position, outerRadius = 26.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            HomeSquircleIcon(icon = icon, tint = iconTint, containerColor = iconContainer)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.padding(start = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
