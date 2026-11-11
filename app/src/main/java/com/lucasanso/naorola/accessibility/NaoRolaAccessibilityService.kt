package com.lucasanso.naorola.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.lucasanso.naorola.NaoRolaApp

/**
 * Entry point used by Android. Kept thin: everything happens in [ProtectionCoordinator].
 * No exception may escape from here — a crash makes Android mark the service as "not working"
 * until the user turns it on again.
 */
class NaoRolaAccessibilityService : AccessibilityService(), ScreenAccess {
    private var coordinator: ProtectionCoordinator? = null
    private val container get() = (application as NaoRolaApp).container

    override val ownPackage: String get() = packageName

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            coordinator = ProtectionCoordinator(this, this, container).also { it.start() }
            container.onServiceConnected()
        } catch (e: Exception) {
            container.logger.error(TAG, "Falha ao iniciar a proteção", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        try {
            coordinator?.onEvent(EventKind.of(event.eventType))
        } catch (e: Exception) {
            container.logger.error(TAG, "Falha ao tratar evento", e)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        val current = coordinator ?: return
        coordinator = null
        try {
            current.stop()
        } catch (e: Exception) {
            container.logger.error(TAG, "Falha ao parar a proteção", e)
        }
        container.onServiceDisconnected()
    }

    override fun activeRoot(): AccessibilityNodeInfo? = rootInActiveWindow

    override fun foregroundAppPackage(): String? {
        val apps = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val front = apps.firstOrNull { it.isActive } ?: apps.maxByOrNull { it.layer }
        return front?.root?.packageName?.toString()
    }

    override fun displaySize(): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    override fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    override fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    override fun setMonitoredPackages(packages: Set<String>) {
        val info = serviceInfo ?: return
        info.packageNames = packages.toTypedArray()
        serviceInfo = info
    }

    private companion object {
        const val TAG = "Servico"
    }
}
