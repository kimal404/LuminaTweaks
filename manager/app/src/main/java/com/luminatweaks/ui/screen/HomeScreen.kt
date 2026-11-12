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

package com.luminatweaks.ui.screen

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.luminatweaks.R
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.ui.component.LuminaBottomBar
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
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
    var showRebootDialog by remember { mutableStateOf(false) }
    var isScreenResumed by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> isScreenResumed = true
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
        }
    }

    LaunchedEffect(selectedTab, isScreenResumed, currentSubscreen) {
        if (selectedTab != 0 || !isScreenResumed || currentSubscreen != null) {
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val refreshedRoot = RootUtil.checkRootAccess()
            val refreshedSelinux = DeviceUtil.getSelinuxStatus()
            withContext(Dispatchers.Main) {
                isRootGranted = refreshedRoot
                selinuxStatus = refreshedSelinux
            }

            while (isActive) {
                val currentTemp = DeviceUtil.getBatteryTemperature(context)
                val propProfile = readSystemProperty("sys.lumina.profile")
                val status = DaemonManager.getStatus()
                val rebootNeeded = if (status.pid == null) DaemonManager.isRebootRequired() else false
                val installed = if (status.pid == null) DaemonManager.isModuleInstalled() else true

                withContext(Dispatchers.Main) {
                    batteryTemp = currentTemp
                    daemonPid = status.pid
                    isRebootRequired = rebootNeeded
                    isModuleInstalled = installed
                    currentProfile = propProfile ?: status.profile
                    currentFps = status.fps
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

    if (currentSubscreen == "personalisasi") {
        PersonalisasiScreen(onBackClick = { currentSubscreen = null })
        return
    }

    val restartAction: suspend () -> Unit = {
        daemonPid = null
        currentProfile = "-"
        DaemonManager.restartDaemon(context)
        val updated = DaemonManager.getStatus()
        daemonPid = updated.pid
        currentProfile = readSystemProperty("sys.lumina.profile") ?: updated.profile
        currentFps = updated.fps
        if (updated.pid == null) {
            isRebootRequired = DaemonManager.isRebootRequired()
            isModuleInstalled = DaemonManager.isModuleInstalled()
        }
    }

    if (isFloating) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when (selectedTab) {
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
                        onRequestRebootDialog = { showRebootDialog = true },
                        onRestartDaemon = restartAction
                    )
                    1 -> AppsScreen()
                    2 -> TweaksScreen()
                    3 -> SettingsScreen(onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" })
                }
            }

            if (currentSubscreen == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    LuminaBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            bottomBar = {
                if (currentSubscreen == null) {
                    LuminaBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                when (selectedTab) {
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
                        onRequestRebootDialog = { showRebootDialog = true },
                        onRestartDaemon = restartAction
                    )
                    1 -> AppsScreen()
                    2 -> TweaksScreen()
                    3 -> SettingsScreen(onNavigateToPersonalisasi = { currentSubscreen = "personalisasi" })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onRequestRebootDialog: () -> Unit,
    onRestartDaemon: suspend () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isRestarting by remember { mutableStateOf(false) }
    val isRunning = daemonPid != null

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    val context = LocalContext.current
                    val haptic = LocalHapticFeedback.current
                    var showPowerDialog by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isChecking -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    isRunning -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.errorContainer
                                }
                            )
                            .combinedClickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showPowerDialog = true
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!isRestarting) {
                                        isRestarting = true
                                        Toast.makeText(context, context.getString(R.string.toast_restarting_service), Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            onRestartDaemon()
                                            isRestarting = false
                                        }
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = stringResource(R.string.power_menu_title),
                            tint = when {
                                isChecking -> MaterialTheme.colorScheme.onSurfaceVariant
                                isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(22.dp)
                        )
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
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
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
                                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        title = stringResource(R.string.power_action_reboot),
                                        cornerRadius = currentRadius,
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
                                        cornerRadius = currentRadius,
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
                                        cornerRadius = currentRadius,
                                        onClick = {
                                            showPowerDialog = false
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (!isRestarting) {
                                                isRestarting = true
                                                Toast.makeText(context, context.getString(R.string.toast_restarting_service), Toast.LENGTH_SHORT).show()
                                                coroutineScope.launch {
                                                    onRestartDaemon()
                                                    isRestarting = false
                                                }
                                            }
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
                            shape = RoundedCornerShape(currentRadius.coerceAtLeast(16.dp)),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
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
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = if (LocalFloatingNavBar.current) 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CoreStatusCard(
                    isChecking = isChecking,
                    isRestarting = isRestarting,
                    isRunning = isRunning,
                    isModuleInstalled = isModuleInstalled,
                    isRebootRequired = isRebootRequired,
                    daemonPid = daemonPid,
                    onStart = {
                        if (!isChecking && !isRunning && !isRestarting) {
                            coroutineScope.launch {
                                if (isRebootRequired || DaemonManager.isRebootRequired()) {
                                    onRequestRebootDialog()
                                } else if (isModuleInstalled || DaemonManager.isModuleInstalled()) {
                                    isRestarting = true
                                    onRestartDaemon()
                                    isRestarting = false
                                }
                            }
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardMetricCard(
                        icon = Icons.Rounded.Thermostat,
                        label = stringResource(R.string.home_thermal),
                        value = batteryTemp,
                        supporting = null,
                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    DashboardMetricCard(
                        icon = Icons.Rounded.Speed,
                        label = stringResource(R.string.home_profile),
                        value = currentProfile,
                        supporting = currentFps.takeIf { it != "-" }?.let { "$it FPS" },
                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ConnectedCardRow(
                        icon = Icons.Rounded.DeveloperBoard,
                        title = stringResource(R.string.home_manager_version),
                        subtitle = stringResource(
                            R.string.home_version_release_format,
                            com.luminatweaks.BuildConfig.VERSION_NAME.removeSuffix(".0")
                        ),
                        position = ContainerPosition.Top
                    )
                    ConnectedCardRow(
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.home_kernel_version),
                        subtitle = kernelVersion,
                        position = ContainerPosition.Middle,
                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    ConnectedCardRow(
                        icon = Icons.Rounded.Smartphone,
                        title = stringResource(R.string.home_device_model),
                        subtitle = deviceModel,
                        position = ContainerPosition.Bottom,
                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ConnectedCardRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.home_root_access),
                        subtitle = stringResource(
                            if (isRootGranted) R.string.home_root_granted else R.string.home_root_denied
                        ),
                        position = ContainerPosition.Top,
                        iconContainer = if (isRootGranted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        iconTint = if (isRootGranted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    ConnectedCardRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.home_selinux_status),
                        subtitle = selinuxStatus,
                        position = ContainerPosition.Bottom,
                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            item {
                val uriHandler = LocalUriHandler.current
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ConnectedActionCardRow(
                        icon = Icons.Rounded.Favorite,
                        title = stringResource(R.string.home_support_us),
                        subtitle = stringResource(R.string.home_support_us_desc),
                        position = ContainerPosition.Top,
                        onClick = { uriHandler.openUri("https://sociabuzz.com/kimal404") },
                        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    ConnectedActionCardRow(
                        icon = Icons.Rounded.MenuBook,
                        title = stringResource(R.string.home_learn_more),
                        subtitle = stringResource(R.string.home_learn_more_desc),
                        position = ContainerPosition.Bottom,
                        onClick = { uriHandler.openUri("https://github.com/kimal404/LuminaTweaks") },
                        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
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
    onStart: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.surfaceContainerHighest
            isRunning -> MaterialTheme.colorScheme.primaryContainer
            isRebootRequired -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        },
        label = "coreContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isChecking || isRestarting -> MaterialTheme.colorScheme.onSurfaceVariant
            isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
            isRebootRequired -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onErrorContainer
        },
        label = "coreContent"
    )
    val canStart = !isChecking && !isRunning && !isRestarting && (isModuleInstalled || isRebootRequired)
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
        isRunning -> stringResource(R.string.home_core_pid_format, daemonPid ?: "-")
        isRebootRequired -> stringResource(R.string.home_core_reboot_hint)
        !isModuleInstalled -> stringResource(R.string.home_core_install_hint)
        else -> stringResource(R.string.home_core_tap_start)
    }
    val isAlive = isRunning && !isRestarting

    val aliveTransition = rememberInfiniteTransition(label = "coreAlive")
    val pulse by aliveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )
    val wavePhase by aliveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coreWave"
    )

    Card(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = contentColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(containerColor, containerColor.copy(alpha = 0.74f))
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = (-38).dp)
                    .size(140.dp)
                    .background(contentColor.copy(alpha = 0.06f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-44).dp, y = 40.dp)
                    .size(104.dp)
                    .background(contentColor.copy(alpha = 0.04f), CircleShape)
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAlive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = 0.08f + pulse * 0.12f
                                        scaleX = 0.82f + pulse * 0.18f
                                        scaleY = 0.82f + pulse * 0.18f
                                    }
                                    .background(contentColor, CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(contentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isChecking || isRestarting -> CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    color = contentColor,
                                    strokeWidth = 2.5.dp
                                )
                                isRunning -> Icon(
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
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
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

                    if (canStart) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = CircleShape, color = contentColor) {
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isRebootRequired) {
                                        Icons.Rounded.Refresh
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = stringResource(R.string.action_start),
                                    tint = containerColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                if (isAlive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LiveWave(
                        phase = wavePhase,
                        color = contentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                }
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
        val amplitude = size.height * 0.34f
        val cycles = 2.2f
        val step = size.width / 72f
        var x = 0f

        while (x <= size.width) {
            val envelope = sin((x / size.width) * PI.toFloat())
            val angle = (x / size.width) * cycles * 2f * PI.toFloat() +
                phase * 2f * PI.toFloat()
            val y = size.height / 2f + sin(angle) * amplitude * envelope
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += step
        }

        drawPath(
            path = path,
            color = color.copy(alpha = 0.45f),
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
    iconContainer: Color,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(LocalCornerRadius.current),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(iconContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!supporting.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = getConnectedContainerShape(position),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                    .size(38.dp)
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
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(1.dp))
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                    .size(38.dp)
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
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PowerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    cornerRadius: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val itemRadius = (cornerRadius * 0.75f).coerceIn(8.dp, 20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(itemRadius))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(itemRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBg),
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
