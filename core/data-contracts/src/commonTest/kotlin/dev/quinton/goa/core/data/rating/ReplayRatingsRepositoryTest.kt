package dev.quinton.goa.core.data.rating

import app.cash.turbine.test
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.TestCatalog
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private const val LAG_MS = 100L

/** Shared code: runs on the JVM and on Node (the web app replays the log with this same class). */
class ReplayRatingsRepositoryTest {
    private fun assertNear(expected: Double, actual: Double) =
        assertTrue(abs(expected - actual) < 1e-9, "expected $expected, was $actual")

    private fun match(id: String, playedAt: Long, winner: Team = Team.RED) = Match(
        id = id,
        createdAt = playedAt,
        playedAt = playedAt,
        status = MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = winner,
        participants = listOf(Participant("ann", "h1", Team.RED), Participant("bob", "h2", Team.BLUE)),
    )

    private fun TestScope.repository(
        matches: MatchRepository,
        drafts: FakeDraftRecordRepository = FakeDraftRecordRepository(),
        config: FakeEloConfigRepository = FakeEloConfigRepository(),
        catalog: Catalog = Catalog.EMPTY,
    ) = ReplayRatingsRepository(
        matchRepository = matches,
        draftRepository = drafts,
        configRepository = config,
        catalogRepository = FakeCatalogRepository(catalog),
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun recomputesWhenMatchesChange() = runTest {
        val matches = FakeMatchRepository()
        val repo = repository(matches)
        repo.observePlayerRatings().test {
            assertTrue(awaitItem().isEmpty())
            matches.upsert(match("m1", 1))
            assertNear(1220.0, awaitItem().getValue("ann").value)
            matches.delete("m1")
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun heroesThisCatalogDoesntKnowCountAsUnknownHeroesInRatingsAndBanStats() = runTest {
        // A newer phone logged "h-new"; this phone's catalog only knows h1 and h2.
        val catalog = TestCatalog.catalog.copy(
            heroes = TestCatalog.catalog.heroes.take(2).mapIndexed { i, h -> h.copy(id = "h${i + 1}") },
        )
        val logged = match("m1", 1).let { m ->
            m.copy(participants = m.participants + Participant("cid", "h-new", Team.RED))
        }
        val draft = DraftRecord(
            id = "d1",
            createdAt = 1,
            methodId = "house",
            methodKind = DraftMethodKind.CUSTOM,
            pool = listOf("h1", "h2", "h-new"),
            teamA = Team.RED,
            steps = listOf(DraftStepRecord(Team.BLUE, DraftAction.BAN, "h-new")),
        )
        val repo = repository(
            FakeMatchRepository(listOf(logged)),
            FakeDraftRecordRepository(listOf(draft)),
            catalog = catalog,
        )
        val snapshot = repo.observeSnapshot().first()
        assertEquals(setOf("h1", "h2"), snapshot.ratings.heroes.keys)
        assertEquals(setOf("ann", "bob", "cid"), snapshot.ratings.players.keys)
        assertFalse("h-new" in snapshot.meta.usage().keys)
    }

    @Test
    fun recomputesWhenConfigChanges() = runTest {
        val matches = FakeMatchRepository(listOf(match("m1", 1)))
        val config = FakeEloConfigRepository()
        val repo = repository(matches, config = config)
        repo.observeSnapshot().test {
            assertNear(1220.0, awaitItem().ratings.player("ann").value)
            config.update { it.copy(kProvisional = 10.0) }
            val next = awaitItem()
            assertEquals(10.0, next.config.kProvisional)
            assertNear(1205.0, next.ratings.player("ann").value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun historyAndUsageAreExposed() = runTest {
        val matches = FakeMatchRepository(listOf(match("m1", 1), match("m2", 2, Team.BLUE)))
        val drafts = FakeDraftRecordRepository(
            listOf(
                DraftRecord(
                    id = "d",
                    createdAt = 0,
                    methodId = "house",
                    methodKind = DraftMethodKind.CUSTOM,
                    pool = emptyList(),
                    teamA = Team.RED,
                    steps = listOf(DraftStepRecord(Team.RED, DraftAction.BAN, "h9")),
                ),
            ),
        )
        val repo = repository(matches, drafts)
        assertEquals(listOf("m1", "m2"), repo.observePlayerHistory("ann").first().map { it.matchId })
        assertEquals(2, repo.observeHeroHistory("h2").first().size)
        val usage = repo.observeHeroUsage().first()
        assertEquals(2, usage.getValue("h1").matches)
        assertEquals(1.0, usage.getValue("h9").banRate)
        assertEquals(setOf("h1", "h2"), repo.observeHeroRatings().first().keys)
    }

    @Test
    fun ratingsAndMetaInEverySnapshotComeFromTheSameMatchList() = runTest {
        // Like Room, every subscription runs its own query, so two readers can see different states.
        val matches = LaggingMatchRepository(FakeMatchRepository(listOf(match("m1", 1))))
        val repo = repository(matches)
        repo.observeSnapshot().test {
            val first = awaitItem()
            assertEquals(1, first.ratings.replayOrder.size)
            assertEquals(1, first.meta.totalMatches)
            matches.upsert(match("m2", 2))
            matches.upsert(match("m3", 3, Team.BLUE))
            val seen = mutableListOf(first)
            while (seen.last().ratings.replayOrder.size < 3 || seen.last().meta.totalMatches < 3) seen += awaitItem()
            // Never N+1-match ratings next to N-match meta (or the other way round).
            seen.forEach { assertEquals(it.ratings.replayOrder.size, it.meta.totalMatches) }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, matches.readers)
    }

    /** Each new collector sees updates [LAG_MS] later than the previous one. */
    private class LaggingMatchRepository(private val delegate: FakeMatchRepository) : MatchRepository by delegate {
        var readers = 0
            private set

        override fun observeMatches(): Flow<List<Match>> = flow {
            val lag = LAG_MS * readers++
            emitAll(delegate.observeMatches().onEach { delay(lag) })
        }
    }

    @Test
    fun heroPilotsComeFromTheSnapshotMeta() = runTest {
        val matches = FakeMatchRepository((1L..3L).map { match("m$it", it) })
        val repo = repository(matches)
        val pilots = repo.observeHeroPilots("h1").first()
        assertEquals(repo.observeSnapshot().first().meta.pilots("h1"), pilots)
        assertEquals("ann", pilots.best.single().playerId)
        assertEquals(3, pilots.best.single().record.games)
        assertTrue(repo.observeHeroPilots("nobody").first().all.isEmpty())
    }

    @Test
    fun snapshotIsSharedBetweenCollectors() = runTest {
        val repo =
            repository(
                FakeMatchRepository(listOf(match("m1", 1))),
                config = FakeEloConfigRepository(EloConfig(kProvisional = 30.0)),
            )
        val a = repo.observeSnapshot().first()
        val b = repo.observeSnapshot().first()
        assertSame(a, b)
    }
}
