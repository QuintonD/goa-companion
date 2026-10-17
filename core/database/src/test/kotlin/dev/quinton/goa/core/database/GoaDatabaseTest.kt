package dev.quinton.goa.core.database

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoaDatabaseTest {
    private lateinit var db: GoaDatabase

    @Before
    fun setUp() {
        db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
    }

    @After
    fun tearDown() = db.close()

    private val ann = Player("p-ann", "Ann", 0xFF3FC1B0, avatarId = 3, createdAt = 10)
    private val bob = Player("p-bob", "bob", 0xFFE26459, avatarId = 7, createdAt = 20)

    private fun match(id: String, playedAt: Long, vararg seats: Participant) = Match(
        id = id,
        createdAt = playedAt,
        playedAt = playedAt,
        status = MatchStatus.COMPLETED,
        mode = GameMode.DOUBLE_LANE,
        map = "Double lane",
        winner = Team.BLUE,
        winCondition = WinCondition.LIFE_COUNTERS,
        durationMin = 75,
        notes = "Close game",
        draftId = "d1",
        participants = seats.toList(),
    )

    @Test
    fun playersRoundTripSortedByNameAndArchiveHidesThem() = runTest {
        val dao = db.playerDao()
        dao.upsert(bob.toEntity())
        dao.upsert(ann.toEntity())
        assertThat(dao.observeAll().first().map { it.toModel() }).containsExactly(ann, bob).inOrder()

        dao.setArchived(ann.id, true)
        assertThat(dao.observeActive().first().map { it.id }).containsExactly(bob.id)
        assertThat(dao.observe(ann.id).first()!!.toModel()).isEqualTo(ann.copy(archived = true))
    }

    @Test
    fun matchKeepsSeatOrderDoublePilotsAndUnknownHeroes() = runTest {
        val m = match(
            "m1",
            100,
            Participant(ann.id, "hero-b", Team.BLUE),
            Participant(ann.id, null, Team.BLUE, handicap = true),
            Participant(bob.id, "hero-a", Team.RED),
        )
        db.matchDao().upsert(m.toEntity(), m.participantEntities())
        assertThat(db.matchDao().observe("m1").first()!!.toModel()).isEqualTo(m)
    }

    @Test
    fun upsertReplacesSeatsAndDeleteCascades() = runTest {
        val dao = db.matchDao()
        val m = match("m1", 100, Participant(ann.id, "a", Team.RED), Participant(bob.id, "b", Team.BLUE))
        dao.upsert(m.toEntity(), m.participantEntities())
        val edited = m.copy(
            participants = listOf(Participant(bob.id, "c", Team.RED), Participant(ann.id, "d", Team.BLUE)),
        )
        dao.upsert(edited.toEntity(), edited.participantEntities())
        assertThat(dao.getAll().single().toModel()).isEqualTo(edited)

        dao.delete("m1")
        assertThat(dao.getAll()).isEmpty()
    }

    @Test
    fun matchesEmitNewestFirstAndReactToWrites() = runTest {
        val dao = db.matchDao()
        dao.observeAll().test {
            assertThat(awaitItem()).isEmpty()
            val old = match("old", 1, Participant(ann.id, "a", Team.RED), Participant(bob.id, "b", Team.BLUE))
            dao.upsert(old.toEntity(), old.participantEntities())
            assertThat(awaitItem().map { it.match.id }).containsExactly("old")
            val new = match("new", 2, Participant(ann.id, "a", Team.RED), Participant(bob.id, "b", Team.BLUE))
            dao.upsert(new.toEntity(), new.participantEntities())
            assertThat(awaitItem().map { it.match.id }).containsExactly("new", "old").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun draftRecordsRoundTrip() = runTest {
        val draft = DraftRecord(
            id = "d1",
            createdAt = 5,
            methodId = "house",
            methodKind = DraftMethodKind.CUSTOM,
            pool = listOf("a", "b", "c"),
            teamA = Team.BLUE,
            steps = listOf(
                DraftStepRecord(Team.BLUE, DraftAction.BAN, "a"),
                DraftStepRecord(Team.BLUE, DraftAction.PICK, "b"),
                DraftStepRecord(Team.RED, DraftAction.PICK, "c"),
            ),
            matchId = "m1",
        )
        db.draftRecordDao().upsert(draft.toEntity(), draft.stepEntities())
        assertThat(db.draftRecordDao().observeAll().first().single().toModel()).isEqualTo(draft)
        db.draftRecordDao().deleteAll()
        assertThat(db.draftRecordDao().getAll()).isEmpty()
    }
}
