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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The group's rating constants with their change time (ADR-025): ratings must match on every
 * phone, so the constants travel with group sync and the later change wins.
 */
interface StampedEloConfigStore {
    suspend fun stamped(): StampedEloConfig

    /** Stores constants received from the group, keeping their change time (no new stamp). */
    suspend fun applySynced(config: StampedEloConfig)
}

@Singleton
internal class DataStoreEloConfigRepository @Inject constructor(
    private val source: EloConfigDataSource,
    private val clock: EditClock,
) : EloConfigRepository, StampedEloConfigStore {
    override val config: Flow<EloConfig> = source.json.map { decode(it) }.distinctUntilChanged()

    private val stampedFlow: Flow<StampedEloConfig> = combine(source.json, source.updatedAt) { json, at ->
        // Constants edited before sync existed have no stamp; they still beat a phone's untouched defaults.
        StampedEloConfig(decode(json), at ?: if (json != null) 1 else 0)
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
        // Defaults are stored as "nothing", so future default changes reach users who never edited them.
        val json = EloConfigJson.encodeToString(EloConfig.serializer(), config).takeIf { config != EloConfig.DEFAULT }
        source.write(json, updatedAt.takeIf { it > 0 })
    }

    private fun decode(json: String?): EloConfig = if (json == null) {
        EloConfig.DEFAULT
    } else {
        try {
            EloConfigJson.decodeFromString(EloConfig.serializer(), json).sanitized()
        } catch (_: SerializationException) {
            EloConfig.DEFAULT
        } catch (_: IllegalArgumentException) {
            EloConfig.DEFAULT
        }
    }
}

/** Lenient so configs written by newer versions (extra keys) still load. */
internal val EloConfigJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
