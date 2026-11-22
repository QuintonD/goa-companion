package dev.quinton.goa.core.game.ai.heuristic

import kotlin.concurrent.Volatile

/**
 * The named terms of [HEval] (HEVAL.md). Each is a hand-written feature in its own natural unit,
 * Red minus Blue; the evaluation is `Σ wᵢ · fᵢ` in coin-equivalents. The non-linear parts (the lane's
 * win probability, the Life ladder, the threat map) live inside the features.
 *
 * [hand] is the weight the book was written and measured with: with it every term adds exactly what
 * the evaluation added before the terms were split. A term new in wave 3 has hand weight 0.
 */
enum class HTerm(val label: String, val hand: Double) {
    /** `60 × (2W − 1)` per lane: W is P(Red wins the lane) from the push recursion. */
    LANE("lane", 1.4),

    /** The extra `40 × (2W − 1)` of a lane at match point (100 in place of 60). */
    LANE_MATCH("laneMatch", 1.4),

    /** Minions on the lane, Red minus Blue, 1.5 each (3 at match point). */
    MARGIN("margin", 1.0),

    /** The Life ladder (`HContext.lifeLadder`). */
    LIFE("life", 1.0),

    /** 1 for a team one defeat from losing on Life (Blue's minus Red's). */
    LIFE_DANGER("lifeDanger", 0.0),

    /** Set-up pieces on the board (the best four per owner). */
    PIECES("pieces", 1.0),

    /** Coins (×1.3 in the opening rounds). */
    COINS("coins", 1.0),

    /** Levels: the cumulative cost plus 3 a level, a level due, the Ultimate. */
    LEVELS("levels", 1.0),

    /** Hero counters (Gydion's spellbook, wishes). */
    COUNTERS("counters", 1.0),

    /** The hand: 2.5 per needed card, the spare by turn, −3 per missing card. */
    HAND("hand", 1.0),

    /** 0.12 × the card ratings of a hand this team may read. */
    HAND_RATING("handRating", 1.0),

    /** Heroes off the board. */
    OFF_BOARD("offBoard", -8.0),

    /** Lane presence: +1 in the Battle Zone, −0.55 a space outside. */
    PRESENCE("presence", 1.0),

    /** Attack cards in hand that reach an enemy minion (1.6 each, capped at 4.8). */
    OPPORTUNITY("opportunity", 2.0),

    /** A minion in reach of a card still to play, priced by what its kill does to the lane (share 1). */
    LANE_THREAT("laneThreat", HEval.LANE_THREAT),

    /** The pull toward the zone the hero should stand in (+2.5 there, −0.7 a space). */
    ZONE_PULL("zonePull", 1.0),

    /** Expected loss from the threat map (positive; the weight is negative). */
    EXPOSURE("exposure", -1.0),

    /** The playbooks' `evalTerms`. */
    PLAYBOOK("playbook", 1.0),

    /**
     * Expected attack plays that reach an enemy minion: pool weights for a hidden hand, at most the
     * plays left this round (REVIEW-2-design §2.2.4).
     */
    OPP_POOL("oppPool", 0.0),

    /** Minus the actions a hero needs to reach its zone, Fast Travel and respawn included (#13). */
    ACCESS("access", 0.0),

    /**
     * The lane from one projected minion battle (#9, #14): both sides' expected minion kills this round
     * from reachable attack cards, the exact a ≥ 2b push and carry-over at the round's end, and the next
     * push's chance from the new zone's spawns. `(60 or 100) × (2W − 1)`.
     */
    LANE_PROJ("laneProj", 0.0),
    ;

    companion object {
        val byLabel: Map<String, HTerm> = entries.associateBy { it.label }
    }
}

/**
 * A set of [HTerm] weights plus the scale on the book's minion price (`laneKillDelta`, which the lane
 * term's weight does not reach). Selected through the profile's knobs (HEVAL.md "Knobs"):
 * `hev` picks the set for the book's own evaluations, `hevLeaf` the one a search leaf uses (default:
 * `hev`), and `hw.<label>` / `laneDelta` override single weights for a measurement.
 */
class HEvalWeights(val name: String, val w: DoubleArray, val laneDelta: Double) {
    init {
        require(w.size == HTerm.entries.size) { "HEvalWeights $name: ${w.size} weights for ${HTerm.entries.size} terms" }
    }

    operator fun get(t: HTerm): Double = w[t.ordinal]

    fun with(t: HTerm, value: Double): HEvalWeights =
        HEvalWeights(name, w.copyOf().also { it[t.ordinal] = value }, laneDelta)

    companion object {
        /** The weights the book was measured with before wave 3 (the default behaviour). */
        val HAND: HEvalWeights = HEvalWeights("hand", DoubleArray(HTerm.entries.size) { HTerm.entries[it].hand }, 1.0)

        /** Weight sets by number (`hev=1`). 0 is [HAND]. */
        val SETS: List<HEvalWeights> = listOf(HAND)

        /** The set the book uses when no knob says otherwise. */
        const val DEFAULT_SET: Int = 0

        /** The set a search leaf uses when no knob says otherwise. */
        const val DEFAULT_LEAF_SET: Int = 0

        @Volatile
        private var cache: Triple<HeuristicProfile, Boolean, HEvalWeights>? = null

        /** The weights [profile] evaluates with; [leaf] for a search's leaf. */
        fun of(profile: HeuristicProfile, leaf: Boolean = false): HEvalWeights {
            cache?.let { if (it.first === profile && it.second == leaf) return it.third }
            val r = resolve(profile, leaf)
            cache = Triple(profile, leaf, r)
            return r
        }

        private fun resolve(profile: HeuristicProfile, leaf: Boolean): HEvalWeights {
            val book = profile.knob("hev", DEFAULT_SET.toDouble()).toInt()
            val idx = if (leaf) profile.knob("hevLeaf", if ("hev" in profile.tune) book.toDouble() else DEFAULT_LEAF_SET.toDouble()).toInt() else book
            val base = SETS[idx.coerceIn(0, SETS.lastIndex)]
            if (profile.tune.isEmpty()) return base
            val w = base.w.copyOf()
            // The knobs measured before wave 3 keep their meaning: `lane` is the lane's scale, `opp`
            // multiplies the opportunity term's 2.0, `oppLane` is the lane-threat share.
            profile.tune["lane"]?.let {
                w[HTerm.LANE.ordinal] = it
                w[HTerm.LANE_MATCH.ordinal] = it
            }
            profile.tune["opp"]?.let { w[HTerm.OPPORTUNITY.ordinal] = base[HTerm.OPPORTUNITY] * it }
            profile.tune["oppLane"]?.let { w[HTerm.LANE_THREAT.ordinal] = it }
            for ((k, v) in profile.tune) {
                if (!k.startsWith("hw.")) continue
                val t = HTerm.byLabel[k.removePrefix("hw.")] ?: error("No HEval term ${k.removePrefix("hw.")}")
                w[t.ordinal] = v
            }
            return HEvalWeights(base.name, w, profile.knob("laneDelta", base.laneDelta))
        }
    }
}
