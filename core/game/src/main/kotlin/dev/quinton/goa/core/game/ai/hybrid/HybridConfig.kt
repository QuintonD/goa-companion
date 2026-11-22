package dev.quinton.goa.core.game.ai.hybrid

/**
 * How far a hybrid playout runs (REVIEW-2-search.md). Every horizon also stops at the end of the game.
 */
enum class HybridHorizon {
    /** The card's (or the current) action only. */
    ACTION,

    /** The end of this turn (after turn 4: through the end of the round). */
    TURN,

    /** The end of the next turn. */
    TWO_TURNS,

    /** The end of this round: the new round's first card play is the leaf. */
    ROUND,

    /** The end of this round and the next round's first turn. */
    ROUND_PLUS,
    ;

    companion object {
        fun of(code: Int): HybridHorizon = entries[code.coerceIn(0, entries.lastIndex)]
    }
}

/** What scores a playout's end. */
enum class HybridLeaf {
    /** The heuristic book's own position value ([dev.quinton.goa.core.game.ai.heuristic.HEval]). */
    HEVAL,

    /** The old search's hand evaluation with the net's knobs ([dev.quinton.goa.core.game.ai.Evaluator]). */
    OLD,

    /** The old strength 10's learned ensemble ([dev.quinton.goa.core.game.ai.NetEvaluator]). */
    NET,
    ;

    companion object {
        fun of(code: Int): HybridLeaf = entries[code.coerceIn(0, entries.lastIndex)]
    }
}

/** Which version of the book answers inside a playout ([BookPolicy]). */
enum class PolicyMode {
    /** Static scores; one-ply looks only where the text is opaque (a bullet, a token). Card plays static. */
    STATIC,

    /** The book as h10 plays it: card plays through [dev.quinton.goa.core.game.ai.heuristic.CardSim], looks. */
    FULL,
    ;

    companion object {
        fun of(code: Int): PolicyMode = entries[code.coerceIn(0, entries.lastIndex)]
    }
}

/**
 * The hybrid search's knobs (prototype, opt-in; not wired into `AiPlayers`). A tournament spec
 * `x10+K=8+hz=1+leaf=0` sets them by name ([fromKnobs]); every other `name=value` is passed to the
 * book as a [dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile.tune] knob.
 */
data class HybridConfig(
    /** Determinizations per card play. */
    val samples: Int = 8,
    /** Determinizations per other searched decision. */
    val otherSamples: Int = 4,
    /** Candidates for a non-card decision: the book's best this many by static score, plus its own answer. */
    val top: Int = 4,
    val horizon: HybridHorizon = HybridHorizon.TURN,
    val otherHorizon: HybridHorizon = HybridHorizon.TURN,
    val leaf: HybridLeaf = HybridLeaf.HEVAL,
    /** Our own team's playout policy. */
    val ownPolicy: PolicyMode = PolicyMode.STATIC,
    /** The enemy team's playout policy (the opponent model inside a playout). */
    val enemyPolicy: PolicyMode = PolicyMode.STATIC,
    /** 1: card plays only (every other answer is h10's); 2: card plays and the hero's own action choices; 3: all. */
    val scope: Int = 1,
    /** The unrevealed enemy card: 0 uniform, 1 softmax of the book's static card scores, 2 of its card looks. */
    val model: Int = 1,
    /** Softmax temperature of the opponent model, coin-equivalents. */
    val tau: Double = 2.0,
    /** Weight of the book's own card value in the final card-play value (0 = search only). */
    val prior: Double = 0.0,
    /** mean − risk · sd over the samples. */
    val risk: Double = 0.0,
    /** Safety cap on one playout's answers. */
    val maxSteps: Int = 600,
) {
    companion object {
        /** Knob names [fromKnobs] reads; the rest go to the book. */
        val NAMES: Set<String> = setOf(
            "K", "Ko", "top", "hz", "hzo", "leaf", "pol", "polE", "scope", "model", "tau", "prior", "risk",
        )

        fun fromKnobs(k: Map<String, Double>): HybridConfig {
            val d = HybridConfig()
            val pol = k["pol"]?.toInt()?.let(PolicyMode::of)
            return HybridConfig(
                samples = k["K"]?.toInt() ?: d.samples,
                otherSamples = k["Ko"]?.toInt() ?: d.otherSamples,
                top = k["top"]?.toInt() ?: d.top,
                horizon = k["hz"]?.toInt()?.let(HybridHorizon::of) ?: d.horizon,
                otherHorizon = k["hzo"]?.toInt()?.let(HybridHorizon::of) ?: d.otherHorizon,
                leaf = k["leaf"]?.toInt()?.let(HybridLeaf::of) ?: d.leaf,
                ownPolicy = pol ?: d.ownPolicy,
                enemyPolicy = k["polE"]?.toInt()?.let(PolicyMode::of) ?: pol ?: d.enemyPolicy,
                scope = k["scope"]?.toInt() ?: d.scope,
                model = k["model"]?.toInt() ?: d.model,
                tau = k["tau"] ?: d.tau,
                prior = k["prior"] ?: d.prior,
                risk = k["risk"] ?: d.risk,
            )
        }
    }
}
