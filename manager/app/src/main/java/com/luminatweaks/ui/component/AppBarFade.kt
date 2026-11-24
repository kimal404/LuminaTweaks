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

package com.luminatweaks.ui.component

import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun rememberHeaderScrolled(listState: LazyListState): State<Boolean> =
    remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

@Composable
fun rememberHeaderScrolled(scrollState: ScrollState): State<Boolean> =
    remember(scrollState) {
        derivedStateOf { scrollState.value > 0 }
    }

@Composable
fun Modifier.appBarFade(surface: Color, overlap: Float = 1f): Modifier {
    val scrolled = overlap > 0.01f
    val scrimAlpha by animateFloatAsState(
        targetValue = if (scrolled) 0.94f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "appBarScrimAlpha"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (scrolled) 0.2f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "appBarBorderAlpha"
    )
    val elevation by animateDpAsState(
        targetValue = if (scrolled) 6.dp else 0.dp,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "appBarElevation"
    )
    val outline = MaterialTheme.colorScheme.outlineVariant

    return this
        .shadow(elevation, RectangleShape, clip = false)
        .drawBehind {
            if (scrimAlpha > 0.001f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to surface.copy(alpha = scrimAlpha),
                        0.55f to surface.copy(alpha = scrimAlpha * 0.94f),
                        0.82f to surface.copy(alpha = scrimAlpha * 0.62f),
                        1f to Color.Transparent
                    )
                )
            }
            if (borderAlpha > 0.001f) {
                val stroke = 0.5.dp.toPx()
                drawLine(
                    color = outline.copy(alpha = borderAlpha),
                    start = Offset(0f, size.height - stroke),
                    end = Offset(size.width, size.height - stroke),
                    strokeWidth = stroke
                )
            }
        }
}

@Composable
fun Modifier.scrolledHeader(scrolled: Boolean, surface: Color): Modifier =
    appBarFade(surface, if (scrolled) 1f else 0f)
