package dev.quinton.goa.feature.draft

import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftMethodsFile
import dev.quinton.goa.feature.draft.data.DraftEnvironment
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.data.DraftRepository
import dev.quinton.goa.feature.draft.data.MethodLibrary
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** The shipped methods file, read from the app's assets. */
val bundledMethods: DraftMethodsFile by lazy {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    var found: File? = null
    while (dir != null && found == null) {
        found = File(dir, "app/src/main/assets/catalog/draft-methods.json").takeIf { it.exists() }
        dir = dir.parentFile
    }
    DraftJson.decodeMethods(requireNotNull(found) { "draft-methods.json not found" }.readText())
}

class FakeDraftRepository(
    file: DraftMethodsFile = bundledMethods,
    custom: List<DraftMethod> = emptyList(),
    active: DraftProgress? = null,
) : DraftRepository {
    private val builtIn = file.methods
    private val defaultId = file.defaultMethodId
    val customState = MutableStateFlow(custom)
    val activeState = MutableStateFlow(active)

    override val methods: Flow<MethodLibrary> = customState.map { MethodLibrary(builtIn, it, defaultId) }
    override val active: Flow<DraftProgress?> = activeState

    override suspend fun saveActive(progress: DraftProgress?) {
        activeState.value = progress
    }

    override suspend fun saveCustomMethod(method: DraftMethod) {
        customState.value = customState.value.filterNot { it.id == method.id } + method
    }

    override suspend fun deleteCustomMethod(id: String) {
        customState.value = customState.value.filterNot { it.id == id }
    }
}

class FakeDraftEnvironment(private val seed: Long = 11L) : DraftEnvironment {
    private var next = 0

    override fun newId(): String = "id-${next++}"

    override fun newSeed(): Long = seed

    override fun nowMillis(): Long = 1_000L
}
