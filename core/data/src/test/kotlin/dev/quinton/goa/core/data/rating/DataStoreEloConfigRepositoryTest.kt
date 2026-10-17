package dev.quinton.goa.core.data.rating

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.datastore.rating.EloConfigDataSource
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncMerge
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The group's rating constants carry their change time (ADR-025): a phone that never touched them
 * must never overwrite the group's, and a phone's own change must win the next sync.
 */
class DataStoreEloConfigRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 5_000L

    private fun repository(): Pair<EloConfigDataSource, DataStoreEloConfigRepository> {
        val store = PreferenceDataStoreFactory.create(produceFile = { File(tmp.newFolder(), "elo.preferences_pb") })
        val source = EloConfigDataSource(store)
        return source to DataStoreEloConfigRepository(source, EditClock { now })
    }

    @Test
    fun untouchedConstantsNeverBeatTheGroupsAndAnEditBeatsThem() = runTest {
        val (_, repo) = repository()
        assertThat(repo.stamped()).isEqualTo(StampedEloConfig.UNSET)
        val group = StampedEloConfig(EloConfig(kEstablished = 25.0), updatedAt = 3_000)
        val merged = SyncMerge.merge(SyncDocument(eloConfig = repo.stamped()), SyncDocument(eloConfig = group))
        assertThat(merged.eloConfig).isEqualTo(group)

        repo.applySynced(group)
        assertThat(repo.config.first()).isEqualTo(EloConfig(kEstablished = 25.0))
        assertThat(repo.stamped().updatedAt).isEqualTo(3_000) // kept, not restamped

        now = 1_000 // even with the clock behind, a later edit wins
        repo.update { it.copy(heroInfluence = 0.25) }
        assertThat(repo.stamped().updatedAt).isEqualTo(3_001)
        repo.reset()
        assertThat(repo.stamped()).isEqualTo(StampedEloConfig(EloConfig.DEFAULT, 3_002))
    }

    @Test
    fun constantsEditedBeforeSyncExistedStillBeatAnUntouchedPhone() = runTest {
        val (source, repo) = repository()
        source.write("""{"kEstablished":15.0}""") // stored by an older build: no change time
        assertThat(repo.stamped()).isEqualTo(StampedEloConfig(EloConfig(kEstablished = 15.0), updatedAt = 1))
        val merged = SyncMerge.merge(SyncDocument(eloConfig = repo.stamped()), SyncDocument())
        assertThat(merged.eloConfig.config).isEqualTo(EloConfig(kEstablished = 15.0))
    }
}
