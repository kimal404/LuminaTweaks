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

import org.json.JSONObject

enum class CapabilityStatus {
    Supported,
    Coerced,
    Blocked,
    Degraded,
    Unsupported,
    Unknown,
    RolledBack,
    ReadOnly,
    WriteOnly;

    companion object {
        fun from(raw: String?): CapabilityStatus = when (raw?.trim()?.lowercase()) {
            "supported" -> Supported
            "coerced" -> Coerced
            "blocked" -> Blocked
            "degraded" -> Degraded
            "unsupported" -> Unsupported
            "rolled_back" -> RolledBack
            "read_only" -> ReadOnly
            "write_only" -> WriteOnly
            else -> Unknown
        }
    }
}

data class CapabilityEntry(
    val path: String,
    val category: String,
    val status: CapabilityStatus,
    val requested: String?,
    val actual: String?,
    val detail: String?,
    val updatedAtMs: Long
)

data class CapabilityReport(
    val bootId: String,
    val generatedAtMs: Long,
    val mode: String,
    val entries: List<CapabilityEntry>
) {
    val isStrict: Boolean
        get() = mode.equals("strict", ignoreCase = true)

    companion object {
        fun parse(raw: String?): CapabilityReport? {
            if (raw.isNullOrBlank()) return null
            return try {
                val json = JSONObject(raw)
                val array = json.optJSONArray("entries")
                val entries = buildList {
                    if (array != null) {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val path = item.optString("path")
                            if (path.isEmpty()) continue
                            add(
                                CapabilityEntry(
                                    path = path,
                                    category = item.optString("category", "system"),
                                    status = CapabilityStatus.from(item.optString("status")),
                                    requested = item.optString("requested").takeIf { it.isNotEmpty() },
                                    actual = item.optString("actual").takeIf { it.isNotEmpty() },
                                    detail = item.optString("detail").takeIf { it.isNotEmpty() },
                                    updatedAtMs = item.optLong("updated_at_ms", 0L)
                                )
                            )
                        }
                    }
                }
                CapabilityReport(
                    bootId = json.optString("boot_id"),
                    generatedAtMs = json.optLong("generated_at_ms", 0L),
                    mode = json.optString("mode", "observe"),
                    entries = entries
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
