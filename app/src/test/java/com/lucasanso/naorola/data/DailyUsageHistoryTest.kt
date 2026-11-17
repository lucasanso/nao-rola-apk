package com.lucasanso.naorola.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.data.db.NaoRolaDatabase
import com.lucasanso.naorola.data.stats.DailyUsageHistory
import com.lucasanso.naorola.data.stats.UsageSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class DailyUsageHistoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NaoRolaDatabase::class.java)
        .allowMainThreadQueries().build()
    private val today = LocalDate.of(2026, 11, 10)
    private val now = today.atTime(15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val packages = setOf("yt")
    private val calls = mutableListOf<LocalDate>()

    /** Android "remembers" usage only for the days in [available]; older days come back empty. */
    private fun history(available: Map<LocalDate, Long>) = DailyUsageHistory(
        db.statsDao(),
        UsageSource { _, from, _ ->
            val day = java.time.Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate()
            calls += day
            mapOf("yt" to (available[day] ?: 0L))
        },
        Clock { now },
        zone = { ZoneOffset.UTC },
    )

    @After fun close() = db.close()

    @Test fun recentDaysAreSavedAndReusedOlderEmptyDaysAreUnknown() = runTest {
        val days = (6L downTo 0L).map { today.minusDays(it) }
        val first = history(mapOf(today to 60_000L, today.minusDays(1) to 0L, today.minusDays(5) to 120_000L)).forDays(days, today, packages)

        assertEquals(60_000L, first[today]?.get("yt"))
        assertEquals(0L, first[today.minusDays(1)]?.get("yt")) // recent zero is a real zero
        assertEquals(120_000L, first[today.minusDays(5)]?.get("yt"))
        assertFalse(first.containsKey(today.minusDays(6))) // old empty day: unknown, not zero

        calls.clear()
        val again = history(emptyMap()).forDays(days, today, packages)
        assertEquals(120_000L, again[today.minusDays(5)]?.get("yt")) // read from the database
        assertFalse(calls.contains(today.minusDays(5)))
        assertTrue(calls.contains(today)) // today is always live
    }
}
