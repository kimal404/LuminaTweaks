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

package com.luminatweaks.model

data class BatteryHealthInfo(
    val healthPercentage: Int?,
    val currentCapacityMah: Int?,
    val designCapacityMah: Int?,
    val cycleCount: Int?,
    val temperatureCelsius: Float?,
    val voltageMv: Float?,
    val status: String = "Unknown",
    val isSupported: Boolean = false
)

data class StorageHealthInfo(
    val storageType: String = "Unknown",
    val model: String = "Internal Storage",
    val revision: String = "",
    val wearLevelA: String = "N/A",
    val wearLevelB: String = "N/A",
    val wearPercentageA: Int = 0,
    val wearPercentageB: Int = 0,
    val preEolStatus: String = "Normal",
    val isCritical: Boolean = false,
    val isSupported: Boolean = false
)

data class HardwareDiagnosticState(
    val battery: BatteryHealthInfo = BatteryHealthInfo(null, null, null, null, null, null),
    val storage: StorageHealthInfo = StorageHealthInfo(),
    val isLoading: Boolean = true
)
