package dev.quinton.goa.feature.draft.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.datastore.draft.BundledDraftMethods
import dev.quinton.goa.core.datastore.draft.DraftStore
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftMethodsFile
import dev.quinton.goa.feature.draft.setup.SetupForm
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

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
    private val bundledFile: Flow<DraftMethodsFile> = flow {
        emit(DraftStorage.bundled(runCatching { bundled.read() }.getOrNull()))
    }

    override val methods: Flow<MethodLibrary> = combine(bundledFile, store.customMethods) { file, custom ->
        MethodLibrary(file.methods, DraftStorage.decodeCustomMethods(custom), file.defaultMethodId)
    }

    override val active: Flow<DraftProgress?> = store.activeDraft.map(DraftStorage::decodeProgress)

    override suspend fun saveActive(progress: DraftProgress?) {
        store.setActiveDraft(progress?.let(DraftStorage::encodeProgress))
    }

    override val setupForm: Flow<SetupForm?> = store.setupForm.map(DraftStorage::decodeSetupForm)

    override suspend fun saveSetupForm(form: SetupForm?) {
        store.setSetupForm(form?.let(DraftStorage::encodeSetupForm))
    }

    override suspend fun saveCustomMethod(method: DraftMethod) {
        val current = DraftStorage.decodeCustomMethods(store.customMethods.first())
        store.setCustomMethods(DraftStorage.encodeCustomMethods(DraftStorage.upsert(current, method)))
    }

    override suspend fun deleteCustomMethod(id: String) {
        val updated = DraftStorage.decodeCustomMethods(store.customMethods.first()).filterNot { it.id == id }
        store.setCustomMethods(DraftStorage.encodeCustomMethods(updated))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DraftDataModule {
    @Binds
    fun repository(impl: DefaultDraftRepository): DraftRepository
}
