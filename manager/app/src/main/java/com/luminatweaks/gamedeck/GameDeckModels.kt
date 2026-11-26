package com.luminatweaks.gamedeck


import android.content.Context
import android.graphics.Bitmap
import com.luminatweaks.ui.screen.FloatingFpsConfig

val DEFAULT_DASHBOARD_WIDGET_ORDER = listOf("telemetry", "profile", "music", "refresh_rate", "sliders")

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
    val quickApps: Set<String> = emptySet(),
    val edgeYOffsetPx: Int = 0
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
    val isCharging: Boolean = false,
    val isBatterySaver: Boolean = false,
    val rrModes: List<Int> = emptyList(),
    val selectedRrHz: Int? = null,
    val brightnessPercent: Int = 50,
    val volumePercent: Int = 50,
    val volumeMuted: Boolean = false,
    val dndEnabled: Boolean = false,
    val autoBrightnessLocked: Boolean = false,
    val rotationLocked: Boolean = false,
    val ramStatusLabel: String = "Pangkas latar",
    val showTelemetryWidget: Boolean = true,
    val showProfileWidget: Boolean = true,
    val showMusicWidget: Boolean = true,
    val showRefreshRateWidget: Boolean = true,
    val showKeepScreenOnWidget: Boolean = true,
    val showSlidersWidget: Boolean = true,
    val widgetOrder: List<String> = DEFAULT_DASHBOARD_WIDGET_ORDER,
    val keepScreenOn: Boolean = false,
    val musicTitle: String = "",
    val musicArtist: String = "",
    val musicPackage: String? = null,
    val musicArt: Bitmap? = null,
    val isMusicPlaying: Boolean = false,
    val musicPositionMs: Long = 0L,
    val musicDurationMs: Long = 0L,
    val floatingHud: FloatingFpsConfig = FloatingFpsConfig(),
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
            quickApps = sp.getStringSet("quick_apps", emptySet()).orEmpty().toSet(),
            edgeYOffsetPx = sp.getInt("edge_y_offset_px", 0)
        )
    }

    fun save(context: Context, config: GameDeckConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", config.enabled)
            .putBoolean("auto_game", config.autoGame)
            .putString("side", config.side.key)
            .putFloat("opacity", config.opacity.coerceIn(0.45f, 1f))
            .putStringSet("quick_apps", config.quickApps)
            .putInt("edge_y_offset_px", config.edgeYOffsetPx)
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

    data class DashboardWidgets(
        val showTelemetry: Boolean = true,
        val showProfile: Boolean = true,
        val showMusic: Boolean = true,
        val showRefreshRate: Boolean = true,
        val showKeepScreenOn: Boolean = true,
        val showSliders: Boolean = true,
        val order: List<String> = DEFAULT_DASHBOARD_WIDGET_ORDER
    )

    fun loadWidgets(context: Context): DashboardWidgets {
        val sp = context.getSharedPreferences("lumina_game_deck", Context.MODE_PRIVATE)
        return DashboardWidgets(
            showTelemetry = sp.getBoolean("widget_telemetry", true),
            showProfile = sp.getBoolean("widget_profile", true),
            showMusic = sp.getBoolean("widget_music", true),
            showRefreshRate = sp.getBoolean("widget_refresh_rate", true),
            showKeepScreenOn = sp.getBoolean("widget_keep_screen_on", true),
            showSliders = sp.getBoolean("widget_sliders", true),
            order = normalizeWidgetOrder(sp.getString("widget_order", null))
        )
    }

    fun saveWidgets(context: Context, widgets: DashboardWidgets) {
        context.getSharedPreferences("lumina_game_deck", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("widget_telemetry", widgets.showTelemetry)
            .putBoolean("widget_profile", widgets.showProfile)
            .putBoolean("widget_music", widgets.showMusic)
            .putBoolean("widget_refresh_rate", widgets.showRefreshRate)
            .putBoolean("widget_keep_screen_on", widgets.showKeepScreenOn)
            .putBoolean("widget_sliders", widgets.showSliders)
            .putString("widget_order", normalizeWidgetOrder(widgets.order.joinToString(",")).joinToString(","))
            .apply()
    }

    fun normalizeWidgetOrder(raw: String?): List<String> {
        val parsed = raw.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it in DEFAULT_DASHBOARD_WIDGET_ORDER }
            .distinct()
        return parsed + DEFAULT_DASHBOARD_WIDGET_ORDER.filterNot { it in parsed }
    }

    fun resetWidgets(context: Context): DashboardWidgets {
        val defaults = DashboardWidgets()
        saveWidgets(context, defaults)
        return defaults
    }
}
