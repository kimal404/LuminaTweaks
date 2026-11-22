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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.data.CapabilityEntry
import com.luminatweaks.data.CapabilityReport
import com.luminatweaks.data.CapabilityStatus
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.ui.component.AospTopBar
import com.luminatweaks.ui.component.LuminaBackButton
import kotlinx.coroutines.launch

@Composable
fun CompatibilityScreen(onBackClick: () -> Unit = {}) {
    BackHandler(onBack = onBackClick)
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var report by remember { mutableStateOf<CapabilityReport?>(null) }
    var strictMode by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = report == null
        report = ConfigRepository.getCapabilityReport()
        strictMode = DaemonManager.getCapabilityMode() == "strict"
        loading = false
    }

    val entries = report?.entries.orEmpty()
    val supported = entries.count {
        it.status == CapabilityStatus.Supported || it.status == CapabilityStatus.Coerced
    }
    val coerced = entries.count { it.status == CapabilityStatus.Coerced }
    val issues = entries.count {
        it.status == CapabilityStatus.Blocked || it.status == CapabilityStatus.Degraded
    }
    val unsupported = entries.count { it.status == CapabilityStatus.Unsupported }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AospTopBar(
                title = stringResource(R.string.compatibility_title),
                subtitle = stringResource(R.string.compatibility_subtitle),
                scrollBehavior = scrollBehavior,
                navigationIcon = { LuminaBackButton(onBack = onBackClick) },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    refreshing = true
                                    DaemonManager.reprobeCapabilities()
                                    report = ConfigRepository.getCapabilityReport()
                                    strictMode = DaemonManager.getCapabilityMode() == "strict"
                                    refreshing = false
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.compatibility_refresh)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (loading && report == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "summary") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.compatibility_summary_supported),
                        value = supported,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.compatibility_summary_coerced),
                        value = coerced,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.compatibility_summary_issues),
                        value = issues,
                        color = MaterialTheme.colorScheme.error
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.compatibility_summary_unsupported),
                        value = unsupported,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "mode") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.compatibility_mode_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (strictMode) {
                                    stringResource(R.string.compatibility_mode_strict_desc)
                                } else {
                                    stringResource(R.string.compatibility_mode_observe_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = strictMode,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    val mode = if (checked) "strict" else "observe"
                                    if (DaemonManager.setCapabilityMode(mode)) {
                                        strictMode = checked
                                        DaemonManager.reprobeCapabilities()
                                        report = ConfigRepository.getCapabilityReport()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                item(key = "empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.compatibility_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.compatibility_empty_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                entries.groupBy { it.category }
                    .toSortedMap()
                    .forEach { (category, items) ->
                        item(key = "header_$category") {
                            Text(
                                text = categoryLabel(category),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                            )
                        }
                        items(
                            items = items.sortedBy { it.path },
                            key = { "entry_${it.path}" }
                        ) { entry ->
                            CapabilityRow(entry)
                        }
                    }
            }
        }
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier,
    label: String,
    value: Int,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CapabilityRow(entry: CapabilityEntry) {
    val (icon, color) = statusVisual(entry.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = entry.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.requested != null || entry.actual != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val requested = entry.requested ?: "-"
                    val actual = entry.actual ?: "-"
                    Text(
                        text = stringResource(R.string.compatibility_requested, requested),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.compatibility_actual, actual),
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
            Text(
                text = statusLabel(entry.status),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun statusVisual(status: CapabilityStatus): Pair<ImageVector, Color> = when (status) {
    CapabilityStatus.Supported -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
    CapabilityStatus.Coerced -> Icons.Rounded.WarningAmber to MaterialTheme.colorScheme.tertiary
    CapabilityStatus.Blocked -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
    CapabilityStatus.Degraded -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
    CapabilityStatus.Unsupported -> Icons.Rounded.RemoveCircleOutline to MaterialTheme.colorScheme.onSurfaceVariant
    CapabilityStatus.RolledBack -> Icons.Rounded.Restore to MaterialTheme.colorScheme.tertiary
    CapabilityStatus.Unknown -> Icons.Rounded.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun statusLabel(status: CapabilityStatus): String = stringResource(
    when (status) {
        CapabilityStatus.Supported -> R.string.compatibility_status_supported
        CapabilityStatus.Coerced -> R.string.compatibility_status_coerced
        CapabilityStatus.Blocked -> R.string.compatibility_status_blocked
        CapabilityStatus.Degraded -> R.string.compatibility_status_degraded
        CapabilityStatus.Unsupported -> R.string.compatibility_status_unsupported
        CapabilityStatus.RolledBack -> R.string.compatibility_status_rolled_back
        CapabilityStatus.Unknown -> R.string.compatibility_status_unknown
    }
)

@Composable
private fun categoryLabel(category: String): String = stringResource(
    when (category) {
        "cpu" -> R.string.compatibility_category_cpu
        "gpu" -> R.string.compatibility_category_gpu
        "storage" -> R.string.compatibility_category_storage
        "network" -> R.string.compatibility_category_network
        "thermal" -> R.string.compatibility_category_thermal
        "scheduler" -> R.string.compatibility_category_scheduler
        else -> R.string.compatibility_category_system
    }
)
