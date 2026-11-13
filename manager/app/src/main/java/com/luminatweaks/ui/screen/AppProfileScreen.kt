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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminatweaks.R
import com.luminatweaks.data.AppProfileConfig
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DisplayRrMode
import com.luminatweaks.model.AppItem
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar

enum class OptionItem(val value: String) {
    OFF("off"),
    DEFAULT("default"),
    ON("on");

    companion object {
        fun fromValue(v: String): OptionItem = entries.find { it.value == v } ?: DEFAULT
    }
}

@Composable
fun OptionItem.getLabel(): String = when (this) {
    OptionItem.OFF -> stringResource(R.string.opt_off)
    OptionItem.DEFAULT -> stringResource(R.string.opt_default)
    OptionItem.ON -> stringResource(R.string.opt_on)
}

data class RendererOption(val id: String, val label: String)

private data class ProfileSheetOption<T>(
    val value: T,
    val label: String
)

@Composable
fun AppProfileScreen(
    app: AppItem,
    profile: AppProfileConfig,
    onProfileChanged: (AppProfileConfig) -> Unit,
    onBack: () -> Unit
) {
    var inTeksturRinganScreen by remember { mutableStateOf(false) }

    if (inTeksturRinganScreen) {
        TeksturRinganScreen(
            appName = app.name,
            profile = profile,
            onProfileChanged = onProfileChanged,
            onBack = { inTeksturRinganScreen = false }
        )
        return
    }

    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val packageManager = remember { context.packageManager }
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)
    val isFloating = LocalFloatingNavBar.current

    var rrModes by remember { mutableStateOf<List<DisplayRrMode>>(emptyList()) }

    var showLiteModeSheet by remember { mutableStateOf(false) }
    var showRefreshRateSheet by remember { mutableStateOf(false) }
    var showRendererSheet by remember { mutableStateOf(false) }
    var showDndSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rrModes = ConfigRepository.getRefreshRateModes()
    }

    val triStateOptions = remember {
        listOf(OptionItem.OFF, OptionItem.DEFAULT, OptionItem.ON)
    }

    val defaultRendererLabel = stringResource(R.string.opt_default)
    val rendererOptions = remember(defaultRendererLabel) {
        listOf(
            RendererOption("default", defaultRendererLabel),
            RendererOption("skiavk", "Skia Vulkan"),
            RendererOption("skiavkthreaded", "Skia Vulkan (Threaded)"),
            RendererOption("skiagl", "Skia OpenGL"),
            RendererOption("skiaglthreaded", "Skia OpenGL (Threaded)"),
            RendererOption("opengl", "OpenGL ES"),
            RendererOption("openglthreaded", "OpenGL ES (Threaded)"),
            RendererOption("vulkan", "Vulkan")
        )
    }

    val isTextureLocked = profile.textureEnabled || profile.textureLod > 0

    val currentHzLabel = if (profile.refreshRate == "default" || profile.refreshRate.isBlank()) {
        stringResource(R.string.opt_default)
    } else {
        val matched = rrModes.find {
            it.id.toString() == profile.refreshRate || it.hz.toString() == profile.refreshRate
        }
        if (matched != null) "${matched.hz} Hz" else stringResource(R.string.profile_mode_format, profile.refreshRate)
    }

    val currentTextureLabel = if (!isTextureLocked) {
        stringResource(R.string.opt_off)
    } else {
        when (profile.textureLod.coerceAtLeast(profile.textureDetail)) {
            1 -> stringResource(R.string.texture_level_1)
            2 -> stringResource(R.string.texture_level_2)
            3 -> stringResource(R.string.texture_level_3)
            4 -> stringResource(R.string.texture_level_4)
            else -> stringResource(R.string.opt_active)
        }
    }

    val currentRendererLabel = if (isTextureLocked) {
        stringResource(R.string.profile_renderer_locked_desc)
    } else {
        rendererOptions.find { it.id == profile.renderer }?.label ?: defaultRendererLabel
    }

    val packageInfo = remember(app.packageName) {
        runCatching { packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
    }

    val versionName = packageInfo?.versionName ?: "1.0.0"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode ?: 1L
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toLong() ?: 1L
    }

    val isMasterEnabled = !profile.disabled && profile.saklarUtama

    val bottomPadding = if (isFloating) {
        110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", app.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.profile_app_info),
                            tint = MaterialTheme.colorScheme.onSurface
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
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "app_header") {
                Surface(
                    shape = RoundedCornerShape(outerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(remember(app.packageName) {
                                    try {
                                        packageManager.getApplicationIcon(app.packageName)
                                    } catch (_: Exception) {
                                        null
                                    }
                                })
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$versionName ($versionCode)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item(key = "override_notice") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.profile_override_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "master_switch") {
                fun toggleMaster(nextEnabled: Boolean) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onProfileChanged(
                        if (nextEnabled) {
                            profile.copy(
                                saklarUtama = true,
                                disabled = false,
                                custom = profile.custom || !ConfigRepository.defaultGames.contains(profile.pkg)
                            )
                        } else {
                            profile.copy(
                                saklarUtama = false,
                                disabled = true,
                                custom = false
                            )
                        }
                    )
                }

                Surface(
                    onClick = { toggleMaster(!isMasterEnabled) },
                    shape = RoundedCornerShape(outerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.profile_master_switch),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isMasterEnabled,
                            onCheckedChange = { toggleMaster(it) },
                            thumbContent = {
                                Icon(
                                    imageVector = if (isMasterEnabled) Icons.Rounded.Check else Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    }
                }
            }

            item(key = "profile_options") {
                AnimatedVisibility(
                    visible = isMasterEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        NestedProfileCardGroup(
                            headerIcon = Icons.Rounded.Speed,
                            headerTitle = stringResource(R.string.profile_section_performance)
                        ) {
                            NestedProfileActionItem(
                                icon = Icons.Rounded.BatterySaver,
                                title = stringResource(R.string.profile_lite_mode),
                                subtitle = OptionItem.fromValue(profile.liteMode).getLabel(),
                                onClick = { showLiteModeSheet = true }
                            )
                        }

                        NestedProfileCardGroup(
                            headerIcon = Icons.Rounded.Brush,
                            headerTitle = stringResource(R.string.profile_section_display_render)
                        ) {
                            NestedProfileActionItem(
                                icon = Icons.Rounded.Tune,
                                title = stringResource(R.string.profile_texture),
                                subtitle = currentTextureLabel,
                                onClick = { inTeksturRinganScreen = true }
                            )

                            NestedProfileActionItem(
                                icon = Icons.Rounded.Speed,
                                title = stringResource(R.string.profile_refresh_rate),
                                subtitle = currentHzLabel,
                                onClick = { showRefreshRateSheet = true }
                            )

                            NestedProfileActionItem(
                                icon = if (isTextureLocked) Icons.Rounded.Lock else Icons.Rounded.Brush,
                                title = stringResource(R.string.profile_renderer),
                                subtitle = currentRendererLabel,
                                enabled = !isTextureLocked,
                                onClick = { showRendererSheet = true }
                            )
                        }

                        NestedProfileCardGroup(
                            headerIcon = Icons.Rounded.Tune,
                            headerTitle = stringResource(R.string.profile_section_additional)
                        ) {
                            NestedProfileActionItem(
                                icon = Icons.Rounded.NotificationsOff,
                                title = stringResource(R.string.profile_dnd),
                                subtitle = OptionItem.fromValue(profile.dnd).getLabel(),
                                onClick = { showDndSheet = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLiteModeSheet) {
        val liteOptions = triStateOptions.map { ProfileSheetOption(it.value, it.getLabel()) }
        ProfileOptionBottomSheet(
            title = stringResource(R.string.dialog_select_lite_mode),
            options = liteOptions,
            isSelected = { it == profile.liteMode },
            onSelect = { value ->
                onProfileChanged(profile.copy(liteMode = value))
                showLiteModeSheet = false
            },
            onDismiss = { showLiteModeSheet = false }
        )
    }

    if (showRefreshRateSheet) {
        val defaultOpt = listOf(ProfileSheetOption("default", stringResource(R.string.opt_default)))
        val modeOpts = rrModes.map { ProfileSheetOption(it.id.toString(), "${it.hz} Hz") }
        ProfileOptionBottomSheet(
            title = stringResource(R.string.dialog_select_refresh_rate),
            options = defaultOpt + modeOpts,
            isSelected = { value ->
                if (value == "default") {
                    profile.refreshRate == "default" || profile.refreshRate.isBlank()
                } else {
                    profile.refreshRate == value ||
                        rrModes.find { it.id.toString() == value }?.hz?.toString() == profile.refreshRate
                }
            },
            onSelect = { value ->
                onProfileChanged(profile.copy(refreshRate = value))
                showRefreshRateSheet = false
            },
            onDismiss = { showRefreshRateSheet = false }
        )
    }

    if (showRendererSheet && !isTextureLocked) {
        ProfileOptionBottomSheet(
            title = stringResource(R.string.dialog_select_renderer),
            options = rendererOptions.map { ProfileSheetOption(it.id, it.label) },
            isSelected = { it.equals(profile.renderer, ignoreCase = true) },
            onSelect = { id ->
                onProfileChanged(profile.copy(renderer = id))
                showRendererSheet = false
            },
            onDismiss = { showRendererSheet = false }
        )
    }

    if (showDndSheet) {
        val dndOptions = triStateOptions.map { ProfileSheetOption(it.value, it.getLabel()) }
        ProfileOptionBottomSheet(
            title = stringResource(R.string.dialog_select_dnd),
            options = dndOptions,
            isSelected = { it == profile.dnd },
            onSelect = { value ->
                onProfileChanged(profile.copy(dnd = value))
                showDndSheet = false
            },
            onDismiss = { showDndSheet = false }
        )
    }
}

@Composable
private fun NestedProfileCardGroup(
    headerIcon: ImageVector,
    headerTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)

    Surface(
        shape = RoundedCornerShape(outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            content()
        }
    }
}

@Composable
private fun NestedProfileActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nestedProfileItemScale"
    )
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(innerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.55f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (enabled) Icons.Rounded.ChevronRight else Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(if (enabled) 20.dp else 18.dp)
            )
        }
    }
}

@Composable
private fun <T> ProfileOptionBottomSheet(
    title: String,
    options: List<ProfileSheetOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets(0.dp) },
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                options.forEachIndexed { index, option ->
                    val selected = isSelected(option.value)
                    SegmentedListItem(
                        selected = selected,
                        onClick = { onSelect(option.value) },
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = options.size
                        ),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        leadingContent = {
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    ) {
                        Text(
                            text = option.label,
                            style = if (selected) {
                                MaterialTheme.typography.titleSmallEmphasized
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSectionTitle(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
