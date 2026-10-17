package dev.quinton.goa.core.domain.teams

import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftEvent
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.PlannedStep
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Heroes chosen for individual players before teams exist (first half of the Player Draft):
 * the method's pool rule is applied per player; [auto] when the method assigns heroes itself.
 */
@Serializable
data class HeroSelection(
    val players: List<String>,
    val pool: List<String>,
    /** Single Draft: player → the heroes dealt to them. */
    val hands: Map<String, List<String>> = emptyMap(),
    val choices: Map<String, String> = emptyMap(),
    val auto: Boolean = false,
) {
    val isComplete: Boolean get() = players.all { it in choices }

    /** Heroes [playerId] may take: their hand (or the pool) minus other players' choices. */
    fun available(playerId: String): List<String> {
        val taken = choices.filterKeys { it != playerId }.values.toSet()
        return (hands[playerId] ?: pool).filter { it !in taken }
    }
}

object HeroSelections {
    /** Methods usable without teams: no bans and no team-specific tables or lineups. */
    fun supports(method: DraftMethod): Boolean = when (val rule = method.sequence) {
        SequenceRule.RandomAssign -> true
        is SequenceRule.Pattern -> (rule.opening + rule.repeat).none { it.action == DraftAction.BAN }
        is SequenceRule.ByCount, is SequenceRule.Lineup -> false
    }

    fun start(method: DraftMethod, players: List<String>, candidates: List<String>, seed: Long): Result<HeroSelection> =
        runCatching {
            if (!supports(method)) throw DraftError.NotSupported("${method.name} needs teams; choose another method")
            val pool = DraftEngine.drawPool(method.pool, candidates.distinct(), players.size, players.size, Random(seed))
            val hands = (method.pool as? PoolRule.DealPerSeat)?.let { rule ->
                players.withIndex().associate { (i, id) -> id to pool.subList(i * rule.perSeat, (i + 1) * rule.perSeat).toList() }
            }.orEmpty()
            val auto = method.sequence == SequenceRule.RandomAssign
            HeroSelection(
                players = players,
                pool = pool,
                hands = hands,
                choices = if (auto) players.zip(pool).toMap() else emptyMap(),
                auto = auto,
            )
        }

    fun choose(selection: HeroSelection, playerId: String, heroId: String): Result<HeroSelection> = runCatching {
        if (playerId !in selection.players) throw DraftError.UnknownSeat(playerId)
        if (heroId !in selection.pool) throw DraftError.NotInPool(heroId)
        val hand = selection.hands[playerId]
        if (hand != null && heroId !in hand) throw DraftError.NotInHand(heroId, playerId)
        if (heroId !in selection.available(playerId)) throw DraftError.Taken(heroId)
        selection.copy(choices = selection.choices + (playerId to heroId))
    }

    fun clear(selection: HeroSelection, playerId: String): HeroSelection =
        if (selection.auto) selection else selection.copy(choices = selection.choices - playerId)
}

/** Which half of the Player Draft is running. */
enum class PlayerDraftPhase { HEROES, CAPTAINS, DONE }

/**
 * The official Player Draft [RB p.22] as one persisted session: first a hero is chosen for
 * every player with a hero selection method, then Captains draft players, Team A's captain first.
 * Everyone plays their pre-selected hero. Odd counts use the official Handicap layout.
 */
@Serializable
data class PlayerDraftSession(
    val id: String,
    val createdAt: Long,
    val method: DraftMethod,
    val seed: Long,
    val candidates: List<String>,
    val selection: HeroSelection,
    val captains: PlayerDraftState,
) {
    val teamA: Team get() = captains.teamA

    val phase: PlayerDraftPhase
        get() = when {
            !selection.isComplete -> PlayerDraftPhase.HEROES
            !captains.isComplete -> PlayerDraftPhase.CAPTAINS
            else -> PlayerDraftPhase.DONE
        }

    companion object {
        fun start(
            id: String,
            createdAt: Long,
            method: DraftMethod,
            players: List<String>,
            redCaptain: String,
            blueCaptain: String,
            teamA: Team,
            candidates: List<String>,
            seed: Long,
        ): Result<PlayerDraftSession> = runCatching {
            val captains = PlayerDraft.start(players, redCaptain, blueCaptain, teamA).getOrThrow()
            val selection = HeroSelections.start(method, players, candidates, seed).getOrThrow()
            PlayerDraftSession(id, createdAt, method, seed, candidates, selection, captains)
        }
    }

    /** A completed [DraftSession] for the shared hand-off (record, pending match, seat editing). */
    fun toDraftSession(): Result<DraftSession> = runCatching {
        if (phase != PlayerDraftPhase.DONE) throw DraftError.NotComplete
        val seats = SeatLayouts.build(captains.red, captains.blue, OddSeatMode.HANDICAP).getOrThrow()
            .sortedWith(compareBy({ it.team != teamA }, { it.index }))
        val plan = seats.map { PlannedStep(it.team, DraftAction.PICK, it.index, auto = true) }
        val history = seats.mapIndexed { i, seat ->
            DraftEvent(i, seat.team, DraftAction.PICK, selection.choices.getValue(seat.pilotId.orEmpty()), seat.id)
        }
        DraftSession(
            id = id,
            createdAt = createdAt,
            method = method,
            seed = seed,
            teamA = teamA,
            seats = seats,
            candidates = candidates,
            pool = selection.pool,
            plan = plan,
            history = history,
        )
    }
}
