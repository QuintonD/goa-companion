package dev.quinton.goa.feature.players.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.designsystem.icon.PlayerCrests
import dev.quinton.goa.core.designsystem.theme.PlayerColors
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.rating.RatingsResult
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.navigation.MatchEditRoute
import dev.quinton.goa.feature.players.common.heroName
import dev.quinton.goa.feature.players.player.MAX_NAME_LENGTH
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

@HiltViewModel(assistedFactory = MatchEditViewModel.Factory::class)
class MatchEditViewModel @AssistedInject constructor(
    @Assisted private val route: MatchEditRoute,
    private val matches: MatchRepository,
    private val players: PlayerRepository,
    catalog: CatalogRepository,
    ratings: RatingsSnapshotRepository,
    private val recentlyDeleted: RecentlyDeletedMatch,
    private val recentlySaved: RecentlySavedMatch,
    private val rematches: RematchLauncher,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: MatchEditRoute): MatchEditViewModel
    }

    private val matchId = route.matchId

    private var nextKey = 0L
    private fun key() = nextKey++

    private val edit = MutableStateFlow<EditState?>(null)
    private val events = Channel<MatchNav>(Channel.BUFFERED)

    /** Rematch / draft-again requests; the screen opens Match setup or the draft. */
    val navigation: Flow<MatchNav> = events.receiveAsFlow()

    /** Form, original and flags owned by this screen. */
    private data class EditState(
        val original: Match?,
        val form: MatchForm,
        val initialForm: MatchForm,
        val notFound: Boolean = false,
        val showErrors: Boolean = false,
        val saveAttempts: Int = 0,
        val saving: Boolean = false,
        val saved: Match? = null,
        val finished: Boolean = false,
    )

    val uiState: StateFlow<MatchEditUiState> = combine(
        edit,
        players.observePlayers(includeArchived = true),
        catalog.catalog.onStart { emit(Catalog.EMPTY) },
        ratings.observeSnapshot().map { it.ratings }.onStart { emit(RatingsResult.EMPTY) },
    ) { edit, people, cat, results ->
        if (edit == null) return@combine MatchEditUiState()
        val byId = people.associateBy { it.id }
        val used = (edit.form.red + edit.form.blue).mapNotNull { it.playerId }.toSet()
        MatchEditUiState(
            loading = false,
            isNew = edit.original == null,
            notFound = edit.notFound,
            form = edit.form,
            pilots = people
                .filter { !it.archived || it.id in used }
                .sortedBy { it.name.lowercase() }
                .map { PilotOption(it, results.player(it.id)) },
            heroes = cat.heroes
                .sortedBy { it.name.lowercase() }
                .map { HeroOption(it.id, it.name, it.title, it.portrait, it.complexity) },
            catalog = cat,
            playersById = byId,
            errors = MatchValidation.validate(
                edit.form,
                { cat.heroName(it) },
                { byId[it]?.name ?: "Unknown player" },
                now = System.currentTimeMillis(),
            ),
            showErrors = edit.showErrors,
            saveAttempts = edit.saveAttempts,
            dirty = edit.form != edit.initialForm,
            saving = edit.saving,
            fromDraft = edit.original?.draftId != null,
            resultFirst = route.result,
            unsavedResult = route.result && edit.original == null && !edit.notFound,
            saved = edit.saved?.let { savedSummary(it, people, results) },
            finished = edit.finished,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchEditUiState())

    init {
        viewModelScope.launch {
            val existing = matchId?.let { matches.observeMatch(it).first() }
            edit.value = when {
                existing != null -> MatchForm.from(existing, ::key).let { loaded ->
                    // A game that just ended: a pending match becomes a result, dated from the session start.
                    val pending = existing.status == MatchStatus.PENDING
                    val form = loaded.copy(
                        status = if (pending && route.result) MatchStatus.COMPLETED else loaded.status,
                        playedAt = route.prefillPlayedAt?.takeIf { pending }?.let(::toMinute) ?: loaded.playedAt,
                    )
                    EditState(existing, form, form)
                }

                matchId != null -> EditState(
                    null,
                    MatchForm(playedAt = now()),
                    MatchForm(playedAt = now()),
                    notFound = true,
                )

                else -> {
                    // A new match may be pre-filled (e.g. by the timer's "Log result"); bad values are ignored.
                    val form = MatchForm(
                        playedAt = route.prefillPlayedAt?.let(::toMinute)?.coerceAtMost(now()) ?: now(),
                        mode = gameModeOrNull(route.prefillMode) ?: GameMode.QUICK,
                        duration = MatchForm.durationText(route.prefillDurationMin),
                        red = List(2) { SeatDraft(key()) },
                        blue = List(2) { SeatDraft(key()) },
                    )
                    EditState(null, form, form)
                }
            }
        }
    }

    private fun now(): Long = toMinute(System.currentTimeMillis())

    private fun toMinute(millis: Long): Long = millis - millis % TimeUnit.MINUTES.toMillis(1)

    private fun updateForm(transform: (MatchForm) -> MatchForm) = edit.update { it?.copy(form = transform(it.form)) }

    private fun updateSeat(team: Team, key: Long, transform: (SeatDraft) -> SeatDraft) = updateForm { form ->
        form.withSeats(team, form.seats(team).map { if (it.key == key) transform(it) else it })
    }

    fun setPlayedAt(epochMillis: Long) = updateForm { it.copy(playedAt = epochMillis) }

    fun setMode(mode: GameMode) = updateForm { it.copy(mode = mode) }

    fun setMap(map: String) = updateForm { it.copy(map = map.take(MAX_TEXT)) }

    fun setStatus(status: MatchStatus) = updateForm { it.copy(status = status) }

    /** Picking a winner implies the match is completed. */
    fun setWinner(team: Team) = updateForm { it.copy(winner = team, status = MatchStatus.COMPLETED) }

    fun setWinCondition(condition: WinCondition?) = updateForm { it.copy(winCondition = condition) }

    /** Digits only; values above [MAX_DURATION_MIN] are capped as you type. */
    fun setDuration(text: String) = updateForm { form ->
        val digits = text.filter(Char::isDigit).take(3)
        val capped = digits.toIntOrNull()?.takeIf { it > MAX_DURATION_MIN }?.let { MAX_DURATION_MIN.toString() }
        form.copy(duration = capped ?: digits)
    }

    fun setNotes(notes: String) = updateForm { it.copy(notes = notes.take(MAX_NOTES)) }

    fun addSeat(team: Team) = updateForm { form ->
        if (form.seats(team).size >= MAX_SEATS_PER_TEAM) {
            form
        } else {
            form.withSeats(team, form.seats(team) + SeatDraft(key()))
        }
    }

    fun removeSeat(team: Team, key: Long) = updateForm { form ->
        form.withSeats(team, form.seats(team).filterNot { it.key == key })
    }

    fun setPilot(team: Team, key: Long, playerId: String) = updateSeat(team, key) { it.copy(playerId = playerId) }

    /**
     * Adds a player from the pilot picker and seats them. A known name (archived included) picks
     * that player instead of creating a second one, as everywhere else.
     */
    fun addPilot(team: Team, key: Long, name: String) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val all = players.observePlayers(includeArchived = true).first()
            val usedColors = all.map { it.color }.toSet()
            val player = players.ensurePlayer(
                Player(
                    id = UUID.randomUUID().toString(),
                    name = trimmed,
                    color = (
                        PlayerColors.all.firstOrNull {
                            it.argb !in usedColors
                        } ?: PlayerColors.default(all.size)
                        ).argb,
                    avatarId = all.size % PlayerCrests.count,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            setPilot(team, key, player.id)
        }
    }

    /** `null` = unknown hero. */
    fun setHero(team: Team, key: Long, heroId: String?) = updateSeat(team, key) { it.copy(heroId = heroId) }

    fun toggleHandicap(team: Team, key: Long) = updateSeat(team, key) { it.copy(handicap = !it.handicap) }

    /** Moves every seat to the other team (and flips the winner) — for "we logged the colours backwards". */
    fun swapTeams() = updateForm { it.copy(red = it.blue, blue = it.red, winner = it.winner?.opponent) }

    fun save() {
        val state = edit.value ?: return
        if (state.saving || state.notFound || state.saved != null) return
        if (MatchValidation.validate(state.form, now = System.currentTimeMillis()).isNotEmpty()) {
            edit.update { it?.copy(showErrors = true, saveAttempts = it.saveAttempts + 1) }
            return
        }
        edit.update { it?.copy(saving = true) }
        viewModelScope.launch {
            val original = state.original
            val match = state.form.toMatch(
                id = original?.id ?: route.newMatchId ?: UUID.randomUUID().toString(),
                createdAt = original?.createdAt ?: System.currentTimeMillis(),
                draftId = original?.draftId,
            ).copy(tieBreaker = original?.tieBreaker) // Not edited here; keep the coin from setup.
            matches.upsert(match)
            recentlySaved.put(match.id)
            // A new result (new match, pending → completed, or the "Who won?" flow) shows what it
            // changed; editing an old result just goes back.
            val newResult = match.status == MatchStatus.COMPLETED &&
                (original == null || original.status != MatchStatus.COMPLETED || route.result)
            edit.update {
                it?.copy(
                    saving = false,
                    saved = match.takeIf {
                        newResult
                    },
                    finished = !newResult,
                    initialForm = it.form,
                )
            }
        }
    }

    /** Opens Match setup for a rematch of the saved (or edited) match; it is saved when its timer starts. */
    fun rematch(kind: RematchKind?) {
        val source = edit.value?.let { it.saved ?: it.original } ?: return
        viewModelScope.launch {
            if (kind == null) {
                rematches.redraft(source)
                events.send(MatchNav.OpenDraft)
            } else {
                rematches.start(source, kind)?.let { events.send(it) }
            }
        }
    }

    fun delete() {
        val original = edit.value?.original ?: return
        viewModelScope.launch {
            matches.delete(original.id)
            recentlyDeleted.put(original)
            edit.update { it?.copy(finished = true) }
        }
    }

    private fun savedSummary(match: Match, people: List<Player>, results: RatingsResult): SavedResultUi {
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

    private companion object {
        const val MAX_TEXT = 60
        const val MAX_NOTES = 1_000

        /** [GameMode] by its serial name ("quick", "long", "double_lane"), or null if unknown. */
        fun gameModeOrNull(serialName: String?): GameMode? {
            val descriptor = GameMode.serializer().descriptor
            return GameMode.entries.firstOrNull { descriptor.getElementName(it.ordinal) == serialName }
        }
    }
}
