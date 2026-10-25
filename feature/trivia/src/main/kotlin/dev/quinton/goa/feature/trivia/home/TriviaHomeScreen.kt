package dev.quinton.goa.feature.trivia.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.data.trivia.HeroProgressUi
import dev.quinton.goa.core.data.trivia.PlayingAsState
import dev.quinton.goa.core.data.trivia.TriviaHomeAction
import dev.quinton.goa.core.data.trivia.TriviaHomeUiState
import dev.quinton.goa.core.data.trivia.TriviaText
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.feature.trivia.ui.MasteryBar
import dev.quinton.goa.feature.trivia.ui.MasteryLegend
import dev.quinton.goa.feature.trivia.ui.SetupLabel
import dev.quinton.goa.feature.trivia.ui.spokenUnverified

/**
 * Trivia home: who is playing, overall progress, the quiz setup, and progress per hero; Start quiz
 * and the Challenge pinned at the bottom, the leaderboard on the top bar (ADR-028, ADR-032).
 */
@Suppress("LongParameterList")
@Composable
fun TriviaHomeScreen(
    state: TriviaHomeUiState,
    onAction: (TriviaHomeAction) -> Unit,
    onStart: () -> Unit,
    onStartHero: (heroId: String) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialHeroPicker: Boolean = false,
    onChallenge: () -> Unit = {},
    onLeaderboard: () -> Unit = {},
    playingAsActions: PlayingAsActions = PlayingAsActions(),
    initialPlayingAsPicker: PlayingAsPicker? = null,
) {
    val ready = state as? TriviaHomeUiState.Ready
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(initialHeroPicker) }
    var playingAsPicker by rememberSaveable { mutableStateOf(initialPlayingAsPicker) }
    BoxWithConstraints(modifier) {
        val trophyInBar = leaderboardInBar(maxWidth)
        GoaScreen(
            title = HOME_TITLE,
            onBack = onBack,
            actions = {
                if (ready != null) {
                    if (trophyInBar) {
                        IconButton(onClick = onLeaderboard) {
                            Icon(Icons.Outlined.EmojiEvents, contentDescription = "Leaderboard")
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (!trophyInBar) {
                                DropdownMenuItem(
                                    text = { Text("Leaderboard") },
                                    leadingIcon = { Icon(Icons.Outlined.EmojiEvents, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        onLeaderboard()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Reset progress") },
                                leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                                enabled = ready.stored.mastered + ready.stored.learning > 0,
                                onClick = {
                                    menu = false
                                    confirmReset = true
                                },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (state) {
                    TriviaHomeUiState.Loading -> CircularProgressIndicator(
                        Modifier.align(Alignment.Center).semantics { contentDescription = "Loading trivia" },
                    )

                    TriviaHomeUiState.Unavailable -> EmptyState(
                        icon = Icons.Outlined.Quiz,
                        title = "No heroes to quiz",
                        message = "The bundled hero data couldn't be read, so there are no card numbers to learn.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is TriviaHomeUiState.Ready -> HomeContent(
                        state = state,
                        onAction = onAction,
                        onStart = onStart,
                        onStartHero = onStartHero,
                        onChooseHeroes = { picker = true },
                        onChoosePlayer = { playingAsPicker = PlayingAsPicker.CHOOSE },
                        onChallenge = {
                            // A Challenge goes on the board under a name: Guest picks one first.
                            if (state.playingAs.isGuest) playingAsPicker = PlayingAsPicker.CHALLENGE else onChallenge()
                        },
                    )
                }
            }
        }
    }
    val pickerMode = playingAsPicker
    if (ready != null && pickerMode != null) {
        PlayingAsSheet(
            state = ready.playingAs,
            mode = pickerMode,
            actions = playingAsActions,
            onDismiss = { playingAsPicker = null },
        )
    }
    if (ready != null && picker) {
        HeroPickerSheet(
            options = ready.heroOptions,
            selected = ready.setup.heroIds,
            ownedOnly = ready.ownedOnly,
            ownedCount = ready.ownedHeroCount,
            onToggle = { onAction(TriviaHomeAction.ToggleHero(it)) },
            onAll = { onAction(TriviaHomeAction.AllHeroes) },
            onDismiss = { picker = false },
        )
    }
    if (ready != null && confirmReset) {
        val learned = ready.stored.mastered
        val learning = ready.stored.learning
        ConfirmDialog(
            title = "Reset progress?",
            message = buildString {
                append("This forgets ")
                append(TriviaText.count(learned, "learned number"))
                if (learning > 0) append(" and ${TriviaText.count(learning, "number")} you're still learning")
                append(". Every card number starts again as new. This can't be undone.")
            },
            confirmLabel = "Reset",
            destructive = true,
            icon = Icons.Outlined.RestartAlt,
            onConfirm = {
                confirmReset = false
                onReset()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun HomeContent(
    state: TriviaHomeUiState.Ready,
    onAction: (TriviaHomeAction) -> Unit,
    onStart: () -> Unit,
    onStartHero: (String) -> Unit,
    onChooseHeroes: () -> Unit,
    onChoosePlayer: () -> Unit,
    onChallenge: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The start bar's content is inset by the screen padding on both sides.
        val layout = barLayout(maxWidth - Spacing.screen * 2)
        HomeColumn(state, onAction, onStart, onStartHero, onChooseHeroes, onChoosePlayer, onChallenge, layout)
    }
}

/**
 * How the pinned bar lays out its buttons. [wide]: text and buttons side by side (landscape).
 * [stacked]: Start quiz and Challenge don't both fit their half of the row at this width and text
 * size, so they stack, and the Challenge line moves into the list to leave the list room.
 */
private data class BarLayout(val wide: Boolean, val stacked: Boolean)

/** Measures the two button labels (with icon, gap and padding) against half of [width]. */
@Composable
private fun barLayout(width: Dp): BarLayout {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium
    val icon = scaledIconSize(20.dp)
    return remember(width, density, style, icon) {
        if (width >= WIDE_START_BAR) return@remember BarLayout(wide = true, stacked = false)
        fun need(label: String) = with(density) {
            measurer.measure(label, style, maxLines = 1, softWrap = false).size.width.toDp()
        } + icon + Spacing.sm + BUTTON_CHROME
        val half = (width - Spacing.sm) / 2
        BarLayout(wide = false, stacked = need(START_LABEL) > half || need(CHALLENGE_LABEL) > half)
    }
}

/** A button's horizontal content padding (24 dp each side), border and a rounding margin. */
private val BUTTON_CHROME = 54.dp
private const val START_LABEL = "Start quiz"
private const val CHALLENGE_LABEL = "Challenge"

@Suppress("LongParameterList")
@Composable
private fun HomeColumn(
    state: TriviaHomeUiState.Ready,
    onAction: (TriviaHomeAction) -> Unit,
    onStart: () -> Unit,
    onStartHero: (String) -> Unit,
    onChooseHeroes: () -> Unit,
    onChoosePlayer: () -> Unit,
    onChallenge: () -> Unit,
    layout: BarLayout,
) {
    val largeText = layout.stacked
    val padded = Modifier.padding(horizontal = Spacing.screen)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.xl),
        ) {
            item(key = "playing-as") { PlayingAsRow(state.playingAs, onChoosePlayer, padded) }
            if (largeText) {
                item(key = "challenge-line") {
                    Text(
                        CHALLENGE_LINE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = padded.padding(top = Spacing.sm),
                    )
                }
            }
            item(key = "overall") { OverallCard(state, padded.padding(top = Spacing.md)) }
            item(key = "setup-title") { SectionHeader("New quiz", padded) }
            item(key = "setup") { SetupBlock(state, onAction, onChooseHeroes, padded) }
            item(key = "heroes-title") {
                SectionHeader(
                    "By hero",
                    padded,
                    trailing = {
                        Text(
                            "Tap one to quiz it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            items(state.heroes, key = { "hero-${it.heroId}" }) { hero ->
                HeroProgressRow(hero, onClick = { onStartHero(hero.heroId) })
            }
        }
        // Pinned: the live count, Start and the Challenge are always in reach, however far the list is scrolled.
        StartBar(state, onStart, onChallenge, layout)
    }
}

/**
 * "Playing as Sam ▾" with the player's crest; the line under it says whether the rounds go on the
 * leaderboard, and a note when the player isn't this phone's "You" (whose learning progress this is).
 */
@Composable
private fun PlayingAsRow(playing: PlayingAsState, onClick: () -> Unit, modifier: Modifier) {
    val name = playing.player?.name ?: "Guest"
    val isMe = playing.player?.let { it.id == playing.me?.id } == true
    val subtitle = when {
        playing.isGuest -> "Not on the leaderboard"
        else -> "Rounds go on the leaderboard"
    }
    val note = playingAsNote(playing)
    Column(modifier.fillMaxWidth()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = "Change who's playing", role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Playing as $name" + (if (isMe) ", you" else "") + ", $subtitle"
                },
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayingAvatar(playing.player, size = scaledIconSize(40.dp, maxScale = 1.5f))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Playing as $name",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(
                            Icons.Outlined.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(scaledIconSize(24.dp)),
                        )
                        if (isMe) {
                            Spacer(Modifier.width(Spacing.xs))
                            YouTag()
                        }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

/**
 * When the player isn't this phone's "You": whose learning progress this is (where the rounds go is
 * the row's own line). Null when they are, or when no "You" is set (then anyone's rounds teach this
 * phone).
 */
internal fun playingAsNote(playing: PlayingAsState): String? = TriviaText.playingAsNote(playing)

@Composable
private fun OverallCard(state: TriviaHomeUiState.Ready, modifier: Modifier) {
    val overall = state.overall
    GoaCard(modifier.fillMaxWidth()) {
        Text(
            "${TriviaText.number(overall.mastered)} of ${TriviaText.count(overall.total, "number")} learned",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            if (state.ownedOnly) {
                "Every number on the Action cards of the heroes you own."
            } else {
                "Every number on every hero's Action cards."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        MasteryBar(overall)
        Spacer(Modifier.height(Spacing.md))
        MasteryLegend(overall)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "A number counts as learned after three right answers in a row.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupBlock(
    state: TriviaHomeUiState.Ready,
    onAction: (TriviaHomeAction) -> Unit,
    onChooseHeroes: () -> Unit,
    modifier: Modifier,
) {
    val setup = state.setup
    val chipSpacing = Arrangement.spacedBy(Spacing.sm)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        // Heroes
        Column {
            SetupLabel("Heroes")
            HeroScopeRow(state, onChooseHeroes)
        }
        Column {
            SetupLabel("Numbers")
            FlowRow(horizontalArrangement = chipSpacing) {
                TriviaStat.entries.forEach { stat ->
                    GoaFilterChip(
                        selected = stat in setup.stats,
                        onClick = { onAction(TriviaHomeAction.ToggleStat(stat)) },
                        label = TriviaText.stat(stat),
                    )
                }
            }
        }
        Column {
            SetupLabel("Cards")
            FlowRow(horizontalArrangement = chipSpacing) {
                CardGroup.entries.forEach { group ->
                    GoaFilterChip(
                        selected = group in setup.groups,
                        onClick = { onAction(TriviaHomeAction.ToggleGroup(group)) },
                        label = TriviaText.group(group),
                    )
                }
            }
        }
        Column {
            SetupLabel("Answer with")
            FlowRow(horizontalArrangement = chipSpacing) {
                GoaFilterChip(
                    selected = setup.answerStyle == AnswerStyle.CHOICES,
                    onClick = { onAction(TriviaHomeAction.SetAnswerStyle(AnswerStyle.CHOICES)) },
                    label = "4 choices",
                )
                GoaFilterChip(
                    selected = setup.answerStyle == AnswerStyle.RECALL,
                    onClick = { onAction(TriviaHomeAction.SetAnswerStyle(AnswerStyle.RECALL)) },
                    label = "Recall",
                )
            }
            Text(
                if (setup.answerStyle == AnswerStyle.CHOICES) {
                    "Pick the number from four close ones."
                } else {
                    "Pick from every number the stat has on any card: no hints."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column {
            SetupLabel("Questions")
            FlowRow(horizontalArrangement = chipSpacing) {
                TriviaConfig.LENGTHS.forEach { length ->
                    GoaFilterChip(
                        selected = setup.length == length,
                        onClick = { onAction(TriviaHomeAction.SetLength(length)) },
                        label = TriviaText.length(length),
                    )
                }
            }
        }
        DuelsRow(state, onAction)
    }
}

@Composable
private fun HeroScopeRow(state: TriviaHomeUiState.Ready, onChooseHeroes: () -> Unit) {
    val names = state.selectedHeroNames
    val summary = when {
        names.isEmpty() -> if (state.ownedOnly) "All heroes you own" else "All heroes"
        names.size <= 2 -> names.joinToString(", ")
        else -> "${names.take(2).joinToString(", ")} +${names.size - 2}"
    }
    // All heroes = the owned ones; the options also list a chosen hero outside them.
    val count = if (names.isEmpty()) state.ownedHeroCount else names.size
    Surface(
        onClick = onChooseHeroes,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "Heroes: $summary, ${TriviaText.count(count, "hero", "heroes")}" },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    TriviaText.count(count, "hero", "heroes"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "Choose",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DuelsRow(state: TriviaHomeUiState.Ready, onAction: (TriviaHomeAction) -> Unit) {
    val enabled = state.initiativeSelected
    val checked = enabled && state.setup.duels
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onAction(TriviaHomeAction.SetDuels(it)) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Initiative duels",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.run { if (enabled) onSurface else onSurfaceVariant },
            )
            Text(
                if (enabled) {
                    "Now and then: which of two cards has the higher initiative?"
                } else {
                    "Needs Initiative under Numbers."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The bar pinned under the home's list: the live "N numbers in this quiz · M learned" line (or why
 * Start is off), Start quiz and the Challenge with its one-line explanation. Side by side on a wide
 * window (landscape), so the list keeps room; the two buttons stack at large text.
 */
@Composable
private fun StartBar(
    state: TriviaHomeUiState.Ready,
    onStart: () -> Unit,
    onChallenge: () -> Unit,
    layout: BarLayout,
) {
    val selection = state.selection
    val line = "${TriviaText.count(selection.total, "number")} in this quiz · " +
        "${TriviaText.number(selection.mastered)} learned"
    val note = startNote(state)
    val noteColor = MaterialTheme.colorScheme.run { if (state.canStart) onSurfaceVariant else error }
    val stacked = layout.stacked
    val wide = layout.wide
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            EngravedRule()
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            ) {
                val texts: @Composable () -> Unit = {
                    Text(line, style = GoaTextStyles.numeric, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (note != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(note, style = MaterialTheme.typography.bodyMedium, color = noteColor)
                    }
                }
                val explain: @Composable () -> Unit = {
                    Text(
                        CHALLENGE_LINE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (wide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            texts()
                            Spacer(Modifier.height(Spacing.xs))
                            explain()
                        }
                        Spacer(Modifier.width(Spacing.lg))
                        ChallengeButton(onChallenge, Modifier.widthIn(min = 160.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        StartButton(state.canStart, onStart, Modifier.widthIn(min = 200.dp))
                    }
                } else {
                    Column {
                        texts()
                        Spacer(Modifier.height(Spacing.sm))
                        if (stacked) {
                            // Stacked: the explanation sits in the list (under Playing as) so the
                            // pinned bar leaves the list room.
                            StartButton(state.canStart, onStart, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(Spacing.sm))
                            ChallengeButton(onChallenge, Modifier.fillMaxWidth())
                        } else {
                            Row {
                                ChallengeButton(onChallenge, Modifier.weight(1f))
                                Spacer(Modifier.width(Spacing.sm))
                                StartButton(state.canStart, onStart, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(Spacing.xs))
                            explain()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartButton(enabled: Boolean, onStart: () -> Unit, modifier: Modifier) {
    Button(onClick = onStart, enabled = enabled, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(scaledIconSize(20.dp)))
        Spacer(Modifier.width(Spacing.sm))
        Text(START_LABEL, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** The fixed Challenge (ADR-032): never off, whatever the setup above it. */
@Composable
private fun ChallengeButton(onChallenge: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onChallenge, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(scaledIconSize(20.dp)))
        Spacer(Modifier.width(Spacing.sm))
        Text(CHALLENGE_LABEL, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

internal const val CHALLENGE_LINE = TriviaText.CHALLENGE_LINE

/**
 * "Card trivia" must never be clipped (S25 Ultra: 384 dp wide). The title is measured; when it and
 * the Leaderboard icon don't both fit beside Back and the menu, or at large text, Leaderboard is
 * the menu's first item instead (as the timer header does with its tools).
 */
@Composable
private fun leaderboardInBar(width: Dp): Boolean {
    val density = LocalDensity.current
    if (density.fontScale >= LARGE_FONT_SCALE) return false
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleLarge
    return remember(width, density, style) {
        val title = with(density) {
            measurer.measure(HOME_TITLE, style, maxLines = 1, softWrap = false).size.width.toDp()
        }
        width - TOP_BAR_FIXED - title >= TOP_BAR_ICON
    }
}

private const val HOME_TITLE = "Card trivia"
private const val LARGE_FONT_SCALE = 1.5f
private val TOP_BAR_ICON = 48.dp

/** Back and the title's inset (56 dp), the bar's end padding (4 dp), the menu icon and a margin. */
private val TOP_BAR_FIXED = 56.dp + 4.dp + TOP_BAR_ICON + 8.dp

@Composable
private fun HeroProgressRow(hero: HeroProgressUi, onClick: () -> Unit) {
    val p = hero.progress
    val canQuiz = hero.quizNumbers > 0
    val spoken = buildString {
        append(hero.name)
        append(spokenUnverified(hero.verified))
        append(", ${p.mastered} of ${TriviaText.count(p.total, "number")} learned")
        if (p.learning > 0) append(", ${p.learning} learning")
    }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canQuiz, onClickLabel = "Quiz ${hero.name}", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (!canQuiz) stateDescription = "No numbers with these settings"
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroPortrait(
                heroId = hero.heroId,
                portrait = hero.portrait,
                contentDescription = null,
                modifier = Modifier.size(scaledIconSize(44.dp, maxScale = 1.5f)),
                shape = CircleShape,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            hero.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!hero.verified) {
                            Spacer(Modifier.width(Spacing.sm))
                            UnverifiedBadge()
                        }
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "${p.mastered}/${p.total}",
                        style = GoaTextStyles.numeric,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.xs + 2.dp))
                MasteryBar(p, height = 4.dp)
                if (!canQuiz) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "No numbers with these settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = if (canQuiz) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
            )
        }
    }
}

/**
 * Why Start is off ("No Ultimate card of the heroes you own prints Initiative."), or a warning for
 * a pool smaller than the round (questions repeat). The claim comes from the counted facts of the
 * quiz's own scope (chosen heroes, owned heroes or every hero) and names that scope, so it is true
 * as written.
 */
internal fun startNote(state: TriviaHomeUiState.Ready): String? = TriviaText.startNote(state)

/** From this width the start bar puts its text and button side by side. */
private val WIDE_START_BAR = 560.dp
