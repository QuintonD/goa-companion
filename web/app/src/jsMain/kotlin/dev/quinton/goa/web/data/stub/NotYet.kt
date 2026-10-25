package dev.quinton.goa.web.data.stub

/*
 * Stubs of the data contracts no stream has implemented on the web yet (ADR-043 "Data contracts,
 * F3"), one file per stream (W1ContentStubs, W2GroupStubs, W3DraftSetupPrefillStub; W4 has none left).
 * A stream replaces its stubs in its own holder (ContentData, GroupData, TableData, PlayData) and
 * deletes its stub file, so the four never edit the same lines. A stub fails only when used, never
 * when the app starts.
 */

/** Thrown by a stub: "[stream]: [contract] is not implemented on the web yet". */
internal fun notYet(stream: String, contract: String): Nothing =
    throw NotImplementedError("$stream: $contract is not implemented on the web yet")
