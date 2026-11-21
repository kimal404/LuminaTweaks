package com.luminatweaks.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.GameBarConfig
import com.luminatweaks.data.GameBarPrefs
import com.luminatweaks.data.GameBarSide
import com.luminatweaks.model.AppItem
import com.luminatweaks.service.GameBarService
import com.luminatweaks.ui.component.LuminaBackButton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBarSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(GameBarPrefs.load(context)) }
    var showAppPicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun save(updated: GameBarConfig) {
        config = updated
        GameBarPrefs.save(context, updated)
    }

    LaunchedEffect(Unit) {
        if (config.enabled) {
            val granted = GameBarService.ensureOverlayPermission(context)
            if (granted) GameBarService.sync(context, config)
        }
    }

    if (showAppPicker) {
        QuickAppsPickerDialog(
            selected = config.quickApps,
            onDismiss = { showAppPicker = false },
            onToggle = { pkg ->
                val next = if (pkg in config.quickApps) {
                    config.quickApps - pkg
                } else if (config.quickApps.size >= 12) {
                    config.quickApps
                } else {
                    config.quickApps + pkg
                }
                save(config.copy(quickApps = next))
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Game Bar",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        LuminaBackButton(onBack = onBack, contentDescription = "Kembali")
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "hero") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(12.dp).size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Game Bar",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Sidepanel dalam game: profil, FPS, suhu, volume, dan quick apps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                            )
                        }
                    }
                }
            }

            item(key = "header_behavior") {
                GameBarSectionHeader("Perilaku")
            }

            item(key = "group_behavior") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    GameBarSwitchRow(
                        index = 0,
                        count = 2,
                        icon = Icons.Rounded.Settings,
                        title = "Aktifkan Game Bar",
                        subtitle = if (config.enabled) "Siap tampil di dalam game" else "Nonaktif",
                        checked = config.enabled,
                        enabled = !busy,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                busy = true
                                scope.launch {
                                    val granted = GameBarService.ensureOverlayPermission(context)
                                    busy = false
                                    if (granted) {
                                        val updated = config.copy(enabled = true)
                                        save(updated)
                                        GameBarService.sync(context, updated)
                                    }
                                }
                            } else {
                                val updated = config.copy(enabled = false)
                                save(updated)
                                GameBarService.sync(context, updated)
                            }
                        }
                    )
                    GameBarSwitchRow(
                        index = 1,
                        count = 2,
                        icon = Icons.Rounded.SportsEsports,
                        title = "Tampil otomatis saat game",
                        subtitle = "Mengikuti daftar game dan deteksi foreground Lumina",
                        checked = config.autoGame,
                        enabled = !busy,
                        onCheckedChange = { auto ->
                            save(config.copy(autoGame = auto))
                        }
                    )
                }
            }

            item(key = "manual") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GameBarActionButton(
                        icon = if (busy) Icons.Rounded.VisibilityOff else Icons.Rounded.PlayArrow,
                        label = "Tampilkan",
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            busy = true
                            scope.launch {
                                val granted = GameBarService.ensureOverlayPermission(context)
                                busy = false
                                if (granted) GameBarService.showNow(context)
                            }
                        }
                    )
                    GameBarActionButton(
                        icon = Icons.Rounded.VisibilityOff,
                        label = "Sembunyikan",
                        modifier = Modifier.weight(1f),
                        onClick = { GameBarService.hideNow(context) }
                    )
                }
            }

            item(key = "header_layout") {
                GameBarSectionHeader("Tata Letak")
            }

            item(key = "group_layout") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Sisi layar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameBarSideChip(
                            label = "Kiri",
                            selected = config.side == GameBarSide.LEFT,
                            modifier = Modifier.weight(1f),
                            onClick = { save(config.copy(side = GameBarSide.LEFT)) }
                        )
                        GameBarSideChip(
                            label = "Kanan",
                            selected = config.side == GameBarSide.RIGHT,
                            modifier = Modifier.weight(1f),
                            onClick = { save(config.copy(side = GameBarSide.RIGHT)) }
                        )
                    }
                    GameBarSliderSetting(
                        label = "Transparansi panel",
                        value = config.opacity,
                        valueRange = 0.55f..1f,
                        valueLabel = "${(config.opacity * 100).roundToInt()}%",
                        onValueChange = { save(config.copy(opacity = it)) }
                    )
                    GameBarSliderSetting(
                        label = "Panjang handle",
                        value = config.handleHeightDp.toFloat(),
                        valueRange = 88f..220f,
                        valueLabel = "${config.handleHeightDp} dp",
                        onValueChange = { save(config.copy(handleHeightDp = it.roundToInt())) }
                    )
                    GameBarSliderSetting(
                        label = "Ketebalan handle",
                        value = config.handleThicknessDp.toFloat(),
                        valueRange = 12f..32f,
                        valueLabel = "${config.handleThicknessDp} dp",
                        onValueChange = { save(config.copy(handleThicknessDp = it.roundToInt())) }
                    )
                    GameBarSliderSetting(
                        label = "Lebar panel",
                        value = config.panelWidthDp.toFloat(),
                        valueRange = 280f..440f,
                        valueLabel = "${config.panelWidthDp} dp",
                        onValueChange = { save(config.copy(panelWidthDp = it.roundToInt())) }
                    )
                }
            }

            item(key = "header_quick") {
                GameBarSectionHeader("Quick Apps")
            }

            item(key = "group_quick") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (config.quickApps.isEmpty()) {
                            "Belum ada aplikasi dipilih"
                        } else {
                            "${config.quickApps.size} aplikasi dipilih"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Quick Apps")
                    }
                }
            }
        }
    }
}

@Composable
private fun GameBarSectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GameBarSwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = when {
            count <= 1 -> RoundedCornerShape(24.dp)
            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            index == count - 1 -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(0.dp)
        },
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun GameBarActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun GameBarSideChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun GameBarSliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun QuickAppsPickerDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            ConfigRepository.loadInstalledApps(context.packageManager)
                .sortedWith(compareByDescending<AppItem> { it.isGame }.thenBy { it.name.lowercase() })
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Quick Apps", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "Pilih maksimal 12 aplikasi untuk ditampilkan di Game Bar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(apps, key = { it.packageName }) { app ->
                            QuickAppPickerRow(
                                app = app,
                                checked = app.packageName in selected,
                                enabled = app.packageName in selected || selected.size < 12,
                                onToggle = { onToggle(app.packageName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Selesai")
            }
        }
    )
}

@Composable
private fun QuickAppPickerRow(
    app: AppItem,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.name.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { onToggle() }
        )
    }
}
