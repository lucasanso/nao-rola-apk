package com.lucasanso.naorola.accessibility

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * The accessibility service as an Android component. "Bank mode" disables the component itself:
 * while disabled, Não-Rola has no accessibility service at all (Android cannot run it and does
 * not list it), which is what banking apps check for. Nothing is disguised: the app keeps its
 * name, signature and origin, and simply has no screen access until the user brings it back.
 */
object ServiceComponent {
    fun name(context: Context) = ComponentName(context, NaoRolaAccessibilityService::class.java)

    fun isDisabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(name(context)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            name(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Only after the user granted WRITE_SECURE_SETTINGS by adb (the same grant the DNS uses). */
    fun canWriteSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Removes our service from Android's list of enabled services, keeping everyone else's. */
    fun removeFromEnabledList(context: Context): Boolean = canWriteSettings(context) && runCatching {
        val ours = name(context)
        val remaining = enabledList(context).filterNot { ComponentName.unflattenFromString(it) == ours }
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, remaining.joinToString(":"))
    }.getOrDefault(false)

    /**
     * Turns our service back on directly, as the user asked, when they granted the permission;
     * otherwise the caller opens Android's accessibility settings.
     */
    fun addToEnabledList(context: Context): Boolean = canWriteSettings(context) && runCatching {
        val ours = name(context)
        val current = enabledList(context)
        val list = if (current.any { ComponentName.unflattenFromString(it) == ours }) current else current + ours.flattenToString()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }.getOrDefault(false)

    private fun enabledList(context: Context): List<String> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.filter { it.isNotBlank() }.orEmpty()
}
