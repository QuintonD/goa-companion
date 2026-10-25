package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.web.catalog.CatalogBrowse
import dev.quinton.goa.web.rules.AssetRulesReferenceSource
import dev.quinton.goa.web.settings.WebSettingsRepository
import dev.quinton.goa.web.storage.scoped
import dev.quinton.goa.web.trivia.WebTriviaProgressRepository

/** The bundled rules reference (`catalog/rules-reference.json`), as on Android's feature:rules. */
fun interface RulesReferenceSource {
    suspend fun load(): RulesReference
}

/**
 * W1 content (feature/web-content): the hero catalog, settings, card-trivia progress and the rules
 * reference. Owned by W1: only W1 edits this file ([WebServices]).
 */
@Suppress("UnusedPrivateProperty", "unused")
class ContentData(private val services: WebServices) {
    val catalog: CatalogRepository = WebCatalogRepository()

    /** The Heroes tab's search index, search, filters and sort (page lifetime). */
    val browse: CatalogBrowse = CatalogBrowse(catalog)

    val settings: SettingsRepository = WebSettingsRepository(services.store.scoped("content/"))

    val triviaProgress: TriviaProgressRepository =
        WebTriviaProgressRepository(services.store.scoped("content/"), catalog)

    val rulesReference: RulesReferenceSource = AssetRulesReferenceSource()
}
