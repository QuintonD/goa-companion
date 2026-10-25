package dev.quinton.goa.web.draft

import dev.quinton.goa.core.data.DraftSetupPrefill
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.data.DraftStorage
import dev.quinton.goa.feature.draft.data.MethodLibrary
import dev.quinton.goa.feature.draft.setup.SetupForm
import dev.quinton.goa.web.storage.KeyValueStore
import dev.quinton.goa.web.table.TableAssets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The draft on this device (the web app's Android `DraftRepository` over `DraftStore`): the bundled
 * and the group's own methods, the draft in progress and the setup form, in IndexedDB under
 * `table/draft/` in the phone's formats ([DraftStorage]). The draft in progress survives a reload,
 * Safari putting the page away and switching apps: every pick is written as it is made.
 */
class WebDraftRepository(private val store: KeyValueStore, private val assets: TableAssets) {
    private data class Loaded(
        val methods: MethodLibrary,
        val active: DraftProgress?,
        val form: SetupForm?,
    )

    private val lock = Mutex()
    private val state = MutableStateFlow<Loaded?>(null)
    private val failure = MutableStateFlow<Throwable?>(null)
    private val loaded: Flow<Loaded> = state.filterNotNull()

    /** Why loading failed (the bundled methods couldn't be read), or null. */
    val loadError: StateFlow<Throwable?> = failure.asStateFlow()

    val methods: Flow<MethodLibrary> = loaded.map { it.methods }

    val active: Flow<DraftProgress?> = loaded.map { it.active }

    /** The draft setup form as last edited (survives leaving the tab and a reload). */
    val setupForm: Flow<SetupForm?> = loaded.map { it.form }

    private val pushed = MutableSharedFlow<SetupForm>(extraBufferCapacity = 1)

    /** Forms pushed from outside (a rematch's "Draft again") to a draft setup that is already open. */
    val prefills: SharedFlow<SetupForm> = pushed

    /** Reads the store and the bundled methods once; call again after a failure. */
    suspend fun load() {
        if (state.value != null) return
        lock.withLock {
            if (state.value != null) return
            try {
                val file = assets.draftMethods()
                val custom = DraftStorage.decodeCustomMethods(store.get(CUSTOM))
                state.value = Loaded(
                    methods = MethodLibrary(file.methods, custom, file.defaultMethodId),
                    active = DraftStorage.decodeProgress(store.get(ACTIVE)),
                    form = DraftStorage.decodeSetupForm(store.get(FORM)),
                )
                failure.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                failure.value = e
            }
        }
    }

    private suspend fun current(): Loaded {
        load()
        return state.value ?: loaded.first()
    }

    suspend fun library(): MethodLibrary = current().methods

    suspend fun activeNow(): DraftProgress? = current().active

    suspend fun setupFormNow(): SetupForm? = current().form

    suspend fun saveActive(progress: DraftProgress?) {
        update { it.copy(active = progress) }
        if (progress == null) store.delete(ACTIVE) else store.put(ACTIVE, DraftStorage.encodeProgress(progress))
    }

    suspend fun saveSetupForm(form: SetupForm?) {
        update { it.copy(form = form) }
        if (form == null) store.delete(FORM) else store.put(FORM, DraftStorage.encodeSetupForm(form))
    }

    suspend fun saveCustomMethod(method: DraftMethod) {
        val updated = DraftStorage.upsert(current().methods.custom, method)
        update { it.copy(methods = it.methods.copy(custom = updated)) }
        store.put(CUSTOM, DraftStorage.encodeCustomMethods(updated))
    }

    suspend fun deleteCustomMethod(id: String) {
        val updated = current().methods.custom.filterNot { it.id == id }
        update { it.copy(methods = it.methods.copy(custom = updated)) }
        store.put(CUSTOM, DraftStorage.encodeCustomMethods(updated))
    }

    /** Tells an open draft setup about a form saved from elsewhere ("Draft again"). */
    fun push(form: SetupForm) {
        pushed.tryEmit(form)
    }

    private suspend fun update(transform: (Loaded) -> Loaded) {
        val s = current()
        state.value = transform(state.value ?: s)
    }

    private companion object {
        const val ACTIVE = "draft/active"
        const val FORM = "draft/setup"
        const val CUSTOM = "draft/methods"
    }
}

/**
 * "Draft again" with the same group (a rematch, W2's match screens): the saved setup form gets these
 * players and teams, keeping the method, pool and timer choices, so the Draft tab opens ready at the
 * Players step — the phone's `DefaultDraftSetupPrefill`.
 */
class WebDraftSetupPrefill(private val drafts: WebDraftRepository) : DraftSetupPrefill {
    override suspend fun prefill(red: List<String>, blue: List<String>, doublePilot: String?, handicap: Boolean) {
        val form = (drafts.setupFormNow() ?: SetupForm()).prefilled(red, blue, doublePilot, handicap)
        drafts.saveSetupForm(form)
        drafts.push(form)
    }
}
