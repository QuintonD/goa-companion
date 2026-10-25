package dev.quinton.goa.web.group

import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Keeps the app's tabs (a Safari tab beside the home-screen app, two desktop tabs) from writing the
 * group's data over each other: [locked] runs a write under a Web Lock named [name] (Safari 15.4+,
 * every current browser; without it, only this tab's own writes are serialised), and [announce] /
 * [listen] tell the other tabs to reload over a `BroadcastChannel`.
 */
interface TabChannel {
    suspend fun <T> locked(block: suspend () -> T): T

    fun announce()

    fun listen(onChange: suspend () -> Unit)

    /** One tab, no browser APIs (tests). */
    object None : TabChannel {
        override suspend fun <T> locked(block: suspend () -> T): T = block()

        override fun announce() = Unit

        override fun listen(onChange: suspend () -> Unit) = Unit
    }

    companion object {
        /** The browser's channel and lock named [name]; [None] where the APIs are missing. */
        fun browser(name: String): TabChannel = BrowserTabChannel(name)
    }
}

private class BrowserTabChannel(private val name: String) : TabChannel {
    private val channel: dynamic = openChannel(name)

    private val locks: dynamic
        get() = js("(typeof navigator !== 'undefined' && navigator.locks) ? navigator.locks : null")

    override suspend fun <T> locked(block: suspend () -> T): T {
        val manager = locks ?: return block()
        val acquired = CompletableDeferred<Unit>()
        var release: () -> Unit = {}
        val held = Promise<Unit> { resolve, _ -> release = { resolve(Unit) } }
        val options: dynamic = js("({})")
        // A tab that never lets go (frozen in the background) must not stop this one for good.
        if (js("typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout === 'function'") as Boolean) {
            options.signal = js("AbortSignal.timeout(5000)")
        }
        val request = manager.request(name, options) {
            // Nobody waits any more (cancelled): give the lock back at once.
            if (!acquired.complete(Unit)) release()
            held
        }.unsafeCast<Promise<Any?>>()
        request.catch {
            // Timed out waiting for another tab: go ahead without the lock rather than hang.
            acquired.complete(Unit)
        }
        try {
            acquired.await()
        } catch (e: CancellationException) {
            acquired.cancel()
            release()
            throw e
        }
        try {
            return block()
        } finally {
            release()
        }
    }

    override fun announce() {
        if (channel != null) channel.postMessage("changed")
    }

    override fun listen(onChange: suspend () -> Unit) {
        if (channel == null) return
        channel.onmessage = { _: dynamic -> MainScope().launch { onChange() } }
    }
}

@Suppress("UnusedParameter") // read by the js() below
private fun openChannel(channelName: String): dynamic {
    if (!(js("typeof BroadcastChannel !== 'undefined'") as Boolean)) return null
    val channel: dynamic = js("new BroadcastChannel(channelName)")
    // Node (the unit tests) would otherwise stay alive for an open channel; browsers have no unref.
    if (js("typeof channel.unref === 'function'") as Boolean) channel.unref()
    return channel
}
