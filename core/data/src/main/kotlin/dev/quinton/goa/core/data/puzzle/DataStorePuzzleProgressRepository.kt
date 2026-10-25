package dev.quinton.goa.core.data.puzzle

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.core.datastore.puzzle.PuzzleProgressStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Puzzle progress in `datastore/puzzle_progress` (ADR-035), one JSON object of puzzle id to
 * [PuzzleProgress]. Reading never fails: a document that can't be decoded reads as no progress
 * (and the next write replaces it).
 */
@Singleton
internal class DataStorePuzzleProgressRepository @Inject constructor(
    private val store: PuzzleProgressStore,
) : PuzzleProgressRepository {
    override val progress: Flow<Map<String, PuzzleProgress>> =
        store.progressJson.map(PuzzleProgressJson::decode).distinctUntilChanged()

    override suspend fun update(puzzleId: String, transform: (PuzzleProgress) -> PuzzleProgress) {
        store.update { json ->
            val all = PuzzleProgressJson.decode(json)
            PuzzleProgressJson.encode(all + (puzzleId to transform(all[puzzleId] ?: PuzzleProgress())))
        }
    }

    override suspend fun reset() = store.update { null }
}

/** Puzzle progress (ADR-035; owner: board). */
@Module
@InstallIn(SingletonComponent::class)
internal interface PuzzleProgressModule {
    @Binds fun progress(impl: DataStorePuzzleProgressRepository): PuzzleProgressRepository
}
