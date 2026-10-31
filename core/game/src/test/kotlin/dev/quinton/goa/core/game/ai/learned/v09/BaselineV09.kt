package dev.quinton.goa.core.game.ai.learned.v09

import dev.quinton.goa.core.domain.f32
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.PositionEvaluator
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team

/**
 * The strength 10 of `feature/v0.10`'s first commit at the end of a playout: the v0.9 net on the v0.9
 * features, the baseline of every v0.10 measurement (`net=v09`, ADR-049). It gives the very answers
 * of that commit (`FingerprintTest`). No per-hero or per-card parts.
 */
internal class BaselineV09(data: EngineData) : PositionEvaluator {
    private val features = FeaturesV09(data)

    override fun evaluate(state: GameState, team: Team): Double {
        val red =
            state.winner?.let { if (it == Team.RED) Evaluator.WIN - state.round else -Evaluator.WIN + state.round }
                ?: (redLogit(state) * NET.coinScale)
        return if (team == Team.RED) red else -red
    }

    private fun redLogit(state: GameState): Double {
        val x = FloatArray(2 * FeaturesV09.SIZE)
        features.extract(state, x)
        return f32(NET.forward(x, 0).toDouble() - NET.forward(x, FeaturesV09.SIZE).toDouble())
    }

    companion object {
        val NET: LearnedNet by lazy {
            LearnedNet(
                LearnedWeightsV09.INPUTS,
                LearnedWeightsV09.H1,
                LearnedWeightsV09.H2,
                LearnedWeightsV09.w1(),
                LearnedWeightsV09.b1(),
                LearnedWeightsV09.w2(),
                LearnedWeightsV09.b2(),
                LearnedWeightsV09.w3(),
                LearnedWeightsV09.wl(),
                LearnedWeightsV09.B3,
                LearnedWeightsV09.COIN_SCALE,
            )
        }
    }
}
