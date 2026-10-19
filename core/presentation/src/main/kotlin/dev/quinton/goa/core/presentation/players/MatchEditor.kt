package dev.quinton.goa.core.presentation.players

import dev.quinton.goa.core.domain.rating.RatingsResult
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import java.util.concurrent.TimeUnit

/** A hero the picker can offer. */
data class HeroOption(
    val id: String,
    val name: String,
    val title: String,
    val portrait: String?,
    val complexity: Int?,
    /** The hero's product (Settings → owned products). */
    val product: String? = null,
)

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

/**
 * The match editor's rules and pickers (Players → Log match / Edit match / "Who won?"), shared by
 * the phone's `MatchEditViewModel` and the hub's editor page.
 */
object MatchEditor {
    /** Longest map text. */
    const val MAX_TEXT = 60

    /** Longest notes. */
    const val MAX_NOTES = 1_000

    /** Seats a new match starts with, per team. */
    const val NEW_SEATS_PER_TEAM = 2

    /** Pilots to offer: active players plus archived ones already seated, by name, with their rating. */
    fun pilotOptions(people: List<Player>, form: MatchForm, results: RatingsResult): List<PilotOption> {
        val used = (form.red + form.blue).mapNotNull { it.playerId }.toSet()
        return people
            .filter { !it.archived || it.id in used }
            .sortedBy { it.name.lowercase() }
            .map { PilotOption(it, results.player(it.id)) }
    }

    /** Every hero in the catalog, by name. */
    fun heroOptions(catalog: Catalog): List<HeroOption> = catalog.heroes
        .sortedBy { it.name.lowercase() }
        .map { HeroOption(it.id, it.name, it.title, it.portrait, it.complexity, it.product) }

    /** Validation with names for the messages ("Unknown hero", "Unknown player" for ids not found). */
    fun errors(form: MatchForm, catalog: Catalog, playersById: Map<String, Player>, now: Long?): List<MatchError> =
        MatchValidation.validate(
            form,
            { catalog.heroName(it) },
            { playersById[it]?.name ?: "Unknown player" },
            now = now,
        )

    /**
     * A save that is a new result (a new match, pending → completed, or the "Who won?" flow) shows
     * what it changed; editing an old result just goes back.
     */
    fun isNewResult(saved: Match, original: Match?, resultFlow: Boolean): Boolean =
        saved.status == MatchStatus.COMPLETED &&
            (original == null || original.status != MatchStatus.COMPLETED || resultFlow)

    /** The duration field as typed: digits only (three at most), values above [MAX_DURATION_MIN] capped. */
    fun durationInput(text: String): String {
        val digits = text.filter(Char::isDigit).take(3)
        val capped = digits.toIntOrNull()?.takeIf { it > MAX_DURATION_MIN }?.let { MAX_DURATION_MIN.toString() }
        return capped ?: digits
    }

    /** [millis] rounded down to the minute (the editor's time resolution). */
    fun toMinute(millis: Long): Long = millis - millis % TimeUnit.MINUTES.toMillis(1)

    /** [GameMode] by its serial name ("quick", "long", "double_lane"), or null if unknown. */
    fun gameModeOrNull(serialName: String?): GameMode? {
        val descriptor = GameMode.serializer().descriptor
        return GameMode.entries.firstOrNull { descriptor.getElementName(it.ordinal) == serialName }
    }

    /** The team's pilots in seat order, each once (a double pilot sits twice): the winner buttons' names. */
    fun pilotNames(form: MatchForm, team: Team, playersById: Map<String, Player>): List<String> =
        form.seats(team).mapNotNull { seat -> seat.playerId?.let { playersById[it]?.name } }.distinct()

    /** What a seat shows under it: a missing pilot (after a save attempt), a duplicate hero, a two-team pilot. */
    fun seatProblem(form: MatchForm, team: Team, seat: SeatDraft, showErrors: Boolean): String? {
        val duplicates = form.heroesInUse().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val otherTeam = form.seats(team.opponent).mapNotNull { it.playerId }.toSet()
        return when {
            showErrors && seat.playerId == null -> "Choose a pilot"
            seat.heroId != null && seat.heroId in duplicates -> "Hero picked twice"
            seat.playerId != null && seat.playerId in otherTeam -> "Also on ${team.opponent.label()}"
            else -> null
        }
    }

    /** "Result saved": each player's change and rank now, who leads, and the rematch choices. */
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
