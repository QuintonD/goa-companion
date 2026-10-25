package dev.quinton.goa.web.play.data

import dev.quinton.goa.web.engine.EngineJson
import dev.quinton.goa.web.engine.EngineMessage
import dev.quinton.goa.web.engine.EngineReply
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** The page's end of a channel to an engine worker: text messages both ways. */
interface EngineChannel {
    fun post(message: String)

    /** Stops the worker at once (its search is dropped). */
    fun terminate()

    var onMessage: (String) -> Unit
    var onFailure: (String) -> Unit
}

/** A dedicated Web Worker running `engine-worker.js` (`:web:worker`). */
class WorkerChannel(url: String) : EngineChannel {
    private val worker: dynamic = newWorker(url)
    override var onMessage: (String) -> Unit = {}
    override var onFailure: (String) -> Unit = {}

    init {
        worker.onmessage = { event: dynamic -> onMessage(event.data as String) }
        worker.onerror = { event: dynamic ->
            event.preventDefault()
            onFailure((event.message as? String) ?: "The game engine stopped.")
        }
    }

    override fun post(message: String) {
        worker.postMessage(message)
    }

    override fun terminate() {
        worker.terminate()
    }
}

@Suppress("UNUSED_PARAMETER") // read by the js() code
private fun newWorker(url: String): dynamic = js("new Worker(url)")

/** The engine worker failed (it couldn't load, or it crashed); a fresh one starts with the next request. */
class EngineUnavailableException(message: String) : Exception(message)

/**
 * Talks to one engine Web Worker (ADR-043 "Addendum (play, W4)"): [call] posts a request and waits
 * for its reply. The worker is started on the first call. [reset] stops it, whatever it is doing
 * (the computer thinking, the coach looking): calls in flight end with [CancellationException], and
 * [generation] counts up so a screen knows the worker lost its game and restores it from the save.
 * A call whose caller is cancelled while the worker works on it also stops the worker, so leaving a
 * screen never leaves a search running.
 */
class EngineClient(private val open: () -> EngineChannel = { WorkerChannel(WORKER_URL) }) {
    private var channel: EngineChannel? = null
    private val waiting = mutableMapOf<Int, CompletableDeferred<EngineResponse>>()
    private var nextId = 0

    /** Counts the workers started; a new one knows nothing of the last one's game or puzzle. */
    var generation: Int = 0
        private set

    val busy: Boolean get() = waiting.isNotEmpty()

    suspend fun call(request: EngineRequest): EngineResponse {
        val channel = channel ?: start()
        val id = ++nextId
        val reply = CompletableDeferred<EngineResponse>()
        waiting[id] = reply
        channel.post(EngineJson.encodeToString(EngineMessage.serializer(), EngineMessage(id, request)))
        try {
            return reply.await()
        } catch (e: CancellationException) {
            // The caller left (or reset): the worker would go on searching for nobody.
            if (waiting.containsKey(id)) reset()
            throw e
        } finally {
            waiting.remove(id)
        }
    }

    /** Stops the worker; the next call starts a fresh one. */
    fun reset() {
        channel?.terminate()
        channel = null
        val calls = waiting.values.toList()
        waiting.clear()
        calls.forEach { it.cancel(CancellationException("The engine was stopped")) }
    }

    private fun start(): EngineChannel = open().also { started ->
        generation++
        channel = started
        started.onMessage = { text ->
            val reply = EngineJson.decodeFromString(EngineReply.serializer(), text)
            waiting.remove(reply.id)?.complete(reply.response)
        }
        started.onFailure = { message ->
            if (channel === started) {
                channel = null
                started.terminate()
            }
            val calls = waiting.values.toList()
            waiting.clear()
            calls.forEach { it.completeExceptionally(EngineUnavailableException(message)) }
        }
    }

    companion object {
        /** Next to app.js in the site. */
        const val WORKER_URL = "engine-worker.js"
    }
}
