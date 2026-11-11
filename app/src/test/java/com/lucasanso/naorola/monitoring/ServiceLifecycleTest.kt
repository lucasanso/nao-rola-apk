package com.lucasanso.naorola.monitoring

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.accessibility.NaoRolaAccessibilityService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class ServiceLifecycleTest {
    private val app = ApplicationProvider.getApplicationContext<NaoRolaApp>()

    @Test fun enabledStateIsReadFromAndroidSettings() {
        val health = ServiceHealthMonitor(app)
        assertFalse(health.isAccessibilityServiceEnabled())
        val component = ComponentName(app, NaoRolaAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "other/.X:$component")
        assertTrue(health.isAccessibilityServiceEnabled())
    }

    @Test fun connectingAndDisablingTheServiceUpdatesTheStatus() {
        val controller = Robolectric.buildService(NaoRolaAccessibilityService::class.java).create()
        val service = controller.get()
        NaoRolaAccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
        ShadowLooper.idleMainLooper()
        assertTrue(app.container.serviceConnected.value)

        // The user turns the service off in Android settings: the system unbinds it.
        service.onUnbind(Intent())
        ShadowLooper.idleMainLooper()
        assertFalse(app.container.serviceConnected.value)
        controller.destroy() // second shutdown must be harmless
        assertFalse(app.container.serviceConnected.value)
    }
}
