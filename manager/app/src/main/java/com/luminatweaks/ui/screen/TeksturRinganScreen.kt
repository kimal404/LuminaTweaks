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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.luminatweaks.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.luminatweaks.R
import com.luminatweaks.data.AppProfileConfig
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import java.util.Locale

@Composable
private fun textureLocalizedLabel(id: String, en: String): String {
    val isIndonesian = remember {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        lang == "in" || lang == "id"
    }
    return if (isIndonesian) id else en
}

private fun renderFallbackIconBitmap(): Bitmap {
    val size = 256
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = size * 0.5f
    val cy = size * 0.5f
    val r = size * 0.42f

    paint.shader = RadialGradient(
        cx - r * 0.3f,
        cy - r * 0.32f,
        r * 1.35f,
        intArrayOf(
            android.graphics.Color.rgb(224, 246, 255),
            android.graphics.Color.rgb(92, 196, 248),
            android.graphics.Color.rgb(34, 102, 160),
            android.graphics.Color.rgb(18, 48, 82)
        ),
        floatArrayOf(0f, 0.28f, 0.72f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(cx, cy, r, paint)
    return bmp
}

private fun loadCleanAppIconBitmap(context: Context, packageName: String): Bitmap {
    if (packageName.isBlank()) return renderFallbackIconBitmap()
    return runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap(width = 256, height = 256)
    }.getOrElse {
        renderFallbackIconBitmap()
    }
}

fun generateTextureBitmap(lod: Int): ImageBitmap {
    val base = renderFallbackIconBitmap()
    val clampedLod = lod.coerceIn(0, 4)
    if (clampedLod == 0) return base.asImageBitmap()
    val scale = 1 shl clampedLod
    val targetSize = (base.width / scale).coerceAtLeast(12)
    return Bitmap.createScaledBitmap(base, targetSize, targetSize, true).asImageBitmap()
}

@Composable
fun TextureComparisonPreview(
    packageName: String,
    isActive: Boolean,
    lod: Int,
    pixelSharp: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val baseBitmap = remember(packageName) { loadCleanAppIconBitmap(context, packageName) }
    val originalImage = remember(baseBitmap) { baseBitmap.asImageBitmap() }
    val clampedLod = lod.coerceIn(0, 4)
    val scaleDivider = 1 shl clampedLod

    val processedImage = remember(baseBitmap, clampedLod, isActive) {
        if (!isActive || clampedLod <= 0) {
            originalImage
        } else {
            val targetSize = (baseBitmap.width / scaleDivider).coerceAtLeast(12)
            Bitmap.createScaledBitmap(baseBitmap, targetSize, targetSize, true).asImageBitmap()
        }
    }

    var pulseTrigger by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(clampedLod, pixelSharp, isActive) {
        pulseTrigger = 0.92f
        pulseTrigger = 1f
    }
    val previewScale by animateFloatAsState(
        targetValue = pulseTrigger,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "previewIconPulse"
    )

    val resultCardColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "previewResultCardColor"
    )

    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 22.dp)

    val badgeText = if (isActive && clampedLod > 0) {
        val modeName = if (pixelSharp) {
            stringResource(R.string.texture_mode_pixel)
        } else {
            stringResource(R.string.texture_mode_blur)
        }
        "$modeName • 1/${scaleDivider}x"
    } else {
        stringResource(R.string.texture_badge_inactive)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Compare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = textureLocalizedLabel("Pratinjau Tekstur", "Texture Preview"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Kartu Kiri: Asli
                Surface(
                    shape = RoundedCornerShape(innerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            drawImage(
                                image = originalImage,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                filterQuality = FilterQuality.High
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = textureLocalizedLabel("Asli", "Original"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Kartu Kanan: Hasil Filter
                Surface(
                    shape = RoundedCornerShape(innerRadius),
                    color = resultCardColor,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(82.dp)
                                .graphicsLayer {
                                    scaleX = previewScale
                                    scaleY = previewScale
                                }
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            drawImage(
                                image = processedImage,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                filterQuality = if (isActive && pixelSharp && clampedLod > 0) {
                                    FilterQuality.None
                                } else {
                                    FilterQuality.Low
                                }
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ) {
                            Text(
                                text = if (isActive) {
                                    textureLocalizedLabel("Hasil", "Result")
                                } else {
                                    textureLocalizedLabel("Standar", "Default")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TeksturRinganScreen(
    appName: String,
    profile: AppProfileConfig,
    onProfileChanged: (AppProfileConfig) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val haptic = LocalHapticFeedback.current
    val primaryAccent = MaterialTheme.colorScheme.primary
    val currentLod = profile.textureLod.coerceIn(0, 4)
    val isTweakActive = currentLod > 0
    val isFloating = LocalFloatingNavBar.current
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)

    val bottomPadding = if (isFloating) {
        116.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.texture_quality_title),
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
                            contentDescription = stringResource(R.string.action_back)
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
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "preview") {
                TextureComparisonPreview(
                    packageName = profile.pkg,
                    isActive = isTweakActive,
                    lod = currentLod,
                    pixelSharp = profile.pixelSharp
                )
            }

            item(key = "level_config") {
                val levelLabel = when (currentLod) {
                    1 -> stringResource(R.string.texture_level_1)
                    2 -> stringResource(R.string.texture_level_2)
                    3 -> stringResource(R.string.texture_level_3)
                    4 -> stringResource(R.string.texture_level_4)
                    else -> stringResource(R.string.texture_level_default)
                }

                val tierCaptions = listOf(
                    1 to textureLocalizedLabel("Ringan", "Light"),
                    2 to textureLocalizedLabel("Sedang", "Medium"),
                    3 to textureLocalizedLabel("Tinggi", "High"),
                    4 to textureLocalizedLabel("Ekstrem", "Ultra")
                )

                Surface(
                    shape = RoundedCornerShape(outerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.texture_level_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isTweakActive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ) {
                                Text(
                                    text = levelLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTweakActive) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tierCaptions.forEach { (level, caption) ->
                                val isSelected = currentLod == level
                                val tileBg by animateColorAsState(
                                    targetValue = if (isSelected) {
                                        primaryAccent
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    label = "tierBg"
                                )
                                val tileContent by animateColorAsState(
                                    targetValue = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    label = "tierText"
                                )

                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onProfileChanged(
                                            profile.copy(
                                                textureLod = level,
                                                textureDriver = "angle",
                                                textureEnabled = true,
                                                textureDetail = level
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = tileBg,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(68.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = level.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = tileContent
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = caption,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tileContent.copy(alpha = if (isSelected) 0.9f else 0.75f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "filter_mode") {
                val isBlur = !profile.pixelSharp
                val isPixel = profile.pixelSharp

                Surface(
                    shape = RoundedCornerShape(outerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = textureLocalizedLabel("Mode Filter", "Filter Mode"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterPillTile(
                                selected = isBlur,
                                icon = Icons.Rounded.BlurOn,
                                label = stringResource(R.string.texture_mode_blur),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onProfileChanged(profile.copy(pixelSharp = false))
                                },
                                modifier = Modifier.weight(1f)
                            )

                            FilterPillTile(
                                selected = isPixel,
                                icon = Icons.Rounded.GridOn,
                                label = stringResource(R.string.texture_mode_pixel),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onProfileChanged(profile.copy(pixelSharp = true))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item(key = "info_card") {
                Surface(
                    shape = RoundedCornerShape(outerRadius),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                text = stringResource(R.string.texture_info_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.texture_info_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.opt_default),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.texture_action_done), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPillTile(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "filterPillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "filterPillText"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        modifier = modifier.height(54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.Check else icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun KualitasTeksturScreen(
    appName: String,
    profile: AppProfileConfig,
    onProfileChanged: (AppProfileConfig) -> Unit,
    onBack: () -> Unit
) = TeksturRinganScreen(appName, profile, onProfileChanged, onBack)
