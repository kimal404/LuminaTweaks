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

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.ui.component.AospChevron
import com.luminatweaks.ui.component.AospConnectedGroup
import com.luminatweaks.ui.component.AospOptionBottomSheet
import com.luminatweaks.ui.component.AospPreferenceItem
import com.luminatweaks.ui.component.AospSectionHeader
import com.luminatweaks.ui.component.AospSheetOption
import com.luminatweaks.ui.component.AospTopBar
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.aospConnectedShape
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MemoryZramInit(
    val physicalRamGb: Int,
    val zramSizeGb: Float,
    val algorithm: String,
    val algorithms: List<String>,
    val swappiness: Float,
    val needsReapply: Boolean
)

@Composable
fun MemoryZramScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isFloating = LocalFloatingNavBar.current

    var physicalRamGb by remember { mutableIntStateOf(8) }
    var zramSizeGb by remember { mutableFloatStateOf(8f) }
    var selectedAlgorithm by remember { mutableStateOf("zstd") }
    var algorithms by remember { mutableStateOf(listOf("zstd", "lz4", "lzo-rle", "lzo")) }
    var swappinessValue by remember { mutableFloatStateOf(100f) }

    var showAlgorithmSheet by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }

    val maxZramLimit = remember(physicalRamGb) {
        (physicalRamGb * 1.5).roundToInt().coerceAtLeast(4)
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val totalRam = DeviceUtil.getTotalRamGb(context)
            val currentZram = DeviceUtil.getCurrentZramDisksizeGb()
            val currentSwap = DeviceUtil.getSwappiness()
            val activeAlgo = DeviceUtil.getActiveZramAlgorithm()
            val availableAlgos = DeviceUtil.getAvailableZramAlgorithms()

            val state = ConfigRepository.getTweaksState(forceRefresh = true)
            val targetZramSize = if (state.zramSizeGb > 0) state.zramSizeGb else currentZram
            val targetAlgo = if (state.zramAlgorithm.isNotBlank()) state.zramAlgorithm else activeAlgo
            val targetSwap = if (state.swappiness in 0..200) state.swappiness else currentSwap
            val dynamicMax = (totalRam * 1.5).roundToInt().coerceAtLeast(4)
            val needsReapply = targetZramSize != currentZram ||
                !targetAlgo.equals(activeAlgo, ignoreCase = true) ||
                targetSwap != currentSwap

            MemoryZramInit(
                physicalRamGb = totalRam,
                zramSizeGb = targetZramSize.toFloat().coerceIn(1f, dynamicMax.toFloat()),
                algorithm = targetAlgo,
                algorithms = availableAlgos,
                swappiness = targetSwap.toFloat(),
                needsReapply = needsReapply
            )
        }

        physicalRamGb = loaded.physicalRamGb
        zramSizeGb = loaded.zramSizeGb
        selectedAlgorithm = loaded.algorithm
        algorithms = loaded.algorithms
        swappinessValue = loaded.swappiness
        isLoaded = true

        if (loaded.needsReapply) {
            ConfigRepository.reapplyGlobalTweaks()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (isFloating) 100.dp + navInset else 24.dp + navInset

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AospTopBar(
                title = stringResource(R.string.memory_zram_title),
                subtitle = localizedLabel("Alokasi swap & kompresi", "Swap allocation & compression"),
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
            item(key = "hero") {
                MemoryHero(
                    physicalRamGb = physicalRamGb,
                    zramSizeGb = zramSizeGb,
                    algorithm = selectedAlgorithm,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item(key = "zram_config") {
                val defaultZram = physicalRamGb.coerceIn(1, maxZramLimit)
                Column {
                    AospSectionHeader(title = stringResource(R.string.memory_section_zram_config))
                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                        MemoryFineTunerCard(
                            shapeIndex = 0,
                            shapeCount = 3,
                            icon = Icons.Rounded.DataSaverOn,
                            title = stringResource(R.string.memory_zram_size),
                            description = stringResource(R.string.memory_zram_size_desc),
                            valueText = "${zramSizeGb.roundToInt()} GB",
                            value = zramSizeGb,
                            valueRange = 1f..maxZramLimit.toFloat(),
                            steps = (maxZramLimit - 2).coerceAtLeast(0),
                            stepMinusLabel = "-1 GB",
                            stepResetLabel = "$defaultZram GB",
                            stepPlusLabel = "+1 GB",
                            isAtReset = zramSizeGb.roundToInt() == defaultZram,
                            onValueChange = { zramSizeGb = it },
                            onValueChangeFinished = {
                                coroutineScope.launch {
                                    ConfigRepository.setZramConfig(sizeGb = zramSizeGb.roundToInt())
                                }
                            },
                            onStepMinus = {
                                val next = (zramSizeGb.roundToInt() - 1).coerceIn(1, maxZramLimit)
                                if (next != zramSizeGb.roundToInt()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    zramSizeGb = next.toFloat()
                                    coroutineScope.launch {
                                        ConfigRepository.setZramConfig(sizeGb = next)
                                    }
                                }
                            },
                            onStepReset = {
                                if (zramSizeGb.roundToInt() != defaultZram) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    zramSizeGb = defaultZram.toFloat()
                                    coroutineScope.launch {
                                        ConfigRepository.setZramConfig(sizeGb = defaultZram)
                                    }
                                }
                            },
                            onStepPlus = {
                                val next = (zramSizeGb.roundToInt() + 1).coerceIn(1, maxZramLimit)
                                if (next != zramSizeGb.roundToInt()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    zramSizeGb = next.toFloat()
                                    coroutineScope.launch {
                                        ConfigRepository.setZramConfig(sizeGb = next)
                                    }
                                }
                            }
                        )

                        AospPreferenceItem(
                            index = 1,
                            count = 3,
                            icon = Icons.Rounded.Layers,
                            title = stringResource(R.string.memory_zram_compression),
                            subtitle = stringResource(R.string.memory_zram_compression_desc),
                            valueText = selectedAlgorithm,
                            onClick = { showAlgorithmSheet = true },
                            trailing = { AospChevron() }
                        )

                        MemoryFineTunerCard(
                            shapeIndex = 2,
                            shapeCount = 3,
                            icon = Icons.Rounded.Tune,
                            title = stringResource(R.string.memory_swappiness_title),
                            description = stringResource(R.string.memory_swappiness_desc),
                            valueText = "${swappinessValue.roundToInt()}",
                            value = swappinessValue,
                            valueRange = 0f..200f,
                            stepMinusLabel = "-10",
                            stepResetLabel = "100",
                            stepPlusLabel = "+10",
                            isAtReset = swappinessValue.roundToInt() == 100,
                            onValueChange = { swappinessValue = it },
                            onValueChangeFinished = {
                                coroutineScope.launch {
                                    ConfigRepository.setZramConfig(swappiness = swappinessValue.roundToInt())
                                }
                            },
                            onStepMinus = {
                                val next = (swappinessValue.roundToInt() - 10).coerceIn(0, 200)
                                if (next != swappinessValue.roundToInt()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    swappinessValue = next.toFloat()
                                    coroutineScope.launch {
                                        ConfigRepository.setZramConfig(swappiness = next)
                                    }
                                }
                            },
                            onStepReset = {
                                if (swappinessValue.roundToInt() != 100) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    swappinessValue = 100f
                                    coroutineScope.launch {
                                        ConfigRepository.setZramConfig(swappiness = 100)
                                    }
                                }
                            },
                            onStepPlus = {
                                val next = (swappinessValue.roundToInt() + 10).coerceIn(0, 200)
                                if (next != swappinessValue.roundToInt()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    swappinessValue = next.toFloat()
                                    coroutineScope.launch {
                                        ConfigRepository.setZramConfig(swappiness = next)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item(key = "maintenance") {
                Column {
                    AospSectionHeader(title = stringResource(R.string.memory_section_maintenance))
                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AospPreferenceItem(
                            index = 0,
                            count = 2,
                            icon = Icons.Rounded.CleaningServices,
                            title = stringResource(R.string.memory_action_drop_caches),
                            subtitle = stringResource(R.string.memory_action_drop_caches_desc),
                            onClick = {
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                            Runtime.getRuntime()
                                                .exec(arrayOf("su", "-c", "echo 3 > /proc/sys/vm/drop_caches"))
                                                .waitFor()
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_drop_caches_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            trailing = { AospChevron() }
                        )

                        AospPreferenceItem(
                            index = 1,
                            count = 2,
                            icon = Icons.Rounded.Storage,
                            title = stringResource(R.string.memory_action_fstrim),
                            subtitle = stringResource(R.string.memory_action_fstrim_desc),
                            onClick = {
                                coroutineScope.launch {
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                            Runtime.getRuntime()
                                                .exec(arrayOf("su", "-c", "fstrim -v /data"))
                                                .waitFor()
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_fstrim_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            trailing = { AospChevron() }
                        )
                    }
                }
            }
        }

        if (!isLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    if (showAlgorithmSheet) {
        AospOptionBottomSheet(
            title = stringResource(R.string.dialog_select_compression),
            options = algorithms.map { AospSheetOption(it, it) },
            isSelected = { it.equals(selectedAlgorithm, ignoreCase = true) },
            onSelect = { algo ->
                selectedAlgorithm = algo
                showAlgorithmSheet = false
                coroutineScope.launch {
                    ConfigRepository.setZramConfig(algorithm = algo)
                }
            },
            onDismiss = { showAlgorithmSheet = false }
        )
    }
}

@Composable
private fun MemoryHero(
    physicalRamGb: Int,
    zramSizeGb: Float,
    algorithm: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.onPrimaryContainer.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.memory_status_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = scheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.memory_status_desc),
                        fontSize = 13.sp,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MemoryStat(
                    label = "RAM",
                    value = "$physicalRamGb GB",
                    modifier = Modifier.weight(1f)
                )
                MemoryStat(
                    label = "ZRAM",
                    value = "${zramSizeGb.roundToInt()} GB",
                    modifier = Modifier.weight(1f)
                )
                MemoryStat(
                    label = "ALGO",
                    value = algorithm.uppercase(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MemoryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MemoryFineTunerCard(
    shapeIndex: Int,
    shapeCount: Int,
    icon: ImageVector,
    title: String,
    description: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    stepMinusLabel: String,
    stepResetLabel: String,
    stepPlusLabel: String,
    isAtReset: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onStepMinus: () -> Unit,
    onStepReset: () -> Unit,
    onStepPlus: () -> Unit,
    steps: Int = 0
) {
    Surface(
        shape = aospConnectedShape(index = shapeIndex, count = shapeCount),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = valueText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onStepMinus,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepMinusLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    onClick = onStepReset,
                    shape = CircleShape,
                    color = if (isAtReset) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepResetLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAtReset) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }

                Surface(
                    onClick = onStepPlus,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepPlusLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}