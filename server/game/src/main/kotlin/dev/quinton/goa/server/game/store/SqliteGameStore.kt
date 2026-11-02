package dev.quinton.goa.server.game.store

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.engine.AnswerRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.VoidReason
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * SQLite on one connection, on one thread: JDBC never blocks a CIO thread and writes never interleave.
 * WAL with `synchronous=FULL`: a write is on disk when its call returns, so a frame about an answer can
 * go out afterwards (docs/ONLINE.md "Storage").
 */
class SqliteGameStore private constructor(private val connection: Connection) :
    GameStore,
    AccountStore,
    LobbyStore,
    AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "goa-game-sqlite").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()

    private suspend fun <T> read(block: Connection.() -> T): T = withContext(dispatcher) { connection.block() }

    private suspend fun <T> write(block: Connection.() -> T): T = withContext(dispatcher) {
        connection.transaction(block)
    }

    override fun close() {
        executor.shutdown()
        executor.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)
        connection.close()
    }

    // Games ---------------------------------------------------------------------------------------

    override suspend fun createGame(game: GameRow, seats: List<GameSeatRow>) = write {
        update(
            """
            INSERT INTO games (id, room_code, setup_json, start_save_json, rules_build, engine_version, ranked,
                status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'running', ?)
            """,
            game.id,
            game.roomCode,
            JSON.encodeToString(GameSetup.serializer(), game.setup),
            JSON.encodeToString(SavedGame.serializer(), game.start),
            game.rulesBuild,
            game.engineVersion,
            game.ranked,
            game.createdAt,
        )
        for (seat in seats) {
            update(
                """
                INSERT INTO game_seats (game_id, hero_id, account_id, team, handicap, seat_order)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                game.id,
                seat.heroId,
                seat.accountId,
                seat.team.column,
                seat.handicap,
                seat.seatOrder,
            )
        }
    }

    /** A repeat of a stored answer changes nothing; another answer at the same place would rewrite the past. */
    override suspend fun appendAnswer(answer: StoredAnswer) = write {
        val choice = JSON.encodeToString(Choice.serializer(), answer.record.choice)
        val inserted = update(
            """
            INSERT INTO answers (game_id, seq, decision_id, seat, choice_json, origin, at, events_hash, state_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (game_id, seq) DO NOTHING
            """,
            answer.gameId,
            answer.seq,
            answer.record.decisionId,
            answer.record.seat,
            choice,
            answer.origin.column,
            answer.at,
            answer.eventsHash,
            answer.stateHash,
        )
        if (inserted == 0) {
            val same = query(
                "SELECT decision_id, seat, choice_json FROM answers WHERE game_id = ? AND seq = ?",
                answer.gameId,
                answer.seq,
            ) { rows ->
                rows.next() && rows.getLong(1) == answer.record.decisionId &&
                    rows.getString(2) == answer.record.seat && rows.getString(3) == choice
            }
            check(same) { "Answer ${answer.seq} of a game is already stored, differently" }
        }
    }

    /** The first leaving stands: a repeat changes nothing. */
    override suspend fun markLeft(gameId: String, heroId: String, at: Long) = write {
        update(
            "UPDATE game_seats SET left_at = ? WHERE game_id = ? AND hero_id = ? AND left_at IS NULL",
            at,
            gameId,
            heroId,
        )
        Unit
    }

    /** The first answer stands; a room that wants another clears the lock first. */
    override suspend fun putLock(lock: StoredLock) = write {
        update(
            """
            INSERT INTO locks (game_id, seat, ordinal, round, turn, grp, choice_json, origin, at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (game_id, seat, ordinal) DO NOTHING
            """,
            lock.gameId,
            lock.seat,
            lock.ordinal,
            lock.round,
            lock.turn,
            lock.group,
            JSON.encodeToString(Choice.serializer(), lock.choice),
            lock.origin.column,
            lock.at,
        )
        Unit
    }

    override suspend fun clearLocks(gameId: String, seat: String) = write {
        update("DELETE FROM locks WHERE game_id = ? AND seat = ?", gameId, seat)
        Unit
    }

    override suspend fun finish(gameId: String, result: GameResult, match: Match?) = write {
        val closed = update(
            "UPDATE games SET status = 'finished', finished_at = ? WHERE id = ? AND status = 'running'",
            result.finishedAt,
            gameId,
        )
        if (closed > 0) {
            update("DELETE FROM locks WHERE game_id = ?", gameId)
            if (match != null) {
                update(
                    """
                    INSERT INTO matches (id, played_at, created_at, match_json) VALUES (?, ?, ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    match.id,
                    match.playedAt,
                    match.createdAt,
                    JSON.encodeToString(Match.serializer(), match),
                )
            }
        }
    }

    override suspend fun void(gameId: String, reason: VoidReason, at: Long) = write {
        update(
            "UPDATE games SET status = 'voided', void_reason = ?, finished_at = ? WHERE id = ? AND status = 'running'",
            reason.name.lowercase(),
            at,
            gameId,
        )
        update("DELETE FROM locks WHERE game_id = ?", gameId)
        Unit
    }

    /** Oldest game first, each with its seats in seat order and its answers in the order they were given. */
    override suspend fun runningGames(): List<RunningGame> = read {
        val games = query(
            """
            SELECT id, room_code, setup_json, start_save_json, rules_build, engine_version, ranked, created_at
            FROM games WHERE status = 'running' ORDER BY created_at, id
            """,
        ) { rows -> rows.toList(::gameRow) }
        games.map { game ->
            RunningGame(
                game = game,
                seats = gameSeats(game.id),
                answers = query(
                    """
                    SELECT seq, decision_id, seat, choice_json, origin, at, events_hash, state_hash
                    FROM answers WHERE game_id = ? ORDER BY seq
                    """,
                    game.id,
                ) { rows -> rows.toList { storedAnswer(game.id, it) } },
                locks = query(
                    """
                    SELECT seat, ordinal, round, turn, grp, choice_json, origin, at
                    FROM locks WHERE game_id = ? ORDER BY seat, ordinal
                    """,
                    game.id,
                ) { rows -> rows.toList { storedLock(game.id, it) } },
            )
        }
    }

    override suspend fun matches(): List<Match> = read {
        query("SELECT match_json FROM matches ORDER BY played_at, created_at, id") { rows ->
            rows.toList { JSON.decodeFromString(Match.serializer(), it.getString(1)) }
        }
    }

    private fun gameRow(rows: ResultSet): GameRow {
        val setup = JSON.decodeFromString(GameSetup.serializer(), rows.getString(3))
        return GameRow(
            id = rows.getString(1),
            roomCode = rows.getString(2),
            setup = setup,
            start = JSON.decodeFromString(SavedGame.serializer(), rows.getString(4)),
            rulesBuild = rows.getString(5),
            engineVersion = rows.getInt(6),
            ranked = rows.getInt(7) != 0,
            mapId = setup.mapId,
            length = setup.length,
            createdAt = rows.getLong(8),
        )
    }

    private fun storedAnswer(gameId: String, rows: ResultSet) = StoredAnswer(
        gameId = gameId,
        seq = rows.getLong(1),
        record = AnswerRecord(
            decisionId = rows.getLong(2),
            seat = rows.getString(3),
            choice = JSON.decodeFromString(Choice.serializer(), rows.getString(4)),
        ),
        origin = originOf(rows.getString(5)),
        at = rows.getLong(6),
        eventsHash = rows.getString(7),
        stateHash = rows.getString(8),
    )

    private fun storedLock(gameId: String, rows: ResultSet) = StoredLock(
        gameId = gameId,
        seat = rows.getString(1),
        ordinal = rows.getInt(2),
        round = rows.getInt(3),
        turn = rows.getInt(4),
        group = rows.getString(5),
        choice = JSON.decodeFromString(Choice.serializer(), rows.getString(6)),
        origin = originOf(rows.getString(7)),
        at = rows.getLong(8),
    )

    private fun Connection.gameSeats(gameId: String): List<GameSeatRow> = query(
        """
        SELECT hero_id, account_id, team, handicap, seat_order, left_at
        FROM game_seats WHERE game_id = ? ORDER BY seat_order
        """,
        gameId,
    ) { rows ->
        rows.toList {
            GameSeatRow(
                gameId = gameId,
                heroId = it.getString(1),
                accountId = it.getString(2),
                team = teamOf(it.getString(3)),
                handicap = it.getInt(4) != 0,
                seatOrder = it.getInt(5),
                left = it.longOrNull(6) != null,
            )
        }
    }

    // Accounts ------------------------------------------------------------------------------------

    override suspend fun createAccount(account: AccountRow, tokenHash: String, recoveryHash: String) = write {
        update(
            "INSERT INTO accounts (id, display_name, created_at) VALUES (?, ?, ?)",
            account.id,
            account.name,
            account.createdAt,
        )
        addToken(tokenHash, account.id, account.createdAt)
        addRecovery(recoveryHash, account.id, account.createdAt)
    }

    override suspend fun accountByToken(tokenHash: String, now: Long): TokenMatch? = withContext(dispatcher) {
        val found = connection.query(
            """
            SELECT t.hash, t.last_seen_at, a.id, a.display_name, a.created_at
            FROM tokens t JOIN accounts a ON a.id = t.account_id
            WHERE t.hash = ? AND t.revoked_at IS NULL AND a.deleted_at IS NULL
            """,
            tokenHash,
        ) { rows ->
            if (rows.next()) {
                val account = AccountRow(rows.getString(3), rows.getString(4), rows.getLong(5))
                Triple(rows.getString(1), rows.getLong(2), account)
            } else {
                null
            }
        } ?: return@withContext null
        val (hash, lastSeen, account) = found
        if (now - lastSeen >= LAST_SEEN_EVERY_MILLIS) {
            connection.update("UPDATE tokens SET last_seen_at = ? WHERE hash = ?", now, hash)
        }
        TokenMatch(hash, account)
    }

    override suspend fun account(id: String): AccountRow? = read {
        query("SELECT id, display_name, created_at, deleted_at FROM accounts WHERE id = ?", id) { rows ->
            if (rows.next()) {
                AccountRow(
                    rows.getString(1),
                    rows.getString(2),
                    rows.getLong(3),
                    rows.longOrNull(4),
                )
            } else {
                null
            }
        }
    }

    override suspend fun redeemRecovery(
        recoveryHash: String,
        newTokenHash: String,
        newRecoveryHash: String,
        now: Long,
    ): AccountRow? = write {
        val found = query(
            """
            SELECT r.hash, a.id, a.display_name, a.created_at
            FROM recovery_codes r JOIN accounts a ON a.id = r.account_id
            WHERE r.hash = ? AND r.used_at IS NULL AND a.deleted_at IS NULL
            """,
            recoveryHash,
        ) { rows ->
            if (rows.next() && Secrets.sameHash(rows.getString(1), recoveryHash)) {
                AccountRow(rows.getString(2), rows.getString(3), rows.getLong(4))
            } else {
                null
            }
        }
        if (found != null) {
            update("UPDATE recovery_codes SET used_at = ? WHERE hash = ?", now, recoveryHash)
            addToken(newTokenHash, found.id, now)
            addRecovery(newRecoveryHash, found.id, now)
        }
        found
    }

    override suspend fun rename(id: String, name: String) = write {
        update("UPDATE accounts SET display_name = ? WHERE id = ?", name, id)
        Unit
    }

    override suspend fun anonymise(id: String, name: String, now: Long) = write {
        update("UPDATE accounts SET display_name = ?, deleted_at = ? WHERE id = ?", name, now, id)
        update("UPDATE tokens SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL", now, id)
        update("UPDATE recovery_codes SET used_at = ? WHERE account_id = ? AND used_at IS NULL", now, id)
        Unit
    }

    private fun Connection.addToken(hash: String, accountId: String, now: Long) {
        update(
            "INSERT INTO tokens (hash, account_id, created_at, last_seen_at) VALUES (?, ?, ?, ?)",
            hash,
            accountId,
            now,
            now,
        )
    }

    private fun Connection.addRecovery(hash: String, accountId: String, now: Long) {
        update("INSERT INTO recovery_codes (hash, account_id, created_at) VALUES (?, ?, ?)", hash, accountId, now)
    }

    // Rooms ---------------------------------------------------------------------------------------

    override suspend fun putRoom(room: RoomRow) = write {
        update(
            """
            INSERT INTO rooms (code, host_account, map_id, length, team_size, ranked, created_at, expires_at, game_id,
                status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (code) DO UPDATE SET host_account = excluded.host_account, game_id = excluded.game_id,
                status = excluded.status, expires_at = excluded.expires_at
            """,
            room.code,
            room.hostAccount,
            room.mapId,
            room.length.name.lowercase(),
            room.teamSize,
            room.ranked,
            room.createdAt,
            room.expiresAt,
            room.gameId,
            room.status.name.lowercase(),
        )
        for (seat in room.seats) {
            update(
                """
                INSERT INTO room_seats (code, team, seat_index, hero_id, account_id, ready)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (code, team, seat_index) DO UPDATE SET hero_id = excluded.hero_id,
                    account_id = excluded.account_id, ready = excluded.ready
                """,
                room.code,
                seat.team.column,
                seat.index,
                seat.heroId,
                seat.accountId,
                seat.ready,
            )
        }
    }

    override suspend fun room(code: String): RoomRow? = read { rooms("WHERE code = ?", code).firstOrNull() }

    override suspend fun openRooms(): List<RoomRow> = read { rooms("WHERE status = 'open' ORDER BY created_at, code") }

    override suspend fun markStartedRooms(): Int = write {
        update(
            """
            UPDATE rooms SET status = 'started',
                game_id = (SELECT id FROM games WHERE games.room_code = rooms.code ORDER BY created_at LIMIT 1)
            WHERE status = 'open' AND EXISTS (SELECT 1 FROM games WHERE games.room_code = rooms.code)
            """,
        )
    }

    override suspend fun names(accountIds: Collection<String>): Map<String, String> = read {
        accountIds.distinct().mapNotNull { id ->
            query("SELECT display_name FROM accounts WHERE id = ?", id) { rows ->
                if (rows.next()) id to rows.getString(1) else null
            }
        }.toMap()
    }

    override suspend fun gameSummaries(gameIds: List<String>): List<GameSummary> = read {
        gameIds.mapNotNull { id ->
            query("SELECT room_code, setup_json FROM games WHERE id = ?", id) { rows ->
                if (rows.next()) {
                    val mapId = JSON.decodeFromString(GameSetup.serializer(), rows.getString(2)).mapId
                    rows.getString(1) to mapId
                } else {
                    null
                }
            }?.let { (roomCode, mapId) -> GameSummary(id, roomCode, mapId, gameSeats(id)) }
        }
    }

    override suspend fun isAvailable(): Boolean = try {
        read { query("SELECT 1") { rows -> rows.next() } }
    } catch (_: SQLException) {
        false
    }

    /** A setting of this very connection, for the checks that durability is on. */
    internal suspend fun pragma(name: String): String = read {
        query("PRAGMA $name") { rows ->
            rows.next()
            rows.getString(1)
        }
    }

    private fun Connection.rooms(where: String, vararg args: Any?): List<RoomRow> {
        val rows = query(
            """
            SELECT code, host_account, map_id, length, team_size, ranked, created_at, expires_at, game_id, status
            FROM rooms $where
            """,
            *args,
        ) { rows ->
            rows.toList {
                RoomRow(
                    code = it.getString(1),
                    hostAccount = it.getString(2),
                    mapId = it.getString(3),
                    length = GameLength.valueOf(it.getString(4).uppercase()),
                    teamSize = it.getInt(5),
                    ranked = it.getInt(6) != 0,
                    createdAt = it.getLong(7),
                    expiresAt = it.getLong(8),
                    gameId = it.getString(9),
                    status = RoomStatus.valueOf(it.getString(10).uppercase()),
                    seats = emptyList(),
                )
            }
        }
        return rows.map { room -> room.copy(seats = roomSeats(room.code)) }
    }

    private fun Connection.roomSeats(code: String): List<RoomSeatRow> = query(
        "SELECT team, seat_index, hero_id, account_id, ready FROM room_seats WHERE code = ?",
        code,
    ) { rows ->
        rows.toList {
            RoomSeatRow(
                team = teamOf(it.getString(1)),
                index = it.getInt(2),
                heroId = it.getString(3),
                accountId = it.getString(4),
                ready = it.getInt(5) != 0,
            )
        }
    }.sortedWith(compareBy({ it.team.ordinal }, { it.index }))

    companion object {
        private const val DATABASE_FILE = "games.sqlite"
        private const val TEMP_DIR_PROPERTY = "org.sqlite.tmpdir"
        private const val BUSY_TIMEOUT_MILLIS = 5_000
        private const val CLOSE_WAIT_SECONDS = 10L
        private const val LAST_SEEN_EVERY_MILLIS = 60 * 60_000L

        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            allowStructuredMapKeys = true
        }

        /**
         * Opens, or creates, the database in [dataDir]; repeating this on the same directory changes
         * nothing. sqlite-jdbc unpacks its native library into `org.sqlite.tmpdir`, which falls back to
         * `/tmp`: a container's may be noexec, so unless the JVM was told otherwise it goes in [dataDir].
         * SQLite's own temporary tables stay in memory, for the same reason.
         */
        fun open(dataDir: Path): SqliteGameStore {
            Files.createDirectories(dataDir)
            if (System.getProperty(TEMP_DIR_PROPERTY) == null) {
                System.setProperty(TEMP_DIR_PROPERTY, dataDir.toAbsolutePath().toString())
            }
            val connection = DriverManager.getConnection(
                "jdbc:sqlite:${dataDir.resolve(DATABASE_FILE).toAbsolutePath()}",
            )
            connection.createStatement().use { pragma ->
                pragma.execute("PRAGMA busy_timeout = $BUSY_TIMEOUT_MILLIS")
                pragma.execute("PRAGMA journal_mode = WAL")
                pragma.execute("PRAGMA synchronous = FULL")
                pragma.execute("PRAGMA foreign_keys = ON")
                pragma.execute("PRAGMA temp_store = MEMORY")
            }
            createSchema(connection)
            return SqliteGameStore(connection)
        }

        private fun createSchema(connection: Connection) = connection.transaction {
            createStatement().use { statement ->
                Schema.STATEMENTS.forEach { statement.execute(it) }
            }
            val found = query("SELECT MAX(version) FROM schema_version") { rows ->
                if (rows.next()) rows.longOrNull(1) else null
            }
            check(found == null || found <= Schema.VERSION) {
                "The database is schema $found, newer than this server's ${Schema.VERSION}"
            }
            if (found == null) {
                update(
                    "INSERT INTO schema_version (version, applied_at) VALUES (?, ?)",
                    Schema.VERSION,
                    System.currentTimeMillis(),
                )
            }
        }

        /** All of [block] or none of it; on disk when this returns. */
        private fun <T> Connection.transaction(block: Connection.() -> T): T {
            autoCommit = false
            var committed = false
            try {
                return block().also {
                    commit()
                    committed = true
                }
            } finally {
                if (!committed) rollback()
                autoCommit = true
            }
        }

        private val Team.column: String get() = name.lowercase()

        private val AnswerOrigin.column: String get() = name.lowercase()

        private fun teamOf(column: String): Team = Team.valueOf(column.uppercase())

        private fun originOf(column: String): AnswerOrigin = AnswerOrigin.valueOf(column.uppercase())

        private fun ResultSet.longOrNull(index: Int): Long? = (getObject(index) as? Number)?.toLong()

        private fun <T> ResultSet.toList(row: (ResultSet) -> T): List<T> {
            val out = ArrayList<T>()
            while (next()) out += row(this)
            return out
        }

        private fun PreparedStatement.bind(args: Array<out Any?>) {
            args.forEachIndexed { i, arg ->
                when (arg) {
                    is Boolean -> setInt(i + 1, if (arg) 1 else 0)
                    else -> setObject(i + 1, arg)
                }
            }
        }

        private fun Connection.update(sql: String, vararg args: Any?): Int = prepareStatement(sql).use { statement ->
            statement.bind(args)
            statement.executeUpdate()
        }

        private fun <T> Connection.query(sql: String, vararg args: Any?, read: (ResultSet) -> T): T =
            prepareStatement(sql).use { statement ->
                statement.bind(args)
                statement.executeQuery().use(read)
            }
    }
}
