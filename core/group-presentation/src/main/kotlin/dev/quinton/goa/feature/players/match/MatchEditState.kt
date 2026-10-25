package dev.quinton.goa.feature.players.match

import dev.quinton.goa.core.domain.rating.RatingsResult
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.feature.players.common.heroName

/** A hero the picker can offer. */
data class HeroOption(val id: String, val name: String, val title: String, val portrait: String?, val complexity: Int?)

data class PilotOption(val player: Player, val rating: Rating)

/** One player's line in the "Result saved" summary. */
data class PlayerChangeUi(
    val player: Player?,
    val name: String,
    val team: Team,
    val won: Boolean,
    /** Player-space rating change from this match; null until the replay has run. */
    val delta: Double?,
    val rating: Rating,
    /** Leaderboard position now (active players with rated games); null when unrated or archived. */
    val rank: Int?,
)

/** Shown after a result is saved: what changed, and the way to play again. */
data class SavedResultUi(
    val match: Match,
    /** False while the ratings are still being recomputed. */
    val ratingsReady: Boolean,
    /** Winners first, biggest gain first. */
    val players: List<PlayerChangeUi>,
    /** Rank 1 on the leaderboard now. */
    val leader: PlayerChangeUi?,
    val leaderName: String?,
    val leaderRating: Rating?,
    val rematchOptions: List<RematchOption>,
)

data class MatchEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val notFound: Boolean = false,
    val form: MatchForm = MatchForm(playedAt = 0),
    val pilots: List<PilotOption> = emptyList(),
    val heroes: List<HeroOption> = emptyList(),
    val catalog: Catalog = Catalog.EMPTY,
    val playersById: Map<String, Player> = emptyMap(),
    /** Validation errors; shown after the first save attempt. */
    val errors: List<MatchError> = emptyList(),
    val showErrors: Boolean = false,
    /** Failed save attempts; each one scrolls the errors into view. */
    val saveAttempts: Int = 0,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val fromDraft: Boolean = false,
    /** The focused "Who won?" layout (a game just ended): the result first, details below. */
    val resultFirst: Boolean = false,
    /**
     * A finished game's result that isn't saved anywhere yet (a new match opened by the timer's
     * "Log result"): leaving asks first, even without edits.
     */
    val unsavedResult: Boolean = false,
    /** Set after a result was saved: the screen shows what changed instead of closing. */
    val saved: SavedResultUi? = null,
    /** Set once saved (without a summary) or deleted; the screen navigates back. */
    val finished: Boolean = false,
) {
    fun heroName(id: String?): String = catalog.heroName(id)

    fun playerName(id: String?): String = id?.let { playersById[it]?.name } ?: "Choose pilot"

    fun heroOption(id: String?): HeroOption? = id?.let { heroId -> heroes.firstOrNull { it.id == heroId } }
}

/** The match editor's rules, shared by the phone and the web app (ADR-043). */
object MatchEditing {
    const val MAX_TEXT = 60
    const val MAX_NOTES = 1_000
    private const val MINUTE_MS = 60_000L

    /** Who can pilot a seat: active players, plus archived ones already seated, by name. */
    fun pilots(people: List<Player>, form: MatchForm, results: RatingsResult): List<PilotOption> {
        val used = (form.red + form.blue).mapNotNull { it.playerId }.toSet()
        return people
            .filter { !it.archived || it.id in used }
            .sortedBy { it.name.lowercase() }
            .map { PilotOption(it, results.player(it.id)) }
    }

    /** Every hero in the catalog, by name. */
    fun heroes(catalog: Catalog): List<HeroOption> = catalog.heroes
        .sortedBy { it.name.lowercase() }
        .map { HeroOption(it.id, it.name, it.title, it.portrait, it.complexity) }

    /** [millis] rounded down to the minute (the editor shows no seconds). */
    fun toMinute(millis: Long): Long = millis - millis % MINUTE_MS

    /** Digits only; values above [MAX_DURATION_MIN] are capped as you type. */
    fun duration(form: MatchForm, text: String): MatchForm {
        val digits = text.filter(Char::isDigit).take(3)
        val capped = digits.toIntOrNull()?.takeIf { it > MAX_DURATION_MIN }?.let { MAX_DURATION_MIN.toString() }
        return form.copy(duration = capped ?: digits)
    }

    /** A new empty seat on [team], up to [MAX_SEATS_PER_TEAM]. */
    fun addSeat(form: MatchForm, team: Team, key: Long): MatchForm = if (form.seats(team).size >= MAX_SEATS_PER_TEAM) {
        form
    } else {
        form.withSeats(team, form.seats(team) + SeatDraft(key))
    }

    /** Picking a winner implies the match is completed. */
    fun winner(form: MatchForm, team: Team): MatchForm = form.copy(winner = team, status = MatchStatus.COMPLETED)

    /** Moves every seat to the other team (and flips the winner) — for "we logged the colours backwards". */
    fun swapTeams(form: MatchForm): MatchForm = form.copy(red = form.blue, blue = form.red, winner = form.winner?.opponent)

    /**
     * A new result (new match, pending → completed, or the "Who won?" flow) shows what it changed;
     * editing an old result just goes back.
     */
    fun isNewResult(match: Match, original: Match?, resultFlow: Boolean): Boolean =
        match.status == MatchStatus.COMPLETED &&
            (original == null || original.status != MatchStatus.COMPLETED || resultFlow)

    fun savedSummary(match: Match, people: List<Player>, results: RatingsResult): SavedResultUi {
        val byId = people.associateBy { it.id }
        val change = results.matchChanges[match.id]
        val ranked = people.filter { !it.archived && results.player(it.id).games > 0 }
            .sortedWith(compareByDescending<Player> { results.player(it.id).value }.thenBy { it.name.lowercase() })
        val rankOf = ranked.mapIndexed { i, p -> p.id to i + 1 }.toMap()
        val lines = match.participants.distinctBy { it.playerId }.map { seat ->
            PlayerChangeUi(
                player = byId[seat.playerId],
                name = byId[seat.playerId]?.name ?: "Unknown player",
                team = seat.team,
                won = seat.team == match.winner,
                delta = change?.playerDeltas?.get(seat.playerId),
                rating = results.player(seat.playerId),
                rank = rankOf[seat.playerId],
            )
        }.sortedWith(compareByDescending<PlayerChangeUi> { it.won }.thenByDescending { it.delta ?: 0.0 })
        val leader = ranked.firstOrNull()
        return SavedResultUi(
            match = match,
            ratingsReady = change != null,
            players = lines,
            leader = lines.firstOrNull { it.player?.id == leader?.id },
            leaderName = leader?.name,
            leaderRating = leader?.let { results.player(it.id) },
            rematchOptions = rematchOptions(match),
        )
    }

}
