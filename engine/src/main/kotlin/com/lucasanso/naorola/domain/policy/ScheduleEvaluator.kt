package com.lucasanso.naorola.domain.policy

import com.lucasanso.naorola.domain.model.ExceptionPeriod
import com.lucasanso.naorola.domain.model.ScheduleWindow
import java.time.LocalDateTime

object ScheduleEvaluator {

    /** No windows means "always". */
    fun withinActiveWindows(windows: List<ScheduleWindow>, time: LocalDateTime): Boolean =
        windows.isEmpty() || windows.any { contains(it, time) }

    fun inException(windows: List<ScheduleWindow>, periods: List<ExceptionPeriod>, time: LocalDateTime, now: Long): Boolean =
        windows.any { contains(it, time) } || periods.any { now >= it.startMillis && now < it.endMillis }

    fun contains(window: ScheduleWindow, time: LocalDateTime): Boolean {
        val minute = time.hour * 60 + time.minute
        val today = time.dayOfWeek
        return if (window.startMinute < window.endMinute) {
            today in window.days && minute >= window.startMinute && minute < window.endMinute
        } else {
            // Crosses midnight: the evening part belongs to the listed day, the early part to the next.
            (today in window.days && minute >= window.startMinute) ||
                (today.minus(1) in window.days && minute < window.endMinute)
        }
    }
}
