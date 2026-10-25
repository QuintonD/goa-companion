package dev.quinton.goa.feature.players.meta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.domain.meta.DurationStat
import dev.quinton.goa.core.domain.meta.HeroMeta
import dev.quinton.goa.core.domain.meta.MetaConfig
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.meta.PlayerPairStat
import dev.quinton.goa.core.domain.meta.WinConditionMix
import dev.quinton.goa.core.domain.rating.WinLoss
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.feature.players.common.heroName
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.toJavaLocalDate

data class HeroMetaRow(
    val heroId: String,
    val name: String,
    val portrait: String?,
    val rating: Rating?,
    val meta: HeroMeta,
    val bestPlayer: Player?,
    val bestRecord: WinLoss?,
    /** How this hero's wins came. */
    val winConditions: WinConditionMix = WinConditionMix.NONE,
)

data class SynergyRow(val heroA: String, val heroB: String, val record: WinLoss, val wilsonLower: Double)

/** Two players: rivals (head-to-head, [record] is [a]'s) or teammates (their record together). */
data class PairRow(
    val a: Player?,
    val aName: String,
    val b: Player?,
    val bName: String,
    val record: WinLoss,
    val wilsonLower: Double,
)

data class MoverRow(val player: Player?, val name: String, val delta: Double)

data class StreakRow(val player: Player?, val name: String, val won: Boolean, val length: Int)

/** The latest evening at the table. */
data class GameNightUi(
    val date: LocalDate,
    val matches: Int,
    val redWins: Int,
    val blueWins: Int,
    val movers: List<MoverRow>,
)

/** Hero table view: compact sortable rows, or the full cards. */
enum class MetaView { TABLE, CARDS }

enum class HeroSort(val label: String) {
    RANK("Rank"),
    WIN_RATE("Win %"),
    GAMES("Games"),
    PICK("Pick %"),
    BAN("Ban %"),
    ELO("Hero Elo"),
}

data class MetaUiState(
    val loading: Boolean = true,
    val totalMatches: Int = 0,
    val draftsWithBans: Int = 0,
    val ranked: List<HeroMetaRow> = emptyList(),
    val unranked: List<HeroMetaRow> = emptyList(),
    val synergies: List<SynergyRow> = emptyList(),
    val largerTeam: WinLoss = WinLoss.NONE,
    val minSample: Int = MetaConfig().minSample,
    val view: MetaView = MetaView.TABLE,
    val sort: HeroSort = HeroSort.RANK,
    val night: GameNightUi? = null,
    val streaks: List<StreakRow> = emptyList(),
    val winConditions: WinConditionMix = WinConditionMix.NONE,
    /** Team Red's record (wins = Red won). */
    val red: WinLoss = WinLoss.NONE,
    val durationByMode: Map<GameMode, DurationStat> = emptyMap(),
    /** Ranked rivalries (≥ [minSample] games), most one-sided first. */
    val rivals: List<PairRow> = emptyList(),
    /** Ranked teammate pairs, best first. */
    val duos: List<PairRow> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && ranked.isEmpty() && unranked.isEmpty()

    /** Every hero with games or bans, in [sort] order (ranked heroes keep rank order for [HeroSort.RANK]). */
    val sortedHeroes: List<HeroMetaRow>
        get() {
            val all = ranked + unranked
            val order: Comparator<HeroMetaRow> = when (sort) {
                HeroSort.RANK -> return all
                HeroSort.WIN_RATE -> compareByDescending<HeroMetaRow> { it.meta.winRate ?: -1.0 }
                HeroSort.GAMES -> compareByDescending { it.meta.record.games }
                HeroSort.PICK -> compareByDescending { it.meta.pickRate }
                HeroSort.BAN -> compareByDescending { it.meta.banRate ?: -1.0 }
                HeroSort.ELO -> compareByDescending { it.rating?.value ?: Double.NEGATIVE_INFINITY }
            }
            return all.sortedWith(order.thenByDescending { it.meta.record.games }.thenBy { it.name })
        }
}

@HiltViewModel
class MetaViewModel @Inject constructor(
    ratings: RatingsSnapshotRepository,
    players: PlayerRepository,
    catalog: CatalogRepository,
) : ViewModel() {
    private val choice = MutableStateFlow(MetaView.TABLE to HeroSort.RANK)

    val uiState: StateFlow<MetaUiState> = combine(
        ratings.observeSnapshot(),
        players.observePlayers(includeArchived = true),
        catalog.catalog.onStart { emit(Catalog.EMPTY) },
        choice,
    ) { snapshot, people, cat, (view, sort) ->
        build(snapshot, people.associateBy { it.id }, cat).copy(view = view, sort = sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetaUiState())

    fun setView(view: MetaView) {
        choice.value = view to choice.value.second
    }

    fun setSort(sort: HeroSort) {
        choice.value = choice.value.first to sort
    }

    companion object {
        fun build(snapshot: RatingsSnapshot, players: Map<String, Player>, catalog: Catalog): MetaUiState {
            val meta = snapshot.meta
            val group = snapshot.group
            fun name(id: String) = players[id]?.name ?: "Unknown player"
            fun pair(
                p: PlayerPairStat,
            ) = PairRow(players[p.a], name(p.a), players[p.b], name(p.b), p.record, p.wilsonLower)
            val rows = meta.heroes.map { hero ->
                HeroMetaRow(
                    heroId = hero.heroId,
                    name = catalog.heroName(hero.heroId),
                    portrait = catalog.hero(hero.heroId)?.portrait,
                    rating = snapshot.ratings.heroes[hero.heroId],
                    meta = hero,
                    bestPlayer = hero.bestPlayer?.let { players[it.playerId] },
                    bestRecord = hero.bestPlayer?.record,
                    winConditions = group.winConditionsByHero[hero.heroId] ?: WinConditionMix.NONE,
                )
            }
            // Rivalries read best from the winning side: "Ann 5–1 Cid", ranked by how one-sided they are.
            val rivals = group.headToHead.filter { it.ranked }.map { p ->
                val flipped = p.copy(a = p.b, b = p.a, record = WinLoss(p.record.games, p.record.losses))
                val lead = if (p.record.wins >= p.record.losses) p else flipped
                lead.copy(wilsonLower = MetaStats.wilsonLowerBound(lead.record.wins, lead.record.games))
            }.sortedByDescending { it.wilsonLower }
            return MetaUiState(
                loading = false,
                totalMatches = meta.totalMatches,
                draftsWithBans = meta.draftsWithBans,
                ranked = rows.filter { it.meta.ranked },
                unranked = rows.filterNot { it.meta.ranked },
                synergies = meta.synergies.take(MAX_SYNERGIES).map {
                    SynergyRow(catalog.heroName(it.heroA), catalog.heroName(it.heroB), it.record, it.wilsonLower)
                },
                largerTeam = meta.largerTeam,
                night = group.lastNight?.let { night ->
                    GameNightUi(
                        date = night.date.toJavaLocalDate(),
                        matches = night.matchIds.size,
                        redWins = night.redWins,
                        blueWins = night.blueWins,
                        movers = night.movers.map { (id, delta) -> MoverRow(players[id], name(id), delta) },
                    )
                },
                streaks = group.streaks.take(MAX_LIST).map {
                    StreakRow(players[it.playerId], name(it.playerId), it.won, it.length)
                },
                winConditions = group.winConditions,
                red = group.red,
                durationByMode = group.durationByMode,
                rivals = rivals.take(MAX_LIST).map(::pair),
                duos = group.teammates.filter { it.ranked }.take(MAX_LIST).map(::pair),
            )
        }

        private const val MAX_SYNERGIES = 5
        private const val MAX_LIST = 5
    }
}
