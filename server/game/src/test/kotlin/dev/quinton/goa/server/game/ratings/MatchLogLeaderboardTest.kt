package dev.quinton.goa.server.game.ratings

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.LeaderRow
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.server.game.store.GameResult
import dev.quinton.goa.server.game.store.GameRow
import dev.quinton.goa.server.game.store.GameSeatRow
import dev.quinton.goa.server.game.store.InMemoryGameStore
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MatchLogLeaderboardTest {
    private val names = mapOf("ann" to "Ann", "bob" to "Bob", "cid" to "Cid", "dee" to "Dee")

    private fun heroName(id: String): String? = id.replaceFirstChar { it.uppercase() }

    private fun match(
        id: String,
        playedAt: Long,
        red: Pair<String, String> = "ann" to "arien",
        blue: Pair<String, String> = "bob" to "brogan",
        winner: Team? = Team.RED,
        status: MatchStatus = MatchStatus.COMPLETED,
    ) = Match(
        id = id,
        createdAt = playedAt - 1,
        playedAt = playedAt,
        status = status,
        mode = GameMode.QUICK,
        winner = winner,
        participants = listOf(
            Participant(red.first, red.second, Team.RED),
            Participant(blue.first, blue.second, Team.BLUE),
        ),
    )

    /** [games] rated games, red and blue winning by turns. */
    private fun log(games: Int, red: Pair<String, String> = "ann" to "arien", prefix: String = "m"): List<Match> =
        List(games) { i ->
            match("$prefix$i", 1_000L + i, red = red, winner = if (i % 3 == 2) Team.BLUE else Team.RED)
        }

    private fun source(
        matches: () -> List<Match>,
        accounts: () -> Map<String, String> = { names },
        clock: () -> Long = { NOW },
    ) = MatchLogLeaderboard(
        matches = { matches() },
        names = { accounts() },
        heroName = ::heroName,
        clock = clock,
        ttlMillis = 0,
    )

    @Test
    fun aSnapshotIsKeptThirtySecondsAndTheLogIsDecodedOnceForIt() = runBlocking<Unit> {
        var reads = 0
        var nameReads = 0
        var accounts = names
        var now = NOW
        val board = MatchLogLeaderboard(
            matches = {
                reads++
                log(6)
            },
            names = { ids ->
                nameReads++
                accounts.filterKeys { it in ids }
            },
            heroName = ::heroName,
            clock = { now },
        )
        val first = board.snapshot()
        repeat(50) { assertThat(board.snapshot()).isEqualTo(first) }
        assertThat(reads).isEqualTo(1)
        assertThat(nameReads).isEqualTo(1)

        // A rename shows once the snapshot has gone.
        accounts = names + ("ann" to "Annie")
        now += MatchLogLeaderboard.DEFAULT_TTL_MILLIS - 1
        assertThat(board.snapshot().players.map { it.name }).contains("Ann")
        now += 1
        val next = board.snapshot()
        assertThat(next.players.map { it.name }).contains("Annie")
        assertThat(next.generatedAt).isEqualTo(now)
        assertThat(reads).isEqualTo(2)
    }

    @Test
    fun namesAreAskedOnlyForThePeopleOnTheBoard() = runBlocking<Unit> {
        var asked: Collection<String> = emptyList()
        val rare = match("rare", 500, red = "cid" to "wasp", blue = "dee" to "xargatha")
        MatchLogLeaderboard(
            matches = { log(6) + rare },
            names = { ids ->
                asked = ids
                names
            },
            heroName = ::heroName,
            clock = { NOW },
        ).snapshot()
        assertThat(asked).containsExactly("ann", "bob")
    }

    @Test
    fun aSnapshotIsNeverWrittenDown() {
        val source = java.io.File("src/main/kotlin/dev/quinton/goa/server/game/store/Schema.kt").readText()
        assertThat(source).doesNotContain("snapshot")
    }

    private suspend fun board(log: List<Match>): LeaderboardSnapshot = source({ log }).snapshot()

    @Test
    fun theSnapshotCarriesItsSchemaAndConfig() = runBlocking<Unit> {
        val snapshot = board(log(6))
        assertThat(snapshot.schema).isEqualTo(LeaderboardSnapshot.SCHEMA)
        assertThat(snapshot.generatedAt).isEqualTo(NOW)
        assertThat(snapshot.matches).isEqualTo(6)
        assertThat(snapshot.eloConfig).isEqualTo(ELO_CONFIG_VERSION)
    }

    @Test
    fun ratingsAreTheReplayRounded() = runBlocking<Unit> {
        val log = log(12)
        val replay = EloCalculator.replay(log, EloConfig.DEFAULT)
        val snapshot = board(log)
        val ann = snapshot.players.single { it.id == "ann" }
        val rating = replay.player("ann")
        assertThat(ann).isEqualTo(LeaderRow("ann", "Ann", rating.value.roundToInt(), rating.games, rating.wins))
        val arien = snapshot.heroes.single { it.id == "arien" }
        val hero = replay.hero("arien")
        assertThat(arien).isEqualTo(LeaderRow("arien", "Arien", hero.value.roundToInt(), hero.games, hero.wins))
    }

    @Test
    fun onlyTheMatchesAreNeededToRebuildTheBoard() = runBlocking<Unit> {
        val log = log(20)
        val first = board(log)
        // A new process over the same rows: no cache, no store but the log.
        val second = board(log.toList())
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun theOrderOfInsertionChangesNothing() = runBlocking<Unit> {
        val log = log(25) + log(25, red = "cid" to "cutter", prefix = "n")
        val first = board(log)
        repeat(5) { seed ->
            assertThat(board(log.shuffled(Random(seed)))).isEqualTo(first)
        }
    }

    @Test
    fun fewerThanFiveGamesStaysOffTheBoard() = runBlocking<Unit> {
        val four = board(log(4))
        assertThat(four.players).isEmpty()
        assertThat(four.heroes).isEmpty()
        assertThat(four.matches).isEqualTo(4)

        val five = board(log(5))
        assertThat(five.players.map { it.id }).containsExactly("ann", "bob")
        assertThat(five.heroes.map { it.id }).containsExactly("arien", "brogan")
        assertThat(five.players.map { it.games }).containsExactly(5, 5)
    }

    @Test
    fun aVoidedGameNeverReachesTheLogOrTheBoard() = runBlocking<Unit> {
        val store = InMemoryGameStore()
        for (m in log(6)) store.finish(m.id, GameResult(m.playedAt, rated = true), m)
        val board = MatchLogLeaderboard({ store.matches() }, { names }, ::heroName, { NOW })
        val expected = board.snapshot()

        store.createGame(voidable("voided"), emptyList<GameSeatRow>())
        store.void("voided", VoidReason.RULES_CHANGED, NOW)
        store.createGame(voidable("abandoned"), emptyList<GameSeatRow>())
        store.void("abandoned", VoidReason.ABANDONED, NOW)
        // An unrated game closes without a match.
        store.createGame(voidable("friendly"), emptyList<GameSeatRow>())
        store.finish("friendly", GameResult(NOW, rated = false), null)

        assertThat(store.matches().map { it.id }).doesNotContain("voided")
        assertThat(board.snapshot()).isEqualTo(expected)
    }

    @Test
    fun aRowTheReplaySkipsMovesNeitherTheBoardNorTheHash() = runBlocking<Unit> {
        val log = log(6)
        val stray = listOf(
            match("abandoned", 1_001, status = MatchStatus.ABANDONED, winner = null),
            match("pending", 1_002, status = MatchStatus.PENDING, winner = null),
            match("no-winner", 1_003, winner = null),
        )
        assertThat(board(log + stray)).isEqualTo(board(log))
    }

    @Test
    fun theHashChangesOnlyWhenTheLogChanges() = runBlocking<Unit> {
        val log = log(6)
        val hash = board(log).logSha256
        assertThat(hash).matches("[0-9a-f]{64}")

        assertThat(board(log.reversed()).logSha256).isEqualTo(hash)
        assertThat(source({ log }, accounts = { mapOf("ann" to "Renamed") }).snapshot().logSha256).isEqualTo(hash)
        assertThat(source({ log }, clock = { NOW + 999_999 }).snapshot().logSha256).isEqualTo(hash)

        assertThat(board(log + match("new", 9_000)).logSha256).isNotEqualTo(hash)
        val edited = log.mapIndexed { i, m -> if (i == 3) m.copy(winner = m.winner?.opponent) else m }
        assertThat(board(edited).logSha256).isNotEqualTo(hash)
        val noted = log.mapIndexed { i, m -> if (i == 3) m.copy(notes = "changed") else m }
        assertThat(board(noted).logSha256).isNotEqualTo(hash)
    }

    @Test
    fun theHashOfAFixedLogIsPinned() {
        // Across deploys the hash must stay: a change here (a new field on Match) republishes the board once.
        val log = listOf(match("m0", 1_000))
        assertThat(MatchLogLeaderboard.logSha256(log)).isEqualTo(PINNED_LOG_HASH)
    }

    @Test
    fun anEmptyLogIsAnEmptyBoardWithAStableHash() = runBlocking<Unit> {
        val empty = board(emptyList())
        assertThat(empty.matches).isEqualTo(0)
        assertThat(empty.players).isEmpty()
        assertThat(empty.heroes).isEmpty()
        assertThat(empty.logSha256).isEqualTo(board(emptyList()).logSha256)
    }

    @Test
    fun aDeletedAccountStaysListedAsAFormerPlayer() = runBlocking<Unit> {
        val log = log(6)
        val before = board(log)
        val anonymised = source({ log }, accounts = { names + ("ann" to FORMER_PLAYER) }).snapshot()
        val gone = source({ log }, accounts = { names - "ann" }).snapshot()
        for (after in listOf(anonymised, gone)) {
            val row = after.players.single { it.id == "ann" }
            assertThat(row.name).isEqualTo(FORMER_PLAYER)
            assertThat(row.rating).isEqualTo(before.players.single { it.id == "ann" }.rating)
            assertThat(after.logSha256).isEqualTo(before.logSha256)
        }
    }

    @Test
    fun aHeroTheCatalogNoLongerHasShowsItsId() = runBlocking<Unit> {
        val snapshot = MatchLogLeaderboard({ log(6) }, { names }, { null }, { NOW }).snapshot()
        assertThat(snapshot.heroes.map { it.name }).containsExactly("arien", "brogan")
    }

    @Test
    fun aRenameShowsWithoutANewReplay() = runBlocking<Unit> {
        val log = log(6)
        var accounts = names
        var replays = 0
        var now = NOW
        val board = MatchLogLeaderboard(
            matches = { log },
            names = { accounts },
            heroName = ::heroName,
            clock = { now },
            replay = {
                replays++
                EloCalculator.replay(it, EloConfig.DEFAULT)
            },
        )
        val first = board.snapshot()
        accounts = names + ("ann" to "Annie")
        now += 60_000
        val second = board.snapshot()

        assertThat(replays).isEqualTo(1)
        assertThat(second.players.single { it.id == "ann" }.name).isEqualTo("Annie")
        assertThat(second.generatedAt).isEqualTo(now)
        assertThat(second.logSha256).isEqualTo(first.logSha256)
        assertThat(second.players.map { it.rating }).isEqualTo(first.players.map { it.rating })
    }

    @Test
    fun aNewMatchReplaysAgain() = runBlocking<Unit> {
        var current = log(6)
        var replays = 0
        val board = MatchLogLeaderboard(
            matches = { current },
            names = { names },
            heroName = ::heroName,
            clock = { NOW },
            ttlMillis = 0,
            replay = {
                replays++
                EloCalculator.replay(it, EloConfig.DEFAULT)
            },
        )
        val before = board.snapshot()
        current = current + match("new", 9_000)
        val after = board.snapshot()

        assertThat(replays).isEqualTo(2)
        assertThat(after.matches).isEqualTo(before.matches + 1)
        assertThat(after.players.single { it.id == "ann" }.games).isEqualTo(7)
    }

    @Test
    fun boardsAreSortedByRatingThenIdAndCapped() = runBlocking<Unit> {
        // Pairs that each play 5 games among themselves: winners tie on rating, so do losers.
        val pairs = 150
        val log = (0 until pairs).flatMap { p ->
            log(5, red = "w%03d".format(p) to "hw%03d".format(p), prefix = "p$p-").map { m ->
                m.copy(
                    winner = Team.RED,
                    participants = listOf(
                        Participant("w%03d".format(p), "hw%03d".format(p), Team.RED),
                        Participant("l%03d".format(p), "hl%03d".format(p), Team.BLUE),
                    ),
                )
            }
        }
        val snapshot = board(log)

        for (rows in listOf(snapshot.players, snapshot.heroes)) {
            assertThat(rows).hasSize(BOARD_ROWS)
            val order = compareByDescending<LeaderRow> { it.rating }.thenBy { it.id }
            assertThat(rows).isInOrder(order)
        }
        // All 150 winners first, then the losers cut at the cap, by id.
        assertThat(snapshot.players.first().id).isEqualTo("w000")
        assertThat(snapshot.players[149].id).isEqualTo("w149")
        assertThat(snapshot.players[150].id).isEqualTo("l000")
        assertThat(snapshot.players.last().id).isEqualTo("l049")
    }

    @Test
    fun theConfigVersionGoesUpWithTheDefault() {
        // If this fails, EloConfig.DEFAULT changed: bump ELO_CONFIG_VERSION and update this copy.
        assertThat(ELO_CONFIG_VERSION).isEqualTo(1)
        assertThat(EloConfig.DEFAULT).isEqualTo(
            EloConfig(
                initialRating = 1200.0,
                kProvisional = 40.0,
                kEstablished = 20.0,
                provisionalGames = 10,
                heroInfluence = 0.5,
                pilotInfluence = 0.5,
                seatAdvantage = 0.0,
                doublePilotWeight = 0.5,
                quickMultiplier = 1.0,
                longMultiplier = 1.0,
                doubleLaneMultiplier = 1.0,
            ),
        )
    }

    @Test
    fun theMapperFeedsTheBoard() = runBlocking<Unit> {
        val mapper = LogMatchMapper { 1 }
        val game = voidable("g1")
        val seats = listOf(
            GameSeatRow("g1", "arien", "ann", Team.RED, handicap = false, seatOrder = 0),
            GameSeatRow("g1", "brogan", "bob", Team.BLUE, handicap = false, seatOrder = 1),
        )
        val log = List(5) { i ->
            val reason = WinReason.entries[i % WinReason.entries.size]
            mapper.matchOf(game.copy(id = "g$i"), seats, Team.RED, reason, 2_000L + i)
        }
        val snapshot = board(log)
        assertThat(snapshot.matches).isEqualTo(5)
        assertThat(snapshot.players.first().id).isEqualTo("ann")
        assertThat(snapshot.players.first().wins).isEqualTo(5)
    }

    private fun voidable(id: String): GameRow {
        val setup = GameSetup(
            mapId = "forgotten-island",
            seats = listOf(
                SeatSetup("arien", Team.RED, Controller.Human),
                SeatSetup("brogan", Team.BLUE, Controller.Human),
            ),
            tieBreaker = Team.RED,
        )
        return GameRow(
            id = id,
            roomCode = "ABCD2345",
            setup = setup,
            start = SavedGame(engineVersion = 1, setup = setup, payload = ""),
            rulesBuild = "test",
            engineVersion = 1,
            ranked = true,
            mapId = setup.mapId,
            length = GameLength.QUICK,
            createdAt = 1,
        )
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
        const val PINNED_LOG_HASH = "c29dbaddb1f5e7aeddcf2429772e334d25ae6c3c64e4b5b29153c6ef34f40382"
    }
}
