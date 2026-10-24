package dev.quinton.goa.core.data.game

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.datastore.game.GameStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The game in progress in `datastore/games` (ADR-035), one JSON document. Reading never fails: a
 * document that can't be decoded reads as [StoredGame.Unreadable]. Encoding and decoding run on
 * [computeDispatcher] (a long game's answers are a few KB).
 */
@Singleton
internal class DataStoreGameSaveRepository internal constructor(
    private val store: GameStore,
    private val computeDispatcher: CoroutineDispatcher,
) : GameSaveRepository {
    @Inject
    constructor(store: GameStore) : this(store, Dispatchers.Default)

    override val current: Flow<StoredGame> = store.current
        .distinctUntilChanged()
        .map { json -> if (json == null) StoredGame.None else GameJson.decode(json) }
        .flowOn(computeDispatcher)

    override suspend fun save(game: GameInProgress) {
        val json = withContext(computeDispatcher) { GameJson.encode(game) }
        store.write(json)
    }

    override suspend fun clear() = store.clear()
}

/** The stored document. Lenient, so a newer build's extra fields don't lose the game. */
internal object GameJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(game: GameInProgress): String = json.encodeToString(GameInProgress.serializer(), game)

    fun decode(stored: String): StoredGame = try {
        StoredGame.Saved(json.decodeFromString(GameInProgress.serializer(), stored))
    } catch (_: SerializationException) {
        StoredGame.Unreadable
    } catch (_: IllegalArgumentException) {
        StoredGame.Unreadable
    }
}

/** Saved games against the computer (ADR-035; owner: board). */
@Module
@InstallIn(SingletonComponent::class)
internal interface GameSaveModule {
    @Binds fun games(impl: DataStoreGameSaveRepository): GameSaveRepository
}
