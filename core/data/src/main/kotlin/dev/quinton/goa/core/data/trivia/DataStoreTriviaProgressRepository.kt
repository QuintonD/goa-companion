package dev.quinton.goa.core.data.trivia

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.datastore.trivia.TriviaProgressStore
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Card-trivia progress (ADR-028): this phone's own practice record, in its own DataStore file.
 * Reading never fails: an unreadable document is "no progress yet" / "no last setup", and one
 * unreadable entry only loses that entry.
 *
 * The progress document holds up to ~2,000 entries, so decoding, merging and encoding it run on
 * [computeDispatcher], never on the caller's (main) thread. DataStore runs an edit's transform in
 * the caller's context, hence the `withContext` around each edit.
 */
@Singleton
internal class DataStoreTriviaProgressRepository internal constructor(
    private val store: TriviaProgressStore,
    private val catalogRepository: CatalogRepository,
    private val computeDispatcher: CoroutineDispatcher,
) : TriviaProgressRepository {
    @Inject
    constructor(store: TriviaProgressStore, catalogRepository: CatalogRepository) :
        this(store, catalogRepository, Dispatchers.Default)

    override val progress: Flow<Map<String, FactProgress>> =
        store.progressJson.distinctUntilChanged().map(TriviaJson::decodeProgress).flowOn(computeDispatcher)

    override val lastConfig: Flow<TriviaConfig?> = store.configJson.distinctUntilChanged().map(TriviaJson::decodeConfig)

    override suspend fun record(updates: Map<String, FactProgress>) {
        if (updates.isEmpty()) return
        withContext(computeDispatcher) {
            store.updateProgress { stored -> TriviaJson.encodeProgress(TriviaJson.decodeProgress(stored) + updates) }
        }
    }

    override suspend fun saveConfig(config: TriviaConfig) = store.writeConfig(TriviaJson.encodeConfig(config))

    override val playingAs: Flow<String?> = store.playingAs.distinctUntilChanged()

    override suspend fun setPlayingAs(playerIdOrGuest: String) = store.writePlayingAs(playerIdOrGuest)

    override suspend fun reset(heroIds: Set<String>?) {
        if (heroIds == null) {
            store.updateProgress { null }
            return
        }
        // The heroes' facts by their cards in the catalog: card ids need not start with the hero id.
        val keys = TriviaFacts.keysOf(catalogRepository.catalog.first(), heroIds)
        if (keys.isEmpty()) return
        withContext(computeDispatcher) {
            store.updateProgress { stored ->
                val kept = TriviaJson.decodeProgress(stored) - keys
                if (kept.isEmpty()) null else TriviaJson.encodeProgress(kept)
            }
        }
    }
}
