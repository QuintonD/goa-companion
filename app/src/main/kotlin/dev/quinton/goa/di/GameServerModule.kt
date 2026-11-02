package dev.quinton.goa.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.BuildConfig
import dev.quinton.goa.core.data.online.GameServerDefaults

/** The build's game server (ADR-056): BuildConfig.GAME_SERVER_URL, from the Gradle property goa.gameServer. */
@Module
@InstallIn(SingletonComponent::class)
internal object GameServerModule {
    @Provides
    fun defaults(): GameServerDefaults = GameServerDefaults(
        url = BuildConfig.GAME_SERVER_URL.ifBlank { null },
        // Matches network_security_config: only debug builds may speak plain HTTP to a local test server.
        allowLocalHttp = BuildConfig.DEBUG,
    )
}
