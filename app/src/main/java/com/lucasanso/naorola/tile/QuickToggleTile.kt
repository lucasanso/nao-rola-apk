package com.lucasanso.naorola.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.R
import com.lucasanso.naorola.core.SystemSettings

/**
 * Quick Settings tile: shows whether protection is running. Tapping it while on turns the
 * service off (e.g. before opening a banking app); tapping it while off opens the accessibility
 * settings so the user turns it back on. Android does not let apps turn an accessibility service
 * on by themselves, and Não-Rola does not try to.
 */
class QuickToggleTile : TileService() {
    private val container get() = (application as NaoRolaApp).container

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (container.serviceConnected.value) {
            container.disableServiceNow()
            render(connected = false)
        } else {
            openAccessibilitySettings()
        }
    }

    // The Intent overload is used only below Android 14, where it is the only one available.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAccessibilitySettings() {
        val intent = SystemSettings.accessibilityIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(connected: Boolean = container.serviceConnected.value) {
        val tile = qsTile ?: return
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = getString(if (connected) R.string.tile_on else R.string.tile_off)
        tile.updateTile()
    }

    companion object {
        /** Asks Android to refresh the tile after the service state changed. */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, QuickToggleTile::class.java)) }
        }
    }
}
