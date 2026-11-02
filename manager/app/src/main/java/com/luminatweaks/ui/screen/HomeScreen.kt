package com.luminatweaks.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luminatweaks.data.ConfigRepository
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.luminatweaks.util.RootUtil
import com.luminatweaks.data.DaemonManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.ui.component.LuminaBottomBar
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.util.DeviceUtil
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
            // Buffer check pertama (Inisialisasi awal ala AZenith)
            val initialTemp = DeviceUtil.getBatteryTemperature(context)
            val initialPid = DaemonManager.getDaemonPid()
            val initialProfile = if (initialPid != null) ConfigRepository.getCurrentProfile() else "-"

            withContext(Dispatchers.Main) {
                batteryTemp = initialTemp
                daemonPid = initialPid
                currentProfile = initialProfile
                isChecking = false // Buffer check selesai
            }

            // Loop monitoring berkelanjutan
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
                                        Toast.makeText(context, "Memulai ulang layanan...", Toast.LENGTH_SHORT).show()
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
                            contentDescription = "Menu Daya",
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
                                    text = "Menu Daya",
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
                                        title = "Mulai Ulang",
                                                                                cornerRadius = currentRadius,
                                        onClick = {
                                            showPowerDialog = false
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            Toast.makeText(context, "Memulai ulang perangkat...", Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch {
                                                RootUtil.rebootDevice("")
                                            }
                                        }
                                    )

                                    PowerMenuItem(
                                        icon = Icons.Rounded.Memory,
                                        iconBg = MaterialTheme.colorScheme.secondaryContainer,
                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        title = "Mode Recovery",
                                                                                cornerRadius = currentRadius,
                                        onClick = {
                                            showPowerDialog = false
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            Toast.makeText(context, "Memulai ulang ke Recovery...", Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch {
                                                RootUtil.rebootDevice("recovery")
                                            }
                                        }
                                    )

                                    PowerMenuItem(
                                        icon = Icons.Rounded.PowerSettingsNew,
                                        iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        title = "Restart Service",
                                                                                cornerRadius = currentRadius,
                                        onClick = {
                                            showPowerDialog = false
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (!isRestarting) {
                                                isRestarting = true
                                                Toast.makeText(context, "Memulai ulang layanan...", Toast.LENGTH_SHORT).show()
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
                                        text = "Batal",
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
                    isRunning -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
                val bannerContentColor = when {
                    isChecking -> MaterialTheme.colorScheme.onSurfaceVariant
                    isRunning -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }
                val bannerIcon = when {
                    isChecking -> Icons.Rounded.Memory
                    isRunning -> Icons.Rounded.CheckCircle
                    else -> Icons.Rounded.ErrorOutline
                }
                val bannerTitle = when {
                    isChecking -> "Checking Core..."
                    isRestarting -> "Starting Core..."
                    isRunning -> "Working"
                    else -> "Core Inactive"
                }
                val bannerSubtitle = when {
                    isChecking -> "Scanning daemon process..."
                    isRunning -> "PID: $daemonPid (Active background)"
                    else -> "Tap to start or restart daemon"
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
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = bannerToneColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = bannerIcon,
                            contentDescription = null,
                            tint = bannerContentColor,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bannerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = bannerContentColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bannerSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = bannerContentColor.copy(alpha = 0.8f)
                            )
                        }

                        if (!isRunning && !isChecking) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Start",
                                tint = bannerContentColor,
                                modifier = Modifier.size(24.dp)
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
                                    text = "Thermal",
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
                                    text = "Profile",
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
                        title = "Manager Version",
                        subtitle = "v2.0.0 (Remastered)",
                        position = ContainerPosition.Top
                    )
                    ConnectedCardRow(
                        icon = Icons.Rounded.Memory,
                        title = "Kernel Version",
                        subtitle = kernelVersion,
                        position = ContainerPosition.Middle
                    )
                    ConnectedCardRow(
                        icon = Icons.Rounded.Smartphone,
                        title = "Device Model",
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
                        title = "Root Access",
                        subtitle = "Granted (Superuser)",
                        position = ContainerPosition.Top
                    )
                    ConnectedCardRow(
                        icon = Icons.Rounded.Security,
                        title = "SELinux Status",
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
                        title = "Support Us",
                        subtitle = "LuminaTweaks is free and open source forever. Support development through donations.",
                        position = ContainerPosition.Top,
                        onClick = { }
                    )
                    ConnectedActionCardRow(
                        icon = Icons.Rounded.MenuBook,
                        title = "Learn LuminaTweaks",
                        subtitle = "Explore documentation, kernel tweak parameters, and optimization guides.",
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
