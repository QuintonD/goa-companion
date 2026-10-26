package dev.quinton.goa.web.table

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.setup.DraftSetupInputs
import dev.quinton.goa.feature.draft.setup.DraftSetupPresenter
import dev.quinton.goa.feature.draft.setup.SetupForm
import dev.quinton.goa.feature.draft.setup.SetupStage
import dev.quinton.goa.feature.draft.setup.TeamMode
import dev.quinton.goa.feature.timer.session.TimerCommand
import dev.quinton.goa.web.draft.WebDraftRepository
import dev.quinton.goa.web.draft.WebDraftSetupPrefill
import dev.quinton.goa.web.storage.KeyValueStore
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import dev.quinton.goa.web.timer.WebTimerController
import dev.quinton.goa.web.timer.WebTimerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

/** The site serves the Android app's assets (`app/src/main/assets`) under the same paths. */
private fun asset(path: String): String {
    val root = js("process.env.GOA_REPO_ROOT") as String? ?: error("GOA_REPO_ROOT is not set")
    val fs = js("process.getBuiltinModule('fs')") // the tests run as ES modules (es2015 target): no require
    return fs.readFileSync("$root/app/src/main/assets/$path", "utf8") as String
}

private class TestClock(var now: Long = 1_000_000L) : ElapsedClock, WallClock {
    override fun elapsedMillis() = now

    override fun nowMillis() = now
}

/**
 * The table tools' storage on the web (W3): what a player would lose if it were wrong — a running
 * timer or a draft half-way through after a reload, the iPhone locking or Safari putting the page
 * away. "A reload" here is a new repository over the same IndexedDB store.
 */
class TableDataTest {
    private val assets = TableAssets { asset(it) }

    private fun timer(store: KeyValueStore, clock: TestClock, matches: FakeMatchRepository = FakeMatchRepository()) =
        WebTimerRepository(store, assets).let { it to WebTimerController(it, { matches }, clock, clock) }

    @Test
    fun theBundledPresetsSeedTheListAndAChoiceSurvivesAReload() = runTest {
        val store = MemoryKeyValueStore()
        val (repository, _) = timer(store, TestClock())
        repository.load()
        val presets = repository.presets.first()
        val catalog = assets.timerPresets()
        assertEquals(catalog.presets.map { it.id }, presets.map { it.id })
        assertEquals(catalog.defaultPresetId, repository.selectedPresetId.first())

        repository.select(presets.last().id)
        repository.save(presets.first().copy(name = "Our rules"))

        val (reloaded, _) = timer(store, TestClock())
        reloaded.load()
        assertEquals(presets.last().id, reloaded.selectedPresetId.first())
        assertEquals("Our rules", reloaded.presets.first().first().name)
    }

    @Test
    fun theLastPresetCantBeDeletedAndDeletedDefaultsComeBack() = runTest {
        val (repository, _) = timer(MemoryKeyValueStore(), TestClock())
        repository.load()
        val ids = repository.presets.first().map { it.id }
        ids.drop(1).forEach { assertTrue(repository.delete(it)) }
        assertFalse(repository.delete(ids.first()), "the last preset stays")
        repository.restoreDefaults()
        assertEquals(ids.toSet(), repository.presets.first().map { it.id }.toSet())
    }

    @Test
    fun aRunningTimerKeepsCountingThroughAReload() = runTest {
        val store = MemoryKeyValueStore()
        val clock = TestClock()
        val (repository, controller) = timer(store, clock)
        repository.load()
        controller.start(repository.selectedPreset(), actionSlots = 4)
        clock.now += 42_000

        // The page is reloaded (or Safari brings it back after the phone was locked).
        val (again, reloaded) = timer(store, clock)
        again.load()
        val session = assertNotNull(reloaded.snapshot.first())
        assertEquals(TimerStatus.RUNNING, session.status)
        assertEquals(42_000, reloaded.readout(session).elapsedInStepMillis)
    }

    @Test
    fun aPausedTimerStaysPausedThroughAReload() = runTest {
        val store = MemoryKeyValueStore()
        val clock = TestClock()
        val (repository, controller) = timer(store, clock)
        repository.load()
        controller.start(repository.selectedPreset(), actionSlots = 4)
        clock.now += 5_000
        controller.perform(TimerCommand.Pause)
        clock.now += 60_000

        val (again, reloaded) = timer(store, clock)
        again.load()
        val session = assertNotNull(reloaded.snapshot.first())
        assertEquals(TimerStatus.PAUSED, session.status)
        assertEquals(5_000, reloaded.readout(session).elapsedInStepMillis)
    }

    @Test
    fun autoAdvanceCatchesUpOnPhasesThatEndedWhileThePhoneWasLocked() = runTest {
        val clock = TestClock()
        val (repository, controller) = timer(MemoryKeyValueStore(), clock)
        repository.load()
        val preset = repository.selectedPreset().copy(autoAdvance = true)
        val start = controller.start(preset, actionSlots = 4)
        val first = assertNotNull(start.step.durationMillis)

        clock.now += first + 1_000 // the first phase ended unseen
        assertTrue(controller.resync(), "says a phase ended while the screen was off")
        val session = assertNotNull(controller.snapshot.first())
        assertEquals(1, session.stepIndex)
        assertEquals(
            1_000,
            controller.readout(session).elapsedInStepMillis,
            "the next phase started when the first ended",
        )
        assertFalse(controller.resync(), "nothing more to catch up")
    }

    @Test
    fun endingALinkedSessionWritesItsDurationAndModeIntoTheMatch() = runTest {
        val clock = TestClock()
        val match = Match(
            id = "m1",
            createdAt = 0,
            playedAt = 0,
            status = MatchStatus.PENDING,
            mode = GameMode.QUICK,
            participants = listOf(Participant("p1", "arien", Team.RED), Participant("p2", "brogan", Team.BLUE)),
        )
        val matches = FakeMatchRepository(listOf(match))
        val (repository, controller) = timer(MemoryKeyValueStore(), clock, matches)
        repository.load()
        controller.start(repository.selectedPreset(), actionSlots = 2, matchId = "m1", mode = GameMode.LONG)
        clock.now += 47 * 60_000L + 20_000
        controller.perform(TimerCommand.EndMatch)

        val saved = assertNotNull(matches.observeMatch("m1").first())
        assertEquals(47, saved.durationMin)
        assertEquals(GameMode.LONG, saved.mode)
    }

    @Test
    fun logResultReservesOneMatchIdAndClearsTheSessionOnceThatMatchIsSaved() = runTest {
        val clock = TestClock()
        val (repository, controller) = timer(MemoryKeyValueStore(), clock)
        repository.load()
        controller.start(repository.selectedPreset(), actionSlots = 4)
        controller.perform(TimerCommand.EndMatch)

        val id = assertNotNull(controller.reserveLoggingMatchId())
        assertEquals(id, controller.reserveLoggingMatchId(), "every Log result tap opens the same match")
        controller.clearLogged("another")
        assertNotNull(controller.snapshot.first(), "a different match leaves the summary")
        controller.clearLogged(id)
        assertNull(controller.snapshot.first())
    }

    @Test
    fun tonightsTurnLengthAppliesToTheNextStartAndSurvivesAReload() = runTest {
        val store = MemoryKeyValueStore()
        val clock = TestClock()
        val (repository, _) = timer(store, clock)
        repository.load()
        val preset = repository.selectedPreset()
        repository.setTonightTurn(preset.id, 45)

        val (reloaded, controller) = timer(store, clock)
        reloaded.load()
        val session = controller.start(reloaded.selectedPreset(), actionSlots = 4)
        val turn = session.preset.phases.first { it.kind == dev.quinton.goa.core.domain.timer.PhaseKind.TURN }
        assertEquals(45, turn.seconds)
        assertEquals(preset, reloaded.selectedPreset(), "the preset itself is unchanged")
    }

    @Test
    fun aDraftInProgressAndTheSetupFormSurviveAReload() = runTest {
        val store = MemoryKeyValueStore()
        val drafts = WebDraftRepository(store, assets)
        drafts.load()
        val library = drafts.library()
        val catalog = dev.quinton.goa.core.model.catalog.CatalogFiles.load { asset(it) }
        val form = SetupForm(
            selected = listOf("a", "b", "c", "d"),
            teamMode = TeamMode.MANUAL,
            teamA = Team.RED,
            stage = SetupStage.COIN,
        )
            .withDefaultTeams()
        val inputs =
            DraftSetupInputs(
                emptyList(),
                emptyMap(),
                catalog,
                dev.quinton.goa.core.model.settings.AppSettings(),
                library,
            )
        val state = DraftSetupPresenter.derive(inputs, form, null, null)
        val progress = assertNotNull(
            DraftSetupPresenter.start(state, DraftSetupPresenter.candidates(inputs, state.form), "d1", 0, 7),
        ).getOrThrow()
        drafts.saveActive(progress)
        drafts.saveSetupForm(state.form.forNextGame())

        val reloaded = WebDraftRepository(store, assets)
        reloaded.load()
        assertEquals(progress, reloaded.activeNow())
        assertIs<DraftProgress.Board>(reloaded.activeNow())
        assertEquals(state.form.forNextGame(), reloaded.setupFormNow())
    }

    @Test
    fun theGroupsOwnMethodsAreKeptAfterTheBuiltInOnes() = runTest {
        val store = MemoryKeyValueStore()
        val drafts = WebDraftRepository(store, assets)
        val builtIn = drafts.library().builtIn
        val copy = builtIn.first().copy(
            id = "custom-1",
            name = "Ours",
            origin = dev.quinton.goa.core.domain.draft.MethodOrigin.CUSTOM,
        )
        drafts.saveCustomMethod(copy)

        val reloaded = WebDraftRepository(store, assets)
        assertEquals(builtIn.map { it.id } + "custom-1", reloaded.library().all.map { it.id })
        reloaded.deleteCustomMethod("custom-1")
        assertEquals(builtIn.map { it.id }, WebDraftRepository(store, assets).library().all.map { it.id })
    }

    @Test
    fun draftAgainFillsTheSetupWithTheRematchsTeamsAndTellsAnOpenSetup() = runTest {
        val store = MemoryKeyValueStore()
        val drafts = WebDraftRepository(store, assets)
        drafts.saveSetupForm(SetupForm(methodId = "pick-and-ban", stepTimer = true, selected = listOf("x")))
        val pushed = mutableListOf<SetupForm>()
        val listening = launch(start = CoroutineStart.UNDISPATCHED) { drafts.prefills.collect { pushed += it } }

        WebDraftSetupPrefill(drafts).prefill(listOf("a", "b"), listOf("c"), doublePilot = "c", handicap = false)
        testScheduler.advanceUntilIdle()
        listening.cancel()

        val form = assertNotNull(drafts.setupFormNow())
        assertEquals(listOf("a", "b", "c"), form.selected)
        assertEquals("pick-and-ban", form.methodId, "the method stays")
        assertTrue(form.stepTimer, "the timer choice stays")
        assertTrue(form.lastGame)
        assertEquals(listOf(form), pushed, "an open draft setup gets the form at once")
    }
}
