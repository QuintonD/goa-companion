package dev.quinton.goa.feature.players.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.meta.PlayerHeroStat
import dev.quinton.goa.core.domain.meta.WinConditionMix
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.feature.players.common.heroName
import dev.quinton.goa.feature.players.match.MatchLogViewModel.Companion.toRow
import dev.quinton.goa.feature.players.match.MatchRowUi
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HeroRecordRow(val heroId: String, val name: String, val portrait: String?, val record: WinLoss)

/** Another player and this player's record against them (rivals) or with them (teammates). */
data class OpponentRow(val player: Player?, val name: String, val record: WinLoss)

data class PlayerDetailUiState(
    val loading: Boolean = true,
    val player: Player? = null,
    val rating: Rating = Rating.UNRATED,
    val rank: Int? = null,
    val rankedPlayers: Int = 0,
    val initialRating: Double = Rating.START,
    /** Rating after each rated match, oldest first. */
    val history: List<Double> = emptyList(),
    /** When each [history] match was played (epoch ms), for the chart's date axis. */
    val historyDates: List<Long> = emptyList(),
    /** How this player's wins came. */
    val winConditions: WinConditionMix = WinConditionMix.NONE,
    /** Opponents with enough games, most one-sided first; records are this player's. */
    val rivals: List<OpponentRow> = emptyList(),
    /** Teammates with enough games together, best first. */
    val teammates: List<OpponentRow> = emptyList(),
    val peak: Double? = null,
    val doublePilotGames: Int = 0,
    /** Heroes with enough games (best first); falls back to most played when none qualify. */
    val heroes: List<HeroRecordRow> = emptyList(),
    val heroesAreBest: Boolean = false,
    val recentMatches: List<MatchRowUi> = emptyList(),
    val totalMatches: Int = 0,
    /** Other active players this one could be merged into (a duplicate of one person), by name. */
    val mergeCandidates: List<MergeCandidate> = emptyList(),
    val mergeOutcome: MergeOutcome? = null,
)

/** A player to merge into, with how many matches they have. */
data class MergeCandidate(val player: Player, val matches: Int)

sealed interface MergeOutcome {
    data class Merged(val intoId: String, val intoName: String) : MergeOutcome

    /** Refused: the two played in these matches (one line each: date and teams). */
    data class PlayedTogether(val matches: List<String>) : MergeOutcome
}

const val RECENT_MATCHES = 5
private const val MAX_HEROES = 5

@HiltViewModel(assistedFactory = PlayerDetailViewModel.Factory::class)
class PlayerDetailViewModel @AssistedInject constructor(
    @Assisted private val playerId: String,
    private val players: PlayerRepository,
    private val merges: PlayerMergeRepository,
    matches: MatchRepository,
    ratings: RatingsSnapshotRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playerId: String): PlayerDetailViewModel
    }

    private val outcome = MutableStateFlow<MergeOutcome?>(null)

    val uiState: StateFlow<PlayerDetailUiState> = combine(
        players.observePlayers(includeArchived = true),
        matches.observeMatches(),
        ratings.observeSnapshot(),
        catalog.catalog.onStart { emit(Catalog.EMPTY) },
        outcome,
    ) { people, log, snapshot, cat, merged ->
        build(playerId, people, log, snapshot, cat).copy(mergeOutcome = merged)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerDetailUiState())

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { players.archive(playerId, archived) }
    }

    /** "Merge into…": this player becomes [intoId] everywhere, unless they played together. */
    fun merge(intoId: String) {
        viewModelScope.launch {
            val people = players.observePlayers(includeArchived = true).first().associateBy { it.id }
            outcome.value = when (val result = merges.merge(playerId, intoId)) {
                PlayerMergeResult.Merged -> MergeOutcome.Merged(intoId, people[intoId]?.name.orEmpty())

                is PlayerMergeResult.PlayedTogether -> MergeOutcome.PlayedTogether(playedTogether(result.matches, people))

                PlayerMergeResult.NotPossible -> null
            }
        }
    }

    fun mergeOutcomeShown() {
        outcome.value = null
    }

    companion object {
        fun build(
            playerId: String,
            players: List<Player>,
            matches: List<Match>,
            snapshot: RatingsSnapshot,
            catalog: Catalog,
        ): PlayerDetailUiState {
            val byId = players.associateBy { it.id }
            val player = byId[playerId] ?: return PlayerDetailUiState(loading = false)
            val ratings = snapshot.ratings
            val ranked = players.filter { !it.archived && ratings.player(it.id).games > 0 }
                .sortedByDescending { ratings.player(it.id).value }
            val points = ratings.playerHistory[playerId].orEmpty()
            val history = points.map { it.value }
            // "Best heroes" = the Meta tab's ranking (Wilson lower bound, ≥ 3 games) and only
            // winning records: a 1–2 hero is never called "best". Otherwise: most played.
            val best = snapshot.meta.bestHeroesByPlayer[playerId].orEmpty()
                .filter { it.record.wins > it.record.losses }
                .sortedWith(
                    compareByDescending<PlayerHeroStat> { MetaStats.wilsonLowerBound(it.record.wins, it.record.games) }
                        .thenByDescending { it.record.games }
                        .thenBy { it.heroId },
                )
            fun row(heroId: String, record: WinLoss) =
                HeroRecordRow(heroId, catalog.heroName(heroId), catalog.hero(heroId)?.portrait, record)
            fun other(id: String, record: WinLoss) = OpponentRow(byId[id], byId[id]?.name ?: "Unknown player", record)
            val group = snapshot.group
            val rivals = group.headToHead.filter { it.ranked && (it.a == playerId || it.b == playerId) }
                .map { p ->
                    if (p.a ==
                        playerId
                    ) {
                        other(p.b, p.record)
                    } else {
                        other(p.a, WinLoss(p.record.games, p.record.losses))
                    }
                }
                .sortedByDescending { abs(it.record.wins - it.record.losses).toDouble() / it.record.games }
            val teammates = group.teammates.filter { it.ranked && (it.a == playerId || it.b == playerId) }
                .map { p -> other(if (p.a == playerId) p.b else p.a, p.record) }
            val heroes = if (best.isNotEmpty()) {
                best.take(MAX_HEROES).map { row(it.heroId, it.record) }
            } else {
                ratings.playerHeroRecords
                    .filterKeys { it.playerId == playerId }
                    .entries
                    .sortedWith(
                        compareByDescending<Map.Entry<*, WinLoss>> {
                            it.value.games
                        }.thenByDescending { it.value.wins },
                    )
                    .take(MAX_HEROES)
                    .map { row(it.key.heroId, it.value) }
            }
            val mine = matches
                .filter { m -> m.participants.any { it.playerId == playerId } }
                .sortedWith(compareByDescending<Match> { it.playedAt }.thenByDescending { it.createdAt })
            return PlayerDetailUiState(
                loading = false,
                player = player,
                rating = ratings.player(playerId),
                rank = ranked.indexOfFirst { it.id == playerId }.takeIf { it >= 0 }?.plus(1),
                rankedPlayers = ranked.size,
                initialRating = ratings.initialRating,
                history = history,
                historyDates = points.map { it.playedAt },
                winConditions = group.winConditionsByPlayer[playerId] ?: WinConditionMix.NONE,
                rivals = rivals.take(MAX_HEROES),
                teammates = teammates.take(MAX_HEROES),
                peak = history.maxOrNull(),
                doublePilotGames = ratings.doublePilotGames[playerId] ?: 0,
                heroes = heroes,
                heroesAreBest = best.isNotEmpty(),
                recentMatches = mine.take(RECENT_MATCHES).map { it.toRow(byId, catalog, ratings.matchChanges[it.id]) },
                totalMatches = mine.size,
                mergeCandidates = players.filter { it.id != playerId && !it.archived }
                    .sortedBy { it.name.lowercase() }
                    .map { other -> MergeCandidate(other, matches.count { other.id in it.playerIds }) },
            )
        }
    }
}
