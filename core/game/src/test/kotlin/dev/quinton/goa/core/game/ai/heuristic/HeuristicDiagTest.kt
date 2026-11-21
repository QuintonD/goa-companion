package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in diagnostics: `AI_H_DIAG=h10:s8` plays `AI_H_GAMES` mirrored line-ups (from `AI_H_FIRST`) and
 * prints, for every defeat of side a's heroes and for every game a loses, the turn it happened on
 * and the turn before: who held how many cards, the cards revealed, attacks, defenses and pushes.
 * Read it to see how the heuristic player loses, not how often (that is [HeuristicTournamentTest]).
 */
class HeuristicDiagTest {
    @Test
    fun `how side a loses`() {
        val spec = System.getenv("AI_H_DIAG") ?: return
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 6
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 0
        val (aName, bName) = spec.split(":")
        val all = AiHarness.mixedLineups(first + lineups)
        for (i in first until first + lineups) {
            val (red, blue) = all[i]
            for (aRed in listOf(true, false)) {
                val coin = if (i % 2 == 0) Team.RED else Team.BLUE
                val seed = (50_000L + i) * 2
                val aTeam = if (aRed) Team.RED else Team.BLUE
                val lines = ArrayList<Pair<Int, String>>()
                var seenEvents = 0
                var lastKey = -1
                val r = AiHarness.play(
                    AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin),
                    mapOf(
                        Team.RED to player(if (aRed) aName else bName, seed),
                        Team.BLUE to player(if (aRed) bName else aName, seed + 1),
                    ),
                    observe = { s, d ->
                        val st = s.state
                        val key = st.round * 8 + st.turn
                        seenEvents = drain(s, seenEvents, key, lines)
                        if (key != lastKey && d.kind == DecisionKind.PLAY_CARD) {
                            lastKey = key
                            lines += key to status(s)
                        }
                        if (d.team == aTeam && d.options.size > 1 &&
                            d.kind in setOf(DecisionKind.DEFEND, DecisionKind.PLAY_CARD)
                        ) {
                            lines += key to "    ? ${d.seat} ${d.kind} options ${d.options.size}"
                        }
                    },
                )
                drain(r.session, seenEvents, Int.MAX_VALUE, lines)
                val aWon = r.winner == aTeam
                println("DIAG game ${red + blue} a=$aName ${if (aRed) "Red" else "Blue"} won=$aWon rounds=${r.rounds}")
                val heroesA = (if (aRed) red else blue).toSet()
                val keys = lines.filter { (_, l) ->
                    l.startsWith("  X ") && heroesA.any { h -> l.startsWith("  X $h ") }
                }.map { it.first }.toSortedSet()
                if (!aWon) lines.lastOrNull()?.let { keys += it.first }
                val show = keys.flatMap { listOf(it - 1, it) }.toSet()
                // AI_H_DIAG_ALL: every line of the games side a lost.
                val all = System.getenv("AI_H_DIAG_ALL") != null && !aWon
                for ((k, l) in lines) if (all || k in show || (k == Int.MAX_VALUE && !aWon)) println("DIAG $l")
            }
        }
    }

    private fun drain(s: EngineSession, from: Int, key: Int, lines: MutableList<Pair<Int, String>>): Int {
        val ev = s.events
        for (e in ev.drop(from)) {
            val line = when (e) {
                is GameEvent.CardsRevealed -> "  R " + e.played.entries.joinToString("; ") { (h, c) ->
                    "$h ${c ?: "pass"} ${e.initiatives[h] ?: ""}"
                }

                is GameEvent.ActionChosen -> "  A ${e.heroId} ${e.cardId} ${e.action}"
                is GameEvent.Attacked -> "  ! ${e.attackerId} -> ${e.targetId} total ${e.total}"
                is GameEvent.Defended -> "  D ${e.heroId} ${e.cardId} ${e.total} ok=${e.success}"
                is GameEvent.Defeated -> if (s.state.hero(e.unitId) != null) "  X ${e.unitId} by ${e.byHeroId}" else null
                is GameEvent.LanePushed -> "  P ${e.lane} by ${e.winner} -> ${e.newBattleZone}"
                is GameEvent.GameOver -> "  G ${e.winner} ${e.reason}"
                is GameEvent.CardsChanged -> if (e.discarded.isNotEmpty()) "  C ${e.text}" else null
                is GameEvent.Note -> when {
                    e.text.endsWith(" has no card to defend with.") -> "  E ${e.text}"
                    "battle" in e.text.lowercase() -> "  N ${e.text}"
                    else -> null
                }
                else -> null
            }
            if (line != null) lines += key to line
        }
        return ev.size
    }

    private fun status(s: EngineSession): String {
        val st = s.state
        val heroes = st.heroes.joinToString(" | ") { h ->
            val hand = h.cards.count { it.zone == Zone.HAND }
            "${h.id}(${h.team.name[0]}) L${h.level} hand $hand at ${h.at?.let { "${it.q},${it.r}" } ?: "off"}"
        }
        val lanes = st.battleZones.entries.joinToString { (lane, bz) ->
            val red = st.minions.count { it.team == Team.RED && it.lane == lane && it.at != null }
            val blue = st.minions.count { it.team == Team.BLUE && it.lane == lane && it.at != null }
            "$bz R$red B$blue waves ${st.wavesLeft[lane]}"
        }
        return "R${st.round}T${st.turn} life R${st.lifeCounters[Team.RED]} B${st.lifeCounters[Team.BLUE]} " +
            "coin ${st.tieBreaker} $lanes :: $heroes"
    }

    private fun player(spec: String, seed: Long): AiPlayer {
        val parts = spec.split("+")
        val n = parts[0].drop(1).toInt()
        val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }
        return if (spec[0] == 'h') HeuristicAi(n, seed, tune = tune) else AiPlayers.searchDeterministic(n, seed)
    }
}
