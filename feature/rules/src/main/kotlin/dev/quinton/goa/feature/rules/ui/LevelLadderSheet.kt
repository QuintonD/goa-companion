package dev.quinton.goa.feature.rules.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.ParchmentPanel
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.horizontalEdgeFade
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.rules.LadderLookup
import dev.quinton.goa.core.domain.rules.RulesText
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.rules.LevelRow
import dev.quinton.goa.feature.rules.ExceptionCardUi
import dev.quinton.goa.feature.rules.LadderUiState

/**
 * "Levels & defeats": pick the defeated hero's level for the three numbers that matter, the full
 * p.13/p.15 table, the rules behind it (verbatim) and the cards in play that change it (verbatim).
 * The sheet is a dark plate in both themes; what the book prints sits on parchment on it. A bottom sheet over the
 * screen it opens from: under the sheet's drag handle, its title with a Close button ([onClose]), so how to leave it
 * is plain.
 */
@Composable
fun LevelLadderSheet(
    state: LadderUiState,
    onSelectLevel: (Int) -> Unit,
    onOpenRules: (() -> Unit)?,
    onClose: (() -> Unit)? = null,
    compareInitially: Boolean = false,
) {
    var compare by rememberSaveable { mutableStateOf(compareInitially) }
    GoaPlateTheme {
        Box(Modifier.fillMaxWidth().slatePlate(RoundedCornerShape(0.dp), brackets = false)) {
            when (state) {
                LadderUiState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }

                LadderUiState.Unavailable -> Text(
                    "The bundled rules reference could not be read.",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.xl),
                )

                is LadderUiState.Ready -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.sm,
                        bottom = Spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    ladderContent(state, onSelectLevel, onOpenRules, onClose, compare) { compare = !compare }
                }
            }
        }
    }
}

private fun LazyListScope.ladderContent(
    state: LadderUiState.Ready,
    onSelectLevel: (Int) -> Unit,
    onOpenRules: (() -> Unit)?,
    onClose: (() -> Unit)?,
    compare: Boolean,
    onToggleCompare: () -> Unit,
) {
    val ladder = state.ladder
    item(key = "title") {
        // With large text the page tag goes under the title, so the title never breaks inside a word.
        val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Levels & defeats",
                    style = GoaTextStyles.sectionHead.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    color = GoaTheme.colors.heading,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (!large) PageTag(ladder.pages)
                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.padding(start = Spacing.xs)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
            }
            if (large) PageTag(ladder.pages)
        }
    }
    item(key = "picker") { LevelPicker(ladder.levels, state.selectedLevel, onSelectLevel) }
    val selected = state.selectedLevel?.let { LadderLookup.row(ladder, it) }
    if (selected != null) {
        item(key = "result") { DefeatResult(selected) }
    }
    // The picked level's numbers are the answer; the whole table is one tap away, for comparing levels.
    item(key = "compare") { CompareToggle(compare, onToggleCompare) }
    if (compare) item(key = "table") { LadderTable(ladder, state.selectedLevel, onSelectLevel) }
    item(key = "defeat-header") { SectionHeader("When a hero is defeated") }
    items(ladder.defeat, key = {
        "defeat-${it.page}-${it.text.hashCode()}"
    }) { ExcerptOnPlate(it) }
    item(key = "level-header") { SectionHeader("Levelling up") }
    items(ladder.levelUp, key = {
        "level-${it.page}-${it.text.hashCode()}"
    }) { ExcerptOnPlate(it) }
    item(key = "cards-header") {
        SectionHeader(if (state.matchHeroesOnly) "Cards in this match that change this" else "Cards that change this")
    }
    item(key = "cards-rule") { ExcerptOnPlate(ladder.cardText) }
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
            GoaOutlinedButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                Text("All rules", modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}

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
                // A level key is read at a glance: selected, a solid bronze key with a check by its numeral.
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) colors.primary else colors.surface,
                    contentColor = if (isSelected) colors.onPrimary else colors.onSurface,
                    border = BorderStroke(1.dp, if (isSelected) colors.primary else GoaTheme.colors.engravedBorder),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(row.level) }
                        .semantics { contentDescription = "Level ${row.level}" },
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 2.dp).size(scaledIconSize(18.dp, 1.3f)),
                            )
                        }
                        Text(
                            "${row.level}",
                            style = GoaTextStyles.timerDigitsSmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
                            fontWeight = if (isSelected) FontWeight.Bold else null,
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
            .background(colors.primary.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .border(1.dp, colors.primary.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .padding(Spacing.lg)
            .clearAndSetSemantics { contentDescription = RulesText.spokenDefeat(row) },
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

/** The table's column heads on one line each ("Next level cost"): the table scrolls sideways instead of wrapping. */
private val headers = RulesText.LADDER_HEADERS.map { it.replace('\n', ' ') }

private val MIN_CELL = 56.dp
private val HEADER_HEIGHT = 44.dp
private val ROW_HEIGHT = 48.dp

/** "Compare all levels": shows or hides the whole table under the picked level's numbers. */
@Composable
private fun CompareToggle(open: Boolean, onToggle: () -> Unit) {
    GoaOutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.TableRows,
            contentDescription = null,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.4f)),
        )
        Text(
            if (open) "Hide the table" else "Compare all levels",
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

/**
 * Row per level, as the book's table: the Level column stays put while the other six scroll sideways (each head on one
 * line, at least 14 sp; the edge that hides more fades out), the picked level's row in bronze. Seven columns don't
 * fit large text, and people check these numbers at the table, so none may hide off-screen: there each level becomes
 * a block of "column, value" lines ([LadderBlocks]).
 */
@Composable
private fun LadderTable(ladder: LevelLadder, selected: Int?, onSelect: (Int) -> Unit) {
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
        LadderBlocks(ladder, selected, onSelect)
        return
    }
    val colors = MaterialTheme.colorScheme
    val headStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = 0.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Each column as wide as its head on one line (and never narrower than a comfortable cell).
    val widths = remember(headStyle, density) {
        headers.map { head ->
            val text = with(density) { measurer.measure(head, headStyle).size.width.toDp() }
            maxOf(MIN_CELL, text + Spacing.md * 2)
        }
    }
    val scroll = rememberScrollState()
    val rows = ladder.levels.map { row -> row to LadderLookup.nextLevelCost(ladder, row.level) }
    val picked = colors.primary.copy(alpha = 0.16f)
    val rowGround: (Int) -> Modifier = { level ->
        Modifier.height(ROW_HEIGHT).background(if (level == selected) picked else Color.Transparent)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, GoaTheme.colors.engravedBorder, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .padding(vertical = Spacing.xs),
    ) {
        // The pinned Level column: each row's tap target and its spoken summary.
        Column(Modifier.width(widths[0])) {
            Box(Modifier.height(HEADER_HEIGHT).fillMaxWidth().clearAndSetSemantics {}, Alignment.Center) {
                Text(headers[0], style = headStyle, color = colors.onSurfaceVariant, maxLines = 1)
            }
            rows.forEach { (row, next) ->
                Box(
                    rowGround(row.level)
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Show level ${row.level}") { onSelect(row.level) }
                        .clearAndSetSemantics { contentDescription = RulesText.spokenLadderRow(row, next) },
                    Alignment.Center,
                ) { CellText("${row.level}", bold = true) }
            }
        }
        val tall = HEADER_HEIGHT + ROW_HEIGHT * rows.size
        Box(Modifier.width(1.dp).height(tall).background(GoaTheme.colors.engravedBorder))
        Column(Modifier.weight(1f).horizontalEdgeFade(scroll).horizontalScroll(scroll)) {
            val headRow = Modifier.height(HEADER_HEIGHT).clearAndSetSemantics {}
            Row(headRow, verticalAlignment = Alignment.CenterVertically) {
                headers.drop(1).forEachIndexed { c, head ->
                    Text(
                        head,
                        style = headStyle,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(widths[c + 1]),
                    )
                }
            }
            rows.forEach { (row, next) ->
                Row(
                    rowGround(row.level)
                        .clickable(onClickLabel = "Show level ${row.level}") { onSelect(row.level) }
                        .clearAndSetSemantics {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        RulesText.roman(row.lowestTier),
                        "${row.defeatCoins}",
                        "${row.assistCoins}",
                        "${row.lifeCounters}",
                        next?.toString() ?: "–",
                        RulesText.roman(row.maxTier),
                    ).forEachIndexed { c, value ->
                        CellText(value, Modifier.width(widths[c + 1]))
                    }
                }
            }
        }
    }
}

@Composable
private fun CellText(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(
        text,
        style = GoaTextStyles.numeric.copy(fontSize = 17.sp, lineHeight = 24.sp),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The table at large text: one block per level, each number beside its column's name. */
@Composable
private fun LadderBlocks(ladder: LevelLadder, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ladder.levels.forEach { row ->
            val next = LadderLookup.nextLevelCost(ladder, row.level)
            val isSelected = row.level == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) colors.primary else GoaTheme.colors.engravedBorder,
                        MaterialTheme.shapes.medium,
                    )
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClickLabel = "Show level ${row.level}") { onSelect(row.level) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .clearAndSetSemantics { contentDescription = RulesText.spokenLadderRow(row, next) },
            ) {
                Text("Level ${row.level}", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                listOf(
                    RulesText.roman(row.lowestTier),
                    "${row.defeatCoins}",
                    "${row.assistCoins}",
                    "${row.lifeCounters}",
                    next?.toString() ?: "–",
                    RulesText.roman(row.maxTier),
                ).forEachIndexed { c, value ->
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                        Text(
                            headers[c + 1].replace('\n', ' '),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            value,
                            style = GoaTextStyles.numeric.copy(fontSize = 17.sp, lineHeight = 24.sp),
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExceptionCard(card: ExceptionCardUi) {
    val colors = MaterialTheme.colorScheme
    val parchment = GoaTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(Spacing.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("${card.heroName} · ${card.cardName}", style = MaterialTheme.typography.titleMedium)
        Text(card.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        // The card's own words, on the parchment the card prints them on.
        ParchmentPanel(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            Text(
                cardTextAnnotated(card.text, parchment.onParchmentVariant),
                inlineContent = rememberCardIcons(parchment.parchmentInk),
                style = GoaTextStyles.printed,
                color = parchment.onParchment,
            )
        }
    }
}
