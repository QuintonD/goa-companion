package dev.quinton.goa.core.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

/**
 * Every sealed subtype and enum value that can cross the wire, by serial name. An old client can't decode a new
 * one (docs/ONLINE.md "Protocol versions"), so adding one must go through here: change [NAMES] and raise
 * `OnlineProtocol.VERSION` and [NAMES_AT_VERSION] with it, in the same commit.
 */
class WireNamesTest {
    @OptIn(ExperimentalSerializationApi::class)
    private fun walk(
        descriptor: SerialDescriptor,
        found: MutableMap<String, List<String>>,
        seen: MutableSet<String>,
    ) {
        // A list's name doesn't say of what, so only a class can be passed over the second time.
        val container = descriptor.kind == StructureKind.LIST || descriptor.kind == StructureKind.MAP
        if (!container && !seen.add(descriptor.serialName)) return
        when (descriptor.kind) {
            SerialKind.ENUM -> found[descriptor.serialName.removeSuffix("?")] =
                List(descriptor.elementsCount, descriptor::getElementName).sorted()

            PolymorphicKind.SEALED -> {
                val subtypes = descriptor.getElementDescriptor(1)
                found[descriptor.serialName.removeSuffix("?")] =
                    List(subtypes.elementsCount, subtypes::getElementName).sorted()
                for (i in 0 until subtypes.elementsCount) walk(subtypes.getElementDescriptor(i), found, seen)
            }

            else -> for (i in 0 until descriptor.elementsCount) walk(descriptor.getElementDescriptor(i), found, seen)
        }
    }

    private fun current(): Map<String, List<String>> {
        val found = HashMap<String, List<String>>()
        val seen = HashSet<String>()
        walk(ServerMessage.serializer().descriptor, found, seen)
        walk(ClientMessage.serializer().descriptor, found, seen)
        return found.entries.sortedBy { it.key }.associate { it.key to it.value }
    }

    @Test
    fun theNamesOnTheWireAreTheOnesThisVersionPromises() {
        val now = current()
        val literal = now.entries.joinToString("\n") { (type, names) ->
            "        \"$type\" to listOf(${names.joinToString(", ") { "\"$it\"" }}),"
        }
        assertEquals(
            NAMES.entries.sortedBy { it.key }.joinToString("\n"),
            now.entries.joinToString("\n"),
            "A name on the wire changed. If that is meant, raise OnlineProtocol.VERSION and NAMES_AT_VERSION, " +
                "and make NAMES:\n$literal\n",
        )
    }

    @Test
    fun theListIsThatOfTheCurrentVersion() {
        assertEquals(OnlineProtocol.VERSION, NAMES_AT_VERSION, "Raise NAMES_AT_VERSION with the list above it")
    }

    @Test
    fun aRealFrameIsCovered() {
        val names = current()
        assertEquals(true, names.keys.any { it.endsWith("GameEvent") })
        assertEquals(true, names.keys.any { it.endsWith("Choice") })
        assertEquals(true, names.keys.any { it.endsWith("DecisionKind") })
        assertEquals(true, names.keys.any { it.endsWith("Refusal") })
    }

    private companion object {
        const val NAMES_AT_VERSION = 1

        val NAMES: Map<String, List<String>> = mapOf(
            "dev.quinton.goa.core.game.ActionType" to listOf(
                "attack",
                "clear",
                "fast_travel",
                "hold",
                "movement",
                "primary",
            ),
            "dev.quinton.goa.core.game.AiLevel" to listOf("easy", "expert", "hard", "normal"),
            "dev.quinton.goa.core.game.Choice" to listOf(
                "action",
                "card",
                "decline",
                "number",
                "option",
                "pass",
                "space",
                "token",
                "unit",
                "yes",
            ),
            "dev.quinton.goa.core.game.Controller" to listOf("ai", "human"),
            "dev.quinton.goa.core.game.DecisionKind" to listOf(
                "act_first",
                "choose_action",
                "choose_card",
                "choose_number",
                "choose_option",
                "choose_space",
                "choose_token",
                "choose_unit",
                "confirm",
                "defend",
                "keep_upgrade",
                "minion_return",
                "play_card",
                "remove_minion",
                "respawn",
                "upgrade_card",
            ),
            "dev.quinton.goa.core.game.GameEvent" to listOf(
                "action",
                "attacked",
                "cards",
                "cards_revealed",
                "coins",
                "defeated",
                "defended",
                "game_over",
                "lane_pushed",
                "level_up",
                "life",
                "moved",
                "note",
                "placed",
                "pushed",
                "removed",
                "round_started",
                "swapped",
                "turn_started",
            ),
            "dev.quinton.goa.core.game.GameLength" to listOf("long", "quick"),
            "dev.quinton.goa.core.game.GamePhase" to listOf("end_of_round", "game_over", "play_cards", "resolve"),
            "dev.quinton.goa.core.game.WinReason" to listOf("card_text", "last_push", "life_counters", "throne"),
            "dev.quinton.goa.core.model.board.MinionType" to listOf("heavy", "melee", "ranged"),
            "dev.quinton.goa.core.model.catalog.Stat" to listOf(
                "attack",
                "defense",
                "initiative",
                "movement",
                "radius",
                "range",
            ),
            "dev.quinton.goa.core.model.match.Team" to listOf("blue", "red"),
            "dev.quinton.goa.core.online.ClientMessage" to listOf(
                "game.answer",
                "game.leave",
                "game.review",
                "hello",
                "ping",
                "room.ready",
                "room.seat",
                "room.start",
                "room.watch",
            ),
            "dev.quinton.goa.core.online.Presence" to listOf("away", "computer", "here"),
            "dev.quinton.goa.core.online.Refusal" to listOf(
                "bad_token",
                "draining",
                "no_such_room",
                "not_host",
                "not_ready",
                "rate_limited",
                "room_taken",
                "rooms_full",
                "too_many_games",
                "too_many_rooms",
                "update_app",
            ),
            "dev.quinton.goa.core.online.RoomStatus" to listOf("closed", "open", "started"),
            "dev.quinton.goa.core.online.ServerMessage" to listOf(
                "game.ack",
                "game.end",
                "game.reviewed",
                "game.start",
                "game.table",
                "pong",
                "refused",
                "room",
                "welcome",
            ),
            "dev.quinton.goa.core.online.VoidReason" to listOf("abandoned", "rules_changed"),
        )
    }
}
