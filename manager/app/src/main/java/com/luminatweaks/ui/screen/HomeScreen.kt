/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
)

package com.luminatweaks.ui.screen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpGraphicsColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.luminatweaks.BuildConfig
import com.luminatweaks.R
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.gamedeck.GameDeckSettingsScreen
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.component.ExpressiveInfoTile
import com.luminatweaks.ui.component.LuminaBottomBar
import com.luminatweaks.ui.component.LuminaMicroTicker
import com.luminatweaks.ui.component.OdometerNumber
import com.luminatweaks.ui.component.TouchOriginDialog
import com.luminatweaks.ui.component.bouncyClickable
import com.luminatweaks.ui.component.dampOverscrollPull
import com.luminatweaks.ui.component.luminaHazeHeader
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.component.rememberDialogOrigin
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.ui.theme.LocalPageTransition
import com.luminatweaks.ui.theme.LuminaArtworkManager
import com.luminatweaks.ui.theme.LuminaArtworkState
import com.luminatweaks.ui.theme.PageTransition
import com.luminatweaks.ui.theme.rememberLuminaArtworkState
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.RootUtil
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ContainerPosition {
    Top, Middle, Bottom, Single
}

private data class SystemTelemetrySnapshot(
    val ramUsedGb: Float,
    val ramTotalGb: Float,
    val ramPercent: Float,
    val batteryPercent: Int,
    val batteryVoltageV: Float,
    val isCharging: Boolean
)

private data class EnginePassInfo(
    val rootProvider: String,
    val ipcReachable: Boolean,
    val binderReady: Boolean,
    val capabilityMode: String
)

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
    val tabFadeScale = remember { Animatable(1f) }

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
                        tabFadeScale.animateTo(0.955f, tween(110, easing = FastOutSlowInEasing))
                        pagerState.scrollToPage(index)
                        tabFadeScale.animateTo(1f, spring(dampingRatio = 0.84f, stiffness = 420f))
                    } else {
                        launch {
                            tabFadeScale.snapTo(0.975f)
                            tabFadeScale.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 420f))
                        }
                        pagerState.animateScrollToPage(
                            page = index,
                            animationSpec = tween(
                                durationMillis = 250,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
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

    var activeTweaksSubpage by rememberSaveable { mutableStateOf<TweaksSubpage?>(null) }
    var tweaksTargetHz by rememberSaveable { mutableIntStateOf(120) }
    val isAnyOverlayOpen = currentSubscreen != null || activeTweaksSubpage != null

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

    LaunchedEffect(pagerState.currentPage, isScreenResumed, isAnyOverlayOpen, resumeToken) {
        if (pagerState.currentPage != 0 || !isScreenResumed || isAnyOverlayOpen) {
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

    LaunchedEffect(pagerState.currentPage, isScreenResumed, isAnyOverlayOpen, resumeToken) {
        if (pagerState.currentPage != 0 || !isScreenResumed || isAnyOverlayOpen) {
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

    val subpageBounds = remember { mutableStateMapOf<TweaksSubpage, Rect>() }
    var displayedTweaksSubpage by remember { mutableStateOf<TweaksSubpage?>(null) }
    val expandProgress = remember { Animatable(0f) }
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(activeTweaksSubpage) {
        if (activeTweaksSubpage != null) {
            displayedTweaksSubpage = activeTweaksSubpage
            expandProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f)
            )
        } else if (displayedTweaksSubpage != null) {
            expandProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 430f)
            )
            displayedTweaksSubpage = null
        }
    }

    val p = expandProgress.value.coerceIn(0f, 1f)
    val containerDepthScale = 1f - 0.045f * p
    val containerScrimAlpha = 0.36f * p

    BackHandler(enabled = activeTweaksSubpage != null) {
        activeTweaksSubpage = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { rootSizePx = it }
    ) {
        val pagerScaleModifier = Modifier.graphicsLayer {
            val s = tabFadeScale.value * containerDepthScale
            scaleX = s
            scaleY = s
            alpha = ((tabFadeScale.value - 0.94f) / 0.06f).coerceIn(0f, 1f)
        }

        if (isFloating) {
            Box(modifier = Modifier.fillMaxSize()) {
                LuminaTabPager(
                    pagerState = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(pagerScaleModifier),
                    userScrollEnabled = !isAnyOverlayOpen
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
                            onRegisterSubpageBounds = { subpage, rect ->
                                subpageBounds[subpage] = rect
                            },
                            onOpenSubpage = { subpage, hz ->
                                if (subpage == TweaksSubpage.FLOATING_HUD) {
                                    overlayConfig = FloatingFpsPrefs.load(context)
                                }
                                tweaksTargetHz = hz
                                activeTweaksSubpage = subpage
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
                    visible = !isAnyOverlayOpen,
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
                        visible = !isAnyOverlayOpen,
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
                        .padding(innerPadding)
                        .then(pagerScaleModifier),
                    userScrollEnabled = !isAnyOverlayOpen
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
                            onRegisterSubpageBounds = { subpage, rect ->
                                subpageBounds[subpage] = rect
                            },
                            onOpenSubpage = { subpage, hz ->
                                if (subpage == TweaksSubpage.FLOATING_HUD) {
                                    overlayConfig = FloatingFpsPrefs.load(context)
                                }
                                tweaksTargetHz = hz
                                activeTweaksSubpage = subpage
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

        if (containerScrimAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(Color.Black.copy(alpha = containerScrimAlpha))
            )
        }

        val currentSubpage = displayedTweaksSubpage
        if (currentSubpage != null && rootSizePx.width > 0 && rootSizePx.height > 0) {
            val density = LocalDensity.current
            val fullW = rootSizePx.width.toFloat()
            val fullH = rootSizePx.height.toFloat()
            val startRect = subpageBounds[currentSubpage] ?: Rect(
                left = fullW * 0.06f,
                top = fullH * 0.36f,
                right = fullW * 0.94f,
                bottom = fullH * 0.52f
            )
            val curLeft = lerpFloat(startRect.left, 0f, p)
            val curTop = lerpFloat(startRect.top, 0f, p)
            val curRight = lerpFloat(startRect.right, fullW, p)
            val curBottom = lerpFloat(startRect.bottom, fullH, p)
            val curWidthDp = with(density) { (curRight - curLeft).coerceAtLeast(1f).toDp() }
            val curHeightDp = with(density) { (curBottom - curTop).coerceAtLeast(1f).toDp() }
            val cornerDp = lerp(26.dp, 0.dp, p)
            val morphBg = lerpGraphicsColor(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.background,
                p
            )
            val contentAlpha = ((p - 0.14f) / 0.46f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .zIndex(2f)
                    .offset { IntOffset(curLeft.roundToInt(), curTop.roundToInt()) }
                    .size(width = curWidthDp, height = curHeightDp)
                    .clip(RoundedCornerShape(cornerDp))
                    .background(morphBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    when (currentSubpage) {
                        TweaksSubpage.FLOATING_HUD -> TelemetryOverlayScreen(
                            config = overlayConfig,
                            targetHz = tweaksTargetHz,
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
                            onBack = { activeTweaksSubpage = null }
                        )
                        TweaksSubpage.GAME_BAR -> GameDeckSettingsScreen(
                            onBack = { activeTweaksSubpage = null }
                        )
                        TweaksSubpage.MEMORY_ZRAM -> MemoryZramScreen(
                            onBack = { activeTweaksSubpage = null }
                        )
                        TweaksSubpage.HARDWARE -> HardwareDiagnosticScreen(
                            onBack = { activeTweaksSubpage = null }
                        )
                        TweaksSubpage.UTILITY -> UtilityScreen(
                            onBack = { activeTweaksSubpage = null }
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
                .zIndex(3f)
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
    val density = LocalDensity.current
    var isRestarting by remember { mutableStateOf(false) }
    var showPowerDialog by remember { mutableStateOf(false) }
    val isRunning = daemonPid != null

    val artworkState = rememberLuminaArtworkState()
    var headerBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artworkState.revision, artworkState.enabled) {
        headerBitmap = if (artworkState.enabled) {
            withContext(Dispatchers.IO) {
                LuminaArtworkManager.loadBitmap(context)
            }
        } else {
            null
        }
    }

    val socModel = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        } else {
            null
        } ?: Build.HARDWARE
    }

    var telemetry by remember { mutableStateOf(readSystemTelemetry(context)) }
    var enginePass by remember {
        mutableStateOf(
            EnginePassInfo(
                rootProvider = if (isRootGranted) "Root" else "Checking",
                ipcReachable = daemonPid != null,
                binderReady = false,
                capabilityMode = "observe"
            )
        )
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            telemetry = readSystemTelemetry(context)
            delay(3000L)
        }
    }

    LaunchedEffect(daemonPid) {
        withContext(Dispatchers.IO) {
            val provider = RootUtil.detectRootProvider() ?: if (RootUtil.checkRootAccess()) "Root" else "None"
            val ipc = DaemonManager.isIpcReachable()
            val binder = DaemonManager.isBinderBridgeReady()
            val mode = DaemonManager.getCapabilityMode()
            withContext(Dispatchers.Main) {
                enginePass = EnginePassInfo(
                    rootProvider = provider,
                    ipcReachable = ipc,
                    binderReady = binder,
                    capabilityMode = mode
                )
            }
        }
    }

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
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()

    val maxRevealPx = with(density) { 148.dp.toPx() }
    val pullAnim = remember { Animatable(0f) }
    var rawPullPx by remember { mutableFloatStateOf(0f) }
    var thresholdHapticFired by remember { mutableStateOf(false) }

    val elasticRevealConnection = remember(listState, scrollBehavior, maxRevealPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && rawPullPx > 0f) {
                    val consumedY = maxOf(available.y, -rawPullPx)
                    rawPullPx = (rawPullPx + consumedY).coerceAtLeast(0f)
                    restartScope.launch { pullAnim.snapTo(rawPullPx) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    scrollBehavior.state.heightOffset >= -0.5f
                if (source == NestedScrollSource.UserInput && available.y > 0f && atTop) {
                    rawPullPx = (rawPullPx + available.y).coerceAtMost(840f)
                    val damped = dampOverscrollPull(rawPullPx, maxRevealPx)
                    if (damped >= maxRevealPx * 0.72f && !thresholdHapticFired) {
                        thresholdHapticFired = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else if (damped < maxRevealPx * 0.5f) {
                        thresholdHapticFired = false
                    }
                    restartScope.launch { pullAnim.snapTo(rawPullPx) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (rawPullPx > 0f) {
                    rawPullPx = 0f
                    thresholdHapticFired = false
                    restartScope.launch {
                        pullAnim.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 340f)
                        )
                    }
                }
                return Velocity.Zero
            }
        }
    }

    val hasHeaderArtwork = headerBitmap != null

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = scheme.background,
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (!hasHeaderArtwork) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .luminaHazeHeader(
                                hazeState = hazeState,
                                surfaceColor = scheme.background,
                                overlappedFraction = scrollBehavior.state.overlappedFraction
                            )
                    )
                }
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
                    windowInsets = TopAppBarDefaults.windowInsets
                )
            }
        }
    ) { innerPadding ->
        val topBarCutoffPx = with(density) { innerPadding.calculateTopPadding().toPx() }
        val fadeBandPx = with(density) { 18.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background)
                .then(
                    if (!hasHeaderArtwork) {
                        Modifier.hazeSource(state = hazeState)
                    } else {
                        Modifier
                    }
                )
        ) {
            if (headerBitmap != null) {
                HomeHeaderArtworkBackdrop(
                    bitmap = headerBitmap!!,
                    artwork = artworkState,
                    surfaceColor = scheme.background
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(elasticRevealConnection)
                    .then(
                        if (hasHeaderArtwork) {
                            Modifier
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    val h = size.height.coerceAtLeast(1f)
                                    val clearStop = ((topBarCutoffPx - fadeBandPx * 0.6f) / h).coerceIn(0f, 0.95f)
                                    val solidStop = ((topBarCutoffPx + fadeBandPx * 0.4f) / h).coerceIn(clearStop + 0.005f, 1f)
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.0f to Color.Transparent,
                                            clearStop to Color.Transparent,
                                            solidStop to Color.Black,
                                            1.0f to Color.Black
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        } else {
                            Modifier
                        }
                    ),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    bottom = bottomPadding,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "elastic_engine_reveal") {
                    val dampedPx = dampOverscrollPull(pullAnim.value, maxRevealPx)
                    val revealHeightDp = with(density) { dampedPx.toDp() }
                    val progress = (dampedPx / (maxRevealPx * 0.85f)).coerceIn(0f, 1f)

                    if (dampedPx > 0.5f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(revealHeightDp)
                                .clipToBounds(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            EngineDiagnosticPassCard(
                                info = enginePass,
                                daemonPid = daemonPid,
                                modifier = Modifier.graphicsLayer {
                                    alpha = progress
                                    val s = 0.92f + 0.08f * progress
                                    scaleX = s
                                    scaleY = s
                                }
                            )
                        }
                    }
                }

                item(key = "hero_bento") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
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
                                    .weight(0.58f)
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
                                modifier = Modifier.weight(0.42f),
                                label = homeLocalizedLabel("Profil aktif", "Active profile"),
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

                item(key = "telemetry_bento_pair") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MemoryBentoTile(
                            modifier = Modifier.weight(1f),
                            telemetry = telemetry
                        )

                        BatteryBentoTile(
                            modifier = Modifier.weight(1f),
                            telemetry = telemetry
                        )
                    }
                }

                item(key = "device_specs_card") {
                    DeviceSpecsBentoCard(
                        deviceLabel = homeLocalizedLabel("Model perangkat", "Device model"),
                        deviceValue = deviceModel,
                        socLabel = "SoC",
                        socValue = socModel,
                        kernelLabel = homeLocalizedLabel("Versi kernel", "Kernel version"),
                        kernelValue = kernelVersion,
                        managerLabel = homeLocalizedLabel("Versi manajer", "Manager version"),
                        managerValue = stringResource(
                            R.string.home_version_release_format,
                            BuildConfig.VERSION_NAME.removeSuffix(".0")
                        )
                    )
                }

                item(key = "uptime_bento_card") {
                    UptimeBentoCard()
                }

                item(key = "telegram_channel_card") {
                    TelegramChannelBentoCard(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uriHandler.openUri("https://t.me/MallArchiven")
                        }
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
                            subtitle = homeLocalizedLabel("Dukung proyek", "Support project"),
                            accentColor = scheme.tertiary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                uriHandler.openUri("https://sociabuzz.com/kimal404")
                            }
                        )

                        ExpressiveInfoTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.MenuBook,
                            title = homeLocalizedLabel("Dokumentasi", "Documentation"),
                            subtitle = homeLocalizedLabel("Panduan & GitHub", "Guides & GitHub"),
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
}

@Composable
private fun HomeHeaderArtworkBackdrop(
    bitmap: ImageBitmap,
    artwork: LuminaArtworkState,
    surfaceColor: Color
) {
    val isDark = surfaceColor.luminance() < 0.5f
    val effectiveAlpha = if (isDark) artwork.alpha * 0.88f else artwork.alpha
    val fade = artwork.fadeStrength.coerceIn(0.2f, 1.0f)

    val clearStop = (0.48f - fade * 0.28f).coerceIn(0.15f, 0.45f)
    val midStop = (0.78f - fade * 0.20f).coerceIn(clearStop + 0.12f, 0.76f)
    val endStop = (0.98f - fade * 0.10f).coerceIn(midStop + 0.10f, 0.98f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(artwork.bannerHeightDp.dp)
            .clipToBounds()
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            alpha = effectiveAlpha,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (artwork.blurDp > 0.5f) {
                        Modifier.blur(artwork.blurDp.dp)
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    scaleX = artwork.zoom
                    scaleY = artwork.zoom
                    translationX = artwork.offsetX * size.width * 0.35f
                    translationY = artwork.offsetY * size.height * 0.40f
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to surfaceColor.copy(alpha = 0.32f),
                        clearStop to Color.Transparent,
                        midStop to surfaceColor.copy(alpha = 0.58f),
                        endStop to surfaceColor.copy(alpha = 0.94f),
                        1.0f to surfaceColor
                    )
                )
        )
    }
}

@Composable
private fun EngineDiagnosticPassCard(
    info: EnginePassInfo,
    daemonPid: String?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHighest)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "LUMINA ENGINE // PASS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = scheme.primary
                )
            }

            Text(
                text = "ROOT: ${info.rootProvider.uppercase(Locale.ROOT)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnginePassBadge(
                label = "IPC",
                value = if (info.ipcReachable || daemonPid != null) "ONLINE" else "OFFLINE",
                active = info.ipcReachable || daemonPid != null,
                modifier = Modifier.weight(1f)
            )
            EnginePassBadge(
                label = "BINDER",
                value = if (info.binderReady) "READY" else "STANDBY",
                active = info.binderReady,
                modifier = Modifier.weight(1f)
            )
            EnginePassBadge(
                label = "MODE",
                value = info.capabilityMode.uppercase(Locale.ROOT),
                active = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EnginePassBadge(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            color = scheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            color = if (active) scheme.primary else scheme.error,
            maxLines = 1,
            softWrap = false
        )
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            when {
                isChecking || isRestarting -> CircularWavyProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.24f)
                )
                isAlive -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(30.dp)
                )
                isRebootRequired -> Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(30.dp)
                )
                !isModuleInstalled -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(30.dp)
                )
                else -> Icon(
                    imageVector = Icons.Rounded.HourglassEmpty,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(30.dp)
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(19.dp)
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
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

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            OdometerNumber(
                value = batteryTemp,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = scheme.onSurface
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
private fun MemoryBentoTile(
    telemetry: SystemTelemetrySnapshot,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val animatedRam by animateFloatAsState(
        targetValue = telemetry.ramPercent.coerceIn(0.05f, 1f),
        animationSpec = spring(stiffness = 320f),
        label = "ramProgress"
    )
    val ramValueText = remember(telemetry.ramUsedGb, telemetry.ramTotalGb) {
        String.format(Locale.US, "%.1f / %.1f GB", telemetry.ramUsedGb, telemetry.ramTotalGb)
    }
    val usedPct = (telemetry.ramPercent * 100f).toInt().coerceIn(0, 100)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = homeLocalizedLabel("Memori RAM", "RAM Memory"),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        OdometerNumber(
            value = ramValueText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = scheme.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedRam)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(scheme.primary)
                )
            }

            Text(
                text = homeLocalizedLabel("$usedPct% terpakai", "$usedPct% in use"),
                style = MaterialTheme.typography.labelSmall.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun BatteryBentoTile(
    telemetry: SystemTelemetrySnapshot,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val animatedLevel by animateFloatAsState(
        targetValue = (telemetry.batteryPercent / 100f).coerceIn(0.05f, 1f),
        animationSpec = spring(stiffness = 320f),
        label = "batteryProgress"
    )
    val accent = when {
        telemetry.isCharging -> scheme.tertiary
        telemetry.batteryPercent <= 20 -> scheme.error
        else -> scheme.secondary
    }
    val statusSub = remember(telemetry.isCharging, telemetry.batteryVoltageV) {
        val stateLabel = if (telemetry.isCharging) "Charging" else "Discharging"
        if (telemetry.batteryVoltageV > 0f) {
            String.format(Locale.US, "%s • %.2f V", stateLabel, telemetry.batteryVoltageV)
        } else {
            stateLabel
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = homeLocalizedLabel("Baterai", "Battery"),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = accent,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (telemetry.isCharging) {
                        Icons.Rounded.BatteryChargingFull
                    } else {
                        Icons.Rounded.BatteryStd
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        OdometerNumber(
            value = "${telemetry.batteryPercent}%",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = scheme.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedLevel)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accent)
                )
            }

            Text(
                text = statusSub,
                style = MaterialTheme.typography.labelSmall.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UptimeBentoCard(
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var uptimeText by remember { mutableStateOf(formatHomeUptime()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            uptimeText = formatHomeUptime()
            delay(1000L)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.secondary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = scheme.secondary,
                modifier = Modifier.size(21.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = homeLocalizedLabel("Waktu aktif", "Device uptime"),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.secondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            OdometerNumber(
                value = uptimeText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = scheme.onSurface
            )
        }
    }
}

@Composable
private fun TelegramChannelBentoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.975f)
    val tickerText = homeLocalizedLabel(
        "@MallArchiven  •  Rilis terbaru, changelog & diskusi komunitas LuminaTweaks",
        "@MallArchiven  •  Latest releases, changelogs & LuminaTweaks community"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Send,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = homeLocalizedLabel("Channel Telegram", "Telegram Channel"),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            LuminaMicroTicker(
                text = tickerText,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                speed = 26.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
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
private fun DevicePhoneSvgIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.24f, h * 0.10f),
            size = Size(w * 0.52f, h * 0.80f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.43f, h * 0.20f),
            end = Offset(w * 0.57f, h * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.40f, h * 0.80f),
            end = Offset(w * 0.60f, h * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SocChipSvgIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val stroke = 1.5.dp.toPx()
        val pad = w * 0.22f
        val bodySize = w - pad * 2f

        drawRoundRect(
            color = tint,
            topLeft = Offset(pad, pad),
            size = Size(bodySize, bodySize),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = Stroke(width = stroke)
        )

        val innerPad = w * 0.36f
        val innerSize = w - innerPad * 2f
        drawRoundRect(
            color = tint.copy(alpha = 0.32f),
            topLeft = Offset(innerPad, innerPad),
            size = Size(innerSize, innerSize),
            cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx())
        )

        val pinPositions = listOf(0.35f, 0.50f, 0.65f)
        val pinLen = pad * 0.65f
        pinPositions.forEach { frac ->
            val pos = w * frac
            drawLine(
                color = tint,
                start = Offset(pos, pad - pinLen),
                end = Offset(pos, pad),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(pos, pad + bodySize),
                end = Offset(pos, pad + bodySize + pinLen),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(pad - pinLen, pos),
                end = Offset(pad, pos),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(pad + bodySize, pos),
                end = Offset(pad + bodySize + pinLen, pos),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun KernelTerminalSvgIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.18f),
            size = Size(w * 0.72f, h * 0.64f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        val promptPath = Path().apply {
            moveTo(w * 0.30f, h * 0.38f)
            lineTo(w * 0.44f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.62f)
        }
        drawPath(
            path = promptPath,
            color = tint,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.50f, h * 0.62f),
            end = Offset(w * 0.68f, h * 0.62f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ManagerShieldSvgIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        val shieldPath = Path().apply {
            moveTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.82f, h * 0.24f)
            lineTo(w * 0.82f, h * 0.50f)
            quadraticTo(w * 0.82f, h * 0.74f, w * 0.50f, h * 0.88f)
            quadraticTo(w * 0.18f, h * 0.74f, w * 0.18f, h * 0.50f)
            lineTo(w * 0.18f, h * 0.24f)
            close()
        }
        drawPath(
            path = shieldPath,
            color = tint,
            style = Stroke(
                width = stroke,
                join = StrokeJoin.Round
            )
        )
        val checkPath = Path().apply {
            moveTo(w * 0.36f, h * 0.50f)
            lineTo(w * 0.46f, h * 0.60f)
            lineTo(w * 0.65f, h * 0.40f)
        }
        drawPath(
            path = checkPath,
            color = tint,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun DeviceSpecsBentoCard(
    deviceLabel: String,
    deviceValue: String,
    socLabel: String,
    socValue: String,
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeSvgSpecRow(
            label = deviceLabel,
            value = deviceValue,
            icon = { DevicePhoneSvgIcon(tint = scheme.primary, modifier = Modifier.size(18.dp)) }
        )
        HomeSvgSpecRow(
            label = socLabel,
            value = socValue,
            icon = { SocChipSvgIcon(tint = scheme.primary, modifier = Modifier.size(18.dp)) }
        )
        HomeSvgSpecRow(
            label = kernelLabel,
            value = kernelValue,
            icon = { KernelTerminalSvgIcon(tint = scheme.primary, modifier = Modifier.size(18.dp)) }
        )
        HomeSvgSpecRow(
            label = managerLabel,
            value = managerValue,
            icon = { ManagerShieldSvgIcon(tint = scheme.primary, modifier = Modifier.size(18.dp)) }
        )
    }
}

@Composable
private fun HomeSvgSpecRow(
    label: String,
    value: String,
    icon: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
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
}

private fun readSystemTelemetry(context: Context): SystemTelemetrySnapshot {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)
    val totalBytes = memInfo.totalMem.coerceAtLeast(1L)
    val availBytes = memInfo.availMem.coerceIn(0L, totalBytes)
    val usedBytes = totalBytes - availBytes
    val bytesPerGb = 1024f * 1024f * 1024f
    val ramTotalGb = totalBytes / bytesPerGb
    val ramUsedGb = usedBytes / bytesPerGb
    val ramPercent = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

    val batteryIntent = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()
    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val batteryPercent = if (level >= 0 && scale > 0) {
        ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
    } else {
        0
    }
    val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    val batteryVoltageV = if (voltageMv > 100) voltageMv / 1000f else voltageMv.toFloat()
    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL

    return SystemTelemetrySnapshot(
        ramUsedGb = ramUsedGb,
        ramTotalGb = ramTotalGb,
        ramPercent = ramPercent,
        batteryPercent = batteryPercent,
        batteryVoltageV = batteryVoltageV,
        isCharging = isCharging
    )
}

private fun formatHomeUptime(): String {
    val totalSec = (SystemClock.elapsedRealtime() / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return "${hours}h ${minutes}m ${seconds}s"
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
