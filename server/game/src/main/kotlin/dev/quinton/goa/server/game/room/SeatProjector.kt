package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.DecisionClock
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.ServerMessage

/** The part of a seat's table that does not depend on the game: its cursor and its clock. */
internal class FrameInputs(
    val gameId: String,
    val seq: Long = 0,
    val ticket: Long,
    val eventsFrom: Int = 0,
    val events: List<GameEvent> = emptyList(),
    val presence: Map<String, Presence> = emptyMap(),
    val clock: (Decision?) -> DecisionClock? = { null },
    /** Who answers the pending question, when that isn't the engine's seat: a team's question goes to someone there. */
    val answering: String? = null,
)

/**
 * The one way a table reaches a person (docs/ONLINE.md "Hidden information"). A seat gets its own view
 * and its own question; about anyone else it gets only a stub and the public events. Test 4 keeps this
 * file away from the engine's saves and states, so it can't send them.
 */
internal object SeatProjector {
    /** Who is being asked, and nothing else: the prompt, the options and the card are the seat's own. */
    fun stub(seat: String, team: Team, kind: DecisionKind): Decision =
        Decision(id = 0, seat = seat, team = team, kind = kind, prompt = "", options = emptyList())

    /** The engine's id counts the questions skipped as having one option: a ticket goes out in its place. */
    fun question(d: Decision, ticket: Long): Decision = d.copy(id = ticket)

    /** [seat]'s view, with [own] (its hero after its own locks) in place: only ever its own hero. */
    fun view(session: GameSession, seat: String, own: HeroView?): GameView {
        val view = session.view(seat)
        if (own == null) return view
        return view.copy(heroes = view.heroes.map { if (it.heroId == seat) own else it })
    }

    fun decision(
        session: GameSession,
        window: SimultaneousWindow?,
        seat: String,
        ticket: Long,
        answering: String? = null,
    ): Decision? {
        val engine = session.pending ?: return null
        // On the wire `seat` is who is being asked: only the room's records keep the engine's seat.
        val pending = if (answering == null) engine else engine.copy(seat = answering)
        val own = if (window == null) pending.takeIf { it.seat == seat } else window.forecast(seat).question
        if (own != null) return question(own, ticket)
        return window?.let { windowStub(session, it) } ?: stub(pending.seat, pending.team, pending.kind)
    }

    /** The first seat still to answer, in the window's one kind: how far along that seat is stays its own. */
    private fun windowStub(session: GameSession, window: SimultaneousWindow): Decision? {
        val asked = window.open(session.setup.seats.map { it.heroId }).firstOrNull() ?: return null
        return stub(asked, session.setup.seats.first { it.heroId == asked }.team, window.publicKind)
    }

    fun table(
        session: GameSession,
        window: SimultaneousWindow?,
        seat: String,
        inputs: FrameInputs,
    ): ServerMessage.Table {
        val decision = decision(session, window, seat, inputs.ticket, inputs.answering)
        return ServerMessage.Table(
            gameId = inputs.gameId,
            seq = inputs.seq,
            view = view(session, seat, window?.forecast(seat)?.hero),
            decision = decision,
            eventsFrom = inputs.eventsFrom,
            events = inputs.events,
            locked = window?.locked(session.setup.seats.map { it.heroId }).orEmpty(),
            clock = inputs.clock(decision),
            presence = inputs.presence,
        )
    }
}
