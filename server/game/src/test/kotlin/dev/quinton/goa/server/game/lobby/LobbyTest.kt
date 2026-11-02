package dev.quinton.goa.server.game.lobby

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.server.game.FakeRooms
import dev.quinton.goa.server.game.TestClock
import dev.quinton.goa.server.game.room.GameRooms
import dev.quinton.goa.server.game.store.AccountRow
import dev.quinton.goa.server.game.store.Secrets
import dev.quinton.goa.server.game.store.SqliteGameStore
import dev.quinton.goa.server.game.testEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class LobbyTest {
    private lateinit var dir: Path
    private lateinit var store: SqliteGameStore
    private val rooms = FakeRooms()
    private val clock = TestClock()
    private val heard = ArrayList<RoomView>()
    private var nextCoin = true
    private var draining = false

    private val lobby by lazy {
        Lobby(
            store = store,
            engine = testEngine,
            rooms = rooms,
            maxRooms = 50,
            draining = { draining },
            clock = clock::read,
            coin = { nextCoin },
            onChange = { heard += it },
        )
    }

    @Before
    fun open() {
        dir = Files.createTempDirectory("goa-lobby-test")
        store = SqliteGameStore.open(dir)
    }

    @After
    fun close() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private suspend fun people(count: Int): List<String> = List(count) { i ->
        "p$i".also { store.createAccount(AccountRow(it, "Person $i", 1), "token-$i", "code-$i") }
    }

    /** A room with everyone seated, on a hero, and ready, host first. */
    private suspend fun fullRoom(
        teamSize: Int,
        length: GameLength = GameLength.QUICK,
        ranked: Boolean = false,
    ): Pair<String, List<String>> {
        val everyone = people(teamSize * 2)
        val request = CreateRoom(Lobby.mapFor(teamSize), length, teamSize, ranked)
        val code = lobby.create(everyone.first(), request).code
        val heroes = testEngine.playableHeroes.sorted()
        everyone.forEachIndexed { i, person ->
            val seat = lobby.join(person, code).seats.first { it.accountId == person }
            lobby.pickSeat(person, code, seat.team, seat.index, heroes[i])
            lobby.ready(person, code, true)
        }
        return code to everyone
    }

    private suspend fun refused(block: suspend () -> Unit): LobbyError {
        try {
            block()
        } catch (refusal: LobbyRefused) {
            return refusal.error
        }
        fail("Expected a refusal")
        error("unreachable")
    }

    @Test
    fun everySizeOfTheModeReallyStartsOnTheRealEngine(): Unit = runBlocking {
        for (size in Lobby.TEAM_SIZES) {
            for (length in GameLength.entries) {
                val lobbyRooms = FakeRooms()
                val freshDir = Files.createTempDirectory("goa-lobby-size")
                val freshStore = SqliteGameStore.open(freshDir)
                try {
                    val fresh = Lobby(freshStore, testEngine, lobbyRooms, 50, { false }, clock::read)
                    val people = List(size * 2) { i ->
                        "s$i".also { freshStore.createAccount(AccountRow(it, "Seat $i", 1), "t$i", "c$i") }
                    }
                    val code = fresh.create(people[0], CreateRoom(Lobby.mapFor(size), length, size, true)).code
                    val heroes = testEngine.playableHeroes.sorted()
                    people.forEachIndexed { i, person ->
                        val seat = fresh.join(person, code).seats.first { it.accountId == person }
                        fresh.pickSeat(person, code, seat.team, seat.index, heroes[i])
                        fresh.ready(person, code, true)
                    }
                    val started = fresh.start(people[0], code)
                    assertThat(started.status).isEqualTo(RoomStatus.STARTED)
                    val game = lobbyRooms.games.values.single()
                    assertThat(game.setup.seats).hasSize(size * 2)
                    assertThat(game.setup.length).isEqualTo(length)
                    assertThat(game.ranked).isTrue()
                } finally {
                    freshStore.close()
                    freshDir.toFile().deleteRecursively()
                }
            }
        }
    }

    @Test
    fun theMapsOfTheModeAreOnTheEnginesList() {
        assertThat(testEngine.playableMaps).containsAtLeast(Lobby.FORGOTTEN_ISLAND, Lobby.ACROSS_THE_RIVER)
        assertThat(Lobby.mapFor(3)).isEqualTo(Lobby.FORGOTTEN_ISLAND)
        assertThat(Lobby.mapFor(4)).isEqualTo(Lobby.ACROSS_THE_RIVER)
    }

    @Test
    fun theServerFlipsTheCoinAndSeatsGoInPickOrderEveryOneAPerson(): Unit = runBlocking {
        val (code, everyone) = fullRoom(2)
        nextCoin = false
        lobby.start(everyone.first(), code)
        val game = rooms.games.values.single()
        assertThat(game.setup.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(game.setup.seats.map { it.team }).containsExactly(Team.RED, Team.RED, Team.BLUE, Team.BLUE).inOrder()
        assertThat(game.setup.seats.all { it.controller == Controller.Human }).isTrue()
        assertThat(game.setup.seats.map { it.heroId }.toSet()).hasSize(4)
        assertThat(game.players.map { it.seat }).containsExactlyElementsIn(game.setup.seats.map { it.heroId })
        assertThat(game.players.all { it.name.startsWith("Person ") }).isTrue()
    }

    @Test
    fun theRealCoinLandsBothWays() {
        assertThat(List(200) { Secrets.coin() }.toSet()).containsExactly(true, false)
    }

    @Test
    fun aStartIsRefusedWhileDrainingAndWorksAfter(): Unit = runBlocking {
        val (code, everyone) = fullRoom(1)
        draining = true
        assertThat(refused { lobby.start(everyone.first(), code) }).isEqualTo(LobbyError.DRAINING)
        assertThat(rooms.games).isEmpty()
        draining = false
        assertThat(lobby.start(everyone.first(), code).status).isEqualTo(RoomStatus.STARTED)
    }

    @Test
    fun onlyTheHostStartsAndAStartIsDoneOnce(): Unit = runBlocking {
        val (code, everyone) = fullRoom(1)
        assertThat(refused { lobby.start(everyone.last(), code) }).isEqualTo(LobbyError.NOT_HOST)
        val first = lobby.start(everyone.first(), code)
        assertThat(lobby.start(everyone.first(), code)).isEqualTo(first)
        assertThat(rooms.games).hasSize(1)
    }

    @Test
    fun aRoomThatFailsItsPreflightStaysOpen(): Unit = runBlocking {
        val everyone = people(2)
        val code = lobby.create(everyone[0], CreateRoom(Lobby.FORGOTTEN_ISLAND, GameLength.QUICK, 1, false)).code
        val row = store.room(code)!!
        store.putRoom(
            row.copy(
                seats = listOf(
                    row.seats[0].copy(heroId = "not-a-hero", ready = true),
                    row.seats[1].copy(accountId = everyone[1], heroId = "arien", ready = true),
                ),
            ),
        )
        assertThat(refused { lobby.start(everyone[0], code) }).isEqualTo(LobbyError.SETUP_REFUSED)
        assertThat(rooms.games).isEmpty()
        assertThat(store.room(code)?.status).isEqualTo(RoomStatus.OPEN)
    }

    @Test
    fun aRoomCoreFailureLeavesTheRoomOpen(): Unit = runBlocking {
        val failing = object : GameRooms by rooms {
            override suspend fun start(
                roomCode: String,
                setup: GameSetup,
                players: List<SeatPlayer>,
                ranked: Boolean,
            ): String = error("Room core down")
        }
        val fragile = Lobby(store, testEngine, failing, 50, { false }, clock::read)
        val (code, everyone) = fullRoom(1)
        var failed = false
        try {
            fragile.start(everyone.first(), code)
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertThat(failed).isTrue()
        assertThat(store.room(code)?.status).isEqualTo(RoomStatus.OPEN)
    }

    @Test
    fun aSweepClosesExpiredRoomsAndTellsWatchers(): Unit = runBlocking {
        val everyone = people(1)
        val code = lobby.create(everyone[0], CreateRoom(Lobby.FORGOTTEN_ISLAND, GameLength.QUICK, 1, false)).code
        heard.clear()
        clock.now += Lobby.OPEN_MILLIS - 1
        lobby.sweep()
        assertThat(heard).isEmpty()
        clock.now += 1
        lobby.sweep()
        assertThat(heard.single().status).isEqualTo(RoomStatus.CLOSED)
        assertThat(store.room(code)?.status).isEqualTo(RoomStatus.CLOSED)
        assertThat(refused { lobby.view(code) }).isEqualTo(LobbyError.NO_SUCH_ROOM)
    }

    @Test
    fun onlyRealChangesAreAnnounced(): Unit = runBlocking {
        val everyone = people(2)
        val code = lobby.create(everyone[0], CreateRoom(Lobby.FORGOTTEN_ISLAND, GameLength.QUICK, 1, false)).code
        assertThat(heard).hasSize(1)
        lobby.join(everyone[1], code)
        assertThat(heard).hasSize(2)
        lobby.join(everyone[1], code)
        lobby.ready(everyone[1], code, false)
        assertThat(heard).hasSize(2)
    }
}
