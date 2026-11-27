package com.luminatweaks

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.luminatweaks.data.LuminaBridge
import com.luminatweaks.ui.screen.HomeScreen
import com.luminatweaks.ui.screen.SetupWizardScreen
import com.luminatweaks.ui.theme.HapticSettings
import com.luminatweaks.ui.theme.LocalHapticConfig
import com.luminatweaks.ui.theme.LuminaTheme
import com.luminatweaks.ui.theme.ThemeController
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS_NAME = "lumina_prefs"
        private const val KEY_SETUP_WIZARD_V2_COMPLETED = "setup_wizard_v2_completed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LuminaBridge.start(applicationContext)
        if (com.luminatweaks.gamedeck.GameDeckPrefs.load(this).enabled) {
            com.luminatweaks.gamedeck.GameDeckService.sync(this)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val pkg = packageName
            val cmds = arrayOf(
                "pm grant $pkg android.permission.QUERY_ALL_PACKAGES",
                "pm grant $pkg com.android.permission.GET_INSTALLED_APPS",
                "appops set $pkg android:query_all_packages allow",
                "appops set $pkg query_all_packages allow",
                "appops set $pkg android:get_installed_apps allow",
                "appops set $pkg GET_INSTALLED_APPS allow",
                "appops set $pkg 10022 allow"
            )
            try {
                val p = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(p.outputStream)
                for (cmd in cmds) {
                    os.writeBytes("$cmd 2>/dev/null\n")
                }
                os.writeBytes("exit\n")
                os.flush()
                if (!p.waitFor(15, TimeUnit.SECONDS)) p.destroyForcibly()
            } catch (_: Exception) {}
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setContent {
            val appSettings = remember { ThemeController.getAppSettings(this@MainActivity) }
            val hapticSettings = remember(appSettings.hapticEnabled, appSettings.hapticIntensity) {
                HapticSettings(
                    enabled = appSettings.hapticEnabled,
                    intensity = appSettings.hapticIntensity
                )
            }
            CompositionLocalProvider(LocalHapticConfig provides hapticSettings) {
            LuminaTheme {
                var isSetupFinished by remember {
                    mutableStateOf(prefs.getBoolean(KEY_SETUP_WIZARD_V2_COMPLETED, false))
                }

                AnimatedContent(
                    targetState = isSetupFinished,
                    transitionSpec = {
                        (fadeIn(tween(340, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.95f, animationSpec = tween(340, easing = FastOutSlowInEasing))) togetherWith
                            (fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 1.03f, animationSpec = tween(200, easing = FastOutSlowInEasing)))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "setupToHome"
                ) { finished ->
                    if (!finished) {
                        SetupWizardScreen(
                            onComplete = {
                                prefs.edit().putBoolean(KEY_SETUP_WIZARD_V2_COMPLETED, true).apply()
                                isSetupFinished = true
                            }
                        )
                    } else {
                        HomeScreen(onOpenSetupWizard = { isSetupFinished = false })
                    }
                }
            }
            }
        }
    }
}
