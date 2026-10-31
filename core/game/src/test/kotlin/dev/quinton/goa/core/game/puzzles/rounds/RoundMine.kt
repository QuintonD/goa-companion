package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.learned.Learned
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.puzzles.GoalTracker
import dev.quinton.goa.core.model.match.Team

/** A round start from a self-play game: legal by construction, and what the game really did in it. */
class MinedRound(
    val game: Int,
    val round: Int,
    /** Paused at the first card of turn 1. */
    val start: EngineSession,
    /** Hero → the card it really played in turns 1..4 (null: it passed or the game ended). */
    val played: Map<String, List<String?>>,
    val events: List<GameEvent>,
    val rounds: Int,
    /** Hero → turn → what it really answered while resolving its own card (no defenses). */
    val actions: Map<String, Map<Int, List<Choice>>>,
) {
    val heroes: List<String> get() = start.setup.seats.map { it.heroId }

    fun team(t: Team): List<String> = start.setup.seats.filter { it.team == t }.map { it.heroId }

    /** The coins team [t]'s heroes really gained in the round. */
    fun coins(t: Team): Int {
        val mine = team(t).toSet()
        return events.filterIsInstance<GameEvent.CoinsGained>().filter { it.heroId in mine }.sumOf { it.amount }
    }

    /** Goals the real game reached for team [t] in this round. */
    fun goalsFor(t: Team): List<PuzzleGoal> {
        val mine = team(t).toSet()
        val out = ArrayList<PuzzleGoal>()
        for (e in events.filterIsInstance<GameEvent.Defeated>()) {
            if (e.unitId in heroes && e.unitId !in mine) {
                out += PuzzleGoal.DefeatHero(e.unitId, Deadline.END_OF_ROUND, "Defeat ${e.unitId} this round.")
            }
        }
        if (events.any { it is GameEvent.LanePushed && it.winner == t }) {
            out += PuzzleGoal.PushLane(Deadline.END_OF_ROUND, "Push the lane this round.")
        }
        if (events.any { it is GameEvent.GameOver && it.winner == t }) {
            out += PuzzleGoal.WinGame("Win the game this round.", Deadline.END_OF_ROUND)
        }
        val coins = coins(t)
        if (coins > 0) {
            out += PuzzleGoal.GainCoins(coins, Deadline.END_OF_ROUND, "Gain $coins coins by the end of the round.")
        }
        val hurt = events.any {
            (it is GameEvent.Defeated && it.unitId in mine) || (it is GameEvent.LanePushed && it.winner != t)
        }
        if (hurt) out += PuzzleGoal.Survive(Deadline.END_OF_ROUND, "Survive the round.")
        return out.distinct()
    }

    /** When [goal] was reached in the real game for team [t]: the turn, 5 = end of round; null = it wasn't. */
    fun metIn(goal: PuzzleGoal, t: Team): Int? {
        val mine = team(t).toSet()
        var turn = 1
        var coins = 0
        for (e in events) {
            when (e) {
                is GameEvent.TurnStarted -> turn = e.turn
                is GameEvent.Note -> if (e.text.startsWith("End of round")) turn = RoundMine.END_OF_ROUND
                else -> Unit
            }
            when (goal) {
                is PuzzleGoal.DefeatHero -> if (e is GameEvent.Defeated && e.unitId == goal.heroId) return turn

                is PuzzleGoal.PushLane -> if (e is GameEvent.LanePushed && e.winner == t) return turn

                is PuzzleGoal.WinGame -> if (e is GameEvent.GameOver && e.winner == t) return turn

                is PuzzleGoal.GainCoins -> if (e is GameEvent.CoinsGained && e.heroId in mine) {
                    coins += e.amount
                    if (coins >= goal.atLeast) return turn
                }

                is PuzzleGoal.Survive -> Unit
            }
        }
        return null
    }

    fun halted(): EngineSession {
        val f = start.forkSession()
        f.haltWhen = GoalTracker.haltAt(Deadline.END_OF_ROUND, round, 1, beforeRetrieve = true)
        return f
    }
}

internal object RoundMine {
    /** The round starts of [r] from [minRound] on (a game that no longer replays gives what it had). */
    fun rounds(r: Learned.GameRecord, minRound: Int): List<MinedRound> {
        val s = TestData.engine.newGame(r.setup) as EngineSession
        val out = ArrayList<MinedRound>()
        var round = -1
        var start: EngineSession? = null
        var played = LinkedHashMap<String, MutableList<String?>>()
        var eventsAt = 0
        var actions = LinkedHashMap<String, LinkedHashMap<Int, MutableList<Choice>>>()

        fun close() {
            val st = start ?: return
            val ev = s.events.drop(eventsAt)
            val endOfBattle = ev.indexOfFirst { it is GameEvent.Note && it.text.startsWith("Everyone retrieves") }
            out += MinedRound(
                r.game,
                round,
                st,
                played.mapValues { (_, v) -> v.toList() },
                if (endOfBattle >= 0) ev.take(endOfBattle) else ev,
                r.rounds,
                actions.mapValues { (_, v) -> v.mapValues { (_, l) -> l.toList() } },
            )
        }
        for (c in r.answers) {
            val d = s.pending ?: break
            val st = s.state
            if (st.step == Step.PLAY && st.turn == 1 && st.stepSeat == 0 && st.round != round &&
                s.stepAnswers.isEmpty()
            ) {
                close()
                start = null
                round = st.round
                if (round >= minRound) {
                    start = s.forkSession()
                    played = LinkedHashMap()
                    for (h in st.heroes) played[h.id] = MutableList(TURNS) { null }
                    eventsAt = s.events.size
                    actions = LinkedHashMap()
                }
            }
            if (start != null && d.kind == DecisionKind.PLAY_CARD && c is Choice.Card &&
                d.options.none { it is Choice.Decline }
            ) {
                played[d.seat]?.set(st.turn - 1, c.cardId)
            }
            if (start != null && st.acting == d.seat && d.kind != DecisionKind.DEFEND &&
                d.kind != DecisionKind.PLAY_CARD
            ) {
                actions.getOrPut(d.seat) { LinkedHashMap() }.getOrPut(st.turn) { ArrayList() } += c
            }
            if (s.answer(d.id, c) !is AnswerResult.Accepted) return out
        }
        // The last round only counts if the game ended in it (not when the record stopped short).
        if (s.pending == null) close()
        return out
    }

    private const val TURNS = 4
    const val END_OF_ROUND = 5
}
