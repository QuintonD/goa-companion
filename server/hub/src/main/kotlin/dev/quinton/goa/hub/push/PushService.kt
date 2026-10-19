package dev.quinton.goa.hub.push

import dev.quinton.goa.hub.HubClock
import dev.quinton.goa.hub.store.DeviceSlot
import dev.quinton.goa.hub.store.DeviceStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** A browser's push subscription (`PushSubscription.toJSON()`: endpoint, keys.p256dh, keys.auth). */
@Serializable
data class PushSubscription(val endpoint: String, val p256dh: String, val auth: String, val createdAt: Long = 0)

/**
 * What the service worker shows (`static/sw.js`): [title], [body], and the page a tap opens
 * ([url], same origin). Notifications with the same [tag] replace each other.
 */
@Serializable
data class PushPayload(val title: String, val body: String = "", val url: String = "/", val tag: String? = null)

@Serializable
data class ScheduledPush(val key: String, val at: Long, val payload: PushPayload)

/** Sends one HTTP POST to a push service; returns the status code. Swapped for a fake in tests. */
fun interface PushTransport {
    suspend fun post(endpoint: String, headers: Map<String, String>, body: ByteArray): Int

    companion object {
        /** The JDK's HTTP client (no extra dependency). */
        fun jdk(): PushTransport {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()
            return PushTransport { endpoint, headers, body ->
                val request = HttpRequest.newBuilder(URI(endpoint))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).await().statusCode()
            }
        }

        private const val TIMEOUT_SECONDS = 15L
    }
}

/** How a [PushService.send] went: delivered to the push service, subscriptions dropped (gone), failures. */
data class PushResult(val sent: Int, val removed: Int, val failed: Int)

/**
 * Web Push for signed-in browsers (ADR-030 §9): per-device subscriptions, [send] now, and a
 * persistent [schedule] (the timer's "phase over" while the iPhone is locked). Messages are
 * encrypted per RFC 8291 and signed per RFC 8292 with the hub's [Vapid] key.
 *
 * Endpoints are accepted only on the browsers' own push services ([isPushService]: https, port 443,
 * no user info), so a signed-in device can't make the hub call arbitrary URLs; redirects aren't
 * followed (the JDK client's default).
 */
class PushService(
    private val deviceStore: DeviceStore,
    val vapid: Vapid,
    private val clock: HubClock,
    private val transport: PushTransport = PushTransport.jdk(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {
    private val jobs = ConcurrentHashMap<String, Job>()

    /** The VAPID application server key for `pushManager.subscribe`. */
    val publicKey: String get() = vapid.publicKey

    fun subscriptions(deviceId: String): List<PushSubscription> = deviceStore.read(deviceId, Subscriptions).orEmpty()

    /** Adds (or refreshes) [subscription] for [deviceId]. Throws [IllegalArgumentException] for a bad one. */
    suspend fun subscribe(deviceId: String, subscription: PushSubscription) {
        require(isPushService(subscription.endpoint)) { "Not a browser push service" }
        require(runCatching { Vapid.unb64(subscription.p256dh).size == P256.PUBLIC_BYTES }.getOrDefault(false)) {
            "Bad subscription key"
        }
        val authOk = runCatching { Vapid.unb64(subscription.auth).size == AUTH_BYTES }.getOrDefault(false)
        require(authOk) { "Bad auth secret" }
        val stamped = subscription.copy(createdAt = clock.nowMillis())
        deviceStore.update(deviceId, Subscriptions) { list ->
            (list.orEmpty().filterNot { it.endpoint == subscription.endpoint } + stamped).takeLast(MAX_SUBSCRIPTIONS)
        }
    }

    suspend fun unsubscribe(deviceId: String, endpoint: String) {
        deviceStore.update(deviceId, Subscriptions) { list ->
            list.orEmpty().filterNot { it.endpoint == endpoint }.ifEmpty { null }
        }
    }

    /** Sends [payload] to every subscription of [deviceId]; subscriptions the service says are gone are removed. */
    suspend fun send(deviceId: String, payload: PushPayload, ttlSeconds: Int = DEFAULT_TTL_SECONDS): PushResult {
        val subs = subscriptions(deviceId)
        if (subs.isEmpty()) return PushResult(0, 0, 0)
        val body = PayloadJson.encodeToString(PushPayload.serializer(), payload).toByteArray()
        var sent = 0
        var failed = 0
        val gone = mutableSetOf<String>()
        for (sub in subs) {
            val status = runCatching { post(sub, body, ttlSeconds) }.getOrElse { e ->
                log.warn("Push to {} failed: {}", hostOf(sub.endpoint), e.toString())
                -1
            }
            when (status) {
                in HTTP_OK_RANGE -> sent++
                HTTP_NOT_FOUND, HTTP_GONE -> gone += sub.endpoint
                else -> failed++.also { log.warn("Push service {} answered {}", hostOf(sub.endpoint), status) }
            }
        }
        if (gone.isNotEmpty()) {
            deviceStore.update(deviceId, Subscriptions) { list ->
                list.orEmpty().filterNot { it.endpoint in gone }.ifEmpty { null }
            }
        }
        return PushResult(sent, gone.size, failed)
    }

    /**
     * Sends [payload] to [deviceId] at [at] (epoch ms). One schedule per [key]: scheduling the same
     * key again replaces it. Survives a hub restart (kept in the device's `push-schedule` slot);
     * a push more than [STALE_MS] late after a restart is dropped.
     */
    suspend fun schedule(deviceId: String, at: Long, payload: PushPayload, key: String = DEFAULT_KEY) {
        val item = ScheduledPush(key, at, payload)
        deviceStore.update(deviceId, Schedule) { list -> list.orEmpty().filterNot { it.key == key } + item }
        launch(deviceId, item)
    }

    suspend fun cancel(deviceId: String, key: String = DEFAULT_KEY) {
        jobs.remove(jobKey(deviceId, key))?.cancel()
        deviceStore.update(deviceId, Schedule) { list -> list.orEmpty().filterNot { it.key == key }.ifEmpty { null } }
    }

    fun scheduled(deviceId: String): List<ScheduledPush> = deviceStore.read(deviceId, Schedule).orEmpty()

    /** Re-arms every stored schedule (hub start). */
    fun start() {
        val now = clock.nowMillis()
        for (deviceId in deviceStore.devicesWith(Schedule)) {
            scheduled(deviceId).forEach { item ->
                if (item.at >= now - STALE_MS) launch(deviceId, item)
            }
        }
    }

    fun close() = scope.cancel()

    private fun launch(deviceId: String, item: ScheduledPush) {
        val jobKey = jobKey(deviceId, item.key)
        jobs.remove(jobKey)?.cancel()
        jobs[jobKey] = scope.launch {
            delay((item.at - clock.nowMillis()).coerceAtLeast(0))
            deviceStore.update(deviceId, Schedule) { list -> list.orEmpty().filterNot { it == item }.ifEmpty { null } }
            runCatching {
                send(deviceId, item.payload)
            }.onFailure { log.warn("Scheduled push failed: {}", it.toString()) }
            jobs.remove(jobKey, coroutineContext[Job])
        }
    }

    private suspend fun post(sub: PushSubscription, body: ByteArray, ttlSeconds: Int): Int {
        val encrypted = WebPushCrypto.encrypt(body, Vapid.unb64(sub.p256dh), Vapid.unb64(sub.auth))
        val headers = mapOf(
            "Authorization" to vapid.authorization(sub.endpoint, clock.nowMillis() / MILLIS),
            "Content-Encoding" to "aes128gcm",
            "Content-Type" to "application/octet-stream",
            "TTL" to ttlSeconds.toString(),
            "Urgency" to "high",
        )
        return transport.post(sub.endpoint, headers, encrypted)
    }

    companion object {
        const val DEFAULT_KEY = "default"
        const val DEFAULT_TTL_SECONDS = 120
        const val STALE_MS = 60_000L
        private const val AUTH_BYTES = 16
        private const val MAX_SUBSCRIPTIONS = 5
        private const val MILLIS = 1000L
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_GONE = 410
        private const val HTTPS_PORT = 443
        private val HTTP_OK_RANGE = 200..299
        private val log = LoggerFactory.getLogger(PushService::class.java)
        private val PayloadJson = Json { encodeDefaults = true }

        val Subscriptions = DeviceSlot("push", ListSerializer(PushSubscription.serializer()))
        val Schedule = DeviceSlot("push-schedule", ListSerializer(ScheduledPush.serializer()))

        /** The push services of Safari/iOS, Chrome/Android, Firefox and Edge. */
        private val PUSH_HOSTS = listOf(
            "push.apple.com",
            "fcm.googleapis.com",
            "android.googleapis.com",
            "push.services.mozilla.com",
            "notify.windows.com",
        )

        fun isPushService(endpoint: String): Boolean {
            val uri = runCatching { URI(endpoint) }.getOrNull() ?: return false
            val host = uri.host?.lowercase() ?: return false
            return uri.scheme == "https" && uri.rawUserInfo == null && (uri.port == -1 || uri.port == HTTPS_PORT) &&
                PUSH_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        private fun jobKey(deviceId: String, key: String) = "$deviceId/$key"

        private fun hostOf(endpoint: String) = runCatching { URI(endpoint).host }.getOrNull() ?: "?"
    }
}
