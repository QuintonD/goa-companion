package dev.quinton.goa.core.game.ai.hybrid

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.LeafChance
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.NetEvaluator
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Wave 4 Track F: the search defects of review-3/V2-code.md, each checked by construction. */
class HybridFixesTest {
    /**
     * V2 §2.4: card A loses at once against one reply in four and is 88% by the net in the other three; card B is
     * 5% in all four. On the leaf's coin scale the mean prefers B (−190 against −117); as chances A is 0.66 and
     * B 0.05, and A wins.
     */
    @Test
    fun `columns average as win chances, so one lost column does not swamp a likely win`() {
        val s = newGame(2, 3L)
        val lost = s.state.copy().also { it.winner = Team.BLUE }
        val coin = 39.146
        val weights = DoubleArray(4) { 0.25 }
        // The old scale: a finished game is −(1000 − round), a position logit × coin scale.
        val oldA = doubleArrayOf(-(1000.0 - lost.round), 80.0, 80.0, 80.0)
        val oldB = DoubleArray(4) { -117.0 }
        assertThat(HybridAi.columnScore(oldB, weights)).isGreaterThan(HybridAi.columnScore(oldA, weights))
        // The chance scale.
        val loss = LeafChance.terminal(lost, Team.RED)!!
        val a = doubleArrayOf(loss, LeafChance.ofLogit(80.0 / coin), LeafChance.ofLogit(80.0 / coin), LeafChance.ofLogit(80.0 / coin))
        val b = DoubleArray(4) { LeafChance.ofLogit(-117.0 / coin) }
        val sa = HybridAi.columnScore(a, weights)
        val sb = HybridAi.columnScore(b, weights)
        assertThat(sa).isWithin(0.01).of(0.66)
        assertThat(sb).isWithin(0.01).of(0.05)
        assertThat(sa).isGreaterThan(sb)
        // Finished games bracket every position, a sooner win above a later one.
        assertThat(loss).isLessThan(LeafChance.unfinished(0.0))
        val wonNow = s.state.copy().also { it.winner = Team.RED }
        val wonLater = s.state.copy().also {
            it.winner = Team.RED
            it.round = wonNow.round + 1
        }
        assertThat(LeafChance.terminal(wonNow, Team.RED)!!).isGreaterThan(LeafChance.terminal(wonLater, Team.RED)!!)
        assertThat(LeafChance.terminal(wonLater, Team.RED)!!).isGreaterThan(LeafChance.unfinished(1.0))
    }

    /** The net's chance is a probability for both sides of the same position (and 1/0 when the game is over). */
    @Test
    fun `the learned leaf gives complementary chances`() {
        val s = newGame(2, 5L)
        val net = NetEvaluator(s.engineData, LearnedNet.ENSEMBLE_6)
        val red = net.chance(s.state, Team.RED)
        assertThat(red).isGreaterThan(0.0)
        assertThat(red).isLessThan(1.0)
        assertThat(red + net.chance(s.state, Team.BLUE)).isWithin(1e-12).of(1.0)
        // Same side as the coin score.
        assertThat((red - 0.5) * net.evaluate(s.state, Team.RED)).isAtLeast(0.0)
        val won = s.state.copy().also { it.winner = Team.BLUE }
        assertThat(net.chance(won, Team.BLUE)).isGreaterThan(0.999)
        assertThat(net.chance(won, Team.RED)).isLessThan(0.001)
    }

    /**
     * V2 §1.3: in a playout the enemy seat after ours chooses its card on a copy where ours is already facedown.
     * Its reply (and the opponent model's weights) must not depend on which card we put down: on every Red
     * last-seat card play of a few book games, the Blue seat's answer is the same whatever Red played. The old
     * model (`hide=0`) does depend on it somewhere in the same positions, so the check is not empty.
     */
    @Test
    fun `the modelled enemy does not see which card we played facedown`() {
        val fixed = BookPolicy(1L, mapOf("branch" to 1.0, "restSpaces" to 6.0), hide = true)
        val clairvoyant = BookPolicy(1L, mapOf("branch" to 1.0, "restSpaces" to 6.0), hide = false)
        var positions = 0
        var oldDiffers = 0
        var oldWeightsDiffer = 0
        for (g in 0 until GAMES) {
            val s = newGame(2, 11L + g)
            val book = mapOf(Team.RED to HeuristicAi(10, 21L + g), Team.BLUE to HeuristicAi(10, 31L + g))
            var n = 0
            while (n++ < MAX_DECISIONS && positions < POSITIONS) {
                val d = s.pending ?: break
                val me = s.state.hero(d.seat)
                val redLast = d.kind == DecisionKind.PLAY_CARD && d.team == Team.RED && me != null &&
                    s.state.heroes.none { it.team == Team.RED && it.seat > me.seat } &&
                    d.options.count { it is Choice.Card } > 1
                if (redLast) {
                    val fixedAnswers = HashSet<Choice>()
                    val fixedWeights = HashSet<List<Double>>()
                    val oldAnswers = HashSet<Choice>()
                    val oldWeights = HashSet<List<Double>>()
                    for (c in d.options) {
                        val f = s.forkSession()
                        check(f.answer(d.id, c) is AnswerResult.Accepted)
                        val e = f.pending ?: continue
                        if (e.kind != DecisionKind.PLAY_CARD || e.team != Team.BLUE || e.options.size < 2) continue
                        fixedAnswers += fixed.answer(f, e, PolicyMode.FULL, emptyMap(), 9L)
                        oldAnswers += clairvoyant.answer(f, e, PolicyMode.FULL, emptyMap(), 9L)
                        val pool = e.options.mapNotNull { (it as? Choice.Card)?.cardId }
                        fixedWeights += fixed.playWeights(f.engineData, f.setup, f.state, e.seat, pool, 1, 2.0) {
                            emptyMap()
                        }.orEmpty().map { Math.round(it * 1e9) / 1e9 }
                        oldWeights += clairvoyant.playWeights(f.engineData, f.setup, f.state, e.seat, pool, 1, 2.0) {
                            emptyMap()
                        }.orEmpty().map { Math.round(it * 1e9) / 1e9 }
                    }
                    if (fixedAnswers.isNotEmpty()) {
                        positions++
                        assertThat(fixedAnswers).hasSize(1)
                        assertThat(fixedWeights).hasSize(1)
                        if (oldAnswers.size > 1) oldDiffers++
                        if (oldWeights.size > 1) oldWeightsDiffer++
                    }
                }
                check(s.answer(d.id, book.getValue(d.team).choose(s, d)) is AnswerResult.Accepted)
            }
        }
        assertThat(positions).isAtLeast(POSITIONS / 2)
        assertThat(fixed.fallbacks.total).isEqualTo(0L)
        println(
            "hidden cards: $positions positions; the old model depended on our card in $oldDiffers (FULL reply), " +
                "$oldWeightsDiffer (model weights)",
        )
        assertThat(oldDiffers + oldWeightsDiffer).isAtLeast(1)
    }

    /** The joint card deals are the most likely ones, exactly, and stop at the mass asked for. */
    @Test
    fun `card deals enumerate the most likely joint deals`() {
        val a = listOf(0.6, 0.3, 0.1)
        val b = listOf(1.0, 4.0) // 0.2, 0.8
        val all = HybridAi.topDeals(listOf(a, b), 6, 1.0)
        // 0.48, 0.24, 0.12, 0.08, 0.06, 0.02
        assertThat(all.map { it.first.toList() }).containsExactly(
            listOf(0, 1), listOf(1, 1), listOf(0, 0), listOf(2, 1), listOf(1, 0), listOf(2, 0),
        ).inOrder()
        assertThat(all.sumOf { it.second }).isWithin(1e-12).of(1.0)
        assertThat(all[0].second).isWithin(1e-12).of(0.48)
        // The best two of six, and a stop at 80%: 0.48 + 0.24 = 0.72 < 0.8, so three.
        assertThat(HybridAi.topDeals(listOf(a, b), 2, 1.0).map { it.first.toList() })
            .containsExactly(listOf(0, 1), listOf(1, 1)).inOrder()
        assertThat(HybridAi.topDeals(listOf(a, b), 6, 0.8)).hasSize(3)
        // A forced hero (one card) changes nothing.
        assertThat(HybridAi.topDeals(listOf(a, listOf(1.0), b), 6, 1.0).map { it.second })
            .containsExactlyElementsIn(all.map { it.second }).inOrder()
    }

    /**
     * V2 §2.2: with card deals the same position is modelled the same way at either seat colour: Red's card play
     * (Blue still to choose) and Blue's (Red already facedown) both take several weighted deal columns and no
     * drawn replies, and a Red card play's answer does not move with the seed (no sampled replies; 2v2 has no
     * facedown tokens at the start).
     */
    @Test
    fun `card deals model both seat colours alike, without seed noise`() {
        val s = newGame(2, 17L)
        val book = HeuristicAi(10, 3L)
        val seen = HashMap<Team, Int>()
        var n = 0
        while (n++ < MAX_DECISIONS && ((seen[Team.RED] ?: 0) < 2 || (seen[Team.BLUE] ?: 0) < 2)) {
            val d = s.pending ?: break
            if (d.kind == DecisionKind.PLAY_CARD && d.options.count { it is Choice.Card } > 2 &&
                (seen[d.team] ?: 0) < 2
            ) {
                val cols = IntArray(2)
                val answers = (0..1).map { k ->
                    val ai = HybridAi(100L + k, AiPlayers.SEARCH_10 + ("leaf" to 0.0) + ("td" to 0.0))
                    ai.scored = { _, sc, g ->
                        cols[k] = g
                        assertThat(sc.values.all { it in 0.0..1.0 }).isTrue()
                    }
                    ai.choose(s.forkSession(), d).also { assertThat(ai.fallbacks.values.sum()).isEqualTo(0L) }
                }
                assertThat(cols[0]).isGreaterThan(1)
                assertThat(answers[1]).isEqualTo(answers[0])
                seen.merge(d.team, 1, Int::plus)
            }
            check(s.answer(d.id, book.choose(s, d)) is AnswerResult.Accepted)
        }
        assertThat(seen[Team.RED]).isEqualTo(2)
        assertThat(seen[Team.BLUE]).isEqualTo(2)
    }

    /**
     * V2 §6.2/§6.6: a tiny budget on a large 3v3 card play returns a legal answer within the budget plus one
     * step's slack (the cut is checked inside the playouts), and says the deadline cut it.
     */
    @Test
    fun `a budget bounds the slowest answers`() {
        val s = newGame(3, 29L)
        val book = HeuristicAi(10, 5L)
        // A card play of the second round, many columns (both colours have played and still play).
        var d = s.pending ?: error("no question")
        while (!(d.kind == DecisionKind.PLAY_CARD && s.state.round >= 2 && d.options.size >= 4)) {
            check(s.answer(d.id, book.choose(s, d)) is AnswerResult.Accepted)
            d = s.pending ?: error("game over")
        }
        val knobs = AiPlayers.SEARCH_10 + ("leaf" to AiPlayers.SEARCH_10_LEAF.ordinal.toDouble())
        // Warm up the classes and the nets once.
        HybridAi(7L, knobs, budgetMs = BUDGET_MS).choose(s.forkSession(), d)
        for (seed in 1L..3L) {
            val ai = HybridAi(seed, knobs, budgetMs = BUDGET_MS)
            val t0 = System.nanoTime()
            val c = ai.choose(s.forkSession(), d)
            val ms = (System.nanoTime() - t0) / 1e6
            assertThat(d.options).contains(c)
            assertThat(ms).isLessThan((BUDGET_MS + SLACK_MS).toDouble())
            assertThat(ai.fallbacks.getValue("deadline")).isEqualTo(1L)
            assertThat(ai.fallbacks.getValue("search")).isEqualTo(0L)
        }
        // Without a budget the same answer takes far longer (what the cut saved).
        val t0 = System.nanoTime()
        val free = HybridAi(1L, knobs)
        assertThat(d.options).contains(free.choose(s.forkSession(), d))
        val freeMs = (System.nanoTime() - t0) / 1e6
        println("budget: $BUDGET_MS ms cut; unbudgeted answer ${"%.0f".format(freeMs)} ms")
        assertThat(free.fallbacks.getValue("deadline")).isEqualTo(0L)
    }

    private fun newGame(size: Int, seed: Long): EngineSession {
        val (red, blue) = AiHarness.mixedLineups(1, seed = seed, size = size)[0]
        return TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, Team.RED))
            as EngineSession
    }

    private companion object {
        const val GAMES = 4
        const val POSITIONS = 60
        const val MAX_DECISIONS = 3000
        const val BUDGET_MS = 60L
        const val SLACK_MS = 200L
    }
}
