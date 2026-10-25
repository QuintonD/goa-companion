package dev.quinton.goa.web.play.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardSetup
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.map.HexLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.newPieceId
import dev.quinton.goa.feature.board.ui.PanelPeek
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.play.map.BoardSvg
import dev.quinton.goa.web.play.map.MapCamera
import dev.quinton.goa.web.play.map.MapPiece
import dev.quinton.goa.web.play.map.MapScene
import dev.quinton.goa.web.play.map.PieceLook
import dev.quinton.goa.web.play.ui.PlayScaffold
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.coroutines.flow.first

/** TEMPORARY: the map component on an opening, until the board sandbox screens land (W4). */
@Composable
fun BoardPreviewScreen() {
    val data = LocalServices.current.data
    val loaded = rememberLoad(Unit) {
        val catalog = data.content.catalog.catalog.first()
        val maps = data.play.boardMaps.maps.first()
        catalog to maps
    }
    var shown by remember { mutableStateOf(true) }
    LoadContent(loaded, "Couldn't load the board") { (catalog, maps) ->
        val map = maps.first()
        val heroes = remember(catalog) { catalog.boardHeroes() }
        val lineup = BoardLineup(listOf("brogan", "arien"), listOf("wasp", "tigerclaw"))
        val state = remember { BoardSetup.opening(BoardGeometry.of(map), lineup, ::newPieceId) }
        val layout = remember(map) { HexLayout(map, MapOrientation.PORTRAIT, Team.RED) }
        val geometry = BoardGeometry.of(map)
        val scene = remember(layout) {
            MapScene(map, layout, map.lanes.map { it.startingBattleZone }, layout.laneRegion(map, geometry))
        }
        val camera = remember { MapCamera() }
        PlayScaffold(
            title = "Board preview",
            actions = {},
            panelShown = shown,
            onPanelShown = { shown = it },
            peek = PanelPeek("Tap a piece", label = "Arien", team = Team.RED, attention = true),
            map = {
                BoardSvg(
                    scene = scene,
                    camera = camera,
                    pieces = state.pieces.mapNotNull { p ->
                        val at = p.at ?: return@mapNotNull null
                        when (p) {
                            is HeroPiece -> MapPiece(
                                p.id,
                                at,
                                PieceLook.Hero(heroes.hero(p.heroId).portrait, heroes.hero(p.heroId).name.take(2)),
                                p.team,
                            )

                            is MinionPiece -> MapPiece(p.id, at, PieceLook.Minion(p.type), p.team)

                            is TokenPiece -> MapPiece(p.id, at, PieceLook.Token(p.label), p.team)
                        }
                    },
                    marks = emptyList(),
                    onTap = { _, _ -> false },
                    label = "The board",
                )
            },
            panel = { org.jetbrains.compose.web.dom.Text("The selection panel") },
        )
    }
}
