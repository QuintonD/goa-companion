package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.DraftSetupPrefill
import dev.quinton.goa.web.draft.WebDraftRepository
import dev.quinton.goa.web.draft.WebDraftSetupPrefill
import dev.quinton.goa.web.storage.scoped
import dev.quinton.goa.web.table.TableAssets
import dev.quinton.goa.web.timer.TablePrefsStore
import dev.quinton.goa.web.timer.TimerCues
import dev.quinton.goa.web.timer.WakeLock
import dev.quinton.goa.web.timer.WebTimerController
import dev.quinton.goa.web.timer.WebTimerRepository

/**
 * W3 table (feature/web-table): the draft (setup form, draft in progress, custom methods) and the
 * phase timer (presets, the running timer), stored in [WebServices.store] under `table/`. A finished
 * draft goes into the match log through [GroupData] (pending match + draft record), as on the phone.
 * Owned by W3: only W3 edits this file ([WebServices]).
 */
class TableData(private val services: WebServices) {
    private val store = services.store.scoped("table/")

    /** The bundled match-setup rules, timer presets and draft methods. */
    val assets: TableAssets = TableAssets()

    val timerStore: WebTimerRepository = WebTimerRepository(store, assets)

    /** Every timer change goes through here; the match log is read only when a linked session ends. */
    val timer: WebTimerController = WebTimerController(timerStore, matches = { services.group.matches })

    val drafts: WebDraftRepository = WebDraftRepository(store, assets)

    val draftSetupPrefill: DraftSetupPrefill = WebDraftSetupPrefill(drafts)

    /** Cue tones and the flash that stands in for vibration (created on first use: needs a browser). */
    val cues: TimerCues by lazy { TimerCues() }

    /** Screen Wake Lock while the timer runs. */
    val wakeLock: WakeLock by lazy { WakeLock(services.scope) }

    /** Keep-screen-on and the timer's dismissed notes (this device only). */
    val prefs: TablePrefsStore by lazy { TablePrefsStore(store, services.scope) }
}
