/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.luminatweaks

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.luminatweaks.ui.screen.HomeScreen
import com.luminatweaks.ui.screen.SetupWizardScreen
import com.luminatweaks.ui.theme.LuminaTheme
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
                val os = java.io.DataOutputStream(p.outputStream)
                for (cmd in cmds) {
                    os.writeBytes("$cmd 2>/dev/null\n")
                }
                os.writeBytes("exit\n")
                os.flush()
                p.waitFor()
            } catch (_: Exception) {}
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setContent {
            LuminaTheme {
                var isSetupFinished by remember {
                    mutableStateOf(prefs.getBoolean(KEY_SETUP_WIZARD_V2_COMPLETED, false))
                }

                AnimatedContent(
                    targetState = isSetupFinished,
                    transitionSpec = {
                        fadeIn(tween(320)) togetherWith fadeOut(tween(180))
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
