package dev.quinton.goa.core.data.stub

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.model.settings.AppSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** TEMPORARY stand-in until the real implementation lands. Owner deletes this file. */
@Singleton
internal class StubSettingsRepository @Inject constructor() : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface StubSettingsModule {
    @Binds fun bind(impl: StubSettingsRepository): SettingsRepository
}
