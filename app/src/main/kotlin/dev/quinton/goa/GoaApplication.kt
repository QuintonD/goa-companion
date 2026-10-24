package dev.quinton.goa

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.quinton.goa.feature.sync.server.SyncServerScheduler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class GoaApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var serverSync: SyncServerScheduler

    /** WorkManager starts on first use with Hilt's worker factory (the startup initializer is removed). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Automatic sync with the group's sync server (ADR-036): watches the group and the synced data.
        serverSync.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
