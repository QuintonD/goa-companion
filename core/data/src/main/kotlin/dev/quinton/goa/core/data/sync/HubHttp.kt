package dev.quinton.goa.core.data.sync

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response

/** The HTTP client that talks to the group's hub, and nothing else (ADR-030 §5). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HubHttp

object HubHttpClient {
    /**
     * No cache, cookies or logging; redirects are refused (a hub answers itself, and the bearer
     * token must never follow a redirect). Timeouts bound every stage; the whole call ends within
     * [callSeconds] even on a trickling connection.
     */
    fun create(
        connectSeconds: Long = 15,
        ioSeconds: Long = 30,
        callSeconds: Long = 90,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectSeconds, TimeUnit.SECONDS)
        .readTimeout(ioSeconds, TimeUnit.SECONDS)
        .writeTimeout(ioSeconds, TimeUnit.SECONDS)
        .callTimeout(callSeconds, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
internal object HubHttpModule {
    @Provides
    @Singleton
    @HubHttp
    fun client(): OkHttpClient = HubHttpClient.create()
}

/** Runs the call on OkHttp's dispatcher; cancelling the coroutine cancels the call. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        },
    )
}
