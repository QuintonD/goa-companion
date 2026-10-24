package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.cardtext.GameIcon
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.CardColorDot
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.cardColorName
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.board.tierLabel
import dev.quinton.goa.feature.board.play.PlayText

/** Official card scans are 729 × 1026. */
internal const val CARD_ASPECT = 729f / 1026f

/**
 * A card as it lies on the table: the official card image (`file:///android_asset/official/…`), or
 * a designed face with its name and initiative when there is no image; [card] null = facedown (the
 * back, original art). Decorative for TalkBack: the caller describes it.
 */
@Composable
fun TableCard(card: Card?, modifier: Modifier = Modifier, width: Dp = 76.dp) {
    val shape = RoundedCornerShape(width * 0.08f)
    Box(
        modifier
            .width(width)
            .aspectRatio(CARD_ASPECT)
            .clip(shape)
            .clearAndSetSemantics {},
    ) {
        if (card == null) {
            CardBack(Modifier.fillMaxSize())
        } else {
            var failed by remember(card.id) { mutableStateOf(card.image == null) }
            if (failed) {
                CardFace(card, Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = assetUrl(card.image!!),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onError = { failed = true },
                )
            }
        }
    }
}

/** The back of a card: an original bronze lattice on the deep-ocean canvas. */
@Composable
private fun CardBack(modifier: Modifier = Modifier) {
    val bronze = MaterialTheme.colorScheme.primary
    val deep = GoaTheme.colors.medallion
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.background(Brush.verticalGradient(listOf(surface, deep)))) {
        val stroke = Stroke(width = size.minDimension * 0.02f)
        val inset = size.minDimension * 0.08f
        drawRoundRect(
            bronze.copy(alpha = 0.7f),
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(inset),
            style = stroke,
        )
        // A diamond with a trident-like spine: the app's own mark, not the game's.
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.24f
        val diamond = Path().apply {
            moveTo(c.x, c.y - r * 1.3f)
            lineTo(c.x + r, c.y)
            lineTo(c.x, c.y + r * 1.3f)
            lineTo(c.x - r, c.y)
            close()
        }
        drawPath(diamond, bronze.copy(alpha = 0.18f))
        drawPath(diamond, bronze, style = stroke)
        drawLine(bronze, Offset(c.x, c.y - r * 0.8f), Offset(c.x, c.y + r * 0.8f), strokeWidth = stroke.width)
        drawLine(bronze, Offset(c.x - r * 0.45f, c.y - r * 0.3f), Offset(c.x + r * 0.45f, c.y - r * 0.3f), stroke.width)
    }
}

/** A designed face for a card without an image: colour band, tier, initiative and name. */
@Composable
private fun CardFace(card: Card, modifier: Modifier = Modifier) {
    val accent = cardColor(card.color)
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, accent, RoundedCornerShape(6.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardColorDot(card.color, size = 8.dp)
            Spacer(Modifier.width(4.dp))
            card.initiative?.let {
                Text("$it", style = GoaTextStyles.numeric, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            card.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            tierLabel(card),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** What TalkBack reads for a card: name, tier and colour, initiative, values. */
fun spokenCard(card: Card): String = buildList {
    add(card.name)
    add("${tierLabel(card)} ${cardColorName(card.color)}")
    card.initiative?.let { add("Initiative $it") }
    card.values.attack?.let { add("Attack $it") }
    card.values.defense?.let { add("Defense $it") }
    card.values.movement?.let { add("Movement $it") }
    card.values.range?.let { add("Range $it") }
    card.values.radius?.let { add("Radius $it") }
}.joinToString(", ")

/** A card's printed value with its icon ("Attack 3"), as the card focus viewer lists them. */
@Composable
internal fun ValueChip(icon: GameIcon, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(
            icon.vector,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(scaledIconSize(16.dp)),
        )
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** An item's stat as its icon and a line ("+1 Defense"). */
@Composable
internal fun ItemLine(stat: Stat, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(min = 24.dp).semantics(mergeDescendants = true) {},
    ) {
        ItemIcon(stat)
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The item icon with the stat it raises (backpack + stat glyph). */
@Composable
internal fun ItemIcon(stat: Stat, size: Dp = 18.dp) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        Row(Modifier.padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                GameIcons.item.vector,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(scaledIconSize(size * 0.8f, 1.4f)),
            )
            Icon(
                statIcon(stat).vector,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(scaledIconSize(size, 1.4f)),
            )
        }
    }
}

internal fun statIcon(stat: Stat): GameIcon = when (stat) {
    Stat.ATTACK -> GameIcons.attack
    Stat.DEFENSE -> GameIcons.defense
    Stat.INITIATIVE -> GameIcons.initiative
    Stat.MOVEMENT -> GameIcons.movement
    Stat.RANGE -> GameIcons.range
    Stat.RADIUS -> GameIcons.radius
}
