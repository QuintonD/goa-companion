package dev.quinton.goa.feature.players.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.backup.AppInfo
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-shot results of "Share stats page". */
sealed interface StatsPageEvent {
    /** The page is written: open the share sheet for [file]. */
    data class Share(val file: File, val title: String) : StatsPageEvent

    data class Failed(val message: String) : StatsPageEvent

    companion object {
        const val MIME_TYPE = "text/html"
    }
}

/**
 * "Share stats page" (ADR-027): renders the current ratings, meta, group stats and recent matches
 * into one self-contained HTML file for friends who can't run the app, then asks the screen to
 * share it.
 */
@HiltViewModel
class StatsPageViewModel @Inject constructor(
    private val players: PlayerRepository,
    private val matches: MatchRepository,
    private val ratings: RatingsSnapshotRepository,
    private val catalog: CatalogRepository,
    private val store: StatsPageStore,
    private val appInfo: AppInfo,
) : ViewModel() {
    /** Overridable in tests. */
    internal var io: CoroutineDispatcher = Dispatchers.IO
    internal var clock: () -> Long = System::currentTimeMillis

    private val channel = Channel<StatsPageEvent>(Channel.BUFFERED)
    val events: Flow<StatsPageEvent> = channel.receiveAsFlow()

    private var working = false

    fun share() {
        if (working) return
        working = true
        viewModelScope.launch {
            val event = try {
                val people = players.observePlayers(includeArchived = true).first()
                val log = matches.observeMatches().first()
                val snapshot = ratings.observeSnapshot().first()
                val heroes = catalog.catalog.first()
                withContext(io) {
                    val page = StatsPage.build(people, log, snapshot, heroes, clock(), appInfo.versionName() ?: "dev")
                    val file = store.write("goa-stats-${page.generatedIsoDate}.html", StatsPageRenderer.render(page))
                    StatsPageEvent.Share(file, StatsPageRenderer.title(page))
                }
            } catch (e: IOException) {
                StatsPageEvent.Failed("Couldn't create the stats page: ${e.message}")
            } finally {
                working = false
            }
            channel.send(event)
        }
    }
}
