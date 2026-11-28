package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Strategy round, Track W (wave management and positioning): opt-in behaviour audit of the book.
 * `AI_W_AUDIT=h9:h9,h9+wave=0:h9` plays `AI_H_GAMES` mirrored line-ups from `AI_H_FIRST` (`AI_FORMAT`,
 * `AI_THREADS` as elsewhere; the seeds of [HeuristicDiagTest]) and prints, per side, what the owner's
 * theme asks about: minions taken a round, pushes suffered (mid-round or in the Minion Battle) and how many of
 * the team's heroes stood outside the zone the push moved into (more than one walk, or off the board), double
 * pushes (two pushes against a team without one of theirs between, the second in the same or the next round),
 * heroes on the wrong side of the new zone, card plays where a hero far from the Battle Zone could not Fast
 * Travel, hero targets chosen over an offered minion that did not defeat the hero (the poke), and Holds with a
 * minion in reach. `AI_W_LIST=1` lists every push suffered out of position (line-up, side, round, turn,
 * decision id), to save and replay with `AI_H_DIAG_ONLY`. `AI_W_ONE=1` plays side a as Red only.
 *
 * It also prints a digest of every answer with a choice, per game and in all: with a track's knobs off the
 * digest must equal the baseline's (the knob-off proof, DESIGN "How every change is built").
 */
class WaveAuditTest {
    private class Side {
        var games = 0
        var wins = 0
        var rounds = 0
        var minions = 0
        var pushesWon = 0
        var suffered = 0
        var sufferedMid = 0
        var heroesAtPush = 0
        var outAtPush = 0
        var pushesOut = 0
        var wrongSide = 0
        var doubles = 0
        var playCards = 0
        var ftBlocked = 0
        var pokeChoices = 0
        var pokes = 0
        var minionChoices = 0
        var holds = 0
        var holdsWithMinion = 0
        val endings = sortedMapOf<String, Int>()

        /** Card plays with a choice: answer time, ns. */
        val plays = ArrayList<Long>()

        fun add(o: Side) {
            plays += o.plays
            games += o.games
            wins += o.wins
            rounds += o.rounds
            minions += o.minions
            pushesWon += o.pushesWon
            suffered += o.suffered
            sufferedMid += o.sufferedMid
            heroesAtPush += o.heroesAtPush
            outAtPush += o.outAtPush
            pushesOut += o.pushesOut
            wrongSide += o.wrongSide
            doubles += o.doubles
            playCards += o.playCards
            ftBlocked += o.ftBlocked
            pokeChoices += o.pokeChoices
            pokes += o.pokes
            minionChoices += o.minionChoices
            holds += o.holds
            holdsWithMinion += o.holdsWithMinion
            for ((k, v) in o.endings) endings[k] = (endings[k] ?: 0) + v
        }

        private fun ms(): String {
            if (plays.isEmpty()) return "n/a"
            val sorted = plays.sorted()
            return "%.2f mean / %.2f p95 / %.1f max".format(
                plays.average() / 1e6,
                sorted[(sorted.size - 1) * 95 / 100] / 1e6,
                sorted.last() / 1e6,
            )
        }

        override fun toString(): String {
            fun pct(a: Int, b: Int) = if (b == 0) "n/a" else "%.1f%%".format(100.0 * a / b)
            fun per(a: Int, b: Int) = if (b == 0) "n/a" else "%.2f".format(a.toDouble() / b)
            return "won $wins/$games, minions/round ${per(minions, rounds)}, pushes won $pushesWon suffered " +
                "$suffered (mid-round $sufferedMid), suffered with a hero out of position $pushesOut " +
                "(${pct(pushesOut, suffered)}; heroes out ${pct(outAtPush, heroesAtPush)}), wrong side $wrongSide, " +
                "double pushes $doubles, FT blocked ${pct(ftBlocked, playCards)} of $playCards plays, " +
                "pokes over a (not last) minion $pokes of ${pokeChoices + minionChoices} mixed targets " +
                "(hero chosen $pokeChoices), holds with a minion in reach $holdsWithMinion of $holds, " +
                "endings $endings, " +
                "card play ms ${ms()}"
        }
    }

    private class Recorder(val inner: AiPlayer, val onChoice: (Decision, Choice) -> Unit) : AiPlayer {
        /** The looked-at values of the last answer (AI_W_TRACE). */
        val lines = ArrayList<String>()

        init {
            if (System.getenv("AI_W_TRACE") != null) (inner as? HeuristicAi)?.trace = { lines += it }
        }

        /** Card plays with a choice: answer time, ns. */
        val plays = ArrayList<Long>()

        override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
            lines.clear()
            val t0 = System.nanoTime()
            val c = inner.choose(session, decision, cancelled)
            if (decision.kind == DecisionKind.PLAY_CARD && decision.options.size > 1) plays += System.nanoTime() - t0
            onChoice(decision, c)
            return c
        }
    }

    @Test
    fun `wave audit`() {
        val specs = System.getenv("AI_W_AUDIT") ?: return
        for (spec in specs.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val format = AiHarness.Format.parse(spec.substringAfter("@", System.getenv("AI_FORMAT") ?: ""))
            println("WAVE ==== $spec ${format.label}")
            audit(spec.substringBefore("@"), format)
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun audit(spec: String, format: AiHarness.Format) {
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 6
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 0
        HeuristicTournamentTest.checkUnsealed(first, lineups, System.getenv("AI_UNSEAL"))
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 1
        val list = System.getenv("AI_W_LIST") != null
        val (aName, bName) = spec.split(":")
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        val jobs = ArrayList<Callable<Triple<Map<Team, Side>, String, List<String>>>>()
        for (i in first until first + lineups) {
            val (red, blue) = all[i]
            // AI_W_ONE=1: side a plays Red only (a mirror of one spec against itself is the same game).
            for (aRed in if (System.getenv("AI_W_ONE") != null) listOf(true) else listOf(true, false)) {
                jobs += Callable {
                    val coin = if (i % 2 == 0) Team.RED else Team.BLUE
                    val seed = (50_000L + i) * 2
                    val aTeam = if (aRed) Team.RED else Team.BLUE
                    val sides = mapOf(Team.RED to Side(), Team.BLUE to Side())
                    val notes = ArrayList<String>()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var session: EngineSession? = null
                    val track = Tracker(sides, notes, "$i${if (aRed) "R" else "B"}")
                    var pa: Recorder? = null
                    var pb: Recorder? = null
                    val record: (Decision, Choice) -> Unit = { d, c ->
                        if (d.options.size > 1) digest.update("${d.id}:${d.seat}:$c;".toByteArray())
                        val poke = session?.let { choice(it, d, c, sides.getValue(d.team), track.pokes) } == true
                        if (poke && list) {
                            val rec = if ((d.team == Team.RED) == aRed) pa else pb
                            val st = session!!.state
                            notes += "$i${if (aRed) "R" else "B"} POKE R${st.round}T${st.turn} ${d.seat} -> $c " +
                                rec?.lines?.lastOrNull().orEmpty()
                            // AI_W_ALT=restHidden=1: the same question asked of another tune, on a copy (no
                            // effect on the game).
                            System.getenv("AI_W_ALT")?.let { alt ->
                                val tune = alt.split("+").associate { kv ->
                                    kv.substringBefore("=") to kv.substringAfter("=").toDouble()
                                }
                                val other = HeuristicAi(9, seed, tune = tune)
                                val got = ArrayList<String>()
                                other.trace = { got += it }
                                val c2 = other.choose(session!!.forkSession(), d)
                                notes += "        ALT $alt -> $c2 ${got.lastOrNull().orEmpty()}"
                            }
                        }
                    }
                    pa = Recorder(player(aName, if (aRed) seed else seed + 1), record)
                    pb = Recorder(player(bName, if (aRed) seed + 1 else seed), record)
                    val r = AiHarness.play(
                        AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin, format.length, format.mapId),
                        mapOf(Team.RED to if (aRed) pa!! else pb!!, Team.BLUE to if (aRed) pb!! else pa!!),
                        observe = { s, d ->
                            session = s
                            track.drain(s, d)
                        },
                    )
                    track.drain(r.session, null)
                    sides.getValue(aTeam).plays += pa!!.plays
                    sides.getValue(aTeam.opponent).plays += pb!!.plays
                    for ((team, side) in sides) {
                        side.games = 1
                        side.rounds = r.rounds
                        if (r.winner == team) {
                            side.wins = 1
                            side.endings["${r.session.state.winReason}"] = 1
                        }
                    }
                    val bySide = mapOf(aTeam to sides.getValue(aTeam), aTeam.opponent to sides.getValue(aTeam.opponent))
                    val hex = digest.digest().joinToString("") { "%02x".format(it) }.take(16)
                    Triple(
                        mapOf(Team.RED to bySide.getValue(aTeam), Team.BLUE to bySide.getValue(aTeam.opponent)),
                        "$i${if (aRed) "R" else "B"}:$hex",
                        if (list) notes else emptyList(),
                    )
                }
            }
        }
        val pool = Executors.newFixedThreadPool(threads)
        val a = Side()
        val b = Side()
        val digests = ArrayList<String>()
        try {
            for (f in pool.invokeAll(jobs)) {
                val (s, dg, notes) = f.get()
                // RED here is side a, BLUE side b (re-keyed above).
                a.add(s.getValue(Team.RED))
                b.add(s.getValue(Team.BLUE))
                digests += dg
                notes.forEach { println("WAVE   $it") }
            }
        } finally {
            pool.shutdown()
        }
        val total = MessageDigest.getInstance("SHA-256").digest(digests.joinToString(",").toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        println("WAVE a $aName: $a")
        println("WAVE b $bName: $b")
        println("WAVE digest $total games ${digests.joinToString(" ")}")
    }

    /**
     * Choice-level counts: a hero hit over an offered minion, a Hold with a minion in reach. True when [c] is a hero
     * target chosen over an offered minion.
     */
    private fun choice(s: EngineSession, d: Decision, c: Choice, side: Side, pending: MutableList<Poke>): Boolean {
        when (d.kind) {
            DecisionKind.CHOOSE_UNIT -> {
                val st = s.state
                val heroes = d.options.filterIsInstance<Choice.Unit>().filter { o ->
                    val body = st.hero(o.unitId) ?: st.piece(o.unitId)?.heroBody?.let { st.hero(it) }
                    body != null && body.team != d.team
                }
                // A minion that is not its side's last in the lane: taking it does not push mid-round (that push
                // can be a mistake, GENERAL §2 "Heavy and the early push"), so the hero over it is a real poke.
                val minions = d.options.filterIsInstance<Choice.Unit>().filter { o ->
                    val m = st.minion(o.unitId)
                    m != null && m.team != d.team && lastCount(s, m) > 1
                }
                if (heroes.isEmpty() || minions.isEmpty() || c !is Choice.Unit) return false
                if (c in heroes) {
                    side.pokeChoices++
                    val body = st.hero(c.unitId)?.id ?: st.piece(c.unitId)?.heroBody
                    pending += Poke(d.team, body ?: c.unitId, st.round * 8 + st.turn)
                    return true
                } else if (c in minions) {
                    side.minionChoices++
                }
            }

            DecisionKind.CHOOSE_ACTION -> {
                if ((c as? Choice.Action)?.action != ActionType.HOLD) return false
                side.holds++
                val ctx = HContext(s, d, d.team, HeuristicProfile.of(9))
                val h = ctx.own() ?: return false
                val at = h.at ?: return false
                val card = (d.sourceCardId ?: h.unresolved.firstOrNull()?.id)?.let { ctx.card(it) } ?: return false
                val offered = d.options.any {
                    it is Choice.Action && (it.action == ActionType.PRIMARY || it.action == ActionType.ATTACK)
                }
                val minionInReach = card.hitsMinions && ctx.minionTargets(d.team).any {
                    ctx.distance(at, it.at!!) <= ctx.attackReach(h.id, card).coerceAtLeast(1)
                }
                if (offered && card.attackCard && minionInReach) side.holdsWithMinion++
            }

            else -> Unit
        }
        return false
    }

    /** [m]'s side's minions on its lane, on the board. */
    private fun lastCount(s: EngineSession, m: dev.quinton.goa.core.game.engine.MinionState): Int =
        s.state.minions.count { it.team == m.team && it.lane == m.lane && it.at != null }

    private class Poke(val team: Team, val target: String, val key: Int)

    /** Event-level counts, drained at every decision. */
    private class Tracker(val sides: Map<Team, Side>, val notes: MutableList<String>, val game: String) {
        private var seen = 0
        private var endOfRound = false

        /** Per team: the last push against it (round), cleared by a push of its own. */
        private val lastAgainst = HashMap<Team, Int>()
        val pokes = ArrayList<Poke>()
        private val defeatedThisTurn = HashSet<String>()
        private var awaiting: GameEvent.LanePushed? = null
        private var awaitingMid = false

        fun drain(s: EngineSession, d: Decision?) {
            val st = s.state
            val teamOf = s.setup.seats.associate { it.heroId to it.team }
            awaiting?.let { measure(s, it, awaitingMid, d) }
            awaiting = null
            for (e in s.events.drop(seen)) {
                when (e) {
                    is GameEvent.Note -> if (e.text.startsWith("End of round")) endOfRound = true

                    is GameEvent.RoundStarted -> endOfRound = false

                    is GameEvent.TurnStarted -> settlePokes()

                    is GameEvent.Defeated -> {
                        val by = e.byHeroId?.let { teamOf[it] }
                        if (by != null && st.hero(e.unitId) == null && !teamOf.containsKey(e.unitId)) {
                            sides.getValue(by).minions++
                        }
                        defeatedThisTurn += e.unitId
                    }

                    is GameEvent.LanePushed -> {
                        val loser = e.winner.opponent
                        sides.getValue(e.winner).pushesWon++
                        lastAgainst.remove(e.winner)
                        val side = sides.getValue(loser)
                        side.suffered++
                        if (!endOfRound) side.sufferedMid++
                        val prev = lastAgainst[loser]
                        if (prev != null && st.round - prev <= 1) side.doubles++
                        lastAgainst[loser] = st.round
                        awaiting = e
                        awaitingMid = !endOfRound
                    }

                    else -> Unit
                }
            }
            seen = s.events.size
            if (d != null && d.kind == DecisionKind.PLAY_CARD && d.options.size > 1) {
                val ctx = HContext(s, d, d.team, HeuristicProfile.of(9))
                val h = ctx.own()
                val at = h?.at
                if (at != null) {
                    val side = sides.getValue(d.team)
                    side.playCards++
                    if (ctx.distanceToBattleZone(at) >= 4 && !ctx.canFastTravel(h.id)) side.ftBlocked++
                }
            }
        }

        private fun settlePokes() {
            for (p in pokes) if (p.target !in defeatedThisTurn) sides.getValue(p.team).pokes++
            pokes.clear()
            defeatedThisTurn.clear()
        }

        /** Where the losing team's heroes stand when the push has happened (the first decision after it). */
        private fun measure(s: EngineSession, e: GameEvent.LanePushed, mid: Boolean, d: Decision?) {
            val st = s.state
            if (st.winner != null) return
            val loser = e.winner.opponent
            val side = sides.getValue(loser)
            val ctx = HContext(s, d, loser, HeuristicProfile.of(9))
            val zones = ctx.game.geo.lanes[e.lane]?.zones ?: return
            val newIdx = zones.indexOf(e.newBattleZone)
            var out = 0
            for (h in st.heroes.filter { it.team == loser }) {
                side.heroesAtPush++
                val at = h.at
                if (at == null) {
                    out++
                    continue
                }
                val dist = ctx.distanceToZone(at, e.newBattleZone)
                if (dist > WALK) out++
                val idx = ctx.zoneOf(at)?.let { zones.indexOf(it) } ?: -1
                // The wrong side: further toward the winner's throne than the new zone, and not in it.
                val wrong = idx >= 0 && dist > 0 && if (e.winner == Team.RED) idx < newIdx else idx > newIdx
                if (wrong && dist > WALK) side.wrongSide++
            }
            side.outAtPush += out
            if (out > 0) {
                side.pushesOut++
                notes += "$game OUT ${loser.name} R${st.round}T${st.turn} ${if (mid) "mid" else "battle"} " +
                    "-> ${e.newBattleZone} out $out decision ${d?.id}"
            }
        }
    }

    private fun player(spec: String, seed: Long): AiPlayer {
        val parts = spec.split("+")
        val n = parts[0].drop(1).toInt()
        val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }
        return HeuristicAi(n, seed, tune = tune)
    }

    private companion object {
        /** One walk: about three spaces (GENERAL §2, "3 spaces = one action"). */
        const val WALK = 3
    }
}
