package dev.quinton.goa.web.play.table

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/*
 * Small pieces the game table and its sheets share (the web's TableCards / TableStatus bits).
 */

/** The team's CSS name ("red" / "blue"), for team colours (never colour alone: ◆ / ● go with it). */
fun teamCss(team: Team): String = if (team == Team.RED) "red" else "blue"

/** A hero's official portrait in a ring of its team's colour; initials until it loads. */
@Composable
fun HeroPortrait(hero: BoardHero, team: Team?, size: Int, dim: Boolean = false) {
    Span({
        classes("goa-portrait")
        team?.let { classes(teamCss(it)) }
        if (dim) classes("dim")
        attr("aria-hidden", "true")
        style {
            property("width", "${size}px")
            property("height", "${size}px")
        }
    }) {
        Span({ classes("goa-portrait-initials") }) { Text(hero.name.take(2)) }
        hero.portrait?.let { path ->
            Img(path, alt = "", attrs = {
                attr("loading", "lazy")
                attr("decoding", "async")
                attr("draggable", "false")
            })
        }
    }
}

/** A copper small-caps heading inside a panel or sheet. */
@Composable
fun Overline(text: String, extraClass: String? = null) {
    P({
        classes("t-overline", "goa-table-overline")
        extraClass?.let { classes(it) }
        attr("role", "heading")
        attr("aria-level", "3")
    }) { Text(text) }
}

/** A quiet line of explanation. */
@Composable
fun Quiet(text: String) {
    P({ classes("t-body-small", "goa-quiet") }) { Text(text) }
}

/** The icon (in `icons/`) of an action: the primary's by the card's type. */
fun actionIconName(action: ActionType, card: Card?): String = when (action) {
    ActionType.PRIMARY -> when (card?.primary) {
        PrimaryAction.ATTACK -> "g_attack"
        PrimaryAction.DEFENSE, PrimaryAction.DEFENSE_SKILL -> "g_defense"
        PrimaryAction.MOVEMENT -> "g_movement"
        else -> "g_skill"
    }

    ActionType.MOVEMENT -> "g_movement"

    ActionType.FAST_TRAVEL -> "g_fast"

    ActionType.ATTACK -> "g_attack"

    ActionType.CLEAR -> "g_clear"

    ActionType.HOLD -> "g_hold"
}

/** The icon of a printed value. */
fun statIconName(stat: Stat): String = when (stat) {
    Stat.ATTACK -> "g_attack"
    Stat.DEFENSE -> "g_defense"
    Stat.INITIATIVE -> "g_initiative"
    Stat.MOVEMENT -> "g_movement"
    Stat.RANGE -> "g_range"
    Stat.RADIUS -> "g_radius"
}

/**
 * True while the screen is a phone held sideways (or any wide, short window): the panel goes to
 * the side and the map's Lane runs left to right. Matches the CSS of `.goa-play-body`.
 */
@Composable
fun rememberLandscape(): Boolean {
    val query = remember { window.matchMedia(LANDSCAPE_QUERY) }
    var landscape by remember { mutableStateOf(query.matches) }
    DisposableEffect(query) {
        val listener: (dynamic) -> Unit = { landscape = query.matches }
        query.asDynamic().addEventListener("change", listener)
        onDispose { query.asDynamic().removeEventListener("change", listener) }
    }
    return landscape
}

const val LANDSCAPE_QUERY = "(orientation: landscape) and (min-width: 560px)"

/** A polite live region: VoiceOver reads [text] when it changes (Android's TalkBack announcements). */
@Composable
fun Announce(text: String?) {
    Div({
        classes("goa-visually-hidden")
        attr("aria-live", "polite")
        attr("role", "status")
    }) { text?.let { Text(it) } }
}
