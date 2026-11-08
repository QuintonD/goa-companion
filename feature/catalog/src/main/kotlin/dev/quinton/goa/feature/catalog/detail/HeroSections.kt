package dev.quinton.goa.feature.catalog.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ColourRibbon
import dev.quinton.goa.core.designsystem.component.GoaTabRow
import dev.quinton.goa.core.designsystem.component.GoaTabRowHeight
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.selectionFrame
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.DeckGroupKind
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.plural
import dev.quinton.goa.core.domain.catalog.tierNumeral
import dev.quinton.goa.core.navigation.HeroDetailRoute

/** The hero page's three tabs. */
internal enum class HeroSection(val label: String) {
    OVERVIEW("Overview"),
    DECK("Deck"),
    FAQ("FAQ"),
    ;

    companion object {
        /** The tab a route's section opens ([HeroDetailRoute.DECK], `FAQ`, `SPELLBOOK`); the Overview otherwise. */
        fun opening(section: String?): HeroSection = when (section) {
            HeroDetailRoute.DECK, HeroDetailRoute.SPELLBOOK -> DECK
            HeroDetailRoute.FAQ -> FAQ
            else -> OVERVIEW
        }
    }
}

/** The sticky bar's height: the deck's filters sit just under it. */
internal val SectionBarHeight = GoaTabRowHeight

/**
 * Overview · Deck · FAQ, true tabs held under the banner once the head scrolls away: the app's one tab bar
 * ([GoaTabRow]), as on Players and the trivia leaderboard. With large text the FAQ count gives its room to the words.
 */
@Composable
internal fun HeroSectionBar(active: HeroSection, faqCount: Int, onSelect: (HeroSection) -> Unit) {
    val counts = LocalDensity.current.fontScale <= LARGE_FONT_SCALE
    GoaTabRow(
        labels = HeroSection.entries.map { section ->
            if (section == HeroSection.FAQ && faqCount > 0 && counts) "${section.label} · $faqCount" else section.label
        },
        selectedIndex = active.ordinal,
        onSelect = { onSelect(HeroSection.entries[it]) },
    )
}

/** One part of the deck for the colour filter: a colour (or Basic, the Ultimate, the Handicap card, a spellbook). */
internal class DeckPart(val label: String, val key: String, val ribbon: PlateColor?, val count: Int)

/** The deck's parts in page order, for the colour filter. */
internal fun deckParts(layout: DeckLayout): List<DeckPart> = buildList {
    layout.groups.forEach { add(DeckPart(it.kind.title, groupKey(it.kind), groupRibbon(it.kind), it.cards.size)) }
    layout.handicap?.let { add(DeckPart("Handicap", HANDICAP_KEY, PlateColor.of(it.color), 1)) }
    if (layout.extras.isNotEmpty()) {
        add(DeckPart(if (layout.extrasAreSpellbook) "Spellbook" else "Extras", EXTRAS_KEY, null, layout.extras.size))
    }
}

/** The tiers the deck's colours are printed in (I, II, III), for the tier filter. */
internal fun deckTiers(layout: DeckLayout): List<Int> = layout.groups
    .filter { it.kind in COLOUR_KINDS }
    .flatMap { group -> group.rows.mapNotNull { it.tier } }
    .distinct()
    .sorted()

/** Shows only the cards of [tier] (null: every card): each colour keeps its rows of that tier. */
internal fun DeckLayout.onlyTier(tier: Int?): DeckLayout = if (tier == null) {
    this
} else {
    copy(
        groups = groups.filter { it.kind in COLOUR_KINDS }
            .map { it.copy(rows = it.rows.filter { row -> row.tier == tier }) }
            .filter { it.rows.isNotEmpty() },
        handicap = null,
        extras = extras.filter { it.tier == tier },
    )
}

/** Shows only the part keyed [key] (a colour, Basic, the Ultimate, the Handicap card, the extras); null: all. */
internal fun DeckLayout.onlyPart(key: String?): DeckLayout = if (key == null) {
    this
} else {
    copy(
        groups = groups.filter { groupKey(it.kind) == key },
        handicap = handicap.takeIf { key == HANDICAP_KEY },
        extras = if (key == EXTRAS_KEY) extras else emptyList(),
    )
}

/** The cards a filtered deck shows: its colours' cards, the Handicap card and the extras. */
internal fun DeckLayout.shownCount(): Int =
    groups.sumOf { it.cards.size } + (if (handicap != null) 1 else 0) + extras.size

private val COLOUR_KINDS = setOf(DeckGroupKind.RED, DeckGroupKind.GREEN, DeckGroupKind.BLUE)

/**
 * The deck's filters, held under the tabs while the deck scrolls; each is one choice with its "All" stated and
 * selected when nothing is narrowed, so no row reads as "nothing chosen". Colours: **All colours** then each part
 * with the cards' own ribbon and count, wrapping onto a second line rather than running off the edge (a deck has a
 * handful of parts, so every one stays in sight). Tiers: **All tiers · Tier I · Tier II · Tier III** in the same
 * chips, wrapping. The count over the cards says what is shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeckFilterBar(
    parts: List<DeckPart>,
    part: String?,
    onPart: (String?) -> Unit,
    tiers: List<Int>,
    tier: Int?,
    onTier: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (parts.size >= 2) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen)
                    .selectableGroup()
                    .semantics { contentDescription = "Colours" },
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                PartChip("All colours", null, parts.sumOf { it.count }, selected = part == null) { onPart(null) }
                parts.forEach { p -> PartChip(p.label, p.ribbon, p.count, selected = part == p.key) { onPart(p.key) } }
            }
        }
        if (tiers.isNotEmpty()) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen)
                    .selectableGroup()
                    .semantics { contentDescription = "Tiers" },
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PartChip("All tiers", null, null, selected = tier == null) { onTier(null) }
                tiers.forEach { t ->
                    PartChip("Tier ${tierNumeral(t)}", null, null, selected = tier == t) { onTier(t) }
                }
            }
        }
    }
}

/**
 * One choice of the colour filter: the colour's ribbon, its name and count; selected in the app's one selection look
 * (bronze edge, bronze wash and a check). The whole 48 dp line takes the tap; the chip drawn inside it is smaller.
 */
@Composable
private fun PartChip(label: String, ribbon: PlateColor?, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = if (count == null) label else "$label, ${plural(count, "card")}"
                role = Role.RadioButton
                this.selected = selected
                onClick(label = "Show $label") {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .selectionFrame(selected, shape)
                .heightIn(min = 38.dp)
                .padding(start = if (ribbon != null || selected) 8.dp else 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = GoaTheme.colors.bronzeInk,
                    modifier = Modifier.size(scaledIconSize(16.dp, 1.4f)),
                )
                Spacer(Modifier.width(4.dp))
            }
            ribbon?.let {
                ColourRibbon(it, Modifier.size(width = scaledIconSize(9.dp), height = scaledIconSize(18.dp)))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
            if (count != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "$count",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
