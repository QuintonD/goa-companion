package dev.quinton.goa.core.data.stub

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.model.catalog.Catalog
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** TEMPORARY stand-in until the real implementation lands. Owner deletes this file. */
internal class StubCatalogRepository @Inject constructor() : CatalogRepository {
    override val catalog: Flow<Catalog> = flowOf(Catalog.EMPTY)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface StubCatalogModule {
    @Binds fun bind(impl: StubCatalogRepository): CatalogRepository
}
