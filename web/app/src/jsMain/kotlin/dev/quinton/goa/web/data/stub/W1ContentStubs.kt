@file:Suppress("ktlint:standard:filename") // the rules stub goes when W1's rules reference lands

package dev.quinton.goa.web.data.stub

import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.web.data.RulesReferenceSource

// W1 content (feature/web-content) replaces these in ContentData and deletes this file.

private const val W1 = "W1"

object W1RulesReferenceStub : RulesReferenceSource {
    override suspend fun load(): RulesReference = notYet(W1, "RulesReferenceSource")
}
