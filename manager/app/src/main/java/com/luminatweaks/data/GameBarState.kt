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

data class GameBarQuickApp(
    val packageName: String,
    val label: String
)

data class GameBarUiState(
    val expanded: Boolean = false,
    val fps: Int = 0,
    val frametimeMs: Float = 0f,
    val targetHz: Int = 60,
    val profile: String = "Balanced",
    val autoProfile: Boolean = true,
    val gameLabel: String? = null,
    val gamePackage: String? = null,
    val cpuPercent: Int? = null,
    val gpuPercent: Int? = null,
    val tempLabel: String = "--",
    val batteryPercent: Int = 0,
    val ramPercent: Int? = null,
    val volumePercent: Int = 50,
    val brightnessPercent: Int = 50,
    val side: GameBarSide = GameBarSide.LEFT,
    val opacity: Float = 0.94f,
    val handleOpacity: Float = 0.45f,
    val autoDimHandle: Boolean = true,
    val autoGame: Boolean = true,
    val manual: Boolean = false,
    val fpsOverlay: Boolean = false,
    val dndEnabled: Boolean = false,
    val liteMode: Boolean = false,
    val rrModes: List<Int> = emptyList(),
    val currentRrId: Int? = null,
    val quickApps: List<GameBarQuickApp> = emptyList()
)
