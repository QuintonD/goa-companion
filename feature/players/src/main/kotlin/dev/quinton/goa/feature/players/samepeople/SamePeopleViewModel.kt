package dev.quinton.goa.feature.players.samepeople

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMatchUpRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.domain.player.PlayerMatchUp
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.players.common.Dates
import dev.quinton.goa.feature.players.player.playedTogether
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One side of a pair: who, how many matches, when last. */
data class PairSide(val player: Player, val games: Int, val lastPlayed: Long?)

data class SamePeoplePair(val key: String, val keep: PairSide, val other: PairSide)

sealed interface SamePeopleOutcome {
    data class Merged(val fromName: String, val intoName: String) : SamePeopleOutcome

    /** Refused: the two played in these matches (one line each). */
    data class PlayedTogether(val matches: List<String>) : SamePeopleOutcome
}

data class SamePeopleUiState(
    val loading: Boolean = true,
    /** Closest first; "Same person" merges [SamePeoplePair.other] into [SamePeoplePair.keep]. */
    val pairs: List<SamePeoplePair> = emptyList(),
    /** Pairs this phone was told are different people ("Ask again" forgets them). */
    val differentCount: Int = 0,
    val outcome: SamePeopleOutcome? = null,
)

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
                    SamePeopleOutcome.PlayedTogether(playedTogether(result.matches, people))
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
        ) { people, log, merged, different ->
            val resolved = merged.resolve(log)
            PlayerMatchUp.suggest(people, resolved, merged, different).map { pair ->
                SamePeoplePair(pair.key, side(pair.keep, resolved), side(pair.other, resolved))
            }
        }

        private fun side(player: Player, matches: List<Match>): PairSide {
            val mine = matches.filter { player.id in it.playerIds }
            return PairSide(player, mine.size, mine.maxOfOrNull { it.playedAt })
        }

        /** "12 matches · last played Sat 3 Oct 2026", or "No matches yet". */
        fun summary(side: PairSide): String = when (side.games) {
            0 -> "No matches yet"
            1 -> "1 match · ${Dates.day(side.lastPlayed ?: 0)}"
            else -> "${side.games} matches · last ${Dates.day(side.lastPlayed ?: 0)}"
        }
    }
}
