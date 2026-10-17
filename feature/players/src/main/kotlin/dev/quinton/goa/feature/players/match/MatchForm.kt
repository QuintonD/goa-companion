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

    fun withSeats(team: Team, seats: List<SeatDraft>): MatchForm = if (team == Team.RED) copy(red = seats) else copy(blue = seats)

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
            durationMin = duration.trim().toIntOrNull(),
            notes = notes.trim(),
            draftId = draftId,
            participants = participantsOf(Team.RED) + participantsOf(Team.BLUE),
        )
    }

    companion object {
        fun from(match: Match, nextKey: () -> Long) = MatchForm(
            playedAt = match.playedAt,
            mode = match.mode,
            map = match.map.orEmpty(),
            status = match.status,
            winner = match.winner,
            winCondition = match.winCondition,
            duration = match.durationMin?.toString().orEmpty(),
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

    data object InvalidDuration : MatchError {
        override val message get() = "Duration must be a whole number of minutes (1–600)."
    }
}

const val MAX_SEATS_PER_TEAM = 5
private const val MAX_DURATION_MIN = 600

object MatchValidation {
    fun validate(
        form: MatchForm,
        heroName: (String) -> String = { it },
        playerName: (String) -> String = { it },
    ): List<MatchError> = buildList {
        if (form.red.isEmpty()) add(MatchError.EmptyTeam(Team.RED))
        if (form.blue.isEmpty()) add(MatchError.EmptyTeam(Team.BLUE))
        if ((form.red + form.blue).any { it.playerId == null }) add(MatchError.MissingPilot)
        form.heroesInUse().groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().forEach {
            add(MatchError.DuplicateHero(it, heroName(it)))
        }
        val redPlayers = form.red.mapNotNull { it.playerId }.toSet()
        form.blue.mapNotNull { it.playerId }.distinct().filter { it in redPlayers }.forEach {
            add(MatchError.BothTeams(it, playerName(it)))
        }
        if (form.status == MatchStatus.COMPLETED && form.winner == null) add(MatchError.WinnerRequired)
        val duration = form.duration.trim()
        if (duration.isNotEmpty() && duration.toIntOrNull()?.let { it in 1..MAX_DURATION_MIN } != true) {
            add(MatchError.InvalidDuration)
        }
    }
}
