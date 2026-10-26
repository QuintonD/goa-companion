package dev.quinton.goa.feature.board.list

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.changedHere
import dev.quinton.goa.feature.board.setup.LatestCard
import dev.quinton.goa.feature.board.setup.LineupSource

/**
 * Saved boards, newest change first, under the latest draft's or match's line-up (Set up in one
 * tap), and "New board".
 */
@Composable
fun BoardsScreen(
    state: BoardsUiState,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSetUp: (LineupSource) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit = {},
    onResume: () -> Unit = {},
    onPuzzles: () -> Unit = {},
) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    val ready = state as? BoardsUiState.Ready
    GoaScreen(
        title = "Boards",
        onBack = onBack,
        modifier = modifier,
        floatingActionButton = {
            if (ready != null && ready.boards.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("New board") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ready == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                ready.boards.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PlayHub(
                        ready.play,
                        ready.now,
                        onPlay,
                        onResume,
                        Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.lg),
                        onPuzzles = onPuzzles,
                    )
                    ready.latest?.let {
                        LatestCard(it, { onSetUp(it) }, Modifier.padding(Spacing.screen))
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.lg)) {
                        EmptyState(
                            icon = Icons.Outlined.Hexagon,
                            title = "No boards yet",
                            message = if (ready.unreadable) {
                                "The saved boards couldn't be read."
                            } else {
                                "Set up any line-up on the real map and see who can reach whom."
                            },
                            modifier = Modifier.align(Alignment.Center),
                            action = {
                                Button(onClick = onNew, modifier = Modifier.heightIn(min = 52.dp)) {
                                    Icon(Icons.Outlined.Add, contentDescription = null)
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text("New board")
                                }
                            },
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.lg,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "play") { PlayHub(ready.play, ready.now, onPlay, onResume, onPuzzles = onPuzzles) }
                    item(key = "boards") {
                        Text(
                            "BOARDS",
                            style = GoaTextStyles.overline,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
                        )
                    }
                    ready.latest?.let { latest ->
                        item(key = "latest") { LatestCard(latest, { onSetUp(latest) }) }
                    }
                    items(ready.boards, key = { it.id }) { board ->
                        BoardRow(
                            board = board,
                            now = ready.now,
                            onOpen = { onOpen(board.id) },
                            onRename = { renaming = board.id },
                            onDuplicate = { onDuplicate(board.id) },
                            onDelete = { onDelete(board.id) },
                        )
                    }
                }
            }
        }
    }
    val board = ready?.boards?.firstOrNull { it.id == renaming }
    if (board != null) {
        RenameDialog(
            name = board.name,
            onRename = {
                onRename(board.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardRow(
    board: BoardRowUi,
    now: Long,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val changed = BoardText.changedHere(board.updatedAt, now)
    GoaCard(
        onClick = onOpen,
        contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(
                    board.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    verticalArrangement = Arrangement.Center,
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        board.mapName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!board.mapVerified) {
                        Spacer(Modifier.width(Spacing.sm))
                        UnverifiedBadge()
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    LineupHeroes(Team.RED, board.red)
                    if (board.red.isNotEmpty() && board.blue.isNotEmpty()) {
                        Text(
                            "vs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LineupHeroes(Team.BLUE, board.blue)
                }
                Text(
                    changed,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Options for ${board.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** A team's heroes as small portraits ringed in the team colour, with the team's shape first. */
@Composable
internal fun LineupHeroes(team: Team, heroes: List<BoardHero>, size: Dp = 28.dp) {
    if (heroes.isEmpty()) return
    val color = teamColor(team)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "${BoardText.team(team)}: " + heroes.joinToString(", ") { it.name }
        },
    ) {
        TeamMark(team, markSize = scaledIconSize(10.dp))
        Spacer(Modifier.width(Spacing.xs))
        heroes.forEach { hero ->
            HeroPortrait(
                heroId = hero.id,
                portrait = hero.portrait,
                contentDescription = null,
                shape = CircleShape,
                framed = false,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(scaledIconSize(size, maxScale = 1.4f))
                    .border(1.5.dp, color, CircleShape),
            )
        }
    }
}

@Composable
private fun RenameDialog(name: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    // Opens ready to type: the field focused, the old name selected so typing replaces it.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(name, selection = TextRange(0, name.length)))
    }
    val text = field.text
    val valid = text.isNotBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename board") },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = {
                    field = if (it.text.length > BoardsViewModel.MAX_NAME) {
                        it.copy(text = it.text.take(BoardsViewModel.MAX_NAME))
                    } else {
                        it
                    }
                },
                singleLine = true,
                label = { Text("Name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onRename(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onRename(text) }, enabled = valid) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    )
}
