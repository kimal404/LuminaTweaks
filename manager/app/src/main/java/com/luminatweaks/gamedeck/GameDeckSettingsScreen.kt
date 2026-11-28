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

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.model.AppItem
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.luminaHazeHeader
import com.luminatweaks.ui.component.pressScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val deckIconCache = mutableMapOf<String, Drawable?>()

@Composable
fun GameDeckSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val emptyPreviewIcons: Map<String, ImageBitmap> = emptyMap()
    var config by remember { mutableStateOf(GameDeckPrefs.load(context)) }
    var showAppPicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var selectedIcons by remember { mutableStateOf(emptyPreviewIcons) }

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
                    save(config.copy(enabled = true))
                }
            }
        } else {
            val updated = config.copy(enabled = false)
            config = updated
            GameDeckPrefs.save(context, updated)
            GameDeckService.sync(context, updated)
        }
    }

    LaunchedEffect(config.quickApps) {
        if (config.quickApps.isEmpty()) {
            selectedIcons = emptyPreviewIcons
            return@LaunchedEffect
        }
        selectedIcons = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val map = LinkedHashMap<String, ImageBitmap>()
            config.quickApps.take(4).forEach { pkg ->
                val drawable = deckIconCache.getOrPut(pkg) {
                    runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                } ?: return@forEach
                runCatching {
                    map[pkg] = drawable.toBitmap(64, 64).asImageBitmap()
                }
            }
            map
        }
    }

    if (showAppPicker) {
        DeckAppsPickerSheet(
            selected = config.quickApps,
            onDismiss = { showAppPicker = false },
            onToggle = { pkg ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val next = if (pkg in config.quickApps) {
                    config.quickApps - pkg
                } else if (config.quickApps.size >= 8) {
                    config.quickApps
                } else {
                    config.quickApps + pkg
                }
                save(config.copy(quickApps = next))
            }
        )
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
                    config = config,
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

            item(key = "behavior_group") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DeckSectionLabel(title = "Perilaku & Tampilan")
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        DeckConnectedSwitchRow(
                            index = 0,
                            count = 3,
                            icon = Icons.Rounded.AutoAwesome,
                            title = "Otomatis saat game",
                            subtitle = if (config.autoGame) {
                                "Muncul otomatis ketika game dibuka"
                            } else {
                                "Tetap tampil di layar saat dinyalakan"
                            },
                            checked = config.autoGame,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                save(config.copy(autoGame = it))
                            }
                        )

                        DeckConnectedSideSelectorRow(
                            index = 1,
                            count = 3,
                            side = config.side,
                            onSelectSide = { newSide ->
                                if (newSide != config.side) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    save(config.copy(side = newSide))
                                }
                            }
                        )

                        DeckConnectedOpacityRow(
                            index = 2,
                            count = 3,
                            opacity = config.opacity,
                            onOpacityChange = { newOpacity ->
                                save(config.copy(opacity = newOpacity))
                            }
                        )
                    }
                }
            }

            item(key = "freeform_group") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DeckSectionLabel(title = "Pintasan Freeform")
                    DeckFreeformAppsRow(
                        selectedPackages = config.quickApps,
                        previewIcons = selectedIcons,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showAppPicker = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameDeckHeroStage(
    config: GameDeckConfig,
    busy: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onPreviewShow: () -> Unit,
    onPreviewHide: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val heroContainer by animateColorAsState(
        targetValue = if (config.enabled) scheme.primaryContainer else scheme.surfaceContainerHigh,
        animationSpec = tween(260),
        label = "deckHeroContainer"
    )
    val heroContent by animateColorAsState(
        targetValue = if (config.enabled) scheme.onPrimaryContainer else scheme.onSurface,
        animationSpec = tween(260),
        label = "deckHeroContent"
    )
    val subContent = if (config.enabled) {
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
                ) { onToggleEnabled(!config.enabled) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (config.enabled) scheme.primary else scheme.primary.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    tint = if (config.enabled) scheme.onPrimary else scheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lumina Game Deck",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = heroContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (config.enabled) "Aktif · Siap di dalam game" else "Nonaktif",
                    style = MaterialTheme.typography.bodySmall,
                    color = subContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = config.enabled,
                enabled = !busy,
                onCheckedChange = onToggleEnabled,
                thumbContent = {
                    if (config.enabled) {
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

        DeckLiveStageViewport(config = config)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StageControlPill(
                icon = Icons.Rounded.PlayArrow,
                label = "Tampilkan",
                containerColor = if (config.enabled) scheme.primary else scheme.secondaryContainer,
                contentColor = if (config.enabled) scheme.onPrimary else scheme.onSecondaryContainer,
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
private fun DeckLiveStageViewport(config: GameDeckConfig) {
    val scheme = MaterialTheme.colorScheme
    val isLeft = config.side == GameDeckSide.LEFT

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.background.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = scheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rail: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier
                        .width(38.dp)
                        .height(120.dp),
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh.copy(alpha = config.opacity)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(1.dp)
                                .background(scheme.outlineVariant)
                        )
                        val slotCount = config.quickApps.size.coerceIn(1, 3)
                        repeat(slotCount) { idx ->
                            val hasApp = idx < config.quickApps.size
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (hasApp) scheme.primary.copy(alpha = 0.28f)
                                        else scheme.surfaceContainerHighest
                                    )
                            )
                        }
                    }
                }
            }

            val deck: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier
                        .width(184.dp)
                        .height(120.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surfaceContainerHigh.copy(alpha = config.opacity)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CPU 42%",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant
                            )
                            Text(
                                text = "120 FPS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary
                            )
                            Text(
                                text = "GPU 58%",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Eco", "Balanced").forEach { label ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(scheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 7.sp,
                                        color = scheme.onSurface
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Performance", "Lite").forEach { label ->
                                val active = label == "Performance"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (active) scheme.primary else scheme.surfaceContainerHighest
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 7.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        color = if (active) scheme.onPrimary else scheme.onSurface
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(scheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Dock ${if (isLeft) "Kiri" else "Kanan"} · ${(config.opacity * 100).roundToInt()}%",
                                fontSize = 7.sp,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isLeft) {
                rail()
                deck()
            } else {
                deck()
                rail()
            }
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
private fun DeckConnectedSwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = deckConnectedShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.975f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeckSquircleIcon(icon = icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            interactionSource = interactionSource,
            thumbContent = {
                if (checked) {
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

@Composable
private fun DeckFreeformAppsRow(
    selectedPackages: Set<String>,
    previewIcons: Map<String, ImageBitmap>,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.975f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeckSquircleIcon(icon = Icons.Rounded.Apps)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Aplikasi Freeform di Dock Rail",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (selectedPackages.isEmpty()) {
                    "Pilih hingga 8 aplikasi jendela bebas"
                } else {
                    "${selectedPackages.size} dari 8 aplikasi dipilih"
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }

        if (previewIcons.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-6).dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                previewIcons.values.take(3).forEach { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, scheme.surfaceContainerHigh, CircleShape)
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeckAppsPickerSheet(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val emptyApps: List<AppItem> = emptyList()
    val emptyIcons: Map<String, ImageBitmap> = emptyMap()
    var apps by remember { mutableStateOf(emptyApps) }
    var loading by remember { mutableStateOf(true) }
    var icons by remember { mutableStateOf(emptyIcons) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val systemMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        apps = withContext(Dispatchers.IO) {
            ConfigRepository.loadInstalledApps(pm)
                .filter { item ->
                    if (item.packageName == selfPkg) return@filter false
                    if (item.packageName in selected) return@filter true
                    if (pm.getLaunchIntentForPackage(item.packageName) == null) return@filter false
                    val info = runCatching { pm.getApplicationInfo(item.packageName, 0) }.getOrNull()
                        ?: return@filter false
                    (info.flags and systemMask) == 0
                }
                .sortedWith(
                    compareByDescending<AppItem> { it.packageName in selected }
                        .thenBy { it.name.lowercase() }
                )
        }
        loading = false
    }

    val filteredApps = remember(apps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) apps else apps.filter {
            it.name.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }

    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        icons = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val result = LinkedHashMap<String, ImageBitmap>()
            apps.forEach { app ->
                val drawable = deckIconCache.getOrPut(app.packageName) {
                    runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
                } ?: return@forEach
                runCatching {
                    result[app.packageName] = drawable.toBitmap(72, 72).asImageBitmap()
                }
            }
            result
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = scheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets(0.dp) },
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = scheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Aplikasi Freeform",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "Muncul di Dock Rail sebagai jendela mengambang",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.secondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${selected.size}/8",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSecondaryContainer
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                shape = CircleShape,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Hapus"
                            )
                        }
                    }
                },
                placeholder = { Text("Cari aplikasi...") },
                modifier = Modifier.fillMaxWidth()
            )

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(filteredApps, key = { _, app -> app.packageName }) { index, app ->
                        val icon = icons[app.packageName]
                        val checked = app.packageName in selected
                        val enabled = checked || selected.size < 8
                        val rowShape = deckConnectedShape(
                            index = index,
                            count = filteredApps.size,
                            outerRadius = 22.dp,
                            innerRadius = 4.dp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(
                                    if (checked) scheme.secondaryContainer.copy(alpha = 0.55f)
                                    else scheme.surfaceContainerLow
                                )
                                .clickable(enabled = enabled) { onToggle(app.packageName) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(scheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (icon != null) {
                                    Image(
                                        bitmap = icon,
                                        contentDescription = app.name,
                                        modifier = Modifier.size(30.dp)
                                    )
                                } else {
                                    Text(
                                        text = app.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Checkbox(
                                checked = checked,
                                enabled = enabled,
                                onCheckedChange = { onToggle(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}
