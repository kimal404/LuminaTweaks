package com.luminatweaks.data

import android.content.Context

enum class GameBarSide(val key: String) {
    LEFT("left"),
    RIGHT("right");

    companion object {
        fun fromKey(raw: String?): GameBarSide =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: LEFT
    }
}

data class GameBarConfig(
    val enabled: Boolean = false,
    val autoGame: Boolean = true,
    val side: GameBarSide = GameBarSide.LEFT,
    val handleHeightDp: Int = 48,
    val handleThicknessDp: Int = 20,
    val handlePositionRatio: Float = 0.5f,
    val handleOpacity: Float = 0.45f,
    val autoDimHandle: Boolean = true,
    val panelWidthDp: Int = 280,
    val opacity: Float = 0.94f,
    val quickApps: Set<String> = emptySet()
)

private fun snapPanelWidth(raw: Int): Int = when {
    raw <= 268 -> 256
    raw <= 292 -> 280
    else -> 304
}

object GameBarPrefs {
    private const val PREF_NAME = "game_bar_prefs"

    fun setRefreshRateHint(context: Context, hz: Int?) {
        val meta = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        if (hz == null) meta.remove("refresh_rate_hint") else meta.putInt("refresh_rate_hint", hz)
        meta.apply()
    }

    fun refreshRateHint(context: Context): Int? {
        val meta = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val value = meta.getInt("refresh_rate_hint", -1)
        return value.takeIf { it > 0 }
    }

    fun load(context: Context): GameBarConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return GameBarConfig(
            enabled = prefs.getBoolean("enabled", false),
            autoGame = prefs.getBoolean("auto_game", true),
            side = GameBarSide.fromKey(prefs.getString("side", GameBarSide.LEFT.key)),
            handleHeightDp = prefs.getInt("handle_height_dp", 48).takeIf { it in 32..72 } ?: 48,
            handleThicknessDp = prefs.getInt("handle_thickness_dp", 20).takeIf { it in 16..28 } ?: 20,
            handlePositionRatio = prefs.getFloat("handle_position_ratio", 0.5f).coerceIn(0.15f, 0.85f),
            handleOpacity = prefs.getFloat("handle_opacity", 0.45f).coerceIn(0.10f, 0.80f),
            autoDimHandle = prefs.getBoolean("auto_dim_handle", true),
            panelWidthDp = snapPanelWidth(prefs.getInt("panel_width_dp", 280)),
            opacity = prefs.getFloat("opacity", 0.94f).coerceIn(0.40f, 1f),
            quickApps = prefs.getStringSet("quick_apps", emptySet()).orEmpty().toSet()
        )
    }

    fun save(context: Context, config: GameBarConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", config.enabled)
            .putBoolean("auto_game", config.autoGame)
            .putString("side", config.side.key)
            .putInt("handle_height_dp", config.handleHeightDp.coerceIn(32, 72))
            .putInt("handle_thickness_dp", config.handleThicknessDp.coerceIn(16, 28))
            .putFloat("handle_position_ratio", config.handlePositionRatio.coerceIn(0.15f, 0.85f))
            .putFloat("handle_opacity", config.handleOpacity.coerceIn(0.10f, 0.80f))
            .putBoolean("auto_dim_handle", config.autoDimHandle)
            .putInt("panel_width_dp", config.panelWidthDp.coerceIn(256, 304))
            .putFloat("opacity", config.opacity.coerceIn(0.40f, 1f))
            .putStringSet("quick_apps", config.quickApps)
            .apply()
    }
}
