package dev.quinton.goa.feature.draft.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.CardScan
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.StatBar
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Hero peek during a draft (long-press a pool tile): complexity, roles, stat bars (base → max),
 * the five starting cards and a link to the full hero page. Catalog data only, never draft
 * state, so it can't reveal a hidden choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeroPeekSheet(hero: HeroInfo, onDismiss: () -> Unit, onOpenHero: ((String) -> Unit)?) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroPortrait(hero.id, hero.portrait, null, Modifier.size(width = 80.dp, height = 100.dp))
                Spacer(Modifier.width(Spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            hero.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                        )
                        if (hero.symbol != null) {
                            HeroSymbol(
                                hero.id,
                                hero.symbol,
                                Modifier.padding(start = Spacing.sm).size(scaledIconSize(32.dp, 1.4f)),
                            )
                        }
                    }
                    if (hero.title.isNotEmpty()) {
                        Text(
                            hero.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ComplexityStars(hero.complexity, showLabel = true)
                    RoleChips(hero.roles)
                }
            }
            if (!hero.known) {
                Text(
                    "This hero isn't in the catalog.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PeekHeading("Stats · base → max")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    for ((label, stat) in PEEK_STATS) StatBar(label, hero.stats.get(stat))
                }
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
            if (onOpenHero != null && hero.known) {
                OutlinedButton(
                    onClick = { onOpenHero(hero.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Open hero page")
                }
            }
        }
    }
}

private val PEEK_STATS = listOf(
    "Attack" to Stat.ATTACK,
    "Defense" to Stat.DEFENSE,
    "Initiative" to Stat.INITIATIVE,
    "Movement" to Stat.MOVEMENT,
)

@Composable
private fun PeekHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
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
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = (color ?: Color.Transparent).copy(alpha = if (color == null) 0f else 0.12f),
        border = BorderStroke(1.dp, color?.copy(alpha = 0.6f) ?: GoaTheme.colors.engravedBorder),
    ) {
        Text(
            role.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (color == null) MaterialTheme.colorScheme.onSurface else tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
