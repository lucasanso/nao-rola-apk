package com.lucasanso.naorola.domain.session

import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ShortVideoExposure
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.EnumMap

/**
 * Short-video time already allowed today, per platform, kept in memory by the protection so
 * the daily limit is applied without waiting for the database. Resets at local midnight.
 * Seeded from the stored history when the service starts.
 */
class DailyExposureTracker(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    private var day: LocalDate? = null
    private val used = EnumMap<Platform, Long>(Platform::class.java)

    @Synchronized
    fun seed(now: Long, values: Map<Platform, Long>) {
        day = dayOf(now)
        used.clear()
        used.putAll(values)
    }

    /** Exposures are attributed to the day they started. */
    @Synchronized
    fun add(exposure: ShortVideoExposure) {
        val exposureDay = dayOf(exposure.startMillis)
        if (exposureDay != day) {
            if (day != null && exposureDay.isBefore(day)) return // belongs to a past day
            day = exposureDay
            used.clear()
        }
        val platform = exposure.surface.platform
        used[platform] = (used[platform] ?: 0L) + exposure.durationMillis
    }

    @Synchronized
    fun usedToday(platform: Platform, now: Long): Long {
        if (dayOf(now) != day) return 0L
        return used[platform] ?: 0L
    }

    private fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()
}
