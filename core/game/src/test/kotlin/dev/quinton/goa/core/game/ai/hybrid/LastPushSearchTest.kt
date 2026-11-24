package dev.quinton.goa.core.game.ai.hybrid

import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.ThreadRunner
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Wave 4 Track P: the last push, on positions recorded from the strength-10 candidate's lost games against the old
 * strength 10 (W3 + NET6, line-ups 600–647, `review-3/w3-net6-vs-s10-600-647.log`; RESULTS.md "Wave 4 — Track P").
 * Each is a turn-3 question on the last wave where the game was lost at that round's end: the search's playout
 * stopped at the start of turn 4 and the leaf judged a position whose Minion Battle (p.8) was already decided.
 * With `lpx` the playouts play turn 4 and the round end, and the search answers as the rules require.
 *
 * The rules check (rulebook p.8): at the round's end the side with fewer minions removes the difference, a side
 * left with none loses the push, and the last Wave counter's push wins the game. a minions against b: a ≥ 2b pushes.
 */
class LastPushSearchTest {
    /** The tournament's spec of the lost games (`x10+pol=1+scope=3+leaf=3+…`), the search as it played them. */
    private val played: Map<String, Double> = mapOf(
        "pol" to 1.0, "scope" to 3.0, "leaf" to HybridLeaf.NET6.ordinal.toDouble(), "tt" to 1.0, "K" to 24.0,
        "topS" to 8.0, "topU" to 6.0, "rep" to 4.0, "td" to 2.0, "tw" to 3.0, "branch" to 1.0, "restSpaces" to 6.0,
    )

    /** The seed each side played with (Tournament.runMixed: Red (50 000 + line-up) × 2, Blue one more). */
    private fun seed(game: String): Long {
        val i = game.dropLast(1).toLong()
        return (50_000L + i) * 2 + if (game.endsWith("B")) 1 else 0
    }

    private fun search(game: String, s: EngineSession, knobs: Map<String, Double>): Choice {
        val d = s.pending!!
        return HybridAi(seed(game), knobs, ThreadRunner(THREADS)).choose(s, d)
    }

    private fun book(s: EngineSession): Choice = HeuristicAi(10, 1L).choose(s, s.pending!!)

    /** Red and Blue minions in the Battle Zone. */
    private fun count(s: EngineSession): Pair<Int, Int> {
        val lane = s.state.battleZones.keys.single()
        return s.state.minions.count { it.team == Team.RED && it.lane == lane && it.at != null } to
            s.state.minions.count { it.team == Team.BLUE && it.lane == lane && it.at != null }
    }

    /** Answer [c], then the book for every seat to the round's end: who won, if the game ended there. */
    private fun bookRoundEnd(s: EngineSession, c: Choice): Team? {
        val f = s.forkSession()
        val round = f.state.round
        check(f.answer(f.pending!!.id, c) is AnswerResult.Accepted)
        val book = HeuristicAi(10, 3L)
        var n = 0
        while (f.state.winner == null && f.state.round == round && n++ < 600) {
            val d = f.pending ?: break
            check(f.answer(d.id, book.choose(f, d)) is AnswerResult.Accepted)
        }
        return f.state.winner
    }

    /**
     * Game 632 (x10 Red: Widget, Bain), round 6, turn 3, last wave, Red 2 minions against Blue 4. Widget's Gold
     * hits Swift (a hero who defends) or the Blue minion m36. Left at 2 against 4 the battle pushes for Blue
     * (4 ≥ 2 × 2), which is what happened. The book takes the minion; the search with the turn-3 horizon took Swift.
     */
    @Test
    fun `632 - behind on the last wave, the turn-3 attack takes a minion, not a hero`() {
        val p = RecordedGames.at("game-632R", 6, 3, "widget", DecisionKind.CHOOSE_UNIT)
        val s = p.session
        assertWithMessage("Red 2 against Blue 4").that(count(s)).isEqualTo(2 to 4)
        val minion = s.pending!!.options.single { o -> o is Choice.Unit && s.state.minion(o.unitId) != null }
        assertWithMessage("the game's answer").that(p.played).isEqualTo(Choice.Unit("swift"))
        assertWithMessage("the book").that(book(s)).isEqualTo(minion)
        assertWithMessage("with lpx").that(search("632R", s, played + ("lpx" to 1.0))).isEqualTo(minion)
    }

    /**
     * Game 613 (x10 Red: Trinkets, Bain), round 5, turn 3, last wave, Red 4 against Blue 5. Bain's Red 3a is an
     * attack; Silver is not. Blue's turn-3 attacks took two Red minions (2 against 5): from there Red needs two kills
     * this round (turn 3 and turn 4) to stay under 2 × its count. The search played Silver and held; one kill on turn
     * 4 left 2 against 4 and the push.
     */
    @Test
    fun `613 - behind on the last wave, turn 3 spends the attack card`() {
        val p = RecordedGames.at("game-613R", 5, 3, "bain", DecisionKind.PLAY_CARD)
        val s = p.session
        assertWithMessage("Red 4 against Blue 5").that(count(s)).isEqualTo(4 to 5)
        assertWithMessage("the game's answer").that(p.played).isEqualTo(Choice.Card("bain-silver"))
        assertWithMessage("the book").that(book(s)).isEqualTo(Choice.Card("bain-red-3a"))
        assertWithMessage("with lpx").that(search("613R", s, played + ("lpx" to 1.0)))
            .isEqualTo(Choice.Card("bain-red-3a"))
    }

    /** Opt-in (`AI_LASTPUSH_DIAG=1`): every recorded question, with and without `lpx`, its time and the round end. */
    @Test
    fun `diagnostics`() {
        if (System.getenv("AI_LASTPUSH_DIAG") == null) return
        val cases = listOf(
            Triple("632R", Triple(6, 3, "widget"), DecisionKind.CHOOSE_UNIT),
            Triple("632R", Triple(6, 3, "widget"), DecisionKind.PLAY_CARD),
            Triple("632R", Triple(6, 3, "bain"), DecisionKind.PLAY_CARD),
            Triple("613R", Triple(5, 3, "bain"), DecisionKind.PLAY_CARD),
            Triple("613R", Triple(5, 3, "bain"), DecisionKind.CHOOSE_ACTION),
            Triple("604B", Triple(6, 3, "swift"), DecisionKind.PLAY_CARD),
            Triple("604B", Triple(6, 3, "nebkher"), DecisionKind.PLAY_CARD),
            Triple("604B", Triple(5, 4, "nebkher"), DecisionKind.CHOOSE_ACTION),
            Triple("606B", Triple(6, 3, "ursafar"), DecisionKind.PLAY_CARD),
            Triple("606B", Triple(6, 2, "sabina"), DecisionKind.CHOOSE_ACTION),
        )
        for ((game, at, kind) in cases) {
            val (round, turn, seat) = at
            val p = RecordedGames.at("game-$game", round, turn, seat, kind)
            val s = p.session
            val bookAnswer = book(s)
            val holdAnswer = HeuristicAi(10, 1L, tune = mapOf("hold" to 1.0)).choose(s, s.pending!!)
            val t0 = System.nanoTime()
            val without = search(game, s, played)
            val t1 = System.nanoTime()
            val with = search(game, s, played + ("lpx" to 1.0))
            val t2 = System.nanoTime()
            println(
                "LASTPUSH $game R${round}T$turn $seat $kind count=${count(s)} game=${p.played} book=$bookAnswer " +
                    "hold=$holdAnswer " +
                    "x10=$without (${(t1 - t0) / 1_000_000} ms) x10+lpx=$with (${(t2 - t1) / 1_000_000} ms) " +
                    "bookRoundEnd: " + s.pending!!.options.joinToString { "$it→${bookRoundEnd(s, it) ?: "-"}" },
            )
        }
    }

    private companion object {
        const val THREADS = 3
    }
}
