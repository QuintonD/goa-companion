package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.EvalSource
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.ai.SearchAi
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HEval

/**
 * The prototype players of REVIEW-2-search.md, for tournaments only (not seated by [AiPlayers]):
 *
 * - [hybrid] (`x10+…`): the heuristic-guided determinized search, [HybridAi].
 * - [searchWithBook] (`y10+…`): the old strength 10 ([AiPlayers.searchDeterministic]) with the minimal
 *   change: the heuristic book answers inside its playouts (`pol`: 0 static, 1 full), deals the enemy's
 *   unrevealed card (`model`: 0 the old static model, 1 the book's static scores, 2 its card looks), and
 *   `leaf` 0 HEval, 1 the old hand evaluation, 2 the old learned ensemble (as s10). `K`/`Ko` set its samples.
 */
object HybridPlayers {
    fun hybrid(seed: Long, knobs: Map<String, Double>, runner: SampleRunner = SampleRunner.SEQUENTIAL): AiPlayer =
        HybridAi(seed, knobs, runner)

    fun searchWithBook(seed: Long, knobs: Map<String, Double>, runner: SampleRunner = SampleRunner.SEQUENTIAL): AiPlayer {
        val bookTune = knobs.filterKeys { it !in HybridConfig.NAMES }
        val policy = BookPolicy(seed, bookTune)
        val mode = PolicyMode.of((knobs["pol"] ?: 0.0).toInt())
        val model = (knobs["model"] ?: 1.0).toInt()
        val tau = knobs["tau"] ?: 2.0
        val leaf = (knobs["leaf"] ?: 2.0).toInt()
        var p = AiPlayers.profile(10).copy(
            playoutPolicy = { f, d, _ -> policy.answer(f, d, mode, emptyMap(), seed) },
            playModelOn = if (model > 0) {
                { data, setup, st, hero, pool -> policy.playWeights(data, setup, st, hero, pool, model, tau) { emptyMap() } }
            } else {
                null
            },
        )
        knobs["K"]?.let { p = p.copy(playSamples = it.toInt()) }
        knobs["Ko"]?.let { p = p.copy(otherSamples = it.toInt()) }
        p = when (leaf) {
            0 -> p.copy(
                leafOn = { f, team ->
                    HEval.evaluate(HContext(f, f.pending, team, policy.profile, finishing = true), team)
                },
            )

            1 -> p.copy(learned = EvalSource.HAND)

            else -> p
        }
        return SearchAi(AiLevel.of(10), p, seed, timeCap = false, runner = runner)
    }
}
