package com.lucasanso.naorola.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.lucasanso.naorola.blocking.AudioFocusMuter
import com.lucasanso.naorola.blocking.BlockScreenContent
import com.lucasanso.naorola.blocking.BlockingOverlayManager
import com.lucasanso.naorola.detection.classifier.Classification
import com.lucasanso.naorola.detection.signal.DetectionResult
import com.lucasanso.naorola.di.AppContainer
import com.lucasanso.naorola.domain.model.ProtectionSettings
import com.lucasanso.naorola.domain.policy.BlockingPolicy
import com.lucasanso.naorola.domain.session.BlockingSessionMachine
import com.lucasanso.naorola.domain.session.Command
import com.lucasanso.naorola.domain.session.DailyExposureTracker
import com.lucasanso.naorola.domain.session.SessionInput
import com.lucasanso.naorola.domain.session.UserAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Glue between Android and the engine:
 * events → throttle → read screen → detector → classifier → session machine → commands.
 *
 * Threading: the session machine and screen reads run on ONE background thread ([engine]);
 * windows and Compose run on the main thread; database/DataStore writes on IO.
 */
class ProtectionCoordinator(
    private val context: Context,
    private val screen: ScreenAccess,
    private val container: AppContainer,
) {
    private val clock = container.clock
    private val logger = container.logger
    private val job = SupervisorJob()
    private val mainScope = CoroutineScope(job + Dispatchers.Main.immediate)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val engine = Dispatchers.Default.limitedParallelism(1)
    private val engineScope = CoroutineScope(job + engine)

    private val throttle = EvaluationThrottle()

    @Volatile private var settings = ProtectionSettings()

    @Volatile private var dynamicColor = false
    private var appliedPackages: Set<String>? = null
    private var lastLogKey: String? = null

    /** Short-video time already allowed today (daily limit); updated as exposures are recorded. */
    private val dailyUsage = DailyExposureTracker()

    private val machine = BlockingSessionMachine { surface, now ->
        BlockingPolicy.evaluate(settings, surface, now, ZoneId.systemDefault(), dailyUsage.usedToday(surface.platform, now))
    }
    private val overlay = BlockingOverlayManager(context, logger, onAction = ::onUserAction)
    private val audio = AudioFocusMuter(context)
    private val foregroundWatcher = ForegroundWatcher(engineScope, screen) { pkg ->
        handle(SessionInput.ForegroundChanged(clock.nowMillis(), pkg))
    }
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            engineScope.launch { handle(SessionInput.ScreenOff(clock.nowMillis())) }
        }
    }

    fun start() {
        mainScope.launch {
            container.settingsRepository.settings.collect {
                settings = it
                applyMonitoredPackages()
            }
        }
        mainScope.launch { container.settingsRepository.uiPreferences.collect { dynamicColor = it.dynamicColor } }
        mainScope.launch { container.diagnostics.armed.collect { applyMonitoredPackages() } }
        engineScope.launch {
            runCatching { container.statsRepository.today().first() }
                .onSuccess { dailyUsage.seed(clock.nowMillis(), it.exposureMillisByPlatform) }
                .onFailure { logger.error(TAG, "Falha ao carregar o uso de hoje", it) }
        }
        ContextCompat.registerReceiver(
            context, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        logger.info(TAG, "Proteção iniciada")
    }

    /** Called on the main thread for every accessibility event of a monitored app. */
    fun onEvent(kind: EventKind) {
        val delayMillis = throttle.onEvent(kind, clock.nowMillis()) ?: return
        engineScope.launch {
            delay(delayMillis)
            evaluate()
        }
    }

    /** Called on the main thread when the service goes away. Never blocks. */
    fun stop() {
        runCatching { context.unregisterReceiver(screenOffReceiver) }
        overlay.hide()
        audio.release()
        // Close the session (e.g. record exposure) after our own scopes are gone.
        container.appScope.launch(engine) { execute(machine.handle(SessionInput.ServiceStopping(clock.nowMillis())), persistOnly = true) }
        job.cancel()
        logger.info(TAG, "Proteção parada")
    }

    private fun evaluate() {
        val now = clock.nowMillis()
        throttle.onEvaluated(now)
        try {
            val root = screen.activeRoot() ?: return
            val pkg = root.packageName?.toString() ?: return
            if (pkg == screen.ownPackage) return
            val detector = container.detectors.detectorFor(pkg)
            if (detector == null) {
                handle(SessionInput.ForegroundChanged(now, pkg))
                return
            }
            val (width, height) = screen.displaySize()
            val tree = AccessibilityUiTree(pkg, root, width, height)
            val detection = detector.detect(tree)
            val classification = container.classifier.classify(detection)
            container.diagnostics.captureIfArmed(tree, detection, classification)
            logTransition(pkg, detection, classification)
            handle(SessionInput.Observed(now, pkg, classification))
        } catch (e: Exception) {
            logger.error(TAG, "Falha ao avaliar a tela", e)
        }
    }

    private fun onUserAction(action: UserAction) {
        engineScope.launch { handle(SessionInput.User(clock.nowMillis(), action)) }
    }

    /** Must run on [engine]. */
    private fun handle(input: SessionInput) {
        try {
            execute(machine.handle(input), persistOnly = false)
        } catch (e: Exception) {
            logger.error(TAG, "Falha na sessão de bloqueio", e)
        }
    }

    private fun execute(commands: List<Command>, persistOnly: Boolean) {
        for (command in commands) {
            when (command) {
                is Command.RecordEvent -> container.appScope.launch {
                    runCatching { container.statsRepository.record(command.event) }
                        .onFailure { logger.error(TAG, "Falha ao gravar evento", it) }
                }
                is Command.RecordExposure -> {
                    dailyUsage.add(command.exposure)
                    container.appScope.launch {
                        runCatching { container.statsRepository.recordExposure(command.exposure) }
                            .onFailure { logger.error(TAG, "Falha ao gravar tempo", it) }
                    }
                }
                is Command.PersistSnooze -> container.appScope.launch {
                    container.settingsRepository.setSnoozedUntil(command.untilMillis)
                }
                else -> if (!persistOnly) executeOnScreen(command)
            }
        }
    }

    private fun executeOnScreen(command: Command) {
        when (command) {
            is Command.ShowBlockScreen -> {
                val content = BlockScreenContent(
                    surface = command.surface,
                    snoozeAllowed = command.snoozeAllowed,
                    snoozeMinutes = settings.snoozeMinutes,
                    disciplineActive = command.disciplineActive,
                    dynamicColor = dynamicColor,
                )
                mainScope.launch {
                    overlay.show(content)
                    audio.mute()
                }
                logger.info(TAG, "Bloqueio: ${command.surface}")
            }
            Command.HideBlockScreen -> mainScope.launch {
                overlay.hide()
                audio.release()
            }
            Command.NavigateBack -> if (!screen.goBack()) logger.warn(TAG, "Ação 'voltar' recusada pelo sistema")
            Command.NavigateHome -> if (!screen.goHome()) logger.warn(TAG, "Ação 'início' recusada pelo sistema")
            is Command.ScheduleTick -> engineScope.launch {
                delay((command.atMillis - clock.nowMillis()).coerceAtLeast(0))
                handle(SessionInput.Tick(clock.nowMillis()))
            }
            is Command.WatchForeground -> foregroundWatcher.setEnabled(command.enabled)
            is Command.RecordEvent, is Command.RecordExposure, is Command.PersistSnooze -> Unit
        }
    }

    private fun applyMonitoredPackages() {
        val packages = MonitoredPackages.compute(
            settings, clock.nowMillis(), container.diagnostics.armed.value, screen.ownPackage,
        )
        if (packages == appliedPackages) return
        runCatching { screen.setMonitoredPackages(packages) }
            .onSuccess {
                appliedPackages = packages
                logger.info(TAG, "Apps observados: ${packages.size}")
            }
            .onFailure { logger.error(TAG, "Falha ao atualizar apps observados", it) }
    }

    /** Logs only when the conclusion changes, and only identifiers (never screen content). */
    private fun logTransition(pkg: String, detection: DetectionResult, classification: Classification) {
        val summary = when (classification) {
            is Classification.ShortVideo -> "VIDEO_CURTO ${classification.surface} ${classification.confidence}"
            is Classification.NotShortVideo -> "NAO ${classification.context}" + (classification.vetoedBy?.let { " veto=$it" } ?: "")
        }
        val key = "$pkg|$summary"
        if (key == lastLogKey) return
        lastLogKey = key
        val signals = detection.signals.joinToString(",") { it.id }
        logger.info(TAG, "$pkg: $summary [$signals]" + if (detection.truncated) " (árvore truncada)" else "")
    }

    private companion object {
        const val TAG = "Protecao"
    }
}
