package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.quinton.goa.core.designsystem.icon.HeroSigils
import dev.quinton.goa.core.designsystem.icon.PlayerCrests
import dev.quinton.goa.core.designsystem.theme.GoaFonts
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.designsystem.theme.playerColor
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import kotlin.math.roundToInt

/*
 * Shared game-facing components. APIs are stable contracts used by every feature; visuals may
 * be refined in place (sigils, avatars) without changing call sites.
 */

/** URL for a bundled asset path such as `official/heroes/arien/portrait.webp`. */
fun assetUrl(path: String): String = "file:///android_asset/$path"

/**
 * Official hero art inside our frame, falling back to the hero's sigil when [portrait] is null
 * or fails to load. Never shows a broken tile. The frame is a 1 dp bronze border with a soft
 * inner gradient (bronze glow at the top, deep shade at the bottom) so art of any palette sits
 * in the app's look.
 */
@Composable
fun HeroPortrait(
    heroId: String,
    portrait: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val colors = GoaTheme.colors
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, colors.medallion),
                ),
            )
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // A plain AsyncImage (no subcomposition per tile: cheap in lazy grids) over the sigil,
        // which shows while loading, on error and without art. Coil sizes the request to the
        // tile's constraints, so a 110 dp tile never decodes the full portrait.
        var loaded by remember(portrait) { mutableStateOf(false) }
        if (portrait == null || !loaded) SigilMedallionContent(heroId)
        if (portrait != null) {
            AsyncImage(
                model = assetUrl(portrait),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onSuccess = { loaded = true },
                onError = { loaded = false },
            )
        }
        // Inner frame: top glow + bottom vignette, then the engraved bronze edge.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.bronzeGlow,
                        0.35f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to colors.medallion.copy(alpha = 0.55f),
                    ),
                )
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), shape)
                .padding(3.dp)
                .border(1.dp, colors.engravedBorder.copy(alpha = 0.2f), shape),
        )
    }
}

@Composable
private fun SigilMedallionContent(heroId: String) {
    HeroSigil(
        heroId = heroId,
        modifier = Modifier.centeredSquare(SIGIL_FRACTION),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
    )
}

private const val SIGIL_FRACTION = 0.56f

/** Fills the parent and draws its content as a centred square of [fraction] × the shorter side. */
private fun Modifier.centeredSquare(fraction: Float): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
    val side = (minOf(width, height) * fraction).roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(width, height) { placeable.place((width - side) / 2, (height - side) / 2) }
}

/**
 * Original geometric emblem for a hero (design/sigils/<id>.svg → VectorDrawable), tinted.
 * Unknown ids render a Cinzel monogram in a thin ring. Decorative: no semantics.
 * Fills the space it is given (square); 24 dp when unconstrained.
 */
@Composable
fun HeroSigil(heroId: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    val res = HeroSigils.drawableFor(heroId)
    BoxWithConstraints(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        val side = when {
            maxWidth == Dp.Infinity && maxHeight == Dp.Infinity -> 24.dp
            maxWidth == Dp.Infinity -> maxHeight
            maxHeight == Dp.Infinity -> maxWidth
            else -> min(maxWidth, maxHeight)
        }
        if (res != null) {
            Image(
                painter = painterResource(res),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(side),
            )
        } else {
            Monogram(heroId.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", tint, side)
        }
    }
}

@Composable
private fun Monogram(letter: String, tint: Color, side: Dp) {
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.minDimension / 12f
            drawCircle(tint, radius = size.minDimension / 2f - stroke, style = Stroke(stroke))
        }
        Text(
            text = letter,
            color = tint,
            fontFamily = GoaFonts.display,
            fontWeight = FontWeight.Bold,
            fontSize = (side.value * 0.42f).sp,
            maxLines = 1,
        )
    }
}

/**
 * Player crest (one of 24, `player.avatarId`) tinted by the player's colour inside a circular
 * frame; initial letter when the crest index is unknown, "?" when [player] is null.
 * Decorative: pair it with the player's name.
 */
@Composable
fun PlayerAvatar(player: Player?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = player?.let { playerColor(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val crest = player?.let { PlayerCrests.drawableFor(it.avatarId) }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.26f), color.copy(alpha = 0.08f)),
                ),
            )
            .border(if (size >= 40.dp) 2.dp else 1.5.dp, color, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (crest != null) {
            Image(
                painter = painterResource(crest),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier = Modifier.size(size * 0.56f),
            )
        } else {
            Text(
                text = player?.name?.trim()?.take(1)?.uppercase()?.ifEmpty { "?" } ?: "?",
                color = color,
                fontFamily = GoaFonts.display,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
                maxLines = 1,
            )
        }
    }
}

/** Team identity is never colour-only: ◆ Red / ● Blue (docs/DESIGN.md). */
@Composable
fun TeamBadge(team: Team, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val color = teamColor(team)
    val label = if (team == Team.RED) "Red" else "Blue"
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Team $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamMark(team, color = color, markSize = scaledIconSize(12.dp))
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The team's shape mark alone: diamond for Red, disc for Blue. */
@Composable
fun TeamMark(team: Team, modifier: Modifier = Modifier, color: Color = teamColor(team), markSize: Dp = 12.dp) {
    Canvas(modifier.size(markSize).clearAndSetSemantics {}) {
        if (team == Team.RED) {
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width / 2f, size.height)
                lineTo(0f, size.height / 2f)
                close()
            }
            drawPath(path, color)
        } else {
            drawCircle(color, radius = size.minDimension * 0.44f, center = Offset(size.width / 2f, size.height / 2f))
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

/** Official complexity names (rulebook): ★ Challenging … ★★★★ Wizard. */
fun complexityName(complexity: Int): String? = when (complexity) {
    1 -> "Challenging"
    2 -> "Complex"
    3 -> "Very Complex"
    4 -> "Wizard"
    else -> null
}

/**
 * Complexity ★–★★★★ (rulebook scale 1..4); null renders nothing. [showLabel] appends the
 * official name ("Very Complex").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComplexityStars(
    complexity: Int?,
    modifier: Modifier = Modifier,
    max: Int = 4,
    starSize: Dp = 14.dp,
    showLabel: Boolean = false,
) {
    if (complexity == null) return
    val name = complexityName(complexity)
    val size = scaledIconSize(starSize)
    // FlowRow: with large text the label moves under the stars instead of breaking mid-word.
    FlowRow(
        modifier.clearAndSetSemantics {
            contentDescription = "Complexity $complexity of $max" + (name?.let { ", $it" } ?: "")
        },
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            modifier = if (showLabel) Modifier.padding(end = Spacing.sm - 1.dp) else Modifier,
        ) {
            repeat(max) { i ->
                val filled = i < complexity
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = if (filled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(size),
                )
            }
        }
        if (showLabel && name != null) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
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

/** Friendly empty/error state with an optional call to action: icon in a bronze medallion. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.22f), primary.copy(alpha = 0.04f))))
                .border(1.dp, primary.copy(alpha = 0.45f), CircleShape)
                .padding(6.dp)
                .border(1.dp, GoaTheme.colors.engravedBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(Spacing.lg + Spacing.xs))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
