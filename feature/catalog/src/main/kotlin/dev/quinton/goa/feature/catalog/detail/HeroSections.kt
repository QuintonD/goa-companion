package dev.quinton.goa.feature.catalog.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.DeckLayout

/** The hero page's three parts, each opening at its item's key. */
internal enum class HeroSection(val label: String, val key: String) {
    OVERVIEW("Overview", HEADER_KEY),
    DECK("Deck", DECK_KEY),
    FAQ("FAQ", FAQ_KEY),
}

/** The sticky bar's height: the deck and FAQ land just under it. */
internal val SectionBarHeight = 48.dp

/**
 * Overview · Deck · FAQ over the hero page, held under the banner once the art scrolls away: a tap scrolls to
 * that part, and the part you are reading is marked in bronze with a rule under it (never by colour alone).
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
                val rule = colors.primary
                Box(
                    Modifier
                        .widthIn(min = TAB_MIN_WIDTH)
                        .heightIn(min = SectionBarHeight)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(section) })
                        .semantics { contentDescription = "${section.label}, jump to section" }
                        .drawBehind {
                            if (selected) {
                                val h = UNDERLINE.toPx()
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

/**
 * A compact row of the deck's colours under its title, each with the cards' own ribbon: a tap scrolls to that
 * colour. It wraps rather than scrolls, so every stop shows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeckJumpRow(jumps: List<DeckJump>, onJump: (String) -> Unit, modifier: Modifier = Modifier) {
    if (jumps.size < 2) return
    FlowRow(
        modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xs)
            .semantics { contentDescription = "Jump to a part of the deck" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        jumps.forEach { jump -> JumpChip(jump) { onJump(jump.key) } }
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
