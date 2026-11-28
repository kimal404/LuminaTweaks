/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.luminatweaks.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

data class LuminaArtworkState(
    val enabled: Boolean = false,
    val hasImage: Boolean = false,
    val alpha: Float = LuminaArtworkManager.DEFAULT_ALPHA,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val zoom: Float = LuminaArtworkManager.DEFAULT_ZOOM,
    val bannerHeightDp: Float = LuminaArtworkManager.DEFAULT_HEIGHT_DP,
    val fadeStrength: Float = LuminaArtworkManager.DEFAULT_FADE,
    val blurDp: Float = 0f,
    val revision: Int = 0
)

object LuminaArtworkManager {
    private const val PREFS_NAME = "settings"
    private const val HEADER_FILE_NAME = "header_banner.webp"
    private const val LEGACY_CARD_FILE_NAME = "card_watermark.webp"
    private const val MAX_DIMENSION_PX = 1440

    private const val KEY_ENABLED = "artwork_header_enabled"
    private const val KEY_ALPHA = "artwork_header_alpha"
    private const val KEY_OFFSET_X = "artwork_header_offset_x"
    private const val KEY_OFFSET_Y = "artwork_header_offset_y"
    private const val KEY_ZOOM = "artwork_header_zoom"
    private const val KEY_HEIGHT_DP = "artwork_header_height_dp"
    private const val KEY_FADE = "artwork_header_fade"
    private const val KEY_BLUR_DP = "artwork_header_blur_dp"
    private const val KEY_REVISION = "artwork_revision"

    const val DEFAULT_ALPHA = 0.46f
    const val MIN_ALPHA = 0.15f
    const val MAX_ALPHA = 0.85f

    const val DEFAULT_ZOOM = 1.0f
    const val MIN_ZOOM = 1.0f
    const val MAX_ZOOM = 2.4f

    const val DEFAULT_HEIGHT_DP = 268f
    const val MIN_HEIGHT_DP = 200f
    const val MAX_HEIGHT_DP = 340f

    const val DEFAULT_FADE = 0.65f
    const val MIN_FADE = 0.20f
    const val MAX_FADE = 1.0f

    const val MIN_BLUR_DP = 0f
    const val MAX_BLUR_DP = 20f

    private fun artworkDir(context: Context): File {
        val dir = File(context.filesDir, "artwork")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun headerFile(context: Context): File =
        File(artworkDir(context), HEADER_FILE_NAME)

    fun getState(context: Context): LuminaArtworkState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val file = headerFile(context)
        val exists = file.exists() && file.length() > 0L
        return LuminaArtworkState(
            enabled = prefs.getBoolean(KEY_ENABLED, false) && exists,
            hasImage = exists,
            alpha = prefs.getFloat(KEY_ALPHA, DEFAULT_ALPHA).coerceIn(MIN_ALPHA, MAX_ALPHA),
            offsetX = prefs.getFloat(KEY_OFFSET_X, 0f).coerceIn(-1f, 1f),
            offsetY = prefs.getFloat(KEY_OFFSET_Y, 0f).coerceIn(-1f, 1f),
            zoom = prefs.getFloat(KEY_ZOOM, DEFAULT_ZOOM).coerceIn(MIN_ZOOM, MAX_ZOOM),
            bannerHeightDp = prefs.getFloat(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP)
                .coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP),
            fadeStrength = prefs.getFloat(KEY_FADE, DEFAULT_FADE).coerceIn(MIN_FADE, MAX_FADE),
            blurDp = prefs.getFloat(KEY_BLUR_DP, 0f).coerceIn(MIN_BLUR_DP, MAX_BLUR_DP),
            revision = prefs.getInt(KEY_REVISION, 0)
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextRev = prefs.getInt(KEY_REVISION, 0) + 1
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_REVISION, nextRev)
            .apply()
    }

    fun setAlpha(context: Context, alpha: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_ALPHA, alpha.coerceIn(MIN_ALPHA, MAX_ALPHA))
            .apply()
    }

    fun updateTransform(context: Context, offsetX: Float, offsetY: Float, zoom: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_OFFSET_X, offsetX.coerceIn(-1f, 1f))
            .putFloat(KEY_OFFSET_Y, offsetY.coerceIn(-1f, 1f))
            .putFloat(KEY_ZOOM, zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
            .apply()
    }

    fun setBannerHeight(context: Context, heightDp: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_HEIGHT_DP, heightDp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP))
            .apply()
    }

    fun setFadeStrength(context: Context, fade: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_FADE, fade.coerceIn(MIN_FADE, MAX_FADE))
            .apply()
    }

    fun setBlurDp(context: Context, blurDp: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_BLUR_DP, blurDp.coerceIn(MIN_BLUR_DP, MAX_BLUR_DP))
            .apply()
    }

    fun resetTransform(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_OFFSET_X, 0f)
            .putFloat(KEY_OFFSET_Y, 0f)
            .putFloat(KEY_ZOOM, DEFAULT_ZOOM)
            .putFloat(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP)
            .putFloat(KEY_FADE, DEFAULT_FADE)
            .putFloat(KEY_BLUR_DP, 0f)
            .apply()
    }

    fun saveFromUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sampleSize = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return false

            val rotationDegrees = resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val oriented = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    rawBitmap.width,
                    rawBitmap.height,
                    matrix,
                    true
                ).also {
                    if (it !== rawBitmap) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            val finalBitmap = if (maxOf(oriented.width, oriented.height) > MAX_DIMENSION_PX) {
                val scale = MAX_DIMENSION_PX.toFloat() / maxOf(oriented.width, oriented.height).toFloat()
                val targetW = (oriented.width * scale).toInt().coerceAtLeast(1)
                val targetH = (oriented.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(oriented, targetW, targetH, true).also {
                    if (it !== oriented) oriented.recycle()
                }
            } else {
                oriented
            }

            val outFile = headerFile(context)
            val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            FileOutputStream(outFile).use { out ->
                finalBitmap.compress(compressFormat, 92, out)
            }
            finalBitmap.recycle()

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val nextRev = prefs.getInt(KEY_REVISION, 0) + 1
            prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putFloat(KEY_OFFSET_X, 0f)
                .putFloat(KEY_OFFSET_Y, 0f)
                .putFloat(KEY_ZOOM, DEFAULT_ZOOM)
                .putInt(KEY_REVISION, nextRev)
                .apply()
            true
        }.getOrDefault(false)
    }

    fun clear(context: Context) {
        val file = headerFile(context)
        if (file.exists()) runCatching { file.delete() }
        val legacyCard = File(artworkDir(context), LEGACY_CARD_FILE_NAME)
        if (legacyCard.exists()) runCatching { legacyCard.delete() }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextRev = prefs.getInt(KEY_REVISION, 0) + 1
        prefs.edit()
            .putBoolean(KEY_ENABLED, false)
            .putInt(KEY_REVISION, nextRev)
            .apply()
    }

    fun loadBitmap(context: Context): ImageBitmap? {
        val file = headerFile(context)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }
}

@Composable
fun rememberLuminaArtworkState(): LuminaArtworkState {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf(LuminaArtworkManager.getState(context)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            state = LuminaArtworkManager.getState(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}
