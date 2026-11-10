package com.lucasanso.naorola

import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivitySmokeTest {
    @Test fun activityStarts() {
        ActivityScenario.launch(MainActivity::class.java).use { }
    }
}
