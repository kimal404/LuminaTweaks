package com.luminatweaks.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

fun Modifier.luminaHazeHeader(
    hazeState: HazeState,
    surfaceColor: Color,
    overlappedFraction: Float
): Modifier {
    val blurProgress = (overlappedFraction * 1.35f).coerceIn(0f, 1f)
    return if (blurProgress > 0.01f) {
        this
            .fillMaxWidth()
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = surfaceColor,
                    tints = listOf(
                        HazeTint(surfaceColor.copy(alpha = 0.62f * blurProgress))
                    ),
                    blurRadius = (24f * blurProgress).dp,
                    noiseFactor = 0f
                )
            ) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 1f,
                    endIntensity = 0f
                )
            }
            .background(
                brush = Brush.verticalGradient(
                    0.0f to surfaceColor.copy(alpha = 0.90f * blurProgress),
                    0.65f to surfaceColor.copy(alpha = 0.65f * blurProgress),
                    1.0f to Color.Transparent
                )
            )
            .statusBarsPadding()
    } else {
        this
            .fillMaxWidth()
            .statusBarsPadding()
    }
}
