package com.lucasanso.naorola.domain

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.domain.model.BlockOutcome
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.domain.policy.EffectivePolicy
import com.lucasanso.naorola.domain.policy.InactiveReason
import com.lucasanso.naorola.domain.session.BlockingSessionMachine
import com.lucasanso.naorola.domain.session.Command
import com.lucasanso.naorola.domain.session.SessionConfig
import com.lucasanso.naorola.domain.session.SessionInput
import com.lucasanso.naorola.domain.session.SessionState
import com.lucasanso.naorola.domain.session.UserAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingSessionMachineTest {
    private val yt = "com.google.android.youtube"
    private val ig = "com.instagram.android"

    private var policy = policy()
    private val machine = BlockingSessionMachine { _, _ -> policy }

    private fun policy(
        protects: Boolean = true,
        allowanceMillis: Long = 0,
        snoozeAllowed: Boolean = true,
        allowDirect: Boolean = true,
        discipline: Boolean = false,
    ) = EffectivePolicy(
        protects = protects,
        inactiveReason = if (protects) null else InactiveReason.PLATFORM_OFF,
        allowanceMillis = allowanceMillis,
        dailyLimitMillis = null,
        dailyRemainingMillis = null,
        snoozeAllowed = snoozeAllowed,
        snoozeMillis = 10 * 60_000L,
        allowReelFromDirect = allowDirect,
        totalMode = false,
        disciplineActive = discipline,
    )

    private fun shorts(t: Long, page: Int = 1) =
        machine.handle(SessionInput.Observed(t, yt, Classification.ShortVideo(Surface.SHORTS_PLAYER, 90, page)))
    private fun reels(t: Long, page: Int) =
        machine.handle(SessionInput.Observed(t, ig, Classification.ShortVideo(Surface.REELS_VIEWER, 90, page)))
    private fun other(t: Long, pkg: String = yt, context: ScreenContext = ScreenContext.UNKNOWN) =
        machine.handle(SessionInput.Observed(t, pkg, Classification.NotShortVideo(context)))
    private fun user(t: Long, action: UserAction) = machine.handle(SessionInput.User(t, action))

    private fun List<Command>.outcomes() = filterIsInstance<Command.RecordEvent>().map { it.event.outcome }

    @Test fun blocksImmediatelyWithoutTolerance() {
        val out = shorts(1_000)
        assertTrue(out.contains(Command.ShowBlockScreen(Surface.SHORTS_PLAYER, snoozeAllowed = true, disciplineActive = false)))
        assertEquals(listOf(BlockOutcome.BLOCKED), out.outcomes())
        assertTrue(out.contains(Command.WatchForeground(true)))
        assertTrue(machine.state is SessionState.Blocked)
    }

    @Test fun repeatedObservationsWhileBlockedDoNotCountAgain() {
        shorts(1_000)
        assertTrue(shorts(1_200).isEmpty())
        assertTrue(shorts(5_000).isEmpty())
    }

    @Test fun goBackSendsBackAndRetriesOnceIfStillInPlayer() {
        shorts(1_000)
        val back = user(2_000, UserAction.GO_BACK)
        assertTrue(back.containsAll(listOf(Command.HideBlockScreen, Command.NavigateBack)))
        assertEquals(listOf(BlockOutcome.WENT_BACK), back.outcomes())
        assertTrue(shorts(2_100).isEmpty()) // grace period: the app is still transitioning
        assertEquals(listOf(Command.NavigateBack), shorts(2_000 + SessionConfig.EXIT_GRACE_MS + 1))
        val again = shorts(5_000)
        assertTrue(again.single() is Command.ShowBlockScreen) // back failed twice: cover again
        assertTrue(machine.state is SessionState.Blocked)
    }

    @Test fun leavingThePlayerEndsTheSession() {
        shorts(1_000)
        user(2_000, UserAction.GO_BACK)
        val out = other(2_500)
        assertTrue(out.contains(Command.WatchForeground(false)))
        assertEquals(SessionState.Idle, machine.state)
    }

    @Test fun leaveAppGoesHome() {
        shorts(1_000)
        val out = user(2_000, UserAction.LEAVE_APP)
        assertTrue(out.containsAll(listOf(Command.HideBlockScreen, Command.NavigateHome)))
        assertEquals(listOf(BlockOutcome.LEFT_APP), out.outcomes())
        assertEquals(SessionState.Idle, machine.state)
    }

    @Test fun toleranceLetsContentPlayThenBlocksAndMeasuresExposure() {
        policy = policy(allowanceMillis = 10_000)
        val enter = shorts(1_000)
        assertTrue(enter.contains(Command.ScheduleTick(11_000)))
        assertTrue(enter.none { it is Command.ShowBlockScreen })
        assertTrue(shorts(6_000).isEmpty())
        val tick = machine.handle(SessionInput.Tick(11_000))
        assertTrue(tick.any { it is Command.ShowBlockScreen })
        val exposure = tick.filterIsInstance<Command.RecordExposure>().single().exposure
        assertEquals(10_000L, exposure.durationMillis)
    }

    @Test fun dailyAllowanceShrinksAcrossEntries() {
        policy = policy(allowanceMillis = 8_000) // e.g. what is left of the daily limit
        shorts(1_000)
        other(4_000)
        policy = policy(allowanceMillis = 5_000) // 3 s were used in the first entry
        val second = shorts(10_000)
        assertTrue(second.contains(Command.ScheduleTick(15_000)))
        assertTrue(machine.handle(SessionInput.Tick(15_000)).any { it is Command.ShowBlockScreen })
    }

    @Test fun leavingDuringToleranceRecordsExposureAndResets() {
        policy = policy(allowanceMillis = 10_000)
        shorts(1_000)
        val out = other(4_000)
        assertEquals(3_000L, out.filterIsInstance<Command.RecordExposure>().single().exposure.durationMillis)
        assertEquals(SessionState.Idle, machine.state)
    }

    @Test fun snoozeStopsBlockingUntilItEnds() {
        shorts(1_000)
        val out = user(2_000, UserAction.SNOOZE)
        assertTrue(out.contains(Command.PersistSnooze(2_000 + 10 * 60_000L)))
        assertEquals(listOf(BlockOutcome.SNOOZED), out.outcomes())
        assertTrue(shorts(3_000).isEmpty()) // settings not persisted yet: machine remembers the snooze
        val afterSnooze = shorts(2_000 + 10 * 60_000L + 1)
        assertTrue(afterSnooze.any { it is Command.ShowBlockScreen })
    }

    @Test fun snoozeIsIgnoredWhenNotAllowed() {
        policy = policy(snoozeAllowed = false, discipline = true)
        val shown = shorts(1_000).filterIsInstance<Command.ShowBlockScreen>().single()
        assertEquals(false, shown.snoozeAllowed)
        assertTrue(user(2_000, UserAction.SNOOZE).isEmpty())
        assertTrue(machine.state is SessionState.Blocked)
    }

    @Test fun unprotectedContentIsMeasuredButNotBlocked() {
        policy = policy(protects = false)
        val enter = shorts(1_000)
        assertTrue(enter.none { it is Command.ShowBlockScreen })
        val out = other(61_000)
        assertEquals(60_000L, out.filterIsInstance<Command.RecordExposure>().single().exposure.durationMillis)
    }

    @Test fun protectionTurningOnWhileWatchingBlocks() {
        policy = policy(protects = false)
        shorts(1_000)
        policy = policy(protects = true)
        assertTrue(shorts(2_000).any { it is Command.ShowBlockScreen })
    }

    @Test fun reelFromDirectIsAllowedUntilTheNextOne() {
        other(1_000, ig, ScreenContext.DIRECT_MESSAGES)
        val open = reels(2_000, page = 7)
        assertEquals(listOf(BlockOutcome.ALLOWED_FROM_DIRECT), open.outcomes())
        assertTrue(reels(5_000, page = 7).isEmpty()) // same Reel keeps playing
        val swipe = reels(9_000, page = 8)
        assertTrue(swipe.any { it is Command.ShowBlockScreen })
        assertEquals(7_000L, swipe.filterIsInstance<Command.RecordExposure>().single().exposure.durationMillis)
    }

    @Test fun reelNotFromDirectIsBlocked() {
        other(1_000, ig, ScreenContext.FEED)
        assertTrue(reels(2_000, page = 1).any { it is Command.ShowBlockScreen })
    }

    @Test fun directExceptionCanBeTurnedOff() {
        policy = policy(allowDirect = false)
        other(1_000, ig, ScreenContext.DIRECT_MESSAGES)
        assertTrue(reels(2_000, page = 1).any { it is Command.ShowBlockScreen })
    }

    @Test fun directOriginExpires() {
        other(1_000, ig, ScreenContext.DIRECT_MESSAGES)
        assertTrue(reels(1_000 + SessionConfig.DIRECT_ORIGIN_WINDOW_MS + 1, page = 1).any { it is Command.ShowBlockScreen })
    }

    @Test fun switchingToAnotherAppClosesTheBlockScreen() {
        shorts(1_000)
        val out = machine.handle(SessionInput.ForegroundChanged(2_000, "com.sec.android.app.launcher"))
        assertTrue(out.contains(Command.HideBlockScreen))
        assertEquals(listOf(BlockOutcome.LEFT_APP), out.outcomes())
        assertEquals(SessionState.Idle, machine.state)
    }

    @Test fun sameAppInForegroundKeepsTheSession() {
        shorts(1_000)
        assertTrue(machine.handle(SessionInput.ForegroundChanged(2_000, yt)).isEmpty())
        assertTrue(machine.state is SessionState.Blocked)
    }

    @Test fun screenOffAndServiceStopCloseEverything() {
        shorts(1_000)
        assertTrue(machine.handle(SessionInput.ScreenOff(2_000)).contains(Command.HideBlockScreen))
        policy = policy(allowanceMillis = 60_000)
        shorts(3_000)
        val out = machine.handle(SessionInput.ServiceStopping(4_000))
        assertEquals(1_000L, out.filterIsInstance<Command.RecordExposure>().single().exposure.durationMillis)
        assertEquals(SessionState.Idle, machine.state)
    }
}
