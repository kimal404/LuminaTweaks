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

package com.luminatweaks.data

data class AppProfileConfig(
    val pkg: String = "",
    val saklarUtama: Boolean = false,
    val liteMode: String = "default",
    val refreshRate: String = "default",
    val renderer: String = "default",
    val dnd: String = "default",
    val textureDriver: String = "native",
    val textureLod: Int = 0,
    val textureEnabled: Boolean = false,
    val textureDetail: Int = 0,
    val pixelSharp: Boolean = false,
    val hardDistance: Boolean = false
)
