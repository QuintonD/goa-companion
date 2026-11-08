package dev.quinton.goa.feature.trivia.home

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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.data.trivia.HeroProgressUi
import dev.quinton.goa.core.data.trivia.PlayingAsState
import dev.quinton.goa.core.data.trivia.TriviaHomeAction
import dev.quinton.goa.core.data.trivia.TriviaHomeUiState
import dev.quinton.goa.core.data.trivia.TriviaText
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.FootArt
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSegment
import dev.quinton.goa.core.designsystem.component.GoaSegmentCheck
import dev.quinton.goa.core.designsystem.component.GoaSegmentedRow
import dev.quinton.goa.core.designsystem.component.GoaSwitch
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.IdentitySelector
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.ScreenFoot
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.TriviaChallenge
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.feature.trivia.ui.MasteryBar
import dev.quinton.goa.feature.trivia.ui.MasteryLegend
import dev.quinton.goa.feature.trivia.ui.SetupLabel
import dev.quinton.goa.feature.trivia.ui.spokenUnverified

/**
 * Trivia home: Practice or Challenge first, then who is playing; in Practice the overall progress, the quiz setup
 * and progress per hero, in Challenge its fixed round. The mode's one Start is pinned at the bottom, the
 * leaderboard on the top bar (ADR-028, ADR-032).
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
    initialMode: TriviaMode = TriviaMode.PRACTICE,
) {
    val ready = state as? TriviaHomeUiState.Ready
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(initialHeroPicker) }
    var playingAsPicker by rememberSaveable { mutableStateOf(initialPlayingAsPicker) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
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
                        mode = mode,
                        onMode = { mode = it },
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

/** What the home starts: a practice round on the setup below, or the fixed, ranked Challenge (ADR-032). */
enum class TriviaMode { PRACTICE, CHALLENGE }

@Suppress("LongParameterList")
@Composable
private fun HomeContent(
    state: TriviaHomeUiState.Ready,
    mode: TriviaMode,
    onMode: (TriviaMode) -> Unit,
    onAction: (TriviaHomeAction) -> Unit,
    onStart: () -> Unit,
    onStartHero: (String) -> Unit,
    onChooseHeroes: () -> Unit,
    onChoosePlayer: () -> Unit,
    onChallenge: () -> Unit,
) {
    val padded = Modifier.padding(horizontal = Spacing.screen)
    val challenge = mode == TriviaMode.CHALLENGE
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_START_BAR
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // The Challenge's page is short: it ends in the leaderboards' hall of banners, not in empty navy.
                if (challenge) ScreenFoot(FootArt.HALL)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.xl),
                ) {
                    // The choice first: practice on your own setup, or the Challenge that is the same for everyone.
                    item(key = "mode") { ModeChoice(mode, onMode, padded) }
                    item(key = "playing-as") {
                        PlayingAsRow(state.playingAs, mode, onChoosePlayer, padded.padding(top = Spacing.md))
                    }
                    if (challenge) {
                        item(key = "challenge") { ChallengeCard(padded.padding(top = Spacing.md)) }
                    } else {
                        item(key = "overall") { MasteryHeader(state, padded.padding(top = Spacing.lg)) }
                        // The setup folded into one row that says what the quiz is; it opens in place.
                        item(key = "setup-title") {
                            val toggle = { optionsOpen = !optionsOpen }
                            QuizOptionsRow(state, optionsOpen, toggle, padded.padding(top = Spacing.lg))
                        }
                        if (optionsOpen) {
                            item(key = "setup") {
                                SetupBlock(state, onAction, onChooseHeroes, padded.padding(top = Spacing.md))
                            }
                        }
                        item(key = "heroes-title") {
                            SectionHeader(
                                "By hero",
                                padded,
                            )
                        }
                        items(state.heroes, key = { "hero-${it.heroId}" }) { hero ->
                            GoaPlate(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
                                    .clip(PlateShape),
                                brackets = false,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                HeroProgressRow(hero, onClick = { onStartHero(hero.heroId) })
                            }
                        }
                    }
                }
            }
            // Pinned: the live line and the one Start for the chosen mode, however far the list is scrolled.
            StartBar(state, mode, if (challenge) onChallenge else onStart, wide)
        }
    }
}

/** Practice or Challenge, side by side: the one choice the home's Start depends on. */
@Composable
private fun ModeChoice(mode: TriviaMode, onMode: (TriviaMode) -> Unit, modifier: Modifier) {
    GoaSegmentedRow(modifier.fillMaxWidth()) {
        TriviaMode.entries.forEach { option ->
            val selected = option == mode
            GoaSegment(
                selected = selected,
                onClick = { onMode(option) },
                // The mode's own mark until chosen, then the shared bronze check.
                icon = {
                    if (selected) {
                        GoaSegmentCheck(true)
                    } else {
                        Icon(
                            if (option == TriviaMode.PRACTICE) Icons.Outlined.School else Icons.Outlined.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(scaledIconSize(18.dp, 1.4f)),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                    }
                },
            ) {
                Text(modeLabel(option), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
    }
}

internal fun modeLabel(mode: TriviaMode): String = when (mode) {
    TriviaMode.PRACTICE -> "Practice"
    TriviaMode.CHALLENGE -> "Challenge"
}

/**
 * The Challenge (ADR-032) as it is: the fixed round of [TriviaChallenge.CONFIG] (every number, every card group,
 * four choices, duels), the same for everyone; each player's best one is on the board.
 */
@Composable
private fun ChallengeCard(modifier: Modifier) {
    GoaCard(modifier.fillMaxWidth()) {
        Text(
            "${TriviaChallenge.QUESTIONS} questions on every hero",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * "Playing as Sam ›" with the player's crest, the shared [IdentitySelector]; the line under it says whether the rounds
 * go on the leaderboard, and a note when the player isn't this phone's "You" (whose learning progress this is).
 */
@Composable
private fun PlayingAsRow(playing: PlayingAsState, mode: TriviaMode, onClick: () -> Unit, modifier: Modifier) {
    val name = playing.player?.name ?: "Guest"
    val isMe = playing.player?.let { it.id == playing.me?.id } == true
    val subtitle = rankLine(playing, mode)
    val note = playingAsNote(playing)
    Column(modifier.fillMaxWidth()) {
        IdentitySelector(
            title = "Playing as $name",
            scope = subtitle,
            avatar = { size -> PlayingAvatar(playing.player, size = size, showFace = true) },
            onClick = onClick,
            onClickLabel = "Change who's playing",
            spoken = "Playing as $name" + (if (isMe) ", you" else "") + ", $subtitle",
            tag = if (isMe) {
                { YouTag() }
            } else {
                null
            },
        )
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

/**
 * Mastery as one header, not a panel: the learned figure large (Inter, as every score), what it counts in the
 * section's Cinzel, the bar and its legend under it.
 */
@Composable
private fun MasteryHeader(state: TriviaHomeUiState.Ready, modifier: Modifier) {
    val overall = state.overall
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.semantics(mergeDescendants = true) {
                heading()
                contentDescription = "${TriviaText.number(overall.mastered)} of " +
                    "${TriviaText.number(overall.total)} numbers learned"
            },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                TriviaText.number(overall.mastered),
                style = MasteryFigure,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "of ${TriviaText.number(overall.total)} learned",
                style = GoaTextStyles.sectionHead,
                color = GoaTheme.colors.heading,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        MasteryBar(overall, height = 8.dp)
        Spacer(Modifier.height(Spacing.sm))
        MasteryLegend(overall)
    }
}

/**
 * The quiz's setup folded into one row: "Quiz options" and what is set ("All heroes · all numbers · all cards ·
 * 4 choices · 10 questions"), wrapping, never cut; a tap opens the setup under it (and closes it again).
 */
@Composable
private fun QuizOptionsRow(state: TriviaHomeUiState.Ready, open: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val summary = quizSummary(state)
    GoaPlate(
        onClick = onToggle,
        brackets = false,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Quiz options: $summary"
                stateDescription = if (open) "Expanded" else "Collapsed"
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Quiz options",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(scaledIconSize(24.dp)).rotate(if (open) 180f else 0f),
            )
        }
    }
}

/** The learned figure: Inter bold, 28 sp, as every score. */
private val MasteryFigure =
    GoaTextStyles.scoreLarge.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)

/** "All heroes · all numbers · all cards · 4 choices · 10 questions": the setup in one line of words. */
internal fun quizSummary(state: TriviaHomeUiState.Ready): String {
    val setup = state.setup
    val names = state.selectedHeroNames
    val heroes = when {
        names.isEmpty() -> if (state.ownedOnly) "All heroes you own" else "All heroes"
        names.size <= 2 -> names.joinToString(", ")
        else -> "${names.take(2).joinToString(", ")} +${names.size - 2}"
    }
    val numbers = if (setup.stats.size == TriviaStat.entries.size) {
        "all numbers"
    } else {
        TriviaStat.entries.filter { it in setup.stats }.joinToString(", ") { TriviaText.stat(it) }
    }
    val cards = if (setup.groups.size == CardGroup.entries.size) {
        "all cards"
    } else {
        CardGroup.entries.filter { it in setup.groups }.joinToString(", ") { TriviaText.group(it) }
    }
    val answers = if (setup.answerStyle == AnswerStyle.CHOICES) "4\u00A0choices" else "recall"
    val length = if (setup.length == TriviaConfig.ENDLESS) "endless" else "${setup.length}\u00A0questions"
    // A no-break space before each dot: a wrapped line never starts with one.
    return listOf(heroes, numbers, cards, answers, length).joinToString("\u00A0· ")
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
            FlowRow(
                horizontalArrangement = chipSpacing,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
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
            FlowRow(
                horizontalArrangement = chipSpacing,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
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
            FlowRow(
                horizontalArrangement = chipSpacing,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
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
        }
        Column {
            SetupLabel("Questions")
            FlowRow(
                horizontalArrangement = chipSpacing,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
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
    GoaPlate(
        onClick = onChooseHeroes,
        contentPadding = PaddingValues(0.dp),
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
            // Only why it's off: on, the name says it.
            if (!enabled) {
                Text(
                    "Needs Initiative under Numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Spacing.md))
        GoaSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The bar pinned under the home's list: the live line ("N numbers in this quiz · M learned", or why Start is off)
 * and the chosen mode's one Start. Side by side on a wide window (landscape), so the list keeps room.
 */
@Composable
private fun StartBar(state: TriviaHomeUiState.Ready, mode: TriviaMode, onStart: () -> Unit, wide: Boolean) {
    val challenge = mode == TriviaMode.CHALLENGE
    // The Challenge's card says what it asks: its bar is the button alone.
    val line = if (challenge) {
        null
    } else {
        "${TriviaText.count(state.selection.total, "number")} in this quiz · " +
            "${TriviaText.number(state.selection.mastered)} learned"
    }
    val note = if (challenge) null else startNote(state)
    val enabled = challenge || state.canStart
    GoaPlateTheme {
        val noteColor = MaterialTheme.colorScheme.run { if (enabled) onSurfaceVariant else error }
        Column(Modifier.slatePlate(RectangleShape, brackets = false)) {
            val texts: @Composable () -> Unit = {
                if (line != null) {
                    Text(line, style = GoaTextStyles.numeric, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (note != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = noteColor)
                }
            }
            val padding = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md)
            if (wide) {
                Row(padding, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { texts() }
                    Spacer(Modifier.width(Spacing.lg))
                    StartButton(mode, enabled, onStart, Modifier.widthIn(min = 240.dp))
                }
            } else {
                Column(padding) {
                    texts()
                    if (line != null || note != null) Spacer(Modifier.height(Spacing.sm))
                    StartButton(mode, enabled, onStart, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StartButton(mode: TriviaMode, enabled: Boolean, onStart: () -> Unit, modifier: Modifier) {
    GoaButton(onClick = onStart, enabled = enabled, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(
            if (mode == TriviaMode.CHALLENGE) Icons.Outlined.Bolt else Icons.Outlined.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(scaledIconSize(20.dp)),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(startLabel(mode), style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

internal fun startLabel(mode: TriviaMode): String = when (mode) {
    TriviaMode.PRACTICE -> "Start practice"
    TriviaMode.CHALLENGE -> "Start challenge"
}

/** Under "Playing as": whether this player's rounds are ranked, in the chosen mode. */
internal fun rankLine(playing: PlayingAsState, mode: TriviaMode): String = when {
    mode == TriviaMode.CHALLENGE && playing.isGuest -> "Choose a player: a Challenge is ranked by name"
    mode == TriviaMode.CHALLENGE -> "Ranked on the Challenge leaderboard"
    playing.isGuest -> "Practice only · unranked"
    else -> "Rounds count on the Practice leaderboard"
}

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
    Box(
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
                MasteryBar(p, height = 6.dp)
                // The learned count is the figure over the bar; only the learning count (striped) needs words.
                if (p.learning > 0) {
                    Text(
                        "${TriviaText.number(p.learning)} learning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
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
