package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.GameHost
import dev.quinton.goa.core.online.NetworkInfo
import dev.quinton.goa.core.online.OnlineProtocol

/** What `network.json` says about connecting (docs/ONLINE.md): where to, or why not. */
data class HostChoice(val base: String?, val note: String? = null, val tooOld: Boolean = false)

/** Host status meaning "don't connect": a person's choice, and anything else counts as open. */
const val HOST_STATUS_CLOSED: String = "closed"

/**
 * Picks from `network.json` ([info] null when it can't be read): its first open host, else the built-in address
 * unless the file closes that one too. `minProtocol` above this build's means it is too old to play here at all.
 */
fun chooseHost(info: NetworkInfo?, builtIn: String?): HostChoice {
    val listed = info?.games.orEmpty()
    val closed = listed.filter { it.status == HOST_STATUS_CLOSED }
    val closedOrigins = closed.map { it.origin.trimEnd('/') }
    val open = listed.filter { it.status != HOST_STATUS_CLOSED }.map(GameHost::origin)
    val base = (open + listOfNotNull(builtIn)).map { it.trimEnd('/') }.firstOrNull { it !in closedOrigins }
    return HostChoice(
        base = base,
        note = closed.firstNotNullOfOrNull(GameHost::note).takeIf { base == null },
        tooOld = info != null && info.minProtocol > OnlineProtocol.VERSION,
    )
}

/**
 * Thrown by a [GameConnector] that `network.json` tells not to connect, so that no socket is opened:
 * [tooOld] is the same as being refused with `UPDATE_APP`, and otherwise the host is closed, with [note] if
 * the file gives one, and the client looks again later.
 */
class HostUnavailable(val tooOld: Boolean, val note: String? = null) : Exception()

/** A connector that asks [choice] first: platforms pass their own socket as [open], given the host's base address. */
fun hostAwareConnector(
    choice: suspend () -> HostChoice,
    open: suspend (base: String, hello: ClientMessage.Hello) -> GameConnection,
): GameConnector = GameConnector { hello ->
    val host = choice()
    if (host.tooOld) throw HostUnavailable(tooOld = true)
    open(host.base ?: throw HostUnavailable(tooOld = false, note = host.note), hello)
}
