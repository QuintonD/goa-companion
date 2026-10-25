package dev.quinton.goa.web.players

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.rating.RatingsResult
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.player.PlayerPalette
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.heroName
import dev.quinton.goa.feature.players.common.label
import dev.quinton.goa.feature.players.match.HeroOption
import dev.quinton.goa.feature.players.match.MAX_SEATS_PER_TEAM
import dev.quinton.goa.feature.players.match.MatchEditUiState
import dev.quinton.goa.feature.players.match.MatchEditing
import dev.quinton.goa.feature.players.match.MatchForm
import dev.quinton.goa.feature.players.match.MatchValidation
import dev.quinton.goa.feature.players.match.PilotOption
import dev.quinton.goa.feature.players.match.PlayerChangeUi
import dev.quinton.goa.feature.players.match.SavedResultUi
import dev.quinton.goa.feature.players.match.SeatDraft
import dev.quinton.goa.feature.players.match.search
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.ChipRow
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.PlayerAvatar
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import kotlin.js.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.browser.window
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputMode
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** The editor's own state (the phone's MatchEditViewModel.EditState), kept while the entry exists. */
private class MatchEditor {
    var loaded by mutableStateOf(false)
    var original by mutableStateOf<Match?>(null)
    var form by mutableStateOf(MatchForm(playedAt = 0))
    var initialForm by mutableStateOf(MatchForm(playedAt = 0))
    var notFound by mutableStateOf(false)
    var showErrors by mutableStateOf(false)
    var saveAttempts by mutableStateOf(0)
    var saving by mutableStateOf(false)
    var saved by mutableStateOf<Match?>(null)
    private var nextKey = 0L

    fun key() = nextKey++

    fun update(transform: (MatchForm) -> MatchForm) {
        form = transform(form)
    }

    fun updateSeat(team: Team, key: Long, transform: (SeatDraft) -> SeatDraft) = update { f ->
        f.withSeats(team, f.seats(team).map { if (it.key == key) transform(it) else it })
    }
}

private sealed interface Picker {
    data class HeroFor(val team: Team, val key: Long) : Picker

    data class PilotFor(val team: Team, val key: Long) : Picker
}

private fun now(): Long = MatchEditing.toMinute(Date.now().toLong())

/**
 * Log, edit or record the result of a match (MatchEditScreen): when and where, the teams and their
 * seats, the result, details; the shared MatchForm, MatchValidation and MatchEditing rules. A new
 * result shows what it changed (the saved-result summary) instead of closing.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun MatchEditScreen(matchId: String?, resultFirst: Boolean) {
    val services = LocalServices.current
    val group = services.data.group
    val presenter = remember { playersPresenter(services) }
    val scope = rememberCoroutineScope()
    val editor = rememberRetained("match-editor") { MatchEditor() }
    LaunchedEffect(matchId) {
        if (editor.loaded) return@LaunchedEffect
        val existing = matchId?.let { group.matches.observeMatch(it).first() }
        when {
            existing != null -> {
                val loaded = MatchForm.from(existing, editor::key)
                // A game that just ended: a pending match becomes a result.
                val pending = existing.status == MatchStatus.PENDING
                val form = loaded.copy(status = if (pending && resultFirst) MatchStatus.COMPLETED else loaded.status)
                editor.original = existing
                editor.form = form
                editor.initialForm = form
            }

            matchId != null -> editor.notFound = true

            else -> {
                val form = MatchForm(
                    playedAt = now(),
                    mode = GameMode.QUICK,
                    red = List(2) { SeatDraft(editor.key()) },
                    blue = List(2) { SeatDraft(editor.key()) },
                )
                editor.form = form
                editor.initialForm = form
            }
        }
        editor.loaded = true
    }
    val people by remember { group.players.observePlayers(includeArchived = true) }.collectAsState(emptyList())
    val catalog by remember { presenter.catalog }.collectAsState(Catalog.EMPTY)
    val results by remember {
        group.ratingsSnapshot.observeSnapshot().map { it.ratings }.onStart { emit(RatingsResult.EMPTY) }
    }.collectAsState(RatingsResult.EMPTY)
    val byId = people.associateBy { it.id }
    val state = MatchEditUiState(
        loading = !editor.loaded,
        isNew = editor.original == null,
        notFound = editor.notFound,
        form = editor.form,
        pilots = MatchEditing.pilots(people, editor.form, results),
        heroes = MatchEditing.heroes(catalog),
        catalog = catalog,
        playersById = byId,
        errors = MatchValidation.validate(
            editor.form,
            { catalog.heroName(it) },
            { byId[it]?.name ?: "Unknown player" },
            now = Date.now().toLong(),
        ),
        showErrors = editor.showErrors,
        saveAttempts = editor.saveAttempts,
        dirty = editor.form != editor.initialForm,
        saving = editor.saving,
        fromDraft = editor.original?.draftId != null,
        resultFirst = resultFirst,
        unsavedResult = resultFirst && editor.original == null && !editor.notFound,
        saved = editor.saved?.let { MatchEditing.savedSummary(it, people, results) },
    )
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val saved = state.saved
    val unsaved = (state.dirty || state.unsavedResult) && saved == null
    val save: () -> Unit = save@{
        if (editor.saving || editor.notFound || editor.saved != null) return@save
        if (MatchValidation.validate(editor.form, now = Date.now().toLong()).isNotEmpty()) {
            editor.showErrors = true
            editor.saveAttempts++
            window.scrollTo(0.0, 0.0)
            return@save
        }
        editor.saving = true
        scope.launch {
            val original = editor.original
            val match = editor.form.toMatch(
                id = original?.id ?: Uuid.random().toString(),
                createdAt = original?.createdAt ?: Date.now().toLong(),
                draftId = original?.draftId,
            ).copy(tieBreaker = original?.tieBreaker) // Not edited here; keep the coin from setup.
            group.matches.upsert(match)
            presenter.recentlySaved = match.id
            editor.saving = false
            editor.initialForm = editor.form
            if (MatchEditing.isNewResult(match, original, resultFirst)) {
                editor.saved = match
            } else {
                services.router.back()
            }
        }
    }
    Screen(
        when {
            saved != null -> "Result saved"
            resultFirst -> "Who won?"
            state.isNew -> "Log match"
            else -> "Edit match"
        },
        actions = {
            if (!state.loading && !state.notFound && saved == null) {
                GoaButton("Save", save, ButtonKind.TEXT, enabled = !state.saving)
            }
        },
    ) {
        when {
            state.loading -> LoadingState()

            state.notFound -> EmptyState("g_history", "Match not found", "It may have been deleted.")

            saved != null -> ResultSavedContent(saved, onDone = { services.router.back() }) { kind ->
                rematch(services, saved.match.id, kind)
            }

            else -> MatchFormContent(
                state,
                editor,
                onPickHero = { team, key -> picker = Picker.HeroFor(team, key) },
                onPickPilot = { team, key -> picker = Picker.PilotFor(team, key) },
                onSave = save,
                onDelete = { confirmDelete = true },
                onCancel = { if (unsaved) confirmDiscard = true else services.router.back() },
            )
        }
    }
    when (val open = picker) {
        is Picker.HeroFor -> {
            val seat = state.form.seats(open.team).firstOrNull { it.key == open.key }
            HeroPickerSheet(
                heroes = state.heroes,
                selected = seat?.heroId,
                taken = state.form.heroesInUse().toSet() - setOfNotNull(seat?.heroId),
                onPick = { heroId ->
                    editor.updateSeat(open.team, open.key) { it.copy(heroId = heroId) }
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }

        is Picker.PilotFor -> {
            val seat = state.form.seats(open.team).firstOrNull { it.key == open.key }
            PilotPickerSheet(
                team = open.team,
                pilots = state.pilots,
                selected = seat?.playerId,
                sameTeam = state.form.seats(open.team).filter { it.key != open.key }.mapNotNull { it.playerId }.toSet(),
                otherTeam = state.form.seats(open.team.opponent).mapNotNull { it.playerId }.toSet(),
                onPick = { playerId ->
                    editor.updateSeat(open.team, open.key) { it.copy(playerId = playerId) }
                    picker = null
                },
                onAddPlayer = { name ->
                    picker = null
                    val trimmed = name.trim().take(MAX_NAME_LENGTH)
                    if (trimmed.isNotEmpty()) {
                        scope.launch {
                            val all = group.players.observePlayers(includeArchived = true).first()
                            val (color, crest) = PlayerPalette.forNew(all)
                            // A known name (archived included) picks that player instead of creating a second one.
                            val player = group.players.ensurePlayer(
                                Player(
                                    Uuid.random().toString(),
                                    trimmed,
                                    color,
                                    crest,
                                    createdAt = Date.now().toLong(),
                                ),
                            )
                            editor.updateSeat(open.team, open.key) { it.copy(playerId = player.id) }
                        }
                    }
                },
                onDismiss = { picker = null },
            )
        }

        null -> Unit
    }
    if (confirmDiscard) {
        AlertDialog(
            if (state.unsavedResult) "Leave without saving?" else "Discard changes?",
            onDismiss = { confirmDiscard = false },
            buttons = {
                GoaButton("Keep editing", { confirmDiscard = false }, ButtonKind.TEXT)
                GoaButton(if (state.unsavedResult) "Leave" else "Discard", {
                    confirmDiscard = false
                    services.router.back()
                }, ButtonKind.TEXT)
            },
        ) { P { Text("Your edits to this match haven't been saved.") } }
    }
    if (confirmDelete) {
        AlertDialog("Delete this match?", onDismiss = { confirmDelete = false }, buttons = {
            GoaButton("Cancel", { confirmDelete = false }, ButtonKind.TEXT)
            GoaButton("Delete", {
                confirmDelete = false
                val id = editor.original?.id
                if (id != null) {
                    services.router.back()
                    services.data.scope.launch { deleteWithUndo(services, presenter, id) }
                }
            }, ButtonKind.DANGER)
        }) { P { Text("All ratings are recalculated without it. You can undo from the match log.") } }
    }
}

@Suppress("LongParameterList")
@Composable
private fun MatchFormContent(
    state: MatchEditUiState,
    editor: MatchEditor,
    onPickHero: (Team, Long) -> Unit,
    onPickPilot: (Team, Long) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val form = state.form
    Div({ classes("goa-w2-form") }) {
        if (state.showErrors && state.errors.isNotEmpty()) ErrorCard(state.errors.map { it.message })
        if (state.resultFirst) {
            // "Who won?": the two answers the table has right now, then everything else.
            WinnerAndCondition(state, editor, showHeader = false)
            P({
                classes("t-body-small", "c-variant")
            }) { Text("Everything below is optional: check the seats, then save.") }
            TeamsSection(state, editor, onPickHero, onPickPilot)
            WhenSection(form, editor)
            DetailsSection(form, editor)
            SectionHeader("Match status")
            StatusChoice(form, editor)
        } else {
            if (state.fromDraft && form.status == MatchStatus.PENDING) {
                P({
                    classes("t-body-small", "c-variant")
                }) { Text("Created from a draft. Fill in the result when the game ends.") }
            }
            WhenSection(form, editor)
            TeamsSection(state, editor, onPickHero, onPickPilot)
            SectionHeader("Result")
            StatusChoice(form, editor)
            if (form.status == MatchStatus.COMPLETED) WinnerAndCondition(state, editor, showHeader = true)
            DetailsSection(form, editor)
        }
        Div({ classes("goa-w2-spaced") }) {
            GoaButton(
                when {
                    state.resultFirst && form.status == MatchStatus.COMPLETED -> "Save result"
                    state.isNew -> "Save match"
                    else -> "Save changes"
                },
                onSave,
                enabled = !state.saving,
                block = true,
            )
        }
        Div({ classes("goa-button-row", "goa-w2-start") }) {
            GoaButton("Cancel", onCancel, ButtonKind.TEXT)
            if (!state.isNew) GoaButton("Delete match", onDelete, ButtonKind.TEXT, icon = "g_delete")
        }
        P({ classes("t-body-small", "c-variant") }) {
            Text("Ratings recompute from the whole match log whenever a match is saved, edited or deleted.")
        }
    }
}

@Composable
private fun ErrorCard(messages: List<String>) {
    Div({
        classes("goa-w2-error-card")
        attr("role", "alert")
    }) {
        Span({ classes("c-error") }) { Icon("g_error") }
        Div {
            H3({ classes("t-title-small", "goa-w2-tight0") }) { Text("Can't save yet") }
            messages.forEach { P({ classes("t-body-medium", "goa-w2-tight0") }) { Text("• $it") } }
        }
    }
}

@Composable
private fun StatusChoice(form: MatchForm, editor: MatchEditor) {
    ChoiceChips(
        "Match status",
        listOf(MatchStatus.COMPLETED, MatchStatus.PENDING, MatchStatus.ABANDONED),
        form.status,
        { it.label() },
    ) { status -> editor.update { it.copy(status = status) } }
}

@Composable
private fun WhenSection(form: MatchForm, editor: MatchEditor) {
    SectionHeader("When and where")
    Div({ classes("goa-w2-when") }) {
        Div({ classes("goa-w2-field") }) {
            Label(forId = "match-date", attrs = { classes("t-label-large") }) { Text("Date") }
            Input(InputType.Date) {
                id("match-date")
                value(isoDate(form.playedAt))
                attr("max", isoDate(Date.now().toLong()))
                onChange { event ->
                    val picked = event.value.takeIf { it.isNotEmpty() } ?: return@onChange
                    editor.update { f -> f.copy(playedAt = minOf(withDate(f.playedAt, picked), Date.now().toLong())) }
                }
            }
        }
        Div({ classes("goa-w2-field") }) {
            Label(forId = "match-time", attrs = { classes("t-label-large") }) { Text("Time") }
            Input(InputType.Time) {
                id("match-time")
                value(isoTime(form.playedAt))
                onChange { event ->
                    val picked = event.value.takeIf { it.isNotEmpty() } ?: return@onChange
                    // A time later today than now is taken as "just now".
                    editor.update { f -> f.copy(playedAt = minOf(withTime(f.playedAt, picked), Date.now().toLong())) }
                }
            }
        }
    }
    ChoiceChips("Game mode", GameMode.entries, form.mode, { it.label() }) { mode ->
        editor.update { it.copy(mode = mode) }
    }
    // Free text: the mode above already says single or double lane.
    TextField(
        label = "Map or table (optional)",
        value = form.map,
        onValue = { v -> editor.update { it.copy(map = v.take(MatchEditing.MAX_TEXT)) } },
        maxLength = MatchEditing.MAX_TEXT,
        id = "match-map",
    )
}

@Composable
private fun TeamsSection(
    state: MatchEditUiState,
    editor: MatchEditor,
    onPickHero: (Team, Long) -> Unit,
    onPickPilot: (Team, Long) -> Unit,
) {
    Div({ classes("goa-section-header", "goa-w2-section-trailing") }) {
        H2({ classes("t-section-head") }) { Text("Teams") }
        GoaButton("Swap", { editor.update(MatchEditing::swapTeams) }, ButtonKind.TEXT, icon = "g_swap")
    }
    Div({ classes("goa-w2-team-editors") }) {
        Team.entries.forEach { team -> TeamEditor(team, state, editor, onPickHero, onPickPilot) }
    }
}

@Composable
private fun WinnerAndCondition(state: MatchEditUiState, editor: MatchEditor, showHeader: Boolean) {
    val form = state.form
    H3({ classes(if (showHeader) "t-title-small" else "t-title-medium", "goa-w2-question") }) {
        Text(if (showHeader) "Winner" else "Which team won?")
    }
    Div({
        classes("goa-w2-winners")
        attr("role", "radiogroup")
        attr("aria-label", "Winner")
    }) {
        Team.entries.forEach { team ->
            val pilots = form.seats(team).mapNotNull { seat ->
                seat.playerId?.let { state.playersById[it]?.name }
            }.distinct()
            val selected = form.winner == team
            Button({
                type(ButtonType.Button)
                classes("goa-w2-winner", if (team == Team.RED) "red" else "blue")
                attr("role", "radio")
                attr("aria-checked", selected.toString())
                attr(
                    "aria-label",
                    "${team.label()} won" + if (pilots.isEmpty()) "" else ": ${pilots.joinToString(", ")}",
                )
                onClick { editor.update { MatchEditing.winner(it, team) } }
            }) {
                Span({ classes("goa-w2-line", "goa-w2-center") }) {
                    TeamBadge(team)
                    Span({ classes("t-label-large", "bold") }) { Text(" won") }
                    if (selected) Icon("g_trophy", IconSize.SMALL)
                }
                if (pilots.isNotEmpty()) Span({ classes("t-body-medium") }) { Text(pilots.joinToString(" · ")) }
            }
        }
    }
    H3({ classes(if (showHeader) "t-title-small" else "t-title-medium", "goa-w2-question") }) {
        Text(if (showHeader) "Win condition" else "How did they win?")
    }
    // One choice (tap again to clear).
    ChipRow(wrap = true) {
        WinCondition.entries.forEach { condition ->
            val selected = form.winCondition == condition
            Chip(condition.label(), selected, onClick = {
                editor.update { it.copy(winCondition = if (selected) null else condition) }
            })
        }
    }
}

@Composable
private fun DetailsSection(form: MatchForm, editor: MatchEditor) {
    TextField(
        label = "Duration (minutes, optional)",
        value = form.duration,
        onValue = { v -> editor.update { MatchEditing.duration(it, v) } },
        inputMode = InputMode.Numeric,
        id = "match-duration",
    )
    TextAreaField(
        "Notes",
        form.notes,
        { v -> editor.update { it.copy(notes = v.take(MatchEditing.MAX_NOTES)) } },
        id = "match-notes",
        maxLength = MatchEditing.MAX_NOTES,
    )
}

@Composable
private fun TeamEditor(
    team: Team,
    state: MatchEditUiState,
    editor: MatchEditor,
    onPickHero: (Team, Long) -> Unit,
    onPickPilot: (Team, Long) -> Unit,
) {
    val seats = state.form.seats(team)
    val doublePilots = state.form.doublePilots(team)
    val duplicates = state.form.heroesInUse().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    val otherTeam = state.form.seats(team.opponent).mapNotNull { it.playerId }.toSet()
    Div({ classes("goa-w2-team-editor") }) {
        Div({ classes("goa-w2-line") }) {
            TeamBadge(team)
            Span({ classes("t-label-large", "c-variant", "goa-w2-grow") }) {
                Text("  ·  ${seats.size} ${if (seats.size == 1) "seat" else "seats"}")
            }
            Button({
                type(ButtonType.Button)
                classes("goa-button", "text")
                attr("aria-label", "Add seat to ${team.label()}")
                if (seats.size >= MAX_SEATS_PER_TEAM) attr("disabled", "")
                onClick { editor.update { MatchEditing.addSeat(it, team, editor.key()) } }
            }) {
                Icon("g_add")
                Text("Add seat")
            }
        }
        if (seats.isEmpty()) {
            P({ classes("t-body-medium", "c-error") }) { Text("No seats. Add at least one hero for ${team.label()}.") }
        }
        seats.forEach { seat ->
            SeatEditor(
                team = team,
                seat = seat,
                state = state,
                doublePilot = seat.playerId != null && seat.playerId in doublePilots,
                problem = when {
                    state.showErrors && seat.playerId == null -> "Choose a pilot"
                    seat.heroId != null && seat.heroId in duplicates -> "Hero picked twice"
                    seat.playerId != null && seat.playerId in otherTeam -> "Also on ${team.opponent.label()}"
                    else -> null
                },
                onPickHero = { onPickHero(team, seat.key) },
                onPickPilot = { onPickPilot(team, seat.key) },
                onToggleHandicap = { editor.updateSeat(team, seat.key) { it.copy(handicap = !it.handicap) } },
                onRemove = { editor.update { f -> f.withSeats(team, f.seats(team).filterNot { it.key == seat.key }) } },
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SeatEditor(
    team: Team,
    seat: SeatDraft,
    state: MatchEditUiState,
    doublePilot: Boolean,
    problem: String?,
    onPickHero: () -> Unit,
    onPickPilot: () -> Unit,
    onToggleHandicap: () -> Unit,
    onRemove: () -> Unit,
) {
    val hero = state.heroOption(seat.heroId)
    val heroLabel = if (seat.heroId == null) "Unknown hero" else state.heroName(seat.heroId)
    val pilot = seat.playerId?.let { state.playersById[it] }
    Div({
        classes("goa-w2-seat-editor", if (team == Team.RED) "red" else "blue")
        if (problem != null) classes("problem")
    }) {
        Div({ classes("goa-w2-line") }) {
            Button({
                type(ButtonType.Button)
                classes("goa-w2-seat-hero-button")
                attr("aria-label", "Hero: $heroLabel. Change hero")
                onClick { onPickHero() }
            }) {
                HeroThumb(seat.heroId?.let { heroLabel }, hero?.portrait, 48)
                Span({ classes("goa-w2-seat-hero-text") }) {
                    Span({
                        classes("t-title-medium", "goa-w2-ellipsis")
                        if (seat.heroId == null) classes("c-variant")
                    }) { Text(heroLabel) }
                    Span({
                        classes("t-body-small", "c-variant", "goa-w2-ellipsis")
                    }) { Text(hero?.title ?: "Tap to choose") }
                }
            }
            IconButton("close", "Remove seat: $heroLabel", onClick = onRemove)
        }
        Div({ classes("goa-w2-flow") }) {
            Button({
                type(ButtonType.Button)
                classes("goa-chip", "goa-w2-assist")
                attr("aria-label", "Pilot: ${pilot?.name ?: "none"}. Change pilot")
                onClick { onPickPilot() }
            }) {
                if (pilot != null) PlayerAvatar(pilot, 24) else Icon("g_people", IconSize.SMALL)
                Span({ classes("goa-w2-ellipsis") }) { Text(pilot?.name ?: "Choose pilot") }
            }
            Button({
                type(ButtonType.Button)
                classes("goa-chip")
                attr("aria-pressed", seat.handicap.toString())
                attr("aria-label", "Handicap card for $heroLabel")
                onClick { onToggleHandicap() }
            }) {
                if (seat.handicap) Icon("check", IconSize.SMALL)
                Text("Handicap")
            }
            if (doublePilot) Pill("Double pilot", "secondary")
        }
        problem?.let { P({ classes("t-label-medium", "c-error", "goa-w2-tight0") }) { Text(it) } }
    }
}

/** "Choose hero": search by name or title; heroes already in the match can't be picked twice. */
@Composable
private fun HeroPickerSheet(
    heroes: List<HeroOption>,
    selected: String?,
    taken: Set<String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    BottomSheet("Choose hero", onDismiss = onDismiss) {
        TextField(
            label = "Search heroes",
            value = query,
            onValue = { query = it },
            placeholder = "Search heroes",
            type = InputType.Search,
            id = "hero-search",
        )
        val results = heroes.search(query)
        Ul({ classes("goa-list") }) {
            if (query.isBlank()) {
                PickerRow(selected == null, true, {
                    onPick(null)
                }, leading = { HeroThumb("?", null, 40) }, "Unknown hero", "Counts for the pilot's rating only")
            }
            if (heroes.isEmpty()) {
                Li {
                    P({
                        classes("t-body-medium", "c-variant")
                    }) { Text("The hero catalog isn't available, so only “Unknown hero” can be chosen.") }
                }
            } else if (results.isEmpty()) {
                Li { P({ classes("t-body-medium", "c-variant") }) { Text("No hero matches “$query”.") } }
            }
            results.forEach { hero ->
                val inUse = hero.id in taken
                PickerRow(
                    hero.id == selected,
                    !inUse,
                    { onPick(hero.id) },
                    leading = { HeroThumb(hero.name, hero.portrait, 40) },
                    hero.name,
                    hero.title,
                    trailing = { if (inUse) Pill("In this match") else ComplexityStars(hero.complexity) },
                )
            }
        }
    }
}

@Composable
private fun ComplexityStars(complexity: Int?) {
    val value = complexity ?: return
    Span({
        classes("goa-w2-stars", "t-label-large")
        attr("aria-label", "Complexity $value of $MAX_COMPLEXITY")
    }) { Text("★".repeat(value.coerceIn(0, MAX_COMPLEXITY)) + "☆".repeat((MAX_COMPLEXITY - value).coerceAtLeast(0))) }
}

private const val MAX_COMPLEXITY = 3

/** "Pilot for Red": pick a player (twice on one team = double pilot), or add someone missing. */
@Suppress("LongParameterList")
@Composable
private fun PilotPickerSheet(
    team: Team,
    pilots: List<PilotOption>,
    selected: String?,
    sameTeam: Set<String>,
    otherTeam: Set<String>,
    onPick: (String) -> Unit,
    onAddPlayer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    BottomSheet("Pilot for ${team.label()}", onDismiss = onDismiss) {
        P({ classes("t-body-medium", "c-variant", "goa-w2-sheet-note") }) {
            Text("Pick the same player for two seats to record a double pilot.")
        }
        Div({ classes("goa-w2-add-row") }) {
            TextField(
                label = if (pilots.isEmpty()) "Name of the first player" else "Someone missing? Add them",
                value = newName,
                onValue = { newName = it.take(MAX_NAME_LENGTH) },
                maxLength = MAX_NAME_LENGTH,
                words = true,
                id = "pilot-new",
                onEnter = { if (newName.isNotBlank()) onAddPlayer(newName) },
            )
            GoaButton("Add", { onAddPlayer(newName) }, ButtonKind.TEXT, enabled = newName.isNotBlank())
        }
        Ul({ classes("goa-list") }) {
            if (pilots.isEmpty()) {
                Li {
                    P({
                        classes("t-body-medium", "c-variant")
                    }) { Text("No players yet. Add your group on the Players tab first.") }
                }
            }
            pilots.forEach { option ->
                val id = option.player.id
                val onOther = id in otherTeam
                PickerRow(
                    id == selected,
                    !onOther,
                    { onPick(id) },
                    leading = { PlayerAvatar(option.player, 40) },
                    option.player.name,
                    if (option.rating.games > 0) "Elo ${formatRating(option.rating.value)}" else "Unrated",
                    trailing = {
                        when {
                            onOther -> Pill("On ${team.opponent.label()}")
                            id in sameTeam -> Pill("Double pilot", "secondary")
                        }
                    },
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PickerRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit = {},
) {
    Li {
        Button({
            type(ButtonType.Button)
            classes("goa-row", "goa-w2-picker-row")
            if (selected) classes("selected")
            if (!enabled) {
                attr("disabled", "")
                attr("aria-label", "$title, unavailable")
            }
            attr("aria-pressed", selected.toString())
            onClick { onClick() }
        }) {
            Span({ classes("goa-row-leading") }) { leading() }
            Span({ classes("goa-row-text") }) {
                Span({ classes("goa-row-title", "t-title-medium") }) { Text(title) }
                subtitle?.let { Span({ classes("goa-row-subtitle", "t-body-medium") }) { Text(it) } }
            }
            Span({ classes("goa-row-trailing") }) {
                trailing()
                if (selected) Span({ classes("goa-w2-selected") }) { Icon("check") }
            }
        }
    }
}

/** After a result is saved: who won, each player's rating change and rank, who leads, and Rematch. */
@Composable
private fun ResultSavedContent(state: SavedResultUi, onDone: () -> Unit, onRematch: (RematchKind?) -> Unit) {
    var chooseRematch by remember { mutableStateOf(false) }
    val match = state.match
    match.winner?.let { winner ->
        Div({ classes("goa-w2-line", "goa-w2-won", if (winner == Team.RED) "c-team-red" else "c-team-blue") }) {
            Icon("g_trophy")
            TeamBadge(winner)
            Span({
                classes("t-title-medium", "bold")
            }) { Text(" won" + (match.winCondition?.let { " · ${it.label()}" } ?: "")) }
        }
    }
    Div({ classes("goa-section-header", "goa-w2-section-trailing") }) {
        H2({ classes("t-section-head") }) { Text("Rating changes") }
        if (!state.ratingsReady) {
            Div({
                classes("goa-spinner", "small")
                attr("role", "status")
                attr("aria-label", "Updating ratings")
            })
        }
    }
    GoaCard(flush = true) { state.players.forEach { PlayerChangeRow(it, state.ratingsReady) } }
    val leaderRating = state.leaderRating
    if (state.ratingsReady && state.leaderName != null && leaderRating != null) {
        P({
            classes("t-title-small", "c-primary")
        }) { Text("On top now: ${state.leaderName} · ${formatRating(leaderRating.value)} Elo") }
    }
    Div({ classes("goa-w2-spaced", "goa-w2-stack") }) {
        GoaButton("Rematch", { chooseRematch = true }, icon = "g_replay", block = true)
        GoaButton("Done", onDone, ButtonKind.OUTLINED, block = true)
    }
    if (chooseRematch) {
        RematchSheet(state.rematchOptions, onPick = {
            chooseRematch = false
            onRematch(it)
        }, onDismiss = { chooseRematch = false })
    }
}

@Composable
private fun PlayerChangeRow(line: PlayerChangeUi, ready: Boolean) {
    val description = buildString {
        append(line.name)
        append(if (line.won) ", won" else ", lost")
        if (ready) {
            line.delta?.let { append(", rating ${formatDelta(it)}") }
            append(", now ${formatRating(line.rating.value)}")
            line.rank?.let { append(", rank $it") }
        }
    }
    Div({
        classes("goa-w2-pair", "goa-w2-min56")
        attr("aria-label", description)
    }) {
        PlayerAvatar(line.player, 40)
        Div({
            classes("goa-w2-grow")
            attr("aria-hidden", "true")
        }) {
            Div({ classes("t-title-medium") }) { Text(line.name) }
            Div({ classes("t-body-small", "c-variant") }) {
                TeamBadge(line.team, showLabel = false)
                Text(
                    buildString {
                        append(" ")
                        append(if (line.won) "Won" else "Lost")
                        if (ready) line.rank?.let { append(" · #$it") }
                    },
                )
            }
        }
        if (ready) {
            Div({
                classes("goa-w2-elo")
                attr("aria-hidden", "true")
            }) {
                DeltaIndicator(line.delta, label = "rating")
                Span({ classes("t-label-medium", "c-variant") }) { Text(formatRating(line.rating.value)) }
            }
        }
    }
}

private fun pad(n: Int) = n.toString().padStart(2, '0')

/** `yyyy-mm-dd` of [millis] in this browser's time zone (the date input's value). */
private fun isoDate(millis: Long): String {
    val d = Date(millis.toDouble())
    return "${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}"
}

private fun isoTime(millis: Long): String {
    val d = Date(millis.toDouble())
    return "${pad(d.getHours())}:${pad(d.getMinutes())}"
}

private fun withDate(millis: Long, iso: String): Long {
    val (y, m, d) = iso.split('-').map { it.toInt() }
    val old = Date(millis.toDouble())
    return Date(y, m - 1, d, old.getHours(), old.getMinutes()).getTime().toLong()
}

private fun withTime(millis: Long, iso: String): Long {
    val (h, min) = iso.split(':').take(2).map { it.toInt() }
    val old = Date(millis.toDouble())
    return Date(old.getFullYear(), old.getMonth(), old.getDate(), h, min).getTime().toLong()
}
