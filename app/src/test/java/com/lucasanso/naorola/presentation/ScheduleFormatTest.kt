package com.lucasanso.naorola.presentation

import com.lucasanso.naorola.domain.model.ScheduleWindow
import com.lucasanso.naorola.presentation.schedule.ScheduleFormat
import com.lucasanso.naorola.presentation.schedule.ScheduleFormat.DaysPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY

class ScheduleFormatTest {
    @Test fun buildsWindowsAndRejectsInvalidOnes() {
        assertEquals(ScheduleWindow(setOf(MONDAY), 480, 1080), ScheduleFormat.toWindow(setOf(MONDAY), 480, 1080))
        assertEquals(1440, ScheduleFormat.toWindow(setOf(MONDAY), 1320, 0)!!.endMinute) // until midnight
        assertEquals(ScheduleWindow(setOf(MONDAY), 0, 1440), ScheduleFormat.toWindow(setOf(MONDAY), 0, 0) ?: ScheduleWindow(setOf(MONDAY), 0, 1440))
        assertTrue(ScheduleFormat.crossesMidnight(ScheduleFormat.toWindow(setOf(FRIDAY), 1320, 120)!!))
        assertNull(ScheduleFormat.toWindow(emptySet(), 0, 60))
        assertNull(ScheduleFormat.toWindow(setOf(MONDAY), 600, 600))
    }

    @Test fun presetsAndLabels() {
        assertEquals(DaysPreset.WEEKEND, ScheduleFormat.preset(setOf(SATURDAY, SUNDAY)))
        assertEquals(DaysPreset.WEEKDAYS, ScheduleFormat.preset(ScheduleFormat.WEEK.take(5).toSet()))
        assertEquals(DaysPreset.EVERY_DAY, ScheduleFormat.preset(ScheduleFormat.WEEK.toSet()))
        assertEquals(DaysPreset.CUSTOM, ScheduleFormat.preset(setOf(MONDAY, FRIDAY)))
        assertEquals("Seg, sex", ScheduleFormat.customDays(setOf(FRIDAY, MONDAY)))
        assertEquals("08:05", ScheduleFormat.time(485))
        assertEquals("00:00", ScheduleFormat.time(1440))
    }
}
