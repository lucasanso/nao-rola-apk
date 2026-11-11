package com.lucasanso.naorola.domain.policy

import com.lucasanso.naorola.domain.model.ExceptionPeriod
import com.lucasanso.naorola.domain.model.ScheduleWindow
import java.time.DayOfWeek

/**
 * Compact, versionable text encoding for the list settings stored in DataStore.
 *
 *     windows: "MONDAY,FRIDAY@480-1080;SATURDAY@1320-120"
 *     periods: "1700000000000-1700003600000;…"
 *
 * Invalid entries are skipped instead of failing, so a corrupt value never disables protection.
 */
object SettingsCodec {
    private const val ITEM_SEPARATOR = ";"

    fun encodeWindows(windows: List<ScheduleWindow>): String =
        windows.joinToString(ITEM_SEPARATOR) { w ->
            w.days.sorted().joinToString(",") { it.name } + "@" + w.startMinute + "-" + w.endMinute
        }

    fun decodeWindows(text: String?): List<ScheduleWindow> =
        text.orEmpty().split(ITEM_SEPARATOR).filter { it.isNotBlank() }.mapNotNull { item ->
            runCatching {
                val (daysPart, range) = item.split('@', limit = 2)
                val days = daysPart.split(',').filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it.trim()) }.toSet()
                val (start, end) = range.split('-', limit = 2).map { it.trim().toInt() }
                if (days.isEmpty()) null else ScheduleWindow(days, start, end)
            }.getOrNull()
        }

    fun encodePeriods(periods: List<ExceptionPeriod>): String =
        periods.joinToString(ITEM_SEPARATOR) { "${it.startMillis}-${it.endMillis}" }

    fun decodePeriods(text: String?): List<ExceptionPeriod> =
        text.orEmpty().split(ITEM_SEPARATOR).filter { it.isNotBlank() }.mapNotNull { item ->
            runCatching {
                val (start, end) = item.split('-', limit = 2).map { it.trim().toLong() }
                ExceptionPeriod(start, end)
            }.getOrNull()
        }
}
