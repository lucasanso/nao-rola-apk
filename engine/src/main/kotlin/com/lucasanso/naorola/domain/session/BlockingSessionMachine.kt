package com.lucasanso.naorola.domain.session

import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.domain.model.BlockEvent
import com.lucasanso.naorola.domain.model.BlockOutcome
import com.lucasanso.naorola.domain.model.ScreenContext
import com.lucasanso.naorola.domain.model.ShortVideoExposure
import com.lucasanso.naorola.domain.model.Surface
import com.lucasanso.naorola.domain.policy.EffectivePolicy

/** Supplies the effective policy for a surface at a moment (backed by the latest settings). */
fun interface PolicyProvider {
    fun policyFor(surface: Surface, now: Long): EffectivePolicy
}

enum class UserAction { GO_BACK, LEAVE_APP, SNOOZE }

sealed interface SessionInput {
    val time: Long

    data class Observed(override val time: Long, val packageName: String, val classification: Classification) : SessionInput

    /** The app window under our overlay changed (null when unknown). */
    data class ForegroundChanged(override val time: Long, val packageName: String?) : SessionInput
    data class User(override val time: Long, val action: UserAction) : SessionInput

    /** A timer previously requested with [Command.ScheduleTick] fired. */
    data class Tick(override val time: Long) : SessionInput
    data class ScreenOff(override val time: Long) : SessionInput
    data class ServiceStopping(override val time: Long) : SessionInput
}

/** Side effects for the Android layer to execute. The machine itself never touches Android. */
sealed interface Command {
    data class ShowBlockScreen(val surface: Surface, val snoozeAllowed: Boolean, val disciplineActive: Boolean) : Command
    data object HideBlockScreen : Command
    data object NavigateBack : Command
    data object NavigateHome : Command
    data class PersistSnooze(val untilMillis: Long) : Command
    data class RecordEvent(val event: BlockEvent) : Command
    data class RecordExposure(val exposure: ShortVideoExposure) : Command
    data class ScheduleTick(val atMillis: Long) : Command

    /** Start/stop checking which app is in the foreground (needed while a session is open). */
    data class WatchForeground(val enabled: Boolean) : Command
}

sealed interface SessionState {
    data object Idle : SessionState

    /** Short video on screen and not blocked: tolerance running ([blockAt]) or protection off (null). */
    data class Watching(val surface: Surface, val packageName: String, val since: Long, val blockAt: Long?) : SessionState

    /** Reel opened from a DM: allowed until the user swipes to another page. */
    data class AllowedSingle(val surface: Surface, val packageName: String, val since: Long, val pageKey: Int?) : SessionState
    data class Blocked(val surface: Surface, val packageName: String, val since: Long) : SessionState

    /** "Back" was sent; waiting to confirm the player was left. */
    data class Exiting(val surface: Surface, val packageName: String, val since: Long, val attempts: Int) : SessionState
}

object SessionConfig {
    /** A Reel viewer that opens within this time after a DM screen counts as "opened from a DM". */
    const val DIRECT_ORIGIN_WINDOW_MS = 8_000L

    /** Time given to the app to leave the player after "back" before checking again. */
    const val EXIT_GRACE_MS = 900L
    const val MAX_BACK_ATTEMPTS = 2
}

/**
 * Decides, over time, what to do with short-video content: tolerance, block screen, snooze,
 * the DM exception and exposure measurement. Deterministic and clock-free: time comes in with
 * every input, so every scenario is testable on the JVM.
 */
class BlockingSessionMachine(private val policies: PolicyProvider) {

    var state: SessionState = SessionState.Idle
        private set

    private var lastContext = ScreenContext.UNKNOWN
    private var lastContextPackage: String? = null
    private var lastContextAt = Long.MIN_VALUE
    private var snoozedUntil = 0L

    fun handle(input: SessionInput): List<Command> {
        val out = ArrayList<Command>()
        when (input) {
            is SessionInput.Observed -> onObserved(input, out)
            is SessionInput.ForegroundChanged -> onForeground(input, out)
            is SessionInput.User -> onUser(input, out)
            is SessionInput.Tick -> onTick(input.time, out)
            is SessionInput.ScreenOff -> endSession(input.time, out, outcome = null)
            is SessionInput.ServiceStopping -> endSession(input.time, out, outcome = null)
        }
        return out
    }

    private fun onObserved(input: SessionInput.Observed, out: MutableList<Command>) {
        val classification = input.classification
        if (classification is Classification.NotShortVideo) {
            lastContext = classification.context
            lastContextPackage = input.packageName
            lastContextAt = input.time
            if (state !is SessionState.Idle) endSession(input.time, out, outcome = null)
            return
        }
        classification as Classification.ShortVideo
        val now = input.time
        when (val s = state) {
            SessionState.Idle -> enter(classification, input.packageName, now, out)
            is SessionState.Watching ->
                if (s.surface != classification.surface || s.packageName != input.packageName) {
                    endSession(now, out, outcome = null)
                    enter(classification, input.packageName, now, out)
                } else {
                    reevaluateWatching(s, now, out)
                }
            is SessionState.AllowedSingle -> onAllowedSingle(s, classification.pageKey, now, out)
            is SessionState.Blocked -> Unit
            is SessionState.Exiting -> onStillInPlayerAfterBack(s, now, out)
        }
    }

    private fun enter(c: Classification.ShortVideo, packageName: String, now: Long, out: MutableList<Command>) {
        val policy = policies.policyFor(c.surface, now)
        out += Command.WatchForeground(true)
        when {
            !protects(policy, now) -> state = SessionState.Watching(c.surface, packageName, now, blockAt = null)
            policy.allowReelFromDirect && c.surface == Surface.REELS_VIEWER && cameFromDirect(packageName, now) -> {
                state = SessionState.AllowedSingle(c.surface, packageName, now, c.pageKey)
                out += record(now, c.surface, BlockOutcome.ALLOWED_FROM_DIRECT)
            }
            policy.allowanceMillis > 0 -> {
                val blockAt = now + policy.allowanceMillis
                state = SessionState.Watching(c.surface, packageName, now, blockAt)
                out += Command.ScheduleTick(blockAt)
                out += record(now, c.surface, BlockOutcome.TOLERANCE_STARTED)
            }
            else -> block(c.surface, packageName, exposureSince = null, now = now, policy = policy, out = out)
        }
    }

    private fun reevaluateWatching(s: SessionState.Watching, now: Long, out: MutableList<Command>) {
        val policy = policies.policyFor(s.surface, now)
        when {
            !protects(policy, now) -> if (s.blockAt != null) state = s.copy(blockAt = null)
            s.blockAt == null ->
                if (policy.allowanceMillis > 0) {
                    val blockAt = now + policy.allowanceMillis
                    state = s.copy(blockAt = blockAt)
                    out += Command.ScheduleTick(blockAt)
                } else {
                    block(s.surface, s.packageName, s.since, now, policy, out)
                }
            now >= s.blockAt -> block(s.surface, s.packageName, s.since, now, policy, out)
        }
    }

    private fun onAllowedSingle(s: SessionState.AllowedSingle, pageKey: Int?, now: Long, out: MutableList<Command>) {
        val policy = policies.policyFor(s.surface, now)
        when {
            !protects(policy, now) -> state = SessionState.Watching(s.surface, s.packageName, s.since, blockAt = null)
            s.pageKey == null && pageKey != null -> state = s.copy(pageKey = pageKey)
            s.pageKey != null && pageKey != null && pageKey != s.pageKey ->
                // Swiped to the next Reel: from here on the normal rules apply.
                if (policy.allowanceMillis > 0) {
                    val blockAt = now + policy.allowanceMillis
                    state = SessionState.Watching(s.surface, s.packageName, s.since, blockAt)
                    out += Command.ScheduleTick(blockAt)
                } else {
                    block(s.surface, s.packageName, s.since, now, policy, out)
                }
        }
    }

    private fun onStillInPlayerAfterBack(s: SessionState.Exiting, now: Long, out: MutableList<Command>) {
        if (now - s.since < SessionConfig.EXIT_GRACE_MS) return
        if (s.attempts < SessionConfig.MAX_BACK_ATTEMPTS) {
            state = s.copy(since = now, attempts = s.attempts + 1)
            out += Command.NavigateBack
        } else {
            // "Back" did not leave the player (e.g. the app changed): cover it again.
            val policy = policies.policyFor(s.surface, now)
            state = SessionState.Blocked(s.surface, s.packageName, now)
            out += Command.ShowBlockScreen(s.surface, policy.snoozeAllowed, policy.disciplineActive)
        }
    }

    private fun onForeground(input: SessionInput.ForegroundChanged, out: MutableList<Command>) {
        val pkg = input.packageName ?: return
        val current = packageOf(state) ?: return
        if (pkg != current) {
            val outcome = if (state is SessionState.Blocked) BlockOutcome.LEFT_APP else null
            endSession(input.time, out, outcome)
        }
    }

    private fun onUser(input: SessionInput.User, out: MutableList<Command>) {
        val s = state as? SessionState.Blocked ?: return
        val now = input.time
        when (input.action) {
            UserAction.GO_BACK -> {
                out += Command.HideBlockScreen
                out += Command.NavigateBack
                out += record(now, s.surface, BlockOutcome.WENT_BACK)
                state = SessionState.Exiting(s.surface, s.packageName, now, attempts = 1)
            }
            UserAction.LEAVE_APP -> {
                out += Command.HideBlockScreen
                out += Command.NavigateHome
                out += record(now, s.surface, BlockOutcome.LEFT_APP)
                toIdle(out)
            }
            UserAction.SNOOZE -> {
                val policy = policies.policyFor(s.surface, now)
                if (!policy.snoozeAllowed) return
                snoozedUntil = now + policy.snoozeMillis
                out += Command.HideBlockScreen
                out += Command.PersistSnooze(snoozedUntil)
                out += Command.ScheduleTick(snoozedUntil)
                out += record(now, s.surface, BlockOutcome.SNOOZED)
                state = SessionState.Watching(s.surface, s.packageName, now, blockAt = null)
            }
        }
    }

    private fun onTick(now: Long, out: MutableList<Command>) {
        val s = state as? SessionState.Watching ?: return
        reevaluateWatching(s, now, out)
    }

    private fun block(
        surface: Surface,
        packageName: String,
        exposureSince: Long?,
        now: Long,
        policy: EffectivePolicy,
        out: MutableList<Command>,
    ) {
        if (exposureSince != null) recordExposure(surface, exposureSince, now, out)
        state = SessionState.Blocked(surface, packageName, now)
        out += Command.ShowBlockScreen(surface, policy.snoozeAllowed, policy.disciplineActive)
        out += record(now, surface, BlockOutcome.BLOCKED)
    }

    private fun endSession(now: Long, out: MutableList<Command>, outcome: BlockOutcome?) {
        when (val s = state) {
            SessionState.Idle -> return
            is SessionState.Watching -> recordExposure(s.surface, s.since, now, out)
            is SessionState.AllowedSingle -> recordExposure(s.surface, s.since, now, out)
            is SessionState.Blocked -> {
                out += Command.HideBlockScreen
                if (outcome != null) out += record(now, s.surface, outcome)
            }
            is SessionState.Exiting -> Unit
        }
        toIdle(out)
    }

    private fun toIdle(out: MutableList<Command>) {
        state = SessionState.Idle
        out += Command.WatchForeground(false)
    }

    /** The machine's own snooze memory covers the moment before settings are persisted. */
    private fun protects(policy: EffectivePolicy, now: Long): Boolean =
        policy.protects && !(policy.snoozeAllowed && now < snoozedUntil)

    private fun cameFromDirect(packageName: String, now: Long): Boolean =
        lastContext == ScreenContext.DIRECT_MESSAGES &&
            lastContextPackage == packageName &&
            now - lastContextAt <= SessionConfig.DIRECT_ORIGIN_WINDOW_MS

    private fun recordExposure(surface: Surface, since: Long, now: Long, out: MutableList<Command>) {
        if (now > since) out += Command.RecordExposure(ShortVideoExposure(surface, since, now))
    }

    private fun record(now: Long, surface: Surface, outcome: BlockOutcome) =
        Command.RecordEvent(BlockEvent(now, surface, outcome))

    private fun packageOf(s: SessionState): String? = when (s) {
        SessionState.Idle -> null
        is SessionState.Watching -> s.packageName
        is SessionState.AllowedSingle -> s.packageName
        is SessionState.Blocked -> s.packageName
        is SessionState.Exiting -> s.packageName
    }
}
