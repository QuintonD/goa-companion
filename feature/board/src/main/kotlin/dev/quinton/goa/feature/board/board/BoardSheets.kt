package dev.quinton.goa.feature.board.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.ui.PieceToken
import dev.quinton.goa.feature.board.ui.boardPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { content() }
}

// ---- Add ---------------------------------------------------------------------------------

/** Add a hero (from the catalog), a minion (team and type) or a token (a short label), then tap where. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddContent(
    state: BoardUiState.Ready,
    onAdd: (NewPiece) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var team by rememberSaveable { mutableStateOf(Team.RED) }
    Column(modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
        SheetTitle("Add a piece", "Then tap an empty space to put it there.", onDone)
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            listOf("Hero", "Minion", "Token").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label, maxLines = 1) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Team", style = MaterialTheme.typography.labelLarge)
            Team.entries.forEach { option ->
                GoaFilterChip(selected = team == option, onClick = { team = option }, label = BoardText.team(option))
            }
        }
        when (tab) {
            0 -> HeroChoices(state, onPick = { onAdd(NewPiece.Hero(it, team)) })
            1 -> MinionChoices(onPick = { onAdd(NewPiece.Minion(team, it)) })
            else -> TokenChoices(onPick = { label, withTeam -> onAdd(NewPiece.Token(label, team.takeIf { withTeam })) })
        }
    }
}

@Composable
private fun HeroChoices(state: BoardUiState.Ready, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val onBoard = state.heroIdsOnBoard
    val heroes = state.heroes.values.sortedBy { it.name }.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
            it.title.contains(query.trim(), ignoreCase = true)
    }
    SearchField(query, onChange = { query = it }, onSearch = { focus.clearFocus() })
    LazyColumn {
        items(heroes, key = { it.id }) { hero ->
            val taken = hero.id in onBoard
            HeroRow(hero, taken, onClick = { onPick(hero.id) })
        }
        if (heroes.isEmpty()) {
            item { Text("No hero matches “${query.trim()}”.", Modifier.padding(Spacing.screen)) }
        }
    }
}

@Composable
private fun HeroRow(hero: BoardHero, taken: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !taken,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroPortrait(
                hero.id,
                hero.portrait,
                null,
                Modifier.size(scaledIconSize(40.dp, maxScale = 1.5f)),
                shape = CircleShape,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    hero.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (taken) "On the board already" else hero.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MinionChoices(onPick: (MinionType) -> Unit) {
    val palette = boardPalette()
    Column(Modifier.padding(horizontal = Spacing.screen)) {
        MinionType.entries.forEach { type ->
            Surface(
                onClick = { onPick(type) },
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Row(Modifier.padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    PieceToken(MinionPiece("preview", null, Team.RED, type), null, 36.dp, palette)
                    Spacer(Modifier.width(Spacing.md))
                    Text(BoardText.minion(type), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TokenChoices(onPick: (String, Boolean) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var withTeam by rememberSaveable { mutableStateOf(false) }
    val trimmed = label.trim()
    Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(TOKEN_LABEL) },
            label = { Text("Label") },
            supportingText = { Text("A token is an obstacle (p.19). Name it after the card that places it.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (trimmed.isNotEmpty()) onPick(trimmed, withTeam) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoaFilterChip(selected = withTeam, onClick = { withTeam = !withTeam }, label = "Belongs to the team")
        }
        Button(
            onClick = { onPick(trimmed, withTeam) },
            enabled = trimmed.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Place token") }
    }
}

// ---- Pieces ------------------------------------------------------------------------------

/** Every piece by team, for TalkBack and quick selection: tap one to select it. */
@Composable
internal fun PiecesContent(
    state: BoardUiState.Ready,
    onSelect: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = boardPalette()
    val groups = listOf(
        "Red" to state.pieces.filter { it.team() == Team.RED && it.at != null },
        "Blue" to state.pieces.filter { it.team() == Team.BLUE && it.at != null },
        "Tokens" to state.pieces.filter { it is TokenPiece && it.team() == null && it.at != null },
        "Waiting in the tray" to state.pieces.filter { it.at == null },
    ).filter { it.second.isNotEmpty() }
    Column(modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
        SheetTitle("Pieces", BoardText.count(state.pieces.size, "piece") + " on this board", onDone)
        LazyColumn(Modifier.weight(1f)) {
            groups.forEach { (title, pieces) ->
                item(key = "header-$title") { SectionHeader(title, Modifier.padding(horizontal = Spacing.screen)) }
                items(pieces.sortedBy { order(it) }, key = { it.id }) { piece ->
                    val name = BoardText.piece(piece) { state.heroes.hero(it).name }
                    val where = piece.at?.let { BoardText.zone(state.map, state.geometry.zoneOf(it)) } ?: "In the tray"
                    Surface(
                        onClick = { onSelect(piece.id) },
                        color = if (piece.id == state.selectedId) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PieceToken(
                                piece,
                                (piece as? HeroPiece)?.let { state.heroes.hero(it.heroId) },
                                scaledIconSize(36.dp, maxScale = 1.4f),
                                palette,
                            )
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    where,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
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
    is TokenPiece -> 9
}

// ---- Push the lane -----------------------------------------------------------------------

/** Push the lane (p.8, steps 2–3): pick the team that won the push, per lane. */
@Composable
internal fun PushDialog(
    state: BoardUiState.Ready,
    onPush: (laneId: String, winner: Team) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Push the lane", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "Which team won the push? The next Zone toward the losing team's Throne becomes the Battle Zone; " +
                        "the minions there are removed and new ones spawn (p.8). Flip the Wave counter at the table.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.map.lanes.forEachIndexed { i, lane ->
                    if (state.map.lanes.size > 1) {
                        Text(
                            "Lane ${i + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.semantics {
                                heading()
                            },
                        )
                    }
                    PushButtons(onPush = { onPush(lane.id, it) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PushButtons(onPush: (Team) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Team.entries.forEach { team ->
            OutlinedButton(
                onClick = { onPush(team) },
                modifier = Modifier.heightIn(min = 48.dp),
                border = BorderStroke(1.dp, teamColor(team)),
            ) {
                TeamMark(team, markSize = 12.dp)
                Spacer(Modifier.width(Spacing.sm))
                Text("${BoardText.team(team)} won", color = teamColor(team))
            }
        }
    }
}

/** Pushing into a Throne Zone: that team wins (p.8). Nothing on the board changes. */
@Composable
internal fun ThroneDialog(winner: Team, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { TeamMark(winner, markSize = 20.dp) },
        title = { Text("${BoardText.team(winner)} wins", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "${BoardText.team(winner)} wins: the Throne is captured. This push would make " +
                    "${BoardText.team(winner.opponent)}'s Throne Zone the Battle Zone (p.8). The board stays as it is.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

// ---- Shared ------------------------------------------------------------------------------

@Composable
internal fun SheetTitle(title: String, subtitle: String?, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onDone) { Text("Done") }
    }
}

@Composable
internal fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Spacing.screen,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = Spacing.sm),
        placeholder = { Text("Search heroes", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    onChange("")
                }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

private const val SHEET_HEIGHT = 0.9f
private const val TOKEN_LABEL = 12
