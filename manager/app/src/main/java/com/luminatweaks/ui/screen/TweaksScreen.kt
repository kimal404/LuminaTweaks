package com.luminatweaks.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DisplayRrMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RendererItem(
    val id: String,
    val label: String
)

val RENDERER_OPTIONS = listOf(
    RendererItem("default", "System Default"),
    RendererItem("skiavk", "Skia Vulkan"),
    RendererItem("skiavkthreaded", "Skia Vulkan (Threaded)"),
    RendererItem("skiagl", "Skia OpenGL"),
    RendererItem("skiaglthreaded", "Skia OpenGL (Threaded)"),
    RendererItem("opengl", "OpenGL ES"),
    RendererItem("openglthreaded", "OpenGL ES (Threaded)"),
    RendererItem("vulkan", "Vulkan")
)

suspend fun runShellCommand(cmd: String): String = withContext(Dispatchers.IO) {
    try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output.trim()
    } catch (_: Exception) {
        ""
    }
}

suspend fun loadActiveRenderer(): String = withContext(Dispatchers.IO) {
    val fileSaved = runShellCommand("cat /data/adb/luminatweaks/current_renderer.txt 2>/dev/null")
    if (fileSaved.isNotBlank()) return@withContext fileSaved
    val propSaved = runShellCommand("getprop persist.lumina.renderer")
    if (propSaved.isNotBlank()) return@withContext propSaved
    val hwui = runShellCommand("getprop debug.hwui.renderer")
    if (hwui.isNotBlank()) return@withContext hwui
    "default"
}

suspend fun applyRendererSetting(id: String) = withContext(Dispatchers.IO) {
    val cmds = mutableListOf<String>()
    cmds.add("mkdir -p /data/adb/luminatweaks")
    cmds.add("echo '$id' > /data/adb/luminatweaks/current_renderer.txt")
    cmds.add("[ -d /data/adb/modules/luminatweaks ] && echo '$id' > /data/adb/modules/luminatweaks/current_renderer.txt")
    cmds.add("setprop persist.lumina.renderer '$id'")

    if (id == "default") {
        cmds.add("setprop debug.hwui.renderer ''")
        cmds.add("setprop debug.renderengine.backend ''")
        cmds.add("setprop debug.hwui.render_thread ''")
        cmds.add("setprop debug.skia.threaded_mode ''")
        cmds.add("resetprop --delete ro.hwui.use_vulkan 2>/dev/null || resetprop ro.hwui.use_vulkan ''")
    } else {
        cmds.add("setprop debug.hwui.renderer '$id'")
        if (id.contains("threaded")) {
            cmds.add("setprop debug.hwui.render_thread true")
            if (id.contains("skia")) {
                cmds.add("setprop debug.skia.threaded_mode true")
            } else {
                cmds.add("setprop debug.skia.threaded_mode false")
            }
        } else {
            cmds.add("setprop debug.hwui.render_thread false")
            cmds.add("setprop debug.skia.threaded_mode false")
        }

        when (id) {
            "skiavk", "skiavkthreaded", "vulkan" -> {
                cmds.add("setprop debug.renderengine.backend vulkan")
                cmds.add("resetprop ro.hwui.use_vulkan true")
            }
            "skiagl", "skiaglthreaded", "opengl", "openglthreaded" -> {
                cmds.add("setprop debug.renderengine.backend gles")
                cmds.add("resetprop ro.hwui.use_vulkan false")
            }
        }
    }

    runShellCommand(cmds.joinToString(" && "))
}

suspend fun loadFpsgoBypass(): Boolean = withContext(Dispatchers.IO) {
    val fileSaved = runShellCommand("cat /data/adb/luminatweaks/fpsgo_bypass.txt 2>/dev/null")
    if (fileSaved.isNotBlank()) return@withContext fileSaved == "1"
    val propSaved = runShellCommand("getprop persist.lumina.fpsgo_bypass")
    if (propSaved.isNotBlank()) return@withContext propSaved == "1"
    false
}

suspend fun saveFpsgoBypass(enabled: Boolean) = withContext(Dispatchers.IO) {
    val valStr = if (enabled) "1" else "0"
    val cmd = "mkdir -p /data/adb/luminatweaks && echo '$valStr' > /data/adb/luminatweaks/fpsgo_bypass.txt && setprop persist.lumina.fpsgo_bypass '$valStr'"
    runShellCommand(cmd)
}

suspend fun loadGedBoost(): Boolean = withContext(Dispatchers.IO) {
    val fileSaved = runShellCommand("cat /data/adb/luminatweaks/ged_boost.txt 2>/dev/null")
    if (fileSaved.isNotBlank()) return@withContext fileSaved == "1"
    val propSaved = runShellCommand("getprop persist.lumina.ged_boost")
    if (propSaved.isNotBlank()) return@withContext propSaved == "1"
    true
}

suspend fun saveGedBoost(enabled: Boolean) = withContext(Dispatchers.IO) {
    val valStr = if (enabled) "1" else "0"
    val cmd = "mkdir -p /data/adb/luminatweaks && echo '$valStr' > /data/adb/luminatweaks/ged_boost.txt && setprop persist.lumina.ged_boost '$valStr'"
    runShellCommand(cmd)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TweaksScreen() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val coroutineScope = rememberCoroutineScope()

    var isGlobalLiteMode by remember { mutableStateOf(false) }

    var rrModes by remember { mutableStateOf<List<DisplayRrMode>>(emptyList()) }
    var currentRrId by remember { mutableStateOf<Int?>(null) }

    var currentRendererId by remember { mutableStateOf("default") }

    var isMtkDevice by remember { mutableStateOf(false) }
    var isBypassFpsgo by remember { mutableStateOf(false) }
    var isBoostGed by remember { mutableStateOf(true) }

    var showRefreshRateDialog by remember { mutableStateOf(false) }
    var showRenderDialog by remember { mutableStateOf(false) }
    var showMtkSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        isGlobalLiteMode = ConfigRepository.getGlobalLiteMode()
        rrModes = ConfigRepository.getRefreshRateModes()
        currentRrId = ConfigRepository.getCurrentRefreshRateId()
        currentRendererId = loadActiveRenderer()

        val mtkCheck = runShellCommand("[ -e /sys/kernel/fpsgo/common/force_onoff ] && echo 1 || echo 0")
        isMtkDevice = mtkCheck == "1"
        if (isMtkDevice) {
            isBypassFpsgo = loadFpsgoBypass()
            isBoostGed = loadGedBoost()
        }
    }

    val currentRrText = remember(currentRrId, rrModes) {
        if (currentRrId == null) {
            "Default / Dynamic"
        } else {
            val matched = rrModes.find { it.id == currentRrId }
            if (matched != null) "${matched.hz} Hz" else "Mode $currentRrId"
        }
    }

    val currentRendererText = remember(currentRendererId) {
        RENDERER_OPTIONS.find { it.id.equals(currentRendererId, ignoreCase = true) }?.label ?: currentRendererId
    }

    val mtkSummaryText = remember(isBypassFpsgo, isBoostGed) {
        val activeFeatures = mutableListOf<String>()
        if (isBypassFpsgo) activeFeatures.add("Bypass FPSGo")
        if (isBoostGed) activeFeatures.add("GED Boost")
        if (activeFeatures.isEmpty()) "Default Baseline" else activeFeatures.joinToString(" • ")
    }

    if (showRefreshRateDialog) {
        AlertDialog(
            onDismissRequest = { showRefreshRateDialog = false },
            title = {
                Text(
                    text = "Pilih Refresh Rate",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rrModes.forEach { mode ->
                        val isSelected = currentRrId == mode.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        currentRrId = mode.id
                                        showRefreshRateDialog = false
                                        coroutineScope.launch {
                                            ConfigRepository.setRefreshRate(mode.id)
                                        }
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${mode.hz} Hz",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRefreshRateDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showRenderDialog) {
        AlertDialog(
            onDismissRequest = { showRenderDialog = false },
            title = {
                Text(
                    text = "Pilih Graphics Renderer",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RENDERER_OPTIONS.forEach { item ->
                        val isSelected = item.id.equals(currentRendererId, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        currentRendererId = item.id
                                        showRenderDialog = false
                                        coroutineScope.launch {
                                            applyRendererSetting(item.id)
                                        }
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRenderDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showMtkSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMtkSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "MediaTek Engine",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Konfigurasi subsistem frame governor dan akselerasi grafis GPU Mali.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bypass Frame Limiter (FPSGo)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Bypass pembatasan frame rate dinamis saat perangkat hangat. Menjaga FPS tetap stabil, namun suhu perangkat akan meningkat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isBypassFpsgo,
                            onCheckedChange = { newState ->
                                isBypassFpsgo = newState
                                coroutineScope.launch {
                                    saveFpsgoBypass(newState)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mali GED GPU Boost",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Akselerasi frekuensi GPU Mali seketika saat render beban grafis berat untuk memangkas micro-stutter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isBoostGed,
                            onCheckedChange = { newState ->
                                isBoostGed = newState
                                coroutineScope.launch {
                                    saveGedBoost(newState)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "Tweaks Suite",
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 16.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showRefreshRateDialog = true },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Display",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Refresh Rate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentRrText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showRenderDialog = true },
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.DeveloperBoard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Graphics",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Renderer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentRendererText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = getConnectedContainerShape(ContainerPosition.Single, outerRadius = 18.dp),
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
                            imageVector = Icons.Rounded.BatterySaver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lite Mode (Global)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Terapkan profil gaming ringan ke semua aplikasi secara global",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isGlobalLiteMode,
                            onCheckedChange = { newState ->
                                isGlobalLiteMode = newState
                                coroutineScope.launch {
                                    ConfigRepository.setGlobalLiteMode(newState)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            if (isMtkDevice) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMtkSheet = true },
                        shape = getConnectedContainerShape(ContainerPosition.Single, outerRadius = 18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MediaTek Engine",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mtkSummaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
