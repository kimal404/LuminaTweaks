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

package com.luminatweaks.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Bolt
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.luminatweaks.data.GameBarQuickApp
import com.luminatweaks.data.GameBarSide
import com.luminatweaks.data.GameBarUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAB_PERFORMANCE = 0
private const val TAB_TOOLS = 1
private const val TAB_CONTROLS = 2

private data class GameBarProfile(
    val value: String,
    val label: String,
    val icon: ImageVector
)

private val gameBarProfiles = listOf(
    GameBarProfile("Eco", "Eco", Icons.Rounded.Memory),
    GameBarProfile("Balanced", "Balanced", Icons.Rounded.Tune),
    GameBarProfile("Maestro", "Maestro", Icons.Rounded.Bolt),
    GameBarProfile("Performance", "Performance", Icons.Rounded.Speed)
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
    onToggleFpsOverlay: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleLiteMode: () -> Unit,
    onOpenManager: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit
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
                onToggleLiteMode = onToggleLiteMode,
                onOpenManager = onOpenManager,
                onRefreshRateSelected = onRefreshRateSelected
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
                .height(48.dp)
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
    onToggleLiteMode: () -> Unit,
    onOpenManager: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 28.dp).coerceAtLeast(220.dp)
    val isLeft = state.side == GameBarSide.LEFT
    var selectedTab by remember { mutableStateOf(TAB_PERFORMANCE) }
    val railModifier = Modifier.align(Alignment.CenterVertically)
    val cardModifier = Modifier
        .align(Alignment.CenterVertically)
        .weight(1f)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLeft) {
                GameBarRail(
                    state = state,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onQuickAppClick = onQuickAppClick,
                    onOpenManager = onOpenManager,
                    modifier = railModifier
                )
            }
            GameBarCard(
                state = state,
                selectedTab = selectedTab,
                maxHeight = maxHeight,
                onCollapse = onCollapse,
                onProfileSelected = onProfileSelected,
                onVolumeChanged = onVolumeChanged,
                onBrightnessChanged = onBrightnessChanged,
                onToggleFpsOverlay = onToggleFpsOverlay,
                onToggleDnd = onToggleDnd,
                onToggleLiteMode = onToggleLiteMode,
                onOpenManager = onOpenManager,
                onRefreshRateSelected = onRefreshRateSelected,
                modifier = cardModifier
            )
            if (!isLeft) {
                GameBarRail(
                    state = state,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onQuickAppClick = onQuickAppClick,
                    onOpenManager = onOpenManager,
                    modifier = railModifier
                )
            }
        }
    }
}

@Composable
private fun GameBarRail(
    state: GameBarUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onQuickAppClick: (String) -> Unit,
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(48.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = state.opacity.coerceIn(0.40f, 1f)
        ),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RailTab(
                icon = Icons.Rounded.Speed,
                description = "Performa",
                selected = selectedTab == TAB_PERFORMANCE,
                onClick = { onTabSelected(TAB_PERFORMANCE) }
            )
            RailTab(
                icon = Icons.Rounded.Apps,
                description = "Alat",
                selected = selectedTab == TAB_TOOLS,
                onClick = { onTabSelected(TAB_TOOLS) }
            )
            RailTab(
                icon = Icons.Rounded.Tune,
                description = "Kontrol",
                selected = selectedTab == TAB_CONTROLS,
                onClick = { onTabSelected(TAB_CONTROLS) }
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            )
            Spacer(modifier = Modifier.height(2.dp))
            val apps = state.quickApps.take(3)
            if (apps.isEmpty()) {
                RailQuickApp(
                    label = null,
                    icon = Icons.Rounded.Apps,
                    onClick = onOpenManager
                )
            } else {
                apps.forEach { app ->
                    RailQuickApp(
                        app = app,
                        onClick = { onQuickAppClick(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RailTab(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RailQuickApp(
    app: GameBarQuickApp? = null,
    label: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isServiceContext = !(context is android.app.Activity)
    val bitmap = if (app != null && !isServiceContext) rememberAppIcon(app.packageName) else null
    Surface(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                bitmap != null -> Image(
                    bitmap = bitmap,
                    contentDescription = app?.label,
                    modifier = Modifier.size(22.dp)
                )
                app != null -> Text(
                    text = app.label.take(1).uppercase(Locale.ROOT),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GameBarCard(
    state: GameBarUiState,
    selectedTab: Int,
    maxHeight: Dp,
    onCollapse: () -> Unit,
    onProfileSelected: (String) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onBrightnessChanged: (Int) -> Unit,
    onToggleFpsOverlay: () -> Unit,
    onToggleDnd: () -> Unit,
    onToggleLiteMode: () -> Unit,
    onOpenManager: () -> Unit,
    onRefreshRateSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GameBarCardHeader(onCollapse = onCollapse)
            when (selectedTab) {
                TAB_TOOLS -> ToolsContent(
                    state = state,
                    onToggleFpsOverlay = onToggleFpsOverlay,
                    onToggleLiteMode = onToggleLiteMode,
                    onToggleDnd = onToggleDnd,
                    onOpenManager = onOpenManager
                )
                TAB_CONTROLS -> ControlsContent(
                    state = state,
                    onVolumeChanged = onVolumeChanged,
                    onBrightnessChanged = onBrightnessChanged
                )
                else -> PerformanceContent(
                    state = state,
                    onProfileSelected = onProfileSelected,
                    onRefreshRateSelected = onRefreshRateSelected
                )
            }
        }
    }
}

@Composable
private fun GameBarCardHeader(onCollapse: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(28.dp))
        Text(
            text = "Lumina Game",
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Surface(
            onClick = onCollapse,
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup Game Bar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PerformanceContent(
    state: GameBarUiState,
    onProfileSelected: (String) -> Unit,
    onRefreshRateSelected: (Int?) -> Unit
) {
    HeroTelemetry(state)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        gameBarProfiles.chunked(2).forEach { rowProfiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                rowProfiles.forEach { profile ->
                    ProfileButton(
                        profile = profile,
                        selected = profileMatches(state.profile, profile.value),
                        modifier = Modifier.weight(1f),
                        onClick = { onProfileSelected(profile.value) }
                    )
                }
            }
        }
    }
    RefreshRateCard(
        modes = state.rrModes,
        currentId = state.currentRrId,
        liveHz = state.targetHz,
        onSelect = onRefreshRateSelected
    )
}

@Composable
private fun HeroTelemetry(state: GameBarUiState) {
    val frametimeText = if (state.frametimeMs > 0f) {
        String.format(Locale.US, "%.1f ms", state.frametimeMs)
    } else {
        "-- ms"
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Baterai",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "${state.batteryPercent}%",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Icon(
                    imageVector = Icons.Rounded.BatteryFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1.4f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Frame rate",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HudAccentLine()
                    Text(
                        text = if (state.fps > 0) state.fps.toString() else "--",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                    HudAccentLine()
                }
                Text(
                    text = "FPS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Thermostat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = "Suhu",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = state.tempLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = frametimeText,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun HudAccentLine() {
    Box(
        modifier = Modifier
            .width(8.dp)
            .height(2.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
    )
}

@Composable
private fun ProfileButton(
    profile: GameBarProfile,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = profile.icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = profile.label,
                fontSize = 11.5.sp,
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
private fun RefreshRateCard(
    modes: List<Int>,
    currentId: Int?,
    liveHz: Int,
    onSelect: (Int?) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Refresh rate",
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "$liveHz Hz",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                RateCapsule(
                    label = "Auto",
                    selected = currentId == null,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(null) }
                )
                modes.forEach { hz ->
                    RateCapsule(
                        label = "$hz",
                        selected = currentId != null && currentId == hz,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(hz) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RateCapsule(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(26.dp),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ToolsContent(
    state: GameBarUiState,
    onToggleFpsOverlay: () -> Unit,
    onToggleLiteMode: () -> Unit,
    onToggleDnd: () -> Unit,
    onOpenManager: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolCard(
                icon = Icons.Rounded.Speed,
                label = "FPS Meter",
                active = state.fpsOverlay,
                modifier = Modifier.weight(1f),
                onClick = onToggleFpsOverlay
            )
            ToolCard(
                icon = Icons.Rounded.Bolt,
                label = "Mode Lite",
                active = state.liteMode,
                modifier = Modifier.weight(1f),
                onClick = onToggleLiteMode
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolCard(
                icon = Icons.Rounded.NotificationsOff,
                label = "Jangan Ganggu",
                active = state.dndEnabled,
                modifier = Modifier.weight(1f),
                onClick = onToggleDnd
            )
            ToolCard(
                icon = Icons.Rounded.Apps,
                label = "Lumina Manager",
                active = false,
                modifier = Modifier.weight(1f),
                onClick = onOpenManager
            )
        }
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
        },
        border = if (active) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ControlsContent(
    state: GameBarUiState,
    onVolumeChanged: (Int) -> Unit,
    onBrightnessChanged: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameBarSlider(
            icon = Icons.Rounded.Brightness6,
            value = state.brightnessPercent,
            onValueChange = onBrightnessChanged
        )
        GameBarSlider(
            icon = Icons.Rounded.VolumeUp,
            value = state.volumePercent,
            onValueChange = onVolumeChanged
        )
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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

private fun profileMatches(current: String, option: String): Boolean {
    val value = current.lowercase(Locale.ROOT)
    return when (option) {
        "Eco" -> value.contains("eco") || value.contains("power")
        "Balanced" -> value.contains("balanc")
        "Maestro" -> value.contains("maestro")
        "Performance" -> value.contains("perform")
        else -> value == option.lowercase(Locale.ROOT)
    }
}

@Composable
private fun rememberAppIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        icon = if (packageName.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
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