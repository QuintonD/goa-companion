@file:Suppress("UnusedParameter") // interim: the game stream's table replaces this file

package dev.quinton.goa.web.play.puzzles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.map.HexLayout
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUi
import dev.quinton.goa.feature.board.play.review.ReviewAction
import dev.quinton.goa.feature.board.play.review.ReviewUi
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.web.play.map.BoardSvg
import dev.quinton.goa.web.play.map.HexMark
import dev.quinton.goa.web.play.map.MapCamera
import dev.quinton.goa.web.play.map.MapLine
import dev.quinton.goa.web.play.map.MapPiece
import dev.quinton.goa.web.play.map.MapScene
import dev.quinton.goa.web.play.map.MarkStyle
import dev.quinton.goa.web.play.map.PieceLook
import dev.quinton.goa.web.play.map.PieceTag
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/*
 * INTERIM table pieces for the puzzle screen, until the game stream's table components (hero strip
 * and turn track, card-first decision panel, card focus viewer, hero detail, review sheet) are
 * merged in; then the puzzle screen uses those and this file goes.
 */

/** What a tap on [hex] means: an option, else a piece (to read about it), else nothing (GameMapView.resolve). */
internal fun resolve(table: TableUi, hex: Hex): TableTap? {
    val occupant = table.occupancy[hex]
    if (occupant != null && occupant in table.pieceOptions) return TableTap.Piece(occupant)
    if (hex in table.spaceOptions) return TableTap.Space(hex)
    if (occupant != null) return TableTap.Piece(occupant)
    return null
}

/** The game board for a [TableUi]: options, the choice and its path, highlights, the acting hero, the hint. */
@Composable
internal fun TableMap(
    table: TableUi,
    layout: HexLayout,
    camera: MapCamera,
    onTap: (TableTap) -> Boolean,
    onFit: () -> Unit,
) {
    val scene = remember(table.map, layout, table.battleZones) {
        MapScene(
            table.map,
            layout,
            table.battleZones,
            layout.laneRegion(table.map, dev.quinton.goa.core.domain.board.BoardGeometry.of(table.map)),
        )
    }
    val pieces = table.pieces.mapNotNull { p ->
        val at = p.at ?: return@mapNotNull null
        when (p) {
            is HeroPiece -> {
                val hero = table.hero(p.heroId)
                MapPiece(p.id, at, PieceLook.Hero(hero.portrait, hero.name.take(2)), p.team)
            }

            is MinionPiece -> MapPiece(
                p.id,
                at,
                PieceLook.Minion(p.type),
                p.team,
                immune = table.view.minions.any { it.id == p.id && it.immune },
            )

            is TokenPiece -> MapPiece(p.id, at, PieceLook.Token(p.label), p.team)
        }
    }
    val chosen = when (val s = table.selected) {
        is Choice.Space -> s.hex
        is Choice.Unit -> table.at(s.unitId)
        is Choice.Token -> table.at(s.tokenId)
        else -> null
    }
    val marks = buildList {
        table.decision?.context?.highlight.orEmpty().forEach { add(HexMark(it, MarkStyle.RANGE)) }
        (table.spaceOptions.keys + table.pieceOptions.keys.mapNotNull(table::at)).forEach {
            add(HexMark(it, MarkStyle.TARGET))
        }
        table.view.acting?.let(table::at)?.let { add(HexMark(it, MarkStyle.ACTING)) }
        table.hintAt?.let { add(HexMark(it, MarkStyle.HINT)) }
        chosen?.let { add(HexMark(it, MarkStyle.SELECTION)) }
    }
    val path = (table.selected as? Choice.Space)?.let { choice ->
        val steps = table.pathTo(choice.hex)
        val from = (table.subject ?: table.decision?.seat)?.let(table::at)
        (listOfNotNull(from) + steps.filter { it != from }).takeIf { it.size > 1 }
    }
    BoardSvg(
        scene = scene,
        camera = camera,
        pieces = pieces,
        marks = marks,
        lines = listOfNotNull(path?.let { MapLine(it, stepDots = true) }),
        tags = listOfNotNull(table.view.acting?.let { PieceTag(it, "NOW") }),
        onTap = { hex, _ -> resolve(table, hex)?.let(onTap) ?: false },
        label = "The board",
        bottomTeam = table.bottomTeam,
    )
}

/** The heroes in a row: team, name, level; a tap opens the hero. */
@Composable
internal fun TableTop(table: TableUi, onHero: (String) -> Unit) {
    Div({ classes("goa-puzzle-strip") }) {
        table.view.heroes.forEach { hero ->
            Button({
                classes("goa-puzzle-strip-hero")
                attr("type", "button")
                onClick { onHero(hero.heroId) }
            }) {
                TeamMark(hero.team)
                Span { Text(table.hero(hero.heroId).name) }
                Span({ classes("c-variant") }) { Text("L${hero.level}") }
            }
        }
    }
}

/** The question: what is asked, and its answers as buttons; a tapped space or piece waits for Confirm. */
@Composable
internal fun TableQuestion(table: TableUi, busy: Boolean, onSelect: (Choice) -> Unit, onAnswer: (Choice) -> Unit) {
    val decision = table.decision ?: return
    Div({ classes("goa-puzzle-question") }) {
        P({ classes("t-title-medium") }) { Text(decision.prompt) }
        table.sourceCard?.let { P({ classes("t-body-small", "c-variant") }) { Text(it.name) } }
        Div({ classes("goa-button-row", "start", "wrap") }) {
            (table.cardOptions + table.buttonOptions).forEach { choice ->
                GoaButton(table.label(choice), { onAnswer(choice) }, ButtonKind.TONAL, enabled = table.asking && !busy)
            }
        }
        val picked = table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token }
        if (picked != null) {
            GoaButton("Confirm: ${table.label(picked)}", {
                onAnswer(picked)
            }, enabled = table.asking && !busy, block = true)
        } else if (table.spaceOptions.isNotEmpty() || table.pieceOptions.isNotEmpty()) {
            P({ classes("t-body-small", "c-variant") }) { Text("Tap a highlighted space or piece on the board.") }
        }
    }
}

/** Confirm in the peek bar for a tapped space or piece. */
@Composable
internal fun PeekConfirm(puzzle: PuzzleUi, onAnswer: (Choice) -> Unit) {
    val table = puzzle.table
    val picked = table.selected?.takeIf { it is Choice.Space || it is Choice.Unit || it is Choice.Token } ?: return
    if (!table.asking) return
    GoaButton("Confirm", { onAnswer(picked) }, enabled = !puzzle.busy)
}

/** The turns so far, newest first, with the lines not to miss. */
@Composable
internal fun InterimReview(puzzle: PuzzleUi, review: ReviewUi, onAction: (ReviewAction) -> Unit) {
    Ul({ classes("goa-puzzle-log") }) {
        review.turns.asReversed().forEach { turn ->
            Li({ classes("heading", "t-overline", "c-primary") }) { Text("Round ${turn.round} · Turn ${turn.turn}") }
            turn.lines.forEach { line -> Li({ classes("t-body-medium") }) { Text(line.text) } }
        }
    }
    if (review.rewind != null) GoaButton("Back to now", { onAction(ReviewAction.CloseRewind) }, ButtonKind.TEXT)
}

/** A hero's open information: level, coins, hand (open in a puzzle), cards on the table. */
@Composable
internal fun InterimHeroDetail(table: TableUi, heroId: String) {
    val hero = table.heroView(heroId) ?: return
    P({ classes("t-body-medium") }) {
        Text("${BoardText.team(hero.team)} · level ${hero.level} · ${BoardText.count(hero.coins, "coin")}")
    }
    hero.hand?.let { hand ->
        P({ classes("t-overline", "c-primary") }) { Text("Hand") }
        Ul { hand.forEach { id -> Li({ classes("t-body-medium") }) { Text(table.card(id)?.name ?: id) } } }
    }
}
