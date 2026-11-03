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

package com.luminatweaks.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentSubscreen by remember { mutableStateOf<String?>(null) }
    val kernelVersion = remember { System.getProperty("os.version") ?: "Unknown" }
    val deviceModel = remember { DeviceUtil.getMarketDeviceName(context) }

    var batteryTemp by remember { mutableStateOf(DeviceUtil.getBatteryTemperature(context)) }
    var isChecking by remember { mutableStateOf(true) }
    var daemonPid by remember { mutableStateOf<String?>(null) }
    var currentProfile by remember { mutableStateOf("Balanced") }

    LaunchedEffect(Unit) {
        DaemonManager.startDaemon(context)

        withContext(Dispatchers.IO) {
            val initialTemp = DeviceUtil.getBatteryTemperature(context)
            val initialPid = DaemonManager.getDaemonPid()
            val initialProfile = if (initialPid != null) ConfigRepository.getCurrentProfile() else "-"

            withContext(Dispatchers.Main) {
                batteryTemp = initialTemp
                daemonPid = initialPid
                currentProfile = initialProfile
                isChecking = false
            }

            while (isActive) {
                delay(1500L)
                val currentTemp = DeviceUtil.getBatteryTemperature(context)
                val currentPid = DaemonManager.getDaemonPid()
                val profileText = if (currentPid != null) ConfigRepository.getCurrentProfile() else "-"

                withContext(Dispatchers.Main) {
                    batteryTemp = currentTemp
                    daemonPid = currentPid
                    currentProfile = profileText
                }
            }
        }
    }

    val isFloating = LocalFloatingNavBar.current

    if (currentSubscreen == "personalisasi") {
        PersonalisasiScreen(onBackClick = { currentSubscreen = null })
        return
    }

    if (isFloating) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState >= initialState) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }

                    (slideIntoContainer(
                        towards = direction,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )).togetherWith(
                        slideOutOfContainer(
                            towards = direction,
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        )
                    )
                },
                modifier = Modifier.fillMaxSize(),
                label = "ScreenTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> HomeDashboardContent(
                        batteryTemp = batteryTemp,
                        daemonPid = daemonPid,
                        isChecking = isChecking,
                        currentProfile = currentProfile,
                        kernelVersion = kernelVersion,
                        deviceModel = deviceModel,
                        onRestartDaemon = {
                            daemonPid = null
                            currentProfile = "-"
                            DaemonManager.restartDaemon(context)
                        }
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
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState >= initialState) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }

                    (slideIntoContainer(
                        towards = direction,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )).togetherWith(
                        slideOutOfContainer(
                            towards = direction,
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                label = "ScreenTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> HomeDashboardContent(
                        batteryTemp = batteryTemp,
                        daemonPid = daemonPid,
                        isChecking = isChecking,
                        currentProfile = currentProfile,
                        kernelVersion = kernelVersion,
                        deviceModel = deviceModel,
                        onRestartDaemon = {
                            daemonPid = null
                            currentProfile = "-"
                            DaemonManager.restartDaemon(context)
                        }
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
    currentProfile: String,
    kernelVersion: String,
    deviceModel: String,
    onRestartDaemon: suspend () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val coroutineScope = rememberCoroutineScope()
    var isRestarting by remember { mutableStateOf(false) }

    val isRunning = daemonPid != null

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "LuminaTweaks",
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
                            .size(44.dp)
                            .clip(CircleShape)
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
                            tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
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
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
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
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = if (LocalFloatingNavBar.current) 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                val bannerToneColor = when {
                    isChecking -> MaterialTheme.colorScheme.surfaceContainerHighest
                    isRunning -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
                val bannerContentColor = when {
                    isChecking -> MaterialTheme.colorScheme.onSurfaceVariant
                    isRunning -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }
                val bannerTitle = when {
                    isChecking -> stringResource(R.string.home_core_checking)
                    isRestarting -> stringResource(R.string.home_core_starting)
                    isRunning -> "Alive"
                    else -> stringResource(R.string.home_core_inactive)
                }
                val bannerSubtitle = when {
                    isChecking -> stringResource(R.string.home_core_scanning)
                    isRunning -> "PID: ${daemonPid ?: "-"}"
                    else -> stringResource(R.string.home_core_tap_start)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isChecking && !isRunning && !isRestarting) {
                                isRestarting = true
                                coroutineScope.launch {
                                    onRestartDaemon()
                                    isRestarting = false
                                }
                            }
                        },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = bannerToneColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .border(2.5.dp, bannerContentColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = bannerContentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else if (isChecking) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = bannerContentColor,
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = bannerContentColor,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .width(1.5.dp)
                                .background(bannerContentColor.copy(alpha = 0.35f))
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bannerTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = bannerContentColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bannerSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                color = bannerContentColor.copy(alpha = 0.85f)
                            )
                        }

                        if (!isRunning && !isChecking) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.action_start),
                                tint = bannerContentColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Thermostat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.home_thermal),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = batteryTemp,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.home_profile),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = currentProfile,
                                style = when {
                                    currentProfile.length > 12 -> MaterialTheme.typography.titleSmall
                                    currentProfile.length > 8 -> MaterialTheme.typography.titleMedium
                                    else -> MaterialTheme.typography.titleLarge
                                },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
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
                        subtitle = "v2.0.0 (Remastered)",
                        position = ContainerPosition.Top
                    )
                    ConnectedCardRow(
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.home_kernel_version),
                        subtitle = kernelVersion,
                        position = ContainerPosition.Middle
                    )
                    ConnectedCardRow(
                        icon = Icons.Rounded.Smartphone,
                        title = stringResource(R.string.home_device_model),
                        subtitle = deviceModel,
                        position = ContainerPosition.Bottom
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
                        subtitle = stringResource(R.string.home_root_granted),
                        position = ContainerPosition.Top
                    )
                    ConnectedCardRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.home_selinux_status),
                        subtitle = "Enforcing",
                        position = ContainerPosition.Bottom
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ConnectedActionCardRow(
                        icon = Icons.Rounded.Favorite,
                        title = stringResource(R.string.home_support_us),
                        subtitle = stringResource(R.string.home_support_us_desc),
                        position = ContainerPosition.Top,
                        onClick = { }
                    )
                    ConnectedActionCardRow(
                        icon = Icons.Rounded.MenuBook,
                        title = stringResource(R.string.home_learn_more),
                        subtitle = stringResource(R.string.home_learn_more_desc),
                        position = ContainerPosition.Bottom,
                        onClick = { }
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
fun ConnectedCardRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: ContainerPosition,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = getConnectedContainerShape(position),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = getConnectedContainerShape(position),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
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
                .padding(horizontal = 14.dp, vertical = 14.dp),
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
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
