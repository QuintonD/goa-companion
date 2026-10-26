package dev.quinton.goa.feature.timer.ui.setup

import dev.quinton.goa.core.domain.match.Rematch
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.setup.HouseRuleDifference
import dev.quinton.goa.core.domain.setup.LayoutKind
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.setup.MatchSetup
import dev.quinton.goa.core.domain.setup.ModeBlock
import dev.quinton.goa.core.domain.setup.ModeOption
import dev.quinton.goa.core.domain.setup.RulesNote
import dev.quinton.goa.core.domain.setup.SeatLayout
import dev.quinton.goa.core.domain.setup.SetupPlan
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.ui.timer.PresetOption

data class LayoutOptionUi(
    val id: String,
    val kind: LayoutKind,
    val title: String,
    val teams: String,
    val guidance: String?,
    val official: Boolean,
)

data class ModeOptionUi(
    val mode: GameMode,
    val title: String,
    val available: Boolean,
    val required: Boolean,
    val reason: String?,
    val waves: String,
    val life: String,
    /** Badge for a required mode: "Required", or "Required (house rule)" when only the hero count requires it. */
    val requiredLabel: String? = null,
)

data class CountersUi(
    val lanes: Int,
    val wavesPerLane: Int,
    /** Life counters per team; null = not specified by the rulebook for this many heroes. */
    val lifePerTeam: Int?,
    val redHeroes: Int?,
    val blueHeroes: Int?,
    val largerTeamHeroes: Int,
    val smallerTeamHeroes: Int,
    val handicapHeroes: Int,
    val doublePilots: Int,
    /** Where counting by heroes differs from the rulebook's per-player counters (house rule label). */
    val houseRuleNote: String? = null,
    /** Rules for the chosen mode (Double lane, p.20). */
    val modeNote: RulesNote? = null,
) {
    /** "Red 3 · Blue 2 heroes · 1 Handicap card · 1 player controls 2 heroes" under the counters. */
    val seatsLine: String
        get() = buildList {
            if (redHeroes != null && blueHeroes != null) {
                add("Red $redHeroes · Blue $blueHeroes heroes")
            } else {
                add("$largerTeamHeroes vs $smallerTeamHeroes heroes")
            }
            if (handicapHeroes >
                0
            ) {
                add(if (handicapHeroes == 1) "1 Handicap card" else "$handicapHeroes Handicap cards")
            }
            if (doublePilots > 0) {
                add(
                    if (doublePilots ==
                        1
                    ) {
                        "1 player controls 2 heroes"
                    } else {
                        "$doublePilots players control 2 heroes each"
                    },
                )
            }
        }.joinToString(" · ")

    /** Said instead of Life counters when the rulebook gives none for this many heroes. */
    val lifeNotSpecified: String
        get() = "Not specified by the rulebook for ${largerTeamHeroes + smallerTeamHeroes} heroes in a single lane. " +
            "Consider Double lane or check the rulebook."
}

/** Where the Tie Breaker coin shown at setup comes from. */
enum class CoinSource {
    /** Not flipped yet: flip it now. */
    NONE,

    /** Flipped during the draft that created this match; leave it as it is. */
    DRAFT,

    /** Recorded on this match at an earlier setup; leave it as it is. */
    MATCH,

    /** Flipped or set on this screen. */
    HERE,
}

data class LinkedMatchUi(val title: String, val redSeats: Int, val blueSeats: Int)

/**
 * A rematch of [sourceId] (DOMAIN.md *Rematch*). Nothing is saved until Start timer: backing out
 * of setup leaves no pending match behind.
 */
data class RematchRequest(val sourceId: String, val kind: RematchKind)

data class MatchSetupUiState(
    val loading: Boolean = true,
    val players: Int = 4,
    val playerRange: IntRange = 2..10,
    val playersLocked: Boolean = false,
    /** Where locked seats come from: "From the drafted match", "Rematch: same seats", … */
    val seatsSource: String? = null,
    val recommended: Boolean = false,
    val extendedNote: String? = null,
    val layouts: List<LayoutOptionUi> = emptyList(),
    val selectedLayoutId: String? = null,
    val modes: List<ModeOptionUi> = emptyList(),
    val selectedMode: GameMode = GameMode.QUICK,
    val counters: CountersUi? = null,
    val tieBreaker: Team? = null,
    val coinSource: CoinSource = CoinSource.NONE,
    /** The coin was already placed (draft or earlier setup): offer a correction, not a new flip. */
    val coinPlaced: Boolean = false,
    /** "Correct the coin" was tapped: show the Red/Blue choice. */
    val correctingCoin: Boolean = false,
    val flips: Int = 0,
    val tieBreakerRule: List<String> = emptyList(),
    val firstGameAdvice: String = "",
    val linkedMatch: LinkedMatchUi? = null,
    val presets: List<PresetOption> = emptyList(),
    val selectedPresetId: String? = null,
    val replacesRunningTimer: Boolean = false,
) {
    /** Heroes at the table: the Action timer's slots. */
    val heroes: Int get() = counters?.let { it.largerTeamHeroes + it.smallerTeamHeroes } ?: players

    /** The line under the coin: what to do with it. */
    val coinMessage: String
        get() {
            val coin = tieBreaker ?: return "Flip the coin and place it, result side up, by the board."
            val side = if (coin == Team.RED) "Red" else "Blue"
            return when (coinSource) {
                CoinSource.DRAFT -> "$side is already showing from the draft. Leave the coin as it is."

                CoinSource.MATCH -> "$side is showing, as recorded for this match. Leave the coin as it is."

                CoinSource.HERE, CoinSource.NONE ->
                    if (coinPlaced) {
                        "$side is showing (corrected)."
                    } else {
                        "$side is showing. Place the coin by the board."
                    }
            }
        }

    /** "Recommended count", the seats' origin, or the supported range. */
    val playersHint: String
        get() = when {
            seatsSource != null -> seatsSource
            recommended -> "Recommended count"
            else -> "Supported: ${playerRange.first} to ${playerRange.last}"
        }
}

/** What the person chose on the Match setup screen (everything else is derived). */
data class MatchSetupChoices(
    val players: Int? = null,
    val layoutId: String? = null,
    val mode: GameMode? = null,
    val tieBreaker: Team? = null,
    val flips: Int = 0,
    val correctingCoin: Boolean = false,
    val presetId: String? = null,
) {
    fun withPlayers(count: Int) = copy(players = count, layoutId = null)

    /** A coin flipped on this screen. */
    fun flipped(team: Team) = copy(tieBreaker = team, flips = flips + 1)

    /**
     * The side the real coin shows (a correction, or a coin flipped at the table). Picking the side
     * the placed coin ([placed]) already shows is no correction: it keeps (or returns to) that coin.
     */
    fun coinSet(team: Team, placed: Team?) = copy(tieBreaker = team.takeIf { it != placed })
}

/**
 * Builds Match setup from the rules data, the linked (pending, drafted or rematch) match and the
 * person's choices. Pure, shared by the phone's `MatchSetupViewModel` and the web app (ADR-043
 * "Table, W3"): every count comes from `match-setup.json` through [MatchSetup].
 */
object MatchSetupPresenter {
    const val DEFAULT_PLAYERS = 4

    /** The match a rematch request builds (saved only when its timer starts). */
    fun rematch(source: Match, request: RematchRequest, id: String, at: Long): Match? =
        Rematch.create(source, request.kind, id, at).getOrNull()

    fun build(
        rules: MatchRules,
        match: Match?,
        choice: MatchSetupChoices,
        presets: List<PresetOption>,
        selectedPresetId: String,
        rematch: RematchRequest? = null,
    ): MatchSetupUiState {
        val setup = MatchSetup(rules)
        val seats = match?.participants.orEmpty()
        val locked = seats.isNotEmpty()
        val red = seats.count { it.team == Team.RED }
        val blue = seats.count { it.team == Team.BLUE }
        val players = when {
            locked -> match!!.playerIds.size.coerceIn(setup.playerRange)
            else -> (choice.players ?: DEFAULT_PLAYERS).coerceIn(setup.playerRange)
        }
        val plan: SetupPlan = if (locked) {
            // The stored mode (e.g. a draft's default) is kept only if its counters are listed.
            setup.planForSeats(players, red, blue, mode = choice.mode, suggestedMode = match!!.mode)
        } else {
            setup.plan(players, choice.layoutId, choice.mode)
        }
        val layouts = if (locked) listOf(plan.layout) else setup.layouts(players)
        val storedCoin = match?.tieBreaker
        val quickAvailable = plan.modes.any { it.mode == GameMode.QUICK && it.available }
        return MatchSetupUiState(
            loading = false,
            players = players,
            playerRange = setup.playerRange,
            playersLocked = locked,
            seatsSource = if (locked) seatsSource(match, rematch) else null,
            recommended = setup.isRecommended(players),
            extendedNote = if (setup.isExtended(players)) rules.extendedCountNote else null,
            layouts = layouts.map(::layoutOption),
            selectedLayoutId = plan.layout.id,
            modes = plan.modes.map { modeOption(it, rules, players, plan.heroes) },
            selectedMode = plan.selected.mode,
            counters = CountersUi(
                lanes = plan.selected.lanes,
                wavesPerLane = plan.selected.waveCountersPerLane,
                lifePerTeam = plan.selected.lifeCountersPerTeam,
                redHeroes = if (locked) red else null,
                blueHeroes = if (locked) blue else null,
                largerTeamHeroes = plan.layout.largerTeamHeroes,
                smallerTeamHeroes = plan.layout.smallerTeamHeroes,
                handicapHeroes = if (locked) seats.count { it.handicap } else plan.layout.handicapHeroes,
                doublePilots = plan.layout.doublePilots,
                houseRuleNote = houseRuleNote(setup.houseRuleDifference(plan), rules),
                modeNote = rules.mode(plan.selected.mode).rulesNote,
            ),
            tieBreaker = choice.tieBreaker ?: storedCoin,
            coinSource = when {
                choice.tieBreaker != null -> CoinSource.HERE
                storedCoin != null && match.draftId != null -> CoinSource.DRAFT
                storedCoin != null -> CoinSource.MATCH
                else -> CoinSource.NONE
            },
            coinPlaced = storedCoin != null,
            correctingCoin = choice.correctingCoin,
            flips = choice.flips,
            tieBreakerRule = rules.tieBreaker,
            // The rulebook's "Quick game if anyone is new" (p.5) only applies when Quick can be chosen.
            firstGameAdvice = if (quickAvailable) rules.firstGameAdvice else "",
            linkedMatch = match?.let {
                LinkedMatchUi(
                    title = linkedTitle(seats.size, rematch),
                    redSeats = red,
                    blueSeats = blue,
                )
            },
            presets = presets,
            selectedPresetId = selectedPresetId,
        )
    }

    /**
     * What Start timer saves to the linked match: its mode and the coin as placed at setup (the
     * draft's coin, or corrected here). Null when nothing needs saving ([isNew]: a rematch, saved
     * only now that the game actually starts).
     */
    fun matchToSave(linked: Match, state: MatchSetupUiState, isNew: Boolean): Match? {
        val updated = linked.copy(mode = state.selectedMode, tieBreaker = state.tieBreaker ?: linked.tieBreaker)
        return updated.takeIf { it != linked || isNew }
    }

    /** Where locked seats come from (the players card says it instead of the player count range). */
    private fun seatsSource(match: Match?, rematch: RematchRequest?): String = when {
        rematch != null -> when (rematch.kind) {
            RematchKind.SAME_SEATS -> "Rematch: same seats"
            RematchKind.SWAP_HEROES -> "Rematch: heroes swapped"
            RematchKind.SWAP_TEAMS -> "Rematch: sides swapped"
        }

        match?.draftId != null -> "From the drafted match"

        else -> "From the pending match"
    }

    private fun linkedTitle(heroes: Int, rematch: RematchRequest?): String = when {
        rematch != null -> "Rematch · $heroes heroes"
        heroes > 0 -> "Pending match · $heroes heroes"
        else -> "Pending match"
    }

    private fun layoutOption(layout: SeatLayout) = LayoutOptionUi(
        id = layout.id,
        kind = layout.kind,
        title = when (layout.kind) {
            LayoutKind.STANDARD -> "Even teams"
            LayoutKind.RULEBOOK -> "Rulebook setup"
            LayoutKind.HANDICAP -> "Handicap"
            LayoutKind.DOUBLE_PILOT -> "Double pilot"
        },
        teams = "${layout.largerTeamHeroes} vs ${layout.smallerTeamHeroes} heroes",
        guidance = layout.guidance,
        official = layout.kind != LayoutKind.DOUBLE_PILOT,
    )

    private fun modeOption(option: ModeOption, rules: MatchRules, players: Int, heroes: Int) = ModeOptionUi(
        mode = option.mode,
        title = TimerText.modeLabel(option.mode),
        available = option.available,
        required = option.required,
        requiredLabel = when {
            !option.required -> null
            rules.mode(option.mode).requiredFor(players) -> "Required"
            else -> "Required (house rule)"
        },
        reason = when (option.block) {
            ModeBlock.TOO_FEW_HEROES -> {
                val rule = rules.mode(option.mode)
                rule.listedFromPlayers?.let {
                    "The rulebook lists ${TimerText.modeLabel(option.mode)} counters from $it players"
                } ?: "Needs ${rule.minHeroes} or more heroes"
            }

            ModeBlock.OTHER_MODE_REQUIRED -> requiredElsewhere(rules, players, heroes)

            null -> null
        },
        waves = if (option.lanes > 1) {
            "${option.lanes} × ${option.waveCountersPerLane} Wave counters"
        } else {
            "${option.waveCountersPerLane} Wave counters"
        },
        life = option.lifeCountersPerTeam?.let { "$it Life counters per team" }
            ?: "Life counters not specified by the rulebook",
    )

    /** Why a mode is blocked because another is required: the rulebook's rule, or the heroes-count house rule. */
    private fun requiredElsewhere(rules: MatchRules, players: Int, heroes: Int): String {
        val required = rules.modes.first { rule -> rule.requiredFromHeroes?.let { heroes >= it } == true }
        val map = if (required.lanes > 1) "the double-lane map" else TimerText.modeLabel(required.mode)
        val from = required.requiredFromPlayers ?: required.requiredFromHeroes
        return if (required.requiredFor(players)) {
            "The rulebook uses $map from $from players"
        } else {
            "House rule: $heroes heroes play on $map. The rulebook requires it from $from players"
        }
    }

    /**
     * "House rule: counted as 6 heroes. The rulebook gives 5 players 4 (Quick) / 6 (Long) Life
     * counters." Null when the rulebook's per-player counters agree (a house-rule requirement is
     * labelled on the mode cards instead).
     */
    private fun houseRuleNote(difference: HouseRuleDifference?, rules: MatchRules): String? {
        val d = difference?.takeIf { it.lifeDiffers || it.modeNotListed } ?: return null
        val life = d.rulebookLife.entries.joinToString(" / ") { (mode, count) ->
            "$count (${TimerText.modeLabel(mode)})"
        }
        return buildList {
            add("House rule: counted as ${d.heroes} heroes.")
            if (d.lifeDiffers && life.isNotEmpty()) add("The rulebook gives ${d.players} players $life Life counters.")
            if (d.modeNotListed) {
                val from = rules.mode(d.mode).listedFromPlayers
                add("The rulebook lists ${TimerText.modeLabel(d.mode)} counters from $from players.")
            }
        }.joinToString(" ")
    }
}
