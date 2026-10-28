package com.luminatweaks.model

data class AppItem(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L
)
