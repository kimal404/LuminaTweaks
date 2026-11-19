// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.luminatweaks.R
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.component.AospChevron
import com.luminatweaks.ui.component.AospConnectedGroup
import com.luminatweaks.ui.component.AospMetricTile
import com.luminatweaks.ui.component.AospPreferenceItem
import com.luminatweaks.ui.component.AospSectionHeader
import com.luminatweaks.ui.component.AospStatusPill
import com.luminatweaks.ui.component.LuminaBottomBar
import com.luminatweaks.ui.component.AospTopBar
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.ui.theme.LocalPageTransition
import com.luminatweaks.ui.theme.PageTransition
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.RootUtil
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
private fun homeLocalizedLabel(id: String, en: String): String {
    val isIndonesian = remember {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        lang == "in" || lang == "id"
    }
    return if (isIndonesian) id else en
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
    outerRadius: androidx.compose.ui.unit.Dp = LocalCornerRadius.current,
    innerRadius: androidx.compose.ui.unit.Dp = if (outerRadius <= 4.dp) outerRadius else 4.dp
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

    if (showRebootDialog) {
        val coroutineScope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val currentRadius = LocalCornerRadius.current

        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_reboot_required_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_reboot_required_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
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
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text(
                        text = stringResource(R.string.action_later),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            shape = RoundedCornerShape(currentRadius.coerceAtLeast(24.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
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
                    modifier = Modifier.fillMaxSize()
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
                            onRestartDaemon = restartAction,
                            onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" }
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
                        .padding(innerPadding)
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
                            onRestartDaemon = restartAction,
                            onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" }
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
    pageContent: @Composable (Int) -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = 1,
        pageSpacing = 12.dp,
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

@Composable
internal fun <T> LuminaDrillTransition(
    targetState: T,
    isForward: (initial: T, target: T) -> Boolean,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any = { it ?: "root_state" },
    content: @Composable (T) -> Unit
) {
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val spatialFloat = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsFloat = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentKey = contentKey,
        transitionSpec = {
            val forward = isForward(initialState, targetState)
            val transform = if (forward) {
                (slideInHorizontally(animationSpec = spatialOffset) { w -> (w * 0.16f).toInt() } +
                    fadeIn(animationSpec = effectsFloat) +
                    scaleIn(initialScale = 0.96f, animationSpec = spatialFloat)) togetherWith
                    (slideOutHorizontally(animationSpec = spatialOffset) { w -> -(w * 0.08f).toInt() } +
                        fadeOut(animationSpec = effectsFloat) +
                        scaleOut(targetScale = 0.98f, animationSpec = spatialFloat))
            } else {
                (slideInHorizontally(animationSpec = spatialOffset) { w -> -(w * 0.08f).toInt() } +
                    fadeIn(animationSpec = effectsFloat) +
                    scaleIn(initialScale = 0.98f, animationSpec = spatialFloat)) togetherWith
                    (slideOutHorizontally(animationSpec = spatialOffset) { w -> (w * 0.16f).toInt() } +
                        fadeOut(animationSpec = effectsFloat) +
                        scaleOut(targetScale = 0.96f, animationSpec = spatialFloat))
            }
            transform.using(SizeTransform(clip = false))
        },
        label = "subscreenDrillTransition"
    ) { state ->
        content(state)
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
    onNavigateToPersonalisasi: () -> Unit
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

    if (showPowerDialog) {
        val currentRadius = LocalCornerRadius.current
        AlertDialog(
            onDismissRequest = { showPowerDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.power_menu_title),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PowerActionRow(
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
                        icon = Icons.Rounded.PowerSettingsNew,
                        title = stringResource(R.string.power_action_restart_service),
                        onClick = {
                            showPowerDialog = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            launchRestart()
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPowerDialog = false }) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            shape = RoundedCornerShape(currentRadius.coerceAtLeast(24.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    val isFloating = LocalFloatingNavBar.current
    val bottomPadding = if (isFloating) {
        104.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AospTopBar(
                title = stringResource(R.string.app_name),
                subtitle = homeLocalizedLabel("Dasbor performa", "Performance dashboard"),
                scrollBehavior = scrollBehavior,
                actions = {
                    if (isRunning && !isRestarting) {
                        IconButton(onClick = { launchRestart() }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.power_action_restart_service),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    CoreStatusIndicator(
                        isChecking = isChecking,
                        isRestarting = isRestarting,
                        isRunning = isRunning,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showPowerDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = bottomPadding,
                start = 0.dp,
                end = 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "status") {
                Column {
                    AospSectionHeader(title = stringResource(R.string.aosp_section_status))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        CoreStatusHero(
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
                                        showPowerDialog = true
                                    }
                                    isRebootRequired -> onRequestRebootDialog()
                                    else -> launchRestart()
                                }
                            }
                        )
                    }
                }
            }

            item(key = "metrics") {
                Column {
                    AospSectionHeader(title = stringResource(R.string.aosp_section_hardware))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AospMetricTile(
                            label = stringResource(R.string.home_thermal),
                            value = batteryTemp,
                            icon = Icons.Rounded.Thermostat,
                            modifier = Modifier.weight(1f)
                        )
                        AospMetricTile(
                            label = stringResource(R.string.home_profile),
                            value = currentProfile,
                            icon = Icons.Rounded.Speed,
                            valueColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item(key = "quick") {
                Column {
                    AospSectionHeader(title = stringResource(R.string.aosp_section_quick_actions))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        QuickActionRow(onPersonalize = onNavigateToPersonalisasi)
                    }
                }
            }

            item(key = "device") {
                var isDeviceExpanded by remember { mutableStateOf(false) }
                val arrowRotation by animateFloatAsState(
                    targetValue = if (isDeviceExpanded) 180f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "deviceInfoArrowRotation"
                )
                val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
                val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val baseRows = 3
                val extraRows = if (isDeviceExpanded) 2 else 0
                val totalRows = baseRows + extraRows

                Column {
                    AospSectionHeader(
                        title = stringResource(R.string.aosp_section_device),
                        trailing = {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isDeviceExpanded = !isDeviceExpanded
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = if (isDeviceExpanded) {
                                            stringResource(R.string.aosp_action_collapse)
                                        } else {
                                            stringResource(R.string.aosp_action_expand)
                                        },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(arrowRotation)
                                    )
                                }
                            }
                        }
                    )
                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AospPreferenceItem(
                            index = 0,
                            count = totalRows,
                            icon = Icons.Rounded.DeveloperBoard,
                            title = stringResource(R.string.home_manager_version),
                            subtitle = stringResource(
                                R.string.home_version_release_format,
                                com.luminatweaks.BuildConfig.VERSION_NAME.removeSuffix(".0")
                            ),
                            onClick = {}
                        )
                        AospPreferenceItem(
                            index = 1,
                            count = totalRows,
                            icon = Icons.Rounded.Memory,
                            title = stringResource(R.string.home_kernel_version),
                            subtitle = kernelVersion,
                            onClick = {}
                        )
                        AospPreferenceItem(
                            index = 2,
                            count = totalRows,
                            icon = Icons.Rounded.Smartphone,
                            title = stringResource(R.string.home_device_model),
                            subtitle = deviceModel,
                            onClick = {}
                        )
                        AnimatedVisibility(
                            visible = isDeviceExpanded,
                            enter = expandVertically(animationSpec = spatialSpec) + fadeIn(animationSpec = effectsSpec),
                            exit = shrinkVertically(animationSpec = spatialSpec) + fadeOut(animationSpec = effectsSpec)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AospPreferenceItem(
                                    index = 3,
                                    count = totalRows,
                                    icon = Icons.Rounded.Security,
                                    title = stringResource(R.string.home_root_access),
                                    subtitle = stringResource(
                                        if (isRootGranted) R.string.home_root_granted else R.string.home_root_denied
                                    ),
                                    highlightSubtitle = isRootGranted,
                                    onClick = {}
                                )
                                AospPreferenceItem(
                                    index = 4,
                                    count = totalRows,
                                    icon = Icons.Rounded.Security,
                                    title = stringResource(R.string.home_selinux_status),
                                    subtitle = selinuxStatus,
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
            }

            item(key = "support") {
                val uriHandler = LocalUriHandler.current
                Column {
                    AospSectionHeader(title = stringResource(R.string.aosp_section_support))
                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AospPreferenceItem(
                            index = 0,
                            count = 2,
                            icon = Icons.Rounded.Favorite,
                            title = stringResource(R.string.home_support_us),
                            subtitle = stringResource(R.string.home_support_us_desc),
                            onClick = { uriHandler.openUri("https://sociabuzz.com/kimal404") },
                            trailing = { AospChevron() }
                        )
                        AospPreferenceItem(
                            index = 1,
                            count = 2,
                            icon = Icons.Rounded.MenuBook,
                            title = stringResource(R.string.home_learn_more),
                            subtitle = stringResource(R.string.home_learn_more_desc),
                            onClick = { uriHandler.openUri("https://github.com/kimal404/LuminaTweaks") },
                            trailing = { AospChevron() }
                        )
                    }
                }
            }

            item(key = "footer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private suspend fun rootReboot(mode: String) {
    RootUtil.rebootDevice(mode)
}

@Composable
private fun PowerActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            AospChevron()
        }
    }
}

@Composable
private fun CoreStatusIndicator(
    isChecking: Boolean,
    isRestarting: Boolean,
    isRunning: Boolean,
    onClick: () -> Unit
) {
    val iconTint by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.onSurfaceVariant
            isRunning -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "indicatorTint"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.surfaceContainerHigh
            isRunning -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "indicatorContainer"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isChecking || isRestarting) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = iconTint,
                    trackColor = iconTint.copy(alpha = 0.24f)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = stringResource(R.string.power_menu_title),
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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
    onStart: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.surfaceContainer
            isRunning -> MaterialTheme.colorScheme.surfaceContainer
            isRebootRequired -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "coreContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.onSurface
            isRunning -> MaterialTheme.colorScheme.primary
            isRebootRequired -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "coreContent"
    )
    val title = when {
        isChecking -> stringResource(R.string.home_core_checking)
        isRestarting -> stringResource(R.string.home_core_starting)
        isRunning -> stringResource(R.string.home_core_alive)
        isRebootRequired -> stringResource(R.string.dialog_reboot_required_title)
        !isModuleInstalled -> stringResource(R.string.home_core_module_inactive)
        else -> stringResource(R.string.home_core_inactive)
    }
    val subtitle = when {
        isChecking || isRestarting -> stringResource(R.string.home_core_scanning)
        isRunning -> stringResource(
            R.string.home_core_pid_format,
            daemonPid?.takeIf { pid -> pid.all(Char::isDigit) } ?: "-"
        )
        isRebootRequired -> stringResource(R.string.home_core_reboot_hint)
        !isModuleInstalled -> stringResource(R.string.home_core_install_hint)
        else -> stringResource(R.string.home_core_tap_start)
    }
    val isAlive = isRunning && !isRestarting

    val aliveTransition = rememberInfiniteTransition(label = "coreAlive")
    val wavePhase by aliveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "coreWave"
    )

    Surface(
        onClick = onStart,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isAlive) {
                LiveWave(
                    phase = wavePhase,
                    color = contentColor,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(30.dp)
                )
            }

            Row(
                modifier = Modifier.padding(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = if (isAlive) 30.dp else 20.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isRunning && !isRestarting) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                contentColor.copy(alpha = 0.14f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isChecking || isRestarting -> CircularWavyProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = contentColor,
                            trackColor = contentColor.copy(alpha = 0.24f)
                        )
                        isAlive -> Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(28.dp)
                        )
                        isRebootRequired -> Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(28.dp)
                        )
                        else -> Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.5.sp,
                        color = contentColor.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                AospStatusPill(
                    text = if (isAlive) {
                        stringResource(R.string.aosp_label_online)
                    } else {
                        stringResource(R.string.aosp_label_offline)
                    },
                    containerColor = contentColor.copy(alpha = 0.14f),
                    contentColor = contentColor,
                    leadingDotColor = if (isAlive) contentColor else contentColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun LiveWave(
    phase: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val path = androidx.compose.ui.graphics.Path()
        val amplitude = size.height * 0.22f
        val cycles = 2.2f
        val step = size.width / 72f
        val baseline = size.height * 0.74f
        var x = 0f

        while (x <= size.width) {
            val envelope = sin((x / size.width) * PI.toFloat())
            val angle = (x / size.width) * cycles * 2f * PI.toFloat() +
                phase * 2f * PI.toFloat()
            val y = baseline + sin(angle) * amplitude * envelope
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += step
        }

        drawPath(
            path = path,
            color = color.copy(alpha = 0.35f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

@Composable
private fun QuickActionRow(
    onPersonalize: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isTrimming by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }

    val runTrim: () -> Unit = {
        if (!isTrimming) {
            isTrimming = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                val command =
                    "if command -v fstrim >/dev/null 2>&1; then " +
                        "fstrim /data || command fstrim /data; " +
                        "[ -d /cache ] && { fstrim /cache || true; }; " +
                        "fi; " +
                        "sm fstrim >/dev/null 2>&1; " +
                        "pm trim-caches 999G >/dev/null 2>&1; " +
                        "echo TRIM_EXIT:$?"
                val (exitOk, output) = RootUtil.runAsRootWithOutput(command)
                val trimmed = output.contains("TRIM_EXIT:0")
                Toast.makeText(
                    context,
                    context.getString(
                        when {
                            !exitOk && output.contains("Permission denied") ->
                                R.string.toast_trim_failed
                            trimmed -> R.string.toast_trim_done
                            else -> R.string.toast_trim_no_space
                        }
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                isTrimming = false
            }
        }
    }

    val runCache: () -> Unit = {
        if (!isClearing) {
            isClearing = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                val ok = RootUtil.runAsRoot("sync; echo 3 > /proc/sys/vm/drop_caches; pm trim-caches 999G")
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.toast_cache_done else R.string.toast_cache_failed),
                    Toast.LENGTH_SHORT
                ).show()
                isClearing = false
            }
        }
    }

    AospConnectedGroup {
        AospPreferenceItem(
            index = 0,
            count = 3,
            icon = Icons.Rounded.AutoFixHigh,
            title = stringResource(R.string.home_quick_trim),
            subtitle = homeLocalizedLabel(
                "Keluarkan blok penyimpanan yang tidak terpakai",
                "Release unused storage blocks"
            ),
            onClick = runTrim,
            enabled = !isTrimming,
            trailing = { AospChevron() }
        )
        AospPreferenceItem(
            index = 1,
            count = 3,
            icon = Icons.Rounded.CleaningServices,
            title = stringResource(R.string.home_quick_cache),
            subtitle = homeLocalizedLabel(
                "Kosongkan cache RAM dan berkas sementara",
                "Flush RAM cache and temporary files"
            ),
            onClick = runCache,
            enabled = !isClearing,
            trailing = { AospChevron() }
        )
        AospPreferenceItem(
            index = 2,
            count = 3,
            icon = Icons.Rounded.Palette,
            title = stringResource(R.string.home_quick_personalize),
            subtitle = homeLocalizedLabel(
                "Buka personalisasi warna dan tema",
                "Open colour and theme personalisation"
            ),
            onClick = { onPersonalize() },
            trailing = { AospChevron() }
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
    iconContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconTint: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = getConnectedContainerShape(position),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    iconContainer: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    iconTint: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = getConnectedContainerShape(position),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            AospChevron()
        }
    }
}
