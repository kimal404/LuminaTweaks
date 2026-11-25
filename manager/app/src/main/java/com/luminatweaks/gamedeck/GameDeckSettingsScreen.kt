package com.luminatweaks.gamedeck

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.model.AppItem
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.rememberHeaderScrolled
import com.luminatweaks.ui.component.scrolledHeader
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val deckIconCache = mutableMapOf<String, Drawable?>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDeckSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val headerScrolled by rememberHeaderScrolled(listState)
    var config by remember { mutableStateOf(GameDeckPrefs.load(context)) }
    var showAppPicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun save(updated: GameDeckConfig) {
        config = updated
        GameDeckPrefs.save(context, updated)
        if (updated.enabled) {
            GameDeckService.sync(context, updated)
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            busy = true
            scope.launch {
                val granted = GameDeckService.ensureOverlayPermission(context)
                busy = false
                if (granted) {
                    save(config.copy(enabled = true))
                }
            }
        } else {
            val updated = config.copy(enabled = false)
            config = updated
            GameDeckPrefs.save(context, updated)
            GameDeckService.sync(context, updated)
        }
    }

    if (showAppPicker) {
        DeckAppsPickerDialog(
            selected = config.quickApps,
            onDismiss = { showAppPicker = false },
            onToggle = { pkg ->
                val next = if (pkg in config.quickApps) {
                    config.quickApps - pkg
                } else if (config.quickApps.size >= 8) {
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
                    .scrolledHeader(headerScrolled, MaterialTheme.colorScheme.background)
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
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 110.dp,
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
                            shape = RoundedCornerShape(16.dp),
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
                                text = "Lumina Game Deck",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (config.enabled) "Aktif di dalam game" else "Nonaktif",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                            )
                        }
                        Switch(
                            checked = config.enabled,
                            enabled = !busy,
                            onCheckedChange = { setEnabled(it) }
                        )
                    }
                }
            }

            item(key = "preview") {
                DeckMiniPreview(config)
            }

            item(key = "auto_game") {
                DeckCardContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Otomatis saat game",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (config.autoGame) {
                                    "Muncul otomatis saat bermain game"
                                } else {
                                    "Selalu aktif saat dinyalakan"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.autoGame,
                            onCheckedChange = { save(config.copy(autoGame = it)) }
                        )
                    }
                }
            }

            item(key = "position") {
                DeckCardContainer {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Posisi Dock Rail",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DeckSideChip(
                                label = "Kiri",
                                selected = config.side == GameDeckSide.LEFT,
                                icon = Icons.Rounded.KeyboardArrowLeft,
                                modifier = Modifier.weight(1f),
                                onClick = { save(config.copy(side = GameDeckSide.LEFT)) }
                            )
                            DeckSideChip(
                                label = "Kanan",
                                selected = config.side == GameDeckSide.RIGHT,
                                icon = Icons.Rounded.KeyboardArrowRight,
                                modifier = Modifier.weight(1f),
                                onClick = { save(config.copy(side = GameDeckSide.RIGHT)) }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Transparansi",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(config.opacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = config.opacity.coerceIn(0.45f, 1f),
                            onValueChange = { save(config.copy(opacity = it)) },
                            valueRange = 0.45f..1f
                        )
                    }
                }
            }

            item(key = "quick_apps") {
                DeckCardContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAppPicker = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(9.dp).size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aplikasi Freeform di Dock Rail",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (config.quickApps.isEmpty()) {
                                    "Pilih hingga 8 aplikasi"
                                } else {
                                    "${config.quickApps.size} aplikasi dipilih"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "test_buttons") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeckActionTile(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Tampilkan",
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            busy = true
                            scope.launch {
                                val granted = GameDeckService.ensureOverlayPermission(context)
                                busy = false
                                if (granted) GameDeckService.showNow(context)
                            }
                        }
                    )
                    DeckActionTile(
                        icon = Icons.Rounded.VisibilityOff,
                        label = "Sembunyikan",
                        modifier = Modifier.weight(1f),
                        onClick = { GameDeckService.hideNow(context) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckMiniPreview(config: GameDeckConfig) {
    val scheme = MaterialTheme.colorScheme
    val isLeft = config.side == GameDeckSide.LEFT
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(164.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rail: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier
                        .width(42.dp)
                        .height(134.dp),
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh.copy(alpha = config.opacity)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(1.dp)
                                .background(scheme.outlineVariant)
                        )
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.surfaceContainerHighest)
                            )
                        }
                    }
                }
            }
            val deck: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier
                        .width(178.dp)
                        .height(134.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surfaceContainerHigh.copy(alpha = config.opacity)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CPU 42%", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("120", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                            Text("GPU 58%", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Eco", "Balanced").forEach { label ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(scheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 7.sp)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Performance", "Performance Lite").forEach { label ->
                                val active = label == "Performance"
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) scheme.primary else scheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        fontSize = 7.sp,
                                        color = if (active) scheme.onPrimary else scheme.onSurface
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Refresh rate · Auto", fontSize = 7.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (isLeft) {
                rail()
                deck()
            } else {
                deck()
                rail()
            }
        }
    }
}

@Composable
private fun DeckCardContainer(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        content()
    }
}

@Composable
private fun DeckSideChip(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "deckSideColor"
    )
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DeckActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
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
private fun DeckAppsPickerDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit
) {
    val context = LocalContext.current
    val emptyApps: List<AppItem> = emptyList()
    val emptyIcons: Map<String, ImageBitmap> = emptyMap()
    var apps by remember { mutableStateOf(emptyApps) }
    var loading by remember { mutableStateOf(true) }
    var icons by remember { mutableStateOf(emptyIcons) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val systemMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        apps = withContext(Dispatchers.IO) {
            ConfigRepository.loadInstalledApps(pm)
                .filter { item ->
                    if (item.packageName == selfPkg) return@filter false
                    if (item.packageName in selected) return@filter true
                    if (pm.getLaunchIntentForPackage(item.packageName) == null) return@filter false
                    val info = runCatching { pm.getApplicationInfo(item.packageName, 0) }.getOrNull()
                        ?: return@filter false
                    (info.flags and systemMask) == 0
                }
                .sortedBy { it.name.lowercase() }
        }
        loading = false
    }

    val filteredApps = remember(apps, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) apps else apps.filter {
            it.name.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }

    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        icons = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val result = mutableMapOf<String, ImageBitmap>()
            apps.forEach { app ->
                val drawable = deckIconCache.getOrPut(app.packageName) {
                    runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
                } ?: return@forEach
                runCatching {
                    result[app.packageName] = drawable.toBitmap(72, 72).asImageBitmap()
                }
            }
            result
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Pilih Aplikasi Freeform", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Aplikasi terpilih muncul di bagian bawah Dock Rail dan terbuka dalam mode jendela bebas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text("Cari aplikasi...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val icon = icons[app.packageName]
                            val checked = app.packageName in selected
                            val enabled = checked || selected.size < 8
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) { onToggle(app.packageName) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (icon != null) {
                                        Image(
                                            bitmap = icon,
                                            contentDescription = app.name,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    } else {
                                        Text(
                                            text = app.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
                                    onCheckedChange = { onToggle(app.packageName) }
                                )
                            }
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
