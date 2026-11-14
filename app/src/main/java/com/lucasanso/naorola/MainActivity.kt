package com.lucasanso.naorola

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucasanso.naorola.adblock.BrowserFilters
import com.lucasanso.naorola.data.settings.UiPreferences
import com.lucasanso.naorola.presentation.navigation.NaoRolaNavHost
import com.lucasanso.naorola.presentation.navigation.Routes
import com.lucasanso.naorola.presentation.theme.NaoRolaTheme

class MainActivity : ComponentActivity() {
    private val requestedRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedRoute.value = routeFor(intent)
        val container = (application as NaoRolaApp).container
        setContent {
            val ui by container.settingsRepository.uiPreferences.collectAsStateWithLifecycle(UiPreferences())
            val route by requestedRoute
            NaoRolaTheme(dynamicColor = ui.dynamicColor) {
                NaoRolaNavHost(requestedRoute = route, onRouteHandled = { requestedRoute.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFor(intent)?.let { requestedRoute.value = it }
    }

    private fun routeFor(intent: Intent?): String? =
        if (intent?.action == BrowserFilters.ACTION_SETTING) Routes.ADS else null
}
