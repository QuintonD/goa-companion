package dev.quinton.goa.feature.board.play

import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.table.TableUi

/**
 * A game in round 2, turn 3 on the real rules engine, for the hero sheet (ADR-044). The person plays
 * Arien (Red, level 3: Rogue Wave and Arcane Whirlpool tucked, +1 Defense ×2). Wasp (Blue, level 3)
 * has Electrocute (+1 Initiative) and Control Gravity (+1 Defense) tucked, Charged Boomerang and Kinetic
 * Repulse on her Turn 1 and 2 slots, Stop Projectiles discarded, Magnetic Dagger in hand, and Static
 * Barrier played (facedown until the reveal). Tigerclaw is off the board.
 */
object HeroStateGame {
    const val MAP = "forgotten-island"

    /** Wasp's cards nobody but Wasp's player may see before the reveal (p.4). */
    val waspHidden = listOf("Electrocute", "Control Gravity", "Magnetic Dagger")
    const val WASP_PLAYED = "Static Barrier"

    val engine by lazy { GameEngine(bundledCatalog, bundledMaps) }
    val heroes by lazy { bundledCatalog.boardHeroes() }
    val cards by lazy { bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id } }

    /** Before the reveal (Arien to play a card), or [revealed]: cards resolving, Wasp first (14). */
    fun session(revealed: Boolean = false): EngineSession = position(engine, MAP) {
        clearMinions()
        round = 2
        turn = 3
        hero("wasp", Team.BLUE, Hex(2, -2)) {
            controller = Controller.Ai(AiLevel.HARD)
            level = 3
            coins = 3
            items("wasp-red-2a", "wasp-blue-2a")
            resolved(1, "wasp-red-2b")
            resolved(2, "wasp-blue-2b")
            played("wasp-silver")
            hand("wasp-gold")
        }
        hero("arien", Team.RED, Hex(1, -2)) {
            level = 3
            coins = 1
            items("arien-red-2b", "arien-green-2b")
            resolved(1, "arien-red-2a")
            resolved(2, "arien-green-2a")
            if (revealed) {
                played("arien-blue-1")
                hand("arien-gold", "arien-silver")
            } else {
                hand("arien-gold", "arien-silver", "arien-blue-1")
            }
        }
        hero("brogan", Team.RED, Hex(0, -5)) {
            controller = Controller.Ai(AiLevel.HARD)
            if (revealed) played("brogan-blue-1")
        }
        hero("tigerclaw", Team.BLUE, null) {
            controller = Controller.Ai(AiLevel.HARD)
            if (revealed) played("tigerclaw-green-1")
        }
        phase = if (revealed) PositionPhase.Resolving() else PositionPhase.PlayCards(fromSeat = 1)
    }

    fun play(s: EngineSession, mine: String = "arien") = PlayUi(
        table = TableUi(
            map = bundledMaps.first { it.id == MAP },
            view = s.view(mine),
            heroes = heroes,
            cards = cards,
            mine = setOf(mine),
            decision = s.pending,
            asking = s.pending?.seat == mine,
        ),
        status = if (s.pending?.seat == mine) TurnStatus.YourTurn else TurnStatus.Computer(s.pending?.seat ?: mine),
        recent = emptyList(),
        log = emptyList(),
        coach = false,
        pace = AiPace.NORMAL,
        takeBacks = 0,
        canTakeBack = false,
    )
}
