package dev.quinton.goa.feature.board.play

import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.MAP
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.cards
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.engine
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.heroes
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.game.recentSince
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.resolutionFlow
import dev.quinton.goa.feature.board.play.table.turnLog

/**
 * A 3v3 turn on the real rules engine for the resolution flow (ADR-046): Brogan (the person, Blue)
 * attacks Wasp with Onslaught, Wasp defends with Lift Up (5 vs 4); then Xargatha's Threatening Slash
 * defeats a Blue minion, Gydion holds, Arien and Tigerclaw move — five cards resolved before Brogan's
 * next question (turn 2's card).
 */
object ResolutionFlowGame {
    /** At Brogan's PLAY_CARD of turn 2, with the event count before his last answer. */
    fun session(): Pair<EngineSession, Int> {
        val ai = Controller.Ai(AiLevel.HARD)
        val s = position(engine, MAP) {
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) {
                played("brogan-gold")
                hand("brogan-silver", "brogan-green-1", "brogan-blue-1", "brogan-red-1")
            }
            hero("arien", Team.BLUE, Hex(4, -5)) {
                controller = ai
                played("arien-green-1")
                hand("arien-blue-1")
            }
            hero("tigerclaw", Team.BLUE, Hex(3, -4)) {
                controller = ai
                played("tigerclaw-green-1")
                hand("tigerclaw-blue-1")
            }
            hero("wasp", Team.RED, Hex(0, -2)) {
                controller = ai
                played("wasp-green-1")
                hand("wasp-blue-1", "wasp-red-1")
            }
            hero("xargatha", Team.RED, Hex(-1, -1)) {
                controller = ai
                played("xargatha-red-1")
                hand("xargatha-blue-1")
            }
            hero("gydion", Team.RED, Hex(-4, 0)) {
                controller = ai
                played("gydion-green-1")
                hand("gydion-blue-1")
            }
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        val before = s.events.size
        answer(s, Choice.Action(ActionType.PRIMARY))
        repeat(MAX_STEPS) {
            val d = s.pending ?: error("No next question")
            if (d.seat == "brogan" && d.kind == DecisionKind.PLAY_CARD) return s to before
            answer(
                s,
                when {
                    d.kind == DecisionKind.DEFEND && d.seat == "wasp" -> Choice.Card("wasp-blue-1")
                    d.kind == DecisionKind.DEFEND -> d.options.last()
                    d.kind == DecisionKind.CHOOSE_ACTION && d.seat == "xargatha" -> Choice.Action(ActionType.PRIMARY)
                    d.kind == DecisionKind.CHOOSE_ACTION && d.seat == "gydion" -> Choice.Action(ActionType.HOLD)
                    d.kind == DecisionKind.CHOOSE_ACTION ->
                        d.options.firstOrNull { (it as? Choice.Action)?.action == ActionType.MOVEMENT } ?: d.options.first()
                    else -> d.options.first()
                },
            )
        }
        error("Brogan is not asked again")
    }

    private fun answer(s: EngineSession, choice: Choice) {
        val d = s.pending ?: error("No question")
        check(s.answer(d.id, choice) is dev.quinton.goa.core.game.AnswerResult.Accepted) { "Rejected $choice for $d" }
    }

    fun play(): PlayUi {
        val (s, before) = session()
        val mine = setOf("brogan")
        return PlayUi(
            table = TableUi(
                map = bundledMaps.first { it.id == MAP },
                view = s.view("brogan"),
                heroes = heroes,
                cards = cards,
                mine = mine,
                decision = s.pending,
                asking = true,
                bottomTeam = Team.BLUE,
                turnLog = turnLog(s.events),
            ),
            status = TurnStatus.YourTurn,
            recent = recentSince(s.events, before, s.setup.seats.map { it.heroId }.toSet()).lines,
            log = emptyList(),
            coach = false,
            pace = AiPace.NORMAL,
            takeBacks = 0,
            canTakeBack = true,
            flow = resolutionFlow(
                s.events,
                before,
                s.setup.seats.associate { it.heroId to it.team },
                mine,
                { heroes.hero(it).name },
                { cards[it]?.name },
            ),
        )
    }

    private const val MAX_STEPS = 60
}
