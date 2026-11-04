package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.domain.f32
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.PositionEvaluator
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team
import kotlin.math.sqrt

/**
 * Several nets on one position (ADR-060): the features are extracted once, each net scores them in
 * its own coin scale, and the scores combine by [mode]. A finished game scores like [Evaluator].
 *
 * - `avg`: the mean of the nets' scores (seed averaging, against the search's overrating);
 * - `lcb<k>`: the mean less k × the nets' spread (standard deviation), from the asking team's view: a
 *   position the seeds disagree on counts as worse for whoever asks (pessimism under the evaluation's
 *   own uncertainty, as ensembles do in offline reinforcement learning);
 * - `min`: the worst of the nets' scores for the asking team.
 */
internal class EnsembleEvaluator(
    data: EngineData,
    private val nets: List<LearnedNet>,
    private val mode: Mode,
    private val k: Double = 0.0,
) : PositionEvaluator {
    enum class Mode { AVG, LCB, MIN }

    private val features = Features(data)

    init {
        require(nets.isNotEmpty() && nets.all { it.inputs == Features.SIZE }) { "Ensemble: nets for these features" }
    }

    /** Each net's Red score in coin-equivalents. */
    fun redScores(state: GameState): DoubleArray {
        val x = FloatArray(2 * Features.SIZE)
        val perHero = FloatArray(state.heroes.size * Features.HERO.size)
        features.extract(state, x, perHero)
        return DoubleArray(nets.size) { i ->
            val net = nets[i]
            val red = net.forward(x, 0).toDouble() + net.corrections(state, Team.RED, perHero)
            val blue = net.forward(x, Features.SIZE).toDouble() + net.corrections(state, Team.BLUE, perHero)
            f32(f32(red) - f32(blue)) * net.coinScale
        }
    }

    override fun evaluate(state: GameState, team: Team): Double {
        state.winner?.let {
            val red = if (it == Team.RED) Evaluator.WIN - state.round else -Evaluator.WIN + state.round
            return if (team == Team.RED) red else -red
        }
        val s = redScores(state)
        if (team != Team.RED) for (i in s.indices) s[i] = -s[i]
        return when (mode) {
            Mode.AVG -> s.average()

            Mode.MIN -> s.min()

            Mode.LCB -> {
                val m = s.average()
                val sd = if (s.size < 2) 0.0 else sqrt(s.sumOf { (it - m) * (it - m) } / s.size)
                m - k * sd
            }
        }
    }

    companion object {
        /** `avg(a,b,c)`, `lcb1.5(a,b,c)`, `min(a,b)`; null if [spec] is a plain net name. */
        fun parse(spec: String): ((EngineData) -> PositionEvaluator)? {
            val open = spec.indexOf('(')
            if (open < 0 || !spec.endsWith(")")) return null
            val head = spec.substring(0, open)
            val nets = spec.substring(open + 1, spec.length - 1).split(",").map { Learned.net(it.trim()) }
            val (mode, k) = when {
                head == "avg" -> Mode.AVG to 0.0
                head == "min" -> Mode.MIN to 0.0
                head.startsWith("lcb") -> Mode.LCB to head.removePrefix("lcb").toDouble()
                else -> error("Unknown ensemble $head")
            }
            return { data -> EnsembleEvaluator(data, nets, mode, k) }
        }
    }
}
