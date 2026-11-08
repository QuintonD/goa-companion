package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.cardColorName

/**
 * A card's family in words with its colour's mark, the same on every screen (DESIGN.md "Card family"): the card
 * frame's ribbon (a dot for an Ultimate's purple) then "Red · Tier II", "Gold · Basic" or "Ultimate", and, given a
 * hero's [symbol] path, that hero's official deck symbol before it. Never colour alone: the word always says it.
 * [text] overrides the words (a Handicap or a spellbook page, from the catalog's own `kindLabel`).
 */
@Composable
fun CardFamilyLabel(
    color: CardColor,
    tier: Int?,
    modifier: Modifier = Modifier,
    text: String = cardFamilyWords(color, tier),
    heroId: String? = null,
    symbol: String? = null,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val mark = scaledIconSize(FamilyMark, 1.6f)
    Row(
        modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (heroId != null) {
            HeroSymbol(heroId, symbol, Modifier.size(mark + 4.dp))
            Spacer(Modifier.width(4.dp))
        }
        if (color == CardColor.PURPLE || color == CardColor.OTHER) {
            CardColorDot(color, Modifier.clearAndSetSemantics {}, size = mark * 0.7f)
        } else {
            ColourRibbon(PlateColor.of(color), Modifier.size(width = mark * 0.5f, height = mark))
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = style, color = textColor)
    }
}

/**
 * A card family's words: "Red · Tier II", "Gold · Basic", "Ultimate" (tier 4 or the purple frame), the colour alone
 * when no tier is printed. App-authored labels over the catalog's colour and tier.
 */
fun cardFamilyWords(color: CardColor, tier: Int?): String = when {
    color == CardColor.PURPLE || tier == ULTIMATE_TIER -> "Ultimate"
    color == CardColor.GOLD || color == CardColor.SILVER -> "${cardColorName(color)} · Basic"
    tier != null -> "${cardColorName(color)} · Tier ${familyNumeral(tier)}"
    else -> cardColorName(color)
}

private fun familyNumeral(tier: Int): String = listOf("I", "II", "III").getOrElse(tier - 1) { tier.toString() }

private const val ULTIMATE_TIER = 4
private val FamilyMark = 18.dp
