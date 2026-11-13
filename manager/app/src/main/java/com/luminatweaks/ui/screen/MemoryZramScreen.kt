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

package com.luminatweaks.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class MemoryZramInit(
    val physicalRamGb: Int,
    val zramSizeGb: Float,
    val algorithm: String,
    val algorithms: List<String>,
    val swappiness: Float,
    val needsReapply: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryZramScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isFloating = LocalFloatingNavBar.current

    var physicalRamGb by remember { mutableIntStateOf(8) }
    var zramSizeGb by remember { mutableFloatStateOf(8f) }
    var selectedAlgorithm by remember { mutableStateOf("zstd") }
    var algorithms by remember { mutableStateOf(listOf("zstd", "lz4", "lzo-rle", "lzo")) }
    var swappinessValue by remember { mutableFloatStateOf(100f) }

    var showAlgorithmDialog by remember { mutableStateOf(false) }
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

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.memory_zram_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = if (isFloating) {
                    100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                } else {
                    24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                },
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                MemoryHero(
                    physicalRamGb = physicalRamGb,
                    zramSizeGb = zramSizeGb,
                    algorithm = selectedAlgorithm
                )
            }

            item {
                MemorySection(
                    title = stringResource(R.string.memory_section_zram_config),
                    icon = Icons.Rounded.Tune,
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    MemoryGroup {
                        MemorySliderBlock(
                            icon = Icons.Rounded.DataSaverOn,
                            title = stringResource(R.string.memory_zram_size),
                            description = stringResource(R.string.memory_zram_size_desc),
                            valueText = "${zramSizeGb.roundToInt()} GB",
                            value = zramSizeGb,
                            valueRange = 1f..maxZramLimit.toFloat(),
                            steps = (maxZramLimit - 2).coerceAtLeast(0),
                            onValueChange = { zramSizeGb = it },
                            onValueChangeFinished = {
                                coroutineScope.launch {
                                    ConfigRepository.setZramConfig(
                                        sizeGb = zramSizeGb.roundToInt()
                                    )
                                }
                            }
                        )
                        MemoryDivider()
                        MemoryRow(
                            icon = Icons.Rounded.Layers,
                            title = stringResource(R.string.memory_zram_compression),
                            subtitle = stringResource(R.string.memory_zram_compression_desc),
                            valueText = selectedAlgorithm,
                            onClick = { showAlgorithmDialog = true }
                        )
                        MemoryDivider()
                        MemorySliderBlock(
                            icon = Icons.Rounded.Tune,
                            title = stringResource(R.string.memory_swappiness_title),
                            description = stringResource(R.string.memory_swappiness_desc),
                            valueText = "${swappinessValue.roundToInt()}",
                            value = swappinessValue,
                            valueRange = 0f..200f,
                            onValueChange = { swappinessValue = it },
                            onValueChangeFinished = {
                                coroutineScope.launch {
                                    ConfigRepository.setZramConfig(
                                        swappiness = swappinessValue.roundToInt()
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item {
                MemorySection(
                    title = stringResource(R.string.memory_section_maintenance),
                    icon = Icons.Rounded.CleaningServices,
                    accent = MaterialTheme.colorScheme.tertiary
                ) {
                    MemoryGroup {
                        MemoryActionRow(
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
                        MemoryDivider()
                        MemoryActionRow(
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
        }

        if (!isLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    if (showAlgorithmDialog) {
        CompressionDialog(
            algorithms = algorithms,
            selected = selectedAlgorithm,
            onSelect = { algo ->
                selectedAlgorithm = algo
                showAlgorithmDialog = false
                coroutineScope.launch {
                    ConfigRepository.setZramConfig(algorithm = algo)
                }
            },
            onDismiss = { showAlgorithmDialog = false }
        )
    }
}

@Composable
private fun MemoryHero(
    physicalRamGb: Int,
    zramSizeGb: Float,
    algorithm: String
) {
    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = scheme.onSurface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 36.dp, y = (-48).dp)
                    .size(150.dp)
                    .background(scheme.primary.copy(alpha = 0.10f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-40).dp, y = 36.dp)
                    .size(120.dp)
                    .background(scheme.tertiary.copy(alpha = 0.08f), CircleShape)
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.memory_status_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.memory_status_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemoryStat(
                        label = "RAM",
                        value = "$physicalRamGb GB",
                        modifier = Modifier.weight(1f)
                    )
                    MemoryStatDivider()
                    MemoryStat(
                        label = "ZRAM",
                        value = "${zramSizeGb.roundToInt()} GB",
                        modifier = Modifier.weight(1f)
                    )
                    MemoryStatDivider()
                    MemoryStat(
                        label = "ALGO",
                        value = algorithm.uppercase(),
                        valueColor = scheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            ),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MemoryStatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .width(1.dp)
            .height(30.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

@Composable
private fun MemorySection(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.3.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun MemoryGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.coerceAtLeast(24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun MemoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueText: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemoryIcon(
                icon = icon,
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            MemoryValueChip(text = valueText)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MemorySliderBlock(
    icon: ImageVector,
    title: String,
    description: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    steps: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemoryIcon(
                icon = icon,
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            MemoryValueChip(text = valueText, active = true)
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
    }
}

@Composable
private fun MemoryActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemoryIcon(
                icon = icon,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MemoryIcon(icon: ImageVector, container: Color, tint: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MemoryValueChip(text: String, active: Boolean = false) {
    val container = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(shape = CircleShape, color = container) {
        Text(
            text = text,
            modifier = Modifier
                .widthIn(max = 104.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MemoryDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun CompressionDialog(
    algorithms: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_select_compression),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                algorithms.forEach { algo ->
                    val isSelected = algo.equals(selected, ignoreCase = true)
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(algo) }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = algo,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
