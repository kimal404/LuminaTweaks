package com.luminatweaks.model

data class AppItem(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val isGame: Boolean,
    val isEnabled: Boolean
)
