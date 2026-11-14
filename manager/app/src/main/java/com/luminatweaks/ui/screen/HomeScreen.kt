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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.luminatweaks.R
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.ui.component.LuminaBottomBar
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
    // The bar indicator follows the tapped tab immediately. It is only synced
    // back to the pager for manual swipes, so a far jump never lights up the
    // tabs that are merely crossed by the scroll animation.
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
                        // Skip the intermediate page so a long jump does not
                        // animate through the heavy apps list at full cost.
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
    var currentFps by remember { mutableStateOf("-") }
    var isRootGranted by remember { mutableStateOf(false) }
    var selinuxStatus by remember { mutableStateOf("Unknown") }
    var isIpcAlive by remember { mutableStateOf(false) }
    var socLabel by remember { mutableStateOf<String?>(null) }
    var isBinderReady by remember { mutableStateOf(false) }
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
                currentFps = initialStatus.fps
                isRootGranted = initialRoot
                selinuxStatus = initialSelinux
                isChecking = false
            }

            val initialIpc = initialStatus.pid != null && DaemonManager.isIpcReachable()
            val initialSoc = DaemonManager.getSocLabel()
            val initialBinder = moduleInstalled && DaemonManager.isBinderBridgeReady()

            withContext(Dispatchers.Main) {
                isIpcAlive = initialIpc
                socLabel = initialSoc
                isBinderReady = initialBinder
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, isScreenResumed, currentSubscreen, resumeToken) {
        if (pagerState.currentPage != 0 || !isScreenResumed || currentSubscreen != null) {
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            // Never start root/su checks while a page transition is in
            // flight; the pager may have just re-entered the dashboard.
            while (pagerState.isScrollInProgress && isActive) {
                delay(100L)
            }
            val refreshedRoot = RootUtil.checkRootAccess()
            val refreshedSelinux = DeviceUtil.getSelinuxStatus()
            val refreshedIpc = DaemonManager.isIpcReachable()
            val refreshedSoc = DaemonManager.getSocLabel()
            val refreshedBinder = DaemonManager.isBinderBridgeReady()
            withContext(Dispatchers.Main) {
                isRootGranted = refreshedRoot
                selinuxStatus = refreshedSelinux
                isIpcAlive = refreshedIpc
                socLabel = refreshedSoc
                isBinderReady = refreshedBinder
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
                val justStarted = status.pid != null && daemonPid == null
                // The stopped-daemon fallback shells out through su. Throttle it
                // to every 4th poll instead of spawning su every 2.5s.
                val checkFallback = status.pid == null && fallbackChecks % 4 == 0
                // Keep the transport indicator honest without pinging the
                // socket on every tick. A fresh start probes it immediately.
                val checkIpc = status.pid != null &&
                    (justStarted || fallbackChecks % 8 == 0)
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
                    currentFps = status.fps
                }

                if (checkIpc) {
                    val ipc = DaemonManager.isIpcReachable()
                    val soc = if (justStarted) DaemonManager.getSocLabel() else null
                    val binder = if (justStarted) DaemonManager.isBinderBridgeReady() else null
                    withContext(Dispatchers.Main) {
                        isIpcAlive = ipc
                        if (soc != null) socLabel = soc
                        if (binder != null) isBinderReady = binder
                    }
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
            shape = RoundedCornerShape(currentRadius.coerceAtLeast(16.dp)),
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
        currentFps = updated.fps
        val refreshedIpc = updated.pid != null && DaemonManager.isIpcReachable()
        val refreshedSoc = DaemonManager.getSocLabel()
        val refreshedBinder = DaemonManager.isBinderBridgeReady()
        withContext(Dispatchers.Main) {
            isIpcAlive = refreshedIpc
            socLabel = refreshedSoc
            isBinderReady = refreshedBinder
        }
        if (updated.pid == null) {
            isRebootRequired = DaemonManager.isRebootRequired()
            isModuleInstalled = DaemonManager.isModuleInstalled()
        }
    }

    LuminaDrillTransition(
        targetState = currentSubscreen,
        isForward = { _, target -> target != null }
    ) { subscreen ->
        if (subscreen == "personalisasi") {
            PersonalisasiScreen(onBackClick = { currentSubscreen = null })
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
                            currentFps = currentFps,
                            kernelVersion = kernelVersion,
                            deviceModel = deviceModel,
                            isRootGranted = isRootGranted,
                            selinuxStatus = selinuxStatus,
                            isIpcAlive = isIpcAlive,
                            socLabel = socLabel,
                            isBinderReady = isBinderReady,
                            onRequestRebootDialog = { showRebootDialog = true },
                            onRestartDaemon = restartAction,
                            onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" }
                        )
                        1 -> AppsScreen(pagerState = pagerState)
                        2 -> TweaksScreen(pagerState = pagerState)
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
                            currentFps = currentFps,
                            kernelVersion = kernelVersion,
                            deviceModel = deviceModel,
                            isRootGranted = isRootGranted,
                            selinuxStatus = selinuxStatus,
                            isIpcAlive = isIpcAlive,
                            socLabel = socLabel,
                            isBinderReady = isBinderReady,
                            onRequestRebootDialog = { showRebootDialog = true },
                            onRestartDaemon = restartAction,
                            onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" }
                        )
                        1 -> AppsScreen(pagerState = pagerState)
                        2 -> TweaksScreen(pagerState = pagerState)
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
    currentFps: String,
    kernelVersion: String,
    deviceModel: String,
    isRootGranted: Boolean,
    selinuxStatus: String,
    isIpcAlive: Boolean = false,
    socLabel: String? = null,
    isBinderReady: Boolean = false,
    onRequestRebootDialog: () -> Unit,
    onRestartDaemon: suspend () -> Unit,
    onNavigateToPersonalisasi: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isRestarting by remember { mutableStateOf(false) }
    var showPowerDialog by remember { mutableStateOf(false) }
    val isRunning = daemonPid != null

    val launchRestart: () -> Unit = {
        if (!isRestarting) {
            isRestarting = true
            Toast.makeText(context, context.getString(R.string.toast_restarting_service), Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PowerMenuItem(
                        icon = Icons.Rounded.Refresh,
                        iconBg = MaterialTheme.colorScheme.primary,
                        iconTint = MaterialTheme.colorScheme.onPrimary,
                        title = stringResource(R.string.power_action_reboot),
                        onClick = {
                            showPowerDialog = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, context.getString(R.string.toast_rebooting_device), Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                RootUtil.rebootDevice("")
                            }
                        }
                    )

                    PowerMenuItem(
                        icon = Icons.Rounded.Memory,
                        iconBg = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        title = stringResource(R.string.power_action_recovery),
                        onClick = {
                            showPowerDialog = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, context.getString(R.string.toast_rebooting_recovery), Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                RootUtil.rebootDevice("recovery")
                            }
                        }
                    )

                    PowerMenuItem(
                        icon = Icons.Rounded.PowerSettingsNew,
                        iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
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
            shape = RoundedCornerShape(currentRadius.coerceAtLeast(22.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    val isFloating = LocalFloatingNavBar.current
    val bottomPadding = if (isFloating) {
        108.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    CoreStatusIndicator(
                        isChecking = isChecking,
                        isRestarting = isRestarting,
                        isRunning = isRunning,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showPowerDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hero") {
                CoreStatusCard(
                    isChecking = isChecking,
                    isRestarting = isRestarting,
                    isRunning = isRunning,
                    isModuleInstalled = isModuleInstalled,
                    isRebootRequired = isRebootRequired,
                    daemonPid = daemonPid,
                    isIpcAlive = isIpcAlive,
                    socLabel = socLabel,
                    isBinderReady = isBinderReady,
                    onStart = {
                        if (!isChecking && !isRunning && !isRestarting) {
                            coroutineScope.launch {
                                if (isRebootRequired || DaemonManager.isRebootRequired()) {
                                    onRequestRebootDialog()
                                } else if (isModuleInstalled || DaemonManager.isModuleInstalled()) {
                                    launchRestart()
                                }
                            }
                        }
                    }
                )
            }

            item(key = "metrics") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardMetricCard(
                        icon = Icons.Rounded.Thermostat,
                        label = stringResource(R.string.home_thermal),
                        value = batteryTemp,
                        supporting = null,
                        badgeBg = MaterialTheme.colorScheme.tertiaryContainer,
                        badgeTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardMetricCard(
                        icon = Icons.Rounded.Speed,
                        label = stringResource(R.string.home_profile),
                        value = currentProfile,
                        supporting = currentFps.takeIf { it != "-" }?.let { "$it FPS" },
                        badgeBg = MaterialTheme.colorScheme.primary,
                        badgeTint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "quick") {
                QuickActionRow(
                    onPersonalize = onNavigateToPersonalisasi
                )
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

                HomeNestedCardGroup(
                    headerIcon = Icons.Rounded.Smartphone,
                    headerTitle = homeLocalizedLabel("Informasi Perangkat", "Device Information"),
                    onHeaderClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isDeviceExpanded = !isDeviceExpanded
                    },
                    headerTrailing = {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isDeviceExpanded = !isDeviceExpanded
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = if (isDeviceExpanded) {
                                        homeLocalizedLabel("Sembunyikan", "Collapse")
                                    } else {
                                        homeLocalizedLabel("Tampilkan lainnya", "Expand")
                                    },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(arrowRotation)
                                )
                            }
                        }
                    }
                ) {
                    HomeNestedItemCard(
                        icon = Icons.Rounded.DeveloperBoard,
                        title = stringResource(R.string.home_manager_version),
                        subtitle = stringResource(
                            R.string.home_version_release_format,
                            com.luminatweaks.BuildConfig.VERSION_NAME.removeSuffix(".0")
                        )
                    )
                    HomeNestedItemCard(
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.home_kernel_version),
                        subtitle = kernelVersion
                    )
                    HomeNestedItemCard(
                        icon = Icons.Rounded.Smartphone,
                        title = stringResource(R.string.home_device_model),
                        subtitle = deviceModel
                    )

                    AnimatedVisibility(
                        visible = isDeviceExpanded,
                        enter = expandVertically(animationSpec = spatialSpec) + fadeIn(animationSpec = effectsSpec),
                        exit = shrinkVertically(animationSpec = spatialSpec) + fadeOut(animationSpec = effectsSpec)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeNestedItemCard(
                                icon = Icons.Rounded.Security,
                                title = stringResource(R.string.home_root_access),
                                subtitle = stringResource(
                                    if (isRootGranted) R.string.home_root_granted else R.string.home_root_denied
                                ),
                                subtitleColor = if (isRootGranted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                            HomeNestedItemCard(
                                icon = Icons.Rounded.Security,
                                title = stringResource(R.string.home_selinux_status),
                                subtitle = selinuxStatus
                            )
                        }
                    }
                }
            }

            item(key = "support") {
                val uriHandler = LocalUriHandler.current
                HomeNestedCardGroup(
                    headerIcon = Icons.Rounded.Favorite,
                    headerTitle = homeLocalizedLabel("Dukungan", "Support")
                ) {
                    HomeNestedItemCard(
                        icon = Icons.Rounded.Favorite,
                        title = stringResource(R.string.home_support_us),
                        subtitle = stringResource(R.string.home_support_us_desc),
                        onClick = { uriHandler.openUri("https://sociabuzz.com/kimal404") }
                    )
                    HomeNestedItemCard(
                        icon = Icons.Rounded.MenuBook,
                        title = stringResource(R.string.home_learn_more),
                        subtitle = stringResource(R.string.home_learn_more_desc),
                        onClick = { uriHandler.openUri("https://github.com/kimal404/LuminaTweaks") }
                    )
                }
            }

            item(key = "footer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
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

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .padding(end = 6.dp)
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
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                )
            }
        }
    }
}

@Composable
private fun CoreStatusCard(
    isChecking: Boolean,
    isRestarting: Boolean,
    isRunning: Boolean,
    isModuleInstalled: Boolean,
    isRebootRequired: Boolean,
    daemonPid: String?,
    isIpcAlive: Boolean,
    socLabel: String?,
    isBinderReady: Boolean,
    onStart: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.surfaceContainerHigh
            isRunning -> MaterialTheme.colorScheme.primaryContainer
            isRebootRequired -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "coreContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.onSurface
            isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
            isRebootRequired -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onErrorContainer
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
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coreWave"
    )
    val morphProgress by animateFloatAsState(
        targetValue = if (isAlive) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "coreMorph"
    )

    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)

    Card(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(outerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isAlive) {
                LiveWave(
                    phase = wavePhase,
                    color = contentColor,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                )
            }

            Column(
                modifier = Modifier.padding(
                    start = 22.dp,
                    top = 22.dp,
                    end = 22.dp,
                    bottom = if (isAlive) 34.dp else 22.dp
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(contentColor.copy(alpha = 0.14f), MorphShape(iconMorph, morphProgress)),
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
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isAlive) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoreInfoPill(
                            icon = Icons.Rounded.Memory,
                            label = stringResource(R.string.home_core_info_pid),
                            value = daemonPid?.takeIf { pid -> pid.all(Char::isDigit) } ?: "-",
                            contentColor = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                        CoreInfoPill(
                            icon = Icons.Rounded.Link,
                            label = stringResource(R.string.home_core_info_ipc),
                            value = if (isIpcAlive) {
                                stringResource(R.string.home_core_info_socket)
                            } else {
                                stringResource(R.string.home_core_info_offline)
                            },
                            contentColor = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoreInfoPill(
                            icon = Icons.Rounded.DeveloperBoard,
                            label = stringResource(R.string.home_core_info_chipset),
                            value = socLabel ?: stringResource(R.string.home_core_info_unknown),
                            contentColor = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                        CoreInfoPill(
                            icon = Icons.Rounded.Security,
                            label = stringResource(R.string.home_core_info_binder),
                            value = if (isBinderReady) {
                                stringResource(R.string.home_core_info_ready)
                            } else {
                                stringResource(R.string.home_core_info_unavailable)
                            },
                            contentColor = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreInfoPill(
    icon: ImageVector,
    label: String,
    value: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = contentColor.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.82f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

        val path = Path()
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
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun DashboardMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String?,
    badgeBg: Color,
    badgeTint: Color,
    modifier: Modifier = Modifier
) {
    val cardRadius = LocalCornerRadius.current.coerceAtLeast(24.dp)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!supporting.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToggleButton(
            checked = false,
            onCheckedChange = { runTrim() },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            enabled = !isTrimming,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoFixHigh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_quick_trim),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
        ToggleButton(
            checked = false,
            onCheckedChange = { runCache() },
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            enabled = !isClearing,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.CleaningServices,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_quick_cache),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
        ToggleButton(
            checked = false,
            onCheckedChange = { onPersonalize() },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Palette,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_quick_personalize),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Nested Bento Card Container & Inner Items (MD3E)
// ---------------------------------------------------------------------------

@Composable
private fun HomeNestedCardGroup(
    headerIcon: ImageVector,
    headerTitle: String,
    modifier: Modifier = Modifier,
    onHeaderClick: (() -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)

    Surface(
        shape = RoundedCornerShape(outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .then(
                        if (onHeaderClick != null) {
                            Modifier.clickable(onClick = onHeaderClick)
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (headerTrailing != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    headerTrailing()
                }
            }

            content()
        }
    }
}

@Composable
private fun HomeNestedItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "homeNestedItemScale"
    )
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onClick != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            shape = RoundedCornerShape(innerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            content = rowContent
        )
    } else {
        Surface(
            shape = RoundedCornerShape(innerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
            content = rowContent
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
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = getConnectedContainerShape(position),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
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
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = getConnectedContainerShape(position),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PowerMenuItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private class MorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress = progress, path = android.graphics.Path())
        path.transform(
            android.graphics.Matrix().apply {
                setScale(size.width, size.height)
            }
        )
        return Outline.Generic(path.asComposePath())
    }
}
