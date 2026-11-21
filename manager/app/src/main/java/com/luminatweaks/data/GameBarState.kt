package com.luminatweaks.data

data class GameBarQuickApp(
    val packageName: String,
    val label: String
)

data class GameBarUiState(
    val expanded: Boolean = false,
    val fps: Int = 0,
    val frametimeMs: Float = 0f,
    val targetHz: Int = 60,
    val profile: String = "Balanced",
    val autoProfile: Boolean = true,
    val gameLabel: String? = null,
    val gamePackage: String? = null,
    val cpuPercent: Int? = null,
    val gpuPercent: Int? = null,
    val tempLabel: String = "--",
    val batteryPercent: Int = 0,
    val ramPercent: Int? = null,
    val volumePercent: Int = 50,
    val brightnessPercent: Int = 50,
    val side: GameBarSide = GameBarSide.LEFT,
    val opacity: Float = 0.94f,
    val autoGame: Boolean = true,
    val manual: Boolean = false,
    val quickApps: List<GameBarQuickApp> = emptyList()
)
