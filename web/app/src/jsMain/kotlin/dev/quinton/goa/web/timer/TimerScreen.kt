package dev.quinton.goa.web.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.data.TonightTurn
import dev.quinton.goa.feature.timer.ui.timer.PendingMatchItem
import dev.quinton.goa.feature.timer.ui.timer.PresetOption
import dev.quinton.goa.feature.timer.ui.timer.StartUiState
import dev.quinton.goa.feature.timer.ui.timer.SummaryUiState
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.table.DialogButtons
import dev.quinton.goa.web.table.RadioCard
import dev.quinton.goa.web.table.RadioGroup
import dev.quinton.goa.web.table.SmallHeading
import dev.quinton.goa.web.table.StepButton
import dev.quinton.goa.web.table.Stepper
import dev.quinton.goa.web.table.TeamBadge
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.ErrorState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconLink
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The Timer tab: the start card (preset, tonight's turn length, pending matches), the running
 * session (full screen: no tab bar to mis-tap beside Next) or the match summary.
 */
@Composable
fun TimerScreen() {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    val model = remember { TimerModel(services, scope) }
    val state by model.uiState.collectAsState()
    val error by model.loadError.collectAsState()
    // Back on the page after it was hidden, the shell catches up with the clock (TimerBackground).
    val active = state.active
    val summary = state.summary
    val start = state.start
    when {
        active != null -> ActiveTimer(active, model)

        summary != null -> MatchSummary(summary, model)

        start != null -> TimerStart(start, model)

        error != null -> Screen("Timer") {
            ErrorState(
                "Couldn't load the timer presets",
                "Check the connection and try again. Once the app has been opened online, this works offline too.",
                onRetry = model::retryLoad,
            )
        }

        else -> Screen("Timer") { LoadingState() }
    }
}

@Composable
private fun TimerStart(state: StartUiState, model: TimerModel) {
    Screen(
        "Timer",
        actions = {
            IconLink("tune", "Manage timer presets", Route.TimerPresets)
            IconLink("settings", "Settings", Route.Settings)
        },
    ) {
        Div({ classes("goa-table-stack") }) {
            ReadyCard(state, model)
            if (state.selected?.usesActionTimer == true) SeatsStepper(state.seats, model::setSeats)
            if (state.pendingMatches.isNotEmpty()) {
                SmallHeading("Pending matches")
                state.pendingMatches.forEach { PendingMatchCard(it, model) }
            }
            Div({ classes("goa-table-heading-row") }) {
                SmallHeading("Presets")
                dev.quinton.goa.web.ui.RouteLink(Route.TimerPresets, {
                    classes("goa-button", "text")
                }) { Text("Manage") }
            }
            RadioGroup("Presets") {
                state.presets.forEach { preset ->
                    PresetCard(preset, preset.id == state.selectedPresetId, model::selectPreset)
                }
            }
            P({ classes("t-body-small", "c-variant") }) {
                Text(
                    "Timers are table rules. The rulebook sets no time limits; strategy talk is " +
                        "allowed from the end of one turn until cards are revealed.",
                )
            }
        }
    }
}

@Composable
private fun ReadyCard(state: StartUiState, model: TimerModel) {
    val router = LocalServices.current.router
    Div({ classes("goa-card", "goa-table-ready") }) {
        Div({ classes("goa-table-ready-head") }) {
            Span({ classes("goa-table-medallion") }) { Icon("hourglass") }
            Div {
                H2({ classes("t-headline-small", "goa-table-tight") }) { Text("Phase timer") }
                P({
                    classes("t-title-small", "c-primary", "goa-table-tight")
                }) { Text(state.selected?.name ?: "No preset") }
            }
        }
        P({ classes("t-body-medium", "c-variant") }) { Text(state.selected?.summary.orEmpty()) }
        state.tonight?.let { tonight ->
            Div({
                classes("goa-table-tonight")
                if (tonight.adjusted) classes("adjusted")
            }) {
                Div({ classes("goa-table-tonight-row") }) {
                    Div({ classes("goa-table-row-text") }) {
                        Span({ classes("t-title-small") }) { Text("Turn length tonight") }
                        Span({ classes("t-body-small", "c-variant") }) {
                            Text(
                                if (tonight.adjusted) {
                                    "Preset: ${TimerText.durationLabel(
                                        tonight.presetSeconds,
                                    )}"
                                } else {
                                    "From the preset"
                                },
                            )
                        }
                    }
                    StepButton("remove", "15 seconds shorter", tonight.seconds > TonightTurn.MIN_SECONDS) {
                        model.setTonightTurn(tonight.seconds - TonightTurn.STEP_SECONDS)
                    }
                    Span({
                        classes("goa-table-tonight-value", "t-title-large", "t-numeric")
                        attr("aria-live", "polite")
                        attr("aria-label", "Turns ${TimerText.spokenDuration(tonight.seconds.toLong())} tonight")
                    }) { Text(TimerText.durationLabel(tonight.seconds)) }
                    StepButton("add", "15 seconds longer", tonight.seconds < TonightTurn.MAX_SECONDS) {
                        model.setTonightTurn(tonight.seconds + TonightTurn.STEP_SECONDS)
                    }
                }
                if (tonight.adjusted) {
                    Div({ classes("goa-table-tonight-row") }) {
                        Span({ classes("t-body-small", "c-variant", "goa-table-grow") }) { Text("Just for tonight.") }
                        GoaButton("Reset", { model.setTonightTurn(tonight.presetSeconds) }, ButtonKind.TEXT)
                        GoaButton("Save to preset", model::saveTonightToPreset, ButtonKind.TEXT)
                    }
                }
            }
        }
        GoaButton("Start timer", model::start, icon = "play", block = true)
        GoaButton("Match setup", {
            router.navigate(Route.MatchSetup())
        }, ButtonKind.OUTLINED, icon = "flag", block = true)
        P({
            classes("t-body-small", "c-variant", "goa-table-center")
        }) { Text("Mode, counters to lay out and the Tie Breaker coin.") }
    }
}

@Composable
private fun SeatsStepper(seats: Int, onChange: (Int) -> Unit) {
    Div({ classes("goa-card", "goa-table-inline-card") }) {
        Div({ classes("goa-table-row-text") }) {
            Span({ classes("t-title-small") }) { Text("Heroes at the table") }
            Span({ classes("t-body-small", "c-variant") }) { Text("The Action timer runs once per hero each turn.") }
        }
        Stepper(seats, TimerModel.MIN_SEATS..TimerModel.MAX_SEATS, onChange, "heroes")
    }
}

/** A pending match: its heroes per team and when it was set up, with Set up (the card) and Discard. */
@Composable
private fun PendingMatchCard(match: PendingMatchItem, model: TimerModel) {
    val router = LocalServices.current.router
    var confirm by remember(match.id) { mutableStateOf(false) }
    Div({ classes("goa-card", "goa-table-pending") }) {
        Button({
            type(ButtonType.Button)
            classes("goa-table-pending-open")
            onClick { router.navigate(Route.MatchSetup(match.id)) }
        }) {
            Span({ classes("t-title-small") }) { Text(match.title) }
            match.red?.let { TeamHeroesLine(Team.RED, it) }
            match.blue?.let { TeamHeroesLine(Team.BLUE, it) }
            Span({ classes("t-body-small", "c-variant") }) { Text(match.subtitle) }
        }
        Div({ classes("goa-table-pending-actions") }) {
            GoaButton("Discard", { confirm = true }, ButtonKind.TEXT)
            Button({
                type(ButtonType.Button)
                classes("goa-button", "text")
                onClick { router.navigate(Route.MatchSetup(match.id)) }
            }) {
                Text("Set up")
                Icon("chevron_right", IconSize.SMALL)
            }
        }
    }
    if (confirm) {
        AlertDialog(
            "Discard this pending match?",
            onDismiss = { confirm = false },
            buttons = {
                DialogButtons("Keep", { confirm = false }, "Discard", {
                    confirm = false
                    model.discardPending(match.id)
                }, danger = true)
            },
        ) { Text("It leaves this list and the match log. Matches with a result aren't affected.") }
    }
}

@Composable
private fun TeamHeroesLine(team: Team, heroes: String) {
    Span({
        classes("goa-table-team-line")
        attr("aria-label", "${if (team == Team.RED) "Red" else "Blue"}: $heroes")
    }) {
        TeamBadge(team, showLabel = false)
        Span({ classes("t-body-medium") }) { Text(heroes) }
    }
}

@Composable
private fun PresetCard(preset: PresetOption, selected: Boolean, onSelect: (String) -> Unit) {
    RadioCard(selected, onClick = { onSelect(preset.id) }) {
        Span({ classes("t-title-small") }) { Text(preset.name) }
        Span({ classes("t-body-small", "c-variant") }) { Text(preset.summary) }
    }
}

@Composable
private fun MatchSummary(state: SummaryUiState, model: TimerModel) {
    var confirmDiscard by remember { mutableStateOf(false) }
    Screen("Timer", actions = { IconLink("settings", "Settings", Route.Settings) }) {
        Div({ classes("goa-card", "goa-table-summary") }) {
            Span({ classes("goa-table-medallion", "large") }) { Icon("trophy") }
            H2({ classes("t-headline-small", "goa-table-tight") }) { Text("Match over") }
            P({ classes("goa-table-summary-time", "t-numeric") }) { Text(state.duration) }
            P({ classes("t-body-medium", "c-variant", "goa-table-center") }) {
                Text(
                    listOfNotNull(
                        if (state.rounds == 1) "1 round" else "${state.rounds} rounds",
                        state.mode?.let(TimerText::modeLabel),
                        state.presetName,
                    ).joinToString(" · "),
                )
            }
            GoaButton("Log result", model::logResult, block = true)
            P({ classes("t-body-small", "c-variant", "goa-table-center") }) {
                Text(
                    if (state.hasMatch) {
                        "The duration is saved to the match. Log the winner and win condition next."
                    } else {
                        "Log result opens a new match with this duration filled in."
                    },
                )
            }
            // A linked match already has its duration, so closing loses nothing. Without a match,
            // closing throws the duration away: ask first.
            GoaButton(
                if (state.hasMatch) "Close, log later" else "Discard",
                { if (state.hasMatch) model.dismissSummary() else confirmDiscard = true },
                ButtonKind.TEXT,
            )
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            "Discard this match?",
            onDismiss = { confirmDiscard = false },
            buttons = {
                GoaButton("Discard", {
                    confirmDiscard = false
                    model.dismissSummary()
                }, ButtonKind.TEXT)
                GoaButton("Log result", {
                    confirmDiscard = false
                    model.logResult()
                })
            },
        ) { Text("The ${state.duration} of play won't be logged anywhere.") }
    }
}
