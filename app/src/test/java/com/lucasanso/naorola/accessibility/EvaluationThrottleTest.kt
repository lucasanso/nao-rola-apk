package com.lucasanso.naorola.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvaluationThrottleTest {
    @Test fun onlyOneEvaluationPendingAtATime() {
        val throttle = EvaluationThrottle(debounceMillis = 100, contentMinIntervalMillis = 400)
        assertEquals(100L, throttle.onEvent(EventKind.WINDOW_STATE, now = 0))
        assertNull(throttle.onEvent(EventKind.SCROLLED, now = 10))
        throttle.onEvaluated(now = 100)
        assertEquals(100L, throttle.onEvent(EventKind.SCROLLED, now = 150))
    }

    @Test fun contentChangesRespectMinimumInterval() {
        val throttle = EvaluationThrottle(debounceMillis = 100, contentMinIntervalMillis = 400)
        throttle.onEvent(EventKind.WINDOW_STATE, now = 0)
        throttle.onEvaluated(now = 1_000)
        assertEquals(350L, throttle.onEvent(EventKind.CONTENT_CHANGED, now = 1_050))
        throttle.onEvaluated(now = 1_400)
        assertEquals(100L, throttle.onEvent(EventKind.CONTENT_CHANGED, now = 5_000))
    }
}
