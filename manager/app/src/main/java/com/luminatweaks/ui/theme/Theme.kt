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

package com.luminatweaks.ui.theme

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.app.UiModeManager
import android.content.res.Configuration
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

private val FallbackDarkColorScheme = darkColorScheme(
    primary = PinkPrimary,
    onPrimary = PinkOnPrimary,
    primaryContainer = PinkPrimaryContainer,
    onPrimaryContainer = PinkOnPrimaryContainer,
    secondary = RoseSecondary,
    onSecondary = RoseOnSecondary,
    secondaryContainer = RoseSecondaryContainer,
    onSecondaryContainer = RoseOnSecondaryContainer,
    tertiary = AccentEyeBlue,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

private val FallbackLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = AccentStarYellow,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

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
    val paletteStyle: PaletteStyle = when (themeState.colorSpec) {
        ColorEngine.MaterialYou -> PaletteStyle.TonalSpot
        ColorEngine.MaterialExpressive -> PaletteStyle.Vibrant
    }

    val resolvedSeedColor: Color = if (isDynamic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                Color(context.getColor(android.R.color.system_accent1_500))
            }.getOrElse {
                val nativeDynamic = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                nativeDynamic.primary
            }
        } else {
            if (isDark) PinkPrimary else LightPrimary
        }
    } else {
        Color(themeState.keyColor)
    }

    val baseScheme: ColorScheme = rememberDynamicColorScheme(
        seedColor = resolvedSeedColor,
        isDark = isDark,
        isAmoled = amoledMode,
        specVersion = colorSpec,
        style = paletteStyle
    )

    val animatedColorScheme = animateColorSchemeAsState(
        targetColorScheme = baseScheme.withAccentIntensity(
            intensity = themeState.accentIntensity,
            isDark = isDark,
            isAmoled = amoledMode
        )
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
