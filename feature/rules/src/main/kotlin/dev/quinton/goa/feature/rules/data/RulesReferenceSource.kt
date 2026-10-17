package dev.quinton.goa.feature.rules.data

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.model.rules.RulesReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The bundled rules reference (`assets/catalog/rules-reference.json`), parsed once. */
fun interface RulesReferenceSource {
    suspend fun load(): RulesReference
}

@Singleton
class AssetRulesReferenceSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : RulesReferenceSource {
    private val mutex = Mutex()
    private var cached: RulesReference? = null

    override suspend fun load(): RulesReference = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            RulesReference.parse(context.assets.open(RulesReference.PATH).bufferedReader().use { it.readText() })
        }.also { cached = it }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface RulesDataModule {
    @Binds
    fun bindRulesReferenceSource(impl: AssetRulesReferenceSource): RulesReferenceSource
}
