package dev.quinton.goa.web.rules

import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.web.data.Assets
import dev.quinton.goa.web.data.RulesReferenceSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The bundled rules reference (`catalog/rules-reference.json`, the phone's own file), parsed once by
 * the phone's strict [RulesReference.parse]: the web's `AssetRulesReferenceSource`. A failed fetch
 * (offline before the service worker cached it) throws and is retried on the next [load].
 */
class AssetRulesReferenceSource(
    private val read: suspend (path: String) -> String = Assets::text,
) : RulesReferenceSource {
    private val lock = Mutex()
    private var cached: RulesReference? = null

    override suspend fun load(): RulesReference =
        cached ?: lock.withLock { cached ?: RulesReference.parse(read(RulesReference.PATH)).also { cached = it } }
}
