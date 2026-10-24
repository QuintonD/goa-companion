package dev.quinton.goa.feature.board

import dev.quinton.goa.core.data.game.PlayOptions
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random

/** The real rules engine on the bundled catalog and maps (Wasp, Xargatha, Arien and Brogan are playable). */
object PlayTestData {
    val engine: GameEngine by lazy { GameEngine(bundledCatalog, bundledMaps) }

    const val MAP = "forgotten-island"
    const val CREATED = 1_000L

    /** The rulebook's first game (p.5): Wasp and Xargatha (Red) against Arien and Brogan (Blue). */
    fun firstGame(
        human: Set<String> = setOf("wasp"),
        level: AiLevel = AiLevel.NORMAL,
        tieBreaker: Team = Team.RED,
    ): GameSetup {
        fun seat(id: String, team: Team) = SeatSetup(id, team, if (id in human) Controller.Human else Controller.Ai(level))
        return GameSetup(
            mapId = MAP,
            seats = listOf(seat("wasp", Team.RED), seat("xargatha", Team.RED), seat("arien", Team.BLUE), seat("brogan", Team.BLUE)),
            length = GameLength.QUICK,
            tieBreaker = tieBreaker,
        )
    }

    /** 1 v 1: Wasp (you) against Arien. */
    fun duel(): GameSetup = GameSetup(
        mapId = MAP,
        seats = listOf(
            SeatSetup("wasp", Team.RED, Controller.Human),
            SeatSetup("arien", Team.BLUE, Controller.Ai(AiLevel.EASY)),
        ),
        length = GameLength.QUICK,
        tieBreaker = Team.BLUE,
    )

    fun stored(setup: GameSetup, id: String = "game", options: PlayOptions = PlayOptions()): StoredGame {
        val saved: SavedGame = engine.newGame(setup).save().copy(savedAt = CREATED)
        return StoredGame(id, saved, options, createdAt = CREATED, updatedAt = CREATED)
    }

    /** A random-legal player that prefers doing something (random games otherwise run very long). */
    class Busy(seed: Long) {
        private val random = Random(seed)

        fun choose(d: Decision): Choice {
            if (d.kind == DecisionKind.CHOOSE_ACTION) {
                val busy = d.options.filter { (it as? Choice.Action)?.action != ActionType.HOLD }
                if (busy.isNotEmpty() && random.nextInt(10) < 8) return busy[random.nextInt(busy.size)]
            }
            if (d.kind == DecisionKind.DEFEND) {
                val cards = d.options.filterIsInstance<Choice.Card>()
                if (cards.isNotEmpty() && random.nextInt(10) < 7) return cards[random.nextInt(cards.size)]
            }
            return d.options[random.nextInt(d.options.size)]
        }
    }

    /** The computer for tests: [Busy] choices; [onChoose] sees every decision it answers. */
    class TestAi(seed: Long, private val onChoose: (Decision) -> Unit = {}) : AiPlayer {
        private val busy = Busy(seed)

        override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
            onChoose(decision)
            return busy.choose(decision)
        }
    }
}

/** A coach that nudges while [nudging] is true, and counts the calls. */
class FakeCoach(var nudging: Boolean = false, private val points: List<CoachNote> = emptyList()) : Coach {
    var calls = 0
        private set

    override fun nudge(session: GameSession, decision: Decision, choice: Choice, cancelled: () -> Boolean): CoachNote? {
        calls++
        return if (nudging) CoachNote("Wasp's Lift Up can still reach you", "Movement and Range matter here (p.11).") else null
    }

    override fun turningPoints(saved: SavedGame, seat: String, cancelled: () -> Boolean): List<CoachNote> = points
}
