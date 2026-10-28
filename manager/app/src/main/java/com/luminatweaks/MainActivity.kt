package com.luminatweaks

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.luminatweaks.ui.screen.HomeScreen
import com.luminatweaks.ui.screen.SetupWizardScreen
import com.luminatweaks.ui.theme.LuminaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("lumina_prefs", Context.MODE_PRIVATE)

        setContent {
            LuminaTheme {
                var isSetupFinished by remember {
                    mutableStateOf(prefs.getBoolean("setup_completed", false))
                }

                if (!isSetupFinished) {
                    SetupWizardScreen(
                        onComplete = {
                            prefs.edit().putBoolean("setup_completed", true).apply()
                            isSetupFinished = true
                        }
                    )
                } else {
                    HomeScreen()
                }
            }
        }
    }
}
