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

        /** Strength 10's answers with a choice in [theShippedStrengthTenChoosesAlike] (card plays and the rest). */
        const val SHIPPED_TEN_ANSWERS = 30

        const val SHIPPED_TEN_HASH = "b3bfc3bb986a33b0d132c9cf7a7ce83a351179db548397206336920bdca94141"
    }

    @Test
    fun theShippedCatalogDecodesStrictly() {
        val transcript = buildString {
            appendLine("catalog ${catalog.dataVersion} ${catalog.printing} ${catalog.products.size}")
            catalog.heroes.forEach { appendLine(canon(it)) }
            maps.forEach { appendLine(canon(it)) }
        }
        assertTrue(catalog.heroes.size >= 30)
        assertTranscript("72d4f6e64083d24d4cafac16b347879be3eef531b230a0273978f3d3b27324ef", transcript, "catalog")
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
        // Re-pinned for ADR-054: Inscribe the Runes' question names the picks so far; the course is unchanged.
        assertTranscript("7e56373ee41abd144cdb7f287e0427a6c92fdacc000a1b1774613d1a582ac9cc", transcript, "random game")
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
        // Re-pinned for ADR-085: the seated player is the heuristic book, the same on the JVM and in JS (last:
        // both review rounds, REVIEW-core.md and REVIEW-heroes.md).
        assertTranscript("d7017562b5c647433c9f309e2775589d830d5b87b439b853de2f75e2356dcb3b", transcript, "AI game")
    }

    /**
     * Hard and Expert search (strengths 8 and 10 since ADR-039: the tuned evaluation, the opponent
     * soft-max, greedy playouts): determinized samples, playouts and the soft-max pick (exp). The
     * hashes were re-pinned when v0.7's AI merged in (2026-10-26) and again for v0.9 (2026-10-28: strength 10
     * scores with the learned net, its float arithmetic rounded per operation, f32) and v0.10 (ADR-049:
     * the net with its per-hero and per-card parts) and v0.14 (ADR-060: the mean of three seed nets, in the
     * one-ply looks too); the JVM and JS agreed.
     */
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
            Team.RED to AiPlayers.searchDeterministic(AiLevel.HARD.strength, 3),
            Team.BLUE to AiPlayers.searchDeterministic(AiLevel.EXPERT.strength, 5),
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
        assertTranscript("da6c3fe62c21e8d0b6168f2184449f96d9c4827e896acdf7469168b4a646e39c", transcript, "searching AI")
    }

    /**
     * The strength 10 the apps seat (the search over the book, ADR-085 addendum; wave 4: one player per team
     * for the game, so its memo carries between answers, as the apps keep it) against strength 9 (the book
     * alone): determinized samples merged by state hash, book playouts, enemy replies drawn from the softmax
     * opponent model, the post-reveal tree and the six learned nets at the leaves (f32). The phone and the
     * browser must play it alike, answer for answer.
     */
    @Test
    fun theShippedStrengthTenChoosesAlike() {
        val setup = GameSetup(
            mapId = "forgotten-island",
            seats = listOf(
                SeatSetup("wasp", Team.RED, Controller.Ai(AiLevel.EXPERT, strength = 9)),
                SeatSetup("xargatha", Team.RED, Controller.Ai(AiLevel.EXPERT, strength = 9)),
                SeatSetup("arien", Team.BLUE, Controller.Ai(AiLevel.EXPERT)),
                SeatSetup("brogan", Team.BLUE, Controller.Ai(AiLevel.EXPERT)),
            ),
            length = GameLength.QUICK,
            tieBreaker = Team.RED,
        )
        val players = mapOf(
            Team.RED to AiPlayers.deterministic(9, 13),
            Team.BLUE to AiPlayers.deterministic(10, 17),
        )
        val s = engine.newGame(setup) as EngineSession
        val transcript = buildString {
            var searched = 0
            var n = 0
            while (searched < SHIPPED_TEN_ANSWERS && n < 400) {
                val d = s.pending ?: break
                val c = players.getValue(d.team).choose(s, d)
                appendLine(d.line(d.options.indexOf(c)))
                if (d.team == Team.BLUE && d.options.size > 1) searched++
                check(s.answer(d.id, c) is AnswerResult.Accepted)
                n++
            }
            appendLine("round ${s.state.round} turn ${s.state.turn} searched $searched")
        }
        assertTranscript(SHIPPED_TEN_HASH, transcript, "shipped strength 10")
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
