package dev.quinton.goa.web.trivia

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.data.trivia.TriviaJson
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.web.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Card-trivia progress on this device (ADR-028), the web's `DataStoreTriviaProgressRepository`:
 * three IndexedDB values under `trivia/` in the shared stored format ([TriviaJson], the phone's), so
 * the documents read the same on both. Never synced. Reading never fails: an unreadable document is
 * "no progress yet" / "no last setup", one unreadable entry only loses that entry.
 *
 * The documents are read once and then kept in memory; every write goes to IndexedDB first, under a
 * lock, so two quick answers never lose one another's update.
 */
class WebTriviaProgressRepository(
    private val store: KeyValueStore,
    private val catalogRepository: CatalogRepository,
) : TriviaProgressRepository {
    private val lock = Mutex()
    private var loaded = false
    private val progressJson = MutableStateFlow<String?>(null)
    private val configJson = MutableStateFlow<String?>(null)
    private val playingAsValue = MutableStateFlow<String?>(null)

    override val progress: Flow<Map<String, FactProgress>> =
        stored(progressJson).distinctUntilChanged().map(TriviaJson::decodeProgress)

    override val lastConfig: Flow<TriviaConfig?> =
        stored(configJson).distinctUntilChanged().map(TriviaJson::decodeConfig)

    override val playingAs: Flow<String?> = stored(playingAsValue).distinctUntilChanged()

    override suspend fun record(updates: Map<String, FactProgress>) {
        if (updates.isEmpty()) return
        edit(PROGRESS, progressJson) { stored ->
            TriviaJson.encodeProgress(TriviaJson.decodeProgress(stored) + updates)
        }
    }

    override suspend fun saveConfig(config: TriviaConfig) = edit(CONFIG, configJson) { TriviaJson.encodeConfig(config) }

    override suspend fun setPlayingAs(playerIdOrGuest: String) = edit(PLAYING_AS, playingAsValue) { playerIdOrGuest }

    override suspend fun reset(heroIds: Set<String>?) {
        if (heroIds == null) {
            edit(PROGRESS, progressJson) { null }
            return
        }
        val keys = TriviaFacts.keysOf(catalogRepository.catalog.first(), heroIds)
        if (keys.isEmpty()) return
        edit(PROGRESS, progressJson) { stored ->
            val kept = TriviaJson.decodeProgress(stored) - keys
            if (kept.isEmpty()) null else TriviaJson.encodeProgress(kept)
        }
    }

    private fun stored(value: MutableStateFlow<String?>): Flow<String?> = flow {
        load()
        emitAll(value)
    }

    private suspend fun load() {
        if (loaded) return
        lock.withLock { loadLocked() }
    }

    /** Call with [lock] held. */
    private suspend fun loadLocked() {
        if (loaded) return
        progressJson.value = store.get(PROGRESS)
        configJson.value = store.get(CONFIG)
        playingAsValue.value = store.get(PLAYING_AS)
        loaded = true
    }

    private suspend fun edit(key: String, value: MutableStateFlow<String?>, transform: (String?) -> String?) {
        lock.withLock {
            loadLocked()
            val next = transform(value.value)
            if (next == null) store.delete(key) else store.put(key, next)
            value.value = next
        }
    }

    private companion object {
        const val PROGRESS = "trivia/progress"
        const val CONFIG = "trivia/config"
        const val PLAYING_AS = "trivia/playingAs"
    }
}
