package dev.quinton.goa.core.data.online

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** REST to the game server and the two static files on Pages (ADR-056). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnlineRestHttp

/** The game's WebSocket: open for as long as a game, so no call timeout. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GameSocketHttp

/**
 * Apart from the sync server's client (ADR-036), as that server is apart from this one. No cache, cookies
 * or logging; redirects are refused, so the token can't follow one anywhere.
 */
object OnlineHttpClients {
    fun rest(connectSeconds: Long = 10, ioSeconds: Long = 20, callSeconds: Long = 30): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(connectSeconds, TimeUnit.SECONDS)
            .readTimeout(ioSeconds, TimeUnit.SECONDS)
            .writeTimeout(ioSeconds, TimeUnit.SECONDS)
            .callTimeout(callSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    /** OkHttp fails the socket if a ping's pong doesn't come: the driver then reconnects. */
    fun socket(connectSeconds: Long = 10, writeSeconds: Long = 15, pingSeconds: Long = 20): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(connectSeconds, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(writeSeconds, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .pingInterval(pingSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
internal object OnlineHttpModule {
    @Provides
    @Singleton
    @OnlineRestHttp
    fun rest(): OkHttpClient = OnlineHttpClients.rest()

    @Provides
    @Singleton
    @GameSocketHttp
    fun socket(): OkHttpClient = OnlineHttpClients.socket()
}
