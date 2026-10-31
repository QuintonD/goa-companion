package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.ai.AiCoach
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.game.GameOverUi
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.ReportUi
import dev.quinton.goa.feature.board.play.game.SuggestUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.logLines
import dev.quinton.goa.feature.board.play.table.turnLog
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A 3v3 on the real engine with the real coach, drawn at each kind of question and at the report
 * (ADR-051): the person's seat follows the top suggestion. Opt-in: `AI_COACH_SHOTS=<dir>` writes the screens,
 * for reading them as a player would; no baselines (the text comes from the search).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CoachRealGameTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun table(game: GameSession, seat: String) = TableUi(
        map = bundledMaps.first { it.id == game.setup.mapId },
        view = game.view(seat),
        heroes = bundledCatalog.boardHeroes(),
        cards = bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id },
        mine = setOf(seat),
        decision = game.pending,
        asking = game.pending?.seat == seat,
        turnLog = turnLog(game.events),
    )

    private fun play(game: GameSession, seat: String) = PlayUi(
        table = table(game, seat),
        status = TurnStatus.YourTurn,
        recent = emptyList(),
        log = logLines(game.events),
        coach = true,
        pace = AiPace.NORMAL,
        takeBacks = 0,
        canTakeBack = false,
    )

    @Suppress("LoopWithTooManyJumpStatements")
    @Test
    fun `the sheet and the report on a real game`() {
        val dir = System.getenv("AI_COACH_SHOTS")
        assumeTrue("Opt-in: AI_COACH_SHOTS", dir != null)
        val engine = GameEngine(bundledCatalog, bundledMaps)
        val red = listOf("wasp", "tigerclaw", "gydion")
        val blue = listOf("brogan", "arien", "razzle")
        val setup = GameSetup(
            mapId = engine.playableMaps.first(),
            seats = red.mapIndexed { i, h ->
                SeatSetup(h, Team.RED, if (i == 0) Controller.Human else Controller.Ai(AiLevel.HARD))
            } + blue.map { SeatSetup(it, Team.BLUE, Controller.Ai(AiLevel.HARD)) },
            tieBreaker = Team.RED,
        )
        val game = engine.newGame(setup)
        val seat = red.first()
        val coach = AiCoach(engine)
        val computer = AiPlayers.deterministic(6, 5L)
        var state by mutableStateOf(play(game, seat))
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                GameScreen(GameUiState.Playing(state), {}, { false }, {}, {}, {}, {})
            }
        }
        val shots = HashMap<DecisionKind, Int>()
        var n = 0
        while (n++ < 8000) {
            val d = game.pending ?: break
            if (d.seat != seat || d.options.size < 2) {
                game.answer(d.id, computer.choose(game, d))
                continue
            }
            val said = coach.suggest(coach.prepare(game.fork(), d), game.fork(), d)
            assertThat(said).isNotNull()
            if ((shots[d.kind] ?: 0) < 2) {
                shots[d.kind] = (shots[d.kind] ?: 0) + 1
                state = play(game, seat).copy(suggest = SuggestUi(seat, suggestions = said))
                compose.waitForIdle()
                compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
                compose.waitForIdle()
                captureScreenRoboImage("$dir/${d.kind}-${shots[d.kind]}.png")
            }
            game.answer(d.id, said!!.options.first().choice)
        }
        val t0 = System.nanoTime()
        val report = coach.review(game.save(), setOf(seat))
        println(
            "COACHREAL review ${(System.nanoTime() - t0) / 1_000_000} ms: ${report?.overall}, " +
                "${report?.mistakes?.size} mistakes",
        )
        assertThat(report).isNotNull()
        val view = game.view(seat)
        state = play(game, seat).copy(
            status = TurnStatus.Over,
            over = GameOverUi(
                view.winner!!,
                view.winReason!!,
                view.winner == Team.RED,
                view.round,
                emptyList(),
                0,
                0,
                0,
                emptyList(),
                true,
            ),
            report = ReportUi(report = report),
        )
        compose.waitForIdle()
        captureScreenRoboImage("$dir/REPORT.png")
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 20_000L
    }
}
