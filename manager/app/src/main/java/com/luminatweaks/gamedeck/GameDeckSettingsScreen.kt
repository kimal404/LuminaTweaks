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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.luminatweaks.gamedeck

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.luminaHazeHeader
import com.luminatweaks.ui.component.pressScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun GameDeckSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var config by remember { mutableStateOf(GameDeckPrefs.load(context)) }
    var busy by remember { mutableStateOf(false) }

    fun save(updated: GameDeckConfig) {
        config = updated
        GameDeckPrefs.save(context, updated)
        if (updated.enabled) {
            GameDeckService.sync(context, updated)
        }
    }

    fun setEnabled(enabled: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (enabled) {
            busy = true
            scope.launch {
                val granted = GameDeckService.ensureOverlayPermission(context)
                busy = false
                if (granted) {
                    save(config.copy(enabled = true, autoGame = true))
                }
            }
        } else {
            val updated = config.copy(enabled = false, autoGame = true)
            config = updated
            GameDeckPrefs.save(context, updated)
            GameDeckService.sync(context, updated)
        }
    }

    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = scheme.background,
        topBar = {
            Box(
                modifier = Modifier.luminaHazeHeader(
                    hazeState = hazeState,
                    surfaceColor = scheme.background,
                    overlappedFraction = scrollBehavior.state.overlappedFraction
                )
            ) {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = "Game Bar",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        LuminaBackButton(onBack = onBack, contentDescription = "Kembali")
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = navBottomInset + 28.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "hero_stage") {
                GameDeckHeroStage(
                    enabled = config.enabled,
                    busy = busy,
                    onToggleEnabled = { setEnabled(it) },
                    onPreviewShow = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        busy = true
                        scope.launch {
                            val granted = GameDeckService.ensureOverlayPermission(context)
                            busy = false
                            if (granted) GameDeckService.showNow(context)
                        }
                    },
                    onPreviewHide = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        GameDeckService.hideNow(context)
                    }
                )
            }

            item(key = "appearance_group") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DeckSectionLabel(title = "Tampilan")
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        DeckConnectedSideSelectorRow(
                            index = 0,
                            count = 2,
                            side = config.side,
                            onSelectSide = { newSide ->
                                if (newSide != config.side) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    save(config.copy(side = newSide))
                                }
                            }
                        )

                        DeckConnectedOpacityRow(
                            index = 1,
                            count = 2,
                            opacity = config.opacity,
                            onOpacityChange = { newOpacity ->
                                save(config.copy(opacity = newOpacity))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameDeckHeroStage(
    enabled: Boolean,
    busy: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onPreviewShow: () -> Unit,
    onPreviewHide: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val heroContainer by animateColorAsState(
        targetValue = if (enabled) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = tween(260),
        label = "deckHeroContainer"
    )
    val heroContent by animateColorAsState(
        targetValue = if (enabled) scheme.onPrimaryContainer else scheme.onSurface,
        animationSpec = tween(260),
        label = "deckHeroContent"
    )
    val subContent = if (enabled) {
        scheme.onPrimaryContainer.copy(alpha = 0.78f)
    } else {
        scheme.onSurfaceVariant
    }

    val toggleSource = remember { MutableInteractionSource() }
    val toggleScale = pressScale(toggleSource, 0.985f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(heroContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(toggleScale)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = toggleSource,
                    indication = null,
                    enabled = !busy
                ) { onToggleEnabled(!enabled) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (enabled) scheme.primary else scheme.primary.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    tint = if (enabled) scheme.onPrimary else scheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Side Panel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = heroContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Munculkan saat bermain game",
                    style = MaterialTheme.typography.bodySmall,
                    color = subContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = enabled,
                enabled = !busy,
                onCheckedChange = onToggleEnabled,
                thumbContent = {
                    if (enabled) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = scheme.surfaceContainerHighest,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StageControlPill(
                icon = Icons.Rounded.PlayArrow,
                label = "Tampilkan",
                containerColor = if (enabled) scheme.primary else scheme.secondaryContainer,
                contentColor = if (enabled) scheme.onPrimary else scheme.onSecondaryContainer,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                onClick = onPreviewShow
            )
            StageControlPill(
                icon = Icons.Rounded.VisibilityOff,
                label = "Sembunyikan",
                containerColor = scheme.surfaceContainerHighest.copy(alpha = 0.85f),
                contentColor = scheme.onSurface,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onPreviewHide
            )
        }
    }
}

@Composable
private fun StageControlPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.965f, enabled)

    Row(
        modifier = modifier
            .then(scaleModifier)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun DeckSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun DeckSquircleIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun deckConnectedShape(
    index: Int,
    count: Int,
    outerRadius: Dp = 26.dp,
    innerRadius: Dp = 4.dp
): Shape {
    return when {
        count <= 1 -> RoundedCornerShape(outerRadius)
        index <= 0 -> RoundedCornerShape(
            topStart = outerRadius,
            topEnd = outerRadius,
            bottomStart = innerRadius,
            bottomEnd = innerRadius
        )
        index >= count - 1 -> RoundedCornerShape(
            topStart = innerRadius,
            topEnd = innerRadius,
            bottomStart = outerRadius,
            bottomEnd = outerRadius
        )
        else -> RoundedCornerShape(innerRadius)
    }
}

@Composable
private fun DeckConnectedSideSelectorRow(
    index: Int,
    count: Int,
    side: GameDeckSide,
    onSelectSide: (GameDeckSide) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = deckConnectedShape(index = index, count = count)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeckSquircleIcon(icon = Icons.Rounded.ViewSidebar)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Posisi Dock Rail",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (side == GameDeckSide.LEFT) "Tepi layar kiri" else "Tepi layar kanan",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            DeckCompactSidePill(
                label = "Kiri",
                selected = side == GameDeckSide.LEFT,
                onClick = { onSelectSide(GameDeckSide.LEFT) }
            )
            DeckCompactSidePill(
                label = "Kanan",
                selected = side == GameDeckSide.RIGHT,
                onClick = { onSelectSide(GameDeckSide.RIGHT) }
            )
        }
    }
}

@Composable
private fun DeckCompactSidePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "compactSidePillBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "compactSidePillFg"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fg
        )
    }
}

@Composable
private fun DeckConnectedOpacityRow(
    index: Int,
    count: Int,
    opacity: Float,
    onOpacityChange: (Float) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val shape = deckConnectedShape(index = index, count = count)
    var sliderVal by remember(opacity) { mutableFloatStateOf(opacity.coerceIn(0.45f, 1f)) }
    val pct = (sliderVal * 100f).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeckSquircleIcon(icon = Icons.Rounded.Opacity)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Transparansi Panel",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.primary
            )
        }

        Slider(
            value = sliderVal,
            onValueChange = { raw ->
                val nextPct = (raw * 100f).roundToInt()
                if (nextPct != pct && nextPct % 5 == 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                sliderVal = raw
                onOpacityChange(raw)
            },
            valueRange = 0.45f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
