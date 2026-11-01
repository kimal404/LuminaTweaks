package com.luminatweaks.data

data class AppProfileConfig(
    val pkg: String = "",
    val saklarUtama: Boolean = false,
    val liteMode: String = "default",
    val refreshRate: String = "default",
    val renderer: String = "default",
    val dnd: String = "default",
    val textureDriver: String = "native",
    val textureLod: Int = 0,
    val textureEnabled: Boolean = false,
    val textureDetail: Int = 0,
    val pixelSharp: Boolean = false,
    val hardDistance: Boolean = false
)
