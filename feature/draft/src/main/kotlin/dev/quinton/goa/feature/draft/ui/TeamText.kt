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
import dev.quinton.goa.core.domain.draft.TeamSlot
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

/** What the draft calls the method's Team A: the team the Tie Breaker coin shows (rulebook p.22). */
internal const val COIN_TEAM = "Coin team"

/** The method's Team B. */
internal const val OTHER_TEAM = "Other team"

/** A method slot's name at the table: [COIN_TEAM] for A, [OTHER_TEAM] for B. */
internal fun slotName(slot: TeamSlot): String = if (slot == TeamSlot.A) COIN_TEAM else OTHER_TEAM

/**
 * A method's own words with its teams named as the table knows them: "Team A bans 1" reads "The coin team bans 1",
 * "Team B" "the other team". The words are the app's (draft-methods.json), so only the names change.
 */
internal fun coinTeamWords(text: String): String {
    val named = text
        .replace(TEAM_A, "the ${COIN_TEAM.lowercase()}")
        .replace(TEAM_B, "the ${OTHER_TEAM.lowercase()}")
    // A sentence that now starts with "the" starts with "The".
    return SENTENCE_START.replace(named) { it.value.uppercase() }
}

private val TEAM_A = Regex("""\bTeam A\b""")
private val TEAM_B = Regex("""\bTeam B\b""")
private val SENTENCE_START = Regex("""(^|[.!?]\s+)[a-z]""")
