package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.DecisionKind

/**
 * How far a hybrid playout runs (REVIEW-2-search.md). Every horizon also stops at the end of the game.
 */
enum class HybridHorizon(
    /** Turn ends a playout runs through (0: it stops on something else). */
    val turns: Int,
) {
    /** The card's (or the current) action only. */
    ACTION(0),

    /** The end of this turn (after turn 4: through the end of the round). */
    TURN(1),

    /** The end of the next turn. */
    TWO_TURNS(2),

    /** The end of this round: the new round's first card play is the leaf. */
    ROUND(0),

    /** The end of this round and the next round's first turn. */
    ROUND_PLUS(0),
    ;

    /** A playout to this horizon from a position is the same from any start in its turn (the memo). */
    val memoable: Boolean get() = turns > 0

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
    /** Samples that deal the same position are played once, with their weight (same answers, less work). */
    val dedupe: Boolean = true,
    /** The transposition memo: a playout's value is remembered for every position it passed, one real turn. */
    val memo: Boolean = false,
    /** The kill extension: 0 off, 1 a playout runs on through a next-turn team kill of either side, 2 ours only. */
    val kill: Int = 0,
    /** Card plays: cards the book's card look scores more than this below its best are not searched (0 = off; 3 kept). */
    val prune: Double = 0.0,
    /** Card-play horizon on a given turn of the round (1–4), overriding [horizon]. */
    val playHorizonByTurn: Map<Int, HybridHorizon> = emptyMap(),
    /** The other decisions' horizon on a given turn, overriding [otherHorizon]. */
    val otherHorizonByTurn: Map<Int, HybridHorizon> = emptyMap(),
    /** Candidates for a space (movement end, push, placement), else [top]. */
    val topSpace: Int? = null,
    /** Candidates for a unit (attack target and other units), else [top]. */
    val topUnit: Int? = null,
    /**
     * Enemy replies at a card play: each sample is played this many times while an enemy still has to
     * choose its card, once with the book's own reply and the rest with the enemy's card drawn from the
     * opponent model ([replyModel], [replyTau]), so the search is not tuned to one deterministic reply.
     */
    val replies: Int = 1,
    /** The drawn replies' model: 1 the book's static card scores, 2 its card looks. */
    val replyModel: Int = 1,
    /** Softmax temperature of the drawn replies, coin-equivalents. */
    val replyTau: Double = 2.0,
    /**
     * The leaf averaged over the playout: the leaf at every turn end it passes and at its end, equally
     * weighted (a longer horizon judged by several positions, not the last one). Off for the memo.
     */
    val leafAverage: Boolean = false,
    /**
     * Robustness at card plays with several enemy replies ([replies]): a sample's value is
     * (1 − this) × its mean over the replies + this × its worst reply.
     */
    val worstReply: Double = 0.0,
    /**
     * The tree after the reveal (Wave 3 Track C): at a searched question that is not a card play, each
     * candidate is followed by a minimax over the rest of the turn this many choices deep (0 = off: one
     * book playout). A choice is a later question with more than one option, ours (max) or the enemy's
     * (min, [treeEnemy]); forced answers are free. Past the depth, or at a node the budget no longer
     * covers, the book plays out to the horizon and the leaf scores it.
     */
    val treeDepth: Int = 0,
    /** Options per tree node: the book's own answer plus its best by static score, this many in all. */
    val treeWidth: Int = 3,
    /** Enemy nodes in the tree: 1 the enemy takes its worst-for-us option (minimax), 0 the book answers. */
    val treeEnemy: Int = 1,
    /** Tree nodes, ours or theirs, of these kinds only (empty = every kind). */
    val treeKinds: Set<DecisionKind> = emptySet(),
) {
    /** How many of the book's best by static score are searched at [kind] (beside its own answer). */
    fun topFor(kind: DecisionKind): Int = when (kind) {
        DecisionKind.CHOOSE_SPACE -> topSpace ?: top
        DecisionKind.CHOOSE_UNIT -> topUnit ?: top
        else -> top
    }

    companion object {
        private val DEFENSE_KINDS = setOf(DecisionKind.DEFEND, DecisionKind.CHOOSE_CARD)

        /**
         * `tk` as bits: 1 spaces (moves, pushes, placements), 2 units (targets), 4 defenses and discards
         * (the card a hero gives up), 8 actions; 0 every kind.
         */
        fun treeKindsOf(bits: Int): Set<DecisionKind> {
            if (bits == 0) return emptySet()
            val out = HashSet<DecisionKind>()
            if (bits and 1 != 0) out += DecisionKind.CHOOSE_SPACE
            if (bits and 2 != 0) out += DecisionKind.CHOOSE_UNIT
            if (bits and 4 != 0) out += DEFENSE_KINDS
            if (bits and 8 != 0) out += DecisionKind.CHOOSE_ACTION
            return out
        }

        /** Knob names [fromKnobs] reads; the rest go to the book. */
        val NAMES: Set<String> = setOf(
            "K", "Ko", "top", "hz", "hzo", "leaf", "pol", "polE", "scope", "model", "tau", "prior", "risk",
            "dd", "tt", "kill", "prune", "hz1", "hz2", "hz3", "hz4", "hzo1", "hzo2", "hzo3", "hzo4", "topS", "topU",
            "rep", "repM", "repT", "lavg", "mm",
            "td", "tw", "te", "tk",
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
                dedupe = k["dd"]?.let { it != 0.0 } ?: d.dedupe,
                memo = k["tt"]?.let { it != 0.0 } ?: d.memo,
                kill = k["kill"]?.toInt() ?: d.kill,
                prune = k["prune"] ?: d.prune,
                playHorizonByTurn = (1..4).mapNotNull { t -> k["hz$t"]?.let { t to HybridHorizon.of(it.toInt()) } }.toMap(),
                otherHorizonByTurn = (1..4).mapNotNull { t -> k["hzo$t"]?.let { t to HybridHorizon.of(it.toInt()) } }.toMap(),
                topSpace = k["topS"]?.toInt(),
                topUnit = k["topU"]?.toInt(),
                replies = k["rep"]?.toInt() ?: d.replies,
                replyModel = k["repM"]?.toInt() ?: d.replyModel,
                replyTau = k["repT"] ?: d.replyTau,
                leafAverage = k["lavg"]?.let { it != 0.0 } ?: d.leafAverage,
                worstReply = k["mm"] ?: d.worstReply,
                treeDepth = k["td"]?.toInt() ?: d.treeDepth,
                treeWidth = k["tw"]?.toInt() ?: d.treeWidth,
                treeEnemy = k["te"]?.toInt() ?: d.treeEnemy,
                treeKinds = k["tk"]?.let { treeKindsOf(it.toInt()) } ?: d.treeKinds,
            )
        }
    }
}
