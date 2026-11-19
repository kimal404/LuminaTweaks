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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalCornerRadius = compositionLocalOf { 18.dp }
val LocalFloatingNavBar = compositionLocalOf { false }
val LocalScrollAnimation = compositionLocalOf { true }
val LocalEdgeSwipeBack = compositionLocalOf { true }
val LocalPageTransition = compositionLocalOf { PageTransition.CrossAxis }

val LuminaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

fun createLuminaShapes(baseRadius: Int): Shapes {
    val r = baseRadius.toFloat()
    val ratio = r / 18f
    return Shapes(
        extraSmall = RoundedCornerShape((6f * ratio).dp),
        small = RoundedCornerShape((10f * ratio).dp),
        medium = RoundedCornerShape(r.dp),
        large = RoundedCornerShape((24f * ratio).dp),
        extraLarge = RoundedCornerShape((30f * ratio).dp)
    )
}
