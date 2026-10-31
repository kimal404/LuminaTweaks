package com.luminatweaks.ui.screen

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminatweaks.data.AppProfileConfig
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DisplayRrMode
import com.luminatweaks.model.AppItem

enum class OptionItem(val label: String, val value: String) {
    OFF("Mati", "off"),
    DEFAULT("Default", "default"),
    ON("Hidup", "on");

    companion object {
        fun fromValue(v: String): OptionItem = entries.find { it.value == v } ?: DEFAULT
    }
}

data class RendererOption(val id: String, val label: String)

fun generateTextureBitmap(detailLevel: Int): ImageBitmap {
    val scale = when (detailLevel) {
        0 -> 1
        1 -> 2
        2 -> 4
        3 -> 8
        4 -> 16
        else -> 1
    }
    val width = (96 / scale).coerceAtLeast(4)
    val height = (96 / scale).coerceAtLeast(4)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val wallHeight = (height * 0.62f).toInt()

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixelColor = if (y < wallHeight) {
                val brickRow = y / 4.coerceAtLeast(1)
                val shift = if (brickRow % 2 == 0) 0 else 3
                val isMortar = (y % 4 == 0) || ((x + shift) % 8 == 0)
                if (isMortar) {
                    android.graphics.Color.rgb(65, 65, 65)
                } else {
                    val noise = (x * 13 + y * 7) % 30
                    android.graphics.Color.rgb(145 + noise, 95 + noise / 2, 70 + noise / 3)
                }
            } else {
                val isGrassEdge = y <= wallHeight + 1
                if (isGrassEdge) {
                    val gNoise = (x * 19) % 35
                    android.graphics.Color.rgb(45 + gNoise, 155 + gNoise, 35 + gNoise)
                } else {
                    val dNoise = (x * 11 + y * 13) % 25
                    android.graphics.Color.rgb(85 + dNoise, 55 + dNoise, 35)
                }
            }
            bitmap.setPixel(x, y, pixelColor)
        }
    }
    return bitmap.asImageBitmap()
}

@Composable
fun TextureComparisonPreview(
    detailLevel: Int,
    pixelSharp: Boolean,
    modifier: Modifier = Modifier
) {
    val beforeBitmap = remember { generateTextureBitmap(0) }
    val afterBitmap = remember(detailLevel) { generateTextureBitmap(detailLevel) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
    ) {
        val totalWidth = constraints.maxWidth.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        splitFraction = (splitFraction + dragAmount.x / totalWidth).coerceIn(0.05f, 0.95f)
                    }
                }
        ) {
            val splitPx = totalWidth * splitFraction

            clipRect(left = 0f, top = 0f, right = splitPx, bottom = size.height) {
                drawImage(
                    image = beforeBitmap,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Medium
                )
            }

            clipRect(left = splitPx, top = 0f, right = size.width, bottom = size.height) {
                drawImage(
                    image = afterBitmap,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = if (pixelSharp) FilterQuality.None else FilterQuality.Medium
                )
            }

            drawLine(
                color = Color.White,
                start = Offset(splitPx, 0f),
                end = Offset(splitPx, size.height),
                strokeWidth = 3.dp.toPx()
            )

            drawCircle(
                color = Color.White,
                radius = 16.dp.toPx(),
                center = Offset(splitPx, size.height / 2)
            )
            drawCircle(
                color = Color(0xFF1E1E1E),
                radius = 13.dp.toPx(),
                center = Offset(splitPx, size.height / 2)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "SEBELUM",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "SESUDAH",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeksturRinganScreen(
    appName: String,
    profile: AppProfileConfig,
    onProfileChanged: (AppProfileConfig) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val currentPreset = remember(profile.textureDetail, profile.pixelSharp, profile.hardDistance) {
        when {
            profile.textureDetail == 1 && !profile.pixelSharp && !profile.hardDistance -> "Halus"
            profile.textureDetail == 2 && profile.pixelSharp && !profile.hardDistance -> "Retro"
            profile.textureDetail == 3 && profile.pixelSharp && profile.hardDistance -> "Kotak Total"
            else -> "Kustom"
        }
    }

    val detailLabels = listOf("Asli", "Ringan", "Retro", "Kotak", "Ekstrem")

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tekstur Ringan",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali"
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
                bottom = 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TextureComparisonPreview(
                    detailLevel = profile.textureDetail,
                    pixelSharp = profile.pixelSharp
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "PRESET CEPAT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Halus", "Retro", "Kotak Total", "Kustom").forEach { preset ->
                            val isSelected = currentPreset == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    when (preset) {
                                        "Halus" -> onProfileChanged(
                                            profile.copy(
                                                textureDetail = 1,
                                                pixelSharp = false,
                                                hardDistance = false
                                            )
                                        )
                                        "Retro" -> onProfileChanged(
                                            profile.copy(
                                                textureDetail = 2,
                                                pixelSharp = true,
                                                hardDistance = false
                                            )
                                        )
                                        "Kotak Total" -> onProfileChanged(
                                            profile.copy(
                                                textureDetail = 3,
                                                pixelSharp = true,
                                                hardDistance = true
                                            )
                                        )
                                    }
                                },
                                label = { Text(preset) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Detail Tekstur",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = detailLabels.getOrElse(profile.textureDetail) { "Asli" },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = profile.textureDetail.toFloat(),
                            onValueChange = { newVal ->
                                onProfileChanged(profile.copy(textureDetail = newVal.toInt()))
                            },
                            valueRange = 0f..4f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            detailLabels.forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onProfileChanged(profile.copy(pixelSharp = !profile.pixelSharp))
                            },
                        shape = getConnectedContainerShape(ContainerPosition.Top, outerRadius = 18.dp),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Piksel Tajam",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tepi tekstur kotak-kotak. Matikan untuk efek buram halus.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = profile.pixelSharp,
                                onCheckedChange = { newState ->
                                    onProfileChanged(profile.copy(pixelSharp = newState))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onProfileChanged(profile.copy(hardDistance = !profile.hardDistance))
                            },
                        shape = getConnectedContainerShape(ContainerPosition.Bottom, outerRadius = 18.dp),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Batas Jarak Tegas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Perpindahan detail dekat-jauh jadi patah-patah.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = profile.hardDistance,
                                onCheckedChange = { newState ->
                                    onProfileChanged(profile.copy(hardDistance = newState))
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

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Diubah waktu lagi main? Tutup game lalu buka lagi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Rounded.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Paling kelihatan kalau grafis game di setelan paling rendah.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Aktifkan Tekstur Ringan untuk game ini",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.textureEnabled,
                            onCheckedChange = { newState ->
                                onProfileChanged(profile.copy(textureEnabled = newState))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onProfileChanged(
                                profile.copy(
                                    textureEnabled = false,
                                    textureDetail = 0,
                                    pixelSharp = false,
                                    hardDistance = false
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset ke Asli")
                    }

                    Button(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Selesai")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val packageManager = remember { context.packageManager }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var rrModes by remember { mutableStateOf<List<DisplayRrMode>>(emptyList()) }

    var showLiteModeDialog by remember { mutableStateOf(false) }
    var showRefreshRateDialog by remember { mutableStateOf(false) }
    var showRendererDialog by remember { mutableStateOf(false) }
    var showDndDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rrModes = ConfigRepository.getRefreshRateModes()
    }

    val triStateOptions = remember {
        listOf(
            OptionItem.OFF,
            OptionItem.DEFAULT,
            OptionItem.ON
        )
    }

    val rendererOptions = remember {
        listOf(
            RendererOption("default", "Default"),
            RendererOption("skiavk", "Skia Vulkan"),
            RendererOption("skiavkthreaded", "Skia Vulkan Threaded"),
            RendererOption("skiagl", "Skia OpenGL"),
            RendererOption("skiaglthreaded", "Skia OpenGL Threaded"),
            RendererOption("opengl", "OpenGL ES"),
            RendererOption("openglthreaded", "OpenGL ES Threaded"),
            RendererOption("vulkan", "Vulkan")
        )
    }

    val currentHzLabel = remember(profile.refreshRate, rrModes) {
        if (profile.refreshRate == "default" || profile.refreshRate.isBlank()) {
            "Default"
        } else {
            val matched = rrModes.find { it.id.toString() == profile.refreshRate || it.hz.toString() == profile.refreshRate }
            if (matched != null) "${matched.hz} Hz" else "Mode ${profile.refreshRate}"
        }
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

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "App Profile",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
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
                            contentDescription = "App Info",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                bottom = 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
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
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                    )
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
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Pengaturan di sini hanya berlaku untuk aplikasi ini dan akan mengabaikan pengaturan global yang sudah ada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onProfileChanged(profile.copy(saklarUtama = !profile.saklarUtama))
                            },
                        shape = getConnectedContainerShape(
                            if (profile.saklarUtama) ContainerPosition.Top else ContainerPosition.Single,
                            outerRadius = 18.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Saklar Utama",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = profile.saklarUtama,
                                onCheckedChange = { newState ->
                                    onProfileChanged(profile.copy(saklarUtama = newState))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = profile.saklarUtama,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLiteModeDialog = true },
                                shape = getConnectedContainerShape(ContainerPosition.Middle, outerRadius = 18.dp),
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
                                        imageVector = Icons.Rounded.BatterySaver,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Lite Mode",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Profil gaming yang lebih ringan untuk hemat baterai",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val currentLiteLabel = OptionItem.fromValue(profile.liteMode).label
                                        Text(
                                            text = currentLiteLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showRefreshRateDialog = true },
                                shape = getConnectedContainerShape(ContainerPosition.Middle, outerRadius = 18.dp),
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
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Refresh Rate",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Kunci Hz layar khusus saat game ini dibuka",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = currentHzLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showRendererDialog = true },
                                shape = getConnectedContainerShape(ContainerPosition.Middle, outerRadius = 18.dp),
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
                                        imageVector = Icons.Rounded.Brush,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Graphics Renderer",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Backend render grafik aplikasi",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val currentRendererLabel = rendererOptions.find { it.id == profile.renderer }?.label ?: "Default"
                                        Text(
                                            text = currentRendererLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDndDialog = true },
                                shape = getConnectedContainerShape(ContainerPosition.Middle, outerRadius = 18.dp),
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
                                        imageVector = Icons.Rounded.NotificationsOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "DND saat Gaming",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Matikan notifikasi otomatis saat aplikasi aktif",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val currentDndLabel = OptionItem.fromValue(profile.dnd).label
                                        Text(
                                            text = currentDndLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { inTeksturRinganScreen = true },
                                shape = getConnectedContainerShape(ContainerPosition.Bottom, outerRadius = 18.dp),
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
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Tekstur Ringan",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Tweak resolusi tekstur & LOD untuk dongkrak FPS",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (profile.textureEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val statusText = if (!profile.textureEnabled) {
                                            "Mati"
                                        } else {
                                            when (profile.textureDetail) {
                                                0 -> "Asli"
                                                1 -> "Ringan"
                                                2 -> "Retro"
                                                3 -> "Kotak"
                                                4 -> "Ekstrem"
                                                else -> "Aktif"
                                            }
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (profile.textureEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
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
    }

    if (showLiteModeDialog) {
        AlertDialog(
            onDismissRequest = { showLiteModeDialog = false },
            title = {
                Text(
                    text = "Pilih Lite Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(triStateOptions) { item ->
                        val isSelected = profile.liteMode == item.value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onProfileChanged(profile.copy(liteMode = item.value))
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onProfileChanged(profile.copy(liteMode = item.value))
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLiteModeDialog = false }) {
                    Text("Selesai")
                }
            }
        )
    }

    if (showRefreshRateDialog) {
        AlertDialog(
            onDismissRequest = { showRefreshRateDialog = false },
            title = {
                Text(
                    text = "Pilih Refresh Rate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        val isDefault = profile.refreshRate == "default" || profile.refreshRate.isBlank()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onProfileChanged(profile.copy(refreshRate = "default"))
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isDefault,
                                onClick = {
                                    onProfileChanged(profile.copy(refreshRate = "default"))
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    items(rrModes) { mode ->
                        val isSelected = profile.refreshRate == mode.id.toString() || profile.refreshRate == mode.hz.toString()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onProfileChanged(profile.copy(refreshRate = mode.id.toString()))
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onProfileChanged(profile.copy(refreshRate = mode.id.toString()))
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${mode.hz} Hz",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRefreshRateDialog = false }) {
                    Text("Selesai")
                }
            }
        )
    }

    if (showRendererDialog) {
        AlertDialog(
            onDismissRequest = { showRendererDialog = false },
            title = {
                Text(
                    text = "Pilih Renderer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(rendererOptions) { item ->
                        val isSelected = profile.renderer == item.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onProfileChanged(profile.copy(renderer = item.id))
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onProfileChanged(profile.copy(renderer = item.id))
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRendererDialog = false }) {
                    Text("Selesai")
                }
            }
        )
    }

    if (showDndDialog) {
        AlertDialog(
            onDismissRequest = { showDndDialog = false },
            title = {
                Text(
                    text = "Pilih Mode DND",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(triStateOptions) { item ->
                        val isSelected = profile.dnd == item.value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onProfileChanged(profile.copy(dnd = item.value))
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onProfileChanged(profile.copy(dnd = item.value))
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDndDialog = false }) {
                    Text("Selesai")
                }
            }
        )
    }
}
