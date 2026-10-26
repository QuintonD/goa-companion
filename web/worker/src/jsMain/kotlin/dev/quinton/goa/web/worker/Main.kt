package dev.quinton.goa.web.worker

import dev.quinton.goa.web.engine.BundledData
import dev.quinton.goa.web.engine.EngineHost
import dev.quinton.goa.web.engine.EngineJson
import dev.quinton.goa.web.engine.EngineMessage
import dev.quinton.goa.web.engine.EngineReply
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.launch

/** The worker's global scope (`self` in a dedicated worker). */
private external val self: dynamic

/**
 * The engine worker: reads the bundled catalog and maps (paths relative to this script, served and
 * cached with the site) on the first request, then answers the page's [EngineMessage]s one at a
 * time with [EngineReply]s (JSON text both ways).
 */
fun main() {
    val host = EngineHost { BundledData.load(::fetchText) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    self.onmessage = { event: dynamic ->
        val message = EngineJson.decodeFromString(EngineMessage.serializer(), event.data as String)
        scope.launch {
            val reply = EngineReply(message.id, host.handle(message.request))
            self.postMessage(EngineJson.encodeToString(EngineReply.serializer(), reply))
        }
    }
}

private suspend fun fetchText(path: String): String {
    val response = (self.fetch(path) as Promise<dynamic>).await()
    if (!(response.ok as Boolean)) error("Couldn't load $path (${response.status})")
    return (response.text() as Promise<String>).await()
}
