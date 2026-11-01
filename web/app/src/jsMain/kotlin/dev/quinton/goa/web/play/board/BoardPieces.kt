package dev.quinton.goa.web.play.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.BoardStyle
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.web.play.map.Glyph
import dev.quinton.goa.web.play.map.MapPiece
import dev.quinton.goa.web.play.map.PieceLook
import dev.quinton.goa.web.play.map.miniatureArt
import dev.quinton.goa.web.play.map.rememberBoardStyle
import dev.quinton.goa.web.play.map.spawnArt
import kotlin.js.Date
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** A hero's initials, shown while the portrait loads (and if it can't). */
fun initials(name: String): String = name.split(' ', '-').filter { it.isNotEmpty() }.take(2)
    .joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }

/** The pieces on the map (the tray's are off it), the selected one drawn above its neighbours. */
fun mapPieces(pieces: List<BoardPiece>, heroes: Map<String, BoardHero>, selectedId: String?): List<MapPiece> =
    pieces.mapNotNull { piece ->
        val at = piece.at ?: return@mapNotNull null
        when (piece) {
            is HeroPiece -> heroes.hero(piece.heroId).let { hero ->
                MapPiece(
                    piece.id,
                    at,
                    PieceLook.Hero(hero.portrait, initials(hero.name)),
                    piece.team,
                    raised =
                        piece.id == selectedId,
                )
            }

            is MinionPiece -> MapPiece(
                piece.id,
                at,
                PieceLook.Minion(piece.type),
                piece.team,
                raised =
                    piece.id == selectedId,
            )

            is TokenPiece -> MapPiece(
                piece.id,
                at,
                PieceLook.Token(piece.label),
                piece.team,
                raised =
                    piece.id == selectedId,
            )
        }
    }

/**
 * A hero's official portrait in a round frame, with the team's official gear or star on a dark badge when
 * it has a [team], as on the board; its initials until (or unless) it loads. Decorative.
 */
@Composable
fun HeroPortrait(hero: BoardHero, team: Team? = null, size: String = "medium") {
    var failed by remember(hero.portrait) { mutableStateOf(false) }
    Span({
        classes("goa-board-portrait-wrap", size)
        attr("aria-hidden", "true")
    }) {
        Span({
            classes("goa-board-portrait", size)
            team?.let { classes(teamClass(it)) }
        }) {
            Span({ classes("goa-board-initials") }) { Text(initials(hero.name)) }
            val portrait = hero.portrait
            if (portrait != null && !failed) {
                Img(portrait, "") {
                    attr("loading", "lazy")
                    attr("decoding", "async")
                    addEventListener("error") { failed = true }
                }
            }
        }
        if (team != null && size != "fill") {
            Span({ classes("goa-board-team-badge") }) { Img(spawnArt(SpawnPoint(team, SpawnKind.HERO)), "") }
        }
    }
}

/** A piece as a small token for lists and the tray: a portrait, a minion disc with its glyph, a token's label. */
@Composable
fun PieceAvatar(piece: BoardPiece, heroes: Map<String, BoardHero>) {
    when (piece) {
        is HeroPiece -> HeroPortrait(heroes.hero(piece.heroId), piece.team, "small")

        is MinionPiece -> MinionAvatar(piece.type, piece.team)

        is TokenPiece -> Span({
            classes("goa-board-token-avatar")
            piece.team?.let { classes(teamClass(it)) }
            attr("aria-hidden", "true")
        }) { Text(piece.label.take(3)) }
    }
}

/**
 * A minion: by the board style, its miniature with a badge of the type's glyph, or its team's disc with
 * the glyph (and the team's shape, never colour alone).
 */
@Composable
fun MinionAvatar(type: MinionType, team: Team) {
    if (rememberBoardStyle() == BoardStyle.MINIATURES) {
        Span({
            classes("goa-board-figure", teamClass(team))
            attr("aria-hidden", "true")
        }) {
            Img(miniatureArt(type, team), "") {
                attr("loading", "lazy")
                attr("decoding", "async")
            }
            Span({
                classes("goa-board-figure-badge")
                ref { el ->
                    el.innerHTML = glyphSvg(type)
                    onDispose { }
                }
            })
        }
        return
    }
    Span({
        classes("goa-board-minion", teamClass(team))
        attr("aria-hidden", "true")
        ref { el ->
            el.innerHTML = glyphSvg(type)
            onDispose { }
        }
    })
}

private fun glyphSvg(type: MinionType): String =
    "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path fill=\"currentColor\" d=\"${Glyph.of(type).path}\"/></svg>"

fun teamClass(team: Team): String = if (team == Team.RED) "red" else "blue"

/** A time of day the browser's way ("14:05", "2:05 PM"). */
fun browserTime(epochMillis: Long): String =
    Date(
        epochMillis.toDouble(),
    ).asDynamic().toLocaleTimeString(js("[]"), js("({hour: 'numeric', minute: '2-digit'})")) as String

/** "Changed today, 14:05", "Changed yesterday, 9:12", "Changed 12 Oct 2026" (core:play BoardText). */
fun changedText(epochMillis: Long, now: Long): String =
    dev.quinton.goa.feature.board.BoardText.changed(epochMillis, now, TimeZone.currentSystemDefault()) {
        browserTime(epochMillis)
    }
