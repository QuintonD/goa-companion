package dev.quinton.goa.feature.timer

import dev.quinton.goa.core.datastore.timer.TimerStateStore
import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.timer.TimerJson
import dev.quinton.goa.core.domain.timer.TimerPresetCatalog
import dev.quinton.goa.core.domain.timer.TimerReadout
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.timer.data.TimerAssets
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.session.TimerController
import dev.quinton.goa.feature.timer.system.AlertStyle
import dev.quinton.goa.feature.timer.system.BootCountProvider
import dev.quinton.goa.feature.timer.system.CuePlayer
import dev.quinton.goa.feature.timer.system.TimerNotifier
import dev.quinton.goa.feature.timer.system.TimerScheduler
import dev.quinton.goa.feature.timer.ui.setup.CoinFlipper
import dev.quinton.goa.feature.timer.ui.timer.Ticker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Reads the shipped data file so tests exercise the real rules and presets. */
fun bundledAsset(name: String): String {
    val candidates = listOf("../../app/src/main/assets/catalog/$name", "app/src/main/assets/catalog/$name")
    return candidates.map(::File).first { it.exists() }.readText()
}

class FileTimerAssets : TimerAssets {
    private val rules = MatchRules.parse(bundledAsset("match-setup.json"))
    private val catalog = TimerJson.parseCatalog(bundledAsset("timer-presets.json"))

    override suspend fun rules(): MatchRules = rules

    override suspend fun presetCatalog(): TimerPresetCatalog = catalog
}

class FakeTimerStateStore : TimerStateStore {
    private val snapshot = MutableStateFlow<String?>(null)
    private val presets = MutableStateFlow<String?>(null)
    private val selected = MutableStateFlow<String?>(null)

    override val snapshotJson: Flow<String?> = snapshot
    override val presetsJson: Flow<String?> = presets
    override val selectedPresetId: Flow<String?> = selected.map { it }

    override suspend fun updateSnapshot(transform: (String?) -> String?): String? {
        val next = transform(snapshot.value)
        snapshot.value = next
        return next
    }

    override suspend fun writePresets(json: String?) {
        presets.value = json
    }

    override suspend fun writeSelectedPresetId(id: String?) {
        selected.value = id
    }
}

class FakeClocks(var elapsed: Long = 5_000_000L, var wall: Long = 1_760_000_000_000L) : ElapsedClock, WallClock {
    override fun elapsedMillis(): Long = elapsed

    override fun nowMillis(): Long = wall

    fun advance(millis: Long) {
        elapsed += millis
        wall += millis
    }
}

class FakeScheduler : TimerScheduler {
    var scheduledAt: Long? = null
    var stepKey: String? = null
    var cancels = 0

    override fun schedule(atElapsed: Long, stepKey: String) {
        scheduledAt = atElapsed
        this.stepKey = stepKey
    }

    override fun cancel() {
        scheduledAt = null
        stepKey = null
        cancels++
    }
}

class FakeNotifier : TimerNotifier {
    var ongoing: TimerReadout? = null
    val alerts = mutableListOf<String>()
    val styles = mutableListOf<AlertStyle>()

    /** The "Time's up" alert currently showing, if any. */
    var alertShowing: String? = null
    var enabled = true

    override fun canPost(): Boolean = enabled

    override fun showOngoing(snapshot: TimerSnapshot, readout: TimerReadout, wallNow: Long) {
        ongoing = readout
    }

    override fun cancelOngoing() {
        ongoing = null
    }

    override fun alertPhaseOver(snapshot: TimerSnapshot, endedTitle: String, style: AlertStyle) {
        alerts += endedTitle
        styles += style
        alertShowing = endedTitle
    }

    override fun cancelAlert() {
        alertShowing = null
    }
}

class FakeCuePlayer : CuePlayer {
    val cues = mutableListOf<Triple<Int, Boolean, Boolean>>()
    var taps = 0

    override fun cue(secondsLeft: Int, haptics: Boolean, sound: Boolean) {
        cues += Triple(secondsLeft, haptics, sound)
    }

    override fun tap(haptics: Boolean) {
        taps++
    }
}

class ManualTicker : Ticker {
    val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    override fun ticks(): Flow<Unit> = flow

    fun tick() {
        flow.tryEmit(Unit)
    }
}

class FixedCoin(var team: Team = Team.RED) : CoinFlipper {
    override fun flip(): Team = team
}

/** Everything a controller needs, wired with fakes. */
class TimerHarness(bootCount: Int? = 1) {
    val clocks = FakeClocks()
    val store = FakeTimerStateStore()
    val repository = TimerRepository(store, FileTimerAssets())
    val scheduler = FakeScheduler()
    val notifier = FakeNotifier()
    var boot: Int? = bootCount
    val matches = FakeMatchRepository()
    val settings = FakeSettingsRepository()
    val controller = TimerController(
        repository,
        clocks,
        clocks,
        scheduler,
        notifier,
        BootCountProvider { boot },
        { matches },
        { settings },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
