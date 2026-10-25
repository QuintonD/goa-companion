package dev.quinton.goa.web.pwa

import kotlin.js.Promise
import kotlinx.browser.window
import kotlinx.coroutines.await

/** How much this site stores and how much the browser allows it. */
data class StorageEstimate(val usageBytes: Long, val quotaBytes: Long)

/** `navigator.storage`, feature-detected: every call returns null where the browser lacks it. */
object DeviceStorage {
    private val storage: dynamic get() = window.navigator.asDynamic().storage

    suspend fun estimate(): StorageEstimate? {
        val estimate = call("estimate") ?: return null
        val usage = estimate.usage as? Number ?: return null
        val quota = estimate.quota as? Number ?: return null
        return StorageEstimate(usage.toLong(), quota.toLong())
    }

    /** True when the browser won't clear this site's data on its own. */
    suspend fun persisted(): Boolean? = call("persisted") as? Boolean

    /** Asks for persistent storage (Safari grants it to home-screen apps; Chrome to installed or engaged sites). */
    suspend fun persist(): Boolean? = call("persist") as? Boolean

    /** True when running from the Home Screen (iOS `navigator.standalone`, or display-mode standalone). */
    val standalone: Boolean
        get() = window.navigator.asDynamic().standalone == true ||
            window.matchMedia("(display-mode: standalone)").matches

    private suspend fun call(method: String): dynamic {
        val target = storage
        if (target == null || target == undefined || target[method] == undefined) return null
        return try {
            (target[method]() as Promise<dynamic>).await()
        } catch (_: Throwable) {
            null
        }
    }
}
