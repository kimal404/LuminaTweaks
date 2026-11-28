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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.luminaHazeHeader
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.theme.AccentIntensity
import com.luminatweaks.ui.theme.ColorEngine
import com.luminatweaks.ui.theme.ColorMode
import com.luminatweaks.ui.theme.HapticSettings
import com.luminatweaks.ui.theme.LocalHapticConfig
import com.luminatweaks.ui.theme.LuminaArtworkManager
import com.luminatweaks.ui.theme.LuminaArtworkState
import com.luminatweaks.ui.theme.LuminaHapticEngine
import com.luminatweaks.ui.theme.LuminaHapticPulse
import com.luminatweaks.ui.theme.ThemeController
import com.luminatweaks.ui.theme.animateColorSchemeAsState
import com.luminatweaks.ui.theme.keyColorOptions
import com.luminatweaks.ui.theme.rememberLuminaArtworkState
import com.luminatweaks.ui.theme.withAccentIntensity
import com.materialkolor.rememberDynamicColorScheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val savedSettings = remember { ThemeController.getAppSettings(context) }
    val artworkState = rememberLuminaArtworkState()

    var currentColorMode by remember { mutableStateOf(savedSettings.colorMode) }
    var currentKeyColor by remember { mutableIntStateOf(savedSettings.keyColor) }
    var currentColorSpec by remember { mutableStateOf(savedSettings.colorSpec) }
    var currentIntensity by remember { mutableStateOf(savedSettings.accentIntensity) }
    var currentCornerRadius by remember { mutableIntStateOf(savedSettings.cornerRadius) }
    var isFloatingNavBar by remember { mutableStateOf(savedSettings.floatingNavBar) }
    var hapticEnabled by remember { mutableStateOf(savedSettings.hapticEnabled) }
    var hapticIntensity by remember { mutableFloatStateOf(savedSettings.hapticIntensity) }

    var headerPreviewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artworkState.revision, artworkState.hasImage) {
        headerPreviewBitmap = if (artworkState.hasImage) {
            withContext(Dispatchers.IO) {
                LuminaArtworkManager.loadBitmap(context)
            }
        } else {
            null
        }
    }

    val headerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                LuminaArtworkManager.saveFromUri(context, uri)
            }
        }
    }

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
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .luminaHazeHeader(
                        hazeState = hazeState,
                        surfaceColor = fadeSurface,
                        overlappedFraction = scrollBehavior.state.overlappedFraction
                    )
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
            modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
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

            item(key = "custom_artwork_section") {
                Text(
                    text = persLocalizedLabel("Ilustrasi Header", "Header Artwork"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)
                )
                HeaderBannerStudioCard(
                    state = artworkState,
                    bitmap = headerPreviewBitmap,
                    onToggle = { checked ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (checked && !artworkState.hasImage) {
                            headerPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            LuminaArtworkManager.setEnabled(context, checked)
                        }
                    },
                    onPickImage = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        headerPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onClearImage = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        LuminaArtworkManager.clear(context)
                    },
                    onAlphaChange = { LuminaArtworkManager.setAlpha(context, it) },
                    onTransformChange = { x, y, z ->
                        LuminaArtworkManager.updateTransform(context, x, y, z)
                    },
                    onHeightChange = { LuminaArtworkManager.setBannerHeight(context, it) },
                    onFadeChange = { LuminaArtworkManager.setFadeStrength(context, it) },
                    onBlurChange = { LuminaArtworkManager.setBlurDp(context, it) },
                    onResetTransform = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        LuminaArtworkManager.resetTransform(context)
                    }
                )
            }

            item(key = "haptic_feedback_section") {
                Text(
                    text = persLocalizedLabel("Umpan Balik Haptik", "Haptic Feedback"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)
                )
                PersHapticFeedbackCard(
                    enabled = hapticEnabled,
                    intensity = hapticIntensity,
                    onEnabledChange = { checked ->
                        hapticEnabled = checked
                        ThemeController.saveHapticSettings(context, checked, hapticIntensity)
                        if (checked) {
                            LuminaHapticEngine.perform(
                                context = context,
                                settings = HapticSettings(enabled = true, intensity = hapticIntensity),
                                pulse = LuminaHapticPulse.Click
                            )
                        }
                    },
                    onIntensityChange = { newIntensity ->
                        hapticIntensity = newIntensity
                        ThemeController.saveHapticSettings(context, hapticEnabled, newIntensity)
                        if (hapticEnabled) {
                            LuminaHapticEngine.perform(
                                context = context,
                                settings = HapticSettings(enabled = true, intensity = newIntensity),
                                pulse = LuminaHapticPulse.Tick
                            )
                        }
                    }
                )
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
private fun HeaderBannerStudioCard(
    state: LuminaArtworkState,
    bitmap: ImageBitmap?,
    onToggle: (Boolean) -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onAlphaChange: (Float) -> Unit,
    onTransformChange: (Float, Float, Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onFadeChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    onResetTransform: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.985f)

    var showFineTuneSheet by remember { mutableStateOf(false) }
    var alphaVal by remember(state.alpha) { mutableFloatStateOf(state.alpha) }
    var zoomVal by remember(state.zoom) { mutableFloatStateOf(state.zoom) }
    var offsetXVal by remember(state.offsetX) { mutableFloatStateOf(state.offsetX) }
    var offsetYVal by remember(state.offsetY) { mutableFloatStateOf(state.offsetY) }
    var heightVal by remember(state.bannerHeightDp) { mutableFloatStateOf(state.bannerHeightDp) }
    var fadeVal by remember(state.fadeStrength) { mutableFloatStateOf(state.fadeStrength) }
    var blurVal by remember(state.blurDp) { mutableFloatStateOf(state.blurDp) }

    if (showFineTuneSheet && state.enabled && bitmap != null) {
        BannerFineTuneSheet(
            zoom = zoomVal,
            bannerHeightDp = heightVal,
            fadeStrength = fadeVal,
            blurDp = blurVal,
            onDismiss = { showFineTuneSheet = false },
            onZoomChange = { raw ->
                zoomVal = raw
                onTransformChange(offsetXVal, offsetYVal, raw)
            },
            onHeightChange = { raw ->
                heightVal = raw
                onHeightChange(raw)
            },
            onFadeChange = { raw ->
                fadeVal = raw
                onFadeChange(raw)
            },
            onBlurChange = { raw ->
                blurVal = raw
                onBlurChange(raw)
            },
            onReset = {
                offsetXVal = 0f
                offsetYVal = 0f
                zoomVal = LuminaArtworkManager.DEFAULT_ZOOM
                heightVal = LuminaArtworkManager.DEFAULT_HEIGHT_DP
                fadeVal = LuminaArtworkManager.DEFAULT_FADE
                blurVal = 0f
                onResetTransform()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceColorAtElevation(1.dp))
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(scaleModifier)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onToggle(!state.enabled) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, scheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                )
            } else {
                PersSquircleIcon(icon = Icons.Rounded.Wallpaper)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = persLocalizedLabel("Banner Header Beranda", "Home Header Banner"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = persLocalizedLabel(
                        "Ilustrasi lembut di balik area atas beranda",
                        "Soft illustration behind the home top header"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = state.enabled,
                onCheckedChange = onToggle,
                interactionSource = interactionSource,
                thumbContent = {
                    if (state.enabled) {
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

        AnimatedVisibility(visible = state.enabled && bitmap != null) {
            if (bitmap != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeaderBannerLivePreview(
                        bitmap = bitmap,
                        alpha = alphaVal,
                        offsetX = offsetXVal,
                        offsetY = offsetYVal,
                        zoom = zoomVal,
                        bannerHeightDp = heightVal,
                        fadeStrength = fadeVal,
                        blurDp = blurVal,
                        onGestureTransform = { newX, newY, newZoom ->
                            offsetXVal = newX
                            offsetYVal = newY
                            zoomVal = newZoom
                            onTransformChange(newX, newY, newZoom)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenWith,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = persLocalizedLabel(
                                    "Geser & cubit preview untuk atur posisi",
                                    "Drag & pinch preview to adjust framing"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                offsetXVal = 0f
                                offsetYVal = 0f
                                zoomVal = LuminaArtworkManager.DEFAULT_ZOOM
                                heightVal = LuminaArtworkManager.DEFAULT_HEIGHT_DP
                                fadeVal = LuminaArtworkManager.DEFAULT_FADE
                                blurVal = 0f
                                onResetTransform()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Restore,
                                contentDescription = stringResource(R.string.action_reset),
                                tint = scheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    BannerSliderControl(
                        label = persLocalizedLabel("Intensitas gambar", "Image opacity"),
                        valueText = "${(alphaVal * 100f).roundToInt()}%",
                        value = alphaVal,
                        range = LuminaArtworkManager.MIN_ALPHA..LuminaArtworkManager.MAX_ALPHA,
                        onValueChange = { raw ->
                            val prev = (alphaVal * 100f).roundToInt()
                            val next = (raw * 100f).roundToInt()
                            if (next != prev && next % 5 == 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            alphaVal = raw
                            onAlphaChange(raw)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(scheme.secondaryContainer)
                    .clickable(onClick = onPickImage)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = scheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (state.hasImage) {
                        persLocalizedLabel("Ganti gambar", "Change image")
                    } else {
                        persLocalizedLabel("Pilih dari galeri", "Pick from gallery")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSecondaryContainer
                )
            }

            if (state.enabled && bitmap != null) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHighest)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFineTuneSheet = true
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = scheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = persLocalizedLabel("Atur gaya", "Fine-tune"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.hasImage) {
                IconButton(
                    onClick = onClearImage,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = persLocalizedLabel("Hapus gambar", "Remove image"),
                        tint = scheme.error,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerFineTuneSheet(
    zoom: Float,
    bannerHeightDp: Float,
    fadeStrength: Float,
    blurDp: Float,
    onDismiss: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onFadeChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = persLocalizedLabel("Atur Gaya Banner", "Banner Style & Scale"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = persLocalizedLabel(
                            "Posisi X/Y diatur langsung dengan menggeser preview",
                            "Drag the preview directly to adjust X/Y framing"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = stringResource(R.string.action_reset),
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            BannerSliderControl(
                label = persLocalizedLabel("Perbesaran (Zoom)", "Zoom scale"),
                valueText = String.format(Locale.US, "%.2fx", zoom),
                value = zoom,
                range = LuminaArtworkManager.MIN_ZOOM..LuminaArtworkManager.MAX_ZOOM,
                onValueChange = onZoomChange
            )

            BannerSliderControl(
                label = persLocalizedLabel("Tinggi area banner", "Banner height"),
                valueText = "${bannerHeightDp.roundToInt()} dp",
                value = bannerHeightDp,
                range = LuminaArtworkManager.MIN_HEIGHT_DP..LuminaArtworkManager.MAX_HEIGHT_DP,
                onValueChange = onHeightChange
            )

            BannerSliderControl(
                label = persLocalizedLabel("Kehalusan pudar", "Bottom fade"),
                valueText = "${(fadeStrength * 100f).roundToInt()}%",
                value = fadeStrength,
                range = LuminaArtworkManager.MIN_FADE..LuminaArtworkManager.MAX_FADE,
                onValueChange = onFadeChange
            )

            BannerSliderControl(
                label = persLocalizedLabel("Blur latar", "Backdrop blur"),
                valueText = "${blurDp.roundToInt()} dp",
                value = blurDp,
                range = LuminaArtworkManager.MIN_BLUR_DP..LuminaArtworkManager.MAX_BLUR_DP,
                onValueChange = onBlurChange
            )
        }
    }
}

@Composable
private fun HeaderBannerLivePreview(
    bitmap: ImageBitmap,
    alpha: Float,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    bannerHeightDp: Float,
    fadeStrength: Float,
    blurDp: Float,
    onGestureTransform: (Float, Float, Float) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val surfaceColor = scheme.background
    val isDark = surfaceColor.luminance() < 0.5f
    val effectiveAlpha = if (isDark) alpha * 0.88f else alpha
    val fade = fadeStrength.coerceIn(0.2f, 1.0f)
    val clearStop = (0.48f - fade * 0.28f).coerceIn(0.15f, 0.45f)
    val midStop = (0.78f - fade * 0.20f).coerceIn(clearStop + 0.12f, 0.76f)
    val endStop = (0.98f - fade * 0.10f).coerceIn(midStop + 0.10f, 0.98f)

    val previewHeightDp = (bannerHeightDp * 0.68f).coerceIn(136f, 224f).dp
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val curX by rememberUpdatedState(offsetX)
    val curY by rememberUpdatedState(offsetY)
    val curZoom by rememberUpdatedState(zoom)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(previewHeightDp)
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val w = boxSize.width.coerceAtLeast(1).toFloat()
                    val h = boxSize.height.coerceAtLeast(1).toFloat()
                    val nextZoom = (curZoom * zoomChange).coerceIn(
                        LuminaArtworkManager.MIN_ZOOM,
                        LuminaArtworkManager.MAX_ZOOM
                    )
                    val nextX = (curX + pan.x / (w * 0.35f)).coerceIn(-1f, 1f)
                    val nextY = (curY + pan.y / (h * 0.40f)).coerceIn(-1f, 1f)
                    onGestureTransform(nextX, nextY, nextZoom)
                }
            }
            .clipToBounds()
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            alpha = effectiveAlpha,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (blurDp > 0.5f) {
                        Modifier.blur((blurDp * 0.75f).dp)
                    } else {
                        Modifier
                    }
                )
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offsetX * size.width * 0.35f
                    translationY = offsetY * size.height * 0.40f
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to surfaceColor.copy(alpha = 0.22f),
                        clearStop to Color.Transparent,
                        midStop to surfaceColor.copy(alpha = 0.58f),
                        endStop to surfaceColor.copy(alpha = 0.94f),
                        1.0f to surfaceColor
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LuminaTweaks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface
            )
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun BannerSliderControl(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
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
                specVersion = colorSpec.librarySpec
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

@Composable
private fun PersHapticFeedbackCard(
    enabled: Boolean,
    intensity: Float,
    onEnabledChange: (Boolean) -> Unit,
    onIntensityChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val currentConfig = LocalHapticConfig.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f)
    val minVal = ThemeController.MIN_HAPTIC_INTENSITY
    val maxVal = ThemeController.MAX_HAPTIC_INTENSITY
    var sliderPos by remember(intensity) { mutableFloatStateOf(intensity.coerceIn(minVal, maxVal)) }
    val pct = (sliderPos * 100f).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(pressScaleModifier)
                .clip(RoundedCornerShape(22.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onEnabledChange(!enabled) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersSquircleIcon(icon = Icons.Rounded.Vibration)
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = persLocalizedLabel("Getaran UI Global", "Global UI Haptics"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = persLocalizedLabel(
                        "Terapkan toggle & kekuatan getaran ke seluruh layar",
                        "Apply toggle & strength across all screens"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                interactionSource = interactionSource,
                thumbContent = {
                    if (enabled) {
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = persLocalizedLabel("Kekuatan getaran", "Haptic strength"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$pct%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        val defVal = ThemeController.DEFAULT_HAPTIC_INTENSITY
                        sliderPos = defVal
                        onIntensityChange(defVal)
                        LuminaHapticEngine.perform(
                            context = context,
                            settings = HapticSettings(enabled = true, intensity = defVal),
                            pulse = LuminaHapticPulse.Heavy
                        )
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
                value = sliderPos,
                enabled = enabled,
                onValueChange = { raw ->
                    val stepped = ((raw * 10f).roundToInt() / 10f).coerceIn(minVal, maxVal)
                    if (stepped != sliderPos) {
                        sliderPos = stepped
                        onIntensityChange(stepped)
                    }
                },
                valueRange = minVal..maxVal,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
