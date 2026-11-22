package com.luminatweaks.gamedeck

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GameDeckBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val config = GameDeckPrefs.load(context)
        if (config.enabled) {
            GameDeckService.sync(context, config)
        }
    }
}
