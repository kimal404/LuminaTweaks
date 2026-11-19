package com.luminatweaks.ui.component

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

fun Modifier.appBarFade(surface: Color, overlap: Float = 1f): Modifier {
    val edgeAlpha = if (overlap > 0f) 0.28f else 0.18f
    return background(
        Brush.verticalGradient(
            0f to surface,
            0.42f to surface.copy(alpha = 0.92f),
            0.58f to surface.copy(alpha = 0.80f),
            0.74f to surface.copy(alpha = 0.58f),
            0.88f to surface.copy(alpha = edgeAlpha),
            1f to Color.Transparent
        )
    )
}
