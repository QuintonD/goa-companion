package dev.quinton.goa.server.game.e2e

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.server.game.room.TestRules
import java.nio.file.Path
import java.sql.DriverManager
import kotlinx.serialization.json.Json

/** One row of the `answers` table. */
internal data class LoggedAnswer(
    val seq: Long,
    val decisionId: Long,
    val seat: String,
    val choice: Choice,
    val origin: String,
)

/** The game at one moment of its log: what the server's session held then, for what must stay hidden. */
internal class Position(
    val answers: Int,
    val eventCount: Int,
    val pending: Decision?,
    val views: Map<String, GameView>,
    val spectator: GameView,
)

/** A whole game read back from the database and replayed by itself: the truth a test compares frames to. */
internal class GameTruth(
    val gameId: String,
    val seats: List<String>,
    val answers: List<LoggedAnswer>,
    val status: String,
    val ranked: Boolean,
    val positions: List<Position>,
    val events: List<GameEvent>,
)

/** A second connection on the database, the way an admin would read it: the store only hands out running games. */
internal class GameLogs(dir: Path) {
    private val url = "jdbc:sqlite:${dir.resolve("games.sqlite").toAbsolutePath()}"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    private fun <T> connection(block: (java.sql.Connection) -> T): T = DriverManager.getConnection(url).use(block)

    fun answers(gameId: String): List<LoggedAnswer> = connection { db ->
        db.prepareStatement(
            "SELECT seq, decision_id, seat, choice_json, origin FROM answers WHERE game_id = ? ORDER BY seq",
        ).use { query ->
            query.setString(1, gameId)
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            LoggedAnswer(
                                rows.getLong(1),
                                rows.getLong(2),
                                rows.getString(3),
                                json.decodeFromString(Choice.serializer(), rows.getString(4)),
                                rows.getString(5),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun locks(gameId: String): List<String> = connection { db ->
        db.prepareStatement("SELECT seat FROM locks WHERE game_id = ?").use { query ->
            query.setString(1, gameId)
            query.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(rows.getString(1))
                }
            }
        }
    }

    fun status(gameId: String): String = connection { db ->
        db.prepareStatement("SELECT status FROM games WHERE id = ?").use { query ->
            query.setString(1, gameId)
            query.executeQuery().use { rows ->
                check(rows.next()) { "No game $gameId" }
                rows.getString(1)
            }
        }
    }

    private fun start(gameId: String): Pair<SavedGame, Boolean> = connection { db ->
        db.prepareStatement("SELECT start_save_json, ranked FROM games WHERE id = ?").use { query ->
            query.setString(1, gameId)
            query.executeQuery().use { rows ->
                check(rows.next()) { "No game $gameId" }
                json.decodeFromString(SavedGame.serializer(), rows.getString(1)) to (rows.getInt(2) != 0)
            }
        }
    }

    /** What a rules fix would do to a stored game: the first answer's hash no longer matches its replay. */
    fun tamperWith(gameId: String) = connection { db ->
        db.prepareStatement("UPDATE answers SET events_hash = 'changed' WHERE game_id = ? AND seq = 1").use { update ->
            update.setString(1, gameId)
            check(update.executeUpdate() == 1) { "No first answer in $gameId" }
        }
    }

    /** Replays the game's log, keeping every seat's view after each answer. */
    fun truth(gameId: String): GameTruth {
        val (save, ranked) = start(gameId)
        val log = answers(gameId)
        val session = TestRules.engine.restoreFrom(save, emptyList()) as EngineSession
        val seats = session.setup.seats.map { it.heroId }
        fun position(n: Int) = Position(
            n,
            session.events.size,
            session.pending,
            seats.associateWith { session.view(it) },
            session.view(null),
        )
        val positions = ArrayList<Position>()
        positions += position(0)
        log.forEachIndexed { i, answer ->
            session.answer(answer.decisionId, answer.choice)
            positions += position(i + 1)
        }
        return GameTruth(gameId, seats, log, status(gameId), ranked, positions, session.events.toList())
    }
}
