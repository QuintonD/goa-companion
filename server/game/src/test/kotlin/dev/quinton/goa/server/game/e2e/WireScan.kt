package dev.quinton.goa.server.game.e2e

import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.online.ServerMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * What one person's socket carried, against what the server's session held at that moment (a replay of its
 * log). Every line returned is a leak. It reads the raw JSON as well as the decoded frame, because a field the
 * client doesn't decode would still be on the wire.
 */
internal class WireScan(private val truth: GameTruth) {
    private val byEvents: Map<Int, List<Position>> = truth.positions.groupBy { it.eventCount }

    /** Cards only [seat]'s owner may know at [position]: the hand, the tucked items, the card played facedown. */
    private fun hiddenCards(position: Position, seat: String, viewer: String): Set<String> {
        val own: HeroView = position.views.getValue(seat).heroes.first { it.heroId == seat }
        val public: HeroView = position.views.getValue(viewer).heroes.first { it.heroId == seat }
        val played = (listOfNotNull(own.played) + own.alsoPlayed).filter { card ->
            (listOfNotNull(public.played) + public.alsoPlayed).none { it.cardId == card.cardId }
        }
        val wasPublic = (public.discard + public.resolved).mapNotNull { it.cardId } + public.activeEffects
        return (own.hand.orEmpty() + own.items + played.mapNotNull { it.cardId }).toSet() - wasPublic.toSet()
    }

    fun scan(frames: List<WireFrame>, viewerSeat: String): List<String> {
        val leaks = ArrayList<String>()
        val tickets = ArrayList<Long>()
        for ((i, frame) in frames.withIndex()) {
            val message = frame.message
            val where = "frame $i of $viewerSeat"
            val json = Json.parseToJsonElement(frame.raw)
            BANNED_KEYS.forEach { key ->
                if (json.hasKey(key)) leaks += "$where has a key \"$key\": ${frame.raw.take(SHOWN)}"
            }
            if (message is ServerMessage.Table && message.gameId == truth.gameId) {
                leaks += scanTable(where, message, json.jsonObject, viewerSeat)
                message.decision?.takeIf { it.seat == viewerSeat && it.id != 0L }?.let { tickets += it.id }
            }
        }
        leaks += scanTickets(viewerSeat, tickets)
        return leaks
    }

    private fun scanTable(where: String, table: ServerMessage.Table, json: JsonObject, viewer: String): List<String> {
        val leaks = ArrayList<String>()
        val end = table.eventsFrom + table.events.size
        val candidates = byEvents[end]
        if (candidates == null) {
            leaks += "$where ends at event $end, a moment that never existed in the log"
            return leaks
        }
        if (table.view.viewer != viewer) leaks += "$where is the table of ${table.view.viewer}"
        val decision = table.decision
        if (decision != null && decision.seat != viewer) {
            if (decision.options.isNotEmpty() || decision.id != 0L || decision.prompt.isNotEmpty() ||
                decision.sourceCardId != null
            ) {
                leaks += "$where has ${decision.seat}'s question: $decision"
            }
        }
        table.view.heroes.filter { it.heroId != viewer }.forEach { hero ->
            if (hero.hand != null) leaks += "$where shows the hand of ${hero.heroId}"
            if (hero.items.isNotEmpty()) leaks += "$where shows the tucked items of ${hero.heroId}"
        }
        if (candidates.none { sameExceptOwn(table.view, it.views.getValue(viewer), viewer) }) {
            leaks += "$where shows others differently from any moment of the log at event $end"
        }
        val others = truth.seats.filter { it != viewer }
        val hidden = others.flatMap { seat ->
            candidates.map { hiddenCards(it, seat, viewer) }.reduce { a, b -> a intersect b }
        }.toSet()
        val noEvents = JsonObject(json.filterKeys { it != "events" }).toString()
        hidden.filter { "\"$it\"" in noEvents }.forEach { leaks += "$where holds the hidden card $it: $noEvents" }
        leaks += leaksInEvents(where, table, candidates, others)
        return leaks
    }

    /** An event may name a card once it is open; one played facedown and still unrevealed is not. */
    private fun leaksInEvents(
        where: String,
        table: ServerMessage.Table,
        candidates: List<Position>,
        others: List<String>,
    ): List<String> {
        val facedown = others.flatMap { seat ->
            candidates.flatMap { position ->
                val hero = position.views.getValue(seat).heroes.first { it.heroId == seat }
                val open = position.spectator.heroes.first { it.heroId == seat }
                (listOfNotNull(hero.played) + hero.alsoPlayed).mapNotNull { it.cardId }.filter { id ->
                    open.played?.cardId != id && open.alsoPlayed.none { it.cardId == id }
                }
            }
        }.toSet()
        return table.events.flatMap { event ->
            facedown.filter { it in event.toString() }.map { "$where: an event names the facedown card $it: $event" }
        }
    }

    /** Tickets count only the seat's own questions, one step at a time: gaps would show questions skipped. */
    private fun scanTickets(seat: String, tickets: List<Long>): List<String> {
        val index = truth.seats.indexOf(seat).toLong()
        val leaks = tickets.filter { it % TICKET_STEP != index }.map { "$seat got the ticket $it, not of its seat" }
        val steps = tickets.distinct().sorted().zipWithNext { a, b -> b - a }.filter { it != TICKET_STEP }
        return leaks + steps.map { "$seat's tickets jump by $it: a question it wasn't asked is counted" }
    }

    private fun sameExceptOwn(frame: GameView, truth: GameView, viewer: String): Boolean =
        frame.copy(heroes = frame.heroes.filter { it.heroId != viewer }) ==
            truth.copy(heroes = truth.heroes.filter { it.heroId != viewer })

    private fun JsonElement.hasKey(key: String): Boolean = when (this) {
        is JsonObject -> key in this || values.any { it.hasKey(key) }
        is JsonArray -> any { it.hasKey(key) }
        is JsonPrimitive -> false
    }

    private companion object {
        val BANNED_KEYS = listOf("payload", "initial", "answers", "saved", "state", "start", "decisionId", "humans")
        const val TICKET_STEP = 16L
        const val SHOWN = 300
    }
}

/** A frame with something added that a client wouldn't decode: the scan works on the raw text. */
internal fun WireFrame.withRawKey(key: String): WireFrame =
    WireFrame(connection, raw.replaceFirst("{", "{\"$key\":1,"), message)

/** A person's events as a client rebuilds them: from where each table says it starts, skipping what it holds. */
internal fun rebuiltLog(tables: List<ServerMessage.Table>): List<GameEvent> {
    val log = ArrayList<GameEvent>()
    for (table in tables) {
        check(table.eventsFrom <= log.size) { "A table starts at ${table.eventsFrom}, past the ${log.size} held" }
        log += table.events.drop(log.size - table.eventsFrom)
    }
    return log
}
