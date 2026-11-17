package com.lucasanso.naorola.presentation.schedule

import com.lucasanso.naorola.domain.model.ScheduleWindow
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Pure helpers between what the user picks on screen and [ScheduleWindow]. */
object ScheduleFormat {
    private val LOCALE: Locale = Locale.forLanguageTag("pt-BR")

    /** Monday first, as in Brazilian calendars at work; Sunday last. */
    val WEEK: List<DayOfWeek> = DayOfWeek.entries

    enum class DaysPreset { EVERY_DAY, WEEKDAYS, WEEKEND, CUSTOM }

    /**
     * Builds a window from picked times (minutes of the day). An end at 00:00 means midnight
     * (end of the day). Returns null when no day is chosen or start and end are the same.
     */
    fun toWindow(days: Set<DayOfWeek>, startMinute: Int, endMinute: Int): ScheduleWindow? {
        if (days.isEmpty() || startMinute == endMinute) return null
        val end = if (endMinute == 0) ScheduleWindow.MINUTES_PER_DAY else endMinute
        return runCatching { ScheduleWindow(days, startMinute, end) }.getOrNull()
    }

    fun crossesMidnight(window: ScheduleWindow) = window.endMinute <= window.startMinute

    fun preset(days: Set<DayOfWeek>): DaysPreset = when (days) {
        WEEK.toSet() -> DaysPreset.EVERY_DAY
        WEEK.take(5).toSet() -> DaysPreset.WEEKDAYS
        setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> DaysPreset.WEEKEND
        else -> DaysPreset.CUSTOM
    }

    fun shortName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, LOCALE).trimEnd('.')

    fun customDays(days: Set<DayOfWeek>): String =
        WEEK.filter { it in days }.joinToString(", ") { shortName(it) }.replaceFirstChar { it.titlecase(LOCALE) }

    fun time(minuteOfDay: Int): String {
        val m = minuteOfDay % ScheduleWindow.MINUTES_PER_DAY
        return "%02d:%02d".format(m / 60, m % 60)
    }
}
