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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.R
import com.luminatweaks.ui.component.AospConnectedGroup
import com.luminatweaks.ui.component.AospPreferenceSwitch
import com.luminatweaks.ui.component.AospSectionHeader
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.AospTopBar
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UtilityScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isFloating = LocalFloatingNavBar.current
    var isDozeActive by remember { mutableStateOf(false) }
    var isGmsLimitActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dozeVal = runCatching {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/adb/.config/lumina/aggressive_doze 2>/dev/null"))
                proc.inputStream.bufferedReader().use { it.readText().trim() }
            }.getOrDefault("0")

            val gmsVal = runCatching {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/adb/.config/lumina/gms_limit 2>/dev/null"))
                proc.inputStream.bufferedReader().use { it.readText().trim() }
            }.getOrDefault("0")

            withContext(Dispatchers.Main) {
                isDozeActive = dozeVal == "1"
                isGmsLimitActive = gmsVal == "1"
            }
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (isFloating) 100.dp + navInset else 24.dp + navInset
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AospTopBar(
                title = stringResource(R.string.utility_screen_title),
                subtitle = localizedLabel("Manajemen daya & standby", "Power and standby management"),
                scrollBehavior = scrollBehavior,
                navigationIcon = { LuminaBackButton(onBack = onBack) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = bottomPadding,
                start = 0.dp,
                end = 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "info") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.utility_info_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.padding(top = 4.dp))
                            Text(
                                text = stringResource(R.string.utility_info_desc),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "power_group") {
                Column {
                    AospSectionHeader(title = stringResource(R.string.utility_section_power))
                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AospPreferenceSwitch(
                            index = 0,
                            count = 2,
                            icon = Icons.Rounded.BatterySaver,
                            title = stringResource(R.string.utility_doze_title),
                            subtitle = stringResource(R.string.utility_doze_desc),
                            checked = isDozeActive,
                            onCheckedChange = { newState ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isDozeActive = newState
                                coroutineScope.launch(Dispatchers.IO) {
                                    val dozeCmd = if (newState) {
                                        "echo 1 > /data/adb/.config/lumina/aggressive_doze && dumpsys deviceidle force-idle 2>/dev/null"
                                    } else {
                                        "echo 0 > /data/adb/.config/lumina/aggressive_doze && dumpsys deviceidle unforce 2>/dev/null"
                                    }
                                    runCatching {
                                        Runtime.getRuntime().exec(arrayOf("su", "-c", dozeCmd))
                                    }
                                }
                            }
                        )

                        AospPreferenceSwitch(
                            index = 1,
                            count = 2,
                            icon = Icons.Rounded.SyncDisabled,
                            title = stringResource(R.string.utility_gms_title),
                            subtitle = stringResource(R.string.utility_gms_desc),
                            checked = isGmsLimitActive,
                            onCheckedChange = { newState ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isGmsLimitActive = newState
                                coroutineScope.launch(Dispatchers.IO) {
                                    val gmsCmd = if (newState) {
                                        "echo 1 > /data/adb/.config/lumina/gms_limit && cmd appops set com.google.android.gms RUN_IN_BACKGROUND ignore && cmd appops set com.google.android.gms WAKE_LOCK ignore"
                                    } else {
                                        "echo 0 > /data/adb/.config/lumina/gms_limit && cmd appops set com.google.android.gms RUN_IN_BACKGROUND allow && cmd appops set com.google.android.gms WAKE_LOCK allow"
                                    }
                                    runCatching {
                                        Runtime.getRuntime().exec(arrayOf("su", "-c", gmsCmd))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}