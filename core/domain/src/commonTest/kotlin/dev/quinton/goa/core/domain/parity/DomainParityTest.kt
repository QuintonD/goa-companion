package dev.quinton.goa.core.domain.parity

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.draft.Seat
import dev.quinton.goa.core.domain.meta.GroupStats
import dev.quinton.goa.core.domain.meta.MetaStats
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncJson
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.SyncValidation
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.domain.timer.TimerJson
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaEngine
import dev.quinton.goa.core.domain.trivia.TriviaFacts
import dev.quinton.goa.core.domain.trivia.TriviaQuestion
import dev.quinton.goa.core.domain.trivia.TriviaSession
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.datetime.TimeZone

/**
 * The rules the web app runs give the Android app's answers (ADR-043): each test writes what an
 * engine produced as a transcript and checks its hash against the JVM's. A difference prints the
 * transcript; run the test on both targets and compare.
 */
class DomainParityTest {
    private val players = (1..7).map { "player-$it" }
    private val heroes = (1..12).map { "hero-$it" }

    /** A seeded match log: every mode, uneven teams, pilots on two seats, unknown heroes, skips. */
    private fun matchLog(seed: Int = 11): List<Match> {
        val rng = Random(seed)
        return (0 until 60).map { i ->
            val size = listOf(2, 3, 4, 5)[rng.nextInt(4)]
            val seats = players.shuffled(rng).take(size).toMutableList()
            if (rng.nextInt(8) == 0) seats += seats.first()
            val pool = heroes.shuffled(rng)
            val status = if (rng.nextInt(15) == 0) MatchStatus.ABANDONED else MatchStatus.COMPLETED
            val playedAt = 1_700_000_000_000L + i * 3_600_000L + rng.nextInt(3) * 86_400_000L
            Match(
                id = "m$i",
                createdAt = playedAt,
                playedAt = playedAt,
                status = status,
                mode = GameMode.entries[rng.nextInt(3)],
                winner = if (status == MatchStatus.COMPLETED) Team.entries[rng.nextInt(2)] else null,
                winCondition = WinCondition.entries.getOrNull(rng.nextInt(5)),
                durationMin = if (rng.nextBoolean()) 30 + rng.nextInt(60) else null,
                participants = seats.mapIndexed { s, p ->
                    Participant(p, if (rng.nextInt(10) == 0) null else pool[s], if (s % 2 == 0) Team.RED else Team.BLUE)
                },
            )
        }
    }

    private fun <K, V> Map<K, V>.lines(): String =
        entries.sortedBy { it.key.toString() }.joinToString("\n") { "${it.key} = ${canon(it.value)}" }

    @Test
    fun ratingsAndMetaStats() {
        val log = matchLog()
        val transcript = buildString {
            listOf(EloConfig.DEFAULT, EloConfig(heroInfluence = 0.25, seatAdvantage = 12.5, kProvisional = 32.0))
                .forEach { config ->
                    val r = EloCalculator.replay(log, config)
                    appendLine("players\n" + r.players.lines())
                    appendLine("heroes\n" + r.heroes.lines())
                    appendLine("playerHistory\n" + r.playerHistory.lines())
                    appendLine("heroHistory\n" + r.heroHistory.lines())
                    appendLine("records\n" + r.playerHeroRecords.lines())
                    appendLine("doublePilot\n" + r.doublePilotGames.lines())
                    appendLine("largerTeam ${canon(r.largerTeam)}")
                    appendLine("matchChanges\n" + r.matchChanges.lines())
                }
            val ratings = EloCalculator.replay(log, EloConfig.DEFAULT)
            val meta = MetaStats.compute(log, emptyList())
            appendLine("meta ${canon(meta.totalMatches)} ${canon(meta.largerTeam)}")
            meta.heroes.sortedBy { it.heroId }.forEach { appendLine(canon(it)) }
            meta.synergies.forEach { appendLine(canon(it)) }
            val group = GroupStats.compute(log, ratings, TimeZone.UTC)
            appendLine("conditions ${group.winConditions.counts.lines()}")
            appendLine("byHero\n" + group.winConditionsByHero.mapValues { it.value.counts.lines() }.lines())
            appendLine("red ${canon(group.red)} durations ${group.durationByMode.lines()}")
            group.headToHead.forEach { appendLine(canon(it)) }
            group.teammates.forEach { appendLine(canon(it)) }
            group.streaks.forEach { appendLine(canon(it)) }
            appendLine("night ${canon(group.lastNight)}")
        }
        assertTranscript(
            "1c91c6a659834c2512d96f0a0fdb60f2bdfab450e48d6407b0576657f591613b",
            transcript,
            "ratings and meta",
        )
    }

    @Test
    fun draftsOfEveryBundledMethod() {
        val methods = DraftJson.decodeMethods(catalogAsset("draft-methods.json")).methods
        val candidates = (1..40).map { "hero-$it" }
        val transcript = buildString {
            for (method in methods) {
                for ((red, blue) in listOf(2 to 2, 3 to 2, 4 to 4, 5 to 5)) {
                    val seats = (0 until red).map { Seat(Team.RED, it, "red-$it") } +
                        (0 until blue).map { Seat(Team.BLUE, it, "blue-$it") }
                    val setup = DraftSetup("d", 1L, method, seats, Team.BLUE, candidates, seed = 31L * red + blue)
                    var session = DraftEngine.start(setup).getOrElse {
                        appendLine("${method.id} $red-$blue: $it")
                        continue
                    }
                    val rng = Random(red * 10 + blue)
                    var guard = 0
                    while (!session.isComplete && guard++ < 100) {
                        val options = DraftEngine.available(session)
                        val next = DraftEngine.apply(session, DraftCommand(options[rng.nextInt(options.size)]))
                        session = next.getOrElse {
                            appendLine("  refused: $it")
                            break
                        }
                    }
                    appendLine("${method.id} $red-$blue pool=${session.pool}")
                    appendLine("  history=${canon(session.history)}")
                    appendLine("  assignments=${DraftEngine.assignments(session).map(::canon)}")
                }
            }
        }
        assertTranscript("ddf8166127b0a19289e80c09a4d6669f00d2fbeeaa2eccd6cfdec1bdd24d835c", transcript, "drafts")
    }

    private val catalog: Catalog by lazy { CatalogFiles.load { readRepoFile("app/src/main/assets/$it") } }

    @Test
    fun triviaWithAFixedSeed() {
        val ranges = TriviaFacts.valueRange(catalog)
        val pool = TriviaFacts.of(catalog, catalog.heroes.map { it.id })
        val rng = Random(2026)
        var session: TriviaSession = TriviaEngine.start(pool, ranges, emptyMap(), TriviaConfig(length = 40), rng)
        val transcript = buildString {
            appendLine("pool ${pool.size} ranges ${ranges.lines()}")
            var n = 0
            var now = 0L
            while (session.current != null && n < 40) {
                val q = session.current!!
                appendLine(canon(q))
                val answer = when (q) {
                    is TriviaQuestion.CardNumber -> TriviaAnswer.Number(
                        if (n % 3 ==
                            0
                        ) {
                            q.choices.first()
                        } else {
                            q.fact.value
                        },
                    )

                    is TriviaQuestion.HigherInitiative -> TriviaAnswer.Duel(
                        if (n % 2 ==
                            0
                        ) {
                            DuelPick.FIRST
                        } else {
                            DuelPick.TIE
                        },
                    )
                }
                val outcome = TriviaEngine.answer(session, answer, now)
                appendLine("  correct=${outcome.correct} updates=${outcome.updates.lines()}")
                session = TriviaEngine.next(outcome.session, rng)
                now += 4_000
                n++
            }
        }
        assertTranscript("78862da7304ed78025cab37362f4a371ba9a6df93e56f6929990fce0d8642eb9", transcript, "trivia")
    }

    private fun Random.document(): SyncDocument {
        val ids = (1..8).map { "p$it" }
        fun stamp() = nextLong(0, 6)
        return SyncDocument(
            players = ids.filter { nextInt(3) > 0 }.map {
                Player(
                    it,
                    "N${nextInt(3)}",
                    color = 0xFF3FC1B0,
                    avatarId = nextInt(4),
                    createdAt = 0,
                    updatedAt = stamp(),
                )
            },
            matches = matchLog(nextInt()).take(12).filter { nextInt(3) > 0 }
                .map { it.copy(updatedAt = stamp(), notes = "n${nextInt(3)}") },
            drafts = (1..4).filter { nextBoolean() }.map {
                DraftRecord(
                    "d$it",
                    0,
                    "house",
                    DraftMethodKind.PICK_AND_BAN,
                    listOf("hero-1", "hero-2"),
                    Team.RED,
                    listOf(DraftStepRecord(Team.RED, DraftAction.PICK, "hero-${nextInt(3)}")),
                    updatedAt = stamp(),
                )
            },
            triviaRounds = (1..5).filter { nextBoolean() }.map {
                TriviaRound(
                    "r$it", ids.random(this), TriviaRoundKind.CHALLENGE, playedAt = stamp(), length = 20,
                    questions = 20, correct = nextInt(21), bestStreak = 3, answerMillis = 60_000, heroCount = 30,
                    catalogVersion = 7, updatedAt = stamp(),
                )
            },
            deletions = (0 until 12).filter { nextInt(5) == 0 }.map { Deletion(DeletionKind.MATCH, "m$it", stamp()) },
            playerMerges = ids.filter { nextInt(6) == 0 }.map { PlayerMerge(it, ids.random(this), stamp()) },
            eloConfig = if (nextBoolean()) {
                StampedEloConfig.UNSET
            } else {
                StampedEloConfig(
                    EloConfig(heroInfluence = nextInt(0, 3) / 4.0, kProvisional = 30.0 + nextInt(3) * 5),
                    stamp(),
                )
            },
        )
    }

    @Test
    fun syncMergeOfRandomPhones() {
        val rng = Random(99)
        val transcript = buildString {
            repeat(30) { i ->
                val phones = List(3) { rng.document() }
                val merged = SyncMerge.mergeAll(phones)
                appendLine("$i ${SyncJson.encodeToString(SyncDocument.serializer(), merged)}")
                appendLine("  changes=${canon(SyncMerge.changes(phones[0], merged))}")
                appendLine("  problem=${SyncValidation.problem(merged)}")
                // Every order of merging gives the same document.
                check(SyncMerge.merge(phones[2], SyncMerge.merge(phones[1], phones[0])) == merged) { "order" }
            }
        }
        assertTranscript("c1bccda415c82803a03a79091b93903e7b10f19f4001c2a5b75bc6e6f931ce6b", transcript, "sync merge")
    }

    @Test
    fun timerAgainstAFakeClock() {
        val rules = MatchRules.parse(catalogAsset("match-setup.json"))
        val presets = TimerJson.parseCatalog(catalogAsset("timer-presets.json"))
        var time = 1_000_000L
        val clock = object : ElapsedClock, WallClock {
            override fun elapsedMillis() = time

            override fun nowMillis() = 1_700_000_000_000L + time
        }
        val engine = TimerEngine(clock, clock)
        val transcript = buildString {
            for (preset in presets.presets) {
                var s = engine.start(
                    "s",
                    preset,
                    rules.endOfRound,
                    4,
                    "m1",
                    mode = GameMode.QUICK,
                    tieBreaker = Team.RED,
                )
                repeat(25) { step ->
                    time += 7_000L * (step % 5)
                    s = when (step % 6) {
                        0 -> engine.pause(s)
                        1 -> engine.resume(s)
                        2 -> engine.back(s)
                        else -> engine.next(s)
                    }
                    val r = engine.readout(s)
                    appendLine("${preset.id} ${r.round} ${r.step.phase?.id} ${r.remainingMillis} ${s.status}")
                }
            }
        }
        assertTranscript("66a607801e7183e4b3d53dc0bb23ec76838b1b3c5b768f9a718fc813a880e510", transcript, "timer")
    }
}
