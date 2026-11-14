// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness1
import androidx.compose.material.icons.rounded.Brightness3
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.ui.theme.AccentIntensity
import com.luminatweaks.ui.theme.ColorEngine
import com.luminatweaks.ui.theme.ColorMode
import com.luminatweaks.ui.theme.ThemeController
import com.luminatweaks.ui.theme.animateColorSchemeAsState
import com.luminatweaks.ui.theme.keyColorOptions
import com.luminatweaks.ui.theme.withAccentIntensity
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import java.util.Locale
import kotlin.math.roundToInt

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
        AccentIntensity.Neutral -> persLocalizedLabel("Dibungkam", "Muted")
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
    val isAmoled = currentColorMode == ColorMode.DARKAMOLED
    val outerRadius = currentCornerRadius.coerceAtLeast(26).dp

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

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (isFloatingNavBar) 108.dp + navBottom else 24.dp + navBottom

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.personalization_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "color_and_theme") {
                Surface(
                    shape = RoundedCornerShape(outerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ColorEnginePillButton(
                                label = persLocalizedLabel("Materi Anda", "Material You"),
                                selected = currentColorSpec == ColorEngine.MaterialYou,
                                onClick = {
                                    if (currentColorSpec != ColorEngine.MaterialYou) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        saveSettings(spec = ColorEngine.MaterialYou)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            ColorEnginePillButton(
                                label = persLocalizedLabel("Ekspresif Materi", "Material Expressive"),
                                selected = currentColorSpec == ColorEngine.MaterialExpressive,
                                onClick = {
                                    if (currentColorSpec != ColorEngine.MaterialExpressive) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        saveSettings(spec = ColorEngine.MaterialExpressive)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "dynamic") {
                                ExpressiveSwatchTile(
                                    color = Color.Unspecified,
                                    isSelected = currentKeyColor == 0,
                                    isDark = isDark,
                                    isAmoled = isAmoled,
                                    colorSpec = currentColorSpec,
                                    accentIntensity = currentIntensity,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        saveSettings(key = 0)
                                    }
                                )
                            }
                            items(keyColorOptions, key = { it }) { colorInt ->
                                ExpressiveSwatchTile(
                                    color = Color(colorInt),
                                    isSelected = currentKeyColor == colorInt,
                                    isDark = isDark,
                                    isAmoled = isAmoled,
                                    colorSpec = currentColorSpec,
                                    accentIntensity = currentIntensity,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        saveSettings(key = colorInt)
                                    }
                                )
                            }
                        }

                        val themeModes = listOf(
                            Triple(
                                ColorMode.SYSTEM,
                                Icons.Rounded.BrightnessAuto,
                                persLocalizedLabel("Ikuti Sistem", "Follow System")
                            ),
                            Triple(
                                ColorMode.LIGHT,
                                Icons.Rounded.LightMode,
                                persLocalizedLabel("Terang", "Light")
                            ),
                            Triple(
                                ColorMode.DARK,
                                Icons.Rounded.Brightness3,
                                persLocalizedLabel("Gelap", "Dark")
                            ),
                            Triple(
                                ColorMode.DARKAMOLED,
                                Icons.Rounded.Brightness1,
                                "Pure AMOLED"
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            themeModes.forEach { (mode, icon, label) ->
                                val isSelected = currentColorMode == mode
                                val pillBg by animateColorAsState(
                                    targetValue = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    label = "modePillBg"
                                )
                                val pillTint by animateColorAsState(
                                    targetValue = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    label = "modePillTint"
                                )
                                val weight by animateFloatAsState(
                                    targetValue = if (isSelected) 1.45f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "modePillWeight"
                                )

                                Surface(
                                    onClick = {
                                        if (!isSelected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            saveSettings(mode = mode)
                                        }
                                    },
                                    shape = CircleShape,
                                    color = pillBg,
                                    modifier = Modifier
                                        .weight(weight)
                                        .height(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = pillTint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AccentIntensityCard(
                            currentIntensity = currentIntensity,
                            onIntensityChange = { newIntensity ->
                                if (newIntensity != currentIntensity) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    saveSettings(intensity = newIntensity)
                                }
                            }
                        )
                    }
                }
            }

            item(key = "appearance_and_nav") {
                PersNestedCardGroup(
                    headerIcon = Icons.Rounded.Tune,
                    headerTitle = persLocalizedLabel("Tampilan & Navigasi", "Appearance & Navigation"),
                    outerRadius = outerRadius
                ) {
                    PersFloatingNavSwitchCard(
                        checked = isFloatingNavBar,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            saveSettings(floating = checked)
                        }
                    )

                    PersCornerRadiusCard(
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
private fun ColorEnginePillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "enginePillScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "enginePillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "enginePillText"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        interactionSource = interactionSource,
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpressiveSwatchTile(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    isAmoled: Boolean,
    colorSpec: ColorEngine,
    accentIntensity: AccentIntensity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val swatchSeed = if (color == Color.Unspecified) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                Color(context.getColor(android.R.color.system_accent1_500))
            }.getOrElse {
                val base = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                base.primary
            }
        } else {
            if (isDark) darkColorScheme().primary else lightColorScheme().primary
        }
    } else {
        color
    }

    val swatchPaletteStyle = when (colorSpec) {
        ColorEngine.MaterialYou -> PaletteStyle.TonalSpot
        ColorEngine.MaterialExpressive -> PaletteStyle.Vibrant
    }

    val targetScheme = rememberDynamicColorScheme(
        seedColor = swatchSeed,
        isDark = isDark,
        isAmoled = isAmoled,
        specVersion = colorSpec.librarySpec,
        style = swatchPaletteStyle
    ).withAccentIntensity(
        intensity = accentIntensity,
        isDark = isDark,
        isAmoled = isAmoled
    )

    val colorScheme = animateColorSchemeAsState(targetColorScheme = targetScheme)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "swatchScale"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(44.dp)) {
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

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(initialScale = 0.78f),
                exit = fadeOut() + scaleOut(targetScale = 0.78f)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .border(2.dp, colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isSelected,
                enter = fadeIn() + scaleIn(initialScale = 0.78f),
                exit = fadeOut() + scaleOut(targetScale = 0.78f)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun M3ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbHeight by animateDpAsState(
        targetValue = if (isInteracting) 32.dp else 28.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "m3eThumbHeight"
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (isInteracting) 3.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "m3eThumbWidth"
    )

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    val sliderState = remember(steps, valueRange) {
        SliderState(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            steps = steps,
            valueRange = valueRange
        )
    }

    LaunchedEffect(value) {
        sliderState.value = value.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Slider(
        state = sliderState,
        onValueChange = { newVal ->
            sliderState.value = newVal
            onValueChange(newVal)
        },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        thumb = {
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .width(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(thumbWidth)
                        .height(thumbHeight)
                        .clip(CircleShape)
                        .background(activeColor)
                )
            }
        },
        track = { sliderState: SliderState ->
            val span = (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                .takeIf { it > 0f } ?: 1f
            val fraction = ((sliderState.value - sliderState.valueRange.start) / span)
                .coerceIn(0f, 1f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            ) {
                val trackH = size.height
                val outerR = trackH / 2f
                val innerR = 4.dp.toPx().coerceAtMost(outerR)
                val gapPx = 5.dp.toPx()
                val thumbCenterX = size.width * fraction

                val activeRight = (thumbCenterX - gapPx).coerceAtLeast(0f)
                val inactiveLeft = (thumbCenterX + gapPx).coerceAtMost(size.width)

                if (activeRight > 1f) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = 0f,
                                top = 0f,
                                right = activeRight,
                                bottom = trackH,
                                topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(outerR),
                                bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(outerR),
                                topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(innerR),
                                bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(innerR)
                            )
                        )
                    }
                    drawPath(path = path, color = activeColor)
                }

                if (inactiveLeft < size.width - 1f) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                left = inactiveLeft,
                                top = 0f,
                                right = size.width,
                                bottom = trackH,
                                topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(innerR),
                                bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(innerR),
                                topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(outerR),
                                bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(outerR)
                            )
                        )
                    }
                    drawPath(path = path, color = inactiveColor)
                }

                if (steps > 0) {
                    val totalTicks = steps + 2
                    val dotRadius = 2.dp.toPx()
                    val pad = outerR
                    val usableW = (size.width - pad * 2f).coerceAtLeast(0f)
                    for (i in 0 until totalTicks) {
                        val tickFrac = i.toFloat() / (totalTicks - 1).toFloat()
                        val tickX = pad + usableW * tickFrac
                        if (tickX > inactiveLeft + dotRadius * 2f) {
                            drawCircle(
                                color = dotColor,
                                radius = dotRadius,
                                center = Offset(tickX, trackH / 2f)
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun AccentIntensityCard(
    currentIntensity: AccentIntensity,
    onIntensityChange: (AccentIntensity) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val entries = remember { AccentIntensity.entries }
    val defaultIntensity = AccentIntensity.Vibrant
    val currentIndex = entries.indexOf(currentIntensity).coerceAtLeast(0)
    var sliderPos by remember(currentIndex) { mutableFloatStateOf(currentIndex.toFloat()) }
    val maxIdx = (entries.size - 1).coerceAtLeast(1)
    val snappedIdx = sliderPos.roundToInt().coerceIn(0, entries.lastIndex)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = persLocalizedLabel("Intensitas aksen", "Accent intensity"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                        imageVector = Icons.Rounded.History,
                        contentDescription = stringResource(R.string.action_reset),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            M3ExpressiveSlider(
                value = sliderPos.coerceIn(0f, maxIdx.toFloat()),
                onValueChange = { raw ->
                    val nextIdx = raw.roundToInt().coerceIn(0, entries.lastIndex)
                    if (nextIdx != sliderPos.roundToInt()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    sliderPos = raw
                    if (entries[nextIdx] != currentIntensity) {
                        onIntensityChange(entries[nextIdx])
                    }
                },
                valueRange = 0f..maxIdx.toFloat(),
                steps = (entries.size - 2).coerceAtLeast(0)
            )
        }
    }
}

@Composable
private fun PersNestedCardGroup(
    headerIcon: ImageVector,
    headerTitle: String,
    outerRadius: androidx.compose.ui.unit.Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            content()
        }
    }
}

@Composable
private fun PersFloatingNavSwitchCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Waves,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.personalization_floating_navbar_short),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        }
    }
}

@Composable
private fun PersCornerRadiusCard(
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

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RoundedCorner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.personalization_corner_radius),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                        imageVector = Icons.Rounded.History,
                        contentDescription = stringResource(R.string.action_reset),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            M3ExpressiveSlider(
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
                steps = stepCount
            )
        }
    }
}
