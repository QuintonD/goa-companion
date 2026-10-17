package dev.quinton.goa.core.domain.draft

import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random

/** One choice on the board. [team]/[action] are optional assertions from the UI. */
data class DraftCommand(
    val heroId: String,
    val team: Team? = null,
    val action: DraftAction? = null,
    /** The step index the choice was made for; a different current step is [DraftError.StaleStep]. */
    val step: Int? = null,
)

/**
 * Pure draft state machine (no Android, no clocks): every function returns a new
 * [DraftSession] or a typed [DraftError]. Randomness comes only from the session seed, so the
 * same setup always deals the same pool.
 */
object DraftEngine {
    fun start(setup: DraftSetup): Result<DraftSession> = runCatching {
        validateSeats(setup.seats)
        val countA = setup.seats.count { it.team == setup.teamA }
        val countB = setup.seats.size - countA
        val slotSteps = DraftPlanner.plan(setup.method, countA, countB).getOrThrow()
        val candidates = setup.candidates.distinct()
        val sequence = setup.method.sequence
        val auto = sequence == SequenceRule.RandomAssign || sequence is SequenceRule.Lineup
        var plan = slotSteps.map { s ->
            PlannedStep(
                team = if (s.team == TeamSlot.A) setup.teamA else setup.teamA.opponent,
                action = s.action,
                seat = s.seat,
                hidden = s.hidden,
                seconds = s.seconds ?: setup.stepSeconds,
                auto = auto,
            )
        }
        val pool = if (sequence is SequenceRule.Lineup) {
            // Fixed heroes; a hero that isn't eligible (e.g. not in the catalog) leaves its seat open.
            plan = plan.filter { lineupHero(sequence, it, countA, countB) in candidates }
            plan.mapNotNull { lineupHero(sequence, it, countA, countB) }
        } else {
            drawPool(setup.method.pool, candidates, setup.seats.size, slotSteps.size, Random(setup.seed))
        }
        val session = DraftSession(
            id = setup.id,
            createdAt = setup.createdAt,
            method = setup.method,
            seed = setup.seed,
            teamA = setup.teamA,
            seats = setup.seats.sortedWith(compareBy({ it.team != setup.teamA }, { it.index })),
            candidates = candidates,
            pool = pool,
            plan = plan,
            stepSeconds = setup.stepSeconds,
        )
        rebuild(session, pool)
    }

    fun apply(session: DraftSession, command: DraftCommand): Result<DraftSession> = runCatching {
        val index = session.currentStepIndex
        command.step?.let { if (it != index) throw DraftError.StaleStep(it, index) }
        if (index == null) throw DraftError.Complete
        val step = session.plan[index]
        command.team?.let { if (it != step.team) throw DraftError.WrongTeam(step.team, it) }
        command.action?.let { if (it != step.action) throw DraftError.WrongAction(step.action, it) }
        val hero = command.heroId
        if (hero !in session.pool) throw DraftError.NotInPool(hero)
        val seatId = step.seatId
        val hand = seatId?.takeIf { step.action == DraftAction.PICK }?.let { session.handOf(it) }
        val notInHand = if (seatId != null && hand != null && hero !in hand) DraftError.NotInHand(hero, seatId) else null
        // A hero concealed in the current hidden run looks available to everyone; refusing it
        // must not say who took it or whether it was banned or picked [APP].
        if (hero in session.concealedIds()) throw notInHand ?: DraftError.Unavailable
        if (available(session).isEmpty()) throw DraftError.PoolExhausted
        if (hero in session.bannedIds) throw DraftError.AlreadyBanned(hero)
        session.picks.firstOrNull { it.heroId == hero }?.let { throw DraftError.AlreadyPicked(hero, it.team) }
        notInHand?.let { throw it }
        session.copy(history = session.history + DraftEvent(index, step.team, step.action, hero, seatId))
    }

    fun undo(session: DraftSession): Result<DraftSession> =
        if (!session.canUndo) {
            Result.failure(DraftError.NothingToUndo)
        } else {
            Result.success(session.copy(history = session.history.dropLast(1), assignments = emptyMap()))
        }

    /** Heroes that can legally be chosen for the current step (empty when complete). */
    fun available(session: DraftSession): List<String> {
        val step = session.currentStep ?: return emptyList()
        val taken = session.bannedIds + session.pickedIds
        val hand = step.seatId?.takeIf { step.action == DraftAction.PICK }?.let { session.handOf(it) }
        return (hand ?: session.pool).filter { it !in taken }
    }

    /** Deals a fresh pool with [seed]; only before the first choice. */
    fun reroll(session: DraftSession, seed: Long): Result<DraftSession> {
        if (session.fromPlayerDraft) return Result.failure(DraftError.PlayerDraftFixed)
        if (session.isStarted) return Result.failure(DraftError.AlreadyStarted)
        return start(
            DraftSetup(
                id = session.id,
                createdAt = session.createdAt,
                method = session.method,
                seats = session.seats,
                teamA = session.teamA,
                candidates = session.candidates,
                seed = seed,
                stepSeconds = session.stepSeconds,
            ),
        )
    }

    /** Heroes that may replace a pool hero (eligible but not in the pool). */
    fun swapCandidates(session: DraftSession): List<String> = session.candidates.filter { it !in session.pool }

    /** Replaces [out] with [replacement] in the pool; only before the first choice. */
    fun swapPoolHero(session: DraftSession, out: String, replacement: String): Result<DraftSession> = runCatching {
        if (session.fromPlayerDraft) throw DraftError.PlayerDraftFixed
        if (session.isStarted) throw DraftError.AlreadyStarted
        val position = session.pool.indexOf(out)
        if (position < 0) throw DraftError.NotInPool(out)
        if (replacement !in session.candidates || replacement in session.pool) throw DraftError.NotInPool(replacement)
        val pool = session.pool.toMutableList().also { it[position] = replacement }
        rebuild(session, pool)
    }

    /**
     * Hero per seat, in seat order, after any rearranging. Once seats were rearranged,
     * [DraftSession.assignments] is the full seat → hero map (open seats are null) and is used
     * as is; there is no fallback to the original picks, so a hero never shows on two seats.
     */
    fun assignments(session: DraftSession): List<SeatAssignment> {
        if (session.assignments.isNotEmpty()) {
            return session.seats.map { seat -> SeatAssignment(seat, session.assignments[seat.id]) }
        }
        val picked = session.picks.associate { it.seatId to it.heroId }
        return session.seats.map { seat -> SeatAssignment(seat, picked[seat.id]) }
    }

    /**
     * Swaps the heroes of two seats on the same team once the draft is complete. Only for
     * team-pick methods; see [DraftSession.seatLock].
     */
    fun swapSeats(session: DraftSession, first: String, second: String): Result<DraftSession> = runCatching {
        session.seatLock?.let { throw it }
        if (!session.isComplete) throw DraftError.NotComplete
        val a = session.seat(first) ?: throw DraftError.UnknownSeat(first)
        val b = session.seat(second) ?: throw DraftError.UnknownSeat(second)
        if (a.team != b.team) throw DraftError.InvalidSeats("Heroes can only move between seats of the same team")
        val current = assignments(session).associate { it.seat.id to it.heroId }
        // A full map (open seats included as null): swapping with an open seat moves the hero.
        val updated = current + mapOf(a.id to current[b.id], b.id to current[a.id])
        session.copy(assignments = updated)
    }

    /** Changes who pilots a seat (assigning picks to players before the match). */
    fun setPilot(session: DraftSession, seatId: String, pilotId: String?): Result<DraftSession> = runCatching {
        if (session.seat(seatId) == null) throw DraftError.UnknownSeat(seatId)
        session.copy(seats = session.seats.map { if (it.id == seatId) it.copy(pilotId = pilotId) else it })
    }

    /** Marks a seat as playing with its Handicap card (allowed at any count to calibrate skill [RB]). */
    fun setHandicap(session: DraftSession, seatId: String, handicap: Boolean): Result<DraftSession> = runCatching {
        if (session.seat(seatId) == null) throw DraftError.UnknownSeat(seatId)
        session.copy(seats = session.seats.map { if (it.id == seatId) it.copy(handicap = handicap) else it })
    }

    fun toRecord(session: DraftSession, matchId: String?): DraftRecord = DraftRecord(
        id = session.id,
        createdAt = session.createdAt,
        methodId = session.method.id,
        methodKind = session.method.kind,
        pool = session.pool,
        teamA = session.teamA,
        steps = session.history.map { DraftStepRecord(it.team, it.action, it.heroId) },
        matchId = matchId,
    )

    /** A PENDING match with one participant per seat; fails if a seat has no pilot. */
    fun toPendingMatch(session: DraftSession, matchId: String, now: Long): Result<Match> = runCatching {
        if (!session.isComplete) throw DraftError.NotComplete
        val participants = assignments(session)
            .sortedWith(compareBy({ it.seat.team }, { it.seat.index }))
            .map { (seat, hero) ->
                Participant(
                    playerId = seat.pilotId ?: throw DraftError.InvalidSeats("Seat ${seat.id} has no pilot"),
                    heroId = hero,
                    team = seat.team,
                    handicap = seat.handicap,
                )
            }
        val players = participants.mapTo(mutableSetOf()) { it.playerId }.size
        Match(
            id = matchId,
            createdAt = now,
            playedAt = now,
            status = MatchStatus.PENDING,
            mode = suggestedMode(players),
            draftId = session.id,
            participants = participants,
        )
    }

    /**
     * Starting mode for the pending match; Match setup lets the table change it. Double lane is
     * required with 8+ players [RB]; otherwise Quick.
     */
    fun suggestedMode(players: Int): GameMode = if (players >= 8) GameMode.DOUBLE_LANE else GameMode.QUICK

    private fun validateSeats(seats: List<Seat>) {
        seatProblem(seats)?.let { throw DraftError.InvalidSeats(it) }
    }

    private fun seatProblem(seats: List<Seat>): String? {
        if (seats.map { it.id }.toSet().size != seats.size) return "Duplicate seats"
        return Team.entries.firstNotNullOfOrNull { team ->
            val indices = seats.filter { it.team == team }.map { it.index }.sorted()
            when {
                indices.isEmpty() -> "${team.name} has no seats"
                indices != indices.indices.toList() -> "Seat indices must be 0..n-1"
                else -> null
            }
        }
    }

    internal fun drawPool(
        rule: PoolRule,
        candidates: List<String>,
        seats: Int,
        required: Int,
        rng: Random,
    ): List<String> {
        val size = when (rule) {
            PoolRule.All -> candidates.size
            is PoolRule.Random -> rule.size
            is PoolRule.SeatsPlus -> seats + rule.extra
            is PoolRule.DealPerSeat -> seats * rule.perSeat
        }
        if (size < required) throw DraftError.PoolTooSmall(required, size)
        val strict = rule is PoolRule.DealPerSeat || rule is PoolRule.SeatsPlus
        val target = if (strict) size else minOf(size, candidates.size)
        if (candidates.size <
            maxOf(target, required)
        ) {
            throw DraftError.PoolTooSmall(maxOf(target, required), candidates.size)
        }
        return if (rule == PoolRule.All) candidates else candidates.shuffled(rng).take(target)
    }

    /** Re-derives hands and automatic picks from [pool] (after a deal, re-roll or swap). */
    private fun rebuild(session: DraftSession, pool: List<String>): DraftSession {
        val hands = when (val rule = session.method.pool) {
            is PoolRule.DealPerSeat -> dealOrder(session).withIndex().associate { (i, seat) ->
                seat.id to pool.subList(i * rule.perSeat, (i + 1) * rule.perSeat).toList()
            }

            else -> emptyMap()
        }
        val base = session.copy(pool = pool, hands = hands, history = emptyList(), assignments = emptyMap())
        if (base.plan.none { it.auto }) return base
        // All Random: each seat receives the next hero of the shuffled pool. Lineup: the pool is
        // already the fixed heroes in plan order.
        val history = base.plan.mapIndexed { i, step -> DraftEvent(i, step.team, step.action, pool[i], step.seatId) }
        return base.copy(history = history)
    }

    private fun lineupHero(rule: SequenceRule.Lineup, step: PlannedStep, countA: Int, countB: Int): String? {
        val lineup = rule.forSeats(countA, countB) ?: rule.forSeats(countB, countA) ?: return null
        val heroes = if (step.team == Team.RED) lineup.red else lineup.blue
        return step.seat?.let { heroes.getOrNull(it) }
    }

    /** Dealing order for hands: A0, B0, A1, B1, … then any extra seats of the larger team. */
    private fun dealOrder(session: DraftSession): List<Seat> {
        val a = session.seatsOf(session.teamA)
        val b = session.seatsOf(session.teamB)
        return (0 until maxOf(a.size, b.size)).flatMap { listOfNotNull(a.getOrNull(it), b.getOrNull(it)) }
    }
}
