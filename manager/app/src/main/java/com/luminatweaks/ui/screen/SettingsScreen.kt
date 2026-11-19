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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.ui.component.AospChevron
import com.luminatweaks.ui.component.AospConnectedGroup
import com.luminatweaks.ui.component.AospOptionBottomSheet
import com.luminatweaks.ui.component.AospPreferenceItem
import com.luminatweaks.ui.component.AospSectionHeader
import com.luminatweaks.ui.component.AospSheetOption
import com.luminatweaks.ui.component.AospTopBar
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.LogUtil
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToPersonalisasi: (() -> Unit)? = null,
    onOpenSetupWizard: () -> Unit = {}
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
                val bottomPadding = if (isFloating) 100.dp + navInset else 24.dp + navInset
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentWindowInsets = WindowInsets(0.dp),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        AospTopBar(
                            title = stringResource(R.string.title_settings),
                            subtitle = stringResource(R.string.settings_personalization_desc),
                            scrollBehavior = scrollBehavior
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
                        item(key = "personalization") {
                            Column {
                                AospSectionHeader(title = stringResource(R.string.section_personalization))
                                AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    AospPreferenceItem(
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
                                        },
                                        trailing = { AospChevron() }
                                    )
                                }
                            }
                        }

                        item(key = "others") {
                            Column {
                                AospSectionHeader(title = stringResource(R.string.section_others))
                                AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    AospPreferenceItem(
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
                                        isLoading = isGeneratingLog,
                                        onClick = { showLogBottomSheet = true },
                                        trailing = { AospChevron() }
                                    )

                                    AospPreferenceItem(
                                        index = 1,
                                        count = 3,
                                        icon = Icons.Rounded.Language,
                                        title = stringResource(R.string.settings_language),
                                        subtitle = if (currentLanguage == "en") {
                                            stringResource(R.string.language_english)
                                        } else {
                                            stringResource(R.string.language_indonesian)
                                        },
                                        onClick = { showLanguageSheet = true },
                                        trailing = { AospChevron() }
                                    )

                                    AospPreferenceItem(
                                        index = 2,
                                        count = 3,
                                        icon = Icons.Rounded.AutoFixHigh,
                                        title = stringResource(R.string.settings_setup_wizard),
                                        subtitle = stringResource(R.string.settings_setup_wizard_desc),
                                        onClick = onOpenSetupWizard,
                                        trailing = { AospChevron() }
                                    )
                                }
                            }
                        }

                        item(key = "about_and_module") {
                            Column {
                                AospSectionHeader(title = stringResource(R.string.section_about))
                                AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    AospPreferenceItem(
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
                                        },
                                        trailing = { AospChevron() }
                                    )

                                    AospPreferenceItem(
                                        index = 1,
                                        count = 2,
                                        icon = Icons.Rounded.DeleteForever,
                                        title = stringResource(R.string.settings_module_uninstall_title),
                                        subtitle = stringResource(R.string.settings_module_uninstall_desc),
                                        enabled = !isUninstalling,
                                        titleColor = MaterialTheme.colorScheme.error,
                                        iconTint = MaterialTheme.colorScheme.error,
                                        onClick = { showUninstallDialog = true },
                                        trailing = { AospChevron(tint = MaterialTheme.colorScheme.error) }
                                    )
                                }
                            }
                        }

                        item(key = "footer") {
                            Spacer(modifier = Modifier.height(8.dp))
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
                        shape = RoundedCornerShape(cornerRadius.coerceAtLeast(24.dp)),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }

                if (showLanguageSheet) {
                    val languages = listOf(
                        "id" to stringResource(R.string.language_indonesian),
                        "en" to stringResource(R.string.language_english)
                    )
                    AospOptionBottomSheet(
                        title = stringResource(R.string.dialog_select_language),
                        options = languages.map { AospSheetOption(it.first, it.second) },
                        isSelected = { it == currentLanguage },
                        onSelect = { tag ->
                            showLanguageSheet = false
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
                }

                if (showLogBottomSheet) {
                    AospOptionBottomSheet(
                        title = stringResource(R.string.dialog_log_subsystem),
                        options = listOf(
                            AospSheetOption(
                                value = "save",
                                label = stringResource(R.string.settings_save_log),
                                supporting = stringResource(R.string.log_option_save_desc)
                            ),
                            AospSheetOption(
                                value = "share",
                                label = stringResource(R.string.log_option_share),
                                supporting = stringResource(R.string.log_option_share_desc)
                            )
                        ),
                        isSelected = { false },
                        onSelect = { action ->
                            showLogBottomSheet = false
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