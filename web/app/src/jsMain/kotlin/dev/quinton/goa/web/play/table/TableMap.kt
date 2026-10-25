package dev.quinton.goa.web.play.table

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.map.HexLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.web.play.map.BoardSvg
import dev.quinton.goa.web.play.map.HexMark
import dev.quinton.goa.web.play.map.MapCamera
import dev.quinton.goa.web.play.map.MapLine
import dev.quinton.goa.web.play.map.MapNode
import dev.quinton.goa.web.play.map.MapPiece
import dev.quinton.goa.web.play.map.MapScene
import dev.quinton.goa.web.play.map.MarkStyle
import dev.quinton.goa.web.play.map.PieceLook
import dev.quinton.goa.web.play.map.PieceTag

/** The map scene for [table], turned for the screen ([landscape]) with its bottom team below. */
@Composable
fun rememberTableScene(table: TableUi, landscape: Boolean): MapScene {
    val orientation = if (landscape) MapOrientation.LANDSCAPE else MapOrientation.PORTRAIT
    val layout =
        remember(table.map, orientation, table.bottomTeam) { HexLayout(table.map, orientation, table.bottomTeam) }
    val zones = table.battleZones
    return remember(layout, zones) { MapScene(table.map, layout, zones, layout.laneRegion(table.map, table.geometry)) }
}

/**
 * The game board (the phone's GameMapView): the map with the pieces of [table]; when the person is
 * asked for a space or a piece every option is a dashed teal space, the chosen one outlined with the
 * engine's path drawn to it, the question's other highlights (a card's range) violet, the acting hero
 * outlined in bronze with "NOW" over it, immune minions ringed. A tap goes to [onTap] as the option
 * it is, else the piece (to read about it); VoiceOver gets one button per option and per piece.
 */
@Composable
fun TableMap(table: TableUi, scene: MapScene, camera: MapCamera, onTap: (TableTap) -> Unit) {
    val view = table.view
    val immune = view.minions.filter { it.immune }.map { it.id }.toSet()
    val pieces = table.pieces.mapNotNull { piece ->
        val at = piece.at ?: return@mapNotNull null
        when (piece) {
            is HeroPiece -> {
                val hero = table.hero(piece.heroId)
                MapPiece(
                    piece.id,
                    at,
                    PieceLook.Hero(hero.portrait, hero.name.take(2)),
                    piece.team,
                    raised =
                        piece.id == view.acting,
                )
            }

            is MinionPiece -> MapPiece(
                piece.id,
                at,
                PieceLook.Minion(piece.type),
                piece.team,
                immune =
                    piece.id in immune,
            )

            is TokenPiece -> MapPiece(piece.id, at, PieceLook.Token(piece.label), piece.team)
        }
    }
    val marks = buildList {
        table.decision?.context?.highlight.orEmpty().forEach { add(HexMark(it, MarkStyle.RANGE)) }
        if (table.asking) {
            table.spaceOptions.keys.forEach { add(HexMark(it, MarkStyle.TARGET)) }
            table.pieceOptions.keys.mapNotNull(table::at).forEach { add(HexMark(it, MarkStyle.TARGET)) }
        }
        view.acting?.let(table::at)?.let { add(HexMark(it, MarkStyle.ACTING)) }
        table.hintAt?.let { add(HexMark(it, MarkStyle.HINT)) }
        chosenAt(table)?.let { add(HexMark(it, MarkStyle.SELECTION)) }
    }
    val lines = listOfNotNull(pathLine(table))
    val tags = listOfNotNull(view.acting?.takeIf { table.at(it) != null }?.let { PieceTag(it, "NOW") })
    BoardSvg(
        scene = scene,
        camera = camera,
        pieces = pieces,
        marks = marks,
        lines = lines,
        tags = tags,
        bottomTeam = table.bottomTeam,
        label = "The board, ${table.map.name}",
        onTap = { hex, _ ->
            val tap = resolveTableTap(table, hex)
            if (tap != null) onTap(tap)
            tap != null
        },
        nodes = mapNodes(table, onTap),
    )
}

/** What a tap on [hex] means: an option, else a piece (to read about it), else nothing (the phone's rule). */
fun resolveTableTap(table: TableUi, hex: Hex): TableTap? {
    val occupant = table.occupancy[hex]
    if (occupant != null && occupant in table.pieceOptions) return TableTap.Piece(occupant)
    if (hex in table.spaceOptions) return TableTap.Space(hex)
    if (occupant != null) return TableTap.Piece(occupant)
    return null
}

private fun chosenAt(table: TableUi): Hex? = when (val s = table.selected) {
    is Choice.Space -> s.hex
    is Choice.Unit -> table.at(s.unitId)
    is Choice.Token -> table.at(s.tokenId)
    else -> null
}

/** The path the engine will use to the chosen space, from the unit that moves. */
private fun pathLine(table: TableUi): MapLine? {
    val choice = table.selected as? Choice.Space ?: return null
    val steps = table.pathTo(choice.hex)
    val from = (table.subject ?: table.decision?.seat)?.let(table::at)
    val points = listOfNotNull(from) + steps.filter { it != from }
    return if (points.size > 1) MapLine(points, stepDots = true) else null
}

/** VoiceOver's way into the map: each option (Choose), then each piece (Read about it). */
private fun mapNodes(table: TableUi, onTap: (TableTap) -> Unit): List<MapNode> = buildList {
    if (table.asking && table.decision?.kind != DecisionKind.PLAY_CARD) {
        table.spaceOptions.values.forEach { choice ->
            add(
                MapNode("s${choice.hex}", "Choose ${table.label(choice)}", table.selected == choice) {
                    onTap(TableTap.Space(choice.hex))
                },
            )
        }
        table.pieceOptions.forEach { (id, choice) ->
            add(
                MapNode("p$id", "Choose ${table.label(choice)}", table.selected == choice) {
                    onTap(TableTap.Piece(id))
                },
            )
        }
    }
    table.view.heroes.filter { it.at != null }.forEach { hero ->
        add(
            MapNode("h${hero.heroId}", "${table.hero(hero.heroId).name}, ${BoardText.team(hero.team)}: read about it") {
                onTap(TableTap.Piece(hero.heroId))
            },
        )
    }
}
