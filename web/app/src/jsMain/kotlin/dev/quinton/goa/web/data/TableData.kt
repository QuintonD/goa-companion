package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.DraftSetupPrefill
import dev.quinton.goa.web.data.stub.W3DraftSetupPrefillStub

/**
 * W3 table (feature/web-table): the draft (setup form, draft in progress, custom methods) and the
 * phase timer (presets, the running timer), stored in [WebServices.store]. A finished draft goes
 * into the match log through [GroupData]. Owned by W3: only W3 edits this file ([WebServices]).
 */
@Suppress("UnusedPrivateProperty", "unused")
class TableData(private val services: WebServices) {
    val draftSetupPrefill: DraftSetupPrefill = W3DraftSetupPrefillStub
}
