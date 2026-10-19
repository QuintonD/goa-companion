package dev.quinton.goa.feature.players.samepeople

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMatchUpRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.presentation.players.MergeCopy
import dev.quinton.goa.core.presentation.players.SamePeople
import dev.quinton.goa.core.presentation.players.SamePeopleOutcome
import dev.quinton.goa.core.presentation.players.SamePeoplePair
import dev.quinton.goa.core.presentation.players.SamePeopleUiState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * "Same people?" (ADR-031): players whose names suggest one person entered twice, computed from
 * the data on this phone whenever it changes (a Nearby sync, a sync file, the hub). "Same person"
 * merges the newer into the older (the usual merge, refused when they played together);
 * "Different people" is remembered on this phone only.
 */
@HiltViewModel
class SamePeopleViewModel @Inject constructor(
    private val players: PlayerRepository,
    matches: MatchRepository,
    private val merges: PlayerMergeRepository,
    private val matchUp: PlayerMatchUpRepository,
) : ViewModel() {
    private val outcome = MutableStateFlow<SamePeopleOutcome?>(null)

    val uiState: StateFlow<SamePeopleUiState> = combine(
        suggestions(players, matches, merges, matchUp),
        matchUp.differentPeople,
        outcome,
    ) { pairs, different, done ->
        SamePeopleUiState(loading = false, pairs = pairs, differentCount = different.size, outcome = done)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SamePeopleUiState())

    /** "Same person": the newer player of the pair becomes the older one everywhere. */
    fun samePerson(pairKey: String) {
        viewModelScope.launch {
            val pair = uiState.value.pairs.firstOrNull { it.key == pairKey } ?: return@launch
            val from = pair.other.player
            val into = pair.keep.player
            outcome.value = when (val result = merges.merge(from.id, into.id)) {
                PlayerMergeResult.Merged -> SamePeopleOutcome.Merged(from.name, into.name)

                is PlayerMergeResult.PlayedTogether -> {
                    val people = players.observePlayers(includeArchived = true).first().associateBy { it.id }
                    SamePeopleOutcome.PlayedTogether(MergeCopy.playedTogether(result.matches, people))
                }

                // Gone already (merged on another phone and synced meanwhile): the list updates itself.
                PlayerMergeResult.NotPossible -> null
            }
        }
    }

    /** "Different people": never suggested again on this phone. */
    fun differentPeople(pairKey: String) {
        viewModelScope.launch { matchUp.markDifferent(pairKey) }
    }

    /** "Ask again": forgets every "Different people" answer. */
    fun askAgain() {
        viewModelScope.launch { matchUp.forgetDifferent() }
    }

    fun outcomeShown() {
        outcome.value = null
    }

    companion object {
        /** The pairs to review, from the current data (also the Players tab's count). */
        fun suggestions(
            players: PlayerRepository,
            matches: MatchRepository,
            merges: PlayerMergeRepository,
            matchUp: PlayerMatchUpRepository,
        ): Flow<List<SamePeoplePair>> = combine(
            players.observePlayers(),
            matches.observeMatches(),
            merges.observeMerges(),
            matchUp.differentPeople,
        ) { people, log, merged, different -> SamePeople.pairs(people, log, merged, different) }
    }
}
