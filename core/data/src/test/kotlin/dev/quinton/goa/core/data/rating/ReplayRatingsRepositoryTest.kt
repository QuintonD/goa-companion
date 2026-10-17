package dev.quinton.goa.core.data.rating

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.data.MatchRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val LAG_MS = 100L

class ReplayRatingsRepositoryTest {
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
    ) = ReplayRatingsRepository(
        matchRepository = matches,
        draftRepository = drafts,
        configRepository = config,
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun recomputesWhenMatchesChange() = runTest {
        val matches = FakeMatchRepository()
        val repo = repository(matches)
        repo.observePlayerRatings().test {
            assertThat(awaitItem()).isEmpty()
            matches.upsert(match("m1", 1))
            assertThat(awaitItem().getValue("ann").value).isWithin(1e-9).of(1220.0)
            matches.delete("m1")
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun recomputesWhenConfigChanges() = runTest {
        val matches = FakeMatchRepository(listOf(match("m1", 1)))
        val config = FakeEloConfigRepository()
        val repo = repository(matches, config = config)
        repo.observeSnapshot().test {
            assertThat(awaitItem().ratings.player("ann").value).isWithin(1e-9).of(1220.0)
            config.update { it.copy(kProvisional = 10.0) }
            val next = awaitItem()
            assertThat(next.config.kProvisional).isEqualTo(10.0)
            assertThat(next.ratings.player("ann").value).isWithin(1e-9).of(1205.0)
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
        assertThat(repo.observePlayerHistory("ann").first().map { it.matchId }).containsExactly("m1", "m2").inOrder()
        assertThat(repo.observeHeroHistory("h2").first()).hasSize(2)
        val usage = repo.observeHeroUsage().first()
        assertThat(usage.getValue("h1").matches).isEqualTo(2)
        assertThat(usage.getValue("h9").banRate).isEqualTo(1.0)
        assertThat(repo.observeHeroRatings().first().keys).containsExactly("h1", "h2")
    }

    @Test
    fun ratingsAndMetaInEverySnapshotComeFromTheSameMatchList() = runTest {
        // Like Room, every subscription runs its own query, so two readers can see different states.
        val matches = LaggingMatchRepository(FakeMatchRepository(listOf(match("m1", 1))))
        val repo = repository(matches)
        repo.observeSnapshot().test {
            val first = awaitItem()
            assertThat(first.ratings.replayOrder).hasSize(1)
            assertThat(first.meta.totalMatches).isEqualTo(1)
            matches.upsert(match("m2", 2))
            matches.upsert(match("m3", 3, Team.BLUE))
            val seen = mutableListOf(first)
            while (seen.last().ratings.replayOrder.size < 3 || seen.last().meta.totalMatches < 3) seen += awaitItem()
            // Never N+1-match ratings next to N-match meta (or the other way round).
            seen.forEach { assertThat(it.meta.totalMatches).isEqualTo(it.ratings.replayOrder.size) }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(matches.readers).isEqualTo(1)
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
        assertThat(pilots).isEqualTo(repo.observeSnapshot().first().meta.pilots("h1"))
        assertThat(pilots.best.single().playerId).isEqualTo("ann")
        assertThat(pilots.best.single().record.games).isEqualTo(3)
        assertThat(repo.observeHeroPilots("nobody").first().all).isEmpty()
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
        assertThat(a).isSameInstanceAs(b)
    }
}
