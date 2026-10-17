package dev.quinton.goa.feature.timer.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import dev.quinton.goa.core.domain.setup.LayoutKind
import dev.quinton.goa.core.domain.setup.RulesNote
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.PhaseSpec
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.timer.ui.presets.PresetEditActions
import dev.quinton.goa.feature.timer.ui.presets.PresetEditScreen
import dev.quinton.goa.feature.timer.ui.presets.PresetEditUiState
import dev.quinton.goa.feature.timer.ui.presets.PresetsActions
import dev.quinton.goa.feature.timer.ui.presets.PresetsScreen
import dev.quinton.goa.feature.timer.ui.presets.PresetsUiState
import dev.quinton.goa.feature.timer.ui.setup.CoinSource
import dev.quinton.goa.feature.timer.ui.setup.CountersUi
import dev.quinton.goa.feature.timer.ui.setup.LayoutOptionUi
import dev.quinton.goa.feature.timer.ui.setup.LinkedMatchUi
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupScreen
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupUiState
import dev.quinton.goa.feature.timer.ui.setup.ModeOptionUi
import dev.quinton.goa.feature.timer.ui.setup.SetupActions
import dev.quinton.goa.feature.timer.ui.timer.ActiveUiState
import dev.quinton.goa.feature.timer.ui.timer.ChecklistItemUi
import dev.quinton.goa.feature.timer.ui.timer.ChecklistState
import dev.quinton.goa.feature.timer.ui.timer.PendingMatchItem
import dev.quinton.goa.feature.timer.ui.timer.PresetOption
import dev.quinton.goa.feature.timer.ui.timer.RoundPipsUi
import dev.quinton.goa.feature.timer.ui.timer.SeatUi
import dev.quinton.goa.feature.timer.ui.timer.ShotClockUi
import dev.quinton.goa.feature.timer.ui.timer.StartUiState
import dev.quinton.goa.feature.timer.ui.timer.SummaryUiState
import dev.quinton.goa.feature.timer.ui.timer.TimeOutsUi
import dev.quinton.goa.feature.timer.ui.timer.TimerActions
import dev.quinton.goa.feature.timer.ui.timer.TimerScreen
import dev.quinton.goa.feature.timer.ui.timer.TimerUiState
import dev.quinton.goa.feature.timer.ui.timer.TonightUi

/** Sample states for previews and screenshot tests (shapes of real data, not game data). */
object TimerPreviewData {
    val presets = listOf(
        PresetOption(
            "table-rules",
            "Table rules",
            "Pre-round strategy 1:30 · Play a card 0:30 ×4 · Resolve · Upgrade 2:00",
            false,
        ),
        PresetOption(
            "relaxed",
            "Relaxed",
            "Pre-round strategy 3:00 · Play a card 1:00 ×4 · Resolve · Upgrade 3:00",
            false,
        ),
        PresetOption("untimed", "Untimed (counters only)", "Untimed · round and turn counters", false),
    )

    val start = TimerUiState(
        loading = false,
        start = StartUiState(
            presets = presets,
            selectedPresetId = "table-rules",
            pendingMatches = listOf(PendingMatchItem("m1", "Quick · 6 heroes, 5 players", "Pending since 17 Oct 2026")),
            seats = 4,
            tonight = TonightUi(seconds = 45, presetSeconds = 30),
        ),
    )

    private val endSteps = listOf(
        "Minion Battle" to null,
        "Remove Tokens, Return Markers" to null,
        "Push the Lane" to "If applicable: flip a Wave counter.",
        "Retrieve Cards" to null,
        "Level Up" to "Mandatory: buy every level you can afford. Simultaneous.",
        "Collect a Pity Coin" to "Only if you didn't level up. It can't be spent on a level this round.",
    )

    private fun checklist(current: Int) = endSteps.mapIndexed { i, (title, detail) ->
        ChecklistItemUi(
            number = i + 1,
            title = title,
            detail = detail,
            state = when {
                i + 1 < current -> ChecklistState.DONE
                i + 1 == current -> ChecklistState.CURRENT
                else -> ChecklistState.UPCOMING
            },
            timerName = if (i == 4) "Upgrade 2:00" else null,
            id = if (i == 4) "level_up" else null,
        )
    }

    val turn = ActiveUiState(
        presetName = "Table rules",
        round = 2,
        title = "Play a card",
        context = "Turn 3 of 4",
        kind = PhaseKind.TURN,
        section = RoundSection.TURN,
        clock = "0:18",
        spokenClock = "18 seconds left",
        fraction = 0.6f,
        timed = true,
        overtime = false,
        warning = false,
        paused = false,
        pips = RoundPipsUi(hasRoundStart = true, turns = 4, section = RoundSection.TURN, currentTurn = 3),
        checklist = null,
        seats = null,
        actionSlot = null,
        actionSlots = 6,
        nextLabel = "Cards revealed",
        tieBreaker = Team.RED,
        matchClock = "14:32",
        autoAdvance = false,
        hint = "Strategy talk out loud until the reveal.",
        shotClockSeconds = 30,
    )

    val overtime = turn.copy(
        clock = "+0:07",
        spokenClock = "Overtime, 7 seconds",
        fraction = 0f,
        overtime = true,
    )

    /** Overtime with the Hurry up countdown running and team time-outs on. */
    val hurryUp = overtime.copy(
        shotClock = ShotClockUi("0:08", "Hurry up, 8 seconds left", 0.27f, over = false, warning = true),
        timeOuts = TimeOutsUi(seconds = 40, redLeft = 1, blueLeft = 0, usable = true),
    )

    val resolve = turn.copy(
        title = "Resolve",
        kind = PhaseKind.RESOLVE,
        clock = "0:42",
        spokenClock = "42 seconds elapsed",
        fraction = 1f,
        timed = false,
        hint = "Cards revealed: resolve one by one in initiative order. No strategy talk: only state preferences, " +
            "no reasons.",
        nextLabel = "Next: Turn 4",
    )

    val paused = turn.copy(paused = true, clock = "0:09", fraction = 0.3f, warning = true)

    val upgrade = turn.copy(
        hint = null,
        title = "Upgrade · Level Up",
        context = "End of round · step 5 of 6",
        kind = PhaseKind.UPGRADE,
        section = RoundSection.ROUND_END,
        clock = "1:24",
        spokenClock = "1 minute 24 seconds left",
        fraction = 0.7f,
        pips = RoundPipsUi(true, 4, RoundSection.ROUND_END, null),
        checklist = checklist(5),
        nextLabel = "Next: Collect a Pity Coin",
    )

    val checklistStep = upgrade.copy(
        title = "Push the Lane",
        context = "End of round · step 3 of 6",
        kind = null,
        timed = false,
        clock = "0:12",
        spokenClock = "12 seconds elapsed",
        fraction = 1f,
        checklist = checklist(3),
        nextLabel = "Next: Retrieve Cards",
    )

    /** Longest Next label of the round, for large font scales. */
    val checklistLongNext = checklistStep.copy(
        title = "Minion Battle",
        context = "End of round · step 1 of 6",
        checklist = checklist(1),
        nextLabel = "Next: Remove Tokens, Return Markers",
    )

    val action = turn.copy(
        title = "Test Hero 3 · Cleo",
        context = "Turn 3 of 4 · resolution 3 of 6",
        kind = PhaseKind.ACTION,
        hint = null,
        clock = "0:21",
        fraction = 0.7f,
        actionSlot = 2,
        seats = listOf(
            SeatUi(0, "Test Hero 1", "test-hero-1", null, Team.RED, "Ana", resolved = true, acting = false),
            SeatUi(1, "Test Hero 2", "test-hero-2", null, Team.BLUE, "Ben", resolved = true, acting = false),
            SeatUi(2, "Test Hero 3", "test-hero-3", null, Team.RED, "Cleo", resolved = false, acting = true),
            SeatUi(3, "Test Hero 4", "test-hero-4", null, Team.BLUE, "Dev", resolved = false, acting = false),
            SeatUi(4, "Test Hero 5", "test-hero-5", null, Team.RED, "Eli", resolved = false, acting = false),
            SeatUi(5, "Test Hero 6", "test-hero-6", null, Team.BLUE, "Eli", resolved = false, acting = false),
        ),
        nextLabel = "Next hero",
    )

    /** Ten heroes: every tile must be reachable above the controls. */
    val actionTen = action.copy(
        context = "Turn 3 of 4 · resolution 3 of 10",
        actionSlots = 10,
        seats = (0 until 10).map { i ->
            SeatUi(
                seat = i,
                heroName = "Test Hero ${i + 1}",
                heroId = "test-hero-${i + 1}",
                portrait = null,
                team = if (i % 2 == 0) Team.RED else Team.BLUE,
                playerName = listOf("Ana", "Ben", "Cleo", "Dev", "Eli", "Fay", "Gus", "Hal", "Ivy", "Jo")[i],
                resolved = i < 2,
                acting = i == 2,
            )
        },
    )

    val summary = TimerUiState(
        loading = false,
        summary = SummaryUiState("47 min", 47, 6, "Table rules", GameMode.QUICK, hasMatch = true),
    )

    val setup = MatchSetupUiState(
        loading = false,
        players = 5,
        extendedNote =
            "Not beginner friendly (p.20). Odd player counts and games of up to 10 players \"should only be used " +
                "if all participants have played the game at least five times\" (p.3).",
        layouts = listOf(
            LayoutOptionUi(
                "handicap-5",
                LayoutKind.HANDICAP,
                "Handicap",
                "3 vs 2 heroes",
                "Each hero on the larger team replaces one of its basic cards with its Handicap card.",
                official = true,
            ),
            LayoutOptionUi(
                "double-pilot-5",
                LayoutKind.DOUBLE_PILOT,
                "Double pilot",
                "3 vs 3 heroes",
                "House rule: one player on the smaller team controls 2 heroes, so both teams field 3.",
                official = false,
            ),
        ),
        selectedLayoutId = "double-pilot-5",
        modes = listOf(
            ModeOptionUi(GameMode.QUICK, "Quick", true, false, null, "3 Wave counters", "5 Life counters per team"),
            ModeOptionUi(GameMode.LONG, "Long", true, false, null, "5 Wave counters", "8 Life counters per team"),
            ModeOptionUi(
                GameMode.DOUBLE_LANE,
                "Double lane",
                true,
                false,
                null,
                "2 × 7 Wave counters",
                "6 Life counters per team",
            ),
        ),
        selectedMode = GameMode.QUICK,
        counters = CountersUi(
            1,
            3,
            5,
            null,
            null,
            3,
            3,
            0,
            1,
            houseRuleNote = "House rule: counted as 6 heroes. The rulebook gives 5 players 4 (Quick) / 6 (Long) " +
                "Life counters.",
        ),
        tieBreaker = Team.RED,
        coinSource = CoinSource.HERE,
        flips = 0,
        tieBreakerRule = listOf(
            "Cross-team initiative tie: one of the tied players on the team showing acts first. " +
                "After resolving their card, that player (and only that player) flips the coin to the " +
                "other side (p.7).",
            "Same-team tie: the team agrees who goes first without discussing why. Don't flip the " +
                "coin (pp.4, 7).",
            "Simultaneous Active effects whose order matters are resolved like tied initiative, " +
                "without flipping: the team showing first if the effects are on different teams, " +
                "otherwise the team decides (p.17).",
            "Minions moved back into the Battle Zone: the team showing moves its minion first, " +
                "without flipping (p.18).",
            "It also places first when minions can't respawn on a blocked Spawn point (p.8).",
        ),
        firstGameAdvice = "If anyone is new, the rulebook suggests a Quick game.",
        linkedMatch = null,
        presets = presets,
        selectedPresetId = "table-rules",
    )

    val setupLinked = setup.copy(
        players = 8,
        playersLocked = true,
        extendedNote = null,
        layouts = listOf(LayoutOptionUi("standard-8", LayoutKind.STANDARD, "Even teams", "4 vs 4 heroes", null, true)),
        selectedLayoutId = "standard-8",
        modes = listOf(
            ModeOptionUi(
                GameMode.QUICK,
                "Quick",
                false,
                false,
                "The rulebook uses the double-lane map from 8 players",
                "3 Wave counters",
                "",
            ),
            ModeOptionUi(
                GameMode.LONG,
                "Long",
                false,
                false,
                "The rulebook uses the double-lane map from 8 players",
                "5 Wave counters",
                "",
            ),
            ModeOptionUi(
                GameMode.DOUBLE_LANE,
                "Double lane",
                true,
                true,
                null,
                "2 × 7 Wave counters",
                "6 Life counters per team",
                requiredLabel = "Required",
            ),
        ),
        selectedMode = GameMode.DOUBLE_LANE,
        counters = CountersUi(2, 7, 6, 4, 4, 4, 4, 0, 0, modeNote = DOUBLE_LANE_NOTE),
        firstGameAdvice = "",
        tieBreaker = Team.BLUE,
        coinSource = CoinSource.DRAFT,
        coinPlaced = true,
        linkedMatch = LinkedMatchUi("Pending match · 8 heroes", 4, 4),
    )

    val presetsList = PresetsUiState(loading = false, presets = presets, selectedId = "table-rules")

    val editPreset = PresetEditUiState(
        loading = false,
        isNew = false,
        preset = TimerPreset(
            id = "table-rules",
            name = "Table rules",
            phases = listOf(
                PhaseSpec("strategy", "Pre-round strategy", PhaseKind.STRATEGY, PhaseScope.ROUND_START, 90),
                PhaseSpec("turn", "Play a card", PhaseKind.TURN, PhaseScope.TURN, 30),
                PhaseSpec("resolve", "Resolve", PhaseKind.RESOLVE, PhaseScope.TURN, null, cues = emptyList()),
                PhaseSpec(
                    "action",
                    "Action",
                    PhaseKind.ACTION,
                    PhaseScope.TURN,
                    30,
                    enabled = false,
                    cues = listOf(5, 0),
                ),
                PhaseSpec("upgrade", "Upgrade", PhaseKind.UPGRADE, PhaseScope.ROUND_END, 120, endStepId = "level_up"),
            ),
        ),
        endSteps = endSteps.mapIndexed { i, (title, _) ->
            EndOfRoundStep(
                listOf("minion_battle", "remove_tokens", "push_lane", "retrieve_cards", "level_up", "pity_coin")[i],
                title,
            )
        },
    )
}

@Composable
private fun Themed(dark: Boolean, content: @Composable () -> Unit) =
    GoaTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, content = content)

@Preview(name = "Timer start", heightDp = 891, widthDp = 411)
@Composable
private fun StartPreview() = Themed(true) { TimerScreen(TimerPreviewData.start, TimerActions()) }

@Preview(name = "Turn running", heightDp = 891, widthDp = 411)
@Composable
private fun TurnPreview() = Themed(true) {
    TimerScreen(TimerUiState(loading = false, active = TimerPreviewData.turn), TimerActions())
}

@Preview(name = "Overtime", heightDp = 891, widthDp = 411)
@Composable
private fun OvertimePreview() =
    Themed(true) { TimerScreen(TimerUiState(loading = false, active = TimerPreviewData.overtime), TimerActions()) }

@Preview(name = "Upgrade on Level Up (light)", heightDp = 891, widthDp = 411)
@Composable
private fun UpgradePreview() =
    Themed(false) { TimerScreen(TimerUiState(loading = false, active = TimerPreviewData.upgrade), TimerActions()) }

@Preview(name = "Action timer", heightDp = 891, widthDp = 411)
@Composable
private fun ActionPreview() =
    Themed(true) { TimerScreen(TimerUiState(loading = false, active = TimerPreviewData.action), TimerActions()) }

@Preview(name = "Match over", heightDp = 891, widthDp = 411)
@Composable
private fun SummaryPreview() = Themed(true) { TimerScreen(TimerPreviewData.summary, TimerActions()) }

@Preview(name = "Match setup", heightDp = 891, widthDp = 411)
@Composable
private fun SetupPreview() = Themed(true) { MatchSetupScreen(TimerPreviewData.setup, SetupActions()) }

@Preview(name = "Presets", heightDp = 891, widthDp = 411)
@Composable
private fun PresetsPreview() = Themed(true) { PresetsScreen(TimerPreviewData.presetsList, PresetsActions()) }

@Preview(name = "Edit preset", heightDp = 891, widthDp = 411)
@Composable
private fun EditPresetPreview() = Themed(true) { PresetEditScreen(TimerPreviewData.editPreset, PresetEditActions()) }

private val DOUBLE_LANE_NOTE = RulesNote(
    "Double lane rules (rulebook p.20)",
    listOf(
        "Considerably more challenging than a single lane: everyone should know the game " +
            "well.",
        "Each lane has its own Battle Zone, minions and Wave counters. Minions stay bound to " +
            "the Battle Zone they first spawned in, for respawning and for the \"Minions Outside " +
            "of the Battle Zone\" rules (p.18).",
        "With more than 3 heroes per team, the extra heroes start in empty spaces next to " +
            "their team's occupied hero spawn points.",
        "On cards, \"the Battle Zone\" means \"a Battle Zone\".",
        "The two Minion Battles are resolved separately, at the same time.",
        "After the last Wave counter on either lane flips, the team with more total Zones " +
            "between its Throne and both Battle Zones wins.",
        "If a push would make both teams win at once, spawn all minions back in the Zones " +
            "they held before the push and keep playing.",
    ),
)
