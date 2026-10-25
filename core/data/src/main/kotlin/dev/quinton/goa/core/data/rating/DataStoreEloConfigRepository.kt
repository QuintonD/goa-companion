package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.datastore.rating.EloConfigDataSource
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.EditStamp
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The rating constants in their own DataStore, stored as [EloConfigJson] writes them. */
@Singleton
internal class DataStoreEloConfigRepository @Inject constructor(
    private val source: EloConfigDataSource,
    private val clock: EditClock,
) : EloConfigRepository, StampedEloConfigStore {
    override val config: Flow<EloConfig> = source.json.map { EloConfigJson.decode(it) }.distinctUntilChanged()

    private val stampedFlow: Flow<StampedEloConfig> = combine(source.json, source.updatedAt) { json, at ->
        // Constants edited before sync existed have no stamp; they still beat a phone's untouched defaults.
        StampedEloConfig(EloConfigJson.decode(json), at ?: if (json != null) 1 else 0)
    }

    override suspend fun update(transform: (EloConfig) -> EloConfig) {
        val current = stampedFlow.first()
        val next = transform(current.config).sanitized()
        write(next, EditStamp.next(clock.now(), current.updatedAt))
    }

    override suspend fun reset() {
        write(EloConfig.DEFAULT, EditStamp.next(clock.now(), stampedFlow.first().updatedAt))
    }

    override suspend fun stamped(): StampedEloConfig = stampedFlow.first()

    override suspend fun applySynced(config: StampedEloConfig) = write(config.config.sanitized(), config.updatedAt)

    private suspend fun write(config: EloConfig, updatedAt: Long) {
        source.write(EloConfigJson.encode(config), updatedAt.takeIf { it > 0 })
    }
}
