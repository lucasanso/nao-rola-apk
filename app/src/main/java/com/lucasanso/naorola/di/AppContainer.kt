package com.lucasanso.naorola.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.lucasanso.naorola.core.Clock
import com.lucasanso.naorola.data.db.NaoRolaDatabase
import com.lucasanso.naorola.data.log.LocalLogStore
import com.lucasanso.naorola.data.settings.SettingsRepository
import com.lucasanso.naorola.data.stats.ProtectionEventType
import com.lucasanso.naorola.data.stats.StatsRepository
import com.lucasanso.naorola.detection.classifier.ShortVideoClassifier
import com.lucasanso.naorola.detection.detector.DetectorRegistry
import com.lucasanso.naorola.monitoring.DiagnosticContext
import com.lucasanso.naorola.monitoring.DiagnosticRecorder
import com.lucasanso.naorola.monitoring.ServiceHealthMonitor
import com.lucasanso.naorola.monitoring.UsageStatsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manual dependency injection: one instance of each shared object for the whole process,
 * used by both the accessibility service and the screens.
 */
class AppContainer(private val context: Context) {
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

    val detectors = DetectorRegistry.default()
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

    fun onServiceConnected() {
        _serviceConnected.value = true
        appScope.launch { runCatching { statsRepository.recordProtection(ProtectionEventType.SERVICE_CONNECTED) } }
    }

    fun onServiceDisconnected() {
        _serviceConnected.value = false
        appScope.launch { runCatching { statsRepository.recordProtection(ProtectionEventType.SERVICE_DISCONNECTED) } }
    }

    companion object {
        const val DIAGNOSTICS_DIR = "diagnostics"
    }
}
