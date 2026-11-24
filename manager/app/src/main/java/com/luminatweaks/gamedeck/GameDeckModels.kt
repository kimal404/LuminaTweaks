package com.luminatweaks.gamedeck

import android.content.Context

enum class GameDeckSide(val key: String) {
    LEFT("left"),
    RIGHT("right");

    companion object {
        fun fromKey(raw: String?): GameDeckSide =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: LEFT
    }
}

data class GameDeckConfig(
    val enabled: Boolean = false,
    val autoGame: Boolean = true,
    val side: GameDeckSide = GameDeckSide.LEFT,
    val opacity: Float = 0.94f,
    val quickApps: Set<String> = emptySet()
)

data class GameDeckQuickApp(
    val packageName: String,
    val label: String
)

data class GameDeckUiState(
    val expanded: Boolean = false,
    val side: GameDeckSide = GameDeckSide.LEFT,
    val opacity: Float = 0.94f,
    val fps: Int = 0,
    val frametimeMs: Float = 0f,
    val targetHz: Int = 60,
    val cpuPercent: Int = 0,
    val gpuPercent: Int = 0,
    val tempLabel: String = "--",
    val batteryPercent: Int = 0,
    val profile: String = "Balanced",
    val profileOverride: String = "Auto",
    val profileAutoMode: Boolean = true,
    val rrModes: List<Int> = emptyList(),
    val selectedRrHz: Int? = null,
    val dndEnabled: Boolean = false,
    val quickApps: List<GameDeckQuickApp> = emptyList()
)

object GameDeckPrefs {
    private const val PREF_NAME = "lumina_game_deck_prefs"

    fun load(context: Context): GameDeckConfig {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return GameDeckConfig(
            enabled = sp.getBoolean("enabled", false),
            autoGame = sp.getBoolean("auto_game", true),
            side = GameDeckSide.fromKey(sp.getString("side", GameDeckSide.LEFT.key)),
            opacity = sp.getFloat("opacity", 0.94f).coerceIn(0.45f, 1f),
            quickApps = sp.getStringSet("quick_apps", emptySet()).orEmpty().toSet()
        )
    }

    fun save(context: Context, config: GameDeckConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", config.enabled)
            .putBoolean("auto_game", config.autoGame)
            .putString("side", config.side.key)
            .putFloat("opacity", config.opacity.coerceIn(0.45f, 1f))
            .putStringSet("quick_apps", config.quickApps)
            .apply()
    }

    fun setRefreshRateHint(context: Context, hz: Int?) {
        val editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        if (hz == null) editor.remove("rr_hint") else editor.putInt("rr_hint", hz)
        editor.apply()
    }

    fun refreshRateHint(context: Context): Int? {
        val value = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getInt("rr_hint", -1)
        return value.takeIf { it > 0 }
    }
}
