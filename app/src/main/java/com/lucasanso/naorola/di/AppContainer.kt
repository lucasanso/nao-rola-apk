package com.lucasanso.naorola.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.lucasanso.naorola.adblock.BrowserFilters
import com.lucasanso.naorola.accessibility.ServiceComponent
import com.lucasanso.naorola.adult.AdultSiteStore
import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.data.db.NaoRolaDatabase
import com.lucasanso.naorola.data.log.LocalLogStore
import com.lucasanso.naorola.data.settings.SettingsRepository
import com.lucasanso.naorola.data.stats.DailyUsageHistory
import com.lucasanso.naorola.data.stats.ProtectionEventType
import com.lucasanso.naorola.data.stats.UsageSource
import com.lucasanso.naorola.data.stats.StatsRepository
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.DetectorRegistry
import com.lucasanso.naorola.monitoring.DiagnosticContext
import com.lucasanso.naorola.monitoring.DiagnosticRecorder
import com.lucasanso.naorola.monitoring.ServiceHealthMonitor
import com.lucasanso.naorola.monitoring.UsageStatsReader
import com.lucasanso.naorola.tile.QuickToggleTile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manual dependency injection: one instance of each shared object for the whole process,
 * used by both the accessibility service and the screens.
 */
class AppContainer(val context: Context) {
    val clock = Clock { System.currentTimeMillis() }

    /** Outlives the service and screens: used for writes that must finish. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val appVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    val logger = LocalLogStore(File(context.filesDir, "logs"), clock, alsoLogcat = debuggable)

    val settingsRepository = SettingsRepository(
        dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("settings") },
        ),
        clock = clock,
        logger = logger,
    )

    private val database: NaoRolaDatabase by lazy {
        Room.databaseBuilder(context, NaoRolaDatabase::class.java, NaoRolaDatabase.NAME).build()
    }

    val statsRepository: StatsRepository by lazy { StatsRepository(database.statsDao(), clock) }

    val dailyUsageHistory: DailyUsageHistory by lazy {
        DailyUsageHistory(database.statsDao(), UsageSource { p, from, to -> usageStats.foregroundMillis(p, from, to) }, clock)
    }

    val browserFilters = BrowserFilters(context, logger)
    val adultSites = AdultSiteStore(context, logger)

    val detectors = DetectorRegistry.default(adultSites.matcher)
    val classifier = ShortVideoClassifier()
    val health = ServiceHealthMonitor(context)
    val usageStats = UsageStatsReader(context, clock)

    val diagnostics = DiagnosticRecorder(
        directory = File(context.filesDir, DIAGNOSTICS_DIR),
        clock = clock,
        logger = logger,
        context = DiagnosticContext(
            appVersion = appVersion,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            versionOf = health::versionOf,
        ),
    )

    private val _serviceConnected = MutableStateFlow(false)

    /** True while Android keeps our accessibility service bound and running. */
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    /** Completed after the first sync of the adult filter with Samsung Internet's list. */
    val adultSyncReady = CompletableDeferred<Unit>()

    init {
        // Loads the user's adult-site list and keeps Samsung Internet's blocker in step with the
        // adult filter switch.
        appScope.launch {
            runCatching { adultSites.load() }.onFailure { logger.error("FiltroAdulto", "Falha ao carregar a lista", it) }
            settingsRepository.settings.map { it.adultBlockEnabled }.distinctUntilChanged().collect { on ->
                runCatching { browserFilters.setAdultRules(if (on) adultSites.browserRules() else emptyList()) }
                    .onFailure { logger.error("FiltroAdulto", "Falha ao atualizar a lista do navegador", it) }
                adultSyncReady.complete(Unit)
            }
        }
    }

    /** Turns the running accessibility service off for real (AccessibilityService.disableSelf). */
    @Volatile private var serviceDisabler: (() -> Unit)? = null

    fun onServiceConnected(disableSelf: () -> Unit) {
        serviceDisabler = disableSelf
        _serviceConnected.value = true
        appScope.launch { runCatching { statsRepository.recordProtection(ProtectionEventType.SERVICE_CONNECTED) } }
        QuickToggleTile.refresh(context)
    }

    fun onServiceDisconnected() {
        serviceDisabler = null
        _serviceConnected.value = false
        appScope.launch { runCatching { statsRepository.recordProtection(ProtectionEventType.SERVICE_DISCONNECTED) } }
        QuickToggleTile.refresh(context)
    }

    /**
     * Turns protection off right now, e.g. before opening a banking app that refuses to run while
     * an accessibility service from outside the Play Store is on. The service is really disabled
     * (nothing is hidden from the bank); turning it back on goes through Android's settings.
     * Returns false when the service was not running.
     */
    fun disableServiceNow(): Boolean {
        val disable = serviceDisabler ?: return false
        logger.info("Servico", "Serviço desligado pelo usuário (ex.: para usar app de banco)")
        disable()
        return true
    }

    private val _bankMode = MutableStateFlow(runCatching { ServiceComponent.isDisabled(context) }.getOrDefault(false))

    /** True while the accessibility service component is removed for banking apps. */
    val bankMode: StateFlow<Boolean> = _bankMode.asStateFlow()

    /**
     * Bank mode: turns the service off and disables its component, so Não-Rola has no
     * accessibility service at all until [exitBankMode]. Works whether or not it was running.
     */
    fun enterBankMode() {
        logger.info("Servico", "Modo banco: serviço de acessibilidade retirado")
        disableServiceNow()
        ServiceComponent.removeFromEnabledList(context)
        ServiceComponent.setEnabled(context, false)
        _bankMode.value = true
        QuickToggleTile.refresh(context)
    }

    /**
     * Brings the component back. Returns true when the service was switched back on directly
     * (permission granted by adb); false means the user turns it on in Android's settings.
     */
    fun exitBankMode(): Boolean {
        ServiceComponent.setEnabled(context, true)
        _bankMode.value = false
        val direct = ServiceComponent.addToEnabledList(context)
        logger.info("Servico", "Modo banco encerrado" + if (direct) " (serviço religado)" else " (religar nas configurações)")
        QuickToggleTile.refresh(context)
        return direct
    }

    companion object {
        const val DIAGNOSTICS_DIR = "diagnostics"
    }
}
