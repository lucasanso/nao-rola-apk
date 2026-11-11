package com.lucasanso.naorola.data.stats

import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.data.db.BlockEventEntity
import com.lucasanso.naorola.data.db.ExposureEntity
import com.lucasanso.naorola.data.db.ProtectionEventEntity
import com.lucasanso.naorola.data.db.StatsDao
import com.lucasanso.naorola.domain.model.BlockEvent
import com.lucasanso.naorola.domain.model.BlockOutcome
import com.lucasanso.naorola.domain.model.Platform
import com.lucasanso.naorola.domain.model.ShortVideoExposure
import com.lucasanso.naorola.domain.model.Surface
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ProtectionEventType { SERVICE_CONNECTED, SERVICE_DISCONNECTED, DISCIPLINE_ON, DISCIPLINE_OFF }

data class ProtectionEvent(val timeMillis: Long, val type: ProtectionEventType)

/** Today's numbers for the dashboard. Only things the app measured itself. */
data class TodaySummary(
    val blockedByPlatform: Map<Platform, Int> = emptyMap(),
    val exposureMillisByPlatform: Map<Platform, Long> = emptyMap(),
) {
    val blockedTotal: Int get() = blockedByPlatform.values.sum()
}

class StatsRepository(
    private val dao: StatsDao,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    suspend fun record(event: BlockEvent) = dao.insertEvent(
        BlockEventEntity(
            timeMillis = event.timeMillis,
            surface = event.surface.name,
            platform = event.surface.platform.name,
            outcome = event.outcome.name,
        ),
    )

    suspend fun recordExposure(exposure: ShortVideoExposure) = dao.insertExposure(
        ExposureEntity(
            surface = exposure.surface.name,
            platform = exposure.surface.platform.name,
            startMillis = exposure.startMillis,
            endMillis = exposure.endMillis,
        ),
    )

    suspend fun eventsSince(since: Long): List<BlockEvent> = dao.eventsSince(since).mapNotNull { e ->
        val surface = enumOrNull<Surface>(e.surface) ?: return@mapNotNull null
        val outcome = enumOrNull<BlockOutcome>(e.outcome) ?: return@mapNotNull null
        BlockEvent(e.timeMillis, surface, outcome)
    }

    suspend fun exposuresSince(since: Long): List<ShortVideoExposure> = dao.exposuresSince(since).mapNotNull { e ->
        enumOrNull<Surface>(e.surface)?.let { ShortVideoExposure(it, e.startMillis, e.endMillis) }
    }

    suspend fun protectionEventsSince(since: Long): List<ProtectionEvent> = dao.protectionEventsSince(since).mapNotNull { e ->
        enumOrNull<ProtectionEventType>(e.type)?.let { ProtectionEvent(e.timeMillis, it) }
    }

    suspend fun recordProtection(type: ProtectionEventType) =
        dao.insertProtectionEvent(ProtectionEventEntity(timeMillis = clock.nowMillis(), type = type.name))

    /** Re-subscribes at local midnight so "today" never goes stale. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun today(): Flow<TodaySummary> = startOfDayTicks().flatMapLatest { since ->
        combine(
            dao.countByPlatformSince(BlockOutcome.BLOCKED.name, since),
            dao.exposureByPlatformSince(since),
        ) { counts, exposures ->
            TodaySummary(
                blockedByPlatform = counts.mapNotNull { c -> platformOf(c.platform)?.let { it to c.count } }.toMap(),
                exposureMillisByPlatform = exposures.mapNotNull { e -> platformOf(e.platform)?.let { it to e.totalMillis } }.toMap(),
            )
        }
    }

    suspend fun purgeOlderThan(days: Long = RETENTION_DAYS) {
        val before = clock.nowMillis() - days * DAY_MILLIS
        dao.purgeEvents(before)
        dao.purgeExposures(before)
        dao.purgeProtectionEvents(before)
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone()).toLocalDate()
        dao.purgeDailyUsage(today.minusDays(days).toEpochDay())
    }

    private fun startOfDayTicks(): Flow<Long> = flow {
        while (true) {
            val now = clock.nowMillis()
            val today = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
            emit(today.atStartOfDay(zone()).toInstant().toEpochMilli())
            val next = LocalDate.from(today).plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli()
            delay((next - now).coerceAtLeast(MIN_TICK_MILLIS))
        }
    }

    private fun platformOf(name: String): Platform? = enumOrNull<Platform>(name)

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = runCatching { enumValueOf<E>(name) }.getOrNull()

    companion object {
        const val RETENTION_DAYS = 30L
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val MIN_TICK_MILLIS = 1_000L
    }
}
