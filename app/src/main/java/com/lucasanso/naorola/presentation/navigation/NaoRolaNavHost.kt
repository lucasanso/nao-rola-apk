package com.lucasanso.naorola.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lucasanso.naorola.R
import com.lucasanso.naorola.presentation.about.AboutScreen
import com.lucasanso.naorola.presentation.dashboard.DashboardScreen
import com.lucasanso.naorola.presentation.diagnostics.DiagnosticsScreen
import com.lucasanso.naorola.presentation.discipline.DisciplineScreen
import com.lucasanso.naorola.presentation.permissions.PermissionsScreen
import com.lucasanso.naorola.presentation.schedule.ScheduleScreen
import com.lucasanso.naorola.presentation.settings.BlockingSettingsScreen
import com.lucasanso.naorola.presentation.settings.SettingsHubScreen
import com.lucasanso.naorola.presentation.stats.StatsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val BLOCKING = "settings/blocking"
    const val DISCIPLINE = "settings/discipline"
    const val SCHEDULE = "settings/schedule"
    const val PERMISSIONS = "settings/permissions"
    const val DIAGNOSTICS = "settings/diagnostics"
    const val ABOUT = "settings/about"
}

private data class TopLevel(val route: String, @StringRes val label: Int, @DrawableRes val icon: Int)

private val TOP_LEVEL = listOf(
    TopLevel(Routes.DASHBOARD, R.string.nav_dashboard, R.drawable.ic_dashboard),
    TopLevel(Routes.STATS, R.string.nav_stats, R.drawable.ic_bar_chart),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_tune),
)

@Composable
fun NaoRolaNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TOP_LEVEL.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TOP_LEVEL.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                    onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.SETTINGS) { SettingsHubScreen(onNavigate = { nav.navigate(it) }) }
            composable(Routes.BLOCKING) { BlockingSettingsScreen(onBack = back) }
            composable(Routes.PERMISSIONS) { PermissionsScreen(onBack = back) }
            composable(Routes.DISCIPLINE) { DisciplineScreen(onBack = back) }
            composable(Routes.SCHEDULE) { ScheduleScreen(onBack = back) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = back) }
            composable(Routes.ABOUT) { AboutScreen(onBack = back, onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) }) }
        }
    }
}
