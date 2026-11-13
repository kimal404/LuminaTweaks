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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import kotlin.math.roundToInt

private enum class ItemSegment { Top, Middle, Bottom, Single }

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

private fun ColorStyle.toPaletteStyle(): PaletteStyle = when (this) {
    ColorStyle.TonalSpot -> PaletteStyle.TonalSpot
    ColorStyle.Vibrant -> PaletteStyle.Vibrant
    ColorStyle.Expressive -> PaletteStyle.Expressive
    ColorStyle.Spritz -> PaletteStyle.Neutral
    ColorStyle.Rainbow -> PaletteStyle.Rainbow
    ColorStyle.FruitSalad -> PaletteStyle.FruitSalad
    ColorStyle.Content -> PaletteStyle.Content
    ColorStyle.Monochrome -> PaletteStyle.Monochrome
}

private fun ColorStyle.labelRes(): Int =
    paletteStyleOptions.first { it.first == this }.second

private fun segmentShape(segment: ItemSegment): RoundedCornerShape {
    val outer = 20.dp
    val inner = 6.dp
    return when (segment) {
        ItemSegment.Top -> RoundedCornerShape(
            topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner
        )
        ItemSegment.Middle -> RoundedCornerShape(inner)
        ItemSegment.Bottom -> RoundedCornerShape(
            topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer
        )
        ItemSegment.Single -> RoundedCornerShape(outer)
    }
}

@Composable
fun PersonalisasiScreen(
    onBackClick: () -> Unit
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
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

    fun buildScheme(seed: Int): ColorScheme {
        val seedColor = if (seed == 0) {
            val base = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                else ->
                    if (isDark) darkColorScheme() else lightColorScheme()
            }
            base.primary
        } else {
            Color(seed)
        }
        return rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = isDark,
            isAmoled = isAmoled,
            specVersion = currentColorSpec.librarySpec,
            style = currentColorStyle.toPaletteStyle()
        ).withAccentIntensity(currentIntensity)
    }

    val previewColorScheme = animateColorSchemeAsState(
        targetColorScheme = buildScheme(currentKeyColor)
    )

    fun updateTheme(
        mode: ColorMode = currentColorMode,
        key: Int = currentKeyColor,
        spec: ColorEngine = currentColorSpec,
        radius: Int = currentCornerRadius,
        floating: Boolean = isFloatingNavBar,
        style: ColorStyle = currentColorStyle
    ) {
        currentColorMode = mode
        currentKeyColor = key
        currentColorSpec = spec
        currentCornerRadius = radius
        isFloatingNavBar = floating
        currentColorStyle = style
        ThemeController.saveTheme(context, mode, key, spec, currentIntensity, radius, floating)
        ThemeController.saveColorStyle(context, style)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.personalization_title),
                        fontWeight = FontWeight.Normal
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val extraBottom = if (isFloatingNavBar) 96.dp else 20.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = extraBottom + navBottom
            ),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item(key = "preview") {
                ThemePreviewCard(
                    colorScheme = previewColorScheme,
                    cornerRadius = currentCornerRadius,
                    isFloatingNavBar = isFloatingNavBar
                )
            }

            item(key = "swatches") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "dynamic") {
                        ColorSwatchTile(
                            color = Color.Unspecified,
                            isSelected = currentKeyColor == 0,
                            isDark = isDark,
                            isAmoled = isAmoled,
                            paletteStyle = currentColorStyle.toPaletteStyle(),
                            colorSpec = currentColorSpec.librarySpec,
                            accentIntensity = currentIntensity,
                            onClick = { updateTheme(key = 0) }
                        )
                    }
                    items(keyColorOptions, key = { it }) { colorInt ->
                        ColorSwatchTile(
                            color = Color(colorInt),
                            isSelected = currentKeyColor == colorInt,
                            isDark = isDark,
                            isAmoled = isAmoled,
                            paletteStyle = currentColorStyle.toPaletteStyle(),
                            colorSpec = currentColorSpec.librarySpec,
                            accentIntensity = currentIntensity,
                            onClick = { updateTheme(key = colorInt) }
                        )
                    }
                }
            }

            item(key = "modes") {
                val modes = listOf(
                    ColorMode.SYSTEM to (Icons.Rounded.Brightness4 to stringResource(R.string.mode_system)),
                    ColorMode.LIGHT to (Icons.Rounded.Brightness7 to stringResource(R.string.mode_light)),
                    ColorMode.DARK to (Icons.Rounded.Brightness3 to stringResource(R.string.mode_dark)),
                    ColorMode.DARKAMOLED to (Icons.Rounded.Brightness1 to stringResource(R.string.mode_amoled))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    modes.forEachIndexed { index, (mode, iconAndLabel) ->
                        val (icon, contentDesc) = iconAndLabel
                        val selected = currentColorMode == mode
                        val shape = when (index) {
                            0 -> RoundedCornerShape(
                                topStart = 21.dp, bottomStart = 21.dp, topEnd = 8.dp, bottomEnd = 8.dp
                            )
                            modes.lastIndex -> RoundedCornerShape(
                                topStart = 8.dp, bottomStart = 8.dp, topEnd = 21.dp, bottomEnd = 21.dp
                            )
                            else -> RoundedCornerShape(8.dp)
                        }
                        val bgColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "modeTileBg"
                        )
                        val iconTint by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "modeTileTint"
                        )

                        Surface(
                            onClick = {
                                if (!selected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    updateTheme(mode = mode)
                                }
                            },
                            shape = shape,
                            color = bgColor,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .semantics { role = Role.RadioButton }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = contentDesc,
                                    tint = iconTint,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "color_engine") {
                SegmentedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SegmentedValueItem(
                        segment = ItemSegment.Top,
                        icon = Icons.Rounded.Style,
                        title = stringResource(R.string.personalization_palette_style),
                        valueText = stringResource(currentColorStyle.labelRes()),
                        onClick = { showPaletteSheet = true }
                    )
                    SegmentedValueItem(
                        segment = ItemSegment.Bottom,
                        icon = Icons.Rounded.DesignServices,
                        title = stringResource(R.string.personalization_color_spec),
                        valueText = stringResource(
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

            item(key = "navigation_and_gesture") {
                SegmentedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SegmentedToggleItem(
                        segment = ItemSegment.Top,
                        icon = Icons.Rounded.Waves,
                        title = stringResource(R.string.personalization_floating_navbar_short),
                        checked = isFloatingNavBar,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            updateTheme(floating = checked)
                        }
                    )
                    SegmentedToggleItem(
                        segment = ItemSegment.Middle,
                        icon = Icons.Rounded.ViewCarousel,
                        title = stringResource(R.string.personalization_scroll_animation),
                        checked = currentScrollAnimation,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentScrollAnimation = checked
                            ThemeController.saveScrollAnimation(context, checked)
                        }
                    )
                    SegmentedToggleItem(
                        segment = ItemSegment.Middle,
                        icon = Icons.Rounded.Swipe,
                        title = stringResource(R.string.personalization_edge_swipe_back),
                        summary = stringResource(R.string.personalization_edge_swipe_back_desc),
                        checked = currentEdgeSwipeBack,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentEdgeSwipeBack = checked
                            ThemeController.saveEdgeSwipeBack(context, checked)
                        }
                    )
                    SegmentedValueItem(
                        segment = ItemSegment.Bottom,
                        icon = Icons.Rounded.ViewCarousel,
                        title = stringResource(R.string.personalization_page_transition),
                        valueText = stringResource(
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
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    if (showPaletteSheet) {
        SelectionBottomSheet(
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
        SelectionBottomSheet(
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
        SelectionBottomSheet(
            title = stringResource(R.string.personalization_dialog_pick_transition),
            options = pageTransitionOptions.map { it.second },
            selectedIndex = pageTransitionOptions.indexOfFirst { it.first == currentPageTransition },
            onSelect = { index ->
                val chosen = pageTransitionOptions[index].first
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentPageTransition = chosen
                ThemeController.savePageTransition(context, chosen)
                showTransitionSheet = false
            },
            onDismiss = { showTransitionSheet = false }
        )
    }
}

@Composable
private fun ThemePreviewCard(
    colorScheme: ColorScheme,
    cornerRadius: Int,
    isFloatingNavBar: Boolean
) {
    val animatedCardRadius by animateDpAsState(
        targetValue = (cornerRadius * 0.45f).coerceIn(4f, 16f).dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "previewCardRadius"
    )
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .aspectRatio(0.48f),
            color = colorScheme.surfaceContainer,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(animatedCardRadius),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {}
                    Surface(
                        color = colorScheme.surfaceBright,
                        shape = RoundedCornerShape(animatedCardRadius),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {}
                }

                AnimatedContent(
                    targetState = isFloatingNavBar,
                    transitionSpec = {
                        (fadeIn(animationSpec = effectsSpec) +
                            scaleIn(initialScale = 0.92f, animationSpec = spatialSpec)) togetherWith
                            (fadeOut(animationSpec = effectsSpec) +
                                scaleOut(targetScale = 0.92f))
                    },
                    label = "previewNavMode"
                ) { floating ->
                    if (floating) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.surfaceContainerHighest,
                                tonalElevation = 3.dp,
                                shadowElevation = 3.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 22.dp, height = 10.dp)
                                            .clip(CircleShape)
                                            .background(colorScheme.primaryContainer)
                                    )
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(40.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Home,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
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
private fun ColorSwatchTile(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    isAmoled: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
    accentIntensity: AccentIntensity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val targetScheme = if (color == Color.Unspecified) {
        val base = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else ->
                if (isDark) darkColorScheme() else lightColorScheme()
        }
        rememberDynamicColorScheme(
            seedColor = base.primary,
            isDark = isDark,
            isAmoled = isAmoled,
            specVersion = colorSpec,
            style = paletteStyle
        )
    } else {
        rememberDynamicColorScheme(
            seedColor = color,
            isDark = isDark,
            isAmoled = isAmoled,
            specVersion = colorSpec,
            style = paletteStyle
        )
    }.withAccentIntensity(accentIntensity)

    val colorScheme = animateColorSchemeAsState(targetColorScheme = targetScheme)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchTileScale"
    )
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
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

            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(animationSpec = effectsSpec) +
                        scaleIn(initialScale = 0.8f, animationSpec = spatialSpec),
                    exit = fadeOut(animationSpec = effectsSpec) +
                        scaleOut(targetScale = 0.8f)
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

                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSelected,
                    enter = fadeIn(animationSpec = effectsSpec) +
                        scaleIn(initialScale = 0.8f, animationSpec = spatialSpec),
                    exit = fadeOut(animationSpec = effectsSpec) +
                        scaleOut(targetScale = 0.8f)
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
private fun SegmentedGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

@Composable
private fun SegmentedValueItem(
    segment: ItemSegment,
    icon: ImageVector,
    title: String,
    valueText: String,
    onClick: () -> Unit,
    summary: String? = null
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = segmentShape(segment),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SegmentedToggleItem(
    segment: ItemSegment,
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = segmentShape(segment),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (summary != null) 13.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun CornerRadiusCard(
    radius: Int,
    onRadiusChange: (Int) -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.RoundedCorner,
                    contentDescription = stringResource(R.string.personalization_corner_radius),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.personalization_corner_radius),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.personalization_corner_radius_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (displayedRadius != ThemeController.DEFAULT_CORNER_RADIUS) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            sliderValue = ThemeController.DEFAULT_CORNER_RADIUS.toFloat()
                            onRadiusChange(ThemeController.DEFAULT_CORNER_RADIUS)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.action_reset),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "$displayedRadius dp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
}

@Composable
private fun SelectionBottomSheet(
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets(0.dp) },
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
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
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = if (isSelected) {
                            MaterialTheme.typography.titleSmallEmphasized
                        } else {
                            MaterialTheme.typography.bodyLarge
                        }
                    )
                }
            }
        }
    }
}
