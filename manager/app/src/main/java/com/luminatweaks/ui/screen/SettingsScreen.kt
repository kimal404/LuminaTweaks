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

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showLogBottomSheet by remember { mutableStateOf(false) }
    var isGeneratingLog by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showPersonalisasiScreen by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }
    var isUninstalling by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    if (showAboutScreen && onNavigateToAbout == null) {
        AboutScreen(onBackClick = { showAboutScreen = false })
        return
    }

    if (showPersonalisasiScreen && onNavigateToPersonalisasi == null) {
        PersonalisasiScreen(onBackClick = { showPersonalisasiScreen = false })
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentLanguage == "en") "Settings" else "Pengaturan",
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
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = if (isFloating) 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Personalisasi",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                    )
                    val singleShape = getConnectedContainerShape(ContainerPosition.Single)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(singleShape)
                            .clickable {
                                if (onNavigateToPersonalisasi != null) {
                                    onNavigateToPersonalisasi()
                                } else {
                                    showPersonalisasiScreen = true
                                }
                            },
                        shape = singleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Personalisasi",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tema, warna, dan kustomisasi antarmuka",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Lainnya",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                    )

                    val topShape = getConnectedContainerShape(ContainerPosition.Top)
                    val bottomShape = getConnectedContainerShape(ContainerPosition.Bottom)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(topShape)
                            .clickable(enabled = !isGeneratingLog) { showLogBottomSheet = true },
                        shape = topShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isGeneratingLog) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Simpan Log",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isGeneratingLog) "Sedang membundel berkas log..." else "Ekspor atau bagikan log diagnosis daemon Lumina",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(bottomShape)
                            .clickable { showLanguageDialog = true },
                        shape = bottomShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bahasa",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (currentLanguage == "en") "English" else "Bahasa Indonesia",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Tentang",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                    )
                    val singleShape = getConnectedContainerShape(ContainerPosition.Single)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(singleShape)
                            .clickable {
                                if (onNavigateToAbout != null) {
                                    onNavigateToAbout()
                                } else {
                                    showAboutScreen = true
                                }
                            },
                        shape = singleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tentang LuminaTweaks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Versi ${com.luminatweaks.BuildConfig.VERSION_NAME.removeSuffix(".0")}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Manajemen Modul",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                    )
                    val singleShape = getConnectedContainerShape(ContainerPosition.Single)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(singleShape)
                            .clickable(enabled = !isUninstalling) { showUninstallDialog = true },
                        shape = singleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Copot Modul",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Hapus modul dan seluruh data konfigurasi",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
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
                    text = "Copot Modul",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Seluruh berkas modul, konfigurasi daemon, dan aplikasi Lumina Manager akan dihapus dari sistem.",
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
                        text = if (isUninstalling) "Memproses..." else "Copot",
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
                        text = "Batal",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            shape = RoundedCornerShape(cornerRadius.coerceAtLeast(16.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = {
                Text(
                    text = "Pilih Bahasa",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageOptionItem(
                        title = "Bahasa Indonesia",
                        isSelected = currentLanguage == "id",
                        cornerRadius = cornerRadius,
                        onClick = {
                            showLanguageDialog = false
                            if (currentLanguage != "id") {
                                currentLanguage = "id"
                                coroutineScope.launch {
                                    delay(120L)
                                    setAppLanguage(context, "id")
                                }
                            }
                        }
                    )

                    LanguageOptionItem(
                        title = "English",
                        isSelected = currentLanguage == "en",
                        cornerRadius = cornerRadius,
                        onClick = {
                            showLanguageDialog = false
                            if (currentLanguage != "en") {
                                currentLanguage = "en"
                                coroutineScope.launch {
                                    delay(120L)
                                    setAppLanguage(context, "en")
                                }
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(
                        text = "Batal",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            shape = RoundedCornerShape(cornerRadius.coerceAtLeast(16.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (showLogBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLogBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Log Subsystem",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                LogOptionCard(
                    icon = Icons.Rounded.FileDownload,
                    title = "Simpan Log",
                    subtitle = "Simpan berkas log ke folder Download",
                    cornerRadius = cornerRadius,
                    onClick = {
                        showLogBottomSheet = false
                        isGeneratingLog = true
                        coroutineScope.launch {
                            val file = LogUtil.dumpDiagnosticLogs(context, saveToDownloads = true)
                            isGeneratingLog = false
                            if (file != null) {
                                Toast.makeText(context, "Log tersimpan: Download/${file.name}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Gagal mengekspor log sistem", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                LogOptionCard(
                    icon = Icons.Rounded.Share,
                    title = "Bagikan Log",
                    subtitle = "Bagikan berkas log langsung ke aplikasi lain",
                    cornerRadius = cornerRadius,
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
                                Toast.makeText(context, "Gagal menyiapkan berkas log untuk dibagikan", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
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
                writer.write("killall -9 luminad 2>/dev/null\n")
                writer.write("pkill -9 -f luminad 2>/dev/null\n")
                writer.write("rm -rf /data/adb/.config/lumina /data/adb/luminatweaks\n")
                writer.write("rm -rf /data/adb/modules/luminatweaks\n")
                writer.write("rm -rf /data/adb/modules_update/luminatweaks\n")
                writer.write("rm -f /dev/socket/luminad*\n")
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

@Composable
private fun LanguageOptionItem(
    title: String,
    isSelected: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val itemRadius = (cornerRadius * 0.75f).coerceIn(8.dp, 20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(itemRadius))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(itemRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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

@Composable
private fun LogOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    cornerRadius: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape((cornerRadius * 0.75f).coerceIn(8.dp, 20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
