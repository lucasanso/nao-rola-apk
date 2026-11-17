package com.lucasanso.naorola.domain

import com.lucasanso.naorola.domain.model.ExceptionPeriod
import com.lucasanso.naorola.domain.model.ScheduleWindow
import com.lucasanso.naorola.domain.policy.ScheduleEvaluator
import com.lucasanso.naorola.domain.policy.SettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.LocalDateTime

class ScheduleAndCodecTest {
    @Test fun windowWithinOneDay() {
        val w = ScheduleWindow(setOf(MONDAY), 9 * 60, 18 * 60)
        assertTrue(ScheduleEvaluator.contains(w, LocalDateTime.of(2026, 11, 9, 9, 0))) // Monday
        assertFalse(ScheduleEvaluator.contains(w, LocalDateTime.of(2026, 11, 9, 18, 0)))
        assertFalse(ScheduleEvaluator.contains(w, LocalDateTime.of(2026, 11, 10, 10, 0))) // Tuesday
    }

    @Test fun windowCrossingMidnightBelongsToStartDay() {
        val w = ScheduleWindow(setOf(FRIDAY), 22 * 60, 2 * 60)
        assertTrue(ScheduleEvaluator.contains(w, LocalDateTime.of(2026, 11, 13, 23, 0))) // Friday night
        assertTrue(ScheduleEvaluator.contains(w, LocalDateTime.of(2026, 11, 14, 1, 59))) // Saturday early
        assertFalse(ScheduleEvaluator.contains(w, LocalDateTime.of(2026, 11, 14, 23, 0))) // Saturday night
    }

    @Test fun codecRoundTrip() {
        val windows = listOf(ScheduleWindow(setOf(MONDAY, FRIDAY), 480, 1080), ScheduleWindow(setOf(SATURDAY), 1320, 120))
        assertEquals(windows, SettingsCodec.decodeWindows(SettingsCodec.encodeWindows(windows)))
        val periods = listOf(ExceptionPeriod(1, 2), ExceptionPeriod(1_700_000_000_000, 1_700_003_600_000))
        assertEquals(periods, SettingsCodec.decodePeriods(SettingsCodec.encodePeriods(periods)))
    }

    @Test fun codecSkipsCorruptEntries() {
        assertEquals(1, SettingsCodec.decodeWindows("MONDAY@0-60;NOTADAY@1-2;@5-6;TUESDAY@9999-1").size)
        assertEquals(1, SettingsCodec.decodePeriods("5-1;10-20;x-y").size)
        assertTrue(SettingsCodec.decodeWindows(null).isEmpty())
    }
}
