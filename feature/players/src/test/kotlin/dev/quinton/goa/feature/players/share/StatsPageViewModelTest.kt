package dev.quinton.goa.feature.players.share

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.backup.AppInfo
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.players.FakeRatingsSnapshotRepository
import dev.quinton.goa.feature.players.Fixtures
import dev.quinton.goa.feature.players.MainDispatcherRule
import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class StatsPageViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val temp = TemporaryFolder()

    private val matches = FakeMatchRepository(Fixtures.matches)
    private val written = mutableListOf<Pair<String, String>>()

    private fun vm(store: StatsPageStore) = StatsPageViewModel(
        players = FakePlayerRepository(Fixtures.players),
        matches = matches,
        ratings = FakeRatingsSnapshotRepository(matches, FakeDraftRecordRepository(Fixtures.drafts)),
        catalog = FakeCatalogRepository(),
        store = store,
        appInfo = AppInfo { "0.1.0" },
    ).apply {
        io = UnconfinedTestDispatcher()
        clock = { Fixtures.at(17, 12) }
    }

    @Test
    fun shareWritesTheRenderedPageAndAsksToShareIt() = runTest {
        val vm = vm { name, html ->
            written += name to html
            File(temp.root, name).apply { writeText(html) }
        }
        vm.share()
        val event = vm.events.first()

        assertThat(event).isInstanceOf(StatsPageEvent.Share::class.java)
        event as StatsPageEvent.Share
        assertThat(event.file.name).isEqualTo("goa-stats-2026-10-17.html")
        assertThat(event.title).isEqualTo("Guards of Atlantis stats · 2026-10-17")
        val (name, html) = written.single()
        assertThat(name).isEqualTo("goa-stats-2026-10-17.html")
        assertThat(event.file.readText()).isEqualTo(html)
        assertThat(html).contains("Guards of Atlantis Companion 0.1.0")
        // The leaderboard is the app's own: every ranked fixture player is on it.
        listOf("Ann", "Bob", "Cid", "Dee", "Eve").forEach { assertThat(html).contains("<th scope=\"row\">$it") }
        assertThat(html).doesNotContain("<th scope=\"row\">Fay") // archived, hidden like the Players tab
    }

    @Test
    fun aFailedWriteIsReportedInsteadOfShared() = runTest {
        val vm = vm { _, _ -> throw IOException("No space left on device") }
        vm.share()
        val event = vm.events.first()
        assertThat(event).isEqualTo(StatsPageEvent.Failed("Couldn't create the stats page: No space left on device"))
        // Not stuck: a second tap tries again.
        vm.share()
        assertThat(vm.events.first()).isInstanceOf(StatsPageEvent.Failed::class.java)
    }
}
