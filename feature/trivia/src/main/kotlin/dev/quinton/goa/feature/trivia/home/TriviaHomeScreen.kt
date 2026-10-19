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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import dev.quinton.goa.core.presentation.trivia.HeroProgressUi
import dev.quinton.goa.core.presentation.trivia.TriviaHomeAction
import dev.quinton.goa.core.presentation.trivia.TriviaHomeUiState
import dev.quinton.goa.core.presentation.trivia.TriviaText
import dev.quinton.goa.core.presentation.trivia.spokenUnverified
import dev.quinton.goa.feature.trivia.ui.MasteryBar
import dev.quinton.goa.feature.trivia.ui.MasteryLegend
import dev.quinton.goa.feature.trivia.ui.SetupLabel

/** Trivia home: overall progress, the quiz setup, and progress per hero. */
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
) {
    val ready = state as? TriviaHomeUiState.Ready
    var menu by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(initialHeroPicker) }
    GoaScreen(
        title = "Card trivia",
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (ready != null) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Reset progress") },
                            leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                            enabled = ready.canReset,
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
                )
            }
        }
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
        ConfirmDialog(
            title = "Reset progress?",
            message = ready.resetMessage,
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

@Composable
private fun HomeContent(
    state: TriviaHomeUiState.Ready,
    onAction: (TriviaHomeAction) -> Unit,
    onStart: () -> Unit,
    onStartHero: (String) -> Unit,
    onChooseHeroes: () -> Unit,
) {
    val padded = Modifier.padding(horizontal = Spacing.screen)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.xl),
        ) {
            item(key = "overall") { OverallCard(state, padded) }
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
        // Pinned: the live count and Start are always in reach, however far the list is scrolled.
        StartBar(state, onStart)
    }
}

@Composable
private fun OverallCard(state: TriviaHomeUiState.Ready, modifier: Modifier) {
    val overall = state.overall
    GoaCard(modifier.fillMaxWidth()) {
        Text(
            state.overallTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            state.overallSubtitle,
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
                state.answerStyleHint,
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
    val summary = state.heroScopeSummary
    // All heroes = the owned ones; the options also list a chosen hero outside them.
    val count = state.heroScopeCount
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
                state.duelsHint,
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
 * Start is off) and Start quiz. Side by side on a wide window (landscape), so the list keeps room.
 */
@Composable
private fun StartBar(state: TriviaHomeUiState.Ready, onStart: () -> Unit) {
    val selection = state.selection
    val line = state.startLine
    val note = state.startNote
    val noteColor = MaterialTheme.colorScheme.run { if (state.canStart) onSurfaceVariant else error }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            EngravedRule()
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            ) {
                val wide = maxWidth >= WIDE_START_BAR
                val texts: @Composable () -> Unit = {
                    Text(line, style = GoaTextStyles.numeric, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (note != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(note, style = MaterialTheme.typography.bodyMedium, color = noteColor)
                    }
                }
                if (wide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { texts() }
                        Spacer(Modifier.width(Spacing.lg))
                        StartButton(state.canStart, onStart, Modifier.widthIn(min = 220.dp))
                    }
                } else {
                    Column {
                        texts()
                        Spacer(Modifier.height(Spacing.sm))
                        StartButton(state.canStart, onStart, Modifier.fillMaxWidth())
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
        Text("Start quiz", style = MaterialTheme.typography.titleMedium)
    }
}

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

/** From this width the start bar puts its text and button side by side. */
private val WIDE_START_BAR = 560.dp
