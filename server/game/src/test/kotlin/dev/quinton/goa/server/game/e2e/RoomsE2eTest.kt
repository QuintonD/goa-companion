package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.online.AccountStage
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.server.game.lobby.Lobby
import dev.quinton.goa.server.game.room.RoomRules
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** What can go wrong around a room, through the real stack, ending in the words a person reads. */
class RoomsE2eTest {
    private suspend fun Person.createsRoom() = onPhone {
        home.setTeamSize(1)
        home.createRoom()
        home.state.until("an answer") { it.openRoom != null || it.message != null }
    }

    @Test
    fun aFullServerSaysSoInWords(): Unit = runBlocking {
        World(configure = { it.copy(maxOpenRooms = 2) }).use { world ->
            val (ana, bo, cy) = world.signedUp("Ana", "Bo", "Cy")
            assertThat(ana.createsRoom().openRoom).isNotNull()
            assertThat(bo.createsRoom().openRoom).isNotNull()
            val refused = cy.createsRoom()
            assertThat(refused.openRoom).isNull()
            assertThat(refused.message).isEqualTo(OnlineWords.refusal(Refusal.ROOMS_FULL, FULL_RETRY_SECONDS))
            assertThat(refused.message).contains("5 minutes")
            assertThat(world.health().openRooms).isEqualTo(2)
        }
    }

    @Test
    fun drainingBlocksNewRoomsWhileAGameFinishes(): Unit = runBlocking {
        World().use { world ->
            val (ana, bo) = world.signedUp("Ana", "Bo")
            val (cy, di) = world.signedUp("Cy", "Di")
            val running = openRoom(listOf(ana, bo), teamSize = 1)
            running.pickAndReady(lineup(2, Random(11)))
            val gameId = running.started()
            val players = listOf(ana, bo).map { Player(it, gameId) }

            // A room open before the drain: it can't start now, and it isn't what the deploy waits for.
            val waiting = openRoom(listOf(cy, di), teamSize = 1)
            waiting.pickAndReady(lineup(2, Random(12)))
            waiting.hostLobby.until("everyone ready") { it.canStart }

            Files.createFile(world.dir.resolve("drain"))
            val nobody = world.signedUp("Ed").single()
            val refused = nobody.createsRoom()
            assertThat(refused.message).isEqualTo(OnlineWords.refusal(Refusal.DRAINING, DRAIN_RETRY_SECONDS))
            assertThat(world.health().status).isEqualTo("draining")

            cy.onPhone { waiting.sitting.first().lobby.startGame() }
            waiting.hostLobby.until("the refusal") { it.message != null }
            assertThat(waiting.hostLobby.value.message)
                .isEqualTo(OnlineWords.refusal(Refusal.DRAINING, DRAIN_RETRY_SECONDS))
            assertThat(waiting.hostLobby.value.gameId).isNull()

            playToEnd(players)
            assertThat(players.map { it.seen?.online?.rated }.toSet()).containsExactly(true)
            assertWithMessage("what the deploy waits for").that(world.health().rooms).isEqualTo(0)

            Files.delete(world.dir.resolve("drain"))
            assertThat(world.health().status).isEqualTo("open")
        }
    }

    @Test
    fun anExpiredRoomIsGoneForEveryone(): Unit = runBlocking {
        val skew = AtomicLong(0)
        World(
            configure = { it.copy(roomSweepMillis = SWEEP_MILLIS) },
            clock = { System.currentTimeMillis() + skew.get() },
        ).use { world ->
            val (ana, bo, cy) = world.signedUp("Ana", "Bo", "Cy")
            val room = openRoom(listOf(ana, bo), teamSize = 1)
            // Both sockets are watching before the time passes: a watch of an expired room is another case.
            eventually("the rooms being watched") {
                listOf(ana, bo).all { p -> p.connector.wire.any { it.message is ServerMessage.Lobby } }
            }
            val keptOut = cy.onPhone {
                skew.set(TWO_HOURS + 1)
                cy.home.join(room.code)
                cy.home.state.until("the answer") { it.message != null }
            }
            assertThat(keptOut.message).isEqualTo(OnlineWords.refusal(Refusal.NO_SUCH_ROOM))
            room.sitting.forEach { sitting ->
                val shown = sitting.lobby.state.until("the room closing") { it.message != null }
                assertThat(shown.message).isEqualTo(OnlineWords.ROOM_CLOSED)
            }
            assertThat(world.health().rooms).isEqualTo(0)
        }
    }

    @Test
    fun twoPeopleGrabbingOneHeroGetOneAnswerEach(): Unit = runBlocking {
        World().use { world ->
            val people = world.signedUp("Ana", "Bo")
            val room = openRoom(people, teamSize = 1)
            val hero = lineup(1, Random(13)).single()
            room.sitting.map { sitting ->
                async { sitting.person.onPhone { sitting.lobby.pickHero(hero) } }
            }.awaitAll()
            val settled = room.sitting.map { sitting ->
                sitting.lobby.state.until("the picks to land") { ui -> ui.seats.any { it.heroId == hero } }
            }
            val winners = settled.filter { it.yourSeat?.heroId == hero }
            assertThat(winners).hasSize(1)
            val loser = room.sitting.first { it.lobby.state.value.yourSeat?.heroId != hero }
            val told = loser.lobby.state.until("the refusal") { it.message != null }
            assertThat(told.message).isEqualTo(OnlineWords.refusal(Refusal.ROOM_TAKEN))
            assertThat(told.heroes).doesNotContain(hero)
            // And they can go on: another hero, the room carries on.
            loser.hero(told.heroes.first())
            room.sitting.forEach { it.ready() }
            assertThat(room.started()).isNotEmpty()
        }
    }

    @Test
    fun aSeatTakenAtTheSameMomentIsRefusedInWords(): Unit = runBlocking {
        World().use { world ->
            val (ana, bo, cy) = world.signedUp("Ana", "Bo", "Cy")
            val room = openRoom(listOf(ana, bo, cy), teamSize = 2)
            val cySeat = room.sitting[2]
            val taken = room.sitting[1]
            taken.lobby.state.until("Cy's seat") { ui -> ui.seats.any { it.accountId == cy.accountId } }
            val where = taken.lobby.state.value.seats.first { it.accountId == cy.accountId }
            bo.onPhone { taken.lobby.pickSeat(where.team, where.index) }
            val shown = taken.lobby.state.value
            assertThat(shown.message).isEqualTo(OnlineWords.SEAT_TAKEN)
            assertThat(shown.seats.first { it.team == where.team && it.index == where.index }.accountId)
                .isEqualTo(cy.accountId)
            cySeat.seat(Team.BLUE, 1)
        }
    }

    @Test
    fun aBadTokenSaysSoAndTheRecoveryCodeBringsTheAccountBack(): Unit = runBlocking {
        World().use { world ->
            val ana = world.signedUp("Ana").single()
            val account = ana.accountId
            val code = checkNotNull(ana.lastRecoveryCode)

            // The same person on a phone whose saved token the server no longer knows.
            val phone = world.person("Ana's old phone")
            phone.identities.identity = OnlineIdentity(account, "the-server-does-not-know-this-token", "Ana")
            val shown = phone.onPhone {
                phone.home.start()
                phone.home.state.until("being told") { it.message != null }
            }
            assertWithMessage("the words for a token the server doesn't know")
                .that(shown.message).isEqualTo(OnlineWords.refusal(Refusal.BAD_TOKEN))
            assertThat(shown.stage).isEqualTo(AccountStage.PICK_NAME)

            val driver = phone.driver("no-such-game")
            phone.onPhone { driver.start() }
            driver.state.until("the socket's refusal") { it is OnlineState.Refused }
            assertThat(driver.state.value).isEqualTo(OnlineState.Refused(Refusal.BAD_TOKEN))

            // A wrong code first: that's not the server being down.
            val wrong = phone.onPhone {
                phone.home.setLinkCode("NOT-THE-CODE")
                phone.home.linkDevice()
                phone.home.state.until("the answer") { it.message != null && !it.working }
            }
            assertThat(wrong.message).isEqualTo(OnlineWords.BAD_RECOVERY_CODE)

            val linked = phone.onPhone {
                phone.home.setLinkCode(code)
                phone.home.linkDevice()
                phone.home.state.until("the new code") { it.stage == AccountStage.SAVE_CODE }
            }
            assertThat(linked.recoveryCode).isNotEqualTo(code)
            assertThat(linked.name).isEqualTo("Ana")
            phone.onPhone { phone.home.recoveryCodeSaved() }
            phone.home.state.until("the home screen") { it.stage == AccountStage.READY }
            assertThat(phone.accountId).isEqualTo(account)
            // Linking burned the old code.
            val burned = world.person("Thief")
            val tried = burned.onPhone {
                burned.home.start()
                burned.home.state.until("the name field") { it.stage == AccountStage.PICK_NAME }
                burned.home.setLinkCode(code)
                burned.home.linkDevice()
                burned.home.state.until("the answer") { it.message != null && !it.working }
            }
            assertThat(tried.message).isEqualTo(OnlineWords.BAD_RECOVERY_CODE)
        }
    }

    @Test
    fun aRefusedNameIsNotTheServerBeingDown(): Unit = runBlocking {
        World().use { world ->
            val person = world.person("Nameless")
            val told = person.onPhone {
                person.home.start()
                person.home.state.until("the name field") { it.stage == AccountStage.PICK_NAME }
                person.home.setName("<b>")
                person.home.createAccount()
                person.home.state.until("the answer") { it.message != null && !it.working }
            }
            assertThat(told.message).isEqualTo(OnlineWords.BAD_NAME)
            assertThat(world.getText(OnlineRoutes.HEALTH).statusCode()).isEqualTo(HTTP_OK)
        }
    }

    @Test
    fun aRoomWaitsThroughARestartAndStillStarts(): Unit = runBlocking {
        World().use { world ->
            val people = world.signedUp("Ana", "Bo")
            val room = openRoom(people, teamSize = 1)
            room.pickAndReady(lineup(2, Random(14)))
            room.hostLobby.until("everyone ready") { it.canStart }
            world.restart()
            room.sitting.forEach { sitting ->
                sitting.lobby.state.until("the room back", RECONNECT_MILLIS) {
                    it.connected && it.message == null && it.yourSeat?.ready == true
                }
            }
            assertThat(world.health().openRooms).isEqualTo(1)
            val gameId = room.started()
            val players = people.map { Player(it, gameId) }
            playToEnd(players)
            assertThat(players.map { it.seen?.online?.rated }.toSet()).containsExactly(true)
        }
    }

    /** A deploy stops the server with people connected: an ERROR per socket would hide a real one. */
    @Test
    fun aStopWithPeopleConnectedIsNotAFailureInTheLog(): Unit = runBlocking {
        World().use { world ->
            openRoom(world.signedUp("Ana", "Bo"), teamSize = 1)
            val original = System.out
            val captured = ByteArrayOutputStream()
            System.setOut(PrintStream(captured, true, Charsets.UTF_8))
            try {
                world.stopServer()
            } finally {
                System.setOut(original)
            }
            assertThat(captured.toString(Charsets.UTF_8)).contains("socket closed")
            assertThat(captured.toString(Charsets.UTF_8)).doesNotContain("unexpected failure")
        }
    }

    /** Games that ended with nobody told aren't running: they don't use up the 3 a person may play at once. */
    @Test
    fun gamesThatEndedWhileAwayDontCountAsRunning(): Unit = runBlocking {
        World(rules = RoomRules(disconnectedMillis = AWAY_MILLIS), beatMillis = FAST_BEAT_MILLIS).use { world ->
            val (ana, bo) = world.signedUp("Ana", "Bo")
            val games = List(Lobby.MAX_RUNNING_GAMES) { n ->
                val room = openRoom(listOf(ana, bo), teamSize = 1)
                room.pickAndReady(lineup(2, Random(40 + n)))
                room.started()
            }
            // Bo's phone stays in their pocket: Ana plays, the computer answers for Bo, all three games end.
            val players = games.map { Player(ana, it) }
            players.forEach { it.start() }
            players.forEach { assertThat(it.finished(GAME_LIMIT_MILLIS)).isTrue() }
            eventually("the games to close") { world.rooms.runningCount == 0 }
            assertThat(world.rooms.gamesOf(bo.accountId)).hasSize(Lobby.MAX_RUNNING_GAMES)
            val told = bo.createsRoom()
            assertWithMessage("words: ${told.message}").that(told.openRoom).isNotNull()
        }
    }

    /** The recovery code links a second phone; either plays the seat, and the other keeps up. */
    @Test
    fun twoPhonesOfOnePersonPlayOneSeat(): Unit = runBlocking {
        World().use { world ->
            val (ana, bo) = world.signedUp("Ana", "Bo")
            val second = world.person("Ana's tablet")
            second.onPhone {
                second.home.start()
                second.home.state.until("the name field") { it.stage == AccountStage.PICK_NAME }
                second.home.setLinkCode(checkNotNull(ana.lastRecoveryCode))
                second.home.linkDevice()
                second.home.state.until("the new code") { it.stage == AccountStage.SAVE_CODE }
                second.home.recoveryCodeSaved()
                second.home.state.until("the home screen") { it.stage == AccountStage.READY }
            }
            assertThat(second.accountId).isEqualTo(ana.accountId)
            val room = openRoom(listOf(ana, bo), teamSize = 1)
            room.pickAndReady(lineup(2, Random(15)))
            val gameId = room.started()
            val first = Player(ana, gameId, hold = { it.table.view.turn % 2 == 0 })
            val tablet = Player(second, gameId, hold = { it.table.view.turn % 2 == 1 })
            val other = Player(bo, gameId)
            val players = listOf(first, tablet, other)
            playToEnd(players)
            val truth = world.log.truth(gameId)
            assertThat(truth.answers.filter { it.origin == "clock" }).isEmpty()
            assertThat(rebuiltLog(second.tables(gameId))).isEqualTo(truth.events)
            assertThat(rebuiltLog(ana.tables(gameId))).isEqualTo(truth.events)
            assertThat(players.map { it.seen?.online?.rated }.toSet()).containsExactly(true)
        }
    }

    private companion object {
        const val AWAY_MILLIS = 50L
        const val RECONNECT_MILLIS = 40_000L
        const val FULL_RETRY_SECONDS = 300
        const val DRAIN_RETRY_SECONDS = 3600
        const val TWO_HOURS = 2 * 60 * 60_000L
        const val SWEEP_MILLIS = 200L
        const val HTTP_OK = 200
    }
}
