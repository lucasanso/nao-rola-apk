package com.lucasanso.naorola

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasanso.naorola.data.settings.UiPreferences
import com.lucasanso.naorola.presentation.navigation.NaoRolaNavHost
import com.lucasanso.naorola.presentation.theme.NaoRolaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as NaoRolaApp).container
        setContent {
            val ui by container.settingsRepository.uiPreferences.collectAsStateWithLifecycle(UiPreferences())
            NaoRolaTheme(dynamicColor = ui.dynamicColor) { NaoRolaNavHost() }
        }
    }
}
