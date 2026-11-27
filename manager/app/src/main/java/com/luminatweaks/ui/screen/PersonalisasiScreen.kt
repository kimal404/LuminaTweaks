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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.appBarFade
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.theme.AccentIntensity
import com.luminatweaks.ui.theme.ColorEngine
import com.luminatweaks.ui.theme.ColorMode
import com.luminatweaks.ui.theme.ThemeController
import com.luminatweaks.ui.theme.animateColorSchemeAsState
import com.luminatweaks.ui.theme.keyColorOptions
import com.luminatweaks.ui.theme.withAccentIntensity
import com.materialkolor.rememberDynamicColorScheme
import java.util.Locale
import kotlin.math.roundToInt

private const val DYNAMIC_SEED = 0

private data class SwatchKey(val seed: Int, val isDark: Boolean)

@Composable
private fun persLocalizedLabel(id: String, en: String): String {
    val isIndonesian = remember {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        lang == "in" || lang == "id"
    }
    return if (isIndonesian) id else en
}

@Composable
private fun accentIntensityDisplayName(intensity: AccentIntensity): String {
    return when (intensity) {
        AccentIntensity.Neutral -> persLocalizedLabel("Netral", "Neutral")
        AccentIntensity.Balanced -> persLocalizedLabel("Seimbang", "Balanced")
        AccentIntensity.Bright -> persLocalizedLabel("Cerah", "Bright")
        AccentIntensity.Vibrant -> persLocalizedLabel("Bersemangat", "Vibrant")
    }
}

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
    val swatchSchemeCache = remember(currentColorSpec) {
        HashMap<SwatchKey, ColorScheme>()
    }

    fun saveSettings(
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
        ThemeController.saveTheme(
            context = context,
            mode = mode,
            key = key,
            spec = spec,
            intensity = intensity,
            cornerRadius = radius,
            floatingNavBar = floating
        )
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPaddingResolved = if (isFloatingNavBar) 112.dp + navInset else 28.dp + navInset
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val fadeSurface = MaterialTheme.colorScheme.background

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .appBarFade(fadeSurface) { scrollBehavior.state.overlappedFraction }
                    .statusBarsPadding()
            ) {
                LargeFlexibleTopAppBar(
                    navigationIcon = { LuminaBackButton(onBack = onBackClick) },
                    title = {
                        Text(
                            text = stringResource(R.string.personalization_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPaddingResolved,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item(key = "accent_color_section") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.personalization_color),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        ColorEngine.entries.forEachIndexed { index, spec ->
                            val engineSource = remember { MutableInteractionSource() }
                            val enginePress = pressScale(engineSource, 0.95f)
                            ToggleButton(
                                checked = currentColorSpec == spec,
                                onCheckedChange = { checked ->
                                    if (checked && currentColorSpec != spec) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        saveSettings(spec = spec)
                                    }
                                },
                                interactionSource = engineSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(enginePress)
                                    .semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    ColorEngine.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                            ) {
                                Text(
                                    text = if (spec == ColorEngine.MaterialYou) {
                                        persLocalizedLabel("Materi Anda", "Material You")
                                    } else {
                                        persLocalizedLabel("Ekspresif Materi", "Material Expressive")
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = 0.99f }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0.0f to Color.Transparent,
                                        0.08f to Color.Black,
                                        0.92f to Color.Black,
                                        1.0f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "dynamic") {
                            AccentColorButton(
                                color = Color.Unspecified,
                                isSelected = currentKeyColor == 0,
                                isDark = isDark,
                                colorSpec = currentColorSpec,
                                accentIntensity = currentIntensity,
                                schemeCache = swatchSchemeCache,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    saveSettings(key = 0)
                                }
                            )
                        }
                        items(keyColorOptions, key = { it }) { colorInt ->
                            AccentColorButton(
                                color = Color(colorInt),
                                isSelected = currentKeyColor == colorInt,
                                isDark = isDark,
                                colorSpec = currentColorSpec,
                                accentIntensity = currentIntensity,
                                schemeCache = swatchSchemeCache,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    saveSettings(key = colorInt)
                                }
                            )
                        }
                    }

                    val modes = listOf(
                        ColorMode.SYSTEM,
                        ColorMode.LIGHT,
                        ColorMode.DARK,
                        ColorMode.DARKAMOLED
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        modes.forEachIndexed { index, mode ->
                            val modeSource = remember { MutableInteractionSource() }
                            val modePress = pressScale(modeSource, 0.95f)
                            ToggleButton(
                                checked = currentColorMode == mode,
                                onCheckedChange = { checked ->
                                    if (checked && currentColorMode != mode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        saveSettings(mode = mode)
                                    }
                                },
                                interactionSource = modeSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(modePress)
                                    .semantics { role = Role.RadioButton },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        ColorMode.SYSTEM -> Icons.Filled.Brightness4
                                        ColorMode.LIGHT -> Icons.Filled.Brightness7
                                        ColorMode.DARK -> Icons.Filled.Brightness3
                                        ColorMode.DARKAMOLED -> Icons.Filled.Brightness1
                                    },
                                    contentDescription = mode.name
                                )
                            }
                        }
                    }

                    AccentIntensityCard(
                        currentIntensity = currentIntensity,
                        onIntensityChange = { newIntensity ->
                            if (newIntensity != currentIntensity) {
                                saveSettings(intensity = newIntensity)
                            }
                        }
                    )
                }
            }

            item(key = "appearance_and_nav") {
                Text(
                    text = persLocalizedLabel("Tampilan & Navigasi", "Appearance & Navigation"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PersFloatingNavRow(
                        index = 0,
                        count = 2,
                        checked = isFloatingNavBar,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            saveSettings(floating = checked)
                        }
                    )
                    PersCornerRadiusRow(
                        index = 1,
                        count = 2,
                        radius = currentCornerRadius,
                        onRadiusChange = { radius ->
                            if (radius != currentCornerRadius) {
                                saveSettings(radius = radius)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentColorButton(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    colorSpec: ColorEngine,
    accentIntensity: AccentIntensity,
    schemeCache: HashMap<SwatchKey, ColorScheme>,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val baseTargetScheme = if (color == Color.Unspecified) {
        schemeCache.getOrPut(SwatchKey(DYNAMIC_SEED, isDark)) {
            val baseScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                else ->
                    if (isDark) darkColorScheme() else expressiveLightColorScheme()
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
        }
    } else {
        schemeCache.getOrPut(SwatchKey(color.toArgb(), isDark)) {
            rememberDynamicColorScheme(
                seedColor = color,
                isDark = isDark,
                specVersion = colorSpec.librarySpec
            )
        }
    }

    val colorScheme = animateColorSchemeAsState(
        targetColorScheme = baseTargetScheme.withAccentIntensity(accentIntensity)
    )
    val swatchSource = remember { MutableInteractionSource() }
    val swatchPress = pressScale(swatchSource, 0.9f)

    Surface(
        onClick = onClick,
        interactionSource = swatchSource,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainer,
        modifier = Modifier
            .size(72.dp)
            .then(swatchPress)
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
                label = "swatchScale"
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
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
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

@Composable
private fun AccentIntensityCard(
    currentIntensity: AccentIntensity,
    onIntensityChange: (AccentIntensity) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val entries = remember { AccentIntensity.entries }
    val defaultIntensity = AccentIntensity.Balanced
    val currentIndex = entries.indexOf(currentIntensity).coerceAtLeast(0)
    var sliderPos by remember(currentIndex) { mutableFloatStateOf(currentIndex.toFloat()) }
    val maxIdx = (entries.size - 1).coerceAtLeast(1)
    val snappedIdx = sliderPos.roundToInt().coerceIn(0, entries.lastIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersSquircleIcon(icon = Icons.Filled.Tune)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = persLocalizedLabel("Intensitas aksen", "Accent intensity"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = accentIntensityDisplayName(entries[snappedIdx]),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val defIdx = entries.indexOf(defaultIntensity).coerceAtLeast(0)
                    sliderPos = defIdx.toFloat()
                    onIntensityChange(defaultIntensity)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = stringResource(R.string.action_reset),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Slider(
            value = sliderPos.coerceIn(0f, maxIdx.toFloat()),
            onValueChange = { raw ->
                val snapped = if (raw in 0.85f..1.15f) 1f else raw
                val nextIdx = snapped.roundToInt().coerceIn(0, entries.lastIndex)
                if (nextIdx != sliderPos.roundToInt()) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                sliderPos = snapped
                if (entries[nextIdx] != currentIntensity) {
                    onIntensityChange(entries[nextIdx])
                }
            },
            valueRange = 0f..maxIdx.toFloat(),
            steps = (entries.size - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PersSquircleIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun PersFloatingNavRow(
    index: Int,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersSquircleIcon(icon = Icons.Rounded.Waves)
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.personalization_floating_navbar_short),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.personalization_floating_navbar_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            }
        )
    }
}

@Composable
private fun PersCornerRadiusRow(
    index: Int,
    count: Int,
    radius: Int,
    onRadiusChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var sliderValue by remember(radius) { mutableFloatStateOf(radius.toFloat()) }
    val displayedRadius = sliderValue.roundToInt()
    val minRadius = ThemeController.MIN_CORNER_RADIUS.toFloat()
    val maxRadius = ThemeController.MAX_CORNER_RADIUS.toFloat()
    val stepCount = ((ThemeController.MAX_CORNER_RADIUS - ThemeController.MIN_CORNER_RADIUS) / 2 - 1)
        .coerceAtLeast(0)
    val shape = connectedContainerShape(index = index, count = count)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersSquircleIcon(icon = Icons.Rounded.RoundedCorner)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.personalization_corner_radius),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$displayedRadius dp",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    sliderValue = ThemeController.DEFAULT_CORNER_RADIUS.toFloat()
                    onRadiusChange(ThemeController.DEFAULT_CORNER_RADIUS)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = stringResource(R.string.action_reset),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Slider(
            value = sliderValue.coerceIn(minRadius, maxRadius),
            onValueChange = { newVal ->
                if (newVal.roundToInt() != sliderValue.roundToInt()) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                sliderValue = newVal
                val rounded = newVal.roundToInt().coerceIn(
                    ThemeController.MIN_CORNER_RADIUS,
                    ThemeController.MAX_CORNER_RADIUS
                )
                onRadiusChange(rounded)
            },
            valueRange = minRadius..maxRadius,
            steps = stepCount,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
