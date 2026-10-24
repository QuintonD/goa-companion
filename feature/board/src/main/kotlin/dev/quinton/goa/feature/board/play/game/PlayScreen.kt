package dev.quinton.goa.feature.board.play.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.data.game.PlaySpeed
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SheetTitle
import dev.quinton.goa.feature.board.play.table.CardImage
import dev.quinton.goa.feature.board.play.table.GameTable
import dev.quinton.goa.feature.board.play.table.HeroFace
import dev.quinton.goa.feature.board.play.table.ItemIcons
import dev.quinton.goa.feature.board.play.table.NoteSurface
import dev.quinton.goa.feature.board.play.table.TableText

/** What the play screen's buttons do; navigation is the caller's. */
data class PlayCallbacks(
    val onChoose: (Long, Choice) -> Unit = { _, _ -> },
    val onContinue: () -> Unit = {},
    val onTakeBack: () -> Unit = {},
    val onChooseAnyway: () -> Unit = {},
    val onRethink: () -> Unit = {},
    val onCoach: (Boolean) -> Unit = {},
    val onSpeed: (PlaySpeed) -> Unit = {},
    val onRematch: () -> Unit = {},
    val onNewGame: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * A game against the computer: the game table with the computer's moves paced and narrated, the
 * reveal and the end of each round as they happen, the coach's nudges, Take back, the log, and the
 * result at the end. Full screen (no bottom bar).
 */
@Composable
fun PlayScreen(
    state: PlayUiState,
    callbacks: PlayCallbacks,
    modifier: Modifier = Modifier,
    initialLog: Boolean = false,
) {
    val playing = state as? PlayUiState.Playing
    var menu by remember { mutableStateOf(false) }
    var log by rememberSaveable { mutableStateOf(initialLog) }
    var showBoard by rememberSaveable { mutableStateOf(false) }
    val title = when {
        playing == null -> "Game"
        playing.over != null -> "Game over"
        else -> "Game"
    }
    GoaScreen(
        title = title,
        onBack = callbacks.onBack,
        modifier = modifier,
        actions = {
            if (playing != null) {
                IconButton(
                    onClick = callbacks.onTakeBack,
                    enabled = playing.canTakeBack && !playing.busy,
                ) { Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Take back your last choice") }
                IconButton(onClick = { log = true }) {
                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, contentDescription = "Game log")
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (playing.options.coach) "Coach: on" else "Coach: off") },
                            leadingIcon = {
                                if (playing.options.coach) Icon(Icons.Outlined.Check, contentDescription = null)
                            },
                            onClick = {
                                menu = false
                                callbacks.onCoach(!playing.options.coach)
                            },
                        )
                        HorizontalDivider()
                        PlaySpeed.entries.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("Computer speed: ${speedName(speed)}") },
                                leadingIcon = {
                                    if (playing.options.speed ==
                                        speed
                                    ) {
                                        Icon(Icons.Outlined.Check, contentDescription = "Chosen")
                                    }
                                },
                                onClick = {
                                    menu = false
                                    callbacks.onSpeed(speed)
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                PlayUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is PlayUiState.Unplayable -> EmptyState(
                    icon = Icons.Outlined.Hexagon,
                    title = "Can't resume this game",
                    message = state.message,
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedButton(onClick = callbacks.onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Back")
                            }
                            Button(onClick = callbacks.onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.width(Spacing.xs))
                                Text("Delete it")
                            }
                        }
                    },
                )

                is PlayUiState.Playing -> if (state.over != null && !showBoard) {
                    GameOverContent(state, callbacks, onShowBoard = { showBoard = true })
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (state.over != null) {
                            TextButton(
                                onClick = { showBoard = false },
                                modifier = Modifier.padding(horizontal = Spacing.sm).heightIn(min = 48.dp),
                            ) { Text("Back to the result") }
                        }
                        GameTable(
                            state = state.table,
                            onChoose = { choice -> state.table.decision?.let { callbacks.onChoose(it.id, choice) } },
                            status = { Status(state) },
                            interlude = interludeOf(state, callbacks),
                            announcement = state.announcement?.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    if (playing != null && log) {
        BoardSheet(onDismiss = { log = false }) { LogContent(playing.log, onDone = { log = false }) }
    }
}

private fun speedName(speed: PlaySpeed): String = when (speed) {
    PlaySpeed.SLOW -> "Slow"
    PlaySpeed.NORMAL -> "Normal"
    PlaySpeed.FAST -> "Fast"
}

/** What takes the panel over: the coach's nudge first, then the reveal or the end of a round. */
private fun interludeOf(state: PlayUiState.Playing, callbacks: PlayCallbacks): (@Composable ColumnScope.() -> Unit)? {
    state.coach?.let { prompt -> return { CoachCard(prompt, callbacks) } }
    return when (val moment = state.interlude) {
        is Interlude.Reveal -> ({ RevealContent(moment, callbacks.onContinue) })
        is Interlude.RoundEnd -> ({ RoundEndContent(moment, callbacks.onContinue) })
        null -> null
    }
}

/** The computer at work: who is choosing, "thinking…", the last thing that happened; or the wait for card play. */
@Composable
private fun ColumnScope.Status(state: PlayUiState.Playing) {
    val activity = state.activity
    if (state.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (activity == null) return
    if (activity.waitingForOthers) {
        Text(
            "Waiting for the others to play their cards facedown… (p.7)",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    } else if (activity.hero != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroFace(activity.hero, 36.dp)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    "${activity.hero.name} · ${activity.hero.controller}".uppercase(),
                    style = GoaTextStyles.overline,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(activity.prompt ?: "Choosing…", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (activity.thinking) {
            Text("thinking…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (activity.last.isNotEmpty()) {
        NoteSurface {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("JUST NOW", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.secondary)
                activity.last.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** The coach's gentle nudge: never blocking, never choosing for the player. */
@Composable
private fun CoachCard(prompt: CoachPrompt, callbacks: PlayCallbacks) {
    NoteSurface(accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("COACH", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
            Text(
                prompt.note.text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (prompt.note.detail.isNotBlank()) {
                Text(prompt.note.detail, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "You chose: ${prompt.choiceLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = callbacks.onRethink, modifier = Modifier.heightIn(min = 52.dp).weight(1f)) {
                    Text("Rethink")
                }
                OutlinedButton(
                    onClick = callbacks.onChooseAnyway,
                    modifier = Modifier.heightIn(min = 52.dp).weight(1f),
                ) {
                    Text("Choose anyway")
                }
            }
            Text(
                "The coach can be switched off in the menu.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The reveal (p.7): every played card, in the order the heroes act, with its initiative. */
@Composable
internal fun RevealContent(reveal: Interlude.Reveal, onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "CARDS REVEALED · p.7",
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Everyone turns their card face up. The highest initiative acts first.",
            style = MaterialTheme.typography.bodyMedium,
        )
        reveal.order.forEachIndexed { i, entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            ) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 20.dp),
                )
                CardImage(entry.card, 44.dp)
                Spacer(Modifier.width(Spacing.sm))
                HeroFace(entry.hero, 28.dp)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${entry.hero.name} · ${entry.card?.name ?: "a card"}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Initiative ${entry.initiative ?: "?"} · ${entry.hero.controller}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val ties = reveal.order.mapNotNull { it.initiative }.let { it.size != it.toSet().size }
        if (ties) {
            Text(
                "Equal initiative: the Tie Breaker coin decides between teams, a team between its own heroes (p.7).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Continue") }
    }
}

/** The end of a round (p.8, pp.15–16), step by step as it happened, and each level up. */
@Composable
internal fun RoundEndContent(end: Interlude.RoundEnd, onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            "END OF ROUND ${end.round} · p.8",
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        end.lines.forEach { line ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.sm))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (end.levelUps.isNotEmpty()) {
            Text("LEVEL UP · p.15", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
            end.levelUps.forEach { hero ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                    },
                ) {
                    HeroFace(hero, 32.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("${hero.name} · level ${hero.level}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(Spacing.sm))
                    ItemIcons(hero.items, size = 16.dp)
                    if (hero.items.isNotEmpty()) {
                        Spacer(Modifier.width(Spacing.xs))
                        Text(TableText.items(hero.items), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Continue") }
    }
}

/** The log: every event as a sentence, oldest first, the newest in view. */
@Composable
internal fun LogContent(lines: List<String>, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    Column(modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
        SheetTitle("Game log", "Everything that happened, oldest first.", onDone)
        LazyColumn(state = list, modifier = Modifier.weight(1f)) {
            itemsIndexed(lines) { _, line ->
                val heading = line.startsWith("Round ") || line.startsWith("End of round")
                Text(
                    line,
                    style = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    color = if (heading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * The result: the winner and how (the rulebook's way to win), the game in numbers, the coach's
 * turning points, then Rematch (same setup, new coin flip), New game and Done.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameOverContent(state: PlayUiState.Playing, callbacks: PlayCallbacks, onShowBoard: () -> Unit) {
    val over = state.over ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val winner = over.winner
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (winner != null) {
                TeamMark(winner, markSize = scaledIconSize(18.dp))
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                TableText.winner(winner, over.humanTeam),
                style = MaterialTheme.typography.headlineMedium,
                color = winner?.let { teamColor(it) } ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(TableText.winReason(over.reason), style = MaterialTheme.typography.titleMedium)
        over.story?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("THE GAME", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(BoardText.count(over.rounds, "round"), style = MaterialTheme.typography.bodyLarge)
            over.pushes.forEach { (team, n) ->
                Text(
                    "${BoardText.team(team)}: ${BoardText.count(n, "push", "pushes")}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(BoardText.count(over.takeBacks, "take back"), style = MaterialTheme.typography.bodyLarge)
        }
        over.heroes.forEach { summary ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                },
            ) {
                HeroFace(summary.hero, 36.dp)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${summary.hero.name} · ${summary.hero.controller} · level ${summary.hero.level}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        listOf(
                            BoardText.count(summary.defeats, "hero", "heroes") + " defeated",
                            "defeated " + BoardText.count(summary.defeated, "time"),
                            BoardText.count(summary.minions, "minion"),
                            BoardText.count(summary.coins, "coin"),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (over.coachOn) {
            Text("TURNING POINTS · COACH", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
            val points = over.turningPoints
            when {
                points == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(20.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("The coach is looking at the game…", style = MaterialTheme.typography.bodyMedium)
                }

                points.isEmpty() -> Text(
                    "The coach found no single moment that decided this game.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> points.forEach { note ->
                    NoteSurface(accent = MaterialTheme.colorScheme.primary) {
                        Column {
                            val `when` = listOfNotNull(note.round?.let { "Round $it" }, note.turn?.let { "turn $it" })
                            if (`when`.isNotEmpty()) {
                                Text(
                                    `when`.joinToString(", ").uppercase(),
                                    style = GoaTextStyles.overline,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(note.text, style = MaterialTheme.typography.titleSmall)
                            if (note.detail.isNotBlank()) Text(note.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Button(onClick = callbacks.onRematch, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Rematch: same setup, new coin flip")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = callbacks.onNewGame, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text("New game")
            }
            OutlinedButton(onClick = callbacks.onDone, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text("Done")
            }
        }
        TextButton(onClick = onShowBoard, modifier = Modifier.heightIn(min = 48.dp)) { Text("See the final board") }
    }
}
