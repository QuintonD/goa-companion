package dev.quinton.goa.web.play.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.list.BoardRowUi
import dev.quinton.goa.feature.board.list.BoardsRules
import dev.quinton.goa.feature.board.list.PlayHubUi
import dev.quinton.goa.feature.board.list.ResumeUi
import dev.quinton.goa.feature.board.setup.LineupSource
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.data.WebServices
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import kotlin.js.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** What the Boards screen shows (the phone's BoardsUiState.Ready, built with the shared BoardsRules). */
private data class BoardsData(
    val boards: List<BoardRowUi>,
    val stored: List<SavedBoard>,
    val latest: LineupSource?,
    val play: PlayHubUi,
    val unreadable: Boolean,
)

/**
 * Boards (PRODUCT §8, the phone's BoardsScreen): Play against the computer and Puzzles above the
 * saved boards, the latest draft's or match's line-up set up in one tap, and every board (rename,
 * duplicate, delete with Undo). With no boards yet it shows those and "New board".
 */
@Composable
fun BoardsScreen() {
    val services = LocalServices.current
    val router = services.router
    val data = services.data
    val scope = rememberCoroutineScope()
    val flow = remember { boardsData(data) }
    val state by flow.collectAsState(null)
    var renaming by remember { mutableStateOf<BoardRowUi?>(null) }
    var options by remember { mutableStateOf<BoardRowUi?>(null) }
    val now = Date.now().toLong()

    Screen("Boards", actions = {
        IconButton("add", "New board", onClick = { router.navigate(Route.NewBoard()) })
    }) {
        val ready = state
        if (ready == null) {
            LoadingState()
            return@Screen
        }
        Div({ classes("goa-stack") }) {
            PlayHub(ready.play, now)
            if (ready.boards.isEmpty()) {
                ready.latest?.let { LatestCard(it, onSetUp = { router.navigate(openRoute(it)) }) }
                EmptyState(
                    "board",
                    "No boards yet",
                    if (ready.unreadable) {
                        "The saved boards couldn't be read."
                    } else {
                        "Set up any line-up on the real map and see who can reach whom."
                    },
                ) { GoaButton("New board", { router.navigate(Route.NewBoard()) }, icon = "add") }
            } else {
                SectionHeader("Boards")
                ready.latest?.let { LatestCard(it, onSetUp = { router.navigate(openRoute(it)) }) }
                Div({ classes("goa-board-list") }) {
                    ready.boards.forEach { board ->
                        BoardRow(board, now, onOpen = { router.navigate(Route.Board(board.id)) }) {
                            options =
                                board
                        }
                    }
                }
                GoaButton("New board", { router.navigate(Route.NewBoard()) }, icon = "add", block = true)
            }
        }
    }
    options?.let { board ->
        BottomSheet(board.name, onDismiss = { options = null }) {
            RowList {
                ListRow("Rename", leadingIcon = "edit", onClick = {
                    options = null
                    renaming = board
                })
                ListRow("Duplicate", leadingIcon = "copy", onClick = {
                    options = null
                    scope.launch {
                        val saved = data.play.boards.observe(board.id).first() ?: return@launch
                        val at = Date.now().toLong()
                        data.play.boards.upsert(
                            saved.copy(
                                id = dev.quinton.goa.feature.board.newPieceId(),
                                name = BoardsRules.copyName(saved.name),
                                createdAt = at,
                                updatedAt = at,
                            ),
                        )
                    }
                })
                ListRow("Delete", leadingIcon = "delete", onClick = {
                    options = null
                    scope.launch {
                        val deleted = data.play.boards.observe(board.id).first() ?: return@launch
                        data.play.boards.delete(board.id)
                        // Undo outlives this screen: the snackbar is app-wide.
                        services.messages.show("Deleted “${deleted.name}”", "Undo") {
                            data.scope.launch { data.play.boards.upsert(deleted) }
                        }
                    }
                })
            }
        }
    }
    renaming?.let { board ->
        RenameDialog(board.name, onDismiss = { renaming = null }) { name ->
            renaming = null
            val trimmed = name.trim().take(BoardsRules.MAX_NAME)
            if (trimmed.isNotEmpty()) {
                scope.launch {
                    val saved = data.play.boards.observe(board.id).first() ?: return@launch
                    data.play.boards.upsert(saved.copy(name = trimmed, updatedAt = Date.now().toLong()))
                }
            }
        }
    }
}

/** The board of a line-up, opened (or reopened) in one tap. */
internal fun openRoute(source: LineupSource): Route =
    Route.OpenBoard(red = source.lineup.red, blue = source.lineup.blue)

private fun boardsData(data: WebServices): Flow<BoardsData> {
    val heroes: Flow<Map<String, BoardHero>> = flow { emit(data.content.catalog.catalog.first().boardHeroes()) }
        .catch { emit(emptyMap()) }
    val maps = data.play.boardMaps.maps.catch { emit(emptyList()) }
    val boards = data.play.boards.boards.map { it to false }.catch { emit(emptyList<SavedBoard>() to true) }
    val sources = combine(drafts(data), matches(data)) { d, m -> d to m }
    val play =
        combine(
            engineFacts(data),
            data.play.gameSave.current.catch {
                emit(StoredGame.None)
            },
            puzzleProgress(data),
        ) { facts, stored, progress ->
            Triple(facts, stored, progress)
        }
    return combine(heroes, maps, boards, sources, play) {
            heroes,
            maps,
            (boards, unreadable),
            (drafts, matches),
            (facts, stored, progress),
        ->
        BoardsData(
            boards = boards.map { BoardsRules.row(it, maps, heroes) },
            stored = boards,
            latest = BoardsRules.latest(drafts, matches, heroes),
            play = PlayHubUi(
                available = facts.available,
                resume = BoardsRules.resumeOf(stored, heroes),
                puzzles = BoardsRules.puzzleHub(facts.puzzleIds, progress),
            ),
            unreadable = unreadable,
        )
    }
}

/** What the engine can play: a game needs two playable heroes and a playable map; the puzzles listed. */
private data class EngineFacts(val available: Boolean = false, val puzzleIds: List<String> = emptyList())

@Suppress("TooGenericExceptionCaught") // the worker failing to load (offline before it was cached) = coming soon
private fun engineFacts(data: WebServices): Flow<EngineFacts> = flow {
    emit(EngineFacts())
    val facts = try {
        val info = data.play.engine.call(EngineRequest.Info) as? EngineResponse.Info
        val puzzles = data.play.engine.call(EngineRequest.PuzzleList) as? EngineResponse.Puzzles
        EngineFacts(
            available = info != null && info.playableHeroes.size >= 2 && info.playableMaps.isNotEmpty(),
            puzzleIds = puzzles?.puzzles?.map { it.id }.orEmpty(),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        EngineFacts()
    }
    emit(facts)
}

private fun puzzleProgress(data: WebServices) = data.play.puzzleProgress.progress.catch { emit(emptyMap()) }

/** The group's drafts (W2's store; empty until it can be read, as the phone's .catch). */
internal fun drafts(data: WebServices): Flow<List<DraftRecord>> =
    runCatching {
        data.group.draftRecords.observeDrafts()
    }.getOrElse { flowOf(emptyList()) }.catch { emit(emptyList()) }

internal fun matches(data: WebServices): Flow<List<Match>> =
    runCatching { data.group.matches.observeMatches() }.getOrElse { flowOf(emptyList()) }.catch { emit(emptyList()) }

/** Learning by playing (ADR-035), above the boards: a game to resume, Play against the computer, Puzzles. */
@Composable
private fun PlayHub(play: PlayHubUi, now: Long) {
    val router = LocalServices.current.router
    Div({ classes("goa-board-hub") }) {
        play.resume?.let { ResumeCard(it, now) { router.navigate(Route.Play) } }
        EntryCard(
            icon = "swords",
            title = "Play against the computer",
            text = if (play.available) {
                "The real game on the map: the app runs every rule, the computer plays at a strength from 1 to 10, " +
                    "a coach nudges."
            } else {
                "Coming soon: heroes join as each of their cards is built and tested."
            },
            enabled = play.available,
        ) { router.navigate(Route.NewGame()) }
        val puzzles = play.puzzles
        EntryCard(
            icon = "puzzle",
            title = "Puzzles",
            text = if (puzzles != null) {
                "${puzzles.solved} of ${puzzles.total} solved · hard positions, gentle nudges, the rule behind each answer."
            } else {
                "Coming soon: hard positions to solve, with gentle nudges and the rule behind each answer."
            },
            enabled = puzzles != null,
        ) { router.navigate(Route.Puzzles) }
    }
}

@Composable
private fun EntryCard(icon: String, title: String, text: String, enabled: Boolean, onClick: () -> Unit) {
    Button({
        classes("goa-board-entry")
        if (!enabled) classes("coming")
        attr("type", "button")
        if (!enabled) attr("aria-disabled", "true")
        onClick { if (enabled) onClick() }
    }) {
        Span({ classes("goa-board-entry-icon") }) { Icon(icon) }
        Span({ classes("goa-board-entry-text") }) {
            Span({ classes("t-title-medium") }) { Text(title) }
            Span({ classes("t-body-small") }) { Text(text) }
        }
        if (enabled) Icon("chevron_right", IconSize.SMALL)
    }
}

@Composable
private fun ResumeCard(resume: ResumeUi, now: Long, onResume: () -> Unit) {
    val names = listOf(resume.red, resume.blue).filter { it.isNotEmpty() }
        .joinToString(" vs ") { team -> team.joinToString(", ") { it.name } }
    Div({
        classes("goa-board-resume")
        attr("role", "group")
        attr("aria-label", "Game in progress" + (if (names.isNotEmpty()) ": $names. " else ". ") + resume.summary)
    }) {
        Div({ classes("goa-board-resume-text") }) {
            Span({ classes("goa-board-overline") }) {
                Text(
                    if (resume.savedAt > 0) {
                        "GAME IN PROGRESS · " + changedText(resume.savedAt, now).removePrefix("Changed ").uppercase()
                    } else {
                        "GAME IN PROGRESS"
                    },
                )
            }
            if (resume.red.isNotEmpty() || resume.blue.isNotEmpty()) LineupLine(resume.red, resume.blue)
            P({ classes("t-body-small", "goa-board-muted") }) { Text(resume.summary) }
        }
        GoaButton(if (resume.unreadable) "Open" else "Resume", onResume, ButtonKind.FILLED)
    }
}

/** The two teams' portraits ("◆ Arien Brogan vs ● Wasp"). */
@Composable
internal fun LineupLine(red: List<BoardHero>, blue: List<BoardHero>) {
    Div({ classes("goa-board-lineup") }) {
        LineupTeam(Team.RED, red)
        if (red.isNotEmpty() && blue.isNotEmpty()) Span({ classes("t-label-medium", "goa-board-muted") }) { Text("vs") }
        LineupTeam(Team.BLUE, blue)
    }
}

@Composable
private fun LineupTeam(team: Team, heroes: List<BoardHero>) {
    if (heroes.isEmpty()) return
    Span({
        classes("goa-board-lineup-team")
        attr(
            "aria-label",
            "${dev.quinton.goa.feature.board.BoardText.team(team)}: " + heroes.joinToString(", ") { it.name },
        )
    }) {
        TeamMark(team)
        heroes.forEach { HeroPortrait(it, team, "small") }
    }
}

/** The newest finished draft or match: "Set up" opens its board in one tap; [onEdit] fills the teams instead. */
@Composable
internal fun LatestCard(source: LineupSource, onSetUp: () -> Unit, onEdit: (() -> Unit)? = null) {
    val names = listOf(source.red, source.blue).filter { it.isNotEmpty() }
        .joinToString(" vs ") { team -> team.joinToString(", ") { it.name } }
    val what = "Latest ${source.kind.label.lowercase()}"
    Div({ classes("goa-board-latest") }) {
        Div({
            classes("goa-board-latest-text")
            attr("role", "group")
            attr("aria-label", "$what, ${shortDay(source.at)}: $names")
        }) {
            Span({ classes("goa-board-overline") }) { Text("${what.uppercase()} · ${shortDay(source.at)}") }
            LineupLine(source.red, source.blue)
            P({ classes("t-body-small", "goa-board-muted", "goa-board-ellipsis") }) { Text(names) }
        }
        Div({ classes("goa-board-latest-buttons") }) {
            GoaButton("Set up", onSetUp, ButtonKind.TONAL)
            onEdit?.let { GoaButton("Edit", it, ButtonKind.TEXT) }
        }
    }
}

/** "12 Oct" (the phone's "d MMM"). */
internal fun shortDay(epochMillis: Long): String = dev.quinton.goa.web.players.WebDateLabels.short(epochMillis)

@Composable
private fun BoardRow(board: BoardRowUi, now: Long, onOpen: () -> Unit, onOptions: () -> Unit) {
    Div({ classes("goa-board-row-card") }) {
        Button({
            classes("goa-board-row-open")
            attr("type", "button")
            onClick { onOpen() }
        }) {
            H2({ classes("t-title-medium") }) { Text(board.name) }
            Span({ classes("t-body-small", "goa-board-muted") }) {
                Text(
                    board.mapName + (if (!board.mapVerified) " · Unverified" else "") + " · " +
                        changedText(board.updatedAt, now),
                )
            }
            if (board.red.isNotEmpty() || board.blue.isNotEmpty()) LineupLine(board.red, board.blue)
        }
        IconButton("more", "Options for ${board.name}", onClick = onOptions)
    }
}

@Composable
private fun RenameDialog(name: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by remember { mutableStateOf(name) }
    val valid = text.isNotBlank()
    AlertDialog(
        "Rename board",
        onDismiss,
        buttons = {
            GoaButton("Cancel", onDismiss, ButtonKind.TEXT)
            GoaButton("Rename", { if (valid) onRename(text) }, ButtonKind.TEXT, enabled = valid)
        },
    ) {
        Label(attrs = { classes("goa-board-field") }) {
            Span({ classes("t-label-large") }) { Text("Name") }
            Input(InputType.Text) {
                value(text)
                attr("maxlength", BoardsRules.MAX_NAME.toString())
                attr("enterkeyhint", "done")
                onInput { text = it.value.take(BoardsRules.MAX_NAME) }
                onKeyDown { if (it.key == "Enter" && valid) onRename(text) }
                ref { el ->
                    el.focus()
                    el.select()
                    onDispose { }
                }
            }
        }
    }
}
