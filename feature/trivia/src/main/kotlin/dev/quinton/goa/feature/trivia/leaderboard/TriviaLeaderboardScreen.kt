package dev.quinton.goa.feature.trivia.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.quinton.goa.core.data.trivia.LeaderboardRowUi
import dev.quinton.goa.core.data.trivia.LeaderboardTab
import dev.quinton.goa.core.data.trivia.PlayerRoundsUi
import dev.quinton.goa.core.data.trivia.RoundUi
import dev.quinton.goa.core.data.trivia.TriviaLeaderboardUiState
import dev.quinton.goa.core.data.trivia.TriviaText
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.FootArt
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSheet
import dev.quinton.goa.core.designsystem.component.GoaTabRow
import dev.quinton.goa.core.designsystem.component.PODIUM_PLACES
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.Podium
import dev.quinton.goa.core.designsystem.component.PodiumPlace
import dev.quinton.goa.core.designsystem.component.ScreenFoot
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.trivia.LeaderboardPeriod
import dev.quinton.goa.feature.trivia.home.YouTag

/** Callbacks of the leaderboard screen. */
data class LeaderboardActions(
    val onTab: (LeaderboardTab) -> Unit = {},
    val onPeriod: (LeaderboardPeriod) -> Unit = {},
    val onOpenPlayer: (String) -> Unit = {},
    val onClosePlayer: () -> Unit = {},
    val onRemoveRound: (String) -> Unit = {},
    /** The screen came back into view: "Last 30 days" counts from now. */
    val onResume: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/**
 * The trivia leaderboard (ADR-032): Challenge and Practice tabs, All time / Last 30 days, ranked
 * rows; a player's rounds in a sheet, each removable after a confirmation.
 */
@Composable
fun TriviaLeaderboardScreen(
    state: TriviaLeaderboardUiState,
    actions: LeaderboardActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Screenshots: the remove confirmation for this round id, already open. */
    initialConfirm: String? = null,
) {
    var confirm by rememberSaveable { mutableStateOf(initialConfirm) }
    LifecycleResumeEffect(Unit) {
        actions.onResume()
        onPauseOrDispose {}
    }
    val snackbar = remember { SnackbarHostState() }
    val message = (state as? TriviaLeaderboardUiState.Ready)?.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            actions.onMessageShown()
        }
    }
    GoaScreen(
        title = "Trivia leaderboard",
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                TriviaLeaderboardUiState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).semantics { contentDescription = "Loading the leaderboard" },
                )

                is TriviaLeaderboardUiState.Ready -> BoardContent(state, actions)
            }
        }
    }
    val ready = state as? TriviaLeaderboardUiState.Ready
    val selected = ready?.selected
    if (selected != null) {
        PlayerRoundsSheet(
            rounds = selected,
            period = ready.period,
            onRemove = { confirm = it },
            onDismiss = actions.onClosePlayer,
        )
    }
    val round = confirm?.let { id -> selected?.rounds?.firstOrNull { it.id == id } }
    if (round != null) {
        ConfirmDialog(
            title = "Remove from leaderboard?",
            message = "${round.label} comes off the leaderboard here now, and on the group's other phones " +
                "when they next sync. This can't be undone.",
            confirmLabel = "Remove",
            destructive = true,
            icon = Icons.Outlined.DeleteOutline,
            onConfirm = {
                confirm = null
                actions.onRemoveRound(round.id)
            },
            onDismiss = { confirm = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardContent(state: TriviaLeaderboardUiState.Ready, actions: LeaderboardActions) {
    Column(Modifier.fillMaxSize()) {
        // The app's one page-tab bar, as on a hero page and Players.
        GoaTabRow(
            LeaderboardTab.entries.map(::tabLabel),
            state.tab.ordinal,
            onSelect = { actions.onTab(LeaderboardTab.entries[it]) },
        )
        // A short board ends in the rulebook's battlefield, not in empty navy.
        Box(Modifier.fillMaxSize()) {
            ScreenFoot(FootArt.HALL)
            val podium = state.rows.size >= PODIUM_PLACES && LocalDensity.current.fontScale < PODIUM_FONT_SCALE
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
            ) {
                item(key = "header") {
                    Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            LeaderboardPeriod.entries.forEach { period ->
                                GoaFilterChip(
                                    selected = period == state.period,
                                    onClick = { actions.onPeriod(period) },
                                    label = periodLabel(period),
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            if (state.tab == LeaderboardTab.CHALLENGE) {
                                "Your group only · each player's best Challenge: most right answers, then the " +
                                    "quicker time."
                            } else {
                                "Your group only · every round played as a player, Challenges included: most right " +
                                    "answers, then accuracy."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.rows.isEmpty()) {
                    item(key = "empty") { EmptyBoard(state, Modifier.fillMaxWidth()) }
                } else {
                    item(key = "board-${state.tab}") {
                        // One plate with dividers, as the Players leaderboard.
                        GoaPlate(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).clip(PlateShape),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            // The top three on a podium, the rest as rows under it, in the same plate.
                            if (podium) {
                                Podium(
                                    state.rows.take(PODIUM_PLACES).map { row ->
                                        PodiumPlace(
                                            place = row.place,
                                            label = if (row.tied) "=${row.place}" else "${row.place}",
                                            description = row.spoken,
                                            onClick = { actions.onOpenPlayer(row.player.id) },
                                            onClickLabel = "See rounds",
                                        ) { PodiumStanding(row) }
                                    },
                                    Modifier.padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.md),
                                )
                            }
                            val rows = if (podium) state.rows.drop(PODIUM_PLACES) else state.rows
                            val scoreColumn = scoreColumnWidth(rows)
                            rows.forEachIndexed { i, row ->
                                if (i > 0 || podium) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                RankedRow(row, scoreColumn, onClick = { actions.onOpenPlayer(row.player.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A podium place: the crest large, the name (two lines if it needs them), the ranked figure, the detail. */
@Composable
private fun PodiumStanding(row: LeaderboardRowUi) {
    PlayerAvatar(row.player, size = if (row.place == 1) 60.dp else 50.dp, showFace = true)
    Spacer(Modifier.height(Spacing.xs))
    Text(
        breakable(row.player.name),
        style = nameStyle(MaterialTheme.typography.titleSmall),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (row.isMe) YouTag()
    Text(
        row.main,
        style = GoaTextStyles.scoreMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (row.place == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
    Text(
        row.mainCaption ?: SCORE_CAPTION,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        row.detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun EmptyBoard(state: TriviaLeaderboardUiState.Ready, modifier: Modifier) {
    val challenge = state.tab == LeaderboardTab.CHALLENGE
    when {
        state.olderScores -> EmptyState(
            icon = Icons.Outlined.EventBusy,
            title = if (challenge) "No Challenges in the last 30 days" else "No rounds in the last 30 days",
            message = "Older scores are under All time. Play a round to get back on the board.",
            modifier = modifier,
        )

        challenge -> EmptyState(
            icon = Icons.Outlined.EmojiEvents,
            title = "Play a Challenge to get on the board",
            message = "20 questions on every hero, the same quiz for everyone. On Card trivia, pick who's " +
                "playing and tap Challenge.",
            modifier = modifier,
        )

        else -> EmptyState(
            icon = Icons.Outlined.EmojiEvents,
            title = "Play a round as a player to get on the board",
            message = "Pick who's playing on Card trivia: every round they finish counts here. Guest rounds " +
                "aren't recorded.",
            modifier = modifier,
        )
    }
}

@Composable
private fun RankedRow(row: LeaderboardRowUi, scoreColumn: Dp, onClick: () -> Unit) {
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        LargeRankedRow(row, onClick)
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "See rounds", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = row.spoken }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceBadge(row.place, row.tied)
        Spacer(Modifier.width(Spacing.md))
        PlayerAvatar(row.player, size = scaledIconSize(44.dp, maxScale = 1.4f), showFace = true)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FittedName(breakable(row.player.name), Modifier.weight(1f, fill = false))
                if (row.isMe) {
                    Spacer(Modifier.width(Spacing.sm))
                    YouTag()
                }
            }
            Text(
                row.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        // One width for every row's figure: the scores line up, whatever the name beside them does.
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(scoreColumn)) {
            Text(
                row.main,
                style = GoaTextStyles.scoreMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (row.place == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            (row.mainCaption ?: SCORE_CAPTION).let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * [name] with a break opportunity after each hyphen (a zero-width space): the line breaker does not offer one
 * there by itself, so "Featherstonehaugh-Llewellyn" was cut inside a word.
 */
internal fun breakable(name: String): String = name.replace("-", "-\u200B")

/**
 * A name breaks between words or after a hyphen, never inside a word ("Kit Featherstonehaugh-" / "Llewellyn"):
 * greedy, strict line breaking without hyphenation.
 */
internal fun nameStyle(style: TextStyle): TextStyle = style.copy(
    lineBreak = LineBreak(LineBreak.Strategy.Simple, LineBreak.Strictness.Strict, LineBreak.WordBreak.Default),
    hyphens = Hyphens.None,
)

/**
 * A row's name in two lines at most, broken only between words or after a hyphen: one size smaller when the
 * row's size would need a third line, the ellipsis only when even that can't hold it.
 */
@Composable
private fun FittedName(name: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val large = nameStyle(MaterialTheme.typography.titleMedium)
        val small = nameStyle(MaterialTheme.typography.titleSmall)
        val width = constraints.maxWidth
        val style = remember(name, width, large, small) {
            val fits = !measurer.measure(
                name,
                large,
                maxLines = 2,
                constraints = Constraints(maxWidth = width),
            ).hasVisualOverflow
            if (fits) large else small
        }
        Text(name, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The widest ranked figure (and its caption) of [rows], so the score column is one width down the board. */
@Composable
private fun scoreColumnWidth(rows: List<LeaderboardRowUi>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val score = GoaTextStyles.scoreMedium.copy(fontWeight = FontWeight.SemiBold)
    val caption = MaterialTheme.typography.labelSmall
    return remember(rows, density, score, caption) {
        val px = rows.maxOfOrNull { row ->
            maxOf(
                measurer.measure(row.main, score, maxLines = 1, softWrap = false).size.width,
                measurer.measure(row.mainCaption ?: SCORE_CAPTION, caption, maxLines = 1, softWrap = false).size.width,
            )
        } ?: 0
        maxOf(with(density) { px.toDp() } + SCORE_SLACK, MIN_SCORE_COLUMN)
    }
}

private val SCORE_SLACK = 2.dp
private val MIN_SCORE_COLUMN = 48.dp

/**
 * At large text there is no room for a column on the right: the name (and You) wrap freely and the
 * ranked figure leads the detail line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LargeRankedRow(row: LeaderboardRowUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "See rounds", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = row.spoken }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PlaceBadge(row.place, row.tied)
            Spacer(Modifier.height(Spacing.xs))
            PlayerAvatar(row.player, size = 32.dp)
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(breakable(row.player.name), style = nameStyle(MaterialTheme.typography.titleMedium))
                if (row.isMe) YouTag()
            }
            Text(
                "${row.main} ${row.mainCaption ?: SCORE_CAPTION}",
                style = GoaTextStyles.scoreMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (row.place == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                row.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The place as a number in a medallion (never colour alone): bronze for the top three, the first
 * filled; a shared place carries "=".
 */
@Composable
private fun PlaceBadge(place: Int, tied: Boolean) {
    val podium = place <= PODIUM
    val primary = MaterialTheme.colorScheme.primary
    val background = when {
        place == 1 -> primary
        podium -> primary.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        place == 1 -> MaterialTheme.colorScheme.onPrimary
        podium -> primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .size(scaledIconSize(28.dp, maxScale = 1.6f))
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (tied) "=$place" else "$place",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerRoundsSheet(
    rounds: PlayerRoundsUi,
    period: LeaderboardPeriod,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    GoaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PlayerRoundsContent(rounds, period, onRemove)
    }
}

/**
 * A player's rounds in the period: their best Challenge (the one on the board) first, then the
 * rest newest first, each with "Remove from leaderboard".
 */
@Composable
internal fun PlayerRoundsContent(
    rounds: PlayerRoundsUi,
    period: LeaderboardPeriod,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        item(key = "title") {
            Row(
                Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerAvatar(rounds.player, size = scaledIconSize(40.dp, maxScale = 1.4f))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            rounds.player.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                        )
                        if (rounds.isMe) {
                            Spacer(Modifier.width(Spacing.sm))
                            YouTag()
                        }
                    }
                    Text(
                        "Rounds · ${periodLabel(period)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (rounds.rounds.isEmpty()) {
            item(key = "none") {
                Text(
                    "No rounds in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.screen),
                )
            }
        }
        items(rounds.rounds, key = { it.id }) { round ->
            RoundRow(round, onRemove = { onRemove(round.id) })
        }
    }
}

@Composable
private fun RoundRow(round: RoundUi, onRemove: () -> Unit) {
    Box {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = round.spoken },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        round.kind.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (round.kind != "Practice") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        round.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    round.score,
                    style = GoaTextStyles.score,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Remove from leaderboard: ${round.label}" },
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(scaledIconSize(18.dp)),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text("Remove from leaderboard", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

internal fun tabLabel(tab: LeaderboardTab): String = TriviaText.tabLabel(tab)

internal fun periodLabel(period: LeaderboardPeriod): String = TriviaText.periodLabel(period)

private const val PODIUM = 3

/** From this font scale the podium's three columns give way to rows. */
private const val PODIUM_FONT_SCALE = 1.3f

/** From this font scale the rows reflow ([LargeRankedRow]). */
private const val LARGE_FONT_SCALE = 1.5f

/** A Challenge score ("18/20") says what it counts, as a practice total does ("40 right"). */
private const val SCORE_CAPTION = "right"
