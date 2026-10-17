package dev.quinton.goa.core.data.settings

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.datastore.settings.SettingsDataSource
import dev.quinton.goa.core.model.settings.AppSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** [SettingsRepository] backed by Preferences DataStore (core:datastore). */
@Singleton
internal class DataStoreSettingsRepository @Inject constructor(private val source: SettingsDataSource) :
    SettingsRepository {
    override val settings: Flow<AppSettings> = source.settings

    override suspend fun update(transform: (AppSettings) -> AppSettings) = source.update(transform)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SettingsDataModule {
    @Binds fun bind(impl: DataStoreSettingsRepository): SettingsRepository
}
