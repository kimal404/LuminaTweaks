/*
 * Copyright (C) 2025 Zexshia
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

import com.materialkolor.dynamiccolor.ColorSpec

enum class ColorEngine(
    val persistedName: String,
    val librarySpec: ColorSpec.SpecVersion
) {
    MaterialYou("SPEC_2021", ColorSpec.SpecVersion.SPEC_2021),
    MaterialExpressive("SPEC_2025", ColorSpec.SpecVersion.SPEC_2025);

    companion object {
        val Default = MaterialExpressive

        fun fromPersisted(value: String?): ColorEngine =
            entries.firstOrNull { it.persistedName == value } ?: Default
    }
}
