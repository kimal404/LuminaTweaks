/*
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

package com.luminatweaks.gamebar

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.luminatweaks.ui.theme.LuminaTheme

@Composable
fun GameBarTheme(
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    LuminaTheme(
        darkTheme = darkOverride ?: isSystemInDarkTheme(),
        content = content
    )
}

fun azShape(base: Dp): Shape = RoundedCornerShape(base)
