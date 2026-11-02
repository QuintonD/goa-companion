package dev.quinton.goa.server.game.store

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.engine.AnswerRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.VoidReason
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Nothing about this reads an answer of a finished game: the board stands after the sweep. */
class RetentionTest {
    private lateinit var dir: Path
    private lateinit var store: SqliteGameStore
    private val day = 24 * 60 * 60_000L
    private val now = 1_000 * day

    @Before
    fun open() {
        dir = Files.createTempDirectory("goa-retention")
        store = SqliteGameStore.open(dir)
    }

    @After
    fun close() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun count(table: String, where: String = "1 = 1"): Int =
        DriverManager.getConnection("jdbc:sqlite:${dir.resolve("games.sqlite")}").use { c ->
            c.createStatement().executeQuery("SELECT COUNT(*) FROM $table WHERE $where").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private suspend fun person(id: String, createdAt: Long) {
        store.createAccount(AccountRow(id, "Name $id", createdAt), "token-$id", "code-$id")
    }

    private fun room(code: String, status: RoomStatus, expiresAt: Long, gameId: String? = null, host: String = "host") =
        RoomRow(
            code = code,
            hostAccount = host,
            mapId = "forgotten-island",
            length = GameLength.QUICK,
            teamSize = 1,
            ranked = false,
            createdAt = expiresAt - 1,
            expiresAt = expiresAt,
            status = status,
            seats = listOf(RoomSeatRow(Team.RED, 0, accountId = host)),
            gameId = gameId,
        )

    @Test
    fun closedRoomsGoAfterSevenDaysWithTheirSeats() = runBlocking<Unit> {
        person("host", now)
        store.putRoom(room("OLD", RoomStatus.CLOSED, now - 8 * day))
        store.putRoom(room("NEW", RoomStatus.CLOSED, now - 6 * day))
        store.putRoom(room("WAITING", RoomStatus.OPEN, now + day))

        assertThat(store.sweepOld(now).rooms).isEqualTo(1)
        assertThat(store.room("OLD")).isNull()
        assertThat(count("room_seats", "code = 'OLD'")).isEqualTo(0)
        assertThat(store.room("NEW")).isNotNull()
        assertThat(store.room("WAITING")).isNotNull()
    }

    @Test
    fun aStartedRoomGoesOnceItsGameHasBeenOverSevenDays() = runBlocking<Unit> {
        person("host", now)
        store.createGame(StoreFixtures.game("done"), StoreFixtures.seats("done"))
        store.finish("done", GameResult(now - 8 * day, rated = false), null)
        store.createGame(StoreFixtures.game("live"), StoreFixtures.seats("live"))
        store.putRoom(room("DONE", RoomStatus.STARTED, now - 9 * day, gameId = "done"))
        store.putRoom(room("LIVE", RoomStatus.STARTED, now - 9 * day, gameId = "live"))

        store.sweepOld(now)
        assertThat(store.room("DONE")).isNull()
        assertThat(store.room("LIVE")).isNotNull()
    }

    @Test
    fun answersOfGamesOverThirtyDaysGoAndTheMatchLogStaysWhole() = runBlocking<Unit> {
        store.createGame(StoreFixtures.game("old"), StoreFixtures.seats("old"))
        store.appendAnswer(StoreFixtures.answer("old", 0))
        store.putLock(StoreFixtures.lock("old", "wasp", 0))
        store.finish("old", GameResult(now - 31 * day, rated = true), StoreFixtures.match("old", now - 31 * day, 1))

        store.createGame(StoreFixtures.game("void"), StoreFixtures.seats("void"))
        store.appendAnswer(StoreFixtures.answer("void", 0))
        store.void("void", VoidReason.ABANDONED, now - 31 * day)

        store.createGame(StoreFixtures.game("recent"), StoreFixtures.seats("recent"))
        store.appendAnswer(StoreFixtures.answer("recent", 0))
        store.finish("recent", GameResult(now - 29 * day, rated = true), StoreFixtures.match("recent", now - 29 * day, 1))

        store.createGame(StoreFixtures.game("running", createdAt = 1), StoreFixtures.seats("running"))
        store.appendAnswer(StoreFixtures.answer("running", 0))

        val before = store.matches()
        assertThat(store.sweepOld(now).answers).isEqualTo(2)

        assertThat(count("answers", "game_id = 'old'")).isEqualTo(0)
        assertThat(count("answers", "game_id = 'void'")).isEqualTo(0)
        assertThat(count("answers", "game_id = 'recent'")).isEqualTo(1)
        assertThat(store.runningGame("running").answers).hasSize(1)
        assertThat(store.matches()).isEqualTo(before)
        assertThat(count("games", "id = 'old'")).isEqualTo(1)
    }

    @Test
    fun tokensRevokedOverThirtyDaysAgoGo() = runBlocking<Unit> {
        person("gone", now - 200 * day)
        store.createGame(StoreFixtures.game("g"), listOf(GameSeatRow("g", "wasp", "gone", Team.RED, false, 0)))
        store.anonymise("gone", "Former player", now - 31 * day)
        person("live", now - 10 * day)
        person("recent", now - 200 * day)
        store.anonymise("recent", "Former player", now - 29 * day)

        assertThat(store.sweepOld(now).tokens).isEqualTo(1)
        assertThat(count("tokens", "account_id = 'gone'")).isEqualTo(0)
        assertThat(count("tokens", "account_id = 'live'")).isEqualTo(1)
        assertThat(count("tokens", "account_id = 'recent'")).isEqualTo(1)
    }

    @Test
    fun anAccountWithNoGamesAndNoActivityFor90DaysGoes() = runBlocking<Unit> {
        person("idle", now - 91 * day)
        person("fresh", now - 89 * day)
        person("player", now - 200 * day)
        person("hosting", now - 200 * day)
        person("seen", now - 200 * day)
        store.accountByToken("token-seen", now - 2 * 24 * 60_000L)
        store.createGame(StoreFixtures.game("g"), listOf(GameSeatRow("g", "wasp", "player", Team.RED, false, 0)))
        store.putRoom(room("R", RoomStatus.OPEN, now + day, host = "hosting"))

        assertThat(store.sweepOld(now).accounts).isEqualTo(1)
        assertThat(store.account("idle")).isNull()
        assertThat(count("tokens", "account_id = 'idle'")).isEqualTo(0)
        assertThat(count("recovery_codes", "account_id = 'idle'")).isEqualTo(0)
        for (kept in listOf("fresh", "player", "hosting", "seen")) assertThat(store.account(kept)).isNotNull()
    }

    @Test
    fun aDeletedAccountWithNoGamesGoesToo() = runBlocking<Unit> {
        person("deleted", now - 200 * day)
        store.anonymise("deleted", "Former player", now - 91 * day)
        store.sweepOld(now)
        assertThat(store.account("deleted")).isNull()
    }

    @Test
    fun aSecondSweepFindsNothing() = runBlocking<Unit> {
        person("idle", now - 91 * day)
        store.sweepOld(now)
        assertThat(store.sweepOld(now)).isEqualTo(Swept(0, 0, 0, 0))
    }
}

internal object StoreFixtures {
    val setup = GameSetup(
        mapId = "forgotten-island",
        seats = listOf(SeatSetup("wasp", Team.RED, Controller.Human), SeatSetup("arien", Team.BLUE, Controller.Human)),
        length = GameLength.LONG,
        tieBreaker = Team.BLUE,
    )

    fun game(id: String, createdAt: Long = 10) = GameRow(
        id = id,
        roomCode = "ROOM$id",
        setup = setup,
        start = SavedGame(1, setup, "payload-$id", 5),
        rulesBuild = "build-1",
        engineVersion = 1,
        ranked = true,
        mapId = setup.mapId,
        length = setup.length,
        createdAt = createdAt,
    )

    fun seats(id: String) = listOf(
        GameSeatRow(id, "wasp", "account-red", Team.RED, handicap = false, seatOrder = 0),
        GameSeatRow(id, "arien", "account-blue", Team.BLUE, handicap = true, seatOrder = 1),
    )

    fun answer(id: String, seq: Long, choice: Choice = Choice.Pass) = StoredAnswer(
        gameId = id,
        seq = seq,
        record = AnswerRecord(100 + seq, "wasp", choice),
        origin = AnswerOrigin.HUMAN,
        at = 1000 + seq,
        eventsHash = "events-$seq",
        stateHash = if (seq % 2 == 0L) "state-$seq" else null,
    )

    fun lock(id: String, seat: String, ordinal: Int, choice: Choice = Choice.Pass) = StoredLock(
        gameId = id,
        seat = seat,
        ordinal = ordinal,
        round = 1,
        turn = 2,
        group = "red-blue",
        choice = choice,
        origin = AnswerOrigin.CLOCK,
        at = 77,
    )

    fun match(id: String, playedAt: Long, createdAt: Long) = Match(
        id = id,
        createdAt = createdAt,
        playedAt = playedAt,
        status = MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = Team.RED,
        participants = listOf(
            Participant("account-red", "wasp", Team.RED),
            Participant("account-blue", "arien", Team.BLUE),
        ),
    )
}

class SqliteGameStoreTest {
    private lateinit var dir: Path
    private lateinit var store: SqliteGameStore

    @Before
    fun open() {
        dir = Files.createTempDirectory("goa-store-test")
        store = SqliteGameStore.open(dir)
    }

    @After
    fun close() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun reopen() {
        store.close()
        store = SqliteGameStore.open(dir)
    }

    private fun databaseUrl() = "jdbc:sqlite:${dir.resolve("games.sqlite").toAbsolutePath()}"

    @Test
    fun schemaCreationIsIdempotentAndKeepsData(): Unit = runBlocking {
        store.createGame(StoreFixtures.game("a"), StoreFixtures.seats("a"))
        repeat(3) { reopen() }
        assertThat(store.runningGames().map { it.game.id }).containsExactly("a")
        DriverManager.getConnection(databaseUrl()).use { connection ->
            connection.createStatement().executeQuery("SELECT COUNT(*), MAX(version) FROM schema_version").use { rows ->
                rows.next()
                assertThat(rows.getInt(1)).isEqualTo(1)
                assertThat(rows.getInt(2)).isEqualTo(Schema.VERSION)
            }
        }
    }

    @Test
    fun aDatabaseFromANewerServerIsRefused() {
        store.close()
        DriverManager.getConnection(databaseUrl()).use {
            it.createStatement().execute("INSERT INTO schema_version (version, applied_at) VALUES (999, 0)")
        }
        assertThrows(IllegalStateException::class.java) { SqliteGameStore.open(dir) }
        DriverManager.getConnection(databaseUrl()).use {
            it.createStatement().execute("DELETE FROM schema_version WHERE version = 999")
        }
        store = SqliteGameStore.open(dir)
    }

    @Test
    fun everyTableIsThereAndNoRatingColumnAnywhere() {
        DriverManager.getConnection(databaseUrl()).use { connection ->
            val tables = connection.createStatement().executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
            )
                .use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            assertThat(tables).containsAtLeast(
                "schema_version",
                "accounts",
                "tokens",
                "recovery_codes",
                "rooms",
                "room_seats",
                "games",
                "game_seats",
                "answers",
                "locks",
                "matches",
                "publish_state",
            )
            for (table in tables) {
                val columns = connection.createStatement().executeQuery("PRAGMA table_info($table)")
                    .use { rows -> buildList { while (rows.next()) add(rows.getString("name")) } }
                assertThat(columns.filter { "rating" in it || "elo" in it }).isEmpty()
            }
        }
    }

    @Test
    fun walFullSyncForeignKeysAndMemoryTempAreOnForTheStoresConnection(): Unit = runBlocking {
        assertThat(store.pragma("journal_mode")).isEqualTo("wal")
        assertThat(store.pragma("synchronous")).isEqualTo("2")
        assertThat(store.pragma("foreign_keys")).isEqualTo("1")
        assertThat(store.pragma("temp_store")).isEqualTo("2")
    }

    @Test
    fun foreignKeysRefuseAnAnswerToNoGame(): Unit = runBlocking {
        assertFails { store.appendAnswer(StoreFixtures.answer("nothing", 0)) }
        assertThat(store.runningGames()).isEmpty()
    }

    @Test
    fun runningGamesComeBackWholeAndInOrderAfterAReopen(): Unit = runBlocking {
        store.createGame(StoreFixtures.game("later", createdAt = 20), StoreFixtures.seats("later"))
        store.createGame(StoreFixtures.game("first", createdAt = 10), StoreFixtures.seats("first"))
        val unit = Choice.Unit("minion-1")
        listOf(3L, 0L, 2L, 1L).forEach { store.appendAnswer(StoreFixtures.answer("first", it, unit)) }
        store.putLock(StoreFixtures.lock("first", "wasp", 1))
        store.putLock(StoreFixtures.lock("first", "wasp", 0, unit))
        store.putLock(StoreFixtures.lock("first", "arien", 0))
        reopen()

        val running = store.runningGames()
        assertThat(running.map { it.game.id }).containsExactly("first", "later").inOrder()
        val first = running.first()
        assertThat(first.game).isEqualTo(StoreFixtures.game("first", createdAt = 10))
        assertThat(first.seats).isEqualTo(StoreFixtures.seats("first"))
        assertThat(first.answers).isEqualTo((0L..3L).map { StoreFixtures.answer("first", it, unit) })
        assertThat(first.locks).isEqualTo(
            listOf(
                StoreFixtures.lock("first", "arien", 0),
                StoreFixtures.lock("first", "wasp", 0, unit),
                StoreFixtures.lock("first", "wasp", 1),
            ),
        )
    }

    @Test
    fun aSeatGivenUpStaysGivenUpAfterAReopen(): Unit = runBlocking {
        store.createGame(StoreFixtures.game("a"), StoreFixtures.seats("a"))
        val seat = StoreFixtures.seats("a").first().heroId
        store.markLeft("a", seat, 5)
        store.markLeft("a", seat, 9)
        reopen()
        val seats = store.runningGames().single().seats
        assertThat(seats.filter { it.left }.map { it.heroId }).containsExactly(seat)
    }

    @Test
    fun aRepeatedAnswerChangesNothingAndADifferentOneIsRefused(): Unit = runBlocking {
        store.createGame(StoreFixtures.game("a"), StoreFixtures.seats("a"))
        store.appendAnswer(StoreFixtures.answer("a", 0))
        store.appendAnswer(StoreFixtures.answer("a", 0))
        assertThat(store.runningGames().single().answers).hasSize(1)
        assertFails { store.appendAnswer(StoreFixtures.answer("a", 0, Choice.Unit("other"))) }
        assertThat(store.runningGames().single().answers.single().record.choice).isEqualTo(Choice.Pass)
    }

    @Test
    fun theFirstLockStandsUntilCleared(): Unit = runBlocking {
        store.createGame(StoreFixtures.game("a"), StoreFixtures.seats("a"))
        store.putLock(StoreFixtures.lock("a", "wasp", 0))
        store.putLock(StoreFixtures.lock("a", "wasp", 0, Choice.Unit("other")))
        assertThat(store.runningGames().single().locks.single().choice).isEqualTo(Choice.Pass)
        store.putLock(StoreFixtures.lock("a", "arien", 0))
        store.clearLocks("a", "wasp")
        assertThat(store.runningGames().single().locks.map { it.seat }).containsExactly("arien")
    }

    @Test
    fun finishingClosesTheGameAndJoinsTheLogInOneStepOnce(): Unit = runBlocking {
        store.createGame(StoreFixtures.game("a"), StoreFixtures.seats("a"))
        store.putLock(StoreFixtures.lock("a", "wasp", 0))
        val match = StoreFixtures.match("m", 5, 5)
        store.finish("a", GameResult(99, rated = true), match)
        store.finish("a", GameResult(100, rated = true), StoreFixtures.match("other", 6, 6))
        reopen()
        assertThat(store.runningGames()).isEmpty()
        assertThat(store.matches()).containsExactly(match)
    }

    @Test
    fun anUnratedFinishAndAVoidLeaveNoMatch(): Unit = runBlocking {
        store.createGame(StoreFixtures.game("a"), StoreFixtures.seats("a"))
        store.createGame(StoreFixtures.game("b"), StoreFixtures.seats("b"))
        store.finish("a", GameResult(99, rated = false), null)
        store.void("b", VoidReason.RULES_CHANGED, 100)
        assertThat(store.runningGames()).isEmpty()
        assertThat(store.matches()).isEmpty()
        DriverManager.getConnection(databaseUrl()).use { connection ->
            connection.createStatement().executeQuery(
                "SELECT status, void_reason FROM games WHERE id = 'b'",
            ).use { rows ->
                rows.next()
                assertThat(rows.getString(1)).isEqualTo("voided")
                assertThat(rows.getString(2)).isEqualTo("rules_changed")
            }
        }
    }

    @Test
    fun matchesComeInReplayOrder(): Unit = runBlocking {
        val inOrder = listOf(
            StoreFixtures.match("z", playedAt = 1, createdAt = 1),
            StoreFixtures.match("b", playedAt = 2, createdAt = 1),
            StoreFixtures.match("a", playedAt = 2, createdAt = 2),
            StoreFixtures.match("c", playedAt = 2, createdAt = 2),
            StoreFixtures.match("y", playedAt = 3, createdAt = 0),
        )
        inOrder.shuffled(Random(7)).forEachIndexed { i, match ->
            store.createGame(StoreFixtures.game("g$i"), StoreFixtures.seats("g$i"))
            store.finish("g$i", GameResult(1, rated = true), match)
        }
        assertThat(store.matches()).isEqualTo(inOrder)
    }

    @Test
    fun accountsKeepOnlyHashesAndGoAwayByAnonymising(): Unit = runBlocking {
        val account = AccountRow("acc", "Name", 5)
        store.createAccount(account, "token-hash", "code-hash")
        assertThat(store.accountByToken("token-hash", 6)?.account).isEqualTo(account)
        assertThat(store.accountByToken("other", 6)).isNull()

        val linked = store.redeemRecovery("code-hash", "token-2", "code-2", 7)
        assertThat(linked?.id).isEqualTo("acc")
        assertThat(store.redeemRecovery("code-hash", "token-3", "code-3", 8)).isNull()
        assertThat(store.accountByToken("token-2", 9)?.account?.id).isEqualTo("acc")

        store.anonymise("acc", "Former player", 10)
        assertThat(store.accountByToken("token-hash", 11)).isNull()
        assertThat(store.accountByToken("token-2", 11)).isNull()
        assertThat(store.redeemRecovery("code-2", "token-4", "code-4", 12)).isNull()
        assertThat(store.account("acc")).isEqualTo(AccountRow("acc", "Former player", 5, 10))
    }

    @Test
    fun aRoomAndItsSeatsRoundTrip(): Unit = runBlocking {
        store.createAccount(AccountRow("host", "Host", 1), "t", "c")
        val room = room().copy(
            seats = listOf(RoomSeatRow(Team.RED, 0, "wasp", "host", ready = true), RoomSeatRow(Team.BLUE, 0)),
        )
        store.putRoom(room)
        assertThat(store.room(room.code)).isEqualTo(room)
        assertThat(store.openRooms()).containsExactly(room)
        val started = room.copy(status = RoomStatus.STARTED, gameId = "g")
        store.putRoom(started)
        assertThat(store.room(room.code)).isEqualTo(started)
        assertThat(store.openRooms()).isEmpty()
    }

    @Test
    fun aRoomLeftOpenByACrashIsMarkedStartedFromItsGame(): Unit = runBlocking {
        store.createAccount(AccountRow("host", "Host", 1), "t", "c")
        store.putRoom(room().copy(code = "ROOMa"))
        store.createGame(StoreFixtures.game("a"), StoreFixtures.seats("a"))
        assertThat(store.markStartedRooms()).isEqualTo(1)
        assertThat(store.room("ROOMa")?.status).isEqualTo(RoomStatus.STARTED)
        assertThat(store.room("ROOMa")?.gameId).isEqualTo("a")
        assertThat(store.markStartedRooms()).isEqualTo(0)
    }

    @Test
    fun theNativeLibraryGoesInTheDataDirectoryUnlessTheJvmWasToldOtherwise() {
        val property = "org.sqlite.tmpdir"
        val pinned = System.getProperty(property)
        try {
            System.clearProperty(property)
            val other = Files.createTempDirectory("goa-store-tmpdir")
            SqliteGameStore.open(other).close()
            assertThat(System.getProperty(property)).isEqualTo(other.toAbsolutePath().toString())

            System.setProperty(property, "elsewhere")
            SqliteGameStore.open(other).close()
            assertThat(System.getProperty(property)).isEqualTo("elsewhere")
            other.toFile().deleteRecursively()
        } finally {
            System.setProperty(property, pinned)
        }
    }

    private fun room() = RoomRow(
        code = "ABCDEFGH",
        hostAccount = "host",
        mapId = "forgotten-island",
        length = GameLength.LONG,
        teamSize = 1,
        ranked = true,
        createdAt = 1,
        expiresAt = 2,
        status = RoomStatus.OPEN,
        seats = emptyList(),
    )

    private suspend fun assertFails(block: suspend () -> Unit) {
        val failed = try {
            block()
            false
        } catch (_: Exception) {
            true
        }
        assertThat(failed).isTrue()
    }
}
