package dev.quinton.goa.web.data.stub

import dev.quinton.goa.core.data.DraftSetupPrefill

// W3 table (feature/web-table) replaces these in TableData and deletes this file.

private const val W3 = "W3"

object W3DraftSetupPrefillStub : DraftSetupPrefill {
    override suspend fun prefill(red: List<String>, blue: List<String>, doublePilot: String?, handicap: Boolean) =
        notYet(W3, "DraftSetupPrefill")
}
