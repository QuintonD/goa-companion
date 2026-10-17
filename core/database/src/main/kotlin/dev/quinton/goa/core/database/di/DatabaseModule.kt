package dev.quinton.goa.core.database.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.database.dao.DeletionDao
import dev.quinton.goa.core.database.dao.DraftRecordDao
import dev.quinton.goa.core.database.dao.MatchDao
import dev.quinton.goa.core.database.dao.PlayerDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GoaDatabase = GoaDatabase.create(context)

    @Provides fun playerDao(db: GoaDatabase): PlayerDao = db.playerDao()

    @Provides fun matchDao(db: GoaDatabase): MatchDao = db.matchDao()

    @Provides fun draftRecordDao(db: GoaDatabase): DraftRecordDao = db.draftRecordDao()

    @Provides fun deletionDao(db: GoaDatabase): DeletionDao = db.deletionDao()
}
