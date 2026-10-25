package dev.quinton.goa.feature.board.board

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.boardHeroes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sandbox's rules run the same on the phone (JVM) and in the browser (Node): the shipped map and
 * catalog, a line-up at its opening, then the moves a player makes.
 */
class BoardSandboxTest {
    private fun asset(path: String) = readRepoFile("app/src/main/assets/$path")

    private val catalog = CatalogFiles.load(::asset)
    private val heroes = catalog.boardHeroes()
    private val maps: List<BoardMap> =
        CatalogFiles.parseMapIndex(asset(CatalogFiles.MAPS_INDEX_PATH)).map {
            CatalogFiles.parseMap(it, asset(CatalogFiles.mapPath(it)))
        }
    private var ids = 0
    private val saved = mutableListOf<SavedBoard>()

    private fun board(): SavedBoard = assertNotNull(
        BoardSandbox.newBoard(
            red = listOf("brogan", "arien"),
            blue = listOf("wasp", "tigerclaw", "arien"),
            drafted = null,
            mapId = null,
            name = null,
            maps = maps,
            heroes = heroes,
            id = "b1",
            now = 1L,
            newPieceId = { "p${++ids}" },
        ),
    )

    private fun sandbox(board: SavedBoard = board()) = BoardSandbox(
        BoardUiState.Ready(board = board, map = maps.first { it.id == board.state.mapId }, heroes = heroes),
        newPieceId = { "n${++ids}" },
        now = { 2L },
        listener = object : BoardSandbox.Listener {
            override fun changed(board: SavedBoard) {
                saved += board
            }
        },
    )

    @Test
    fun aNewBoardIsTheLineUpAtTheOpeningNamedAfterIt() {
        val board = board()
        assertEquals("Brogan, Arien vs Wasp, Tigerclaw", board.name)
        assertEquals(listOf("brogan", "arien"), board.lineup.red)
        assertEquals(listOf("wasp", "tigerclaw"), board.lineup.blue, "a hero is on a board once")
        assertTrue(board.state.pieces.filterIsInstance<HeroPiece>().all { it.at != null })
    }

    @Test
    fun anUntouchedTwinIsReopenedAndAMovedOneIsNot() {
        val fresh = board()
        val twin = fresh.copy(
            id = "older",
            state = fresh.state.copy(
                pieces = fresh.state.pieces.map {
                    it.moveTo(it.at)
                },
            ),
        )
        assertEquals("older", BoardSandbox.twinOf(listOf(twin), fresh)?.id)
        val s = sandbox(twin)
        val arien = s.state.pieces.first { it is HeroPiece && it.heroId == "arien" }
        s.onAction(BoardAction.Select(arien.id))
        val to = emptyNeighbour(s, arien.at!!)
        s.tap(MapTap.Space(to))
        assertNull(BoardSandbox.twinOf(listOf(s.state.board), fresh))
    }

    @Test
    fun selectMoveUndoRedoAndEveryChangeIsSaved() {
        val s = sandbox()
        val arien = s.state.pieces.first { it is HeroPiece && it.heroId == "arien" }
        assertTrue(s.tap(MapTap.Piece(arien.id)))
        assertEquals(arien.id, s.state.selectedId)
        val to = emptyNeighbour(s, arien.at!!)
        assertTrue(s.tap(MapTap.Space(to)))
        assertEquals(to, s.state.selected?.at)
        assertTrue(s.state.announcement!!.text.startsWith("Arien moved 1 space, to "))
        assertEquals(1, saved.size)
        assertTrue(s.state.canUndo)
        s.onAction(BoardAction.Undo)
        assertEquals(arien.at, s.state.selected?.at)
        assertEquals("Undone.", s.state.announcement?.text)
        s.onAction(BoardAction.Redo)
        assertEquals(to, s.state.selected?.at)
        assertEquals(3, saved.size)
    }

    @Test
    fun droppingOnAPieceSwapsThem() {
        val s = sandbox()
        val brogan = s.state.pieces.first { it is HeroPiece && it.heroId == "brogan" }
        val arien = s.state.pieces.first { it is HeroPiece && it.heroId == "arien" }
        s.onAction(BoardAction.Drop(brogan.id, arien.at!!))
        assertEquals(arien.at, s.state.pieces.first { it.id == brogan.id }.at)
        assertEquals(brogan.at, s.state.pieces.first { it.id == arien.id }.at)
        assertEquals("Brogan and Arien swapped places.", s.state.announcement?.text)
    }

    @Test
    fun aHeroIsAddedOnceAndOnlyOnAnEmptySpace() {
        val s = sandbox()
        val arien = s.state.pieces.first { it is HeroPiece && it.heroId == "arien" }
        s.onAction(BoardAction.Add(NewPiece.Hero("arien", Team.BLUE)))
        s.tap(MapTap.Space(emptyNeighbour(s, arien.at!!)))
        assertEquals("Arien is already on this board.", s.state.hint?.text)
        s.onAction(BoardAction.Add(NewPiece.Hero("snorri", Team.RED)))
        s.tap(MapTap.Space(arien.at!!))
        assertEquals("That space is taken: one piece per space.", s.state.hint?.text)
        s.tap(MapTap.Space(emptyNeighbour(s, arien.at!!)))
        assertTrue(s.state.pieces.any { it is HeroPiece && it.heroId == "snorri" })
        assertEquals(BoardTool.Move, s.state.tool)
    }

    @Test
    fun aChosenCardShowsWhereTheHeroReaches() {
        val s = sandbox()
        val brogan = s.state.pieces.first { it is HeroPiece && it.heroId == "brogan" }
        s.onAction(BoardAction.Select(brogan.id))
        val card = heroes.getValue("brogan").cards.first { it.values.movement != null }
        s.onAction(BoardAction.ChooseCard(card.id))
        val overlay = assertNotNull(s.state.overlay)
        assertEquals(card.values.movement, overlay.movementValue)
        assertTrue(overlay.movement.isNotEmpty())
        assertTrue(overlay.movement.values.all { it <= card.values.movement!! })
        s.onAction(BoardAction.MovementBonus(1))
        assertEquals(card.values.movement!! + 1, s.state.overlay?.movementValue)
    }

    @Test
    fun pushingTheLaneMovesTheBattleZoneAndUndoBringsItBack() {
        val s = sandbox()
        val lane = s.state.map.lanes.first()
        val before = s.state.battleZones
        s.onAction(BoardAction.Push(lane.id, Team.RED))
        assertNotEquals(before, s.state.battleZones)
        assertTrue(s.state.announcement!!.text.startsWith("The Battle Zone is now "))
        s.onAction(BoardAction.Undo)
        assertEquals(before, s.state.battleZones)
    }

    @Test
    fun measureReadsDistanceAndStraightLine() {
        val s = sandbox()
        val wasp = s.state.pieces.first { it is HeroPiece && it.heroId == "wasp" }
        s.onAction(BoardAction.Select(wasp.id))
        s.onAction(BoardAction.StartMeasure)
        val target = HexGrid.within(wasp.at!!, 3).first {
            HexGrid.distance(it, wasp.at!!) == 3 &&
                s.state.geometry.isOnBoard(it)
        }
        s.tap(MapTap.Space(target))
        assertEquals(3, s.state.measure?.distance)
        s.onAction(BoardAction.Deselect)
        assertFalse(s.state.tool == BoardTool.Measure)
    }

    private fun emptyNeighbour(s: BoardSandbox, from: dev.quinton.goa.core.model.board.Hex) = HexGrid.neighbors(
        from,
    ).first {
        BoardRules.isEmpty(BoardGeometry.of(s.state.map), BoardRules.occupancy(s.state.board.state), it)
    }
}
