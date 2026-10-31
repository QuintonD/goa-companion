package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.zoneName
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionMark
import dev.quinton.goa.core.game.GameReport
import dev.quinton.goa.core.game.ReviewAccuracy
import dev.quinton.goa.core.game.ReviewMistake
import dev.quinton.goa.core.game.ReviewedDecision
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.engine.SavedPayload

/**
 * The post-game report (ADR-051): replays the save and looks again at every decision of the given
 * seats with more than one option, from what that seat's team could know at that moment (the same search as
 * the nudge, always including the choice made), marks each by the winning chance it gave up, and builds
 * the sheet's content for the biggest mistakes. Runs on the caller's background thread; nothing is stored.
 */
internal class CoachReviewer(
    private val engine: GameEngine,
    private val searcher: Searcher,
    private val seed: Long,
    private val runner: SampleRunner,
) {
    private class Looked(val fork: EngineSession, val d: Decision, val choice: Choice, val row: ReviewedDecision)

    fun review(
        saved: SavedGame,
        seats: Set<String>,
        progress: (done: Int, total: Int) -> Unit,
        cancelled: () -> Boolean,
    ): GameReport? {
        val payload = EngineSession.JSON.decodeFromString(SavedPayload.serializer(), saved.payload)
        val session = engine.fromState(saved.setup, payload.initial)
        val total = payload.answers.count { it.seat in seats }
        var done = 0
        val looked = ArrayList<Looked>()
        for (rec in payload.answers) {
            if (cancelled()) return null
            val d = session.pending ?: break
            if (d.seat in seats) {
                if (d.options.size >= 2 &&
                    rec.choice in d.options
                ) {
                    look(session, d, rec.choice, cancelled)?.let { looked += it }
                }
                progress(++done, total)
            }
            if (session.answer(d.id, rec.choice) !is AnswerResult.Accepted) break
        }
        if (cancelled()) return null
        val rows = looked.map { it.row }
        return GameReport(
            decisions = rows,
            overall = accuracy("All", rows),
            byHero = rows.groupBy { it.seat }.map { (k, v) -> accuracy(k, v) }.sortedBy { it.key },
            byKind = rows.groupBy { it.kind.name }.map { (k, v) -> accuracy(k, v) }.sortedBy { it.key },
            mistakes = mistakes(looked, cancelled) ?: return null,
        )
    }

    private fun look(session: EngineSession, d: Decision, choice: Choice, cancelled: () -> Boolean): Looked? {
        val fork = session.forkSession()
        val a = searcher.analyze(fork, d, Searcher.mix(seed, d.id), cancelled, Long.MAX_VALUE, mustInclude = choice)
            ?: return null
        val perSide = fork.setup.seats.count { it.team == d.team }
        val ranked = rank(a, perSide)
        val best = ranked.firstOrNull() ?: return null
        val mine = ranked.firstOrNull { it.option.choice == choice } ?: return null
        val loss = (best.chance - mine.chance).coerceAtLeast(0.0)
        val st = fork.state
        val row = ReviewedDecision(
            decisionId = d.id,
            seat = d.seat,
            kind = d.kind,
            round = st.round,
            turn = st.turn,
            mark = mark(loss, best === mine).let { m ->
                // Called a mistake only with something to say why: the sheet can't back a bare number.
                if (m >= DecisionMark.MISTAKE && !canSayWhy(fork, d, a, best, mine)) DecisionMark.INACCURACY else m
            },
            loss = loss,
            eventIndex = fork.events.size,
        )
        return Looked(fork, d, choice, row)
    }

    private fun canSayWhy(fork: EngineSession, d: Decision, a: Analysis, best: Ranked, mine: Ranked): Boolean {
        val facts = CoachFacts(fork, d, a, searcher.ev)
        val b = best.option.choice
        val c = mine.option.choice
        return Explainer(fork, d, a, c, b).explainWithKind() != null ||
            ReasonFinder(facts).forOption(b, c).any { it.claim != null }
    }

    private fun mistakes(looked: List<Looked>, cancelled: () -> Boolean): List<ReviewMistake>? {
        val worst = looked.filter { it.row.mark >= DecisionMark.INACCURACY }
            .sortedWith(compareBy({ -it.row.loss }, { it.row.decisionId }))
            .take(MAX_MISTAKES)
            .sortedBy { it.row.decisionId }
        return worst.map { l ->
            if (cancelled()) return null
            val decisionSeed = Searcher.mix(seed, l.d.id)
            val a = searcher.analyze(l.fork, l.d, decisionSeed, cancelled, Long.MAX_VALUE, mustInclude = l.choice)
                ?.let { searcher.deepen(l.fork, l.d, decisionSeed, it, DEEP_SAMPLES, cancelled, Long.MAX_VALUE) }
                ?: return null
            val facts = CoachFacts(l.fork, l.d, a, searcher.ev)
            val suggestions =
                SuggestionBuilder(facts, searcher, decisionSeed, runner, Long.MAX_VALUE, cancelled).build()
                    ?: return null
            ReviewMistake(l.row, l.d.prompt, facts.label(l.choice), position(facts), suggestions)
        }
    }

    /** Only what the seat could see then: its view of the board at that decision. */
    private fun position(f: CoachFacts): String {
        val v = f.view
        val own = v.lifeCounters[f.team] ?: 0
        val other = v.lifeCounters[f.team.opponent] ?: 0
        val lane = v.battleZones.keys.sorted().mapNotNull { id ->
            val (ours, theirs) = f.battleCount(id) ?: return@mapNotNull null
            val zone = f.data.maps[v.mapId]?.zoneName(v.battleZones[id]) ?: id
            "$zone is the Battle Zone: your $ours minions against their $theirs, ${v.wavesLeft[id] ?: 0} Wave counters left"
        }.joinToString("; ")
        val at = f.myAt
        val where = if (at == null) {
            "off the board"
        } else {
            val near = f.distanceToNearestEnemy(at)?.let { (e, n) ->
                "$n space${if (n == 1) "" else "s"} from ${f.data.heroName(e)}"
            }
            listOfNotNull(f.g.spaceName(at), near).joinToString(", ")
        }
        val me = f.me
        return "Round ${v.round}, turn ${v.turn}. $lane. Life counters: yours $own, theirs $other. " +
            "${f.name}, level ${me.level} with ${me.coins} coin${if (me.coins == 1) "" else "s"} and " +
            "${me.handCount} card${if (me.handCount == 1) "" else "s"} in hand, $where."
    }

    private fun accuracy(key: String, rows: List<ReviewedDecision>) = ReviewAccuracy(
        key = key,
        decisions = rows.size,
        accurate = rows.count { it.mark <= DecisionMark.GOOD },
        meanLoss = if (rows.isEmpty()) 0.0 else rows.sumOf { it.loss } / rows.size,
    )

    companion object {
        /** Winning chance given up: the marks' edges, fitted on the survey (AI.md "The coach"). */
        const val GOOD_BELOW: Double = 0.02
        const val INACCURACY_BELOW: Double = 0.05
        const val MISTAKE_BELOW: Double = 0.10
        const val MAX_MISTAKES: Int = 5
        const val DEEP_SAMPLES: Int = 12

        fun mark(loss: Double, best: Boolean): DecisionMark = when {
            best -> DecisionMark.BEST
            loss < GOOD_BELOW -> DecisionMark.GOOD
            loss < INACCURACY_BELOW -> DecisionMark.INACCURACY
            loss < MISTAKE_BELOW -> DecisionMark.MISTAKE
            else -> DecisionMark.BLUNDER
        }
    }
}
