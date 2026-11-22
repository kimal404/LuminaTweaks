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
