package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player

/*
 * Shared game-facing components. APIs are stable contracts used by every feature; visuals may
 * be refined in place (sigils, avatars) without changing call sites.
 */

/** URL for a bundled asset path such as `official/heroes/arien/portrait.webp`. */
fun assetUrl(path: String): String = "file:///android_asset/$path"

/**
 * Official hero art inside our frame, falling back to the hero's sigil when [portrait] is null
 * or fails to load. Never shows a broken tile.
 */
@Composable
fun HeroPortrait(
    heroId: String,
    portrait: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
) {
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, GoaTheme.colors.engravedBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (portrait == null) {
            HeroSigil(heroId = heroId, modifier = Modifier.fillMaxWidth().padding(Spacing.md))
        } else {
            SubcomposeAsyncImage(
                model = assetUrl(portrait),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                error = { HeroSigil(heroId = heroId, modifier = Modifier.padding(Spacing.md)) },
                loading = { HeroSigil(heroId = heroId, modifier = Modifier.padding(Spacing.md)) },
            )
        }
    }
}

/**
 * Original geometric emblem for a hero (design/sigils/<id>.svg → VectorDrawable). Until a sigil
 * exists for [heroId], renders a bronze monogram.
 */
@Composable
fun HeroSigil(heroId: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.semantics { contentDescription = "" }, contentAlignment = Alignment.Center) {
        Text(
            text = heroId.take(1).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = tint,
        )
    }
}

/** Player crest tinted by the player's colour; [player] null = unknown player. */
@Composable
fun PlayerAvatar(player: Player?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = player?.let { Color(it.color.toInt()) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = player?.name?.take(1)?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Team identity is never colour-only: ◆ Red / ● Blue (docs/DESIGN.md). */
@Composable
fun TeamBadge(team: Team, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val color = teamColor(team)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(if (team == Team.RED) "◆" else "●", color = color, style = MaterialTheme.typography.titleMedium)
        if (showLabel) {
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = if (team == Team.RED) "Red" else "Blue",
                color = color,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun teamColor(team: Team): Color = if (team == Team.RED) GoaTheme.colors.teamRed else GoaTheme.colors.teamBlue

@Composable
fun cardColor(color: CardColor): Color = when (color) {
    CardColor.GOLD -> GoaTheme.colors.cardGold
    CardColor.SILVER -> GoaTheme.colors.cardSilver
    CardColor.RED -> GoaTheme.colors.cardRed
    CardColor.GREEN -> GoaTheme.colors.cardGreen
    CardColor.BLUE -> GoaTheme.colors.cardBlue
    CardColor.PURPLE -> GoaTheme.colors.cardPurple
    CardColor.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Complexity ★–★★★★ (rulebook scale 1..4); null renders nothing. */
@Composable
fun ComplexityStars(complexity: Int?, modifier: Modifier = Modifier, max: Int = 4, starSize: Dp = 14.dp) {
    if (complexity == null) return
    Row(
        modifier.semantics { contentDescription = "Complexity $complexity of $max" },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(max) { i ->
            Icon(
                imageVector = if (i < complexity) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (i <
                    complexity
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(starSize),
            )
        }
    }
}

/** Top-bar action that opens Settings; place on every top-level screen. */
@Composable
fun SettingsActionButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
    }
}

/** Friendly empty/error state with an optional call to action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.lg))
            action()
        }
    }
}
