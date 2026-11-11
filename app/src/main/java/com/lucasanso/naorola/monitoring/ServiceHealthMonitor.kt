package com.lucasanso.naorola.monitoring

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.security.advancedprotection.AdvancedProtectionManager
import com.lucasanso.naorola.accessibility.NaoRolaAccessibilityService

/** Real state of everything the protection depends on, read from the system each time. */
data class HealthSnapshot(
    val accessibilityEnabled: Boolean,
    val usageAccessGranted: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    /** null when the system does not report it (Android < 16 or not allowed to ask). */
    val advancedProtectionOn: Boolean?,
    val installedApps: Set<String>,
)

class ServiceHealthMonitor(private val context: Context) {

    fun snapshot(packagesOfInterest: Set<String>): HealthSnapshot = HealthSnapshot(
        accessibilityEnabled = isAccessibilityServiceEnabled(),
        usageAccessGranted = hasUsageAccess(),
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
        advancedProtectionOn = advancedProtectionOn(),
        installedApps = packagesOfInterest.filterTo(HashSet()) { isInstalled(it) },
    )

    fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(context, NaoRolaAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun advancedProtectionOn(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return null
        return runCatching {
            context.getSystemService(AdvancedProtectionManager::class.java)?.isAdvancedProtectionEnabled
        }.getOrNull()
    }

    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun versionOf(packageName: String): String? = try {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
