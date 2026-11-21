package com.luminatweaks.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.luminatweaks.data.GameBarQuickApp
import com.luminatweaks.data.GameBarSide
import com.luminatweaks.data.GameBarUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class GameBarMode(
    val value: String,
    val label: String,
    val icon: ImageVector
)

private val gameBarModes = listOf(
    GameBarMode("Eco", "Hemat", Icons.Rounded.BatterySaver),
    GameBarMode("Balanced", "Seimbang", Icons.Rounded.Speed),
    GameBarMode("Maestro", "Maestro", Icons.Rounded.SportsEsports),
    GameBarMode("Performance", "Performa", Icons.Rounded.Bolt)
)

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
    onOpenManager: () -> Unit
) {
    val current by state
    AnimatedContent(
        targetState = current.expanded,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
        modifier = Modifier.fillMaxSize(),
        label = "gameBarMode"
    ) { expanded ->
        if (expanded) {
            GameSpacePanel(
                state = current,
                onCollapse = onCollapse,
                onProfileSelected = onProfileSelected,
                onVolumeChanged = onVolumeChanged,
                onBrightnessChanged = onBrightnessChanged,
                onQuickAppClick = onQuickAppClick,
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onExpand() })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onHandleDrag(dragAmount.y)
                }
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 2.dp),
            shape = RoundedCornerShape(
                topStart = if (isLeft) 4.dp else 18.dp,
                bottomStart = if (isLeft) 4.dp else 18.dp,
                topEnd = if (isLeft) 18.dp else 4.dp,
                bottomEnd = if (isLeft) 18.dp else 4.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(5.dp).size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    imageVector = if (isLeft) Icons.Rounded.KeyboardArrowRight else Icons.Rounded.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun GameSpacePanel(
    state: GameBarUiState,
    onCollapse: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onBrightnessChanged: (Int) -> Unit,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    val isLeft = state.side == GameBarSide.LEFT
    val shape = if (isLeft) {
        RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp, topStart = 8.dp, bottomStart = 8.dp)
    } else {
        RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 8.dp, bottomEnd = 8.dp)
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .systemBarsPadding()
            .padding(vertical = 8.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(
            alpha = state.opacity.coerceIn(0.55f, 1f)
        ),
        tonalElevation = 2.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GameSpaceHeader(state, onCollapse, onOpenManager)
            GameSpacePerformance(state, onProfileSelected)
            GameSpaceMonitor(state)
            GameSpaceControls(state, onVolumeChanged, onBrightnessChanged)
            GameSpaceQuickApps(state.quickApps, onQuickAppClick, onOpenManager)
            GameSpaceStatus(state)
        }
    }
}

@Composable
private fun GameSpaceHeader(
    state: GameBarUiState,
    onCollapse: () -> Unit,
    onOpenManager: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = rememberAppIcon(state.gamePackage)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = state.gameLabel,
                        modifier = Modifier.size(34.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.gameLabel ?: "Game Space",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (state.autoGame) "Game Space · otomatis" else "Game Space · manual",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = if (state.fps > 0) "${state.fps} FPS" else "-- FPS",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onOpenManager) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Pengaturan Game Bar"
            )
        }
        IconButton(onClick = onCollapse) {
            Icon(
                imageVector = Icons.Rounded.CloseFullscreen,
                contentDescription = "Tutup Game Bar"
            )
        }
    }
}

@Composable
private fun GameSpacePerformance(
    state: GameBarUiState,
    onProfileSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameSpaceSectionTitle(
            title = "Mode performa",
            trailing = if (state.autoProfile) "AUTO" else "MANUAL"
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            gameBarModes.chunked(2).forEach { rowModes ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowModes.forEach { mode ->
                        GameSpaceModeCard(
                            mode = mode,
                            selected = profileMatches(state.profile, mode.value),
                            modifier = Modifier.weight(1f),
                            onClick = { onProfileSelected(mode.value) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameSpaceModeCard(
    mode: GameBarMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "gameBarModeContainer"
    )
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Icon(
                    imageVector = mode.icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(6.dp).size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = mode.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1
            )
        }
    }
}

private fun profileMatches(current: String, option: String): Boolean {
    if (current.equals(option, ignoreCase = true)) return true
    return option == "Performance" && current.startsWith("Performance", ignoreCase = true)
}

@Composable
private fun GameSpaceMonitor(state: GameBarUiState) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.fps > 0) state.fps.toString() else "--",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "FPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (state.frametimeMs > 0f) {
                            "${"%.1f".format(state.frametimeMs)} ms"
                        } else {
                            "-- ms"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${state.targetHz} Hz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameSpaceMetric(
                    icon = Icons.Rounded.Memory,
                    label = "CPU",
                    value = state.cpuPercent?.let { "$it%" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                GameSpaceMetric(
                    icon = Icons.Rounded.GraphicEq,
                    label = "GPU",
                    value = state.gpuPercent?.let { "$it%" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                GameSpaceMetric(
                    icon = Icons.Rounded.Thermostat,
                    label = "Suhu",
                    value = state.tempLabel,
                    modifier = Modifier.weight(1f)
                )
                GameSpaceMetric(
                    icon = Icons.Rounded.BatteryFull,
                    label = "Baterai",
                    value = "${state.batteryPercent}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GameSpaceMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun GameSpaceControls(
    state: GameBarUiState,
    onVolumeChanged: (Int) -> Unit,
    onBrightnessChanged: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            GameSpaceSlider(
                icon = Icons.Rounded.VolumeUp,
                value = state.volumePercent,
                onValueChange = onVolumeChanged
            )
            GameSpaceSlider(
                icon = Icons.Rounded.Brightness6,
                value = state.brightnessPercent,
                onValueChange = onBrightnessChanged
            )
        }
    }
}

@Composable
private fun GameSpaceSlider(
    icon: ImageVector,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Slider(
            value = value.coerceIn(0, 100).toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Text(
            text = "$value%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun GameSpaceQuickApps(
    apps: List<GameBarQuickApp>,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameSpaceSectionTitle(
            title = "Aplikasi cepat",
            trailing = if (apps.isEmpty()) "ATUR" else null,
            onTrailingClick = onOpenManager
        )
        if (apps.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenManager),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = "Tambahkan aplikasi dari Manager",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                apps.chunked(4).forEach { rowApps ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowApps.forEach { app ->
                            GameSpaceAppTile(
                                app = app,
                                modifier = Modifier.weight(1f),
                                onClick = { onQuickAppClick(app.packageName) }
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

@Composable
private fun GameSpaceAppTile(
    app: GameBarQuickApp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val icon = rememberAppIcon(app.packageName)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(38.dp)
                    )
                } else {
                    Text(
                        text = app.label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GameSpaceStatus(state: GameBarUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = if (state.autoGame) "AUTO GAME" else "SELALU TAMPIL",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Lumina Game Space",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GameSpaceSectionTitle(
    title: String,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = onTrailingClick != null) {
                        onTrailingClick?.invoke()
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
