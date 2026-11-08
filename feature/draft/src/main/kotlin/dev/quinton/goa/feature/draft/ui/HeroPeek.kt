package dev.quinton.goa.feature.draft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.CardScan
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaSheet
import dev.quinton.goa.core.designsystem.component.HERO_RATINGS_TITLE
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroStatBlocks
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.StatReadoutStyle
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.match.Team

/**
 * The peek's way to commit: a short verb and name ("Pick Arien") that never truncates, whom it is for
 * ([recipient], "◆ Red · for Ada · Pick 1 of 2", wrapping above it), and, before the first choice, a swap.
 */
internal class PeekCommit(
    val team: Team,
    val label: String,
    val recipient: String,
    val onCommit: () -> Unit,
    val onSwapOut: (() -> Unit)? = null,
    /** The step's clock, still running while the hero is read; null when the draft has no step timer. */
    val remainingSeconds: Int? = null,
)

/**
 * Hero peek during a draft (a pool tile's tap, or a long-press): complexity, roles, stat bars (base → max),
 * the five starting cards, Gydion's spellbook (his hero page opened at it) and a link to the full hero page.
 * Catalog data only, never draft state, so it can't reveal a hidden choice. [commit], when the hero can be
 * chosen now, is pinned under it: the step's own Pick or Ban, always in view. [note] says why it can't be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeroPeekSheet(
    hero: HeroInfo,
    onDismiss: () -> Unit,
    onOpenHero: ((String) -> Unit)?,
    onOpenSpellbook: ((String) -> Unit)? = null,
    commit: PeekCommit? = null,
    note: String? = null,
) {
    GoaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = if (commit == null && note == null) Spacing.xl else Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            TitlePlate(hero.name, Modifier.fillMaxWidth(), gear = false)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 80.dp, height = 100.dp)) {
                    HeroPortrait(hero.id, hero.portrait, null, Modifier.matchParentSize(), framed = false)
                    if (hero.symbol != null) {
                        HeroSymbol(hero.id, hero.symbol, Modifier.padding(2.dp).size(28.dp))
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (hero.title.isNotEmpty()) {
                        Text(
                            hero.title,
                            style = GoaTextStyles.printedSmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    ComplexityStars(hero.complexity, showLabel = true)
                    RoleChips(hero.roles)
                }
            }
            // The links sit under the hero, not at the end: the pinned Pick footer must never hide them.
            if (onOpenHero != null && hero.known) {
                GoaOutlinedButton(
                    onClick = { onOpenHero(hero.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Open hero page")
                }
            }
            // Choosing Gydion: his deck casts spells from a spellbook of its own (ADR-040); the hero
            // page shows the spell cards, opened at them.
            if (onOpenSpellbook != null && hero.known && hero.spellCards > 0) {
                GoaOutlinedButton(
                    onClick = { onOpenSpellbook(hero.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    if (hero.symbol != null) {
                        HeroSymbol(hero.id, hero.symbol, Modifier.size(scaledIconSize(24.dp, 1.3f)))
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text("${hero.name}'s spellbook · ${hero.spellCards} spell cards")
                }
            }
            if (!hero.known) {
                Text(
                    "This hero isn't in the catalog.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PeekHeading(HERO_RATINGS_TITLE)
                // The app's one stat readout (HeroStatBlock), with its 1..8 bar, in the order the card prints, and the
                // shared note that these are ratings, not card values.
                HeroStatBlocks(hero.stats, style = StatReadoutStyle.Bar)
                PeekHeading("Starting cards")
                if (hero.startingCards.isEmpty()) {
                    Text(
                        "Not in the catalog yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StartingCardScans(hero.startingCards)
            }
        }
        if (commit != null) PeekCommitBar(commit)
        if (note != null) PeekNote(note)
    }
}

/**
 * The step's commitment, pinned under the scrolling peek: whom it is for, wrapping, then the bronze primary
 * with a short label that never truncates; before the first choice, the swap under it.
 */
@Composable
private fun PeekCommitBar(commit: PeekCommit) {
    // An engraved line over the bar: it is pinned, the peek scrolls under it.
    HorizontalDivider(color = GoaTheme.colors.engravedBorder)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen)
            .padding(top = Spacing.md, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val remaining = commit.remainingSeconds
        if (remaining == null) {
            TeamText(
                commit.recipient,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Whom it is for beside the step's clock, which keeps running while the hero is read.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamText(
                    commit.recipient,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                StepClock(remaining)
            }
        }
        GoaButton(
            onClick = commit.onCommit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { contentDescription = "${commit.label}, ${commit.recipient.withoutTeamGlyph()}" },
        ) {
            Text(commit.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        commit.onSwapOut?.let { swap ->
            TextButton(onClick = swap, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Swap out of the pool instead")
            }
        }
    }
}

/** Why the hero can't be chosen now (a dealt-hands draft: not in this player's hand). */
@Composable
private fun PeekNote(note: String) {
    Text(
        note,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen)
            .padding(top = Spacing.sm, bottom = Spacing.xl),
    )
}

@Composable
private fun PeekHeading(text: String) {
    Text(
        text,
        style = GoaTextStyles.sectionHead,
        color = GoaTheme.colors.heading,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * The five starting cards as the official scans, three to a row, each named under it (the printed
 * name is too small at this size); a card without a scan is a colour-edged name tile.
 */
@Composable
private fun StartingCardScans(cards: List<CardInfo>) {
    val columns = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        cards.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { card ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        CardScan(
                            image = card.image,
                            contentDescription = "${card.name}, ${cardKind(card)}",
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                Modifier.fillMaxSize().border(3.dp, cardColor(card.color)).padding(Spacing.sm),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    card.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        Text(
                            card.name,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(top = Spacing.xs).clearAndSetSemantics {},
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A hero's roles as small outlined labels. */
@Composable
internal fun RoleChips(roles: List<Role>, modifier: Modifier = Modifier, highlight: Color? = null) {
    if (roles.isEmpty()) return
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        roles.forEach { role -> RoleLabel(role, highlight) }
    }
}

@Composable
internal fun RoleLabel(role: Role, color: Color? = null) {
    val tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(6.dp)
    Text(
        role.label,
        style = MaterialTheme.typography.labelSmall,
        color = if (color == null) MaterialTheme.colorScheme.onSurface else tint,
        modifier = Modifier
            .background(color?.copy(alpha = 0.12f) ?: Color.Transparent, shape)
            .border(1.dp, color?.copy(alpha = 0.6f) ?: GoaTheme.colors.engravedBorder, shape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
