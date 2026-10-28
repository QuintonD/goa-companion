package dev.quinton.goa.web.play.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardAction
import dev.quinton.goa.feature.board.board.BoardUiState
import dev.quinton.goa.feature.board.board.NewPiece
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.ownedIn
import dev.quinton.goa.web.play.map.MapCamera
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.ChipRow
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.Segmented
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

private enum class AddTab(val label: String) { HERO("Hero"), MINION("Minion"), TOKEN("Token") }

/** Add a piece (a hero, owned products' first; a minion by team and type; a token, an obstacle), then tap where. */
@Composable
internal fun AddSheet(state: BoardUiState.Ready, onDismiss: () -> Unit, onAdd: (NewPiece) -> Unit) {
    var tab by remember { mutableStateOf(AddTab.HERO) }
    var team by remember { mutableStateOf(Team.RED) }
    BottomSheet("Add a piece", onDismiss) {
        P({ classes("t-body-medium", "goa-board-muted") }) { Text("Then tap an empty space to put it there.") }
        Segmented("What to add", AddTab.entries, tab, { it.label }) { tab = it }
        Div({ classes("goa-board-row") }) {
            Span({ classes("t-label-large") }) { Text("Team") }
            ChipRow {
                Team.entries.forEach { option -> Chip(BoardText.team(option), team == option, { team = option }) }
            }
        }
        when (tab) {
            AddTab.HERO -> HeroChoices(state) { onAdd(NewPiece.Hero(it, team)) }

            AddTab.MINION -> RowList {
                MinionType.entries.forEach { type ->
                    ListRow(BoardText.minion(type), onClick = {
                        onAdd(NewPiece.Minion(team, type))
                    }, leading = { MinionAvatar(type, team) })
                }
            }

            AddTab.TOKEN -> TokenChoices { label, withTeam -> onAdd(NewPiece.Token(label, team.takeIf { withTeam })) }
        }
    }
}

@Composable
private fun HeroChoices(state: BoardUiState.Ready, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(false) }
    val onBoard = state.heroIdsOnBoard
    val everyone = state.heroes.values.sortedBy { it.name }
    val owned = everyone.filter { it.ownedIn(state.ownedProducts) }
    val hidden = everyone.size - owned.size
    val q = query.trim()
    val heroes = (if (all) everyone else owned).filter {
        q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.title.contains(q, ignoreCase = true)
    }
    SearchField(query, "Search heroes") { query = it }
    if (heroes.isEmpty()) P({ classes("t-body-medium") }) { Text("No hero matches “$q”.") }
    RowList {
        heroes.forEach { hero ->
            val taken = hero.id in onBoard
            ListRow(
                hero.name,
                subtitle = if (taken) "On the board already" else hero.title,
                onClick = { if (!taken) onPick(hero.id) },
                leading = { HeroPortrait(hero, size = "medium") },
            )
        }
    }
    if (hidden > 0) {
        GoaButton(
            if (all) "Only heroes you own" else "Show all heroes ($hidden you don't own)",
            { all = !all },
            ButtonKind.TEXT,
            block = true,
        )
    }
}

@Composable
private fun TokenChoices(onPick: (String, Boolean) -> Unit) {
    var label by remember { mutableStateOf("") }
    var withTeam by remember { mutableStateOf(false) }
    val trimmed = label.trim()
    Label(attrs = { classes("goa-board-field") }) {
        Span({ classes("t-label-large") }) { Text("Label") }
        Input(InputType.Text) {
            value(label)
            attr("maxlength", TOKEN_LABEL.toString())
            attr("enterkeyhint", "done")
            attr("autocapitalize", "words")
            onInput { label = it.value.take(TOKEN_LABEL) }
            onKeyDown { if (it.key == "Enter" && trimmed.isNotEmpty()) onPick(trimmed, withTeam) }
        }
        Span({ classes("t-body-small", "goa-board-muted") }) {
            Text("A token is an obstacle (p.18). Name it after the card that places it.")
        }
    }
    ChipRow { Chip("Belongs to the team", withTeam, { withTeam = !withTeam }) }
    GoaButton("Place token", { onPick(trimmed, withTeam) }, enabled = trimmed.isNotEmpty(), block = true)
}

/** A search box (type="search", the iPhone's keyboard with its Search key). */
@Composable
internal fun SearchField(query: String, placeholder: String, onChange: (String) -> Unit) {
    Div({ classes("goa-board-search") }) {
        Input(InputType.Search) {
            value(query)
            attr("placeholder", placeholder)
            attr("aria-label", placeholder)
            attr("enterkeyhint", "search")
            attr("autocomplete", "off")
            onInput { onChange(it.value) }
        }
    }
}

/** Every piece by team, for VoiceOver and quick selection: tap one to select it. */
@Composable
internal fun PiecesSheet(state: BoardUiState.Ready, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val groups = listOf(
        "Red" to state.pieces.filter { it.team() == Team.RED && it.at != null },
        "Blue" to state.pieces.filter { it.team() == Team.BLUE && it.at != null },
        "Tokens" to state.pieces.filter { it is TokenPiece && it.team() == null && it.at != null },
        "Waiting in the tray" to state.pieces.filter { it.at == null },
    )
    BottomSheet("Pieces", onDismiss) {
        P({ classes("t-body-medium", "goa-board-muted") }) {
            Text(BoardText.count(state.pieces.size, "piece") + " on this board")
        }
        groups.filter { it.second.isNotEmpty() }.forEach { (title, pieces) ->
            SectionHeader(title)
            RowList {
                pieces.sortedBy(::order).forEach { piece ->
                    ListRow(
                        title = BoardText.piece(piece) { state.heroes.hero(it).name },
                        subtitle = state.whereIs(piece),
                        trailing = if (piece.id == state.selectedId) "Selected" else null,
                        onClick = { onSelect(piece.id) },
                        leading = { PieceAvatar(piece, state.heroes) },
                    )
                }
            }
        }
    }
}

private fun BoardPiece.team(): Team? = when (this) {
    is HeroPiece -> team
    is MinionPiece -> team
    is TokenPiece -> team
}

private fun order(piece: BoardPiece): Int = when (piece) {
    is HeroPiece -> 0
    is MinionPiece -> 1 + piece.type.ordinal
    is TokenPiece -> MinionType.entries.size + 1
}

/** Push the lane (p.8, steps 2–3): which team won the push; per Lane on a double-lane map. */
@Composable
internal fun PushDialog(
    state: BoardUiState.Ready,
    onDismiss: () -> Unit,
    onPush: (laneId: String, winner: Team) -> Unit,
) {
    val lanes = state.map.lanes
    AlertDialog(
        if (lanes.size > 1) "Push a lane" else "Push the lane",
        onDismiss,
        buttons = { GoaButton("Cancel", onDismiss, ButtonKind.TEXT) },
    ) {
        P {
            Text(
                "Which team won the push? The next Zone toward the losing team's Throne becomes the Battle " +
                    "Zone. The minions of the previous Battle Zone are removed, and one minion spawns on each " +
                    "Spawn point of the new one (p.8). Flip the Wave counter at the table.",
            )
        }
        lanes.forEach { lane ->
            if (lanes.size > 1) {
                val now = state.board.state.battleZones[lane.id] ?: lane.startingBattleZone
                H3({ classes("t-title-small") }) { Text(BoardText.battleZone(state.map, lane.id)) }
                P({ classes("t-body-small", "goa-board-muted") }) { Text("Now ${BoardText.zone(state.map, now)}") }
            }
            Div({ classes("goa-board-push") }) {
                Team.entries.forEach { team ->
                    val laneName = BoardText.lane(state.map, lane.id)
                    Button({
                        classes("goa-button", "outlined", "goa-board-team-button", teamClass(team))
                        attr("type", "button")
                        attr(
                            "aria-label",
                            listOfNotNull(laneName, "${BoardText.team(team)} won the push").joinToString(": "),
                        )
                        onClick { onPush(lane.id, team) }
                    }) {
                        TeamMark(team)
                        Text("${BoardText.team(team)} won")
                    }
                }
            }
        }
    }
}

/** Pushing into a Throne Zone: that team wins (p.8). Nothing on the board changes. */
@Composable
internal fun ThroneDialog(winner: Team, onDismiss: () -> Unit) {
    AlertDialog("${BoardText.team(winner)} wins", onDismiss, buttons = {
        GoaButton("OK", onDismiss, ButtonKind.TEXT)
    }) {
        P {
            Text(
                "${BoardText.team(winner)} wins: the Throne is captured. This push would make " +
                    "${BoardText.team(winner.opponent)}'s Throne Zone the Battle Zone (p.8). The board stays as it is.",
            )
        }
    }
}

@Composable
internal fun ResetDialog(onDismiss: () -> Unit, onReset: () -> Unit) {
    AlertDialog(
        "Reset to opening?",
        onDismiss,
        buttons = {
            GoaButton("Cancel", onDismiss, ButtonKind.TEXT)
            GoaButton("Reset", onReset, ButtonKind.DANGER)
        },
    ) {
        P {
            Text(
                "The board goes back to the opening for this line-up (p.6): every hero and minion on " +
                    "its Spawn point, each Lane's Battle Zone where it starts. Heroes, minions and tokens you " +
                    "added are removed. You can undo it.",
            )
        }
    }
}

/** The top bar's More: flip, fit, reset (the phone's overflow menu, with the map's buttons). */
@Composable
internal fun MoreSheet(
    state: BoardUiState.Ready,
    camera: MapCamera,
    onDismiss: () -> Unit,
    onPick: (BoardOverlay) -> Unit,
    onAction: (BoardAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    BottomSheet("Board", onDismiss) {
        RowList {
            ListRow(
                "Flip the board",
                "${BoardText.team(
                    state.bottomTeam.opponent,
                )} at the bottom",
                leadingIcon = "flip",
                onClick = {
                    onAction(BoardAction.Flip)
                    onDismiss()
                },
            )
            ListRow(if (camera.isFit) "Zoom to the lane" else "Fit the whole board", leadingIcon = "fit", onClick = {
                scope.launch { if (camera.isFit) camera.animateLane() else camera.animateFit() }
                onDismiss()
            })
            ListRow("Add a piece", leadingIcon = "add", onClick = { onPick(BoardOverlay.ADD) })
            ListRow("Pieces", leadingIcon = "list", onClick = { onPick(BoardOverlay.PIECES) })
            ListRow("Push the lane", leadingIcon = "chevron_up", onClick = { onPick(BoardOverlay.PUSH) })
            ListRow("Reset to opening", leadingIcon = "restart", onClick = { onPick(BoardOverlay.RESET) })
        }
    }
}

/** Minions that could not respawn on a taken Spawn point (p.8), placed by tapping a highlighted space. */
@Composable
internal fun WaitingTray(state: BoardUiState.Ready, onAction: (BoardAction) -> Unit) {
    Div({ classes("goa-board-tray") }) {
        Span({ classes("goa-board-overline", "teal") }) { Text("WAITING TO RESPAWN · P.8") }
        P({
            classes("t-body-medium")
            attr("aria-live", "polite")
        }) {
            Text(
                when {
                    !state.trayActive -> "Deselect the piece, then tap a waiting minion to place it."

                    state.activeWaiting?.targets?.isNotEmpty() == true ->
                        "Its Spawn point is taken. Tap a highlighted space: the nearest empty spaces of its Battle Zone."

                    else -> "Tap an empty space to put it on the board."
                },
            )
        }
        Div({
            classes("goa-board-tray-pieces")
            attr("role", "radiogroup")
            attr("aria-label", "Waiting pieces")
        }) {
            state.waiting.forEach { waiting ->
                val piece = waiting.piece
                val active = piece.id == state.activeWaitingId && state.trayActive
                val lane = (piece as? MinionPiece)?.let { BoardText.lane(state.map, state.geometry.laneOf(it)) }
                val name = listOfNotNull(
                    lane,
                    BoardText.piece(piece) {
                        state.heroes.hero(it).name
                    },
                ).joinToString(" · ")
                Button({
                    classes("goa-board-tray-piece")
                    attr("type", "button")
                    attr("role", "radio")
                    attr("aria-checked", active.toString())
                    onClick { onAction(BoardAction.ActivateWaiting(piece.id)) }
                }) {
                    PieceAvatar(piece, state.heroes)
                    Span({ classes("t-label-large") }) { Text(name) }
                }
            }
        }
    }
}

private const val TOKEN_LABEL = 12
