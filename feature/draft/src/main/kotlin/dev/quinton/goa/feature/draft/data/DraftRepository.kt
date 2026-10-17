package dev.quinton.goa.feature.draft.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.datastore.draft.BundledDraftMethods
import dev.quinton.goa.core.datastore.draft.DraftStore
import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftMethodsFile
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.feature.draft.setup.SetupForm
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** The in-progress draft: a hero board, or the official Player Draft. */
@Serializable
sealed interface DraftProgress {
    val id: String

    @Serializable
    @SerialName("board")
    data class Board(
        val session: DraftSession,
        /** The Player Draft this board was handed off from, so its last captain pick can be undone. */
        val playerDraft: PlayerDraftSession? = null,
    ) : DraftProgress {
        override val id: String get() = session.id
    }

    @Serializable
    @SerialName("players")
    data class Players(val session: PlayerDraftSession) : DraftProgress {
        override val id: String get() = session.id
    }
}

/** Bundled + custom methods. Built-ins come first, in file order. */
data class MethodLibrary(val builtIn: List<DraftMethod>, val custom: List<DraftMethod>, val defaultId: String) {
    val all: List<DraftMethod> get() = builtIn + custom

    fun find(id: String?): DraftMethod? = all.firstOrNull { it.id == id }

    val default: DraftMethod? get() = find(defaultId) ?: all.firstOrNull()

    companion object {
        val EMPTY = MethodLibrary(emptyList(), emptyList(), "")
    }
}

interface DraftRepository {
    val methods: Flow<MethodLibrary>

    val active: Flow<DraftProgress?>

    suspend fun saveActive(progress: DraftProgress?)

    /** The draft setup form as last edited (survives leaving the tab and process death). */
    val setupForm: Flow<SetupForm?>

    suspend fun saveSetupForm(form: SetupForm?)

    suspend fun saveCustomMethod(method: DraftMethod)

    suspend fun deleteCustomMethod(id: String)
}

@Singleton
internal class DefaultDraftRepository @Inject constructor(
    private val store: DraftStore,
    private val bundled: BundledDraftMethods,
) : DraftRepository {
    private val json = DraftJson.lenient
    private val listSerializer = ListSerializer(DraftMethod.serializer())

    private val bundledFile: Flow<DraftMethodsFile> = flow {
        emit(runCatching { DraftJson.decodeMethods(bundled.read()) }.getOrElse { DraftMethodsFile(0, "", emptyList()) })
    }

    override val methods: Flow<MethodLibrary> = combine(bundledFile, store.customMethods) { file, custom ->
        MethodLibrary(file.methods, decodeCustom(custom), file.defaultMethodId)
    }

    override val active: Flow<DraftProgress?> = store.activeDraft.map { text ->
        text?.let { runCatching { json.decodeFromString(DraftProgress.serializer(), it) }.getOrNull() }
    }

    override suspend fun saveActive(progress: DraftProgress?) {
        store.setActiveDraft(progress?.let { json.encodeToString(DraftProgress.serializer(), it) })
    }

    override val setupForm: Flow<SetupForm?> = store.setupForm.map { text ->
        text?.let { runCatching { json.decodeFromString(SetupForm.serializer(), it) }.getOrNull() }
    }

    override suspend fun saveSetupForm(form: SetupForm?) {
        store.setSetupForm(form?.let { json.encodeToString(SetupForm.serializer(), it) })
    }

    override suspend fun saveCustomMethod(method: DraftMethod) {
        val current = decodeCustom(store.customMethods.first())
        val updated = if (current.any { it.id == method.id }) {
            current.map { if (it.id == method.id) method else it }
        } else {
            current + method
        }
        store.setCustomMethods(json.encodeToString(listSerializer, updated))
    }

    override suspend fun deleteCustomMethod(id: String) {
        val updated = decodeCustom(store.customMethods.first()).filterNot { it.id == id }
        store.setCustomMethods(json.encodeToString(listSerializer, updated))
    }

    private fun decodeCustom(text: String?): List<DraftMethod> =
        text?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
            .map { it.copy(summary = DraftMethod.repairSummary(it.summary)) }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DraftDataModule {
    @Binds
    fun repository(impl: DefaultDraftRepository): DraftRepository
}
