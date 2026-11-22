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

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.ui.component.TouchOriginDialog
import com.luminatweaks.ui.component.appBarFade
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.component.rememberDialogOrigin
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.LogUtil
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SettingsSheetOption<T>(
    val value: T,
    val label: String,
    val supporting: String? = null
)

@Composable
fun SettingsScreen(
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToPersonalisasi: (() -> Unit)? = null,
    onOpenSetupWizard: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isFloating = LocalFloatingNavBar.current

    var currentLanguage by remember {
        val lang = getCurrentLanguage(context)
        mutableStateOf(if (lang.startsWith("en")) "en" else "id")
    }

    var showLanguageSheet by remember { mutableStateOf(false) }
    var showLogBottomSheet by remember { mutableStateOf(false) }
    var isGeneratingLog by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showPersonalisasiScreen by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }
    var isUninstalling by remember { mutableStateOf(false) }
    var uninstallOriginPoint by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val uninstallOrigin = rememberDialogOrigin()

    val activeSettingsSubscreen = when {
        showAboutScreen && onNavigateToAbout == null -> "about"
        showPersonalisasiScreen && onNavigateToPersonalisasi == null -> "personalisasi"
        else -> "main"
    }

    LuminaDrillTransition(
        targetState = activeSettingsSubscreen,
        isForward = { _, target -> target != "main" }
    ) { route ->
        when (route) {
            "about" -> AboutScreen(onBackClick = { showAboutScreen = false })
            "personalisasi" -> PersonalisasiScreen(onBackClick = { showPersonalisasiScreen = false })
            else -> {
                val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomPadding = if (isFloating) 112.dp + navInset else 28.dp + navInset
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
                val fadeSurface = MaterialTheme.colorScheme.background

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentWindowInsets = WindowInsets(0.dp),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .appBarFade(fadeSurface, scrollBehavior.state.overlappedFraction)
                                .statusBarsPadding()
                        ) {
                            LargeFlexibleTopAppBar(
                                title = {
                                    Text(
                                        text = stringResource(R.string.title_settings),
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
                        item(key = "personalization") {
                            SettingsSectionHeader(title = stringResource(R.string.section_personalization))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SettingsActionRow(
                                    index = 0,
                                    count = 1,
                                    icon = Icons.Rounded.Palette,
                                    title = stringResource(R.string.personalization_title),
                                    subtitle = stringResource(R.string.settings_personalization_desc),
                                    onClick = {
                                        if (onNavigateToPersonalisasi != null) {
                                            onNavigateToPersonalisasi()
                                        } else {
                                            showPersonalisasiScreen = true
                                        }
                                    }
                                )
                            }
                        }

                        item(key = "others") {
                            SettingsSectionHeader(title = stringResource(R.string.section_others))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SettingsActionRow(
                                    index = 0,
                                    count = 3,
                                    icon = Icons.Rounded.Description,
                                    title = stringResource(R.string.settings_save_log),
                                    subtitle = if (isGeneratingLog) {
                                        stringResource(R.string.settings_save_log_bundling)
                                    } else {
                                        stringResource(R.string.settings_save_log_desc)
                                    },
                                    enabled = !isGeneratingLog,
                                    onClick = { showLogBottomSheet = true }
                                )

                                SettingsActionRow(
                                    index = 1,
                                    count = 3,
                                    icon = Icons.Rounded.Language,
                                    title = stringResource(R.string.settings_language),
                                    subtitle = if (currentLanguage == "en") {
                                        stringResource(R.string.language_english)
                                    } else {
                                        stringResource(R.string.language_indonesian)
                                    },
                                    highlightSubtitle = true,
                                    onClick = { showLanguageSheet = true }
                                )

                                SettingsActionRow(
                                    index = 2,
                                    count = 3,
                                    icon = Icons.Rounded.AutoFixHigh,
                                    title = stringResource(R.string.settings_setup_wizard),
                                    subtitle = stringResource(R.string.settings_setup_wizard_desc),
                                    onClick = onOpenSetupWizard
                                )
                            }
                        }

                        item(key = "about_and_module") {
                            SettingsSectionHeader(title = stringResource(R.string.section_about))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SettingsActionRow(
                                    index = 0,
                                    count = 2,
                                    icon = Icons.Rounded.Info,
                                    title = stringResource(R.string.settings_about),
                                    subtitle = stringResource(
                                        R.string.settings_version_format,
                                        com.luminatweaks.BuildConfig.VERSION_NAME.removeSuffix(".0")
                                    ),
                                    onClick = {
                                        if (onNavigateToAbout != null) {
                                            onNavigateToAbout()
                                        } else {
                                            showAboutScreen = true
                                        }
                                    }
                                )

                                SettingsActionRow(
                                    index = 1,
                                    count = 2,
                                    icon = Icons.Rounded.DeleteForever,
                                    title = stringResource(R.string.settings_module_uninstall_title),
                                    subtitle = stringResource(R.string.settings_module_uninstall_desc),
                                    enabled = !isUninstalling,
                                    accentColor = MaterialTheme.colorScheme.error,
                                    modifier = uninstallOrigin.trackedModifier(),
                                    onClick = {
                                        uninstallOriginPoint = uninstallOrigin.origin
                                        showUninstallDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                TouchOriginDialog(
                    visible = showUninstallDialog,
                    onDismiss = { if (!isUninstalling) showUninstallDialog = false },
                    origin = uninstallOriginPoint
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.settings_module_uninstall_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_module_uninstall_dialog_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showUninstallDialog = false },
                                enabled = !isUninstalling
                            ) {
                                Text(
                                    text = stringResource(R.string.action_cancel),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = {
                                    isUninstalling = true
                                    coroutineScope.launch {
                                        uninstallModule(context)
                                    }
                                },
                                enabled = !isUninstalling
                            ) {
                                Text(
                                    text = if (isUninstalling) {
                                        stringResource(R.string.settings_module_uninstall_processing)
                                    } else {
                                        stringResource(R.string.settings_module_uninstall_confirm)
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                val languages = listOf(
                    "id" to stringResource(R.string.language_indonesian),
                    "en" to stringResource(R.string.language_english)
                )
                SettingsOptionSheet(
                    show = showLanguageSheet,
                    title = stringResource(R.string.dialog_select_language),
                    selectable = true,
                    options = languages.map { SettingsSheetOption(it.first, it.second) },
                    isSelected = { it == currentLanguage },
                    onSelect = { tag ->
                        if (currentLanguage != tag) {
                            currentLanguage = tag
                            coroutineScope.launch {
                                delay(120L)
                                setAppLanguage(context, tag)
                            }
                        }
                    },
                    onDismiss = { showLanguageSheet = false }
                )

                SettingsOptionSheet(
                    show = showLogBottomSheet,
                    title = stringResource(R.string.dialog_log_subsystem),
                    selectable = false,
                    options = listOf(
                        SettingsSheetOption(
                            value = "save",
                            label = stringResource(R.string.settings_save_log),
                            supporting = stringResource(R.string.log_option_save_desc)
                        ),
                        SettingsSheetOption(
                            value = "share",
                            label = stringResource(R.string.log_option_share),
                            supporting = stringResource(R.string.log_option_share_desc)
                        )
                    ),
                    isSelected = { false },
                    onSelect = { action ->
                        isGeneratingLog = true
                        coroutineScope.launch {
                            if (action == "save") {
                                val file = LogUtil.dumpDiagnosticLogs(context, saveToDownloads = true)
                                isGeneratingLog = false
                                if (file != null) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_log_saved, file.name),
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_log_export_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                val file = LogUtil.dumpDiagnosticLogs(context, saveToDownloads = false)
                                isGeneratingLog = false
                                if (file != null) {
                                    val intent = LogUtil.getShareLogIntent(context, file)
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_log_share_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    onDismiss = { showLogBottomSheet = false }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
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
private fun SettingsSwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f, enabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.padding(start = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                interactionSource = interactionSource
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlightSubtitle: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val haptic = LocalHapticFeedback.current
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f, enabled)
    val titleColor = if (accentColor == MaterialTheme.colorScheme.error) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlightSubtitle) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.padding(start = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (accentColor == MaterialTheme.colorScheme.error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun <T> SettingsOptionSheet(
    show: Boolean,
    title: String,
    selectable: Boolean,
    options: List<SettingsSheetOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
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
                    val selected = selectable && isSelected(option.value)
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
                                onSelect(option.value)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                ),
                                color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (option.supporting != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.supporting,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        if (selectable) {
                            SettingsRadioRing(selected = selected)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRadioRing(selected: Boolean) {
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

private suspend fun uninstallModule(context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val p = Runtime.getRuntime().exec("su")
            p.outputStream.bufferedWriter().use { writer ->
                writer.write("touch /data/adb/.config/lumina/daemon.disabled 2>/dev/null || true\n")
                writer.write("if [ -f /data/local/tmp/lumina_watchdog.pid ]; then\n")
                writer.write("  P=\$(cat /data/local/tmp/lumina_watchdog.pid 2>/dev/null)\n")
                writer.write("  if [ -n \"\$P\" ] && grep -qa service.sh \"/proc/\$P/cmdline\" 2>/dev/null; then kill -9 \"\$P\" 2>/dev/null; fi\n")
                writer.write("  rm -f /data/local/tmp/lumina_watchdog.pid\n")
                writer.write("fi\n")
                writer.write("pkill -TERM luminad 2>/dev/null || killall -TERM luminad 2>/dev/null\n")
                writer.write("pkill -TERM lumina-binder 2>/dev/null || killall -TERM lumina-binder 2>/dev/null\n")
                writer.write("sleep 1\n")
                writer.write("pkill -9 luminad 2>/dev/null || killall -9 luminad 2>/dev/null\n")
                writer.write("pkill -9 lumina-binder 2>/dev/null || killall -9 lumina-binder 2>/dev/null\n")
                writer.write("rm -f /dev/socket/lumina.sock /dev/socket/lumina_ipc.sock /dev/socket/lumina_binder.sock\n")
                writer.write("rm -f /data/local/tmp/lumina.sock /data/local/tmp/lumina_ipc.sock /data/local/tmp/lumina_binder.sock\n")
                writer.write("rm -f /data/local/tmp/luminad.lock /data/local/tmp/lumina-binder.lock\n")
                writer.write("rm -rf /data/adb/.config/lumina /data/adb/luminatweaks\n")
                writer.write("rm -rf /data/adb/modules/luminatweaks\n")
                writer.write("rm -rf /data/adb/modules_update/luminatweaks\n")
                writer.write("(sleep 1 && pm uninstall com.luminatweaks) &\n")
                writer.write("exit\n")
                writer.flush()
            }
            if (!p.waitFor(30, TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (_: Exception) {}
    }
    withContext(Dispatchers.Main) {
        (context as? Activity)?.finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

private fun getCurrentLanguage(context: Context): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (locales != null && !locales.isEmpty) {
            locales[0].language
        } else {
            Locale.getDefault().language
        }
    } else {
        val config = context.resources.configuration
        config.locales[0].language
    }
}

private fun setAppLanguage(context: Context, languageTag: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            LocaleList.forLanguageTags(languageTag)
    } else {
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
