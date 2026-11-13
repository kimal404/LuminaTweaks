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

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.LogUtil
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToPersonalisasi: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cornerRadius = LocalCornerRadius.current
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

    if (showAboutScreen && onNavigateToAbout == null) {
        AboutScreen(onBackClick = { showAboutScreen = false })
        return
    }

    if (showPersonalisasiScreen && onNavigateToPersonalisasi == null) {
        PersonalisasiScreen(onBackClick = { showPersonalisasiScreen = false })
        return
    }

    val bottomPadding = if (isFloating) {
        108.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
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
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "personalization") {
                SettingsNestedGroup(
                    headerIcon = Icons.Rounded.Palette,
                    headerTitle = stringResource(R.string.section_personalization)
                ) {
                    SettingsNestedActionCard(
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
                SettingsNestedGroup(
                    headerIcon = Icons.Rounded.Tune,
                    headerTitle = stringResource(R.string.section_others)
                ) {
                    SettingsNestedActionCard(
                        icon = Icons.Rounded.Description,
                        title = stringResource(R.string.settings_save_log),
                        subtitle = if (isGeneratingLog) {
                            stringResource(R.string.settings_save_log_bundling)
                        } else {
                            stringResource(R.string.settings_save_log_desc)
                        },
                        enabled = !isGeneratingLog,
                        isLoading = isGeneratingLog,
                        onClick = { showLogBottomSheet = true }
                    )

                    SettingsNestedActionCard(
                        icon = Icons.Rounded.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = if (currentLanguage == "en") {
                            stringResource(R.string.language_english)
                        } else {
                            stringResource(R.string.language_indonesian)
                        },
                        onClick = { showLanguageSheet = true }
                    )
                }
            }

            item(key = "about_and_module") {
                SettingsNestedGroup(
                    headerIcon = Icons.Rounded.Info,
                    headerTitle = stringResource(R.string.section_about)
                ) {
                    SettingsNestedActionCard(
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

                    SettingsNestedActionCard(
                        icon = Icons.Rounded.DeleteForever,
                        title = stringResource(R.string.settings_module_uninstall_title),
                        subtitle = stringResource(R.string.settings_module_uninstall_desc),
                        enabled = !isUninstalling,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
                        accentColor = MaterialTheme.colorScheme.error,
                        onClick = { showUninstallDialog = true }
                    )
                }
            }
        }
    }

    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUninstalling) showUninstallDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.settings_module_uninstall_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_module_uninstall_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
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
            },
            dismissButton = {
                TextButton(
                    onClick = { showUninstallDialog = false },
                    enabled = !isUninstalling
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            shape = RoundedCornerShape(cornerRadius.coerceAtLeast(16.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (showLanguageSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val languages = listOf(
            "id" to stringResource(R.string.language_indonesian),
            "en" to stringResource(R.string.language_english)
        )

        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
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
                    text = stringResource(R.string.dialog_select_language),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    languages.forEachIndexed { index, (tag, label) ->
                        val isSelected = currentLanguage == tag
                        SegmentedListItem(
                            selected = isSelected,
                            onClick = {
                                showLanguageSheet = false
                                if (currentLanguage != tag) {
                                    currentLanguage = tag
                                    coroutineScope.launch {
                                        delay(120L)
                                        setAppLanguage(context, tag)
                                    }
                                }
                            },
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = languages.size
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
                                    if (isSelected) {
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
                                text = label,
                                style = if (isSelected) {
                                    MaterialTheme.typography.titleSmallEmphasized
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLogBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLogBottomSheet = false },
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_log_subsystem),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )

                SettingsNestedActionCard(
                    icon = Icons.Rounded.FileDownload,
                    title = stringResource(R.string.settings_save_log),
                    subtitle = stringResource(R.string.log_option_save_desc),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = {
                        showLogBottomSheet = false
                        isGeneratingLog = true
                        coroutineScope.launch {
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
                        }
                    }
                )

                SettingsNestedActionCard(
                    icon = Icons.Rounded.Share,
                    title = stringResource(R.string.log_option_share),
                    subtitle = stringResource(R.string.log_option_share_desc),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = {
                        showLogBottomSheet = false
                        isGeneratingLog = true
                        coroutineScope.launch {
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
                )
            }
        }
    }
}

@Composable
private fun SettingsNestedGroup(
    headerIcon: ImageVector,
    headerTitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)

    Surface(
        shape = RoundedCornerShape(outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
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
private fun SettingsNestedActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    accentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(innerRadius),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
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
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (accentColor == MaterialTheme.colorScheme.error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private suspend fun uninstallModule(context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val p = Runtime.getRuntime().exec("su")
            p.outputStream.bufferedWriter().use { writer ->
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
            p.waitFor()
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
