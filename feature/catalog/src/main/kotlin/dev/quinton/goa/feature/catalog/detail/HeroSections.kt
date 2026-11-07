package dev.quinton.goa.feature.catalog.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ColourRibbon
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.fadingHorizontalScroll
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.selectionFrame
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.DeckGroupKind
import dev.quinton.goa.core.domain.catalog.DeckLayout
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

/** The sticky bar's height: a jump in the deck lands just under it. */
internal val SectionBarHeight = 48.dp

/**
 * Overview · Deck · FAQ, true tabs held under the banner once the head scrolls away: a tap shows that part, and the
 * tab you are on is selected in bronze (the app's selected wash, a bronze word and a rule under it). TalkBack hears
 * each as a tab, selected or not.
 */
@Composable
internal fun HeroSectionBar(active: HeroSection, faqCount: Int, onSelect: (HeroSection) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // With large text the count gives its room to the words: "Overview" stays whole.
    val counts = LocalDensity.current.fontScale <= LARGE_FONT_SCALE
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = SectionBarHeight)
                .padding(horizontal = Spacing.sm)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HeroSection.entries.forEach { section ->
                val selected = section == active
                val label = if (section == HeroSection.FAQ && faqCount > 0 && counts) {
                    "${section.label} · $faqCount"
                } else {
                    section.label
                }
                val rule = GoaTheme.colors.selection
                val wash = GoaTheme.colors.selectionFill
                Box(
                    Modifier
                        .widthIn(min = TAB_MIN_WIDTH)
                        .heightIn(min = SectionBarHeight)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(section) })
                        .drawBehind {
                            if (selected) {
                                // The app's selected look: a bronze tint (as the segmented choices) and a bronze rule.
                                val h = UNDERLINE.toPx()
                                val inset = TAB_TINT_INSET.toPx()
                                drawRoundRect(
                                    wash,
                                    topLeft = Offset(0f, inset),
                                    size = Size(size.width, size.height - inset - h),
                                    cornerRadius = CornerRadius(TAB_TINT_CORNER.toPx()),
                                )
                                drawRect(rule, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                            }
                        }
                        .padding(horizontal = Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) colors.primary else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = Spacing.sm),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(GoaTheme.colors.engravedBorder))
    }
}

private val UNDERLINE = 3.dp
private val TAB_MIN_WIDTH = 72.dp
private val TAB_TINT_INSET = 6.dp
private val TAB_TINT_CORNER = 8.dp

/** One stop of the deck's jump row: a colour (or Basic, the Ultimate, the Handicap card, a spellbook). */
internal class DeckJump(val label: String, val key: String, val ribbon: PlateColor?, val count: Int)

/** The deck's parts in page order, for the jump row. */
internal fun deckJumps(layout: DeckLayout): List<DeckJump> = buildList {
    layout.groups.forEach { add(DeckJump(it.kind.title, groupKey(it.kind), groupRibbon(it.kind), it.cards.size)) }
    layout.handicap?.let { add(DeckJump("Handicap", HANDICAP_KEY, PlateColor.of(it.color), 1)) }
    if (layout.extras.isNotEmpty()) {
        add(DeckJump(if (layout.extrasAreSpellbook) "Spellbook" else "Extras", EXTRAS_KEY, null, layout.extras.size))
    }
}

/** The tiers the deck's colours are printed in (I, II, III), for the jump bar's tier chips. */
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

private val COLOUR_KINDS = setOf(DeckGroupKind.RED, DeckGroupKind.GREEN, DeckGroupKind.BLUE)

/**
 * The deck's jump bar, held under the tabs while the deck scrolls. Its colours, each with the cards' own ribbon (a
 * tap scrolls there), on one line that scrolls sideways with the edge that hides more fading out; under them Tier
 * I, II and III (a tap shows only that tier's cards in every colour; again, all of them).
 */
@Composable
internal fun DeckJumpBar(
    jumps: List<DeckJump>,
    tiers: List<Int>,
    tier: Int?,
    onJump: (String) -> Unit,
    onTier: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = rememberScrollState()
    Column(modifier.fillMaxWidth()) {
        if (jumps.size >= 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .fadingHorizontalScroll(row)
                    .semantics { contentDescription = "Jump to a part of the deck" },
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                jumps.forEach { jump -> JumpChip(jump) { onJump(jump.key) } }
            }
        }
        if (tiers.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tiers.forEach { t -> TierChip(t, selected = tier == t) { onTier(if (tier == t) null else t) } }
            }
        }
    }
}

/** A tier's chip: "Tier II", selected (bronze) while the deck shows only that tier. */
@Composable
private fun TierChip(tier: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = "Only Tier ${tierNumeral(tier)} cards" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .selectionFrame(selected, shape)
                .heightIn(min = 38.dp)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Tier ${tierNumeral(tier)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun JumpChip(jump: DeckJump, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    // The whole 48 dp line takes the tap; the chip drawn inside it is smaller.
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Jump to ${jump.label}", role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "${jump.label}, ${jump.count} ${if (jump.count == 1) "card" else "cards"}"
                role = Role.Button
                onClick(label = "Jump to ${jump.label}") {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(shape)
                .border(1.dp, GoaTheme.colors.engravedBorder, shape)
                .background(MaterialTheme.colorScheme.surface)
                .heightIn(min = 38.dp)
                .padding(start = if (jump.ribbon != null) 8.dp else 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            jump.ribbon?.let {
                ColourRibbon(it, Modifier.size(width = scaledIconSize(9.dp), height = scaledIconSize(18.dp)))
                Spacer(Modifier.width(6.dp))
            }
            Text(jump.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text(
                "${jump.count}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
