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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.appBarFade
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.component.rememberSliderHaptics
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (isFloating) 112.dp + navInset else 28.dp + navInset
    val fadeSurface = MaterialTheme.colorScheme.background

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .appBarFade(fadeSurface) { scrollBehavior.state.overlappedFraction }
                    .statusBarsPadding()
            ) {
                LargeFlexibleTopAppBar(
                    navigationIcon = { LuminaBackButton(onBack = onBack) },
                    title = {
                        Text(
                            text = stringResource(R.string.memory_zram_title),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    scrollBehavior = scrollBehavior,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item(key = "info_banner") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MemorySquircleIcon(icon = Icons.Rounded.Info)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.memory_status_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "header_zram") {
                MemorySectionHeader(title = stringResource(R.string.memory_section_zram_config))
            }

            item(key = "group_zram") {
                val defaultZram = physicalRamGb.coerceIn(1, maxZramLimit)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MemoryFineTunerRow(
                        index = 0,
                        count = 3,
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

                    MemoryActionRow(
                        index = 1,
                        count = 3,
                        icon = Icons.Rounded.Layers,
                        title = stringResource(R.string.memory_zram_compression),
                        subtitle = selectedAlgorithm,
                        highlightSubtitle = true,
                        onClick = { showAlgorithmSheet = true }
                    )

                    MemoryFineTunerRow(
                        index = 2,
                        count = 3,
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

            item(key = "header_maintenance") {
                MemorySectionHeader(title = stringResource(R.string.memory_section_maintenance))
            }

            item(key = "group_maintenance") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MemoryActionRow(
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
                        }
                    )

                    MemoryActionRow(
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
                        }
                    )
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

    ZramAlgorithmSheet(
        show = showAlgorithmSheet,
        title = stringResource(R.string.dialog_select_compression),
        options = algorithms,
        selectedValue = selectedAlgorithm,
        onSelect = { algo ->
            selectedAlgorithm = algo
            coroutineScope.launch {
                ConfigRepository.setZramConfig(algorithm = algo)
            }
        },
        onDismiss = { showAlgorithmSheet = false }
    )
}

@Composable
private fun MemorySectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(
            start = 12.dp,
            end = 12.dp,
            top = 16.dp,
            bottom = 8.dp
        )
    )
}

@Composable
private fun MemorySquircleIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun MemoryActionRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlightSubtitle: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            MemorySquircleIcon(icon = icon)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlightSubtitle) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Box(
            modifier = Modifier.padding(start = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MemoryFineTunerRow(
    index: Int,
    count: Int,
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
    val shape = connectedContainerShape(index = index, count = count)
    val sliderHaptics = rememberSliderHaptics()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemorySquircleIcon(icon = icon)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = valueText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Slider(
            value = value,
            onValueChange = { raw ->
                sliderHaptics(raw, valueRange, steps)
                onValueChange(raw)
            },
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
            val minusSource = remember { MutableInteractionSource() }
            val minusPress = pressScale(minusSource, 0.92f)
            Surface(
                onClick = onStepMinus,
                interactionSource = minusSource,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .then(minusPress)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepMinusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val resetSource = remember { MutableInteractionSource() }
            val resetPress = pressScale(resetSource, 0.92f)
            Surface(
                onClick = onStepReset,
                interactionSource = resetSource,
                shape = CircleShape,
                color = if (isAtReset) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier
                    .weight(1f)
                    .then(resetPress)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepResetLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAtReset) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            }

            val plusSource = remember { MutableInteractionSource() }
            val plusPress = pressScale(plusSource, 0.92f)
            Surface(
                onClick = onStepPlus,
                interactionSource = plusSource,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .then(plusPress)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepPlusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ZramAlgorithmSheet(
    show: Boolean,
    title: String,
    options: List<String>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scheme = MaterialTheme.colorScheme

    val hideSheet: (() -> Unit) -> Unit = { afterHide ->
        scope.launch {
            try {
                sheetState.hide()
            } catch (_: CancellationException) {
                return@launch
            }
            if (!sheetState.isVisible) {
                currentOnDismiss()
                afterHide()
            }
        }
    }

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = currentOnDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = if (scheme.surfaceContainerLowest == Color.Black) {
            Color.Black.copy(alpha = 0.5f)
        } else {
            scheme.scrim.copy(alpha = 0.32f)
        },
        shape = sheetShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0.dp) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sheetShape)
                .background(scheme.surfaceContainerLow)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(options) { index, option ->
                    val selected = option.equals(selectedValue, ignoreCase = true)
                    val rowShape = RoundedCornerShape(
                        topStart = if (index == 0) 20.dp else 0.dp,
                        topEnd = if (index == 0) 20.dp else 0.dp,
                        bottomStart = if (index == options.lastIndex) 20.dp else 0.dp,
                        bottomEnd = if (index == options.lastIndex) 20.dp else 0.dp
                    )
                    val haptic = LocalHapticFeedback.current
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressScaleModifier = pressScale(interactionSource, 0.97f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(pressScaleModifier)
                            .clip(rowShape)
                            .background(
                                if (selected) scheme.secondaryContainer else scheme.surfaceContainer
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                hideSheet {}
                                onSelect(option)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            ),
                            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        ZramRadioRing(selected = selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZramRadioRing(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val ringColor = if (selected) scheme.primary else scheme.onSurfaceVariant

    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            val radius = (size.width / 2f) - (stroke / 2f)
            drawCircle(
                color = ringColor,
                radius = radius,
                style = Stroke(width = stroke)
            )
            if (selected) {
                drawCircle(
                    color = scheme.primary,
                    radius = (size.width / 2f) * 0.3f
                )
            }
        }
    }
}
