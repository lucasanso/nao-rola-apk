package com.lucasanso.naorola.data.stats

import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.data.db.DailyAppUsageEntity
import com.lucasanso.naorola.data.db.StatsDao
import java.time.LocalDate
import java.time.ZoneId

/** Reads time inside apps for a range [from, to); null = Android has no data / no permission. */
fun interface UsageSource {
    fun foregroundMillis(packages: Set<String>, from: Long, to: Long): Map<String, Long>?
}

/**
 * Time inside each app per day. Android keeps detailed usage events only for a few days, so
 * closed days are saved once computed. A day that cannot be computed any more is reported as
 * unknown (absent), never as zero.
 */
class DailyUsageHistory(
    private val dao: StatsDao,
    private val source: UsageSource,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    suspend fun forDays(days: List<LocalDate>, today: LocalDate, packages: Set<String>): Map<LocalDate, Map<String, Long>> {
        if (days.isEmpty()) return emptyMap()
        val stored = dao.dailyUsageSince(days.minOf { it.toEpochDay() })
            .groupBy { LocalDate.ofEpochDay(it.epochDay) }
            .mapValues { (_, rows) -> rows.associate { it.packageName to it.foregroundMillis } }
        val result = LinkedHashMap<LocalDate, Map<String, Long>>()
        for (day in days) {
            val start = day.atStartOfDay(zone()).toInstant().toEpochMilli()
            when {
                day == today -> source.foregroundMillis(packages, start, clock.nowMillis())?.let { result[day] = it }
                stored[day] != null -> result[day] = packages.associateWith { stored.getValue(day)[it] ?: 0L }
                day.isBefore(today) -> {
                    val end = day.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli()
                    val computed = source.foregroundMillis(packages, start, end) ?: continue
                    // Recent days are trustworthy even when zero; older zeros may just be expired data.
                    val recent = !day.isBefore(today.minusDays(TRUSTED_RECENT_DAYS))
                    if (recent || computed.values.any { it > 0 }) {
                        dao.upsertDailyUsage(computed.map { (pkg, ms) -> DailyAppUsageEntity(day.toEpochDay(), pkg, ms) })
                        result[day] = computed
                    }
                }
            }
        }
        return result
    }

    private companion object {
        const val TRUSTED_RECENT_DAYS = 2L
    }
}
