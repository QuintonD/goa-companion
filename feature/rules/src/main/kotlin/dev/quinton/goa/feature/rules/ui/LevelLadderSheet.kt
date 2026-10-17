package dev.quinton.goa.feature.rules.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.rules.LadderLookup
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.rules.LevelRow
import dev.quinton.goa.feature.rules.ExceptionCardUi
import dev.quinton.goa.feature.rules.LadderUiState
import dev.quinton.goa.feature.rules.RulesText

/**
 * "Levels & defeats": pick the defeated hero's level for the three numbers that matter, the full
 * p.13/p.15 table, the rules behind it (verbatim) and the cards in play that change it (verbatim).
 */
@Composable
fun LevelLadderSheet(state: LadderUiState, onSelectLevel: (Int) -> Unit, onOpenRules: (() -> Unit)?) {
    when (state) {
        LadderUiState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), Alignment.Center) {
            CircularProgressIndicator()
        }

        LadderUiState.Unavailable -> Text(
            "The bundled rules reference could not be read.",
            modifier = Modifier.padding(Spacing.xl),
        )

        is LadderUiState.Ready -> LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ladderContent(state, onSelectLevel, onOpenRules)
        }
    }
}

private fun LazyListScope.ladderContent(
    state: LadderUiState.Ready,
    onSelectLevel: (Int) -> Unit,
    onOpenRules: (() -> Unit)?,
) {
    val ladder = state.ladder
    item(key = "title") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Levels & defeats",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            PageTag(ladder.pages)
        }
    }
    item(key = "picker") { LevelPicker(ladder.levels, state.selectedLevel, onSelectLevel) }
    val selected = state.selectedLevel?.let { LadderLookup.row(ladder, it) }
    if (selected != null) {
        item(key = "result") { DefeatResult(selected) }
    }
    item(key = "table") { LadderTable(ladder, state.selectedLevel, onSelectLevel) }
    item(key = "defeat-header") { SectionHeader("When a hero is defeated") }
    items(ladder.defeat, key = {
        "defeat-${it.page}-${it.text.hashCode()}"
    }) { ExcerptBlock(it, style = compactReading()) }
    item(key = "level-header") { SectionHeader("Levelling up") }
    items(ladder.levelUp, key = {
        "level-${it.page}-${it.text.hashCode()}"
    }) { ExcerptBlock(it, style = compactReading()) }
    item(key = "cards-header") {
        SectionHeader(if (state.matchHeroesOnly) "Cards in this match that change this" else "Cards that change this")
    }
    item(key = "cards-rule") { ExcerptBlock(ladder.cardText, style = compactReading()) }
    if (state.exceptions.isEmpty()) {
        item(key = "cards-none") {
            Text(
                if (state.matchHeroesOnly) {
                    "No card of the heroes in this match changes Life counters or assist coins."
                } else {
                    "No card in the catalog changes Life counters or assist coins."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    itemsIndexed(state.exceptions, key = { i, _ -> "card-$i" }) { _, card -> ExceptionCard(card) }
    if (onOpenRules != null) {
        item(key = "all-rules") {
            OutlinedButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                Text("All rules", modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

@Composable
private fun compactReading() = MaterialTheme.typography.bodyLarge

private const val LEVELS_PER_ROW = 4

/** Eight large level buttons (two rows of four): the defeated hero's level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelPicker(levels: List<LevelRow>, selected: Int?, onSelect: (Int) -> Unit) {
    Column {
        Text(
            "HERO DEFEATED AT LEVEL",
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Spacing.sm).semantics { heading() },
        )
        FlowRow(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            maxItemsInEachRow = LEVELS_PER_ROW,
        ) {
            levels.forEach { row ->
                val isSelected = row.level == selected
                val colors = MaterialTheme.colorScheme
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) colors.primary else colors.surface,
                    contentColor = if (isSelected) colors.onPrimary else colors.onSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) colors.primary else GoaTheme.colors.engravedBorder,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(row.level) }
                        .semantics { contentDescription = "Level ${row.level}" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${row.level}",
                            style = GoaTextStyles.timerDigitsSmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
                        )
                    }
                }
            }
        }
    }
}

/** The three numbers for the picked level, in the rulebook's table terms. */
@Composable
private fun DefeatResult(row: LevelRow) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.secondary.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .border(1.dp, colors.secondary.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .padding(Spacing.lg)
            .clearAndSetSemantics { contentDescription = spokenDefeat(row) },
    ) {
        Text(
            "Level ${row.level} hero defeated · lowest card Tier ${RulesText.roman(row.lowestTier)}",
            style = MaterialTheme.typography.titleMedium,
        )
        val numbers = listOf(
            Triple(row.lifeCounters, "Life counters", "their team spends"),
            Triple(row.defeatCoins, "Coins (Defeat)", "the hero who defeated them collects"),
            Triple(row.assistCoins, "Assist Coins", "that hero's friendly heroes collect"),
        )
        if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
            // Large text: one number per line, label beside it.
            Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                numbers.forEach { (value, label, detail) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BigDigit(value, Modifier.width(56.dp))
                        Column(Modifier.weight(1f)) { NumberLabels(label, detail, TextAlign.Start) }
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                numbers.forEach { (value, label, detail) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        BigDigit(value)
                        NumberLabels(label, detail, TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigDigit(value: Int, modifier: Modifier = Modifier) {
    Text(
        "$value",
        style = GoaTextStyles.timerDigitsSmall.copy(fontSize = 40.sp, lineHeight = 44.sp),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun NumberLabels(label: String, detail: String, align: TextAlign) {
    Text(label, style = MaterialTheme.typography.labelLarge, textAlign = align)
    Text(
        detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
    )
}

private val headers =
    listOf(
        "Level",
        "Lowest\nTier",
        "Coins\n(Defeat)",
        "Assist\nCoins",
        "Life\ncounters",
        "Next\nlevel\ncost",
        "Max\nTier",
    )

private val MIN_CELL = 50.dp

/** Row per level; scrolls sideways when large text makes it wider than the sheet. */
@Composable
private fun LadderTable(ladder: LevelLadder, selected: Int?, onSelect: (Int) -> Unit) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .border(1.dp, GoaTheme.colors.engravedBorder, MaterialTheme.shapes.medium),
    ) {
        // Fill the sheet's width; with large text the minimum cell grows and the table scrolls.
        val cell = maxOf(MIN_CELL * scale, (maxWidth - Spacing.xs * 2) / headers.size)
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.xs)) {
            Row(Modifier.padding(horizontal = Spacing.xs).clearAndSetSemantics {}) {
                headers.forEach { header ->
                    Text(
                        header,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(cell).padding(horizontal = 2.dp, vertical = Spacing.xs),
                    )
                }
            }
            ladder.levels.forEach { row ->
                val next = LadderLookup.nextLevelCost(ladder, row.level)
                val isSelected = row.level == selected
                Row(
                    Modifier
                        .background(if (isSelected) colors.secondary.copy(alpha = 0.16f) else colors.surface)
                        .clickable(onClickLabel = "Show level ${row.level}") { onSelect(row.level) }
                        .padding(horizontal = Spacing.xs)
                        .heightIn(min = 40.dp)
                        .clearAndSetSemantics { contentDescription = spokenRow(row, next) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cell("${row.level}", cell, bold = true)
                    Cell(RulesText.roman(row.lowestTier), cell)
                    Cell("${row.defeatCoins}", cell)
                    Cell("${row.assistCoins}", cell)
                    Cell("${row.lifeCounters}", cell)
                    Cell(next?.toString() ?: "–", cell)
                    Cell(RulesText.roman(row.maxTier), cell)
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String, width: Dp, bold: Boolean = false) {
    Text(
        text,
        style = GoaTextStyles.numeric.copy(fontSize = 17.sp, lineHeight = 24.sp),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(width),
    )
}

internal fun spokenRow(row: LevelRow, next: Int?): String = buildString {
    append("Level ${row.level}. Lowest card Tier ${row.lowestTier}. ")
    append("Defeat: ${RulesText.count(row.defeatCoins, "coin")}, ")
    append("${RulesText.count(row.assistCoins, "assist coin")}, ")
    append("team spends ${RulesText.count(row.lifeCounters, "Life counter")}. ")
    append(if (next != null) "Next level costs ${RulesText.count(next, "coin")}. " else "Top level. ")
    append(row.maxTier?.let { "Max card Tier $it." } ?: "")
}

/** The defeat result for TalkBack, with singular and plural counts. */
internal fun spokenDefeat(row: LevelRow): String =
    "Level ${row.level} hero defeated, lowest card Tier ${row.lowestTier}. " +
        "Their team spends ${RulesText.count(row.lifeCounters, "Life counter")}. " +
        "The hero who defeated them collects ${RulesText.count(row.defeatCoins, "coin")}. " +
        "That hero's friendly heroes collect ${RulesText.count(row.assistCoins, "assist coin")}."

@Composable
private fun ExceptionCard(card: ExceptionCardUi) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(Spacing.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("${card.heroName} · ${card.cardName}", style = MaterialTheme.typography.titleMedium)
        Text(card.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Text(
            cardTextAnnotated(card.text, colors.onSurfaceVariant),
            inlineContent = rememberCardIcons(colors.primary),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
