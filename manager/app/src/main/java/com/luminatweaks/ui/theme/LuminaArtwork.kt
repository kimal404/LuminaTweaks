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

enum class LuminaArtworkSlot(
    val fileName: String,
    val maxDimensionPx: Int
) {
    HEADER("header_banner.webp", 1440),
    CARD("card_watermark.webp", 1080)
}

data class LuminaArtworkState(
    val headerEnabled: Boolean = false,
    val headerAlpha: Float = 0.42f,
    val hasHeaderFile: Boolean = false,
    val cardEnabled: Boolean = false,
    val cardAlpha: Float = 0.26f,
    val hasCardFile: Boolean = false,
    val revision: Int = 0
)

object LuminaArtworkManager {
    private const val PREFS_NAME = "settings"
    private const val KEY_HEADER_ENABLED = "artwork_header_enabled"
    private const val KEY_HEADER_ALPHA = "artwork_header_alpha"
    private const val KEY_CARD_ENABLED = "artwork_card_enabled"
    private const val KEY_CARD_ALPHA = "artwork_card_alpha"
    private const val KEY_REVISION = "artwork_revision"

    const val DEFAULT_HEADER_ALPHA = 0.42f
    const val MIN_HEADER_ALPHA = 0.15f
    const val MAX_HEADER_ALPHA = 0.78f

    const val DEFAULT_CARD_ALPHA = 0.26f
    const val MIN_CARD_ALPHA = 0.10f
    const val MAX_CARD_ALPHA = 0.55f

    private fun artworkFile(context: Context, slot: LuminaArtworkSlot): File {
        val dir = File(context.filesDir, "artwork")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, slot.fileName)
    }

    fun getState(context: Context): LuminaArtworkState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val headerFile = artworkFile(context, LuminaArtworkSlot.HEADER)
        val cardFile = artworkFile(context, LuminaArtworkSlot.CARD)
        return LuminaArtworkState(
            headerEnabled = prefs.getBoolean(KEY_HEADER_ENABLED, false) && headerFile.exists(),
            headerAlpha = prefs.getFloat(KEY_HEADER_ALPHA, DEFAULT_HEADER_ALPHA)
                .coerceIn(MIN_HEADER_ALPHA, MAX_HEADER_ALPHA),
            hasHeaderFile = headerFile.exists() && headerFile.length() > 0L,
            cardEnabled = prefs.getBoolean(KEY_CARD_ENABLED, false) && cardFile.exists(),
            cardAlpha = prefs.getFloat(KEY_CARD_ALPHA, DEFAULT_CARD_ALPHA)
                .coerceIn(MIN_CARD_ALPHA, MAX_CARD_ALPHA),
            hasCardFile = cardFile.exists() && cardFile.length() > 0L,
            revision = prefs.getInt(KEY_REVISION, 0)
        )
    }

    fun setSlotEnabled(context: Context, slot: LuminaArtworkSlot, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = if (slot == LuminaArtworkSlot.HEADER) KEY_HEADER_ENABLED else KEY_CARD_ENABLED
        val nextRev = prefs.getInt(KEY_REVISION, 0) + 1
        prefs.edit()
            .putBoolean(key, enabled)
            .putInt(KEY_REVISION, nextRev)
            .apply()
    }

    fun setSlotAlpha(context: Context, slot: LuminaArtworkSlot, alpha: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val (key, clamped) = if (slot == LuminaArtworkSlot.HEADER) {
            KEY_HEADER_ALPHA to alpha.coerceIn(MIN_HEADER_ALPHA, MAX_HEADER_ALPHA)
        } else {
            KEY_CARD_ALPHA to alpha.coerceIn(MIN_CARD_ALPHA, MAX_CARD_ALPHA)
        }
        prefs.edit()
            .putFloat(key, clamped)
            .apply()
    }

    fun saveFromUri(context: Context, uri: Uri, slot: LuminaArtworkSlot): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sampleSize = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sampleSize > slot.maxDimensionPx * 2) {
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

            val finalBitmap = if (maxOf(oriented.width, oriented.height) > slot.maxDimensionPx) {
                val scale = slot.maxDimensionPx.toFloat() / maxOf(oriented.width, oriented.height).toFloat()
                val targetW = (oriented.width * scale).toInt().coerceAtLeast(1)
                val targetH = (oriented.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(oriented, targetW, targetH, true).also {
                    if (it !== oriented) oriented.recycle()
                }
            } else {
                oriented
            }

            val outFile = artworkFile(context, slot)
            val compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            FileOutputStream(outFile).use { out ->
                finalBitmap.compress(compressFormat, 90, out)
            }
            finalBitmap.recycle()

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabledKey = if (slot == LuminaArtworkSlot.HEADER) KEY_HEADER_ENABLED else KEY_CARD_ENABLED
            val nextRev = prefs.getInt(KEY_REVISION, 0) + 1
            prefs.edit()
                .putBoolean(enabledKey, true)
                .putInt(KEY_REVISION, nextRev)
                .apply()
            true
        }.getOrDefault(false)
    }

    fun copyHeaderToCard(context: Context): Boolean {
        return runCatching {
            val src = artworkFile(context, LuminaArtworkSlot.HEADER)
            if (!src.exists() || src.length() == 0L) return false
            val dst = artworkFile(context, LuminaArtworkSlot.CARD)
            src.copyTo(dst, overwrite = true)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val nextRev = prefs.getInt(KEY_REVISION, 0) + 1
            prefs.edit()
                .putBoolean(KEY_CARD_ENABLED, true)
                .putInt(KEY_REVISION, nextRev)
                .apply()
            true
        }.getOrDefault(false)
    }

    fun clearSlot(context: Context, slot: LuminaArtworkSlot) {
        val file = artworkFile(context, slot)
        if (file.exists()) {
            runCatching { file.delete() }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabledKey = if (slot == LuminaArtworkSlot.HEADER) KEY_HEADER_ENABLED else KEY_CARD_ENABLED
        val nextRev = prefs.getInt(KEY_REVISION, 0) + 1
        prefs.edit()
            .putBoolean(enabledKey, false)
            .putInt(KEY_REVISION, nextRev)
            .apply()
    }

    fun loadBitmap(context: Context, slot: LuminaArtworkSlot): ImageBitmap? {
        val file = artworkFile(context, slot)
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
