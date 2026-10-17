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
import dev.quinton.goa.core.domain.rating.RatingsResult
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.feature.players.common.heroName
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A hero the picker can offer. */
data class HeroOption(val id: String, val name: String, val title: String, val portrait: String?, val complexity: Int?)

data class PilotOption(val player: Player, val rating: Rating)

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
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val fromDraft: Boolean = false,
    /** Set once saved or deleted; the screen navigates back. */
    val finished: Boolean = false,
) {
    fun heroName(id: String?): String = catalog.heroName(id)

    fun playerName(id: String?): String = id?.let { playersById[it]?.name } ?: "Choose pilot"

    fun heroOption(id: String?): HeroOption? = id?.let { heroId -> heroes.firstOrNull { it.id == heroId } }
}

@HiltViewModel(assistedFactory = MatchEditViewModel.Factory::class)
class MatchEditViewModel @AssistedInject constructor(
    @Assisted private val matchId: String?,
    private val matches: MatchRepository,
    players: PlayerRepository,
    catalog: CatalogRepository,
    ratings: RatingsSnapshotRepository,
    private val recentlyDeleted: RecentlyDeletedMatch,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(matchId: String?): MatchEditViewModel
    }

    private var nextKey = 0L
    private fun key() = nextKey++

    private val edit = MutableStateFlow<EditState?>(null)

    /** Form, original and flags owned by this screen. */
    private data class EditState(
        val original: Match?,
        val form: MatchForm,
        val initialForm: MatchForm,
        val notFound: Boolean = false,
        val showErrors: Boolean = false,
        val saving: Boolean = false,
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
            errors = MatchValidation.validate(edit.form, { cat.heroName(it) }, { byId[it]?.name ?: "Unknown player" }),
            showErrors = edit.showErrors,
            dirty = edit.form != edit.initialForm,
            saving = edit.saving,
            fromDraft = edit.original?.draftId != null,
            finished = edit.finished,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchEditUiState())

    init {
        viewModelScope.launch {
            val existing = matchId?.let { matches.observeMatch(it).first() }
            edit.value = when {
                existing != null -> MatchForm.from(existing, ::key).let { EditState(existing, it, it) }
                matchId != null -> EditState(null, MatchForm(playedAt = now()), MatchForm(playedAt = now()), notFound = true)
                else -> {
                    val form = MatchForm(
                        playedAt = now(),
                        red = List(2) { SeatDraft(key()) },
                        blue = List(2) { SeatDraft(key()) },
                    )
                    EditState(null, form, form)
                }
            }
        }
    }

    private fun now(): Long {
        val millis = System.currentTimeMillis()
        return millis - millis % TimeUnit.MINUTES.toMillis(1)
    }

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

    fun setDuration(text: String) = updateForm { it.copy(duration = text.filter(Char::isDigit).take(3)) }

    fun setNotes(notes: String) = updateForm { it.copy(notes = notes.take(MAX_NOTES)) }

    fun addSeat(team: Team) = updateForm { form ->
        if (form.seats(team).size >= MAX_SEATS_PER_TEAM) form else form.withSeats(team, form.seats(team) + SeatDraft(key()))
    }

    fun removeSeat(team: Team, key: Long) = updateForm { form -> form.withSeats(team, form.seats(team).filterNot { it.key == key }) }

    fun setPilot(team: Team, key: Long, playerId: String) = updateSeat(team, key) { it.copy(playerId = playerId) }

    /** `null` = unknown hero. */
    fun setHero(team: Team, key: Long, heroId: String?) = updateSeat(team, key) { it.copy(heroId = heroId) }

    fun toggleHandicap(team: Team, key: Long) = updateSeat(team, key) { it.copy(handicap = !it.handicap) }

    /** Moves every seat to the other team (and flips the winner) — for "we logged the colours backwards". */
    fun swapTeams() = updateForm { it.copy(red = it.blue, blue = it.red, winner = it.winner?.opponent) }

    fun save() {
        val state = edit.value ?: return
        if (state.saving || state.notFound) return
        if (MatchValidation.validate(state.form).isNotEmpty()) {
            edit.update { it?.copy(showErrors = true) }
            return
        }
        edit.update { it?.copy(saving = true) }
        viewModelScope.launch {
            val original = state.original
            val match = state.form.toMatch(
                id = original?.id ?: UUID.randomUUID().toString(),
                createdAt = original?.createdAt ?: System.currentTimeMillis(),
                draftId = original?.draftId,
            )
            matches.upsert(match)
            edit.update { it?.copy(saving = false, finished = true) }
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

    private companion object {
        const val MAX_TEXT = 60
        const val MAX_NOTES = 1_000
    }
}
