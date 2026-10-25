package dev.quinton.goa.core.game.parity

import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.AnswerKey
import dev.quinton.goa.core.game.puzzles.EnginePuzzleBook
import dev.quinton.goa.core.game.puzzles.EnginePuzzleSession
import dev.quinton.goa.core.game.puzzles.PuzzleKeyFiles
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The game the browser runs is the phone's game (ADR-043): the shipped catalog decodes, a seeded
 * game and a seeded AI game take the same course, and the puzzles load their answer keys and are
 * solved by their solutions. Transcripts are checked against the JVM's hashes.
 */
class GameParityTest {
    private companion object {
        /** Read with the loader both apps use (strict: an unknown key fails). */
        val catalog: Catalog by lazy { CatalogFiles.load { readRepoFile("app/src/main/assets/$it") } }

        val maps: List<BoardMap> by lazy {
            CatalogFiles.parseMapIndex(readRepoFile("app/src/main/assets/${CatalogFiles.MAPS_INDEX_PATH}")).map {
                CatalogFiles.parseMap(it, readRepoFile("app/src/main/assets/${CatalogFiles.mapPath(it)}"))
            }
        }

        val engine: GameEngine by lazy {
            GameEngine(catalog, maps, MatchRules.parse(catalogAsset("match-setup.json")))
        }
    }

    @Test
    fun theShippedCatalogDecodesStrictly() {
        val transcript = buildString {
            appendLine("catalog ${catalog.dataVersion} ${catalog.printing} ${catalog.products.size}")
            catalog.heroes.forEach { appendLine(canon(it)) }
            maps.forEach { appendLine(canon(it)) }
        }
        assertTrue(catalog.heroes.size >= 30)
        assertTranscript("67e964d01cf984bad0e83a992bad79317b29f5a7a936123d527168bc14dea33f", transcript, "catalog")
    }

    private fun Decision.line(answer: Int) = "$id $seat ${kind.name} ${options.size}>$answer $prompt"

    @Test
    fun aSeededRandomGameTakesTheSameCourse() {
        val heroes = engine.playableHeroes.sorted()
        val rng = Random(424242)
        val transcript = buildString {
            repeat(3) { game ->
                val four = heroes.shuffled(rng).take(4)
                val setup = GameSetup(
                    mapId = engine.playableMaps[game % engine.playableMaps.size],
                    seats = four.mapIndexed { i, h ->
                        SeatSetup(
                            h,
                            if (i <
                                2
                            ) {
                                Team.RED
                            } else {
                                Team.BLUE
                            },
                            Controller.Human,
                        )
                    },
                    length = GameLength.QUICK,
                    tieBreaker = Team.entries[game % 2],
                )
                val s = engine.newGame(setup)
                var n = 0
                while (n < 1_500) {
                    val d = s.pending ?: break
                    val i = rng.nextInt(d.options.size)
                    appendLine(d.line(i))
                    check(s.answer(d.id, d.options[i]) is AnswerResult.Accepted)
                    n++
                }
                val view = s.view(null)
                appendLine("game $game: $four round ${view.round} phase ${view.phase} winner ${view.winner}")
            }
        }
        assertTranscript("c6d83cb1604aaeeba6a6e45ec7b87873de3824288ecd4f4942035f2ec7f72c6d", transcript, "random game")
    }

    @Test
    fun aSeededAiGameTakesTheSameCourse() {
        val setup = GameSetup(
            mapId = "forgotten-island",
            seats = listOf(
                SeatSetup("wasp", Team.RED, Controller.Ai(AiLevel.EASY)),
                SeatSetup("xargatha", Team.RED, Controller.Ai(AiLevel.EASY)),
                SeatSetup("arien", Team.BLUE, Controller.Ai(AiLevel.NORMAL)),
                SeatSetup("brogan", Team.BLUE, Controller.Ai(AiLevel.NORMAL)),
            ),
            length = GameLength.QUICK,
            tieBreaker = Team.RED,
        )
        val players = mapOf(
            Team.RED to AiPlayers.deterministic(AiLevel.EASY, 7),
            Team.BLUE to AiPlayers.deterministic(AiLevel.NORMAL, 11),
        )
        val s = engine.newGame(setup) as EngineSession
        val transcript = buildString {
            var n = 0
            while (n < 600) {
                val d = s.pending ?: break
                val c = players.getValue(d.team).choose(s, d)
                appendLine(d.line(d.options.indexOf(c)))
                check(s.answer(d.id, c) is AnswerResult.Accepted)
                n++
            }
            appendLine("round ${s.state.round} winner ${s.state.winner}")
        }
        assertTranscript("b04d3808e164924242baa68fd13312052be889f69f1f9afafd816276fedb577f", transcript, "AI game")
    }

    /** Hard and Expert search: determinized samples, playouts and the soft-max pick (exp). */
    @Test
    fun theSearchingLevelsChooseAlike() {
        val setup = GameSetup(
            mapId = "forgotten-island",
            seats = listOf(
                SeatSetup("arien", Team.RED, Controller.Ai(AiLevel.HARD)),
                SeatSetup("brogan", Team.RED, Controller.Ai(AiLevel.HARD)),
                SeatSetup("wasp", Team.BLUE, Controller.Ai(AiLevel.EXPERT)),
                SeatSetup("xargatha", Team.BLUE, Controller.Ai(AiLevel.EXPERT)),
            ),
            length = GameLength.QUICK,
            tieBreaker = Team.BLUE,
        )
        val players = mapOf(
            Team.RED to AiPlayers.deterministic(AiLevel.HARD, 3),
            Team.BLUE to AiPlayers.deterministic(AiLevel.EXPERT, 5),
        )
        val s = engine.newGame(setup) as EngineSession
        val transcript = buildString {
            var n = 0
            while (n < 40) {
                val d = s.pending ?: break
                val c = players.getValue(d.team).choose(s, d)
                appendLine(d.line(d.options.indexOf(c)))
                check(s.answer(d.id, c) is AnswerResult.Accepted)
                n++
            }
        }
        assertTranscript("9e7e6755b6d8cdad3da4c9616b8315ca0a87a464675601748bb00c7bbf235c13", transcript, "searching AI")
    }

    @Test
    fun everyAnswerKeyLoadsAndFitsItsPuzzle() {
        assertEquals(
            Puzzles.all.map { it.info.id }.sorted(),
            PuzzleKeyFiles.names.filter {
                it in
                    Puzzles.all.map { p -> p.info.id }
            },
        )
        for (id in PuzzleKeyFiles.names) {
            val text = assertNotNull(PuzzleKeyFiles.text(id))
            val key = assertNotNull(AnswerKey.load(id))
            assertEquals(text.trimEnd(), key.format().trimEnd(), "$id: the key reads back as written")
        }
        // The fingerprints hash engine states: a key only applies when both targets hash alike.
        val book = EnginePuzzleBook(engine)
        for (info in book.puzzles) {
            assertNotNull(book.prepared(info.id).key, "${info.id}: the stored key doesn't fit the position here")
        }
    }

    @Test
    fun onePuzzleOfEachDifficultyIsSolvedByItsSolution() {
        val book = EnginePuzzleBook(engine)
        val transcript = buildString {
            for (difficulty in 1..3) {
                val puzzle = Puzzles.all.first {
                    it.info.difficulty == difficulty &&
                        it.info.id in book.puzzles.map { p -> p.id }
                }
                val session = book.start(puzzle.info.id) as EnginePuzzleSession
                appendLine("${puzzle.info.id} ${puzzle.info.title}")
                for (move in puzzle.solution) {
                    val d = session.game.pending ?: break
                    val c = checkNotNull(move.resolve(d, session.position.session)) { "${puzzle.info.id}: $move" }
                    appendLine(d.line(d.options.indexOf(c)))
                    check(session.answer(d.id, c) is AnswerResult.Accepted)
                }
                appendLine("status ${session.status}")
                assertEquals(PuzzleStatus.SOLVED, session.status, puzzle.info.id)
                // Off the key: the solver's own best reply.
                val again = book.start(puzzle.info.id)
                appendLine("best ${canon(again.bestChoice())}")
            }
        }
        assertTranscript("215bbbc584f42fa28b33039edafc23e3ba78cec57ccf247b8eb5b7ea1f582fbc", transcript, "puzzles")
    }
}
