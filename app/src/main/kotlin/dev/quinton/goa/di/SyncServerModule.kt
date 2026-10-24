package dev.quinton.goa.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.BuildConfig
import dev.quinton.goa.core.data.SyncServerDefaults

/** The build's sync server (ADR-036): BuildConfig.SYNC_SERVER_URL, from the Gradle property goa.syncServer. */
@Module
@InstallIn(SingletonComponent::class)
internal object SyncServerModule {
    @Provides
    fun defaults(): SyncServerDefaults = SyncServerDefaults(
        defaultUrl = BuildConfig.SYNC_SERVER_URL.ifBlank { null },
        // Matches network_security_config: only debug builds may speak plain HTTP to a local test server.
        allowLocalHttp = BuildConfig.DEBUG,
    )
}
