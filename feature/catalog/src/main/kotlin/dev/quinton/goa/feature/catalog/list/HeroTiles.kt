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
import dev.quinton.goa.core.designsystem.component.complexityName
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.presentation.catalog.CatalogSort
import dev.quinton.goa.core.presentation.catalog.HeroListItem
import dev.quinton.goa.core.presentation.catalog.matchHint
import dev.quinton.goa.core.presentation.catalog.metricLine
import dev.quinton.goa.core.presentation.catalog.rangedLabel
import dev.quinton.goa.core.presentation.catalog.spokenLabel
import dev.quinton.goa.core.presentation.catalog.traitsLine
import dev.quinton.goa.feature.catalog.ui.MetaPill
import dev.quinton.goa.feature.catalog.ui.UnverifiedBadge

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
            HeroPortrait(
                heroId = hero.id,
                portrait = hero.portrait,
                contentDescription = null,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp),
            )
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
