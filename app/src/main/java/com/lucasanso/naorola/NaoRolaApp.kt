package com.lucasanso.naorola

import android.app.Application
import com.lucasanso.naorola.di.AppContainer
import kotlinx.coroutines.launch

class NaoRolaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            runCatching { container.statsRepository.purgeOlderThan() }
                .onFailure { container.logger.error("App", "Falha ao limpar histórico antigo", it) }
        }
    }
}
