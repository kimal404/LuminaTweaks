package com.luminatweaks.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.luminatweaks.data.GameBarQuickApp
import com.luminatweaks.data.GameBarSide
import com.luminatweaks.data.GameBarUiState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class GameBarProfile(
    val value: String,
    val label: String
)

private val gameBarProfiles = listOf(
    GameBarProfile("Powersave", "Powersave"),
    GameBarProfile("Balanced", "Balanced"),
    GameBarProfile("Maestro", "Maestro")
)

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun GameBarRoot(
    state: State<GameBarUiState>,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onHandleDrag: (Float) -> Unit,
    onProfileSelected: (String) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onBrightnessChanged: (Int) -> Unit,
    onQuickAppClick: (String) -> Unit,
    onToggleFpsOverlay: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleAutoProfile: () -> Unit,
    onOpenManager: () -> Unit
) {
    val current by state
    AnimatedContent(
        targetState = current.expanded,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
        modifier = Modifier.fillMaxWidth(),
        label = "gameBarMode"
    ) { expanded ->
        if (expanded) {
            GameBarPanel(
                state = current,
                onCollapse = onCollapse,
                onProfileSelected = onProfileSelected,
                onVolumeChanged = onVolumeChanged,
                onBrightnessChanged = onBrightnessChanged,
                onQuickAppClick = onQuickAppClick,
                onToggleFpsOverlay = onToggleFpsOverlay,
                onToggleDnd = onToggleDnd,
                onToggleAutoProfile = onToggleAutoProfile,
                onOpenManager = onOpenManager
            )
        } else {
            GameBarHandle(
                state = current,
                onExpand = onExpand,
                onHandleDrag = onHandleDrag
            )
        }
    }
}

@Composable
private fun GameBarHandle(
    state: GameBarUiState,
    onExpand: () -> Unit,
    onHandleDrag: (Float) -> Unit
) {
    val isLeft = state.side == GameBarSide.LEFT
    var touchTick by remember { mutableStateOf(0) }
    var dimmed by remember { mutableStateOf(false) }
    val swipeThreshold = with(LocalDensity.current) { 18.dp.toPx() }

    LaunchedEffect(touchTick, state.autoDimHandle) {
        dimmed = false
        if (state.autoDimHandle) {
            delay(2500L)
            dimmed = true
        }
    }

    val handleAlpha by animateFloatAsState(
        targetValue = if (dimmed) {
            state.handleOpacity.coerceAtMost(0.16f)
        } else {
            state.handleOpacity.coerceIn(0.10f, 0.80f)
        },
        animationSpec = tween(600),
        label = "gameBarHandleAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        touchTick++
                        tryAwaitRelease()
                    },
                    onTap = { onExpand() }
                )
            }
            .pointerInput(isLeft, swipeThreshold) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        touchTick++
                        totalX = 0f
                        totalY = 0f
                    },
                    onDragEnd = {
                        val inward = if (isLeft) totalX else -totalX
                        if (abs(totalX) > abs(totalY) && inward > swipeThreshold) {
                            onExpand()
                        }
                    },
                    onDragCancel = {
                        totalX = 0f
                        totalY = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    totalX += dragAmount.x
                    totalY += dragAmount.y
                    if (abs(totalY) >= abs(totalX)) {
                        onHandleDrag(dragAmount.y)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .width(4.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = handleAlpha))
        )
    }
}

@Composable
private fun GameBarPanel(
    state: GameBarUiState,
    onCollapse: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onBrightnessChanged: (Int) -> Unit,
    onQuickAppClick: (String) -> Unit,
    onToggleFpsOverlay: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleAutoProfile: () -> Unit,
    onOpenManager: () -> Unit
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 28.dp).coerceAtLeast(220.dp)
    var quickAppsOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = state.opacity.coerceIn(0.40f, 1f)
        ),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GameBarHeader(
                state = state,
                onCollapse = onCollapse,
                onToggleFpsOverlay = onToggleFpsOverlay
            )
            GameBarTelemetry(state)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                gameBarProfiles.forEach { profile ->
                    GameBarProfilePill(
                        label = profile.label,
                        selected = profileMatches(state.profile, profile.value),
                        modifier = Modifier.weight(1f),
                        onClick = { onProfileSelected(profile.value) }
                    )
                }
            }
            GameBarSlider(
                icon = Icons.Rounded.VolumeUp,
                value = state.volumePercent,
                onValueChange = onVolumeChanged
            )
            GameBarSlider(
                icon = Icons.Rounded.Brightness6,
                value = state.brightnessPercent,
                onValueChange = onBrightnessChanged
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GameBarAction(
                    icon = Icons.Rounded.Speed,
                    label = "FPS Meter",
                    active = state.fpsOverlay,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleFpsOverlay
                )
                GameBarAction(
                    icon = Icons.Rounded.NotificationsOff,
                    label = "Jangan Ganggu",
                    active = state.dndEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleDnd
                )
                GameBarAction(
                    icon = Icons.Rounded.Autorenew,
                    label = "Mode Otomatis",
                    active = state.autoProfile,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleAutoProfile
                )
                GameBarAction(
                    icon = Icons.Rounded.Apps,
                    label = "Quick Apps",
                    active = quickAppsOpen,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when {
                            state.quickApps.isEmpty() -> onOpenManager()
                            state.quickApps.size == 1 -> onQuickAppClick(
                                state.quickApps.first().packageName
                            )
                            else -> quickAppsOpen = !quickAppsOpen
                        }
                    }
                )
            }
            AnimatedVisibility(
                visible = quickAppsOpen && state.quickApps.isNotEmpty(),
                enter = fadeIn(tween(160)) + expandVertically(tween(160)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(100))
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.quickApps.chunked(4).forEach { rowApps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowApps.forEach { app ->
                                    QuickAppTile(
                                        app = app,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            quickAppsOpen = false
                                            onQuickAppClick(app.packageName)
                                        }
                                    )
                                }
                                repeat(4 - rowApps.size) {
                                    Spacer(modifier = Modifier.weight(1f))
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
private fun GameBarHeader(
    state: GameBarUiState,
    onCollapse: () -> Unit,
    onToggleFpsOverlay: () -> Unit
) {
    val clock by produceState(initialValue = LocalTime.now().format(clockFormatter)) {
        while (true) {
            value = LocalTime.now().format(clockFormatter)
            delay(1000L)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = clock,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = if (state.autoProfile) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
        ) {
            Text(
                text = if (state.autoProfile) "AUTO" else "MANUAL",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.autoProfile) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        HeaderButton(
            icon = Icons.Rounded.Speed,
            active = state.fpsOverlay,
            description = "FPS overlay",
            onClick = onToggleFpsOverlay
        )
        Spacer(modifier = Modifier.width(4.dp))
        HeaderButton(
            icon = Icons.Rounded.Close,
            active = false,
            description = "Tutup Game Bar",
            onClick = onCollapse
        )
    }
}

@Composable
private fun HeaderButton(
    icon: ImageVector,
    active: Boolean,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (active) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun GameBarTelemetry(state: GameBarUiState) {
    val fpsText = if (state.fps > 0) {
        "${state.fps} FPS · ${state.targetHz}Hz"
    } else {
        "-- FPS · ${state.targetHz}Hz"
    }
    val cpuGpu = listOfNotNull(
        state.cpuPercent?.let { "C$it%" },
        state.gpuPercent?.let { "G$it%" }
    ).joinToString(" ")
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TelemetryItem(
                icon = Icons.Rounded.Speed,
                text = fpsText,
                modifier = Modifier.weight(1.4f)
            )
            TelemetryItem(
                icon = Icons.Rounded.Thermostat,
                text = state.tempLabel,
                modifier = Modifier.weight(1f)
            )
            TelemetryItem(
                icon = Icons.Rounded.BatteryFull,
                text = "${state.batteryPercent}%",
                modifier = Modifier.weight(0.9f)
            )
            if (cpuGpu.isNotBlank()) {
                TelemetryItem(
                    icon = Icons.Rounded.Memory,
                    text = cpuGpu,
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}

@Composable
private fun TelemetryItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GameBarProfilePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GameBarSlider(
    icon: ImageVector,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Slider(
                value = value.coerceIn(0, 100).toFloat(),
                onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 100)) },
                valueRange = 0f..100f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
            )
            Text(
                text = "$value%",
                modifier = Modifier.width(32.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GameBarAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuickAppTile(
    app: GameBarQuickApp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val icon = rememberAppIcon(app.packageName)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Text(
                        text = app.label.take(1).uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = app.label,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun profileMatches(current: String, option: String): Boolean {
    val value = current.lowercase(Locale.ROOT)
    return when (option) {
        "Powersave" -> value.contains("eco") || value.contains("power")
        "Balanced" -> value.contains("balanc")
        "Maestro" -> value.contains("maestro") || value.contains("performance")
        else -> value == option.lowercase(Locale.ROOT)
    }
}

@Composable
private fun rememberAppIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = packageName
    ) {
        if (packageName.isNullOrBlank()) {
            value = null
        } else {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(96, 96)
                        .asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return icon
}
