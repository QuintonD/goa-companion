package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.web.data.stub.W1RulesReferenceStub
import dev.quinton.goa.web.data.stub.W1TriviaProgressStub
import dev.quinton.goa.web.settings.WebSettingsRepository
import dev.quinton.goa.web.storage.scoped

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

    val settings: SettingsRepository = WebSettingsRepository(services.store.scoped("content/"))

    val triviaProgress: TriviaProgressRepository = W1TriviaProgressStub

    val rulesReference: RulesReferenceSource = W1RulesReferenceStub
}
