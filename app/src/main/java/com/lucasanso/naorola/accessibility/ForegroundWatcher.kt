package com.lucasanso.naorola.accessibility

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * While a session is open, checks which app is in front. Needed because the service only gets
 * events from monitored apps, so it would not notice the user going to the home screen.
 * Runs only during sessions (tolerance, block screen, allowed Reel), never in the background.
 */
class ForegroundWatcher(
    private val scope: CoroutineScope,
    private val screen: ScreenAccess,
    private val intervalMillis: Long = INTERVAL_MILLIS,
    private val onForeground: (String) -> Unit,
) {
    private var job: Job? = null

    fun setEnabled(enabled: Boolean) {
        if (enabled) start() else stop()
    }

    private fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var last: String? = null
            while (isActive) {
                val pkg = runCatching { screen.foregroundAppPackage() }.getOrNull()
                if (pkg != null && pkg != last) {
                    last = pkg
                    onForeground(pkg)
                }
                delay(intervalMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val INTERVAL_MILLIS = 700L
    }
}
