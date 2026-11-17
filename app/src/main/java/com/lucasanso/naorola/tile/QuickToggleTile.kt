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
 * Quick Settings tile for banking apps: tapping it while protection runs enters bank mode (the
 * accessibility service is removed); tapping it in bank mode brings the service back — directly
 * if the user granted the permission by adb, otherwise through Android's accessibility settings.
 * Tapping it while the service is simply off opens those settings.
 */
class QuickToggleTile : TileService() {
    private val container get() = (application as NaoRolaApp).container

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        when {
            container.bankMode.value -> if (!container.exitBankMode()) openAccessibilitySettings()
            container.serviceConnected.value -> container.enterBankMode()
            else -> openAccessibilitySettings()
        }
        render()
    }

    // The Intent overload is used only below Android 14, where it is the only one available.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAccessibilitySettings() {
        val intent = SystemSettings.accessibilityIntent(this)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure { SystemSettings.openAccessibility(this) } // tile not attached to the shade
    }

    private fun render() {
        val tile = qsTile ?: return
        val bank = container.bankMode.value
        val connected = !bank && container.serviceConnected.value
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = getString(
            when {
                bank -> R.string.tile_bank
                connected -> R.string.tile_on
                else -> R.string.tile_off
            },
        )
        tile.updateTile()
    }

    companion object {
        /** Asks Android to refresh the tile after the service state changed. */
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, QuickToggleTile::class.java)) }
        }
    }
}
