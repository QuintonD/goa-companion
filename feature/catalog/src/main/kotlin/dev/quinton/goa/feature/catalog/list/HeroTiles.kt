package dev.quinton.goa.feature.catalog.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.complexityName
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.catalog.model.AttackStyle
import dev.quinton.goa.feature.catalog.model.CatalogSort
import dev.quinton.goa.feature.catalog.model.HeroListItem
import dev.quinton.goa.feature.catalog.model.MatchField
import dev.quinton.goa.feature.catalog.ui.MetaPill
import dev.quinton.goa.feature.catalog.ui.UnverifiedBadge
import dev.quinton.goa.feature.catalog.ui.elo
import dev.quinton.goa.feature.catalog.ui.label
import dev.quinton.goa.feature.catalog.ui.percent
import dev.quinton.goa.feature.catalog.ui.plural

/** Official roles only ("Disabler · Pusher"); ranged is shown separately so it never reads as a role. */
internal fun HeroListItem.traitsLine(): String = hero.roles.joinToString(" · ") { it.label }

/** "Ranged" when most of the hero's Attack cards are ranged — an app-derived trait, not a role. */
internal val HeroListItem.rangedLabel: String?
    get() = if (attackStyle == AttackStyle.RANGED) "Ranged" else null

/** Secondary line shown when sorting by a stat: "Elo 1268 · 9 games" or "64% wins · 11 games". */
internal fun HeroListItem.metricLine(sort: CatalogSort): String? = when (sort) {
    CatalogSort.ELO -> rating?.takeIf { it.games > 0 }
        ?.let { "Elo ${elo(it.value)} · ${plural(it.games, "game")}${if (it.provisional) " · provisional" else ""}" }
        ?: "No rated games"

    CatalogSort.WIN_RATE -> usage?.takeIf { it.matches > 0 }
        ?.let { "${percent(it.winRate)} wins · ${plural(it.matches, "game")}" }
        ?: "No games logged"

    else -> null
}

/** Why a search matched when it wasn't the name: "Card: Tidal Blast". */
internal fun HeroListItem.matchHint(): String? {
    val hit = hit ?: return null
    return when (hit.field) {
        MatchField.CARD_NAME -> hit.card?.let { "Card: ${it.name}" }
        MatchField.CARD_TEXT -> hit.card?.let { "Card text: ${it.name}" }
        MatchField.ALIAS -> "Matches an alias"
        else -> null
    }
}

/**
 * What a screen reader says for a tile or row, in reading order: "Arien, the Tidemaster.
 * Complexity 1 of 4, Challenging. Tactician · Disabler. Core. Ranged. …". Set on the tile itself
 * so a tile cut off at the edge of the screen (its texts scrolled out of view) is still labelled.
 */
internal fun HeroListItem.spokenLabel(sort: CatalogSort): String = listOfNotNull(
    "${hero.name}, ${hero.title}",
    hero.complexity?.let { c -> "Complexity $c of 4" + (complexityName(c)?.let { ", $it" } ?: "") },
    traitsLine().ifEmpty { null },
    productName,
    rangedLabel,
    if (hero.verified) null else "Unverified data",
    metricLine(sort),
    matchHint(),
).joinToString(". ")

/**
 * Compact grid tile (three per row on a phone): short portrait with stars, then name, title,
 * roles and pills. Text wraps or ellipsizes; the tile never grows wider with the font scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeroGridTile(item: HeroListItem, sort: CatalogSort, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hero = item.hero
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = item.spokenLabel(sort) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        val surface = MaterialTheme.colorScheme.surface
        Box(Modifier.fillMaxWidth().aspectRatio(PORTRAIT_RATIO)) {
            // Unframed art with the fade into the tile drawn over it in the same node: the fewest
            // layers per tile while the grid scrolls.
            HeroPortrait(
                heroId = hero.id,
                portrait = hero.portrait,
                contentDescription = null,
                shape = RectangleShape,
                framed = false,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val fade = Brush.verticalGradient(0.55f to Color.Transparent, 1f to surface)
                        onDrawWithContent {
                            drawContent()
                            drawRect(fade)
                        }
                    },
            )
            ComplexityStars(
                complexity = hero.complexity,
                starSize = 12.dp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = Spacing.sm, bottom = Spacing.xs),
            )
            // The deck symbol (the hero's card backs), as players know the hero at the table.
            if (hero.symbol != null) {
                HeroSymbol(
                    hero.id,
                    hero.symbol,
                    Modifier.align(Alignment.BottomEnd).padding(end = Spacing.xs, bottom = 2.dp).size(GRID_SYMBOL),
                )
            }
            if (!hero.verified) {
                UnverifiedBadge(compact = true, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs))
            }
        }
        Column(Modifier.padding(start = Spacing.sm, end = Spacing.sm, top = 2.dp, bottom = Spacing.sm)) {
            Text(
                hero.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hero.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val traits = item.traitsLine()
            if (traits.isNotEmpty()) {
                Text(
                    traits,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            FlowRow(
                Modifier.padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MetaPill(item.productName)
                item.rangedLabel?.let { MetaPill(it, color = MaterialTheme.colorScheme.primary) }
            }
            TileFootnotes(item, sort)
        }
    }
}

@Composable
private fun TileFootnotes(item: HeroListItem, sort: CatalogSort) {
    item.metricLine(sort)?.let {
        Text(
            it,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
    item.matchHint()?.let { hint ->
        Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Compact list row (six or more per screen): small portrait, name and title, stars and pills, roles. */
@Composable
internal fun HeroListRow(item: HeroListItem, sort: CatalogSort, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hero = item.hero
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = item.spokenLabel(sort) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = Spacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                HeroPortrait(
                    heroId = hero.id,
                    portrait = hero.portrait,
                    contentDescription = null,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 6.dp, bottom = 6.dp).size(48.dp),
                )
                if (hero.symbol != null) {
                    HeroSymbol(hero.id, hero.symbol, Modifier.align(Alignment.BottomEnd).size(LIST_SYMBOL))
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        hero.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        hero.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ComplexityStars(hero.complexity, starSize = 12.dp)
                    MetaPill(item.productName, modifier = Modifier.weight(1f, fill = false))
                    item.rangedLabel?.let { MetaPill(it, color = MaterialTheme.colorScheme.primary) }
                    if (!hero.verified) UnverifiedBadge(compact = true)
                }
                val traits = item.traitsLine()
                if (traits.isNotEmpty()) {
                    Text(
                        traits,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TileFootnotes(item, sort)
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Width / height: a short portrait so three rows of tiles fit on a phone screen. */
private const val PORTRAIT_RATIO = 1.3f

private val GRID_SYMBOL = 30.dp
private val LIST_SYMBOL = 24.dp
