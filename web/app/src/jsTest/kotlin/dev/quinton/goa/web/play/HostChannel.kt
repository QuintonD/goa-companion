package dev.quinton.goa.web.play

import dev.quinton.goa.web.engine.BundledData
import dev.quinton.goa.web.engine.EngineHost
import dev.quinton.goa.web.engine.EngineJson
import dev.quinton.goa.web.engine.EngineMessage
import dev.quinton.goa.web.engine.EngineReply
import dev.quinton.goa.web.play.data.EngineChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The site serves the Android app's assets (`app/src/main/assets`) under the same paths. */
fun shipped(path: String): String {
    val root = js("process.env.GOA_REPO_ROOT") as String? ?: error("GOA_REPO_ROOT is not set")
    val fs = js("process.getBuiltinModule('fs')") // the tests run as ES modules (es2015 target): no require
    return fs.readFileSync("$root/app/src/main/assets/$path", "utf8") as String
}

/**
 * An engine "worker" for tests: the real [EngineHost] answering the same JSON messages in [scope]
 * (no Web Worker on Node). [opened] counts the workers started; [terminated] the ones stopped.
 */
class HostChannels(private val scope: CoroutineScope) {
    var opened = 0
    var terminated = 0

    fun open(): EngineChannel {
        opened++
        val host = EngineHost { BundledData.load { shipped(it) } }
        return object : EngineChannel {
            var alive = true
            override var onMessage: (String) -> Unit = {}
            override var onFailure: (String) -> Unit = {}

            override fun post(message: String) {
                scope.launch {
                    val m = EngineJson.decodeFromString(EngineMessage.serializer(), message)
                    val reply = EngineReply(m.id, host.handle(m.request))
                    if (alive) onMessage(EngineJson.encodeToString(EngineReply.serializer(), reply))
                }
            }

            override fun terminate() {
                alive = false
                terminated++
            }
        }
    }
}
