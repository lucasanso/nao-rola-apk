package com.lucasanso.naorola.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.domain.stats.ForegroundTimeCalculator
import com.lucasanso.naorola.domain.stats.UsageEventRecord
import com.lucasanso.naorola.domain.stats.UsageEventType
import java.time.Instant
import java.time.ZoneId

/**
 * Time inside YouTube/Instagram from Android's usage events. Requires the user to grant
 * "usage access"; without it this returns null and the screens say the data is unavailable.
 */
class UsageStatsReader(private val context: Context, private val clock: Clock) {

    fun todayForegroundMillis(packages: Set<String>, zone: ZoneId = ZoneId.systemDefault()): Map<String, Long>? {
        val now = clock.nowMillis()
        val startOfDay = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return foregroundMillis(packages, startOfDay, now)
    }

    /** Foreground time per package inside [from, to), or null when usage data is unavailable. */
    fun foregroundMillis(packages: Set<String>, from: Long, to: Long): Map<String, Long>? {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching { manager.queryEvents(from - LOOKBACK_MILLIS, to) }.getOrNull() ?: return null
        val records = ArrayList<UsageEventRecord>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventType.PAUSED
                UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventType.DEVICE_SHUTDOWN
                else -> null
            } ?: continue
            val pkg = event.packageName ?: continue
            if (type != UsageEventType.DEVICE_SHUTDOWN && pkg !in packages) continue
            records += UsageEventRecord(event.timeStamp, pkg, event.className, type)
        }
        return ForegroundTimeCalculator.compute(records, from, to, packages)
    }

    private companion object {
        /** Catches apps that were opened shortly before midnight. */
        const val LOOKBACK_MILLIS = 3 * 60 * 60 * 1000L
    }
}
