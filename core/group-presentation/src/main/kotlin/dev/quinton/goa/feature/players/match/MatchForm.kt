package dev.quinton.goa.feature.players.match

import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition

/** One editable hero seat. [key] keeps list identity stable while seats are added/removed. */
data class SeatDraft(
    val key: Long,
    val playerId: String? = null,
    val heroId: String? = null,
    val handicap: Boolean = false,
)

/** The editable part of a match, independent of Android and of the screen. */
data class MatchForm(
    val playedAt: Long,
    val mode: GameMode = GameMode.QUICK,
    val map: String = "",
    val status: MatchStatus = MatchStatus.COMPLETED,
    val winner: Team? = null,
    val winCondition: WinCondition? = null,
    val duration: String = "",
    val notes: String = "",
    val red: List<SeatDraft> = emptyList(),
    val blue: List<SeatDraft> = emptyList(),
) {
    fun seats(team: Team): List<SeatDraft> = if (team == Team.RED) red else blue

    fun withSeats(team: Team, seats: List<SeatDraft>): MatchForm = if (team == Team.RED) {
        copy(red = seats)
    } else {
        copy(blue = seats)
    }

    /** Players piloting two or more seats on [team]. */
    fun doublePilots(team: Team): Set<String> =
        seats(team).mapNotNull { it.playerId }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    fun heroesInUse(): List<String> = (red + blue).mapNotNull { it.heroId }

    fun toMatch(id: String, createdAt: Long, draftId: String?): Match {
        fun participantsOf(team: Team): List<Participant> = seats(team).map {
            Participant(playerId = requireNotNull(it.playerId), heroId = it.heroId, team = team, handicap = it.handicap)
        }
        val completed = status == MatchStatus.COMPLETED
        return Match(
            id = id,
            createdAt = createdAt,
            playedAt = playedAt,
            status = status,
            mode = mode,
            map = map.trim().ifEmpty { null },
            winner = if (completed) winner else null,
            winCondition = if (completed) winCondition else null,
            durationMin = durationMinutes(duration),
            notes = notes.trim(),
            draftId = draftId,
            participants = participantsOf(Team.RED) + participantsOf(Team.BLUE),
        )
    }

    companion object {
        /**
         * Minutes for a stored/incoming duration: clamped to [MAX_DURATION_MIN], and ignored
         * (empty) when not positive, so an odd value never blocks saving.
         */
        fun durationText(minutes: Int?): String =
            minutes?.takeIf { it > 0 }?.coerceAtMost(MAX_DURATION_MIN)?.toString().orEmpty()

        /** The duration field as minutes: clamped like [durationText]; null when empty, zero or not a number. */
        fun durationMinutes(text: String): Int? =
            text.trim().toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(MAX_DURATION_MIN)

        fun from(match: Match, nextKey: () -> Long) = MatchForm(
            playedAt = match.playedAt,
            mode = match.mode,
            map = match.map.orEmpty(),
            status = match.status,
            winner = match.winner,
            winCondition = match.winCondition,
            duration = durationText(match.durationMin),
            notes = match.notes,
            red = match.team(Team.RED).map { SeatDraft(nextKey(), it.playerId, it.heroId, it.handicap) },
            blue = match.team(Team.BLUE).map { SeatDraft(nextKey(), it.playerId, it.heroId, it.handicap) },
        )
    }
}

sealed interface MatchError {
    val message: String

    data class EmptyTeam(val team: Team) : MatchError {
        override val message get() = "Team ${if (team == Team.RED) "Red" else "Blue"} needs at least one hero seat."
    }

    data object MissingPilot : MatchError {
        override val message get() = "Choose a pilot for every seat."
    }

    data class DuplicateHero(val heroId: String, val heroName: String) : MatchError {
        override val message get() = "$heroName is picked twice. Each hero can only be in a match once."
    }

    data class BothTeams(val playerId: String, val playerName: String) : MatchError {
        override val message get() = "$playerName can't play for both teams."
    }

    data object WinnerRequired : MatchError {
        override val message get() = "Choose the winning team of a completed match."
    }

    data object FutureDate : MatchError {
        override val message get() = "The match can't be in the future. Check the date and time."
    }
}

/** Clock slack for [MatchError.FutureDate]: a phone clock a few minutes off never blocks a save. */
const val FUTURE_SLACK_MS = 5 * 60_000L

const val MAX_SEATS_PER_TEAM = 5

/** Longest duration the editor stores; longer incoming values are clamped to it. */
const val MAX_DURATION_MIN = 600

/**
 * Integrity rules (a pilot per seat, each hero once, nobody on both teams) always apply. Result
 * rules (both teams seated, a winner) apply only to COMPLETED matches, so a PENDING or ABANDONED
 * match can be saved with no seats, e.g. to abandon it or keep notes. The duration never blocks
 * saving: out-of-range values are clamped or dropped ([MatchForm.durationMinutes]).
 */
object MatchValidation {
    /** [now]: when set, a [MatchForm.playedAt] later than now (plus [FUTURE_SLACK_MS]) is an error. */
    fun validate(
        form: MatchForm,
        heroName: (String) -> String = { it },
        playerName: (String) -> String = { it },
        now: Long? = null,
    ): List<MatchError> = buildList {
        if (now != null && form.playedAt > now + FUTURE_SLACK_MS) add(MatchError.FutureDate)
        val completed = form.status == MatchStatus.COMPLETED
        if (completed && form.red.isEmpty()) add(MatchError.EmptyTeam(Team.RED))
        if (completed && form.blue.isEmpty()) add(MatchError.EmptyTeam(Team.BLUE))
        if ((form.red + form.blue).any { it.playerId == null }) add(MatchError.MissingPilot)
        form.heroesInUse().groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach {
            add(MatchError.DuplicateHero(it, heroName(it)))
        }
        val redPlayers = form.red.mapNotNull { it.playerId }.toSet()
        form.blue.mapNotNull { it.playerId }.distinct().filter { it in redPlayers }.forEach {
            add(MatchError.BothTeams(it, playerName(it)))
        }
        if (completed && form.winner == null) add(MatchError.WinnerRequired)
    }
}
