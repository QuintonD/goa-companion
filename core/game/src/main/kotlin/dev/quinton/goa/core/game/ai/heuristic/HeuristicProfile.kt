package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiStrength

/**
 * What a strength notices (ADR-085, DESIGN.md). The same playbook runs at every strength;
 * the profile turns layers off so a low strength makes the mistakes a new player makes.
 * Defences and lane-deciding moves never use [slipRate] (those feel broken, not human).
 */
data class HeuristicProfile(
    val strength: Int,
    /** Exposure, minion defense modifiers, and who can reach a space. Off at 1–2. */
    val threatMap: Boolean,
    /** How far one-ply lookahead goes. [Look.OFF] answers from the static scores only. */
    val lookahead: Look,
    /** Per-hero [HeroHeuristics]. Off at 1–4: everyone plays the general book. */
    val heroPlaybooks: Boolean,
    /** [TurnPlan] formed from the played card and followed for the rest of the turn. */
    val turnPlans: Boolean,
    /** Read enemy possible cards for initiative: escape before they act, strike before they leave. */
    val initiativeReading: Boolean,
    /** A strip this turn is worth more when a teammate can reach the same hero. */
    val focusFire: Boolean,
    /** Cards seen since an enemy's last level-up narrow the possible pool. */
    val cardCounting: Boolean,
    /** Hold a defender, or strip now so the kill lands next turn. */
    val twoTurnPlans: Boolean,
    /** Match point and the opening (a push already won): lane timing beats a greedy farm. */
    val laneTiming: Boolean,
    /** Strength 10 only (ADR-062): the enemies' hand pool is known; the facedown card is not. */
    val knowHands: Boolean,
    /** Strength 11 only: no determinization at all, the AI reads the true game state (every hidden card). */
    val omniscient: Boolean = false,
    /**
     * F1 (REVIEW-2-losses): after the reveal a look plays the rest of the turn, every remaining hero's
     * revealed card by its own book, and on turn 4 through the end of the round, instead of stopping at
     * the end of our own action. The hybrid search turns it off for cheaper playouts (`profileEdit`).
     */
    val turnRest: Boolean = false,
    /**
     * F2: card plays scored as this many sampled turns ([CardSim.sampled]: every other card from its book,
     * the turn resolved, [HEval] at its end). 0 is the one-action look of strengths 5–8.
     */
    val revealSamples: Int = 0,
    /**
     * Strength 10: the threat map at the card play weighs each enemy card by the enemy's own book (the
     * known pool, softmax of its card looks) instead of uniformly by colour ([CardSim.cardModel]).
     */
    val predictCards: Boolean = false,
    /**
     * Strength 10: the card play's own-action look continues through an enemy's defense or discard,
     * answered by that enemy's book from the known hand, instead of stopping and pricing it from the pool.
     */
    val knowLook: Boolean = false,
    /**
     * Inside a look (strength 7+, [Look.MOVES]): how many of this hero's own choices after the answer are
     * branched, and how many of the book's answers each branch tries ([CardSim]). Knobs `branch`, `width`.
     */
    val lookBranches: Int = 1,
    val lookWidth: Int = 4,
    /** Softmax temperature in coin-equivalents. 0 is argmax. */
    val temperature: Double,
    /** Options more than this far behind the best are never picked, temperature aside. */
    val maxGap: Double,
    /** Chance to pick among the top few instead of the best. 0 never slips. */
    val slipRate: Double,
    /** Chance a move ignores minion defense modifiers (strengths 1–3). */
    val blindRate: Double,
    /**
     * Named weights a measurement may change (`HeuristicTournamentTest`: `h10+sim=0`). Empty in play:
     * every rule reads its default through [knob].
     */
    val tune: Map<String, Double> = emptyMap(),
) {
    /** [tune]'s value for [name], or [default]. */
    fun knob(name: String, default: Double): Double = tune[name] ?: default

    /** [knob] read as a switch: on when the value is positive. */
    fun on(name: String, default: Boolean): Boolean = tune[name]?.let { it > 0.0 } ?: default

    /*
     * Knob defaults by strength, one row per track layer (read through [on]; `<master>=0` turns a whole track off).
     *
     * Track V, the team plan ([TeamStrategy]), master `plan` (default on):
     *   plan.upgrade  from 5  the job and the team's gaps choose the upgrade and the item (G145-G147); G105 from 7
     *   plan.econ     from 5  the Tier III step, a due level and the Pity coin in HEval (G148)
     *   plan.style    from 7  aggression, lane weight and keep-defender by job and Life race, team-aligned (G142-G143)
     *   plan.race     from 7  the worth of a death by the Life race (G144)
     */

    /** What one-ply lookahead is allowed to re-score. */
    enum class Look {
        /** Static scores only. */
        OFF,

        /** Choices whose text the static rules cannot read (a bullet, a token, a confirm). */
        OPAQUE,

        /** Opaque, plus the action and the attack target: the engine resolves the text. */
        ACTIONS,

        /** Actions, and a close movement when the static scores disagree by little. */
        MOVES,
    }

    companion object {
        /** The strength the apps seat, coerced into 1..10. */
        fun of(strength: Int): HeuristicProfile = table(AiStrength.coerce(strength))

        @Suppress("LongMethod")
        private fun table(s: Int): HeuristicProfile = when (s) {
            1 -> row(s, threat = false, look = Look.OFF, temp = 2.4, gap = 8.0, slip = 0.25, blind = 0.55)

            2 -> row(s, threat = false, look = Look.OFF, temp = 1.6, gap = 5.0, slip = 0.16, blind = 0.35)

            3 -> row(s, threat = true, look = Look.OPAQUE, temp = 1.1, gap = 4.0, slip = 0.12, blind = 0.10)

            4 -> row(s, threat = true, look = Look.OPAQUE, temp = 0.7, gap = 2.5, slip = 0.08, blind = 0.0)

            5 -> row(
                s,
                threat = true,
                look = Look.ACTIONS,
                heroes = true,
                plans = true,
                temp = 0.45,
                gap = 2.0,
                slip = 0.05,
            )

            6 -> row(
                s,
                threat = true,
                look = Look.ACTIONS,
                heroes = true,
                plans = true,
                temp = 0.28,
                gap = 1.4,
                slip = 0.03,
            )

            7 -> row(
                s,
                threat = true,
                look = Look.MOVES,
                heroes = true,
                plans = true,
                initiative = true,
                focus = true,
                counting = true,
                temp = 0.12,
                gap = 0.7,
                slip = 0.0,
            )

            8 -> row(
                s,
                threat = true,
                look = Look.MOVES,
                heroes = true,
                plans = true,
                initiative = true,
                focus = true,
                counting = true,
                temp = 0.05,
                gap = 0.4,
                slip = 0.0,
            )

            9 -> row(
                s,
                threat = true,
                look = Look.MOVES,
                heroes = true,
                plans = true,
                initiative = true,
                focus = true,
                counting = true,
                twoTurn = true,
                lane = true,
                rest = true,
                // Measured: 0.10 here cost 11 points against the old s8 (looks tie often; a tie became a coin).
                temp = 0.0,
                gap = 0.0,
                slip = 0.0,
            )

            else -> row(
                s,
                threat = true,
                look = Look.MOVES,
                heroes = true,
                plans = true,
                initiative = true,
                focus = true,
                counting = true,
                twoTurn = true,
                lane = true,
                hands = true,
                rest = true,
                temp = 0.0,
                gap = 0.0,
                slip = 0.0,
            ).copy(omniscient = s >= AiStrength.ALL_SEEING)
        }

        @Suppress("LongParameterList")
        private fun row(
            strength: Int,
            threat: Boolean,
            look: Look,
            heroes: Boolean = false,
            plans: Boolean = false,
            initiative: Boolean = false,
            focus: Boolean = false,
            counting: Boolean = false,
            twoTurn: Boolean = false,
            lane: Boolean = false,
            hands: Boolean = false,
            rest: Boolean = false,
            temp: Double,
            gap: Double,
            slip: Double,
            blind: Double = 0.0,
        ) = HeuristicProfile(
            strength = strength,
            threatMap = threat,
            lookahead = look,
            heroPlaybooks = heroes,
            turnPlans = plans,
            initiativeReading = initiative,
            focusFire = focus,
            cardCounting = counting,
            twoTurnPlans = twoTurn,
            laneTiming = lane,
            knowHands = hands,
            turnRest = rest,
            // Strength 10 (wave 3): enemy defenses answered from the known hand inside the card look, and
            // two own choices branched in a look (h10 : h9 51.0% without, 55.7% / 55.2% with each).
            knowLook = hands,
            lookBranches = if (hands) 2 else 1,
            temperature = temp,
            maxGap = gap,
            slipRate = slip,
            blindRate = blind,
        )
    }
}
