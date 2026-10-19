package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.domain.meta.HeroPilots
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.rating.RatingPoint
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RatingsScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RatingsDispatcher

/**
 * Ratings and meta stats derived by replaying the match log (ADR-003). The log arrives with merged
 * players resolved ([MatchRepository]); hero ids this catalog doesn't know count as Unknown hero.
 * Nothing is stored: the replay reruns off the main thread whenever matches, drafts or the Elo
 * config change, and the
 * latest snapshot is shared (memoised) by every collector for the life of the process.
 * A replay of 1,000 matches takes milliseconds (EloPropertiesTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class ReplayRatingsRepository @Inject constructor(
    matchRepository: MatchRepository,
    draftRepository: DraftRecordRepository,
    configRepository: EloConfigRepository,
    catalogRepository: CatalogRepository,
    @RatingsScope scope: CoroutineScope,
    @RatingsDispatcher dispatcher: CoroutineDispatcher,
) : RatingsRepository, RatingsSnapshotRepository, HeroPilotsRepository {
    /**
     * Matches, drafts and config are each read through exactly one subscription, and the replay
     * and meta stats are computed together from the same inputs, so a snapshot never pairs
     * ratings from one version of the match log with meta stats from another.
     */
    private val snapshot: Flow<RatingsSnapshot> = combine(
        matchRepository.observeMatches().distinctUntilChanged(),
        draftRepository.observeDrafts().distinctUntilChanged(),
        configRepository.config.distinctUntilChanged(),
        catalogRepository.catalog.map { c -> c.heroes.mapTo(HashSet()) { it.id } }.distinctUntilChanged(),
    ) { m, d, c, known -> Inputs(m, d, c, known) }
        .mapLatest { inputs ->
            // The Unknown-hero rule applies when reading: stored ids stay intact (ADR-025).
            RatingsSnapshot.compute(inputs.matches, inputs.drafts, inputs.config, inputs.knownHeroes)
        }
        .flowOn(dispatcher)
        .shareIn(scope, SharingStarted.Lazily, replay = 1)

    override fun observeSnapshot(): Flow<RatingsSnapshot> = snapshot

    override fun observePlayerRatings(): Flow<Map<String, Rating>> = snapshot.map {
        it.ratings.players
    }.distinctUntilChanged()

    override fun observeHeroRatings(): Flow<Map<String, Rating>> = snapshot.map {
        it.ratings.heroes
    }.distinctUntilChanged()

    override fun observePlayerHistory(playerId: String): Flow<List<RatingPoint>> =
        snapshot.map { it.ratings.playerHistory[playerId].orEmpty() }.distinctUntilChanged()

    override fun observeHeroHistory(heroId: String): Flow<List<RatingPoint>> =
        snapshot.map { it.ratings.heroHistory[heroId].orEmpty() }.distinctUntilChanged()

    override fun observeHeroUsage(): Flow<Map<String, HeroUsage>> = snapshot.map {
        it.meta.usage()
    }.distinctUntilChanged()

    override fun observeHeroPilots(heroId: String): Flow<HeroPilots> =
        snapshot.map { it.meta.pilots(heroId) }.distinctUntilChanged()

    private data class Inputs(
        val matches: List<Match>,
        val drafts: List<DraftRecord>,
        val config: EloConfig,
        val knownHeroes: Set<String>,
    )
}
