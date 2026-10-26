package dev.quinton.goa.web.table

import dev.quinton.goa.core.domain.draft.DraftMethodsFile
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.timer.TimerJson
import dev.quinton.goa.core.domain.timer.TimerPresetCatalog
import dev.quinton.goa.feature.draft.data.DraftStorage
import dev.quinton.goa.web.data.Assets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The bundled rules data the table tools read: `catalog/match-setup.json` (counters, end of round,
 * Tie Breaker), `catalog/timer-presets.json` and `catalog/draft-methods.json` — the Android app's
 * own files, parsed by the same strict code (Android: `TimerAssets`, `BundledDraftMethods`). Read
 * once and kept; a failed read (offline before the service worker cached them) throws and is
 * tried again on the next call.
 */
class TableAssets(private val read: suspend (path: String) -> String = Assets::text) {
    private val lock = Mutex()
    private var rules: MatchRules? = null
    private var presets: TimerPresetCatalog? = null
    private var methods: DraftMethodsFile? = null

    suspend fun rules(): MatchRules = lock.withLock {
        rules ?: MatchRules.parse(read(MATCH_SETUP)).also { rules = it }
    }

    suspend fun timerPresets(): TimerPresetCatalog = lock.withLock {
        presets ?: TimerJson.parseCatalog(read(TIMER_PRESETS)).also { presets = it }
    }

    suspend fun draftMethods(): DraftMethodsFile = lock.withLock {
        methods ?: DraftStorage.bundled(read(DRAFT_METHODS)).also { methods = it }
    }

    companion object {
        const val MATCH_SETUP = "catalog/match-setup.json"
        const val TIMER_PRESETS = "catalog/timer-presets.json"
        const val DRAFT_METHODS = "catalog/draft-methods.json"
    }
}
