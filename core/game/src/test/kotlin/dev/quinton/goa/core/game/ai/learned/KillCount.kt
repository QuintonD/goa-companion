package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.SearchAi
import dev.quinton.goa.core.game.ai.TeamKills
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.model.match.Team

/**
 * One side's team-play counts over a tournament (ADR-061, `AI_KILLS=1`): the kills of [TeamKills] open
 * to it at the start of each turn of the real game (two-hero kills: the target holds two cards or more)
 * and how many of those targets it defeated on that turn; how often its card-play playouts were extended
 * through a kill turn; its work per card play.
 */
internal class KillCount {
    var two = 0
    var twoTaken = 0
    var one = 0
    var oneTaken = 0
    var plays = 0
    var work = 0L
    var ended = 0L
    var extended = 0L

    @Synchronized
    fun add(c: KillCount) {
        two += c.two
        twoTaken += c.twoTaken
        one += c.one
        oneTaken += c.oneTaken
        plays += c.plays
        work += c.work
        ended += c.ended
        extended += c.extended
    }

    fun line(name: String): String = "$name: two-hero kills open $two, taken $twoTaken (${pct(twoTaken, two)}); " +
        "one-hero kills open $one, taken $oneTaken (${pct(oneTaken, one)}); " +
        "work/card play ${if (plays == 0) 0 else work / plays} over $plays; " +
        "kill extension on $extended of $ended playouts (${pct(extended, ended)})"

    private fun pct(a: Number, b: Number) =
        if (b.toDouble() == 0.0) "-" else "%.1f%%".format(100 * a.toDouble() / b.toDouble())

    /** Watches one game ([AiHarness.play]'s `observe`), then counts each team's part of it ([of]). */
    class Game(private val players: Map<Team, AiPlayer>) {
        /** Per team: the kills open at each turn's start, as (round, turn, target, needs two). */
        private val open = mapOf(Team.RED to ArrayList<List<Any>>(), Team.BLUE to ArrayList())
        private var lastTurn = -1
        private val plays = mutableMapOf(Team.RED to 0, Team.BLUE to 0)
        private val playWork = mutableMapOf(Team.RED to 0L, Team.BLUE to 0L)
        private var prev: Triple<DecisionKind, Team, Long>? = null
        private var ev: Evaluator? = null

        private fun workOf(t: Team) = (players.getValue(t) as? SearchAi)?.work ?: 0L

        fun observe(s: EngineSession, d: Decision) {
            prev?.let { (kind, t, w) ->
                if (kind == DecisionKind.PLAY_CARD) playWork[t] = playWork.getValue(t) + workOf(t) - w
            }
            prev = Triple(d.kind, d.team, workOf(d.team))
            if (d.kind == DecisionKind.PLAY_CARD && d.options.size > 1) plays[d.team] = plays.getValue(d.team) + 1
            val st = s.state
            val key = st.round * 8 + st.turn
            if (d.kind == DecisionKind.PLAY_CARD && st.step == Step.PLAY && key != lastTurn) {
                lastTurn = key
                val e = ev ?: Evaluator(s.engineData).also { ev = it }
                for (t in Team.entries) {
                    for (c in TeamKills.chances(e, st, t)) {
                        open.getValue(t) += listOf(st.round, st.turn, c.target, c.needsTwo)
                    }
                }
            }
        }

        fun of(r: AiHarness.Result, t: Team): KillCount {
            val defeated = HashSet<List<Any>>()
            var round = 0
            var turn = 0
            for (e in r.session.events) {
                if (e is GameEvent.TurnStarted) {
                    round = e.round
                    turn = e.turn
                }
                if (e is GameEvent.Defeated) defeated += listOf(round, turn, e.unitId)
            }
            val c = KillCount()
            for (o in open.getValue(t)) {
                val taken = listOf(o[0], o[1], o[2]) in defeated
                if (o[3] == true) {
                    c.two++
                    if (taken) c.twoTaken++
                } else {
                    c.one++
                    if (taken) c.oneTaken++
                }
            }
            c.plays = plays.getValue(t)
            c.work = playWork.getValue(t)
            (players.getValue(t) as? SearchAi)?.kills?.let { (en, ex) ->
                c.ended = en
                c.extended = ex
            }
            return c
        }
    }
}
