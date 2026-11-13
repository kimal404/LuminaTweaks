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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private fun renderFallbackSceneBitmap(): Bitmap {
    val w = 360
    val h = 216
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val horizonY = h * 0.56f

    paint.shader = LinearGradient(
        0f, 0f, 0f, horizonY,
        intArrayOf(
            android.graphics.Color.rgb(12, 22, 38),
            android.graphics.Color.rgb(22, 48, 78),
            android.graphics.Color.rgb(38, 92, 128)
        ),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w.toFloat(), horizonY, paint)

    paint.shader = LinearGradient(
        0f, horizonY, 0f, h.toFloat(),
        intArrayOf(
            android.graphics.Color.rgb(16, 36, 52),
            android.graphics.Color.rgb(11, 20, 32)
        ),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, horizonY, w.toFloat(), h.toFloat(), paint)
    paint.shader = null

    val sphereCx = w * 0.5f
    val sphereCy = h * 0.48f
    val sphereR = 52f
    paint.shader = RadialGradient(
        sphereCx - sphereR * 0.32f,
        sphereCy - sphereR * 0.34f,
        sphereR * 1.35f,
        intArrayOf(
            android.graphics.Color.rgb(235, 250, 255),
            android.graphics.Color.rgb(92, 196, 248),
            android.graphics.Color.rgb(28, 86, 138),
            android.graphics.Color.rgb(12, 32, 58)
        ),
        floatArrayOf(0f, 0.22f, 0.68f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(sphereCx, sphereCy, sphereR, paint)
    return bmp
}

private fun renderAppIconPreviewBitmap(context: Context, packageName: String): Bitmap {
    if (packageName.isBlank()) return renderFallbackSceneBitmap()
    val iconBitmap = runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap(width = 256, height = 256)
    }.getOrNull() ?: return renderFallbackSceneBitmap()

    val w = 360
    val h = 216
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Soft studio backdrop gradient
    paint.shader = LinearGradient(
        0f, 0f, w.toFloat(), h.toFloat(),
        intArrayOf(
            android.graphics.Color.rgb(14, 24, 38),
            android.graphics.Color.rgb(22, 36, 54),
            android.graphics.Color.rgb(12, 20, 32)
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null

    // Subtle radial spotlight behind app logo
    val cx = w * 0.5f
    val cy = h * 0.5f
    paint.shader = RadialGradient(
        cx, cy, 118f,
        intArrayOf(
            android.graphics.Color.argb(75, 130, 210, 255),
            android.graphics.Color.TRANSPARENT
        ),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawCircle(cx, cy, 118f, paint)
    paint.shader = null

    // Subtle reference grid lines behind icon to make compression changes obvious
    paint.color = android.graphics.Color.argb(26, 200, 230, 255)
    paint.strokeWidth = 1f
    val gridStep = 18f
    var gx = gridStep
    while (gx < w) {
        canvas.drawLine(gx, 0f, gx, h.toFloat(), paint)
        gx += gridStep
    }
    var gy = gridStep
    while (gy < h) {
        canvas.drawLine(0f, gy, w.toFloat(), gy, paint)
        gy += gridStep
    }

    // Center the high-res app logo
    val iconSize = 116f
    val dstRect = RectF(
        cx - iconSize / 2f,
        cy - iconSize / 2f,
        cx + iconSize / 2f,
        cy + iconSize / 2f
    )
    canvas.drawBitmap(iconBitmap, null, dstRect, paint)
    return bmp
}

fun generateTextureBitmap(lod: Int): ImageBitmap {
    val base = renderFallbackSceneBitmap()
    val clampedLod = lod.coerceIn(0, 4)
    if (clampedLod == 0) return base.asImageBitmap()
    val scale = 1 shl clampedLod
    val targetW = (base.width / scale).coerceAtLeast(12)
    val targetH = (base.height / scale).coerceAtLeast(8)
    return Bitmap.createScaledBitmap(base, targetW, targetH, true).asImageBitmap()
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
    val baseScene = remember(packageName) { renderAppIconPreviewBitmap(context, packageName) }
    val beforeBitmap = remember(baseScene) { baseScene.asImageBitmap() }
    val afterBitmap = remember(baseScene, lod, isActive) {
        if (!isActive || lod <= 0) {
            beforeBitmap
        } else {
            val scale = 1 shl lod.coerceIn(1, 4)
            val targetW = (baseScene.width / scale).coerceAtLeast(14)
            val targetH = (baseScene.height / scale).coerceAtLeast(9)
            Bitmap.createScaledBitmap(baseScene, targetW, targetH, true).asImageBitmap()
        }
    }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    val handleWidth = 34.dp
    val handleHeight = 46.dp
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val dividerColor = primaryColor.copy(alpha = 0.92f)
    val cardRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(216.dp),
        shape = RoundedCornerShape(cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
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
                            splitFraction = (splitFraction + dragAmount.x / totalWidth).coerceIn(0.08f, 0.92f)
                        }
                    }
            ) {
                clipRect(left = 0f, top = 0f, right = splitPx, bottom = size.height) {
                    drawImage(
                        image = beforeBitmap,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.High
                    )
                }

                clipRect(left = splitPx, top = 0f, right = size.width, bottom = size.height) {
                    drawImage(
                        image = afterBitmap,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = if (isActive && pixelSharp && lod > 0) FilterQuality.None else FilterQuality.Medium
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
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(primaryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = onPrimaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                        text = stringResource(R.string.texture_badge_original),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isActive) Color(0xFF4CAF50) else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isActive) stringResource(R.string.texture_badge_active) else stringResource(R.string.texture_badge_inactive),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.74f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.texture_drag_compare),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
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
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
