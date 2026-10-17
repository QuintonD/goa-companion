package dev.quinton.goa.core.domain.setup

import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random

/** Why a mode can't be chosen for the current number of heroes. */
enum class ModeBlock {
    /** Too few heroes for this mode (e.g. Double lane below 6). */
    TOO_FEW_HEROES,

    /** Another mode is required at this size (Double lane from 8 heroes). */
    OTHER_MODE_REQUIRED,
}

/** One row of the mode picker, with the counters to lay out if chosen. */
data class ModeOption(
    val mode: GameMode,
    val block: ModeBlock?,
    /** True when this is the only mode the rules allow at this size. */
    val required: Boolean,
    val lanes: Int,
    val waveCountersPerLane: Int,
    /** Life counters per team; null when the rules don't list a value for this many heroes. */
    val lifeCountersPerTeam: Int?,
) {
    val available: Boolean get() = block == null
    val totalWaveCounters: Int get() = lanes * waveCountersPerLane

    /** Allowed, and the rules list both its Wave and Life counters for this many heroes. */
    val fullySpecified: Boolean get() = available && waveCountersPerLane > 0 && lifeCountersPerTeam != null
}

/** Everything the setup screen shows for a player count, layout and mode. */
data class SetupPlan(
    val players: Int,
    val layout: SeatLayout,
    val modes: List<ModeOption>,
    val selected: ModeOption,
) {
    val heroes: Int get() = layout.heroes
}

/**
 * Where counting by heroes in play (the app's house rule, DOMAIN.md) gives the selected mode
 * different numbers than the rulebook's per-player rules. Null from
 * [MatchSetup.houseRuleDifference] when they agree.
 */
data class HouseRuleDifference(
    val players: Int,
    val heroes: Int,
    val mode: GameMode,
    /** The rulebook's Life counters per team for [players], by mode; modes it doesn't list are absent. */
    val rulebookLife: Map<GameMode, Int>,
    /** The selected mode's Life counters differ from the rulebook's for [players]. */
    val lifeDiffers: Boolean,
    /** The rulebook doesn't list the selected mode for [players] (Double lane below 6 players). */
    val modeNotListed: Boolean,
    /** The selected mode is required only by the hero count, not by the rulebook's player count. */
    val requiredByHouseRule: Boolean,
)

/** Pure match-setup calculations over [MatchRules] (pure Kotlin, no Android). */
class MatchSetup(private val rules: MatchRules) {
    val playerRange: IntRange get() = rules.minPlayers..rules.maxPlayers

    fun isRecommended(players: Int): Boolean = rules.playerCount(players)?.recommended == true

    fun isExtended(players: Int): Boolean = rules.playerCount(players)?.extended == true

    /** Seat layouts the rules offer for [players]; odd counts offer Handicap and Double pilot. */
    fun layouts(players: Int): List<SeatLayout> = rules.playerCount(players)?.layouts.orEmpty()

    /** Mode options for [heroes] on the board, in data order. */
    fun modeOptions(heroes: Int): List<ModeOption> {
        val required = rules.modes.firstOrNull { rule -> rule.requiredFromHeroes?.let { heroes >= it } == true }
        return rules.modes.map { rule ->
            val block = when {
                rule.minHeroes != null && heroes < rule.minHeroes -> ModeBlock.TOO_FEW_HEROES
                required != null && required.mode != rule.mode -> ModeBlock.OTHER_MODE_REQUIRED
                else -> null
            }
            ModeOption(
                mode = rule.mode,
                block = block,
                required = required?.mode == rule.mode,
                lanes = rule.lanes,
                waveCountersPerLane = rule.waveCountersPerLane,
                lifeCountersPerTeam = rule.lifeCountersFor(heroes),
            )
        }
    }

    /**
     * Builds the plan for [players] using [layoutId] (default: the first layout for the count)
     * and [mode]. Without an allowed [mode] the default is the required mode, else the first
     * allowed mode whose counters are fully specified (7 heroes: Double lane, as the rules list
     * no single-lane Life counters there), else the first allowed mode.
     */
    fun plan(players: Int, layoutId: String? = null, mode: GameMode? = null): SetupPlan {
        val layouts = layouts(players)
        require(layouts.isNotEmpty()) { "No layout for $players players" }
        val layout = layouts.firstOrNull { it.id == layoutId } ?: layouts.first()
        return planFor(players, layout, mode)
    }

    /**
     * Plan for an existing match whose seats are already known (e.g. from a draft). [mode] is an
     * explicit choice; [suggestedMode] (e.g. the mode a draft stored) is only used when its
     * counters are fully specified for these seats.
     */
    fun planForSeats(
        players: Int,
        redHeroes: Int,
        blueHeroes: Int,
        mode: GameMode? = null,
        suggestedMode: GameMode? = null,
    ): SetupPlan {
        val larger = maxOf(redHeroes, blueHeroes)
        val smaller = minOf(redHeroes, blueHeroes)
        val known = layouts(players).firstOrNull { it.largerTeamHeroes == larger && it.smallerTeamHeroes == smaller }
        val layout = known ?: SeatLayout(
            id = "custom-$redHeroes-$blueHeroes",
            kind = if (larger == smaller) LayoutKind.STANDARD else LayoutKind.HANDICAP,
            largerTeamHeroes = larger,
            smallerTeamHeroes = smaller,
            doublePilots = (redHeroes + blueHeroes - players).coerceAtLeast(0),
        )
        return planFor(players, layout, mode, suggestedMode)
    }

    private fun planFor(players: Int, layout: SeatLayout, mode: GameMode?, suggested: GameMode? = null): SetupPlan {
        val options = modeOptions(layout.heroes)
        val selected = options.firstOrNull { it.mode == mode && it.available }
            ?: options.firstOrNull { it.required }
            ?: options.firstOrNull { it.mode == suggested && it.fullySpecified }
            ?: options.firstOrNull { it.fullySpecified }
            ?: options.first { it.available }
        return SetupPlan(players = players, layout = layout, modes = options, selected = selected)
    }

    /** Compares [plan]'s selected mode with the rulebook's per-player values; null when they agree. */
    fun houseRuleDifference(plan: SetupPlan): HouseRuleDifference? {
        val players = plan.players
        val rule = rules.mode(plan.selected.mode)
        val difference = HouseRuleDifference(
            players = players,
            heroes = plan.heroes,
            mode = rule.mode,
            rulebookLife = rules.modes.mapNotNull { m -> m.rulebookLifeCountersFor(players)?.let { m.mode to it } }
                .toMap(),
            lifeDiffers = plan.selected.lifeCountersPerTeam != rule.rulebookLifeCountersFor(players),
            modeNotListed = !rule.listedFor(players),
            requiredByHouseRule = plan.selected.required && !rule.requiredFor(players),
        )
        return difference.takeIf { it.lifeDiffers || it.modeNotListed || it.requiredByHouseRule }
    }

    companion object {
        /** Fair coin for the Tie Breaker: the team shown on the coin acts first on cross-team ties. */
        fun flipTieBreaker(random: Random): Team = if (random.nextBoolean()) Team.RED else Team.BLUE
    }
}
