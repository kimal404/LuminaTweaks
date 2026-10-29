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
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.ui.component.LuminaBottomBar
import com.luminatweaks.util.DeviceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ContainerPosition {
    Top, Middle, Bottom, Single
}

fun getConnectedContainerShape(
    position: ContainerPosition,
    outerRadius: Dp = 18.dp,
    innerRadius: Dp = 4.dp
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
    val kernelVersion = remember { System.getProperty("os.version") ?: "Unknown" }
    val deviceModel = remember { DeviceUtil.getMarketDeviceName(context) }

    var batteryTemp by remember { mutableStateOf(DeviceUtil.getBatteryTemperature(context)) }
    var daemonPid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        DaemonManager.startDaemon(context)

        withContext(Dispatchers.IO) {
            while (isActive) {
                val currentTemp = DeviceUtil.getBatteryTemperature(context)
                val currentPid = DaemonManager.getDaemonPid()
                withContext(Dispatchers.Main) {
                    batteryTemp = currentTemp
                    daemonPid = currentPid
                }
                delay(2000L)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            LuminaBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
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
                    kernelVersion = kernelVersion,
                    deviceModel = deviceModel,
                    onRestartDaemon = {
                        daemonPid = null
                        DaemonManager.restartDaemon(context)
                    }
                )
                1 -> AppsScreen()
                2 -> TweaksScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardContent(
    batteryTemp: String,
    daemonPid: String?,
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
                    IconButton(
                        onClick = {
                            if (!isRestarting) {
                                isRestarting = true
                                coroutineScope.launch {
                                    onRestartDaemon()
                                    isRestarting = false
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = "Restart Core",
                            tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isRunning && !isRestarting) {
                                isRestarting = true
                                coroutineScope.launch {
                                    onRestartDaemon()
                                    isRestarting = false
                                }
                            }
                        },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRunning) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = if (isRunning) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRunning) "Working" else if (isRestarting) "Starting Core..." else "Core Inactive",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRunning) "PID: $daemonPid (Active background)" else "Tap to start or restart daemon",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRunning) {
                                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                }
                            )
                        }

                        if (!isRunning) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Start",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
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
                                text = "Balanced",
                                style = MaterialTheme.typography.titleLarge,
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
