// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.luminatweaks.ui.screen

import android.content.Context
import android.os.Build
import android.view.WindowManager
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
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.luminatweaks.data.DisplayRrMode
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RendererItem(
    val id: String,
    val labelRes: Int
)

val RENDERER_OPTIONS = listOf(
    RendererItem("default", R.string.tweaks_renderer_system_default),
    RendererItem("skiavk", R.string.opt_default),
    RendererItem("skiavkthreaded", R.string.opt_default),
    RendererItem("skiagl", R.string.opt_default),
    RendererItem("skiaglthreaded", R.string.opt_default),
    RendererItem("opengl", R.string.opt_default),
    RendererItem("openglthreaded", R.string.opt_default),
    RendererItem("vulkan", R.string.opt_default)
)

fun getRendererDisplayLabel(id: String, defaultLabel: String): String {
    return when (id.lowercase()) {
        "default" -> defaultLabel
        "skiavk" -> "Skia Vulkan"
        "skiavkthreaded" -> "Skia Vulkan (Threaded)"
        "skiagl" -> "Skia OpenGL"
        "skiaglthreaded" -> "Skia OpenGL (Threaded)"
        "opengl" -> "OpenGL ES"
        "openglthreaded" -> "OpenGL ES (Threaded)"
        "vulkan" -> "Vulkan"
        else -> id
    }
}

fun getRendererShortLabel(id: String, defaultLabel: String): String {
    return when (id.lowercase()) {
        "default" -> defaultLabel
        "skiavk" -> "Skia Vulkan"
        "skiavkthreaded" -> "Vulkan MT"
        "skiagl" -> "Skia GL"
        "skiaglthreaded" -> "Skia GL MT"
        "opengl" -> "OpenGL"
        "openglthreaded" -> "OpenGL MT"
        "vulkan" -> "Vulkan"
        else -> id
    }
}

private data class TweakOption<T>(
    val value: T,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TweaksScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isIndonesian = remember {
        val lang = Locale.getDefault().language
        lang == "in" || lang == "id"
    }

    val cached = ConfigRepository.cachedTweaksState
    var isGlobalLiteMode by remember { mutableStateOf(cached?.isGlobalLiteMode ?: false) }

    var rrModes by remember { mutableStateOf<List<DisplayRrMode>>(cached?.rrModes ?: emptyList()) }
    var currentRrId by remember { mutableStateOf<Int?>(cached?.currentRrId) }
    var liveRefreshRate by remember { mutableIntStateOf(120) }

    var currentRendererId by remember { mutableStateOf(cached?.currentRendererId ?: "default") }
    var currentIoScheduler by remember { mutableStateOf(cached?.currentIoScheduler ?: "mq-deadline") }

    var showRefreshRateDialog by remember { mutableStateOf(false) }
    var showRenderDialog by remember { mutableStateOf(false) }
    var showIoSchedulerDialog by remember { mutableStateOf(false) }

    var inLiteModeSubpage by remember { mutableStateOf(false) }
    var inMemoryZramSubpage by remember { mutableStateOf(false) }
    var inHardwareDiagnosticSubpage by remember { mutableStateOf(false) }
    var inUtilitySubpage by remember { mutableStateOf(false) }

    val defaultDynamicLabel = stringResource(R.string.tweaks_rr_default_dynamic)
    val defaultRendererLabel = stringResource(R.string.tweaks_renderer_system_default)

    LaunchedEffect(Unit) {
        val state = ConfigRepository.getTweaksState()
        isGlobalLiteMode = state.isGlobalLiteMode
        rrModes = state.rrModes
        currentRrId = state.currentRrId
        currentRendererId = state.currentRendererId
        currentIoScheduler = state.currentIoScheduler
    }

    LaunchedEffect(Unit) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        while (isActive) {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { context.display }.getOrNull() ?: windowManager?.defaultDisplay
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay
            }
            val currentHz = display?.refreshRate?.roundToInt() ?: 60
            if (currentHz > 0) {
                liveRefreshRate = currentHz
            }
            delay(1200L)
        }
    }

    val currentRrText = remember(liveRefreshRate, currentRrId, rrModes, defaultDynamicLabel) {
        val targetMode = rrModes.find { it.id == currentRrId }
        if (targetMode != null && targetMode.hz != liveRefreshRate) {
            "${liveRefreshRate} Hz (${targetMode.hz} Hz)"
        } else {
            "${liveRefreshRate} Hz"
        }
    }

    val currentRendererShortText = remember(currentRendererId, defaultRendererLabel) {
        getRendererShortLabel(currentRendererId, defaultRendererLabel)
    }

    val rendererOptions = remember(defaultRendererLabel) {
        listOf(
            "default",
            "skiavk",
            "skiavkthreaded",
            "skiagl",
            "skiaglthreaded",
            "opengl",
            "openglthreaded",
            "vulkan"
        ).map { TweakOption(it, getRendererDisplayLabel(it, defaultRendererLabel)) }
    }

    if (inLiteModeSubpage) {
        BackHandler { inLiteModeSubpage = false }
        LiteModeScreen(
            onBack = { inLiteModeSubpage = false },
            isLiteMode = isGlobalLiteMode,
            onLiteModeChange = { newState ->
                isGlobalLiteMode = newState
                coroutineScope.launch {
                    ConfigRepository.setGlobalLiteMode(newState)
                }
            }
        )
        return
    }

    if (inHardwareDiagnosticSubpage) {
        BackHandler { inHardwareDiagnosticSubpage = false }
        HardwareDiagnosticScreen(onBack = { inHardwareDiagnosticSubpage = false })
        return
    }

    if (inUtilitySubpage) {
        BackHandler { inUtilitySubpage = false }
        UtilityScreen(onBack = { inUtilitySubpage = false })
        return
    }

    if (inMemoryZramSubpage) {
        BackHandler { inMemoryZramSubpage = false }
        MemoryZramScreen(onBack = { inMemoryZramSubpage = false })
        return
    }

    if (showRefreshRateDialog) {
        TweakOptionDialog(
            title = stringResource(R.string.tweaks_dialog_select_rr),
            options = rrModes.map { TweakOption(it.id, "${it.hz} Hz") },
            isSelected = { it == currentRrId },
            onSelect = { id ->
                currentRrId = id
                showRefreshRateDialog = false
                coroutineScope.launch {
                    ConfigRepository.setRefreshRate(id)
                }
            },
            onDismiss = { showRefreshRateDialog = false }
        )
    }

    if (showRenderDialog) {
        TweakOptionDialog(
            title = stringResource(R.string.tweaks_dialog_select_renderer),
            options = rendererOptions,
            isSelected = { it.equals(currentRendererId, ignoreCase = true) },
            onSelect = { rawId ->
                currentRendererId = rawId
                showRenderDialog = false
                coroutineScope.launch {
                    ConfigRepository.setRenderer(rawId)
                }
            },
            onDismiss = { showRenderDialog = false }
        )
    }

    if (showIoSchedulerDialog) {
        TweakOptionDialog(
            title = stringResource(R.string.dialog_select_io_scheduler),
            options = listOf("none", "mq-deadline", "kyber", "bfq").map { TweakOption(it, it) },
            isSelected = { it.equals(currentIoScheduler, ignoreCase = true) },
            onSelect = { scheduler ->
                currentIoScheduler = scheduler
                showIoSchedulerDialog = false
                coroutineScope.launch {
                    ConfigRepository.setIoScheduler(scheduler)
                }
            },
            onDismiss = { showIoSchedulerDialog = false }
        )
    }

    val isFloating = LocalFloatingNavBar.current
    val bottomPadding = if (isFloating) {
        110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tweaks_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
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
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                TweaksHero(
                    liveRefreshRate = liveRefreshRate,
                    rendererLabel = currentRendererShortText,
                    isLiteMode = isGlobalLiteMode,
                    isIndonesian = isIndonesian
                )
            }

            item {
                TweakSection(
                    title = if (isIndonesian) "Layar & Grafis" else "Display & Graphics",
                    icon = Icons.Rounded.Speed,
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    TweakGroup {
                        TweakRow(
                            icon = Icons.Rounded.Speed,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.tweaks_refresh_rate),
                            subtitle = if (isIndonesian) {
                                "Atur kecepatan penyegaran layar"
                            } else {
                                "Set the display refresh rate"
                            },
                            trailingText = currentRrText,
                            onClick = { showRefreshRateDialog = true }
                        )
                        TweakRowDivider()
                        TweakRow(
                            icon = Icons.Rounded.DeveloperBoard,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.tweaks_renderer),
                            subtitle = if (isIndonesian) {
                                "Pilih backend grafis untuk rendering"
                            } else {
                                "Choose the graphics rendering backend"
                            },
                            trailingText = currentRendererShortText,
                            onClick = { showRenderDialog = true }
                        )
                    }
                }
            }

            item {
                TweakSection(
                    title = if (isIndonesian) "Performa & Boost" else "Performance & Boost",
                    icon = Icons.Rounded.BatterySaver,
                    accent = MaterialTheme.colorScheme.tertiary
                ) {
                    TweakGroup {
                        TweakRow(
                            icon = Icons.Rounded.BatterySaver,
                            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                            title = stringResource(R.string.tweaks_lite_mode),
                            subtitle = if (isGlobalLiteMode) {
                                if (isIndonesian) "Aktif • Efisiensi Core Big/Prime" else "On • Big/Prime core efficiency"
                            } else {
                                if (isIndonesian) "Nonaktif • Jam Clock Maksimum" else "Off • Maximum clock speed"
                            },
                            trailingText = when {
                                isGlobalLiteMode && isIndonesian -> "Aktif"
                                isGlobalLiteMode -> "On"
                                isIndonesian -> "Nonaktif"
                                else -> "Off"
                            },
                            trailingActive = isGlobalLiteMode,
                            onClick = { inLiteModeSubpage = true }
                        )
                    }
                }
            }

            item {
                TweakSection(
                    title = stringResource(R.string.section_additional_tweaks),
                    icon = Icons.Rounded.Tune,
                    accent = MaterialTheme.colorScheme.secondary
                ) {
                    TweakGroup {
                        TweakRow(
                            icon = Icons.Rounded.Memory,
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.tweak_memory_zram),
                            subtitle = stringResource(R.string.tweak_memory_zram_desc),
                            onClick = { inMemoryZramSubpage = true }
                        )
                        TweakRowDivider()
                        TweakRow(
                            icon = Icons.Rounded.Storage,
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.tweak_io_scheduler),
                            subtitle = stringResource(R.string.tweak_io_scheduler_desc),
                            trailingText = currentIoScheduler,
                            onClick = { showIoSchedulerDialog = true }
                        )
                    }
                }
            }

            item {
                TweakSection(
                    title = stringResource(R.string.section_utility_diagnostics),
                    icon = Icons.Rounded.Widgets,
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    TweakGroup {
                        TweakRow(
                            icon = Icons.Rounded.DeveloperBoard,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.hardware_diag_title),
                            subtitle = stringResource(R.string.hardware_diag_desc),
                            onClick = { inHardwareDiagnosticSubpage = true }
                        )
                        TweakRowDivider()
                        TweakRow(
                            icon = Icons.Rounded.Widgets,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.utility_screen_title),
                            subtitle = stringResource(R.string.utility_screen_desc),
                            onClick = { inUtilitySubpage = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TweaksHero(
    liveRefreshRate: Int,
    rendererLabel: String,
    isLiteMode: Boolean,
    isIndonesian: Boolean
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
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isIndonesian) "Pusat Penyetelan" else "Tuning Center",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isIndonesian) {
                                "Atur tampilan dan performa dalam satu tempat"
                            } else {
                                "Tune display and performance in one place"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuickStat(
                        label = "REFRESH",
                        value = "$liveRefreshRate Hz",
                        live = true,
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatDivider()
                    QuickStat(
                        label = "RENDERER",
                        value = rendererLabel,
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatDivider()
                    QuickStat(
                        label = "LITE MODE",
                        value = when {
                            isLiteMode && isIndonesian -> "Aktif"
                            isLiteMode -> "On"
                            isIndonesian -> "Nonaktif"
                            else -> "Off"
                        },
                        valueColor = if (isLiteMode) scheme.primary else scheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
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
        }
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
private fun QuickStatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .width(1.dp)
            .height(30.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

@Composable
private fun TweakSection(
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
private fun TweakGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun TweakRow(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingText: String? = null,
    trailingActive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
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
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingText != null) {
            Spacer(modifier = Modifier.width(10.dp))
            TweakValueChip(text = trailingText, active = trailingActive)
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TweakValueChip(text: String, active: Boolean = false) {
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
private fun TweakRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun <T> TweakOptionDialog(
    title: String,
    options: List<TweakOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.forEach { option ->
                    val selected = isSelected(option.value)
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.value) }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) {
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
