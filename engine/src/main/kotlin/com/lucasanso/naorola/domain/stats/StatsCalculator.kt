package com.lucasanso.naorola.domain.stats

import com.lucasanso.naorola.domain.model.BlockEvent
import com.lucasanso.naorola.domain.model.BlockOutcome
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ShortVideoExposure
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day of history. App usage is null when unknown (no usage access, or Android no longer has that day). */
data class DayStats(
    val date: LocalDate,
    val blocked: Map<Platform, Int>,
    val shortVideoMillis: Map<Platform, Long>,
    val appUsageMillis: Map<Platform, Long>?,
) {
    val blockedTotal: Int get() = blocked.values.sum()
}

object StatsCalculator {

    /** Groups raw history into one entry per day of [days] (local dates, any order kept). */
    fun dailySeries(
        days: List<LocalDate>,
        events: List<BlockEvent>,
        exposures: List<ShortVideoExposure>,
        appUsage: Map<LocalDate, Map<Platform, Long>>?,
        zone: ZoneId,
    ): List<DayStats> {
        fun dayOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val blockedByDay = events
            .filter { it.outcome == BlockOutcome.BLOCKED }
            .groupBy { dayOf(it.timeMillis) }
            .mapValues { (_, list) -> list.groupingBy { it.surface.platform }.eachCount() }
        val exposureByDay = exposures
            .groupBy { dayOf(it.startMillis) }
            .mapValues { (_, list) ->
                list.groupBy { it.surface.platform }.mapValues { (_, l) -> l.sumOf { it.durationMillis } }
            }
        return days.map { day ->
            DayStats(
                date = day,
                blocked = blockedByDay[day].orEmpty(),
                shortVideoMillis = exposureByDay[day].orEmpty(),
                appUsageMillis = appUsage?.get(day),
            )
        }
    }

    /** The last [count] local dates ending today, oldest first. */
    fun lastDays(today: LocalDate, count: Int): List<LocalDate> = (count - 1 downTo 0).map { today.minusDays(it.toLong()) }
}

/**
 * "Exposure avoided" is not measured anywhere, so it is only shown as a clearly labelled
 * estimate when there is enough of the user's OWN data:
 *
 *     estimate = blocked attempts × median length of the short-video sessions the user watched
 *                (tolerance, pause, Reel from a DM)
 *
 * The median of sessions that were cut by the tolerance underestimates real sessions, so the
 * figure is conservative.
 */
object AvoidedExposureEstimator {
    const val MIN_SESSIONS = 10

    sealed interface Estimate {
        data class Insufficient(val sessions: Int, val needed: Int = MIN_SESSIONS) : Estimate
        data class Available(val millis: Long, val medianSessionMillis: Long, val sessions: Int, val blocked: Int) : Estimate
    }

    fun estimate(blocked: Int, sessionDurations: List<Long>): Estimate {
        val valid = sessionDurations.filter { it > 0 }
        if (valid.size < MIN_SESSIONS) return Estimate.Insufficient(valid.size)
        val sorted = valid.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        return Estimate.Available(blocked * median, median, valid.size, blocked)
    }
}
