package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.web.storage.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The web's implementations of the data contracts (`:core:data-contracts`: the same interfaces the
 * phone's screens read through), created once in `AppServices.create()` and handed to screens as
 * `LocalServices.current.data` — the web's stand-in for Hilt's bindings (ADR-043 "Data contracts,
 * F3").
 *
 * One holder per stream, each in its own file that only that stream edits, so the four never touch
 * the same lines: [content] (W1, ContentData.kt), [group] (W2, GroupData.kt), [table] (W3,
 * TableData.kt), [play] (W4, PlayData.kt). Until a stream lands, its contracts are stubs
 * (`data/stub/`) that throw `NotImplementedError("Wn: …")` when used, never at start-up. Reach another
 * stream's contracts through these holders only (`services.content.catalog`); a holder may use the
 * ones created before it. The list with owners and files: `D:\.goa-qa\web\F3-contracts.md`.
 */
class WebServices(
    /** IndexedDB, one prefix per stream (`store.scoped("group/")`). */
    val store: KeyValueStore,
    /** Lives as long as the page: shared flows (the ratings snapshot) are kept here. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** Stamps `updatedAt` on every synced write (ADR-025): `Date.now()`. */
    val clock: EditClock = EditClock.System,
) {
    val content: ContentData = ContentData(this)

    val group: GroupData = GroupData(this)

    val table: TableData = TableData(this)

    val play: PlayData = PlayData(this)
}
