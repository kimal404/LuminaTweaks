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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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

    val colorScheme = if (amoledMode) {
        rawColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A)
        )
    } else {
        if (darkTheme && (rawColorScheme.surfaceContainerLowest == Color.Black || rawColorScheme.surface == Color.Black || rawColorScheme.background == Color.Black)) {
            val safeSurface = if (rawColorScheme.surface == Color.Black) Color(0xFF141218) else rawColorScheme.surface
            val safeLowest = if (rawColorScheme.surfaceContainerLowest == Color.Black) Color(0xFF0F0D13) else rawColorScheme.surfaceContainerLowest
            val safeBackground = if (rawColorScheme.background == Color.Black) safeSurface else rawColorScheme.background
            val safeDim = if (rawColorScheme.surfaceDim == Color.Black) safeSurface else rawColorScheme.surfaceDim
            rawColorScheme.copy(
                background = safeBackground,
                surface = safeSurface,
                surfaceDim = safeDim,
                surfaceContainerLowest = safeLowest
            )
        } else {
            rawColorScheme
        }
    }

    val animatedColorScheme = animateColorSchemeAsState(
        targetColorScheme = colorScheme.withAccentIntensity(themeState.accentIntensity)
    )

    val dynamicShapes = remember(themeState.cornerRadius) {
        createLuminaShapes(themeState.cornerRadius)
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
            content = content
        )
    }
}
