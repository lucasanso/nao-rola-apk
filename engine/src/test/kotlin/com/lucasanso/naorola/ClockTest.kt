package com.lucasanso.naorola

import com.lucasanso.naorola.core.Clock
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {
    @Test fun fixedClockReturnsGivenTime() {
        val clock = Clock { 42L }
        assertEquals(42L, clock.nowMillis())
    }
}
