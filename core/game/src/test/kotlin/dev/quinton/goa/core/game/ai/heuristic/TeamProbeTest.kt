package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.SavedPayload
import dev.quinton.goa.core.game.engine.Zone
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Opt-in probe (strategy round, Track T): `AI_T_PROBE=<game.json.gz>@R2T4:snorri:PLAY_CARD[#n]` rebuilds that
 * question of a recorded game and asks `AI_T_SPECS` (default `h9`, `;`-separated) with the trace on: each card's
 * looked value, the board, every hand.
 */
class TeamProbeTest {
    @Test
    fun probe() {
        val spec = System.getenv("AI_T_PROBE") ?: return
        val specs = (System.getenv("AI_T_SPECS") ?: "h9").split(";")
        for (one in spec.split(",")) {
            val file = one.substringBefore("@")
            val (rt, seat, kindN) = one.substringAfter("@").split(":")
            val round = rt.substringAfter("R").substringBefore("T").toInt()
            val turn = rt.substringAfter("T").toInt()
            val kind = DecisionKind.valueOf(kindN.substringBefore("#"))
            val nth = kindN.substringAfter("#", "0").toInt()
            val s = at(file, round, turn, seat, kind, nth)
            println("PROBE $one")
            println(board(s))
            for (sp in specs) {
                val parts = sp.split("+")
                val tune = parts.drop(1).associate { kv ->
                    kv.substringBefore("=") to kv.substringAfter("=").toDouble()
                }
                val ai = HeuristicAi(parts[0].drop(1).toInt(), 7L, tune = tune)
                ai.trace = { println("  $sp > $it") }
                val d = s.pending!!
                println("  $sp answer ${ai.choose(s, d)}")
            }
        }
    }

    companion object {
        fun load(file: String): SavedGame {
            val text = GZIPInputStream(File(file).inputStream()).bufferedReader().use { it.readText() }
            return Json.decodeFromString(SavedGame.serializer(), text)
        }

        /** The recorded game [file] just before the [nth] question of [kind] asked of [seat] in [round], [turn]. */
        fun at(file: String, round: Int, turn: Int, seat: String, kind: DecisionKind, nth: Int = 0): EngineSession {
            val saved = load(file)
            val payload = EngineSession.JSON.decodeFromString(SavedPayload.serializer(), saved.payload)
            val start = SavedGame(
                saved.engineVersion,
                saved.setup,
                EngineSession.JSON.encodeToString(
                    SavedPayload.serializer(),
                    SavedPayload(payload.initial, emptyList()),
                ),
            )
            val s = TestData.engine.restore(start) as EngineSession
            var seen = 0
            for (a in payload.answers) {
                val d = s.pending ?: break
                val st = s.state
                if (st.round == round && st.turn == turn && d.seat == seat && d.kind == kind) {
                    if (seen == nth) return s
                    seen++
                }
                s.answer(a.decisionId, a.choice)
            }
            error("$file: no $kind of $seat in round $round turn $turn (#$nth)")
        }

        fun board(s: EngineSession): String {
            val st = s.state
            val sb = StringBuilder()
            sb.append("  R${st.round}T${st.turn} coin ${st.tieBreaker} life ${st.lifeCounters}")
            sb.append(" zones ${st.battleZones}\n")
            for (h in st.heroes) {
                val hand = h.cards.filter { it.zone == Zone.HAND }.joinToString(",") { it.id.substringAfter("-") }
                val played = h.cards.filter { it.zone == Zone.PLAYED }.joinToString(",") { it.id.substringAfter("-") }
                sb.append("  ${h.id}(${h.team.name[0]}) L${h.level} at ${h.at?.let { "${it.q},${it.r}" }} ")
                sb.append("hand [$hand] played [$played] coins ${h.coins}\n")
            }
            val minions = st.minions.filter { it.at != null }.joinToString(" ") { m ->
                "${m.team.name[0]}${m.type.name[0]}${m.at!!.q},${m.at!!.r}"
            }
            sb.append("  minions $minions")
            return sb.toString()
        }
    }
}
