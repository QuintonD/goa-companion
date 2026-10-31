package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Marker
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.game.puzzles.KnownPlays
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * A mined round start as data: what a book file's `setUp` builds, so the generator proves the very
 * position that ships. [of] returns null for what the builder can't express exactly ([whyNot]),
 * and [build] gives back the session; [same] checks nothing was lost.
 */
class RoundSpec private constructor(
    val round: Int,
    val tieBreaker: Team,
    val length: GameLength,
    val heroes: List<HeroSpec>,
    val minions: List<MinionSpec>,
    val battleZones: Map<String, String>,
    val waves: Map<String, Int>,
    val life: Map<Team, Int>,
    val pieces: List<PieceState>,
    val gameVars: Map<String, String>,
) {
    class MinionSpec(val id: String, val team: Team, val type: MinionType, val at: Hex, val lane: String)

    class HeroSpec(
        val id: String,
        val team: Team,
        val at: Hex?,
        val level: Int,
        val coins: Int,
        val hand: List<String>,
        val items: List<String>,
        val ultimate: Boolean,
        val counters: Map<String, Int>,
        val vars: Map<String, String>,
        /** Starting cards that aren't in the hand: back in the deck after an upgrade. */
        val deck: List<String>,
        /** Extra cards (Gydion's spells) by place, the unused `box` left out. */
        val extras: Map<String, List<String>>,
        val markers: List<Marker>,
    )

    /** Built ids of the minions, in [minions] order: `m1000`, `m1001`, … as the builder numbers them. */
    fun builtId(index: Int): String = "m${FIRST_ID + index}"

    fun build(): EngineSession = position(TestData.engine, "forgotten-island") {
        round = this@RoundSpec.round
        tieBreaker = this@RoundSpec.tieBreaker
        length = this@RoundSpec.length
        clearMinions()
        for ((lane, zone) in battleZones) battleZone(lane, zone)
        for ((lane, n) in waves) wavesLeft(lane, n)
        for ((team, n) in life) lifeCounters(team, n)
        for ((k, v) in gameVars) gameVar(k, v)
        for (p in pieces) piece(p.copy())
        for (h in heroes) {
            hero(h.id, h.team, h.at) {
                if (h.ultimate) ultimate()
                level = h.level
                coins = h.coins
                hand(*h.hand.toTypedArray())
                items(*h.items.toTypedArray())
                if (h.deck.isNotEmpty()) deck(*h.deck.toTypedArray())
                for ((tag, ids) in h.extras) extra(*ids.toTypedArray(), tag = tag)
                for (m in h.markers) marker(m.kind, m.from, m.persistent, m.data)
                for ((k, v) in h.counters) counter(k, v)
                if (h.vars.isNotEmpty()) vars(*h.vars.toList().toTypedArray())
            }
        }
        for (m in minions) minion(m.team, m.type, m.at, m.lane)
    }

    /** True when [built] is the same position as [mined] (ids apart). */
    fun same(mined: GameState, built: GameState): Boolean = picture(mined) == picture(built)

    /** Where the two pictures first differ, for the log. */
    fun difference(mined: GameState, built: GameState): String {
        val a = picture(mined)
        val b = picture(built)
        val i = a.zip(b).indexOfFirst { (x, y) -> x != y }.let { if (it < 0) minOf(a.length, b.length) else it }
        val from = maxOf(0, i - CONTEXT)
        return "mined …${a.substring(from, minOf(a.length, i + CONTEXT))}… built …${b.substring(from, minOf(b.length, i + CONTEXT))}…"
    }

    private fun picture(s: GameState): String = buildString {
        append(s.round, s.turn, s.step, s.stepSeat, s.tieBreaker, s.battleZones, s.wavesLeft, s.lifeCounters)
        append(s.effects.size, s.pushDue, s.lastWaveFlipped, s.revealed, s.vars)
        append(s.pieces.map { listOf(it.id, it.kind, it.at, it.owner, it.team, it.token, it.unit, it.heroBody, it.data) })
        for (h in s.heroes) {
            append(h.id, h.team, h.seat, h.at, h.level, h.coins, h.counters, h.vars, h.spareItems)
            append(h.markers.map { listOf(it.kind, it.from, it.persistent, it.data) })
            append(h.cards.filter { it.zone != Zone.DECK }.map { listOf(it.id, it.zone, it.slot, it.tag) }.sortedBy { it[0].toString() })
        }
        append(s.minions.map { "${it.team} ${it.type} ${it.at} ${it.lane}" }.sorted())
    }

    /** The enemy's known answers with minion ids as the built position numbers them; null if one can't be mapped. */
    fun mapChoices(choices: List<Choice>, heroIds: Set<String>): List<Choice>? = choices.map { c ->
        when (c) {
            is Choice.Unit -> if (c.unitId in heroIds || pieces.any { it.id == c.unitId }) {
                c
            } else {
                val i = minions.indexOfFirst { it.id == c.unitId }
                if (i < 0) return null
                Choice.Unit(builtId(i))
            }

            // A token made during the round has no id to hold on to; one on the board keeps its id.
            is Choice.Token -> if (pieces.any { it.id == c.tokenId }) c else return null

            else -> c
        }
    }

    /** The body of the book file's `setUp`. */
    fun kotlin(): String = buildString {
        appendLine("        round = $round")
        appendLine("        tieBreaker = Team.$tieBreaker")
        if (this@RoundSpec.length != GameLength.QUICK) appendLine("        length = GameLength.${this@RoundSpec.length}")
        appendLine("        clearMinions()")
        for ((lane, zone) in battleZones) appendLine("        battleZone(\"$lane\", \"$zone\")")
        for ((lane, n) in waves) appendLine("        wavesLeft(\"$lane\", $n)")
        for ((team, n) in life) appendLine("        lifeCounters(Team.$team, $n)")
        for ((k, v) in gameVars) appendLine("        gameVar(${quote(k)}, ${quote(v)})")
        for (p in pieces) appendLine(pieceKotlin(p))
        for (h in heroes) {
            val at = h.at?.let { "Hex(${it.q}, ${it.r})" } ?: "null"
            appendLine("        hero(\"${h.id}\", Team.${h.team}, $at) {")
            if (h.ultimate) appendLine("            ultimate()")
            appendLine("            level = ${h.level}")
            if (h.coins != 0) appendLine("            coins = ${h.coins}")
            appendLine("            hand(${h.hand.joinToString { "\"$it\"" }})")
            if (h.items.isNotEmpty()) appendLine("            items(${h.items.joinToString { "\"$it\"" }})")
            if (h.deck.isNotEmpty()) appendLine("            deck(${h.deck.joinToString { "\"$it\"" }})")
            for ((tag, ids) in h.extras) {
                appendLine("            extra(${ids.joinToString { "\"$it\"" }}, tag = \"$tag\")")
            }
            for (m in h.markers) {
                val data = m.data.entries.joinToString { "${quote(it.key)} to ${quote(it.value)}" }
                val from = m.from?.let(::quote) ?: "null"
                appendLine("            marker(${quote(m.kind)}, $from, ${m.persistent}, mapOf($data))")
            }
            for ((k, v) in h.counters) appendLine("            counter(\"$k\", $v)")
            if (h.vars.isNotEmpty()) {
                appendLine("            vars(${h.vars.entries.joinToString { "\"${it.key}\" to \"${it.value}\"" }})")
            }
            appendLine("        }")
        }
        for (m in minions) {
            val lane = if (m.lane == "lane-1") "" else ", \"${m.lane}\""
            appendLine("        minion(Team.${m.team}, MinionType.${m.type}, Hex(${m.at.q}, ${m.at.r})$lane)")
        }
    }

    companion object {
        private const val FIRST_ID = 1000
        private const val CONTEXT = 70

        /** Why the builder can't express [s], or null when it can. */
        fun whyNot(s: GameState): String? = when {
            s.mapId != "forgotten-island" -> "map ${s.mapId}"
            s.effects.isNotEmpty() -> "effects ${s.effects.map { it.cardId }}"
            s.pushDue.isNotEmpty() || s.lastWaveFlipped.isNotEmpty() -> "a push under way"
            s.heroes.any { it.spareItems.isNotEmpty() } -> "spare items"
            s.heroes.any { it.handicap } -> "a handicap"
            s.minions.any { it.at == null } -> "a minion off the board"
            s.heroes.any { h -> h.cards.any { it.zone !in PLAIN_ZONES } } -> "cards in play"
            else -> null
        }

        fun of(s: GameState): RoundSpec? {
            if (whyNot(s) != null) return null
            val heroes = s.heroes.map { h ->
                val start = StartingCards.of(TestData.engine.data.catalogHero(h.id)).map { it.id }.toSet()
                HeroSpec(
                    h.id, h.team, h.at, h.level, h.coins,
                    h.inZone(Zone.HAND).map { it.id }, h.inZone(Zone.ITEM).map { it.id },
                    h.ultimateUnlocked, LinkedHashMap(h.counters), LinkedHashMap(h.vars),
                    h.inZone(Zone.DECK).map { it.id }.filter { it in start },
                    h.inZone(Zone.EXTRA).filter { it.tag != "box" }.groupBy({ it.tag.orEmpty() }, { it.id }),
                    h.markers.map { it.copy() },
                )
            }
            val minions = s.minions.map { m -> MinionSpec(m.id, m.team, m.type, m.at!!, m.lane) }
            return RoundSpec(
                s.round, s.tieBreaker, s.length, heroes, minions,
                LinkedHashMap(s.battleZones), LinkedHashMap(s.wavesLeft), LinkedHashMap(s.lifeCounters),
                s.pieces.map { it.copy() }, LinkedHashMap(s.vars),
            )
        }

        private val PLAIN_ZONES = setOf(Zone.HAND, Zone.ITEM, Zone.DECK, Zone.ULTIMATE, Zone.BOX, Zone.EXTRA)

        private fun pieceKotlin(p: PieceState): String {
            val at = p.at?.let { "Hex(${it.q}, ${it.r})" } ?: "null"
            val owner = p.owner?.let(::quote) ?: "null"
            val team = p.team?.let { "Team.$it" } ?: "null"
            val body = p.heroBody?.let(::quote) ?: "null"
            val data = p.data.entries.joinToString { "${quote(it.key)} to ${quote(it.value)}" }
            return "        piece(PieceState(${quote(p.id)}, ${quote(p.kind)}, ${quote(p.label)}, $at, $owner, $team, " +
                "${p.token}, ${p.unit}, ${p.removeAtEndOfRound}, ${p.passable}, ${p.faceDown}, $body, " +
                "linkedMapOf($data)))"
        }

        /** A line of Kotlin for the book file's `known`. */
        fun knownKotlin(k: KnownPlays): String = buildString {
            appendLine("    override val known = KnownPlays(")
            appendLine("        cards = mapOf(")
            for ((h, cs) in k.cards) appendLine("            \"$h\" to listOf(${cs.joinToString { "\"$it\"" }}),")
            appendLine("        ),")
            appendLine("        actions = mapOf(")
            for ((h, turns) in k.actions) {
                appendLine("            \"$h\" to mapOf(")
                for ((t, cs) in turns) appendLine("                $t to listOf(${cs.joinToString { choice(it) }}),")
                appendLine("            ),")
            }
            appendLine("        ),")
            appendLine("    )")
        }

        private fun choice(c: Choice): String = when (c) {
            is Choice.Action -> "Choice.Action(ActionType.${c.action.name})"
            is Choice.Space -> "Choice.Space(Hex(${c.hex.q}, ${c.hex.r}))"
            is Choice.Unit -> "Choice.Unit(\"${c.unitId}\")"
            is Choice.Card -> "Choice.Card(\"${c.cardId}\")"
            is Choice.Option -> "Choice.Option(${c.index}, ${quote(c.label)})"
            is Choice.Number -> "Choice.Number(${c.value})"
            is Choice.Decline -> "Choice.Decline(${quote(c.label)})"
            Choice.Yes -> "Choice.Yes"
            Choice.Pass -> "Choice.Pass"
            else -> error("Can't write $c")
        }

        private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
    }
}
