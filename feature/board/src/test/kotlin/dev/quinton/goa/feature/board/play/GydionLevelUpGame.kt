package dev.quinton.goa.feature.board.play

import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
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
import dev.quinton.goa.feature.board.play.table.turnLog

/**
 * Gydion's level up on the real rules engine (ADR-045 extended): the end of round 1, Gydion (Red,
 * level 1, one coin) levels up to 2. His Tier I cards each name one spell (Elementary Evocation:
 * Burning Hands), their Tier II upgrades two (Lesser Evocation: Burning Hands, Fireball; Lesser
 * Necromancy: Vampiric Touch, Create Undead). [gydionAi]: Gydion is the computer's, Arien the person's.
 */
object GydionLevelUpGame {
    const val MAP = "forgotten-island"
    const val HAND_CARD = "gydion-red-1"
    const val UPGRADE = "gydion-red-2a"
    const val OTHER_UPGRADE = "gydion-red-2b"

    val engine by lazy { GameEngine(bundledCatalog, bundledMaps) }
    val heroes by lazy { bundledCatalog.boardHeroes() }
    val cards by lazy { bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id } }

    fun name(id: String): String = cards.getValue(id).name

    /** At Gydion's UPGRADE_CARD question; [keep]: Elementary Evocation upgraded, at KEEP_UPGRADE. */
    fun session(gydionAi: Boolean = false, keep: Boolean = false): EngineSession {
        val s = position(engine, MAP) {
            clearMinions()
            hero("gydion", Team.RED, Hex(1, -2)) {
                coins = 1
                if (gydionAi) controller = Controller.Ai(AiLevel.HARD)
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { if (!gydionAi) controller = Controller.Ai(AiLevel.HARD) }
            phase = PositionPhase.EndOfRound
        }
        repeat(MAX_STEPS) {
            val d = s.pending ?: error("No level up")
            if (d.seat == "gydion" && d.kind == DecisionKind.UPGRADE_CARD) {
                if (!keep) return s
                s.answer(d.id, Choice.Card(HAND_CARD))
                check(s.pending?.kind == DecisionKind.KEEP_UPGRADE)
                return s
            }
            s.answer(d.id, d.options.first())
        }
        error("No level up")
    }

    fun play(s: EngineSession, mine: String = "gydion") = PlayUi(
        table = TableUi(
            map = bundledMaps.first { it.id == MAP },
            view = s.view(mine),
            heroes = heroes,
            cards = cards,
            mine = setOf(mine),
            decision = s.pending,
            asking = s.pending?.seat == mine,
            turnLog = turnLog(s.events),
        ),
        status = if (s.pending?.seat == mine) TurnStatus.YourTurn else TurnStatus.Computer(s.pending?.seat ?: mine),
        recent = emptyList(),
        log = emptyList(),
        coach = false,
        pace = AiPace.NORMAL,
        takeBacks = 0,
        canTakeBack = false,
    )

    private const val MAX_STEPS = 40
}
