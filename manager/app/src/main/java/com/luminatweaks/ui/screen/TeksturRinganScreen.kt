package com.luminatweaks.ui.screen

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.data.AppProfileConfig
import com.luminatweaks.data.ConfigRepository

/**
 * Generates procedural texture bitmap reflecting bidirectional LOD (-3 to +3)
 */
fun generateTextureBitmap(lod: Int): ImageBitmap {
    val scale = when (lod.coerceIn(-3, 3)) {
        -3, -2, -1, 0 -> 1
        1 -> 2
        2 -> 4
        3 -> 8
        else -> 1
    }

    val width = when (lod) {
        -3 -> 128
        -2 -> 112
        else -> (96 / scale).coerceAtLeast(4)
    }
    val height = when (lod) {
        -3 -> 128
        -2 -> 112
        else -> (96 / scale).coerceAtLeast(4)
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val wallHeight = (height * 0.62f).toInt()

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixelColor = if (y < wallHeight) {
                val brickRow = (y / (4 * (if (lod <= -2) 2 else 1))).coerceAtLeast(1)
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

fun getLodBadge(lod: Int): String {
    return when {
        lod == 0 -> "Default"
        lod > 0 -> "+$lod"
        else -> "$lod"
    }
}

@Composable
fun TextureComparisonPreview(
    isAngle: Boolean,
    lod: Int,
    modifier: Modifier = Modifier
) {
    val beforeBitmap = remember { generateTextureBitmap(0) }
    val afterBitmap = remember(lod, isAngle) {
        if (isAngle) generateTextureBitmap(lod) else beforeBitmap
    }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    val handleWidth = 32.dp
    val handleHeight = 46.dp
    val primaryColor = MaterialTheme.colorScheme.primary
    val dividerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141416))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidth = constraints.maxWidth.toFloat()
            val totalHeight = constraints.maxHeight.toFloat()
            val splitPx = totalWidth * splitFraction

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            splitFraction = (splitFraction + dragAmount.x / totalWidth).coerceIn(0.06f, 0.94f)
                        }
                    }
            ) {
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
                        filterQuality = if (isAngle && lod == 3) FilterQuality.None else FilterQuality.Medium
                    )
                }

                drawLine(
                    color = dividerColor,
                    start = Offset(splitPx, 0f),
                    end = Offset(splitPx, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (splitPx - (handleWidth / 2).toPx()).toInt(),
                            y = ((totalHeight / 2) - (handleHeight / 2).toPx()).toInt()
                        )
                    }
                    .size(width = handleWidth, height = handleHeight)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ASLI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isAngle) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isAngle) Color(0xFF4CAF50) else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAngle) "AKTIF" else "NONAKTIF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isAngle) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Geser pemisah untuk membandingkan",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
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

    val primaryAccent = MaterialTheme.colorScheme.primary
    val isAngle = profile.textureDriver.equals("angle", ignoreCase = true)
    val currentLod = profile.textureLod.coerceIn(-3, 3)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Kualitas Tekstur",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAngle) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isAngle) Color(0xFF4CAF50) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAngle) "AKTIF" else "NONAKTIF",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isAngle) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                TextureComparisonPreview(
                    isAngle = isAngle,
                    lod = currentLod
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Driver Grafis",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val nativeBg by animateColorAsState(
                                targetValue = if (!isAngle) primaryAccent else Color.Transparent,
                                animationSpec = tween(200),
                                label = "nativeBg"
                            )
                            val angleBg by animateColorAsState(
                                targetValue = if (isAngle) primaryAccent else Color.Transparent,
                                animationSpec = tween(200),
                                label = "angleBg"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(nativeBg)
                                    .clickable {
                                        onProfileChanged(
                                            profile.copy(
                                                textureDriver = "native",
                                                textureEnabled = false
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Native",
                                    fontWeight = if (!isAngle) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isAngle) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(angleBg)
                                    .clickable {
                                        onProfileChanged(
                                            profile.copy(
                                                textureDriver = "angle",
                                                textureEnabled = true
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ANGLE",
                                    fontWeight = if (isAngle) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAngle) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isAngle) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Menggunakan driver GPU asli",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                var sliderPosition by remember(currentLod) { mutableFloatStateOf(currentLod.toFloat()) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isAngle) 1f else 0.45f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Layers,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Offset Detail Tekstur",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = primaryAccent.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = getLodBadge(currentLod),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryAccent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Slider(
                            value = sliderPosition,
                            onValueChange = { rawVal ->
                                if (isAngle) {
                                    val snapped = if (abs(rawVal) <= 0.3f) 0f else rawVal
                                    sliderPosition = snapped
                                    val targetLod = snapped.roundToInt().coerceIn(-3, 3)
                                    if (targetLod != profile.textureLod) {
                                        onProfileChanged(
                                            profile.copy(
                                                textureLod = targetLod,
                                                textureDetail = targetLod.coerceAtLeast(0)
                                            )
                                        )
                                    }
                                }
                            },
                            onValueChangeFinished = {
                                if (isAngle) {
                                    val finalLod = sliderPosition.roundToInt().coerceIn(-3, 3)
                                    sliderPosition = finalLod.toFloat()
                                    onProfileChanged(
                                        profile.copy(
                                            textureLod = finalLod,
                                            textureDetail = finalLod.coerceAtLeast(0)
                                        )
                                    )
                                }
                            },
                            valueRange = -3f..3f,
                            enabled = isAngle,
                            colors = SliderDefaults.colors(
                                thumbColor = primaryAccent,
                                activeTrackColor = primaryAccent
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tickLabels = listOf("-3", "-2", "-1", "|  0  |", "+1", "+2", "+3")
                            val tickValues = listOf(-3, -2, -1, 0, 1, 2, 3)

                            tickLabels.forEachIndexed { index, label ->
                                val pointVal = tickValues[index]
                                val isSelected = currentLod == pointVal

                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected && isAngle) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected && isAngle) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(enabled = isAngle) {
                                            sliderPosition = pointVal.toFloat()
                                            onProfileChanged(
                                                profile.copy(
                                                    textureLod = pointVal,
                                                    textureDetail = pointVal.coerceAtLeast(0)
                                                )
                                            )
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
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
                        Column {
                            Text(
                                text = "Fitur Eksperimental",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Menggunakan ANGLE runtime (Android 15+). Hanya berlaku untuk game berbasis OpenGL ES — game Vulkan native tidak terpengaruh. Restart game setelah menyimpan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val resetProfile = profile.copy(
                                textureDriver = "native",
                                textureLod = 0,
                                textureEnabled = false,
                                textureDetail = 0,
                                pixelSharp = false,
                                hardDistance = false
                            )
                            onProfileChanged(resetProfile)
                            if (profile.pkg.isNotBlank()) {
                                ConfigRepository.killApp(profile.pkg)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp)
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
                        onClick = {
                            if (profile.pkg.isNotBlank()) {
                                ConfigRepository.killApp(profile.pkg)
                            }
                            onBack()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Selesai", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Alias composable for external callers
@Composable
fun KualitasTeksturScreen(
    appName: String,
    profile: AppProfileConfig,
    onProfileChanged: (AppProfileConfig) -> Unit,
    onBack: () -> Unit
) = TeksturRinganScreen(appName, profile, onProfileChanged, onBack)
