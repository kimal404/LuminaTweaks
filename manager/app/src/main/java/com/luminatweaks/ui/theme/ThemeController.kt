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

package com.luminatweaks.ui.theme

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

enum class ColorMode(val value: Int) {
    SYSTEM(3), LIGHT(4), DARK(5), DARKAMOLED(6);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    fun getDarkThemeValue(systemDarkTheme: Boolean) = when (this) {
        SYSTEM -> systemDarkTheme
        LIGHT -> false
        DARK -> true
        DARKAMOLED -> true
    }
}

enum class ColorStyle(val persistedName: String) {
    TonalSpot("tonal_spot"),
    Vibrant("vibrant"),
    Expressive("expressive"),
    Spritz("spritz"),
    Rainbow("rainbow"),
    FruitSalad("fruit_salad"),
    Content("content"),
    Monochrome("monochrome");

    companion object {
        val Default = TonalSpot

        fun fromPersisted(value: String?): ColorStyle =
            entries.firstOrNull { it.persistedName == value } ?: Default
    }
}

enum class PageTransition(val persistedName: String, val default: Boolean = false) {
    CrossAxis("cross_axis", true),
    Horizontal("horizontal"),
    Vertical("vertical");

    companion object {
        fun fromPersisted(value: String?): PageTransition =
            entries.firstOrNull { it.persistedName == value } ?: CrossAxis
    }
}

enum class AccentIntensity(val label: String) {
    Neutral("Dibungkam"),
    Balanced("Seimbang"),
    Bright("Cerah"),
    Vibrant("Bersemangat");

    val saturationFactor: Float
        get() = when (this) {
            Neutral -> 0.14f
            Balanced -> 0.68f
            Bright -> 1.0f
            Vibrant -> 1.32f
        }

    companion object {
        fun fromValue(value: Int): AccentIntensity = entries.getOrElse(value) { Vibrant }
    }
}

data class AppThemeSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
    val colorSpec: ColorEngine,
    val accentIntensity: AccentIntensity,
    val cornerRadius: Int = ThemeController.DEFAULT_CORNER_RADIUS,
    val floatingNavBar: Boolean = false,
    val colorStyle: ColorStyle = ColorStyle.Default,
    val scrollAnimation: Boolean = true,
    val edgeSwipeBack: Boolean = true,
    val pageTransition: PageTransition = PageTransition.CrossAxis
)

object ThemeController {
    const val DEFAULT_CORNER_RADIUS = 18
    const val MIN_CORNER_RADIUS = 0
    const val MAX_CORNER_RADIUS = 32

    fun getAppSettings(context: Context): AppThemeSettings {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val colorMode = ColorMode.fromValue(
            prefs.getInt("color_mode", ColorMode.SYSTEM.value)
        )
        val keyColor = prefs.getInt("key_color", 0)
        val colorSpec = ColorEngine.fromPersisted(prefs.getString("color_spec", null))
        val accentIntensity = AccentIntensity.fromValue(
            prefs.getString("pers_accent_intensity", null)?.toIntOrNull()
                ?: prefs.getInt("pers_accent_intensity", AccentIntensity.Vibrant.ordinal)
        )
        val cornerRadius = prefs.getInt("ui_corner_radius", DEFAULT_CORNER_RADIUS)
            .coerceIn(MIN_CORNER_RADIUS, MAX_CORNER_RADIUS)
        val floatingNavBar = prefs.getBoolean("floating_nav_bar", false)
        val colorStyle = ColorStyle.fromPersisted(prefs.getString("color_style", null))
        val scrollAnimation = prefs.getBoolean("scroll_animation", true)
        val edgeSwipeBack = prefs.getBoolean("edge_swipe_back", true)
        val pageTransition = PageTransition.fromPersisted(prefs.getString("page_transition", null))

        return AppThemeSettings(
            colorMode = colorMode,
            keyColor = keyColor,
            colorSpec = colorSpec,
            accentIntensity = accentIntensity,
            cornerRadius = cornerRadius,
            floatingNavBar = floatingNavBar,
            colorStyle = colorStyle,
            scrollAnimation = scrollAnimation,
            edgeSwipeBack = edgeSwipeBack,
            pageTransition = pageTransition
        )
    }

    fun saveTheme(
        context: Context,
        mode: ColorMode,
        key: Int,
        spec: ColorEngine,
        intensity: AccentIntensity,
        cornerRadius: Int? = null,
        floatingNavBar: Boolean? = null
    ) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt("color_mode", mode.value)
            .putInt("key_color", key)
            .putString("color_spec", spec.persistedName)
            .putString("pers_accent_intensity", intensity.ordinal.toString())

        if (cornerRadius != null) {
            editor.putInt("ui_corner_radius", cornerRadius.coerceIn(MIN_CORNER_RADIUS, MAX_CORNER_RADIUS))
        }
        if (floatingNavBar != null) {
            editor.putBoolean("floating_nav_bar", floatingNavBar)
        }
        editor.apply()
    }

    fun saveCornerRadius(context: Context, radius: Int) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("ui_corner_radius", radius.coerceIn(MIN_CORNER_RADIUS, MAX_CORNER_RADIUS))
            .apply()
    }

    fun saveColorStyle(context: Context, style: ColorStyle) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("color_style", style.persistedName)
            .apply()
    }

    fun saveScrollAnimation(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("scroll_animation", enabled)
            .apply()
    }

    fun saveEdgeSwipeBack(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("edge_swipe_back", enabled)
            .apply()
    }

    fun savePageTransition(context: Context, transition: PageTransition) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("page_transition", transition.persistedName)
            .apply()
    }

    fun saveFloatingNavBar(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("floating_nav_bar", enabled)
            .apply()
    }
}

val keyColorOptions = listOf(
    Color(0xFFF44336).toArgb(),
    Color(0xFFE91E63).toArgb(),
    Color(0xFF9C27B0).toArgb(),
    Color(0xFF673AB7).toArgb(),
    Color(0xFF3F51B5).toArgb(),
    Color(0xFF2196F3).toArgb(),
    Color(0xFF00BCD4).toArgb(),
    Color(0xFF009688).toArgb(),
    Color(0xFF4FAF50).toArgb(),
    Color(0xFFFFEB3B).toArgb(),
    Color(0xFFFFC107).toArgb(),
    Color(0xFFFF9800).toArgb(),
    Color(0xFF795548).toArgb(),
    Color(0xFF607D8F).toArgb(),
    Color(0xFFFF9CA8).toArgb()
)

fun ColorScheme.withAccentIntensity(
    intensity: AccentIntensity,
    isDark: Boolean = true,
    isAmoled: Boolean = false
): ColorScheme {
    val f = intensity.saturationFactor
    val tintedPrimary = primary.scaledSaturation(f)
    val tintScale = when (intensity) {
        AccentIntensity.Neutral -> 0.14f
        AccentIntensity.Balanced -> 0.65f
        AccentIntensity.Bright -> 1.0f
        AccentIntensity.Vibrant -> 1.32f
    }

    val tintedScheme = if (isDark && isAmoled) {
        copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = lerp(Color(0xFF181A1C), tintedPrimary, 0.10f * tintScale),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = lerp(Color(0xFF0A0C0E), tintedPrimary, 0.05f * tintScale),
            surfaceContainer = lerp(Color(0xFF101316), tintedPrimary, 0.075f * tintScale),
            surfaceContainerHigh = lerp(Color(0xFF161A1E), tintedPrimary, 0.095f * tintScale),
            surfaceContainerHighest = lerp(Color(0xFF1D2227), tintedPrimary, 0.115f * tintScale)
        )
    } else if (isDark) {
        copy(
            background = lerp(Color(0xFF0B0E11), tintedPrimary, 0.065f * tintScale),
            surface = lerp(Color(0xFF0B0E11), tintedPrimary, 0.065f * tintScale),
            surfaceVariant = lerp(Color(0xFF1B2024), tintedPrimary, 0.12f * tintScale),
            surfaceContainerLowest = lerp(Color(0xFF080A0D), tintedPrimary, 0.05f * tintScale),
            surfaceContainerLow = lerp(Color(0xFF0E1216), tintedPrimary, 0.075f * tintScale),
            surfaceContainer = lerp(Color(0xFF11161B), tintedPrimary, 0.095f * tintScale),
            surfaceContainerHigh = lerp(Color(0xFF161C22), tintedPrimary, 0.115f * tintScale),
            surfaceContainerHighest = lerp(Color(0xFF1C232A), tintedPrimary, 0.135f * tintScale)
        )
    } else {
        copy(
            background = lerp(Color(0xFFF8FAFC), tintedPrimary, 0.04f * tintScale),
            surface = lerp(Color(0xFFF8FAFC), tintedPrimary, 0.04f * tintScale),
            surfaceVariant = lerp(Color(0xFFE6ECF0), tintedPrimary, 0.08f * tintScale),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(Color(0xFFF2F5F8), tintedPrimary, 0.05f * tintScale),
            surfaceContainer = lerp(Color(0xFFECF1F5), tintedPrimary, 0.065f * tintScale),
            surfaceContainerHigh = lerp(Color(0xFFE5ECF1), tintedPrimary, 0.08f * tintScale),
            surfaceContainerHighest = lerp(Color(0xFFDEE6EC), tintedPrimary, 0.095f * tintScale)
        )
    }

    return tintedScheme.copy(
        primary = primary.scaledSaturation(f),
        onPrimary = onPrimary.scaledSaturation(f),
        primaryContainer = primaryContainer.scaledSaturation(f),
        onPrimaryContainer = onPrimaryContainer.scaledSaturation(f),
        secondary = secondary.scaledSaturation(f),
        onSecondary = onSecondary.scaledSaturation(f),
        secondaryContainer = secondaryContainer.scaledSaturation(f),
        onSecondaryContainer = onSecondaryContainer.scaledSaturation(f),
        tertiary = tertiary.scaledSaturation(f),
        tertiaryContainer = tertiaryContainer.scaledSaturation(f)
    )
}

private fun Color.scaledSaturation(factor: Float): Color {
    val hsl = FloatArray(3)
    colorToHSL(this, hsl)
    hsl[1] = (hsl[1] * factor).coerceIn(0f, 1f)
    return hslToColor(hsl, this.alpha)
}

private fun colorToHSL(color: Color, out: FloatArray) {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    out[2] = l
    if (max == min) {
        out[0] = 0f
        out[1] = 0f
        return
    }
    val d = max - min
    out[1] = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    out[0] = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
}

private fun hslToColor(hsl: FloatArray, alpha: Float): Color {
    val (h, s, l) = hsl
    if (s == 0f) return Color(l, l, l, alpha)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun channel(t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }
    return Color(channel(h + 1f / 3f), channel(h), channel(h - 1f / 3f), alpha)
}

@Composable
fun animateColorSchemeAsState(
    targetColorScheme: ColorScheme,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = tween(400)
): ColorScheme {
    var from by remember { mutableStateOf(targetColorScheme) }
    var to by remember { mutableStateOf(targetColorScheme) }
    val progress = remember { Animatable(1f) }

    val key = targetColorScheme.roleColors()

    LaunchedEffect(key) {
        if (key == to.roleColors()) return@LaunchedEffect
        from = to
        to = targetColorScheme
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec)
    }

    return if (progress.value >= 1f) {
        to
    } else {
        lerpScheme(from, to, progress.value)
    }
}

private fun ColorScheme.roleColors(): List<Color> = listOf(
    primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
    secondary, onSecondary, secondaryContainer, onSecondaryContainer,
    tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
    background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
    surfaceTint, inverseSurface, inverseOnSurface,
    error, onError, errorContainer, onErrorContainer,
    outline, outlineVariant, scrim,
    surfaceBright, surfaceDim,
    surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
    surfaceContainerLow, surfaceContainerLowest
)

private fun lerpScheme(from: ColorScheme, to: ColorScheme, t: Float): ColorScheme = ColorScheme(
    primary = lerpColor(from.primary, to.primary, t),
    onPrimary = lerpColor(from.onPrimary, to.onPrimary, t),
    primaryContainer = lerpColor(from.primaryContainer, to.primaryContainer, t),
    onPrimaryContainer = lerpColor(from.onPrimaryContainer, to.onPrimaryContainer, t),
    inversePrimary = lerpColor(from.inversePrimary, to.inversePrimary, t),
    secondary = lerpColor(from.secondary, to.secondary, t),
    onSecondary = lerpColor(from.onSecondary, to.onSecondary, t),
    secondaryContainer = lerpColor(from.secondaryContainer, to.secondaryContainer, t),
    onSecondaryContainer = lerpColor(from.onSecondaryContainer, to.onSecondaryContainer, t),
    tertiary = lerpColor(from.tertiary, to.tertiary, t),
    onTertiary = lerpColor(from.onTertiary, to.onTertiary, t),
    tertiaryContainer = lerpColor(from.tertiaryContainer, to.tertiaryContainer, t),
    onTertiaryContainer = lerpColor(from.onTertiaryContainer, to.onTertiaryContainer, t),
    background = lerpColor(from.background, to.background, t),
    onBackground = lerpColor(from.onBackground, to.onBackground, t),
    surface = lerpColor(from.surface, to.surface, t),
    onSurface = lerpColor(from.onSurface, to.onSurface, t),
    surfaceVariant = lerpColor(from.surfaceVariant, to.surfaceVariant, t),
    onSurfaceVariant = lerpColor(from.onSurfaceVariant, to.onSurfaceVariant, t),
    surfaceTint = lerpColor(from.surfaceTint, to.surfaceTint, t),
    inverseSurface = lerpColor(from.inverseSurface, to.inverseSurface, t),
    inverseOnSurface = lerpColor(from.inverseOnSurface, to.inverseOnSurface, t),
    error = lerpColor(from.error, to.error, t),
    onError = lerpColor(from.onError, to.onError, t),
    errorContainer = lerpColor(from.errorContainer, to.errorContainer, t),
    onErrorContainer = lerpColor(from.onErrorContainer, to.onErrorContainer, t),
    outline = lerpColor(from.outline, to.outline, t),
    outlineVariant = lerpColor(from.outlineVariant, to.outlineVariant, t),
    scrim = lerpColor(from.scrim, to.scrim, t),
    surfaceBright = lerpColor(from.surfaceBright, to.surfaceBright, t),
    surfaceDim = lerpColor(from.surfaceDim, to.surfaceDim, t),
    surfaceContainer = lerpColor(from.surfaceContainer, to.surfaceContainer, t),
    surfaceContainerHigh = lerpColor(from.surfaceContainerHigh, to.surfaceContainerHigh, t),
    surfaceContainerHighest = lerpColor(from.surfaceContainerHighest, to.surfaceContainerHighest, t),
    surfaceContainerLow = lerpColor(from.surfaceContainerLow, to.surfaceContainerLow, t),
    surfaceContainerLowest = lerpColor(from.surfaceContainerLowest, to.surfaceContainerLowest, t)
)

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha + (to.alpha - from.alpha) * t
)
