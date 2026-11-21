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
    val handleHeightDp: Int = 120,
    val handleThicknessDp: Int = 22,
    val panelWidthDp: Int = 336,
    val opacity: Float = 0.94f,
    val quickApps: Set<String> = emptySet()
)

object GameBarPrefs {
    private const val PREF_NAME = "game_bar_prefs"

    fun load(context: Context): GameBarConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return GameBarConfig(
            enabled = prefs.getBoolean("enabled", false),
            autoGame = prefs.getBoolean("auto_game", true),
            side = GameBarSide.fromKey(prefs.getString("side", GameBarSide.LEFT.key)),
            handleHeightDp = prefs.getInt("handle_height_dp", 120).coerceIn(88, 220),
            handleThicknessDp = prefs.getInt("handle_thickness_dp", 22).coerceIn(12, 32),
            panelWidthDp = prefs.getInt("panel_width_dp", 336).coerceIn(280, 440),
            opacity = prefs.getFloat("opacity", 0.94f).coerceIn(0.55f, 1f),
            quickApps = prefs.getStringSet("quick_apps", emptySet()).orEmpty().toSet()
        )
    }

    fun save(context: Context, config: GameBarConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", config.enabled)
            .putBoolean("auto_game", config.autoGame)
            .putString("side", config.side.key)
            .putInt("handle_height_dp", config.handleHeightDp.coerceIn(88, 220))
            .putInt("handle_thickness_dp", config.handleThicknessDp.coerceIn(12, 32))
            .putInt("panel_width_dp", config.panelWidthDp.coerceIn(280, 440))
            .putFloat("opacity", config.opacity.coerceIn(0.55f, 1f))
            .putStringSet("quick_apps", config.quickApps)
            .apply()
    }
}
