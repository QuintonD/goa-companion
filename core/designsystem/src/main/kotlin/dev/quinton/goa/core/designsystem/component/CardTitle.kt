package dev.quinton.goa.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles

/**
 * How large an official card name is set, by where it stands. Every size is Crimson semibold, as the game prints a
 * card's name, so the same name keeps one typographic identity from the catalog to the table.
 */
enum class CardTitleSize(val style: TextStyle) {
    /** Under a card scan or thumbnail (deck captions, a hand's cards): 15 sp. */
    Caption(cardTitle(15, 18)),

    /** The name leading a result or list row: 18 sp. */
    Row(cardTitle(18, 23)),

    /** A card in a tile that is about it (a trivia duel's choices): 22 sp. */
    Large(cardTitle(22, 27)),

    /** The card a screen is about (a trivia question): 26 sp. */
    Title(cardTitle(26, 31)),
}

private fun cardTitle(size: Int, line: Int) = GoaTextStyles.printed.copy(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.sp,
    // A long word under a narrow card breaks with a hyphen, never bare.
    hyphens = Hyphens.Auto,
)

/** A card's name inside running text ("Matches: Runic Dagger"), as [CardTitle] sets it, at the text's own size. */
val CardTitleSpan: SpanStyle =
    SpanStyle(fontFamily = GoaTextStyles.printed.fontFamily, fontWeight = FontWeight.SemiBold)

/**
 * An official card's name: Crimson, sized for its context ([CardTitleSize]). The one way the app sets a card name, so
 * catalog rows, deck captions, trivia and the game table read alike. Wraps rather than cuts, unless [maxLines] says.
 */
@Composable
fun CardTitle(
    name: String,
    modifier: Modifier = Modifier,
    size: CardTitleSize = CardTitleSize.Row,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        name,
        modifier = modifier,
        style = size.style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}
