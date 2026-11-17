package com.lucasanso.naorola.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import com.lucasanso.naorola.R

/**
 * Android's "Private DNS" with a family filter: blocks adult domains for every app and browser
 * (including links opened inside other apps) and, with most providers, forces safe search.
 *
 * Changing it needs WRITE_SECURE_SETTINGS, which only the user can grant, once, by adb. Without
 * it the app explains how to set it by hand. The DNS provider is a third party chosen by the
 * user: address lookups go to it.
 */
object PrivateDns {
    enum class Provider(val host: String, @StringRes val label: Int) {
        ADGUARD_FAMILY("family.adguard-dns.com", R.string.dns_adguard),
        CLEANBROWSING_ADULT("adult-filter-dns.cleanbrowsing.org", R.string.dns_cleanbrowsing),
        CLOUDFLARE_FAMILY("family.cloudflare-dns.com", R.string.dns_cloudflare),
    }

    private const val MODE = "private_dns_mode"
    private const val SPECIFIER = "private_dns_specifier"
    private const val MODE_HOSTNAME = "hostname"
    private const val MODE_AUTOMATIC = "opportunistic"

    fun grantCommand(context: Context) = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun canManage(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** The family provider in use, null if another setting, or unknown when Android hides it. */
    sealed interface State {
        data class Family(val provider: Provider) : State
        data object Other : State
        data object Unknown : State
    }

    fun state(context: Context): State = runCatching {
        val resolver = context.contentResolver
        val mode = Settings.Global.getString(resolver, MODE)
        val host = Settings.Global.getString(resolver, SPECIFIER)
        val provider = Provider.entries.firstOrNull { it.host == host }
        if (mode == MODE_HOSTNAME && provider != null) State.Family(provider) else State.Other
    }.getOrDefault(State.Unknown)

    fun enable(context: Context, provider: Provider): Boolean = canManage(context) && runCatching {
        Settings.Global.putString(context.contentResolver, SPECIFIER, provider.host)
        Settings.Global.putString(context.contentResolver, MODE, MODE_HOSTNAME)
    }.getOrDefault(false)

    /** Back to Android's default ("automatic"). */
    fun disable(context: Context): Boolean = canManage(context) && runCatching {
        Settings.Global.putString(context.contentResolver, MODE, MODE_AUTOMATIC)
    }.getOrDefault(false)
}
