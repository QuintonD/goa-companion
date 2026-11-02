package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.engine.AnswerRecord
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.server.game.store.AnswerOrigin
import dev.quinton.goa.server.game.store.GameStore
import dev.quinton.goa.server.game.store.RunningGame
import dev.quinton.goa.server.game.store.SqliteGameStore
import dev.quinton.goa.server.game.store.StoredAnswer
import dev.quinton.goa.server.game.store.StoredLock
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * `restart: unless-stopped` turns anything that escapes a restore into a crash loop, so each of these is a
 * damaged game next to a healthy one: the damaged one is voided and said so, the healthy one carries on.
 */
class RestoreTest {
    private lateinit var dir: Path
    private lateinit var store: SqliteGameStore
    private val clock = FakeClock()

    @Before
    fun open() {
        dir = Files.createTempDirectory("goa-restore")
        store = SqliteGameStore.open(dir)
    }

    @After
    fun close() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun sql(statement: String) {
        DriverManager.getConnection("jdbc:sqlite:${dir.resolve("games.sqlite")}").use {
            it.createStatement().execute(statement)
        }
    }

    private fun session() = TestRules.engine.newGame(duel()) as EngineSession

    /** The damaged game is the older one, so it comes first and a failure would stop the healthy one. */
    private suspend fun seed(game: TestGame = TestGame(store = store, clock = clock)): TestGame {
        val s = session()
        store.createGame(game.row(s, ranked = false).copy(id = BAD, createdAt = 1), seats(game, s, BAD))
        store.createGame(game.row(s, ranked = false), game.seatRows(s))
        return game
    }

    private fun seats(game: TestGame, s: EngineSession, id: String) = game.seatRows(s).map { it.copy(gameId = id) }

    private suspend fun assertBadIsVoidAndGoodIsBack(game: TestGame) {
        game.core.restore()
        assertThat(game.core.runningCount).isEqualTo(1)
        assertThat(store.runningGameIds()).containsExactly(TestGame.GAME_ID)
        assertThat(game.core.gamesOf(WASP)).containsExactly(BAD, TestGame.GAME_ID)

        val told = Inbox()
        assertThat(game.core.connect(BAD, WASP, 0, told)).isNotNull()
        assertThat(told.ended!!.voided).isEqualTo(VoidReason.RULES_CHANGED)
        assertThat(told.ended!!.rated).isFalse()

        val box = game.connect("wasp")
        assertThat(box.question("wasp")).isNotNull()
    }

    @Test
    fun `a setup that can't be read`() = runBlocking<Unit> {
        val game = seed()
        sql("UPDATE games SET setup_json = '{' WHERE id = '$BAD'")
        assertBadIsVoidAndGoodIsBack(game)
    }

    @Test
    fun `a start save that can't be read, or that decodes into nonsense`() = runBlocking<Unit> {
        val game = seed()
        sql("UPDATE games SET start_save_json = 'not json' WHERE id = '$BAD'")
        assertBadIsVoidAndGoodIsBack(game)

        val other = TestGame(store = store, clock = clock)
        store.createGame(other.row(session(), ranked = false).copy(id = "worse", createdAt = 2), listOf())
        sql("UPDATE games SET start_save_json = '{\"engineVersion\":1}' WHERE id = 'worse'")
        other.core.restore()
        assertThat(store.runningGameIds()).doesNotContain("worse")
    }

    @Test
    fun `an answer that can't be read`() = runBlocking<Unit> {
        val game = seed()
        store.appendAnswer(
            StoredAnswer(BAD, 1, AnswerRecord(0, "wasp", Choice.Pass), AnswerOrigin.HUMAN, 1, "hash", null),
        )
        sql("UPDATE answers SET choice_json = '{\"type\":\"no-such-choice\"}' WHERE game_id = '$BAD'")
        assertBadIsVoidAndGoodIsBack(game)
    }

    @Test
    fun `a lock that can't be read`() = runBlocking<Unit> {
        val game = seed()
        store.putLock(StoredLock(BAD, "wasp", 0, 1, 1, "play", Choice.Pass, AnswerOrigin.HUMAN, 1))
        sql("UPDATE locks SET choice_json = 'x' WHERE game_id = '$BAD'")
        assertBadIsVoidAndGoodIsBack(game)
    }

    @Test
    fun `seats that can't be read still say who to tell`() = runBlocking<Unit> {
        val game = seed()
        sql("UPDATE game_seats SET team = 'purple' WHERE game_id = '$BAD' AND hero_id = 'arien'")
        assertBadIsVoidAndGoodIsBack(game)
    }

    @Test
    fun `a game whose setup can't be read is still listed for its players`() = runBlocking<Unit> {
        seed()
        sql("UPDATE games SET setup_json = '{' WHERE id = '$BAD'")
        val summary = store.gameSummaries(listOf(BAD, TestGame.GAME_ID))
        assertThat(summary.map { it.gameId }).containsExactly(BAD, TestGame.GAME_ID).inOrder()
        assertThat(summary.first().seats.map { it.heroId }).containsExactly("wasp", "arien")
    }

    /** A game that fails in `begin()`, after everything else has worked. */
    private class FailsOnBegin(private val inner: GameStore) : GameStore by inner {
        override suspend fun appendAnswer(answer: StoredAnswer) {
            check(answer.gameId != BAD) { "disk trouble" }
            inner.appendAnswer(answer)
        }
    }

    @Test
    fun `an exception in begin`() = runBlocking<Unit> {
        val game = TestGame(store = FailsOnBegin(store), clock = clock)
        seed(game)
        // A seat given up: the first thing a restore does is answer for it, which is what fails.
        store.markLeft(BAD, "wasp", 1)
        assertBadIsVoidAndGoodIsBack(game)
    }

    /** Past saving: nothing about this game can be read or written. */
    private class Unreadable(private val inner: GameStore, private val failure: Exception) : GameStore by inner {
        override suspend fun runningGame(id: String): RunningGame {
            if (id == BAD) throw failure
            return inner.runningGame(id)
        }

        override suspend fun accountsOf(gameId: String): Set<String> {
            if (gameId == BAD) throw failure
            return inner.accountsOf(gameId)
        }

        override suspend fun void(gameId: String, reason: VoidReason, at: Long) {
            if (gameId == BAD) throw failure
            inner.void(gameId, reason, at)
        }
    }

    @Test
    fun `a game that can't even be voided is skipped and the rest are restored`() = runBlocking<Unit> {
        val game = TestGame(store = Unreadable(store, IllegalStateException("gone")), clock = clock)
        seed(game)
        game.core.restore()
        assertThat(game.core.runningCount).isEqualTo(1)
        assertThat(game.connect("wasp").question("wasp")).isNotNull()
        // It stays as it was, for the next restart to try again.
        assertThat(store.runningGameIds()).containsExactly(BAD, TestGame.GAME_ID)
    }

    @Test
    fun `a store failure is the store's and voids nothing`() = runBlocking<Unit> {
        val game = TestGame(store = Unreadable(store, SQLException("busy")), clock = clock)
        seed(game)
        game.core.restore()
        assertThat(game.core.runningCount).isEqualTo(1)
        assertThat(store.runningGameIds()).containsExactly(BAD, TestGame.GAME_ID)
    }

    @Test
    fun `a store that can't list games still lets the server come up`() = runBlocking<Unit> {
        val broken = object : GameStore by store {
            override suspend fun runningGameIds(): List<String> = throw SQLException("no such table")
        }
        val game = TestGame(store = broken, clock = clock)
        game.core.restore()
        assertThat(game.core.runningCount).isEqualTo(0)
    }

    @Test
    fun `a match row that can't be read leaves the rest of the log`() = runBlocking<Unit> {
        sql("INSERT INTO matches (id, played_at, created_at, match_json) VALUES ('bad', 1, 1, '{')")
        assertThat(store.matches()).isEmpty()
    }

    private companion object {
        const val BAD = "damaged"
        val WASP = TestGame.account("wasp")
    }
}
