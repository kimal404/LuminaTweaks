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
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.luminatweaks.R
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.component.LuminaBottomBar
import com.luminatweaks.ui.component.TouchOriginDialog
import com.luminatweaks.ui.component.appBarFade
import com.luminatweaks.ui.component.bouncyClickable
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.component.rememberDialogOrigin
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.ui.theme.LocalPageTransition
import com.luminatweaks.ui.theme.PageTransition
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.RootUtil
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ContainerPosition {
    Top, Middle, Bottom, Single
}

private fun readSystemProperty(key: String): String? {
    return try {
        val spClass = Class.forName("android.os.SystemProperties")
        val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
        val value = getMethod.invoke(null, key, "") as? String
        value?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
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
    val unknownLabel = stringResource(R.string.status_unknown)
    val kernelVersion = remember(unknownLabel) { System.getProperty("os.version") ?: unknownLabel }
    val deviceModel = remember { DeviceUtil.getMarketDeviceName(context) }

    var batteryTemp by remember { mutableStateOf(DeviceUtil.getBatteryTemperature(context)) }
    var isChecking by remember { mutableStateOf(true) }
    var daemonPid by remember { mutableStateOf<String?>(null) }
    var isModuleInstalled by remember { mutableStateOf(true) }
    var isRebootRequired by remember { mutableStateOf(false) }
    var currentProfile by remember {
        mutableStateOf(readSystemProperty("sys.lumina.profile") ?: "Balanced")
    }
    var isRootGranted by remember { mutableStateOf(false) }
    var selinuxStatus by remember { mutableStateOf("Unknown") }
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
            val propProfile = readSystemProperty("sys.lumina.profile")
            val initialRoot = RootUtil.checkRootAccess()
            val initialSelinux = DeviceUtil.getSelinuxStatus()

            withContext(Dispatchers.Main) {
                batteryTemp = initialTemp
                daemonPid = initialStatus.pid
                currentProfile = propProfile ?: initialStatus.profile
                isRootGranted = initialRoot
                selinuxStatus = initialSelinux
                isChecking = false
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
            val refreshedRoot = RootUtil.checkRootAccess()
            val refreshedSelinux = DeviceUtil.getSelinuxStatus()
            withContext(Dispatchers.Main) {
                isRootGranted = refreshedRoot
                selinuxStatus = refreshedSelinux
            }

            var fallbackChecks = 0
            while (isActive) {
                if (pagerState.isScrollInProgress) {
                    delay(200L)
                    continue
                }
                val currentTemp = DeviceUtil.getBatteryTemperature(context)
                val propProfile = readSystemProperty("sys.lumina.profile")
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
                    currentProfile = propProfile ?: status.profile
                }
                delay(2500L)
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
        currentProfile = "-"
        DaemonManager.restartDaemon(context)
        val updated = DaemonManager.getStatus()
        daemonPid = updated.pid
        currentProfile = readSystemProperty("sys.lumina.profile") ?: updated.profile
        if (updated.pid == null) {
            isRebootRequired = DaemonManager.isRebootRequired()
            isModuleInstalled = DaemonManager.isModuleInstalled()
        }
    }

    var overlayConfig by remember { mutableStateOf(FloatingFpsPrefs.load(context)) }
    val homeScope = rememberCoroutineScope()

    LuminaDrillTransition(
        targetState = currentSubscreen,
        isForward = { _, target -> target != null }
    ) { subscreen ->
        if (subscreen == "personalisasi") {
            PersonalisasiScreen(onBackClick = { currentSubscreen = null })
        } else if (subscreen == "session_stats") {
            BackHandler { currentSubscreen = null }
            SessionStatsScreen(onBack = { currentSubscreen = null })
        } else if (subscreen == "telemetry_overlay") {
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
        } else if (isFloating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
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
                            isRootGranted = isRootGranted,
                            selinuxStatus = selinuxStatus,
                            onRequestRebootDialog = { showRebootDialog = true },
                            onRestartDaemon = restartAction
                        )
                        1 -> AppsScreen(
                            pagerState = pagerState,
                            onOpenSessionStats = { currentSubscreen = "session_stats" }
                        )
                        2 -> TweaksScreen(
                            pagerState = pagerState,
                            onOpenFloatingHud = {
                                overlayConfig = FloatingFpsPrefs.load(context)
                                currentSubscreen = "telemetry_overlay"
                            },
                            onOpenSessionStats = { currentSubscreen = "session_stats" }
                        )
                        3 -> SettingsScreen(
                            onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" },
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
                            isRootGranted = isRootGranted,
                            selinuxStatus = selinuxStatus,
                            onRequestRebootDialog = { showRebootDialog = true },
                            onRestartDaemon = restartAction
                        )
                        1 -> AppsScreen(
                            pagerState = pagerState,
                            onOpenSessionStats = { currentSubscreen = "session_stats" }
                        )
                        2 -> TweaksScreen(
                            pagerState = pagerState,
                            onOpenFloatingHud = {
                                overlayConfig = FloatingFpsPrefs.load(context)
                                currentSubscreen = "telemetry_overlay"
                            },
                            onOpenSessionStats = { currentSubscreen = "session_stats" }
                        )
                        3 -> SettingsScreen(
                            onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" },
                            onOpenSetupWizard = onOpenSetupWizard
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
        modifier = modifier,
        contentKey = contentKey,
        transitionSpec = {
            val forward = isForward(initialState, targetState)
            val transform = if (forward) {
                slideInHorizontally(
                    animationSpec = tween(DRILL_TRANSITION_DURATION, easing = DrillEmphasizedDecelerate)
                ) { it } togetherWith
                    slideOutHorizontally(
                        animationSpec = tween(DRILL_TRANSITION_DURATION, easing = DrillEmphasizedAccelerate)
                    ) { w -> -(w * 0.25f).toInt() }
            } else {
                slideInHorizontally(
                    animationSpec = tween(DRILL_TRANSITION_DURATION, easing = DrillEmphasizedDecelerate)
                ) { w -> -(w * 0.25f).toInt() } togetherWith
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
    isRootGranted: Boolean,
    selinuxStatus: String,
    onRequestRebootDialog: () -> Unit,
    onRestartDaemon: suspend () -> Unit,
    onNavigateToPersonalisasi: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
                color = MaterialTheme.colorScheme.onSurface
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
    val bottomPadding = if (isFloating) {
        112.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val fadeSurface = MaterialTheme.colorScheme.background

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .appBarFade(fadeSurface, scrollBehavior.state.overlappedFraction)
                    .statusBarsPadding()
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item(key = "status") {
                Spacer(modifier = Modifier.height(12.dp))
                CoreStatusHero(
                    modifier = heroOrigin.trackedModifier(),
                    isChecking = isChecking,
                    isRestarting = isRestarting,
                    isRunning = isRunning,
                    isModuleInstalled = isModuleInstalled,
                    isRebootRequired = isRebootRequired,
                    daemonPid = daemonPid,
                    onStart = {
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
            }

            item(key = "metrics") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HomeHardwareTile(
                        label = stringResource(R.string.home_thermal),
                        value = batteryTemp,
                        icon = Icons.Rounded.Thermostat,
                        highlight = false,
                        modifier = Modifier.weight(1f)
                    )
                    HomeHardwareTile(
                        label = stringResource(R.string.home_profile),
                        value = currentProfile,
                        icon = Icons.Rounded.Speed,
                        highlight = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "device") {
                Spacer(modifier = Modifier.height(16.dp))
                AZenithDeviceInfoCard(
                    kernelVersion = kernelVersion,
                    deviceModel = deviceModel,
                    isRootGranted = isRootGranted,
                    selinuxStatus = selinuxStatus
                )
            }

            item(key = "support") {
                val uriHandler = LocalUriHandler.current
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HomeActionRow(
                        index = 0,
                        count = 2,
                        icon = Icons.Rounded.Favorite,
                        title = stringResource(R.string.home_support_us),
                        subtitle = stringResource(R.string.home_support_us_desc),
                        onClick = { uriHandler.openUri("https://sociabuzz.com/kimal404") }
                    )
                    HomeActionRow(
                        index = 1,
                        count = 2,
                        icon = Icons.Rounded.MenuBook,
                        title = stringResource(R.string.home_learn_more),
                        subtitle = stringResource(R.string.home_learn_more_desc),
                        onClick = { uriHandler.openUri("https://github.com/kimal404/LuminaTweaks") }
                    )
                }
            }
        }
    }
}

@Composable
private fun AZenithDeviceInfoCard(
    kernelVersion: String,
    deviceModel: String,
    isRootGranted: Boolean,
    selinuxStatus: String
) {
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    var isExpanded by remember { mutableStateOf(false) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expandArrowRotation"
    )

    val totalRows = if (isExpanded) 5 else 3
    val innerRowColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val infoInteraction = remember { MutableInteractionSource() }
    val infoPressScale = pressScale(infoInteraction, 0.98f)

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = colorScheme.surfaceColorAtElevation(1.dp),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            isExpanded = !isExpanded
        },
        interactionSource = infoInteraction,
        modifier = Modifier
            .fillMaxWidth()
            .then(infoPressScale)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeSquircleIcon(icon = Icons.Outlined.Info)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.aosp_section_device),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) {
                        stringResource(R.string.aosp_action_collapse)
                    } else {
                        stringResource(R.string.aosp_action_expand)
                    },
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = rotationAngle
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                HomeInfoRow(
                    index = 0,
                    count = totalRows,
                    icon = Icons.Rounded.DeveloperBoard,
                    title = stringResource(R.string.home_manager_version),
                    subtitle = stringResource(
                        R.string.home_version_release_format,
                        com.luminatweaks.BuildConfig.VERSION_NAME.removeSuffix(".0")
                    ),
                    containerColor = innerRowColor,
                    outerRadius = 18.dp
                )
                HomeInfoRow(
                    index = 1,
                    count = totalRows,
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.home_kernel_version),
                    subtitle = kernelVersion,
                    containerColor = innerRowColor,
                    outerRadius = 18.dp
                )
                HomeInfoRow(
                    index = 2,
                    count = totalRows,
                    icon = Icons.Rounded.Smartphone,
                    title = stringResource(R.string.home_device_model),
                    subtitle = deviceModel,
                    containerColor = innerRowColor,
                    outerRadius = 18.dp
                )

                if (isExpanded) {
                    HomeInfoRow(
                        index = 3,
                        count = totalRows,
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.home_root_access),
                        subtitle = stringResource(
                            if (isRootGranted) R.string.home_root_granted else R.string.home_root_denied
                        ),
                        highlightSubtitle = isRootGranted,
                        containerColor = innerRowColor,
                        outerRadius = 18.dp
                    )
                    HomeInfoRow(
                        index = 4,
                        count = totalRows,
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.home_selinux_status),
                        subtitle = selinuxStatus,
                        containerColor = innerRowColor,
                        outerRadius = 18.dp
                    )
                }
            }
        }
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
private fun HomeHardwareTile(
    label: String,
    value: String,
    icon: ImageVector,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val iconBoxBgColor by animateColorAsState(
        targetValue = if (highlight) {
            colorScheme.primaryContainer
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(350),
        label = "homeTileBg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (highlight) {
            colorScheme.onPrimaryContainer
        } else {
            colorScheme.onSurfaceVariant
        },
        animationSpec = tween(350),
        label = "homeTileIcon"
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = colorScheme.surfaceColorAtElevation(1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(iconBoxBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeInfoRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlightSubtitle: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
    outerRadius: Dp = 26.dp
) {
    val shape = connectedContainerShape(
        index = index,
        count = count,
        outerRadius = outerRadius,
        innerRadius = 4.dp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            HomeSquircleIcon(icon = icon)
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
                color = if (highlightSubtitle) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeActionRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f, enabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            HomeSquircleIcon(icon = icon)
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
private fun CoreStatusHero(
    isChecking: Boolean,
    isRestarting: Boolean,
    isRunning: Boolean,
    isModuleInstalled: Boolean,
    isRebootRequired: Boolean,
    daemonPid: String?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAlive = isRunning && !isRestarting
    val heroInteraction = remember { MutableInteractionSource() }
    val heroPressScale = pressScale(heroInteraction, 0.97f)
    val containerColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.surfaceContainerHighest
            isAlive -> MaterialTheme.colorScheme.secondaryContainer
            isRebootRequired -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "bannerContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.onSurfaceVariant
            isAlive -> MaterialTheme.colorScheme.onSecondaryContainer
            isRebootRequired -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onErrorContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "bannerContent"
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
        onClick = onStart,
        interactionSource = heroInteraction,
        shape = RoundedCornerShape(26.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .then(heroPressScale)
            .height(100.dp)
            .animateContentSize(animationSpec = spring())
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                isChecking || isRestarting -> CircularWavyProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.24f)
                )
                isAlive -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(42.dp)
                )
                isRebootRequired -> Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(42.dp)
                )
                !isModuleInstalled -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(42.dp)
                )
                else -> Icon(
                    imageVector = Icons.Rounded.HourglassEmpty,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(42.dp)
                )
            }

            Box(
                modifier = Modifier
                    .height(42.dp)
                    .width(1.5.dp)
                    .background(contentColor.copy(alpha = 0.3f))
            )

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isAlive && cleanPid != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "PID: $cleanPid",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
        }
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
