package dev.quinton.goa.core.domain.parity

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerJson
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.feature.draft.board.BoardUiState
import dev.quinton.goa.feature.draft.board.DraftBoardText
import dev.quinton.goa.feature.draft.board.PoolView
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.data.DraftStorage
import dev.quinton.goa.feature.draft.data.MethodLibrary
import dev.quinton.goa.feature.draft.methods.MethodEditUiState
import dev.quinton.goa.feature.draft.methods.PreviewLayout
import dev.quinton.goa.feature.draft.methods.previewOf
import dev.quinton.goa.feature.draft.setup.DraftSetupInputs
import dev.quinton.goa.feature.draft.setup.DraftSetupPresenter
import dev.quinton.goa.feature.draft.setup.SetupForm
import dev.quinton.goa.feature.draft.setup.SetupStage
import dev.quinton.goa.feature.draft.setup.TeamMode
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.ui.presets.PresetEdits
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupChoices
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupPresenter
import dev.quinton.goa.feature.timer.ui.timer.RunningTimerUi
import dev.quinton.goa.feature.timer.ui.timer.SeatInfo
import dev.quinton.goa.feature.timer.ui.timer.TimerUiMapper
import kotlin.random.Random
import kotlin.test.Test

/**
 * The table tools' words and numbers (ADR-043 "Table, W3"): the timer, Match setup and the draft
 * screens build their state with the shared presenters, so the web app shows exactly what the phone
 * shows. Each test writes what the presenters produced, from the shipped rules data, as a transcript
 * and checks its hash against the JVM's.
 */
class TablePresentationParityTest {
    private val rules by lazy { MatchRules.parse(catalogAsset("match-setup.json")) }
    private val presets by lazy { TimerJson.parseCatalog(catalogAsset("timer-presets.json")).presets }
    private val methods by lazy { DraftJson.decodeMethods(catalogAsset("draft-methods.json")) }
    private val catalog: Catalog by lazy { CatalogFiles.load { readRepoFile("app/src/main/assets/$it") } }

    private class Clock(var now: Long) : ElapsedClock, WallClock {
        override fun elapsedMillis() = now

        override fun nowMillis() = now + WALL_OFFSET
    }

    @Test
    fun theTimerSaysTheSameEverywhere() {
        val out = StringBuilder()
        val clock = Clock(1_000)
        val engine = TimerEngine(clock, clock)
        val seats = listOf(
            SeatInfo("Arien", "arien", null, Team.RED, "Ann"),
            SeatInfo("Brogan", "brogan", null, Team.BLUE, null),
            SeatInfo("Wasp", "wasp", null, Team.RED, "Cleo"),
            SeatInfo("Hero not picked", null, null, Team.BLUE, "Dev"),
        )
        presets.forEach { preset ->
            out.appendLine(
                "preset ${preset.id}: ${TimerText.presetSummary(preset)} | ${TimerUiMapper.presetOption(preset)}",
            )
            var s = engine.start(
                "session-${preset.id}",
                preset,
                rules.endOfRound,
                actionSlots = 4,
                tieBreaker = Team.BLUE,
            )
            repeat(s.plan.steps.size + 2) { i ->
                clock.now += 7_250L * (i % 5) + 333
                val r = engine.readout(s)
                val layout = if (i % 2 == 0) TimerLayout.STEPS else TimerLayout.ROUND
                val active = TimerUiMapper.active(s, r, seats.takeIf { i % 3 != 0 }, layout = layout)
                // Floats are display-only and not rounded to 32 bits on JS (F1): compare them in percent.
                out.appendLine(canon(active.copy(fraction = 0f, shotClock = active.shotClock?.copy(fraction = 0f))))
                out.appendLine(
                    "fraction ${percent(active.fraction)} shot ${active.shotClock?.fraction?.let(::percent)}",
                )
                out.appendLine(canon(RunningTimerUi.from(s, r)))
                out.appendLine(TimerText.nextText(s) + " | " + TimerText.endedTitle(s.step, s.round))
                s = if (i % 4 == 1) engine.heroResolves(s, i % 4) else engine.next(s)
            }
            clock.now += 3_600_000L * 2 + 42_000
            val ended = engine.endMatch(s)
            out.appendLine(canon(TimerUiMapper.summary(ended, engine.readout(ended))))
        }
        listOf(0L, 999L, 59_001L, 3_599_999L, 36_000_000L).forEach { ms ->
            out.appendLine(
                "${TimerText.duration(
                    ms,
                    roundUp = true,
                )} ${TimerText.duration(ms, roundUp = false)} ${TimerUiMapper.humanDuration(ms)}",
            )
        }
        val tonight = presets.first().let { dev.quinton.goa.feature.timer.data.TonightTurn.of(it, 45) }
        out.appendLine(canon(tonight?.applyTo(presets.first())))
        out.appendLine(canon(TimerUiMapper.start(presets, presets[1].id, emptyList(), 6, tonight)))
        val edited = PresetEdits.addPhase(
            PresetEdits.setSeconds(presets.first(), presets.first().phases.first().id, 20),
            dev.quinton.goa.core.domain.timer.PhaseScope.TURN,
            "new",
        )
        out.appendLine(canon(PresetEdits.move(edited, "new", -1)))
        out.appendLine(PresetEdits.validate(edited, rules.endOfRound).map { PresetEdits.issueText(it) }.toString())
        assertTranscript("2b9a38fc1bb84bde660220cc386659ff323b09b616bb026c37424171ca5b9da3", out.toString(), "timer")
    }

    @Test
    fun matchSetupSaysTheSameEverywhere() {
        val out = StringBuilder()
        val presetOptions = presets.map(TimerUiMapper::presetOption)
        for (players in 2..10) {
            for (mode in listOf<GameMode?>(null) + GameMode.entries) {
                val state = MatchSetupPresenter.build(
                    rules,
                    null,
                    MatchSetupChoices(players = players, mode = mode),
                    presetOptions,
                    presets.first().id,
                )
                out.appendLine(canon(state))
                out.appendLine(
                    "${state.coinMessage} | ${state.playersHint} | ${state.counters?.seatsLine} | " +
                        "${state.counters?.lifeNotSpecified}",
                )
            }
        }
        val match = Match(
            id = "m",
            playedAt = 0,
            createdAt = 0,
            mode = GameMode.LONG,
            status = MatchStatus.PENDING,
            participants = listOf(
                Participant("p1", "arien", Team.RED, handicap = true),
                Participant("p2", "wasp", Team.RED),
                Participant("p2", "brogan", Team.RED),
                Participant("p3", "tigerclaw", Team.BLUE),
                Participant("p4", null, Team.BLUE),
            ),
            tieBreaker = Team.RED,
            draftId = "d",
        )
        val linked = MatchSetupPresenter.build(rules, match, MatchSetupChoices(), presetOptions, presets.first().id)
        out.appendLine(canon(linked))
        out.appendLine(linked.coinMessage)
        out.appendLine(canon(MatchSetupPresenter.matchToSave(match, linked, isNew = false)))
        val pending = TimerUiMapper.pendingMatches(listOf(match), catalog) { "at $it" }
        out.appendLine(canon(pending))
        assertTranscript(
            "11c870be6966fb9e26a303aaab4899c5dd0c4539b984804975c10b24c60f7510",
            out.toString(),
            "match setup",
        )
    }

    @Test
    fun theDraftSaysTheSameEverywhere() {
        val out = StringBuilder()
        val library = MethodLibrary(methods.methods, emptyList(), methods.defaultMethodId)
        library.all.forEach { method ->
            PreviewLayout.OPTIONS.forEach { layout ->
                out.appendLine("${method.id} ${layout.label}: ${canon(previewOf(method, layout))}")
            }
            out.appendLine(
                canon(
                    MethodEditUiState.open(method, "custom-x").let {
                        listOf(it.toMethod(), it.error, it.poolValueLabel)
                    },
                ),
            )
        }
        val players = (1..7).map {
            Player(id = "p$it", name = "Player $it", color = 0xFF3FC1B0, avatarId = it, createdAt = it.toLong())
        }
        val inputs = DraftSetupInputs(players, emptyMap(), catalog, AppSettings(), library)
        for (n in 2..7) {
            for (teamMode in TeamMode.entries) {
                val form = SetupForm(
                    selected = players.take(n).map {
                        it.id
                    },
                    teamMode = teamMode,
                    teamA = Team.BLUE,
                    stage = SetupStage.HEROES,
                )
                    .withDefaultTeams()
                val state = DraftSetupPresenter.derive(inputs, form, null, null)
                out.appendLine(canon(state.copy(heroes = emptyList(), players = emptyList())))
                out.appendLine("${state.blocker} | ${state.primaryLabel} | ${state.soloPlayerName}")
            }
        }
        // A seeded draft through the shared engine: the board's words at every step.
        val form = SetupForm(
            selected = players.take(4).map {
                it.id
            },
            teamA = Team.RED,
            poolSource = PoolSource.ALL,
            stage = SetupStage.COIN,
        )
            .withDefaultTeams()
        library.all.forEach { method ->
            val state = DraftSetupPresenter.derive(inputs, form.copy(methodId = method.id), null, null)
            val started = DraftSetupPresenter.start(
                state,
                DraftSetupPresenter.candidates(inputs, state.form),
                "d-${method.id}",
                0,
                SEED,
            )
            out.appendLine("${method.id}: ${started?.exceptionOrNull()?.let(DraftSetupPresenter::errorMessage)}")
            var progress = started?.getOrNull() ?: return@forEach
            out.appendLine(DraftSetupPresenter.resumeInfo(progress).toString())
            val heroes = catalog.heroes.associate { it.id to HeroInfo.of(it) }
            val random = Random(SEED)
            repeat(MAX_STEPS) {
                val session = (progress as? DraftProgress.Board)?.session ?: return@repeat
                val board =
                    BoardUiState(
                        loading = false,
                        progress = progress,
                        heroes = heroes,
                        players = players.associateBy {
                            it.id
                        },
                    )
                out.appendLine(
                    "${DraftBoardText.bannerTitle(
                        session,
                    )} | ${DraftBoardText.bannerDetail(session, board::playerName)}",
                )
                out.appendLine(canon(PoolView.of(session)))
                val open = PoolView.of(session).open.filter {
                    it.state ==
                        dev.quinton.goa.feature.draft.ui.TileState.Available
                }
                if (session.isComplete || open.isEmpty()) return@repeat
                val pick = open[random.nextInt(open.size)].heroId
                out.appendLine(DraftBoardText.confirmText(session, "Pilot").orEmpty())
                progress = DraftProgress.Board(DraftEngine.apply(session, DraftCommand(pick)).getOrThrow())
            }
            out.appendLine(DraftStorage.encodeProgress(progress))
        }
        out.appendLine(DraftSetupPresenter.quickAddPlayer(" Zed ", 13, "id", 5).toString())
        assertTranscript("cdac15cb5d733353c643a534062c95f6c8c6b383951f8f1e661603eb6e3d9431", out.toString(), "draft")
    }

    private fun percent(f: Float): Int = kotlin.math.round(f.toDouble() * 100).toInt()

    private companion object {
        const val WALL_OFFSET = 1_760_000_000_000L
        const val SEED = 20_261_025L
        const val MAX_STEPS = 40
    }
}
