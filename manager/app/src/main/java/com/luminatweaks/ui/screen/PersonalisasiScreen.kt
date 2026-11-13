/*
 * Copyright (C) 2026-2027 KowX
 * Copyright (C) 2026-2027 Zexshia
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

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness1
import androidx.compose.material.icons.rounded.Brightness3
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.ui.theme.AccentIntensity
import com.luminatweaks.ui.theme.ColorEngine
import com.luminatweaks.ui.theme.ColorMode
import com.luminatweaks.ui.theme.ThemeController
import com.luminatweaks.ui.theme.animateColorSchemeAsState
import com.luminatweaks.ui.theme.keyColorOptions
import com.luminatweaks.ui.theme.withAccentIntensity
import com.materialkolor.rememberDynamicColorScheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalisasiScreen(
    onBackClick: () -> Unit
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val savedSettings = remember { ThemeController.getAppSettings(context) }

    var currentColorMode by remember { mutableStateOf(savedSettings.colorMode) }
    var currentKeyColor by remember { mutableIntStateOf(savedSettings.keyColor) }
    var currentColorSpec by remember { mutableStateOf(savedSettings.colorSpec) }
    var currentIntensity by remember { mutableStateOf(savedSettings.accentIntensity) }
    var currentCornerRadius by remember { mutableIntStateOf(savedSettings.cornerRadius) }
    var isFloatingNavBar by remember { mutableStateOf(savedSettings.floatingNavBar) }

    val isDark = currentColorMode.getDarkThemeValue(isSystemInDarkTheme())
    val isAmoled = currentColorMode == ColorMode.DARKAMOLED
    val isDynamic = currentKeyColor == 0

    val previewBaseScheme: ColorScheme = if (isDynamic) {
        val systemDerived = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else ->
                if (isDark) darkColorScheme() else lightColorScheme()
        }
        rememberDynamicColorScheme(
            seedColor = systemDerived.primary,
            isDark = isDark,
            isAmoled = isAmoled,
            specVersion = currentColorSpec.librarySpec,
            primary = systemDerived.primary,
            secondary = systemDerived.secondary,
            tertiary = systemDerived.tertiary,
            neutral = systemDerived.surface,
            neutralVariant = systemDerived.surfaceVariant,
            error = systemDerived.error
        )
    } else {
        rememberDynamicColorScheme(
            seedColor = Color(currentKeyColor),
            isDark = isDark,
            isAmoled = isAmoled,
            specVersion = currentColorSpec.librarySpec
        )
    }

    val previewColorScheme = animateColorSchemeAsState(
        targetColorScheme = previewBaseScheme.withAccentIntensity(currentIntensity)
    )

    val lazyListState = rememberLazyListState()
    var isPreviewExpanded by rememberSaveable { mutableStateOf(true) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -14f) {
                    if (isPreviewExpanded) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isPreviewExpanded = false
                    }
                } else if (available.y > 14f) {
                    if (!isPreviewExpanded && (!lazyListState.canScrollBackward || lazyListState.firstVisibleItemIndex == 0)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isPreviewExpanded = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    fun updateTheme(
        mode: ColorMode = currentColorMode,
        key: Int = currentKeyColor,
        spec: ColorEngine = currentColorSpec,
        intensity: AccentIntensity = currentIntensity,
        radius: Int = currentCornerRadius,
        floating: Boolean = isFloatingNavBar
    ) {
        currentColorMode = mode
        currentKeyColor = key
        currentColorSpec = spec
        currentIntensity = intensity
        currentCornerRadius = radius
        isFloatingNavBar = floating
        ThemeController.saveTheme(context, mode, key, spec, intensity, radius, floating)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.personalization_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            PinnedPreviewHeader(
                expanded = isPreviewExpanded,
                colorScheme = previewColorScheme,
                cornerRadius = currentCornerRadius,
                isFloatingNavBar = isFloatingNavBar,
                onToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isPreviewExpanded = !isPreviewExpanded
                },
                onCollapse = {
                    if (isPreviewExpanded) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isPreviewExpanded = false
                    }
                },
                onExpand = {
                    if (!isPreviewExpanded) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isPreviewExpanded = true
                    }
                }
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    SheetHandle(
                        onToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isPreviewExpanded = !isPreviewExpanded
                        },
                        onCollapse = {
                            if (isPreviewExpanded) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPreviewExpanded = false
                            }
                        },
                        onExpand = {
                            if (!isPreviewExpanded) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPreviewExpanded = true
                            }
                        }
                    )

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            top = 2.dp,
                            bottom = 28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                            start = 20.dp,
                            end = 20.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.personalization_color),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val engines = listOf(
                                    ColorEngine.MaterialYou to stringResource(R.string.personalization_engine_material_you),
                                    ColorEngine.MaterialExpressive to stringResource(R.string.personalization_engine_expressive)
                                )

                                engines.forEach { (engine, label) ->
                                    val isSelected = currentColorSpec == engine
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                updateTheme(spec = engine)
                                            },
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                item {
                                    SwatchButton(
                                        color = Color.Unspecified,
                                        isSelected = currentKeyColor == 0,
                                        isDark = isDark,
                                        colorSpec = currentColorSpec,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            updateTheme(key = 0)
                                        }
                                    )
                                }

                                items(keyColorOptions) { colorInt ->
                                    SwatchButton(
                                        color = Color(colorInt),
                                        isSelected = currentKeyColor == colorInt,
                                        isDark = isDark,
                                        colorSpec = currentColorSpec,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            updateTheme(key = colorInt)
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val modes = listOf(
                                    ColorMode.SYSTEM to Icons.Rounded.Brightness4,
                                    ColorMode.LIGHT to Icons.Rounded.Brightness7,
                                    ColorMode.DARK to Icons.Rounded.Brightness3,
                                    ColorMode.DARKAMOLED to Icons.Rounded.Brightness1
                                )

                                modes.forEach { (mode, icon) ->
                                    val isSelected = currentColorMode == mode
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                updateTheme(mode = mode)
                                            },
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = mode.name,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.personalization_accent_intensity),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = currentIntensity.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                var sliderPosition by remember(currentIntensity) {
                                    mutableFloatStateOf(currentIntensity.ordinal.toFloat())
                                }

                                Slider(
                                    value = sliderPosition,
                                    onValueChange = { rawVal ->
                                        val oldIndex = sliderPosition.roundToInt()
                                        sliderPosition = rawVal
                                        val index = rawVal.roundToInt().coerceIn(0, 3)
                                        if (index != oldIndex) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        val newIntensity = AccentIntensity.fromValue(index)
                                        if (newIntensity != currentIntensity) {
                                            updateTheme(intensity = newIntensity)
                                        }
                                    },
                                    onValueChangeFinished = {
                                        val index = sliderPosition.roundToInt().coerceIn(0, 3)
                                        sliderPosition = index.toFloat()
                                        updateTheme(intensity = AccentIntensity.fromValue(index))
                                    },
                                    valueRange = 0f..3f,
                                    steps = 2,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RoundedCorner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.personalization_shape_display),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        updateTheme(floating = !isFloatingNavBar)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.personalization_floating_navbar),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.personalization_floating_navbar_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                        )
                                    }

                                    Switch(
                                        checked = isFloatingNavBar,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            updateTheme(floating = checked)
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                var sliderRadius by remember(currentCornerRadius) {
                                    mutableFloatStateOf(currentCornerRadius.toFloat())
                                }
                                val hasBeenAdjusted = sliderRadius.roundToInt() != ThemeController.DEFAULT_CORNER_RADIUS

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.RoundedCorner,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.personalization_corner_radius),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (hasBeenAdjusted) {
                                            FilledTonalButton(
                                                onClick = {
                                                    sliderRadius = ThemeController.DEFAULT_CORNER_RADIUS.toFloat()
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    updateTheme(radius = ThemeController.DEFAULT_CORNER_RADIUS)
                                                },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Refresh,
                                                    contentDescription = stringResource(R.string.action_reset),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(R.string.action_reset),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "${sliderRadius.roundToInt()} dp",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Slider(
                                    value = sliderRadius,
                                    onValueChange = { newVal ->
                                        if (newVal.roundToInt() != sliderRadius.roundToInt()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        sliderRadius = newVal
                                        val rounded = newVal.roundToInt().coerceIn(
                                            ThemeController.MIN_CORNER_RADIUS,
                                            ThemeController.MAX_CORNER_RADIUS
                                        )
                                        if (rounded != currentCornerRadius) {
                                            updateTheme(radius = rounded)
                                        }
                                    },
                                    valueRange = ThemeController.MIN_CORNER_RADIUS.toFloat()..ThemeController.MAX_CORNER_RADIUS.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHandle(
    onToggle: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit
) {
    val draggableState = rememberDraggableState { delta ->
        if (delta < -10f) {
            onCollapse()
        } else if (delta > 10f) {
            onExpand()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun PinnedPreviewHeader(
    expanded: Boolean,
    colorScheme: ColorScheme,
    cornerRadius: Int,
    isFloatingNavBar: Boolean,
    onToggle: () -> Unit,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draggableState = rememberDraggableState { delta ->
        if (delta < -10f) {
            onCollapse()
        } else if (delta > 10f) {
            onExpand()
        }
    }

    val baseWidth = 200.dp
    val baseHeight = 400.dp

    val targetHeaderHeight = if (expanded) 400.dp else 190.dp
    val headerHeight by animateDpAsState(
        targetValue = targetHeaderHeight,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "previewHeight"
    )

    val targetScale = if (expanded) 0.94f else 0.44f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "previewScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = baseWidth * scale, height = baseHeight * scale)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .requiredSize(width = baseWidth, height = baseHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                    },
                color = colorScheme.surface,
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(2.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                MockLuminaScreen(
                    colorScheme = colorScheme,
                    cornerRadius = cornerRadius,
                    isFloatingNavBar = isFloatingNavBar
                )
            }
        }
    }
}

@Composable
private fun MockLuminaScreen(
    colorScheme: ColorScheme,
    cornerRadius: Int = 18,
    isFloatingNavBar: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ratio = (cornerRadius / 18f).coerceIn(0.2f, 2f)

    MaterialTheme(
        colorScheme = colorScheme
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape((14f * ratio).dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.home_core_working),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape((12f * ratio).dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {}

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape((12f * ratio).dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {}
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape((16f * ratio).dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {}

            if (isFloatingNavBar) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 2.dp),
                    shape = RoundedCornerShape((20f * ratio).dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        ) {}
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    shape = RoundedCornerShape(topStart = (8f * ratio).dp, topEnd = (8f * ratio).dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        ) {}
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(width = 14.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchButton(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    colorSpec: ColorEngine,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val targetScheme = if (color == Color.Unspecified) {
        val baseScheme = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else ->
                if (isDark) darkColorScheme() else lightColorScheme()
        }
        rememberDynamicColorScheme(
            seedColor = baseScheme.primary,
            isDark = isDark,
            specVersion = colorSpec.librarySpec,
            primary = baseScheme.primary,
            secondary = baseScheme.secondary,
            tertiary = baseScheme.tertiary,
            neutral = baseScheme.surface,
            neutralVariant = baseScheme.surfaceVariant,
            error = baseScheme.error
        )
    } else {
        rememberDynamicColorScheme(
            seedColor = color,
            isDark = isDark,
            specVersion = colorSpec.librarySpec
        )
    }

    val colorScheme = animateColorSchemeAsState(targetScheme)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainer,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(48.dp)) {
                drawArc(
                    color = colorScheme.primaryContainer,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true
                )
                drawArc(
                    color = colorScheme.tertiaryContainer,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true
                )
            }

            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.1f else 1.0f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(
                        initialScale = 0.8f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
                    ),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}
