/*
 * Copyright (C) 2026-2027 KowX
 * Copyright (C) 2026-2027 Zexshia
 * Copyright (C) 2026 kimal
 *
 * Adapted from KOWX712/KernelSU (KowSU) UI, Apache License 2.0.
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import com.luminatweaks.ui.theme.ColorStyle
import com.luminatweaks.ui.theme.PageTransition
import com.luminatweaks.ui.theme.ThemeController
import com.luminatweaks.ui.theme.animateColorSchemeAsState
import com.luminatweaks.ui.theme.keyColorOptions
import com.luminatweaks.ui.theme.withAccentIntensity
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import kotlin.math.roundToInt

private enum class PreviewSoftPosition { Top, Middle, Bottom, Single }

private val paletteStyleOptions = listOf(
    ColorStyle.TonalSpot to R.string.color_style_tonal_spot,
    ColorStyle.Vibrant to R.string.color_style_vibrant,
    ColorStyle.Expressive to R.string.color_style_expressive,
    ColorStyle.Spritz to R.string.color_style_spritz,
    ColorStyle.Rainbow to R.string.color_style_rainbow,
    ColorStyle.FruitSalad to R.string.color_style_fruit_salad,
    ColorStyle.Content to R.string.color_style_content,
    ColorStyle.Monochrome to R.string.color_style_monochrome
)

private val colorSpecOptions = listOf(
    ColorEngine.MaterialExpressive to R.string.color_spec_2025,
    ColorEngine.MaterialYou to R.string.color_spec_2021
)

private val pageTransitionOptions = listOf(
    PageTransition.CrossAxis to R.string.page_transition_cross_axis,
    PageTransition.Horizontal to R.string.page_transition_horizontal,
    PageTransition.Vertical to R.string.page_transition_vertical
)

private fun ColorStyle.toPaletteStyle(): PaletteStyle = when (this.name) {
    "TonalSpot" -> PaletteStyle.TonalSpot
    "Vibrant" -> PaletteStyle.Vibrant
    "Expressive" -> PaletteStyle.Expressive
    "Spritz" -> PaletteStyle.Spritz
    "Rainbow" -> PaletteStyle.Rainbow
    "FruitSalad" -> PaletteStyle.FruitSalad
    "Content" -> PaletteStyle.Content
    else -> PaletteStyle.Monochrome
}

private fun ColorStyle.labelRes(): Int =
    paletteStyleOptions.first { it.first == this }.second

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
    var currentColorStyle by remember { mutableStateOf(savedSettings.colorStyle) }
    var currentScrollAnimation by remember { mutableStateOf(savedSettings.scrollAnimation) }
    var currentEdgeSwipeBack by remember { mutableStateOf(savedSettings.edgeSwipeBack) }
    var currentPageTransition by remember { mutableStateOf(savedSettings.pageTransition) }

    var showPaletteSheet by remember { mutableStateOf(false) }
    var showSpecSheet by remember { mutableStateOf(false) }
    var showTransitionSheet by remember { mutableStateOf(false) }

    val isDark = currentColorMode.getDarkThemeValue(isSystemInDarkTheme())
    val isAmoled = currentColorMode == ColorMode.DARKAMOLED
    val isDynamic = currentKeyColor == 0
    val paletteStyle = currentColorStyle.toPaletteStyle()

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
            style = paletteStyle
        )
    } else {
        rememberDynamicColorScheme(
            seedColor = Color(currentKeyColor),
            isDark = isDark,
            isAmoled = isAmoled,
            specVersion = currentColorSpec.librarySpec,
            style = paletteStyle
        )
    }

    val previewColorScheme = animateColorSchemeAsState(
        targetColorScheme = previewBaseScheme.withAccentIntensity(currentIntensity)
    )

    fun updateTheme(
        mode: ColorMode = currentColorMode,
        key: Int = currentKeyColor,
        spec: ColorEngine = currentColorSpec,
        intensity: AccentIntensity = currentIntensity,
        radius: Int = currentCornerRadius,
        floating: Boolean = isFloatingNavBar,
        style: ColorStyle = currentColorStyle
    ) {
        currentColorMode = mode
        currentKeyColor = key
        currentColorSpec = spec
        currentIntensity = intensity
        currentCornerRadius = radius
        isFloatingNavBar = floating
        currentColorStyle = style
        ThemeController.saveTheme(context, mode, key, spec, intensity, radius, floating)
        ThemeController.saveColorStyle(context, style)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "preview") {
                PhonePreviewCard(
                    colorScheme = previewColorScheme,
                    cornerRadius = currentCornerRadius,
                    isFloatingNavBar = isFloatingNavBar,
                    isDark = isDark
                )
            }

            item(key = "palette") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.personalization_color)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        item {
                            SwatchButton(
                                color = Color.Unspecified,
                                isSelected = currentKeyColor == 0,
                                isDark = isDark,
                                colorSpec = currentColorSpec,
                                colorStyle = currentColorStyle,
                                accentIntensity = currentIntensity,
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
                                colorStyle = currentColorStyle,
                                accentIntensity = currentIntensity,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    updateTheme(key = colorInt)
                                }
                            )
                        }
                    }
                    ModePillSelector(
                        selected = currentColorMode,
                        onSelect = { mode ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            updateTheme(mode = mode)
                        }
                    )
                }
            }

            item(key = "engine") {
                SectionHeader(
                    icon = Icons.Rounded.WaterDrop,
                    title = stringResource(R.string.personalization_color)
                )
                Spacer(modifier = Modifier.height(10.dp))
                ConnectedGroup {
                    SoftRow(
                        position = PreviewSoftPosition.Top,
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.personalization_palette_style),
                        subtitle = stringResource(
                            R.string.personalization_palette_style_desc
                        ).lowercase().replaceFirstChar { it.uppercase() },
                        trailingText = stringResource(currentColorStyle.labelRes()),
                        onClick = { showPaletteSheet = true }
                    )
                    SoftRow(
                        position = PreviewSoftPosition.Bottom,
                        icon = Icons.Rounded.WaterDrop,
                        title = stringResource(R.string.personalization_color_spec),
                        subtitle = stringResource(
                            R.string.personalization_color_spec_desc
                        ).lowercase().replaceFirstChar { it.uppercase() },
                        trailingText = stringResource(
                            if (currentColorSpec == ColorEngine.MaterialExpressive) {
                                R.string.color_spec_2025
                            } else {
                                R.string.color_spec_2021
                            }
                        ),
                        onClick = { showSpecSheet = true }
                    )
                }
            }

            item(key = "nav_animation") {
                SectionHeader(
                    icon = Icons.Rounded.Waves,
                    title = stringResource(R.string.personalization_nav_animation)
                )
                Spacer(modifier = Modifier.height(10.dp))
                ConnectedGroup {
                    SoftToggleRow(
                        position = PreviewSoftPosition.Top,
                        icon = Icons.Rounded.Waves,
                        title = stringResource(R.string.personalization_floating_navbar_short),
                        subtitle = stringResource(R.string.personalization_floating_navbar_desc),
                        checked = isFloatingNavBar,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            updateTheme(floating = checked)
                        }
                    )
                    SoftToggleRow(
                        position = PreviewSoftPosition.Middle,
                        icon = Icons.Rounded.MotionPhotosOn,
                        title = stringResource(R.string.personalization_scroll_animation),
                        subtitle = stringResource(R.string.personalization_scroll_animation_desc),
                        checked = currentScrollAnimation,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentScrollAnimation = checked
                            ThemeController.saveScrollAnimation(context, checked)
                        }
                    )
                    SoftToggleRow(
                        position = PreviewSoftPosition.Middle,
                        icon = Icons.Rounded.SwipeRight,
                        title = stringResource(R.string.personalization_edge_swipe_back),
                        subtitle = stringResource(R.string.personalization_edge_swipe_back_desc),
                        checked = currentEdgeSwipeBack,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentEdgeSwipeBack = checked
                            ThemeController.saveEdgeSwipeBack(context, checked)
                        }
                    )
                    SoftRow(
                        position = PreviewSoftPosition.Bottom,
                        icon = Icons.Rounded.SwipeRight,
                        title = stringResource(R.string.personalization_page_transition),
                        subtitle = stringResource(
                            when (currentPageTransition) {
                                PageTransition.CrossAxis -> R.string.page_transition_cross_axis
                                PageTransition.Horizontal -> R.string.page_transition_horizontal
                                PageTransition.Vertical -> R.string.page_transition_vertical
                            }
                        ),
                        trailingText = stringResource(
                            when (currentPageTransition) {
                                PageTransition.CrossAxis -> R.string.page_transition_cross_axis
                                PageTransition.Horizontal -> R.string.page_transition_horizontal
                                PageTransition.Vertical -> R.string.page_transition_vertical
                            }
                        ),
                        onClick = { showTransitionSheet = true }
                    )
                }
            }

            item(key = "corner_radius") {
                CornerRadiusCard(
                    radius = currentCornerRadius,
                    onRadiusChange = { radius ->
                        if (radius != currentCornerRadius) {
                            updateTheme(radius = radius)
                        }
                    }
                )
            }
        }
    }

    if (showPaletteSheet) {
        OptionBottomSheet(
            title = stringResource(R.string.personalization_dialog_pick_palette),
            options = paletteStyleOptions.map { it.second },
            selectedIndex = paletteStyleOptions.indexOfFirst { it.first == currentColorStyle },
            onSelect = { index ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                updateTheme(style = paletteStyleOptions[index].first)
                showPaletteSheet = false
            },
            onDismiss = { showPaletteSheet = false }
        )
    }

    if (showSpecSheet) {
        OptionBottomSheet(
            title = stringResource(R.string.personalization_dialog_pick_spec),
            options = colorSpecOptions.map { it.second },
            selectedIndex = colorSpecOptions.indexOfFirst { it.first == currentColorSpec },
            onSelect = { index ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                updateTheme(spec = colorSpecOptions[index].first)
                showSpecSheet = false
            },
            onDismiss = { showSpecSheet = false }
        )
    }

    if (showTransitionSheet) {
        OptionBottomSheet(
            title = stringResource(R.string.personalization_dialog_pick_transition),
            options = pageTransitionOptions.map { it.second },
            selectedIndex = pageTransitionOptions.indexOfFirst { it.first == currentPageTransition },
            onSelect = { index ->
                val transition = pageTransitionOptions[index].first
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentPageTransition = transition
                ThemeController.savePageTransition(context, transition)
                showTransitionSheet = false
            },
            onDismiss = { showTransitionSheet = false }
        )
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ConnectedGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        content()
    }
}

private fun softShape(position: PreviewSoftPosition, outerRadius: Int) = RoundedCornerShape(
    topStart = when (position) {
        PreviewSoftPosition.Top, PreviewSoftPosition.Single -> outerRadius
        else -> (outerRadius - 7).coerceAtLeast(7)
    }.dp,
    topEnd = when (position) {
        PreviewSoftPosition.Top, PreviewSoftPosition.Single -> outerRadius
        else -> (outerRadius - 7).coerceAtLeast(7)
    }.dp,
    bottomStart = when (position) {
        PreviewSoftPosition.Bottom, PreviewSoftPosition.Single -> outerRadius
        else -> (outerRadius - 7).coerceAtLeast(7)
    }.dp,
    bottomEnd = when (position) {
        PreviewSoftPosition.Bottom, PreviewSoftPosition.Single -> outerRadius
        else -> (outerRadius - 7).coerceAtLeast(7)
    }.dp
)

@Composable
private fun SoftRow(
    position: PreviewSoftPosition,
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String?,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "softRowScale"
    )
    val outerRadius = 26

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = softShape(position, outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingText != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftToggleRow(
    position: PreviewSoftPosition,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val outerRadius = 26
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "softToggleContainer"
    )

    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = softShape(position, outerRadius),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun CornerRadiusCard(
    radius: Int,
    onRadiusChange: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var sliderRadius by remember(radius) { mutableFloatStateOf(radius.toFloat()) }
    val displayedRadius = sliderRadius.roundToInt()
    val valueBadge by animateDpAsState(
        targetValue = (displayedRadius.coerceAtLeast(8)).dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "radiusBadge"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.RoundedCorner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.personalization_corner_radius),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.personalization_corner_radius_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "$displayedRadius dp",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        ExpressiveRadiusSlider(
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
                onRadiusChange(rounded)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${ThemeController.MIN_CORNER_RADIUS} dp",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            if (displayedRadius != ThemeController.DEFAULT_CORNER_RADIUS) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        sliderRadius = ThemeController.DEFAULT_CORNER_RADIUS.toFloat()
                        onRadiusChange(ThemeController.DEFAULT_CORNER_RADIUS)
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.action_reset),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_reset),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = "${ThemeController.MAX_CORNER_RADIUS} dp",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(valueBadge)
        )
    }
}

@Composable
private fun ExpressiveRadiusSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val activeTick = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
    val inactiveTick = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
    val trackColor = MaterialTheme.colorScheme.secondaryContainer
    val tickRadius = with(LocalDensity.current) { 1.6.dp.toPx() }
    val tickHeight = with(LocalDensity.current) { 10.dp.toPx() }
    val minValue = ThemeController.MIN_CORNER_RADIUS.toFloat()
    val maxValue = ThemeController.MAX_CORNER_RADIUS.toFloat()
    val tickCount = ((ThemeController.MAX_CORNER_RADIUS - ThemeController.MIN_CORNER_RADIUS) / 2)
    val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = minValue..maxValue,
        modifier = Modifier.fillMaxWidth(),
        track = {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val centerY = size.height / 2f
                val activeWidth = size.width * fraction
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, centerY - tickHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width, tickHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(tickHeight / 2f)
                )
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(0f, centerY - tickHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(activeWidth, tickHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(tickHeight / 2f)
                    )
                }
                val step = if (tickCount > 0) size.width / (tickCount + 1) else size.width
                for (index in 0..tickCount) {
                    val x = step * (index + 1)
                    drawCircle(
                        color = if (x <= activeWidth) activeTick else inactiveTick,
                        radius = tickRadius,
                        center = Offset(x, centerY)
                    )
                }
            }
        },
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        },
        colors = SliderDefaults.colors(
            thumbColor = Color.Transparent,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent
        )
    )
}

@Composable
private fun ModePillSelector(
    selected: ColorMode,
    onSelect: (ColorMode) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val modes = listOf(
        ColorMode.SYSTEM to (Icons.Rounded.Brightness4 to R.string.mode_system),
        ColorMode.LIGHT to (Icons.Rounded.Brightness7 to R.string.mode_light),
        ColorMode.DARK to (Icons.Rounded.Brightness3 to R.string.mode_dark),
        ColorMode.DARKAMOLED to (Icons.Rounded.Brightness1 to R.string.mode_amoled)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { (mode, pair) ->
            val (icon, labelRes) = pair
            val isSelected = selected == mode
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val pressScale by animateFloatAsState(
                targetValue = if (pressed) 0.92f else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "modePressScale"
            )
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "modeContainer"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "modeContent"
            )

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(mode)
                },
                shape = CircleShape,
                color = containerColor,
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(labelRes),
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = expandHorizontally(
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                            ) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                            exit = shrinkHorizontally(
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                            ) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionBottomSheet(
    title: String,
    options: List<Int>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            options.forEachIndexed { index, labelRes ->
                val isSelected = index == selectedIndex
                SegmentedListItem(
                    selected = isSelected,
                    onClick = { onSelect(index) },
                    shapes = ListItemDefaults.segmentedShapes(
                        index = index,
                        count = options.size
                    ),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    leadingContent = {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PhonePreviewCard(
    colorScheme: ColorScheme,
    cornerRadius: Int,
    isFloatingNavBar: Boolean,
    isDark: Boolean
) {
    val frameColor by animateColorAsState(
        targetValue = if (isDark) {
            colorScheme.surfaceContainerHighest
        } else {
            colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "previewFrame"
    )
    val borderColor by animateColorAsState(
        targetValue = colorScheme.outlineVariant.copy(alpha = 0.45f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "previewBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.requiredSize(width = 156.dp, height = 300.dp),
            shape = RoundedCornerShape(
                (cornerRadius.coerceAtLeast(8) / 2 + 20).dp
            ),
            color = frameColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 6.dp
        ) {
            AnimatedContent(
                targetState = colorScheme,
                transitionSpec = {
                    fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) togetherWith
                        fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
                },
                label = "previewContent"
            ) { scheme ->
                MockLuminaScreen(
                    colorScheme = scheme,
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
    cornerRadius: Int,
    isFloatingNavBar: Boolean,
    modifier: Modifier = Modifier
) {
    val ratioDenominator = 18f
    val animatedRadius by animateDpAsState(
        targetValue = cornerRadius.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "mockCornerRadius"
    )
    val ratio = (animatedRadius.value / ratioDenominator).coerceIn(0.2f, 2f)

    MaterialTheme(colorScheme = colorScheme) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape((10f * ratio).dp.coerceAtLeast(6.dp)),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f))
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape((8f * ratio).dp.coerceAtLeast(5.dp)))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape((8f * ratio).dp.coerceAtLeast(5.dp)))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape((12f * ratio).dp.coerceAtLeast(8.dp)))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index == 2) 0.6f else 1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .size(width = 34.dp, height = 14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isFloatingNavBar,
                    enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        scaleIn(initialScale = 0.9f),
                    exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        scaleOut(targetScale = 0.9f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 3.dp,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(width = 16.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                )
                            }
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !isFloatingNavBar,
                enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandHorizontally(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkHorizontally(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 18.dp, height = 10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
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
    colorStyle: ColorStyle,
    accentIntensity: AccentIntensity,
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
            style = colorStyle.toPaletteStyle()
        )
    } else {
        rememberDynamicColorScheme(
            seedColor = color,
            isDark = isDark,
            specVersion = colorSpec.librarySpec,
            style = colorStyle.toPaletteStyle()
        )
    }

    val colorScheme = animateColorSchemeAsState(
        targetColorScheme = targetScheme.withAccentIntensity(accentIntensity)
    )

    val ringWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchRing"
    )
    val ringColor by animateColorAsState(
        targetValue = colorScheme.primary,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "swatchRingColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchScale"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .border(ringWidth, ringColor, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
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
                if (color != Color.Unspecified) {
                    drawCircle(color = colorScheme.primary, radius = size.minDimension * 0.22f)
                }
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleIn(
                        initialScale = 0.7f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                    ),
                exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    scaleOut(targetScale = 0.7f)
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
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}