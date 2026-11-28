package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in team-play audit (strategy round, Track T): `AI_TEAM_AUDIT=h9;h9+team=0` plays one self-play game per
 * line-up (`AI_H_GAMES` from `AI_H_FIRST`, default 8 from 2000; `AI_DIGEST_FORMATS` default `2v2,3v3`) and counts,
 * per team and turn, what working together looks like in the event log:
 * - `focus`: a turn in which two heroes of a team attacked (or made discard) the same enemy hero;
 * - `split`: a turn in which two heroes of a team hit two different enemy heroes and none was defeated;
 * - `lone`: a hero hit that did not defeat, with no teammate hitting the same hero that turn;
 * - `stripFollow` / `stripNone`: an enemy left with at most one card after a discard on turns 1-3, and on turn 4
 *   a teammate's hit on it (or none);
 * - `kills`, `focusKills` (two or more attackers on it that turn), `minions` taken.
 * `AI_TEAM_AUDIT_LOG=1` prints the flagged turns, `AI_H_DIAG_SAVE=<dir>` keeps each game as a save.
 */
class TeamAuditTest {
    @Test
    fun `team play audit`() {
        val specs = System.getenv("AI_TEAM_AUDIT")?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        val n = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 8
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 2000
        val formats = (System.getenv("AI_DIGEST_FORMATS") ?: "2v2,3v3").split(",").map { AiHarness.Format.parse(it) }
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        for (spec in specs) {
            val parts = spec.split("+")
            val strength = parts[0].drop(1).toInt()
            val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }
            for (format in formats) {
                println("TEAM spec $spec ${format.label} tune $tune")
                val all = AiHarness.mixedLineups(first + n, size = format.size)
                val jobs = (first until first + n).map { i -> Callable { game(format, all[i], i, strength, tune) } }
                val pool = Executors.newFixedThreadPool(threads)
                val total = Counts()
                try {
                    for (f in pool.invokeAll(jobs)) {
                        val (c, lines) = f.get()
                        lines.forEach { println(it) }
                        total.add(c)
                    }
                } finally {
                    pool.shutdown()
                }
                println("TEAM TOTAL $spec ${format.label} $total")
            }
        }
    }

    class Counts {
        val m = sortedMapOf<String, Int>()

        fun inc(k: String, by: Int = 1) {
            m[k] = (m[k] ?: 0) + by
        }

        fun add(o: Counts) = o.m.forEach { (k, v) -> inc(k, v) }

        override fun toString(): String = m.entries.joinToString(" ") { "${it.key}=${it.value}" }
    }

    private fun game(
        format: AiHarness.Format,
        lineup: Pair<List<String>, List<String>>,
        i: Int,
        strength: Int,
        tune: Map<String, Double>,
    ): Pair<Counts, List<String>> {
        val (red, blue) = lineup
        val coin = if (i % 2 == 0) Team.RED else Team.BLUE
        val seed = (50_000L + i) * 2
        // Cards in hand of each hero on board when turn 4 starts, per round.
        val handAt4 = HashMap<Int, Map<String, Int>>()
        val t4 = HashMap<Pair<Int, String>, Pair<Set<String>, Boolean>>()
        val posAt4 = HashMap<Int, Map<String, dev.quinton.goa.core.model.board.Hex>>()
        val r = AiHarness.play(
            AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin, format.length, format.mapId),
            mapOf(
                Team.RED to HeuristicAi(strength, seed, tune = tune),
                Team.BLUE to HeuristicAi(strength, seed + 1, tune = tune),
            ),
            observe = { ss, d ->
                val st = ss.state
                if (st.turn == 4 && d.kind == dev.quinton.goa.core.game.DecisionKind.PLAY_CARD) {
                    t4[st.round to d.seat] = finishers(ss, d)
                }
                if (st.turn == 4 && !handAt4.containsKey(st.round)) {
                    posAt4[st.round] = st.heroes.filter { it.at != null }.associate { it.id to it.at!! }
                    handAt4[st.round] = st.heroes.filter { it.at != null }.associate { h ->
                        h.id to h.cards.count { it.zone == Zone.HAND || it.zone == Zone.PLAYED }
                    }
                }
            },
        )
        System.getenv("AI_H_DIAG_SAVE")?.let { dir ->
            val f = java.io.File(dir, "team-${format.size}v-$i.json.gz")
            java.util.zip.GZIPOutputStream(f.outputStream()).bufferedWriter().use {
                it.write(
                    kotlinx.serialization.json.Json.encodeToString(
                        dev.quinton.goa.core.game.SavedGame.serializer(),
                        r.session.save(),
                    ),
                )
            }
        }
        val out = ArrayList<String>()
        val c = audit(r.session, "${format.label} $i", out, handAt4, posAt4)
        // Turn 4 with an enemy that will be empty-handed in reach: did the hero hold and play a finisher?
        var round = 0
        var turn = 0
        for (e in r.session.events) {
            if (e is GameEvent.RoundStarted) round = e.round
            if (e is GameEvent.TurnStarted) turn = e.turn
            if (e !is GameEvent.CardsRevealed || turn != 4) continue
            for ((hero, card) in e.played) {
                val (reaching, anyAttack) = t4.remove(round to hero) ?: continue
                when {
                    card != null && card in reaching -> c.inc("t4hit")

                    reaching.isNotEmpty() -> {
                        c.inc("t4missed")
                        out += "TEAM ${format.label} $i R${round}T4 MISSED $hero played $card could $reaching"
                    }

                    anyAttack -> c.inc("t4far")

                    else -> c.inc("t4noAttack")
                }
            }
        }
        out += "TEAM game ${format.label} $i winner ${r.winner} rounds ${r.rounds} $c ${red + blue}"
        return c to out
    }

    /**
     * At [d] (a turn-4 card play), the cards of the hero's hand whose attack reaches an enemy that is empty-handed
     * this turn (at most one card in hand or played), and whether an enemy is empty at all and the hero holds any
     * attack. Null when no enemy is empty-handed.
     */
    private fun finishers(s: EngineSession, d: dev.quinton.goa.core.game.Decision): Pair<Set<String>, Boolean> {
        val ctx = HContext(s, d, d.team, HeuristicProfile.of(9))
        val me = ctx.own() ?: return emptySet<String>() to false
        val from = me.at ?: return emptySet<String>() to false
        val empty = s.state.heroes.filter { e ->
            e.team != d.team && e.at != null &&
                e.cards.count {
                    it.zone == Zone.HAND ||
                        it.zone == Zone.PLAYED
                } <= 1
        }
        if (empty.isEmpty()) return emptySet<String>() to false
        val attacks = me.hand.map { ctx.card(it.id) }.filter { it.attackCard || it.discardOrDefeat }
        val reaching = attacks.filter { c ->
            empty.any { e -> ctx.distance(from, e.at!!) <= ctx.attackReach(me.id, c).coerceAtLeast(1) }
        }.map { it.id }.toSet()
        return reaching to attacks.isNotEmpty()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun audit(
        s: EngineSession,
        label: String,
        out: MutableList<String>,
        handAt4: Map<Int, Map<String, Int>>,
        posAt4: Map<Int, Map<String, dev.quinton.goa.core.model.board.Hex>>,
    ): Counts {
        val c = Counts()
        val teamOf = s.setup.seats.associate { it.heroId to it.team }
        val log = System.getenv("AI_TEAM_AUDIT_LOG") != null
        var round = 0
        var turn = 0
        // Per turn: attacker -> targets hit (attack or forced discard), defeats, played cards.
        var hits = LinkedHashMap<String, MutableSet<String>>()
        var defeated = HashSet<String>()
        var played = mapOf<String, String?>()
        var actions = ArrayList<String>()
        // Cards in hand per hero, from the state is not available per event; count discards per round instead.
        val discardsThisRound = HashMap<String, Int>()
        val strippedBefore4 = HashSet<String>()

        fun flush() {
            if (turn == 0) return
            for (team in Team.entries) {
                val mine = hits.filterKeys { teamOf[it] == team }
                val targets = mine.values.flatten().filter { teamOf[it] != null && teamOf[it] != team }
                val byTarget = targets.groupingBy { it }.eachCount()
                if (byTarget.values.any { it >= 2 }) c.inc("focus")
                val distinct = byTarget.keys
                if (mine.size >= 2 && distinct.size >= 2 && distinct.none { it in defeated }) {
                    c.inc("split")
                    if (log) out += "TEAM $label R${round}T$turn SPLIT $team ${mine.entries} played $played | $actions"
                }
                for ((target, k) in byTarget) {
                    if (k == 1 && target !in defeated) c.inc("lone")
                }
                if (turn == 4) {
                    for (e in strippedBefore4) {
                        if (teamOf[e] == team) continue
                        val cards = handAt4[round]?.get(e) ?: continue
                        if (cards > 1) continue
                        if (byTarget.containsKey(e)) {
                            c.inc("stripFollow")
                        } else {
                            val pos = posAt4[round].orEmpty()
                            val near = pos[e]?.let { at ->
                                pos.filterKeys { teamOf[it] == team }.values.minOfOrNull {
                                    dev.quinton.goa.core.domain.board.HexGrid.distance(it, at)
                                }
                            }
                            c.inc("stripNone")
                            if (near != null && near <= 3) c.inc("stripNoneNear3")
                            if (log) {
                                out +=
                                    "TEAM $label R${round}T4 STRIPNONE $team on $e near $near played $played | $actions"
                            }
                        }
                    }
                }
            }
            hits = LinkedHashMap()
            defeated = HashSet()
            actions = ArrayList()
        }
        for (e in s.events) {
            when (e) {
                is GameEvent.RoundStarted -> {
                    flush()
                    round = e.round
                    turn = 0
                    discardsThisRound.clear()
                    strippedBefore4.clear()
                }

                is GameEvent.TurnStarted -> {
                    flush()
                    turn = e.turn
                }

                is GameEvent.CardsRevealed -> played = e.played

                is GameEvent.ActionChosen -> actions += "${e.heroId}:${e.action}"

                is GameEvent.Attacked -> if (teamOf.containsKey(e.targetId)) {
                    hits.getOrPut(e.attackerId) { LinkedHashSet() } += e.targetId
                }

                is GameEvent.Defended -> {
                    discardsThisRound[e.heroId] = (discardsThisRound[e.heroId] ?: 0) + 1
                    if (turn in 1..3 && (discardsThisRound[e.heroId] ?: 0) >= 1) strippedBefore4 += e.heroId
                }

                is GameEvent.CardsChanged -> if (e.discarded.isNotEmpty() && teamOf.containsKey(e.heroId)) {
                    discardsThisRound[e.heroId] = (discardsThisRound[e.heroId] ?: 0) + e.discarded.size
                    if (turn in 1..3) strippedBefore4 += e.heroId
                }

                is GameEvent.Defeated -> {
                    val by = e.byHeroId
                    if (teamOf.containsKey(e.unitId)) {
                        if (turn == 4 && e.unitId in strippedBefore4 && (handAt4[round]?.get(e.unitId) ?: 9) <= 1) {
                            c.inc("stripFollow")
                        }
                        defeated += e.unitId
                        strippedBefore4 -= e.unitId
                        c.inc("kills")
                        val attackers = hits.filter { e.unitId in it.value }.keys.size
                        if (attackers >= 2) c.inc("focusKills")
                    } else if (by != null && e.unitId.startsWith("m")) {
                        c.inc("minions")
                    }
                }

                else -> Unit
            }
        }
        flush()
        return c
    }
}
