package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.rating.EloCalculator
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
 * Ratings and meta stats derived by replaying the match log (ADR-003). Nothing is stored: the
 * replay reruns off the main thread whenever matches, drafts or the Elo config change, and the
 * latest snapshot is shared (memoised) by every collector for the life of the process.
 * A replay of 1,000 matches takes milliseconds (EloPropertiesTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class ReplayRatingsRepository @Inject constructor(
    matchRepository: MatchRepository,
    draftRepository: DraftRecordRepository,
    configRepository: EloConfigRepository,
    @RatingsScope scope: CoroutineScope,
    @RatingsDispatcher dispatcher: CoroutineDispatcher,
) : RatingsRepository, RatingsSnapshotRepository {
    private val matches = matchRepository.observeMatches().distinctUntilChanged()
    private val config = configRepository.config.distinctUntilChanged()

    private val ratings = combine(matches, config) { m, c -> m to c }
        .mapLatest { (m, c) -> EloCalculator.replay(m, c) to c }
        .flowOn(dispatcher)

    private val meta = combine(matches, draftRepository.observeDrafts().distinctUntilChanged()) { m, d -> m to d }
        .mapLatest { (m, d) -> MetaStats.compute(m, d) }
        .flowOn(dispatcher)

    // The config travels with the ratings it produced, so a snapshot is never half-updated.
    private val snapshot: Flow<RatingsSnapshot> = combine(ratings, meta) { (r, c), m -> RatingsSnapshot(r, m, c) }
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
}
