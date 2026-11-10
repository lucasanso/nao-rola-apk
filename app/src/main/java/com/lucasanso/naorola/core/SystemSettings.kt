package com.lucasanso.naorola.core

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lucasanso.naorola.accessibility.NaoRolaAccessibilityService
import java.io.File

/** Opens the Android settings pages the app depends on, with safe fallbacks. */
object SystemSettings {
    fun openAccessibility(context: Context) {
        val component = ComponentName(context, NaoRolaAccessibilityService::class.java).flattenToString()
        // Highlights our service on devices that support it; others just open the list.
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", component)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", component) })
        start(context, intent)
    }

    fun openAppDetails(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    fun openUsageAccess(context: Context) = start(
        context,
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
    )

    fun openBatteryOptimization(context: Context) = start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openSecurity(context: Context) = start(context, Intent(Settings.ACTION_SECURITY_SETTINGS))

    /** Lets the user send a file (diagnostic capture, log) with an app of their choice. */
    fun shareFile(context: Context, file: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(send, chooserTitle))
    }

    private fun start(context: Context, intent: Intent, fallback: Intent? = null) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            fallback?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        } catch (_: SecurityException) {
            fallback?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
}
