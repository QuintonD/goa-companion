package dev.quinton.goa.feature.trivia.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.feature.trivia.TriviaText
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
                )
            }
        }
    }
    if (ready != null && picker) {
        HeroPickerSheet(
            options = ready.heroOptions,
            selected = ready.setup.heroIds,
            ownedOnly = ready.ownedOnly,
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

@Composable
private fun HomeContent(
    state: TriviaHomeUiState.Ready,
    onAction: (TriviaHomeAction) -> Unit,
    onStart: () -> Unit,
    onStartHero: (String) -> Unit,
    onChooseHeroes: () -> Unit,
) {
    val padded = Modifier.padding(horizontal = Spacing.screen)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.xxl)) {
        item(key = "overall") { OverallCard(state, padded) }
        item(key = "setup-title") { SectionHeader("New quiz", padded) }
        item(key = "setup") { SetupBlock(state, onAction, onChooseHeroes, padded) }
        item(key = "start") { StartBlock(state, onStart, padded) }
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
}

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
    val count = if (names.isEmpty()) state.heroOptions.size else names.size
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

@Composable
private fun StartBlock(state: TriviaHomeUiState.Ready, onStart: () -> Unit, modifier: Modifier) {
    val selection = state.selection
    Column(modifier.fillMaxWidth().padding(top = Spacing.xl)) {
        Text(
            "${TriviaText.count(
                selection.total,
                "number",
            )} in this quiz · ${TriviaText.number(selection.mastered)} learned",
            style = GoaTextStyles.numeric,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        Button(
            onClick = onStart,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(scaledIconSize(20.dp)))
            Spacer(Modifier.width(Spacing.sm))
            Text("Start quiz", style = MaterialTheme.typography.titleMedium)
        }
        val note = startNote(state)
        if (note != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.run { if (state.canStart) onSurfaceVariant else error },
            )
        }
    }
}

@Composable
private fun HeroProgressRow(hero: HeroProgressUi, onClick: () -> Unit) {
    val p = hero.progress
    val canQuiz = hero.quizNumbers > 0
    val spoken = buildString {
        append("${hero.name}, ${p.mastered} of ${TriviaText.count(p.total, "number")} learned")
        if (p.learning > 0) append(", ${p.learning} learning")
    }
    Surface(
        onClick = onClick,
        enabled = canQuiz,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (!canQuiz) {
                    stateDescription = "No numbers with these settings"
                    disabled()
                }
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
                    Text(
                        hero.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
 * Why Start is off ("No Ultimate card prints Initiative."), or a warning for a pool smaller than
 * the round (questions repeat). Built from the setup and the catalog counts, never assumed.
 */
internal fun startNote(state: TriviaHomeUiState.Ready): String? {
    val setup = state.setup
    val total = state.selection.total
    return when {
        total == 0 -> {
            val groups = or(CardGroup.entries.filter { it in setup.groups }.map(TriviaText::group))
            val stats = or(TriviaStat.entries.filter { it in setup.stats }.map(TriviaText::stat))
            val whose = "No $groups card" + if (setup.heroIds.isEmpty()) " prints" else " of these heroes prints"
            "$whose $stats. Add a number or more cards."
        }

        setup.length != TriviaConfig.ENDLESS && total < setup.length ->
            "Only ${TriviaText.count(total, "number")} for ${setup.length} questions, so some come up more than once."

        else -> null
    }
}

/** "Tier I, Tier II or Ultimate". */
private fun or(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> items.dropLast(1).joinToString(", ") + " or " + items.last()
}
