/*
 * Copyright (C) 2025 Zexshia
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

package com.luminatweaks.ui.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun LuminaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var themeState by remember { mutableStateOf(ThemeController.getAppSettings(context)) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            themeState = ThemeController.getAppSettings(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(themeState.colorMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = context.getSystemService(UiModeManager::class.java)
            val nightMode = when (themeState.colorMode) {
                ColorMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ColorMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ColorMode.DARK, ColorMode.DARKAMOLED -> UiModeManager.MODE_NIGHT_YES
            }
            runCatching { uiModeManager?.setApplicationNightMode(nightMode) }
        }
    }

    val configNight = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val systemDarkTheme = configNight || isSystemInDarkTheme()
    val isDark = themeState.colorMode.getDarkThemeValue(systemDarkTheme)
    val amoledMode = themeState.colorMode == ColorMode.DARKAMOLED
    val isDynamic = themeState.keyColor == 0
    val colorSpec = themeState.colorSpec.librarySpec

    val rawColorScheme: ColorScheme = if (isDynamic) {
        val baseScheme = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else ->
                if (isDark) darkColorScheme() else expressiveLightColorScheme()
        }
        rememberDynamicColorScheme(
            seedColor = baseScheme.primary,
            isDark = isDark,
            isAmoled = amoledMode,
            specVersion = colorSpec,
            primary = baseScheme.primary,
            secondary = baseScheme.secondary,
            tertiary = baseScheme.tertiary,
            neutral = baseScheme.surface,
            neutralVariant = baseScheme.surfaceVariant,
            error = baseScheme.error
        )
    } else {
        rememberDynamicColorScheme(
            seedColor = Color(themeState.keyColor),
            isDark = isDark,
            isAmoled = amoledMode,
            specVersion = colorSpec
        )
    }

    val tonedScheme = rawColorScheme
        .withLuminousSurfaceLadder(isDark = isDark, isAmoled = amoledMode)
        .withAccentIntensity(
            intensity = themeState.accentIntensity,
            isDark = isDark,
            isAmoled = amoledMode
        )

    val animatedColorScheme = animateColorSchemeAsState(targetColorScheme = tonedScheme)

    val dynamicShapes = remember(themeState.cornerRadius) {
        createLuminaShapes(themeState.cornerRadius)
    }

    val hapticSettings = remember(themeState.hapticEnabled, themeState.hapticIntensity) {
        HapticSettings(
            enabled = themeState.hapticEnabled,
            intensity = themeState.hapticIntensity
        )
    }
    val systemHaptic = LocalHapticFeedback.current
    val luminaHaptic = remember(context, systemHaptic, hapticSettings) {
        LuminaHapticFeedback(context, systemHaptic, hapticSettings)
    }

    val view = LocalView.current
    LaunchedEffect(isDark) {
        val activity = context as? ComponentActivity
        if (activity != null) {
            val barStyle = if (isDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
            runCatching {
                activity.enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }
        }
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = LuminaTypography,
        shapes = dynamicShapes
    ) {
        CompositionLocalProvider(
            LocalCornerRadius provides themeState.cornerRadius.dp,
            LocalFloatingNavBar provides themeState.floatingNavBar,
            LocalScrollAnimation provides themeState.scrollAnimation,
            LocalEdgeSwipeBack provides themeState.edgeSwipeBack,
            LocalPageTransition provides themeState.pageTransition,
            LocalHapticConfig provides hapticSettings,
            LocalHapticFeedback provides luminaHaptic,
            content = content
        )
    }
}

private fun ColorScheme.withLuminousSurfaceLadder(
    isDark: Boolean,
    isAmoled: Boolean
): ColorScheme {
    val hueAnchor = primary
    return when {
        isAmoled -> copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = hueAnchor.asTintedNeutral(0.18f, 0.14f),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = hueAnchor.asTintedNeutral(0.085f, 0.12f),
            surfaceContainer = hueAnchor.asTintedNeutral(0.12f, 0.14f),
            surfaceContainerHigh = hueAnchor.asTintedNeutral(0.16f, 0.16f),
            surfaceContainerHighest = hueAnchor.asTintedNeutral(0.205f, 0.18f),
            outlineVariant = hueAnchor.asTintedNeutral(0.28f, 0.14f)
        )
        isDark -> copy(
            background = hueAnchor.asTintedNeutral(0.115f, 0.15f),
            surface = hueAnchor.asTintedNeutral(0.115f, 0.15f),
            surfaceDim = hueAnchor.asTintedNeutral(0.095f, 0.14f),
            surfaceBright = hueAnchor.asTintedNeutral(0.255f, 0.18f),
            surfaceContainerLowest = hueAnchor.asTintedNeutral(0.085f, 0.13f),
            surfaceContainerLow = hueAnchor.asTintedNeutral(0.138f, 0.15f),
            surfaceContainer = hueAnchor.asTintedNeutral(0.168f, 0.16f),
            surfaceContainerHigh = hueAnchor.asTintedNeutral(0.205f, 0.18f),
            surfaceContainerHighest = hueAnchor.asTintedNeutral(0.248f, 0.19f),
            surfaceVariant = hueAnchor.asTintedNeutral(0.225f, 0.19f),
            outlineVariant = hueAnchor.asTintedNeutral(0.34f, 0.15f)
        )
        else -> copy(
            background = hueAnchor.asTintedNeutral(0.975f, 0.16f),
            surface = hueAnchor.asTintedNeutral(0.975f, 0.16f),
            surfaceDim = hueAnchor.asTintedNeutral(0.92f, 0.18f),
            surfaceBright = hueAnchor.asTintedNeutral(0.992f, 0.12f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hueAnchor.asTintedNeutral(0.955f, 0.18f),
            surfaceContainer = hueAnchor.asTintedNeutral(0.93f, 0.20f),
            surfaceContainerHigh = hueAnchor.asTintedNeutral(0.90f, 0.22f),
            surfaceContainerHighest = hueAnchor.asTintedNeutral(0.868f, 0.24f),
            surfaceVariant = hueAnchor.asTintedNeutral(0.89f, 0.22f),
            outlineVariant = hueAnchor.asTintedNeutral(0.78f, 0.16f)
        )
    }
}

fun Modifier.luminaAmbientBackdrop(
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    val scheme = MaterialTheme.colorScheme
    val isPureBlack = scheme.background == Color.Black
    if (isPureBlack) return@composed this

    val topGlow = scheme.primary.copy(alpha = 0.085f)
    val sideGlow = scheme.tertiary.copy(alpha = 0.055f)

    this.drawBehind {
        val maxDim = maxOf(size.width, size.height).coerceAtLeast(1f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(topGlow, Color.Transparent),
                center = Offset(size.width * 0.22f, size.height * 0.06f),
                radius = maxDim * 0.72f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(sideGlow, Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.28f),
                radius = maxDim * 0.62f
            )
        )
    }
}
