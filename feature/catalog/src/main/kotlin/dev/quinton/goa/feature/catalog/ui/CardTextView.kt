package dev.quinton.goa.feature.catalog.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import dev.quinton.goa.core.model.catalog.CardText

private const val ICON_PREFIX = "icon:"

/**
 * Builds the printed card text: icons become inline placeholders whose alternate text is the
 * icon's spoken label (so TalkBack reads "Gain 1 Coin"), reminder text is italic, breaks are
 * paragraph breaks. Unknown icon names fall back to their text, never a blank.
 */
fun cardTextAnnotated(text: String, reminderColor: Color): AnnotatedString = buildAnnotatedString {
    CardText.parse(text).forEach { segment ->
        when (segment) {
            is CardText.Segment.Text -> append(segment.value)

            is CardText.Segment.Icon -> {
                val icon = GameIcons.forToken(segment.name)
                if (icon == null) {
                    append(segment.name.replace('_', ' '))
                } else {
                    appendInlineContent(ICON_PREFIX + segment.name, alternateText = icon.label)
                }
            }

            is CardText.Segment.Reminder -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = reminderColor)) {
                append("(${segment.value})")
            }

            CardText.Segment.Break -> append("\n")
        }
    }
}

/** Inline content for every known icon, sized relative to the text (scales with font size). */
@Composable
fun rememberCardTextInlineContent(tint: Color): Map<String, InlineTextContent> = remember(tint) {
    CardText.ICONS.mapNotNull { name ->
        val icon = GameIcons.forToken(name) ?: return@mapNotNull null
        ICON_PREFIX + name to InlineTextContent(
            Placeholder(
                width = 1.15.em,
                height = 1.15.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Icon(icon.vector, contentDescription = null, tint = tint)
        }
    }.toMap()
}

/** Card text with inline game icons. */
@Composable
fun CardTextView(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = LocalContentColor.current,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
) {
    val reminder = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(text, reminder) { cardTextAnnotated(text, reminder) }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        inlineContent = rememberCardTextInlineContent(iconTint),
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}
