package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.datastore.rating.EloConfigDataSource
import dev.quinton.goa.core.domain.rating.EloConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Singleton
internal class DataStoreEloConfigRepository @Inject constructor(
    private val source: EloConfigDataSource,
) : EloConfigRepository {
    override val config: Flow<EloConfig> = source.json.map { decode(it) }.distinctUntilChanged()

    override suspend fun update(transform: (EloConfig) -> EloConfig) {
        val next = transform(config.first()).sanitized()
        source.write(if (next == EloConfig.DEFAULT) null else EloConfigJson.encodeToString(EloConfig.serializer(), next))
    }

    override suspend fun reset() = source.write(null)

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
