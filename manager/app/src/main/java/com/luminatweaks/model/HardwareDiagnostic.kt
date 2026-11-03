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

data class DiagnosticLogEntry(
    val tag: String,
    val value: String,
    val isHeader: Boolean = false,
    val isWarning: Boolean = false
)

data class HardwareDiagnosticState(
    val logs: List<DiagnosticLogEntry> = emptyList(),
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false
)
