package dev.quinton.goa.feature.draft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.model.match.Team

/**
 * Words that carry a team's glyph (the shared draft text: "◆ Red is Team A", "Pick ●") with the official
 * gear or star in its place, in line. The glyph stays the text's alternate, so what TalkBack hears and what
 * a test finds don't change. [markGround] puts a disc behind the mark, for a button in the team's colour.
 */
@Composable
internal fun TeamText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    markGround: Color? = null,
) {
    val marked = remember(text) {
        buildAnnotatedString {
            text.forEach { char ->
                val team = glyphTeam(char)
                if (team != null) appendInlineContent(team.name, char.toString()) else append(char)
            }
        }
    }
    val marks = remember(markGround) {
        Team.entries.associate { team ->
            team.name to InlineTextContent(Placeholder(MARK_EM.em, MARK_EM.em, PlaceholderVerticalAlign.TextCenter)) {
                if (markGround == null) {
                    TeamMark(team, Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(markGround, CircleShape).padding(1.dp)) {
                        TeamMark(team, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
    Text(
        marked,
        modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        inlineContent = marks,
        style = style,
    )
}

/** The same words for a place that holds a plain string beside its own mark (a title plate). */
internal fun String.withoutTeamGlyph(): String = Team.entries.fold(this) { text, team ->
    text.replace("${teamGlyph(team)} ", "")
}

private fun glyphTeam(char: Char): Team? = Team.entries.firstOrNull { teamGlyph(it) == char.toString() }

private const val MARK_EM = 1.25f
