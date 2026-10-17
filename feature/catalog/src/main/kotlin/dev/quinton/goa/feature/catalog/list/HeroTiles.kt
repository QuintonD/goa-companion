package dev.quinton.goa.feature.catalog.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.HeroPortrait
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

@Composable
internal fun heroNameStyle() = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp)

/** "Disabler · Pusher · Ranged". */
internal fun HeroListItem.traitsLine(): String =
    (hero.roles.map { it.label } + listOfNotNull(attackStyle?.takeIf { it == AttackStyle.RANGED }?.label))
        .joinToString(" · ")

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

@Composable
internal fun HeroGridTile(item: HeroListItem, sort: CatalogSort, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hero = item.hero
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(PORTRAIT_RATIO)) {
            HeroPortrait(
                heroId = hero.id,
                portrait = hero.portrait,
                contentDescription = null,
                shape = RectangleShape,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        ),
                    ),
            )
            ComplexityStars(
                complexity = hero.complexity,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = Spacing.md, bottom = Spacing.sm),
            )
            if (!hero.verified) {
                UnverifiedBadge(compact = true, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm))
            }
        }
        Column(Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.md)) {
            Text(hero.name, style = heroNameStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                hero.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(Spacing.sm))
            val traits = item.traitsLine()
            if (traits.isNotEmpty()) {
                Text(
                    traits,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(6.dp))
            }
            MetaPill(item.productName)
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
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
    item.matchHint()?.let { hint ->
        Row(Modifier.padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
internal fun HeroListRow(item: HeroListItem, sort: CatalogSort, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hero = item.hero
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Row(
            Modifier.heightIn(min = 88.dp).padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroPortrait(
                heroId = hero.id,
                portrait = hero.portrait,
                contentDescription = null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        hero.displayName,
                        style = heroNameStyle(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ComplexityStars(hero.complexity)
                    MetaPill(item.productName, modifier = Modifier.weight(1f, fill = false))
                    if (!hero.verified) UnverifiedBadge(compact = true)
                }
                val traits = item.traitsLine()
                if (traits.isNotEmpty()) {
                    Text(
                        traits,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
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

private const val PORTRAIT_RATIO = 1.05f
