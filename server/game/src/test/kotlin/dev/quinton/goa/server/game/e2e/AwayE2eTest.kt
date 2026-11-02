package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.OnlineResult
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.server.game.room.RoomRules
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The ways a person comes back to a game that went on, or ended, without them. */
class AwayE2eTest {
    /** A 1v1 where [away]'s phone is dead from the moment the game is under way until it has ended. */
    private suspend fun aGameThatEndsWithoutThem(world: World, here: Person, away: Person): Finished {
        val room = openRoom(listOf(here, away), teamSize = 1)
        room.pickAndReady(lineup(2, Random(21)))
        val gameId = room.started()
        val players = listOf(Player(here, gameId), Player(away, gameId))
        players.forEach { it.start() }
        eventually("a game under way", GAME_LIMIT_MILLIS) { world.log.answers(gameId).size > UNDER_WAY }
        away.connector.offline = true
        away.connector.killSockets()
        val log = rebuiltLog(away.tables(gameId))
        eventually("the game ending without them", GAME_LIMIT_MILLIS) { world.log.status(gameId) == "finished" }
        return Finished(gameId, players, room, log.size)
    }

    private class Finished(val gameId: String, val players: List<Player>, val room: Room, val heldEvents: Int)

    @Test
    fun theDeviceThatWasPlayingHearsHowItEndedAndSeesTheWholeGame(): Unit = runBlocking {
        World(rules = RoomRules(disconnectedMillis = AWAY_MILLIS), beatMillis = FAST_BEAT_MILLIS).use { world ->
            val (here, away) = world.signedUp("Ana", "Bo")
            val game = aGameThatEndsWithoutThem(world, here, away)
            val phone = game.players.last()

            away.connector.offline = false
            away.onPhone { phone.driver.resumeNow() }
            phone.driver.state.until("the end") { it.play?.online?.rated != null }
            val truth = world.log.truth(game.gameId)
            val ui = checkNotNull(phone.screens.play)
            assertThat(ui.online?.rated).isTrue()
            assertThat(ui.over?.youWon).isEqualTo(truth.positions.last().spectator.winner == game.room.teamOf(away))
            assertWithMessage("the board they end on is the last one").that(ui.table.view.phase)
                .isEqualTo(truth.positions.last().views.getValue(game.room.heroOf(away)).phase)
            assertThat(ui.log.map { it.text }).isEqualTo(truth.events.map { it.text })
        }
    }

    @Test
    fun aNewDeviceOrAFreshlyOpenedAppSeesTheResultToo(): Unit = runBlocking {
        World(rules = RoomRules(disconnectedMillis = AWAY_MILLIS), beatMillis = FAST_BEAT_MILLIS).use { world ->
            val (here, away) = world.signedUp("Ana", "Bo")
            val game = aGameThatEndsWithoutThem(world, here, away)
            away.connector.offline = false
            val games = (away.gateway.myGames() as OnlineResult.Ok).value.games
            assertWithMessage("it's on the list of games to go back to").that(games.map { it.gameId })
                .contains(game.gameId)
            assertThat(world.rooms.gamesOf(away.accountId)).contains(game.gameId)

            val reopened = away.driver(game.gameId)
            away.onPhone { reopened.start() }
            val ui = reopened.state.until("the result") { it.play?.over != null && it.play?.online?.rated != null }.play!!
            val truth = world.log.truth(game.gameId)
            assertThat(ui.over?.winner).isEqualTo(truth.positions.last().spectator.winner)
            assertThat(ui.log.map { it.text }).isEqualTo(truth.events.map { it.text })

            // And after a restart, which forgets who had heard what: it is on the disk.
            world.restart()
            val again = away.driver(game.gameId)
            away.onPhone { again.start() }
            val after = again.state.until("the result again") { it.play?.online?.rated != null }.play!!
            assertThat(after.over?.winner).isEqualTo(truth.positions.last().spectator.winner)
            // A seat given up doesn't come back this way.
            val stranger = world.signedUp("Cy").single()
            val nothing = stranger.driver(game.gameId)
            stranger.onPhone { nothing.start() }
            nothing.state.until("being told") { it !is OnlineState.Connecting }
            assertThat(nothing.state.value).isEqualTo(OnlineState.Refused(Refusal.NO_SUCH_ROOM))
            assertThat(stranger.tables(game.gameId)).isEmpty()
        }
    }

    @Test
    fun aPersonSittingThereWhoDoesntAnswerIsAnsweredForAndSeesIt(): Unit = runBlocking {
        val rules = RoomRules(playCardMillis = CLOCK_MILLIS, otherMillis = CLOCK_MILLIS)
        World(rules = rules).use { world ->
            val (ana, bo) = world.signedUp("Ana", "Bo")
            val room = openRoom(listOf(ana, bo), teamSize = 1)
            room.pickAndReady(lineup(2, Random(23)))
            val gameId = room.started()
            // Bo answers everything but the card of the turn; Ana answers nothing but her card.
            val idle = Player(bo, gameId, hold = { it.table.decision?.kind == DecisionKind.PLAY_CARD })
            val active = Player(ana, gameId)
            listOf(idle, active).forEach { it.start() }
            val hero = room.heroOf(bo)
            eventually("the clock's answer for the card", GAME_LIMIT_MILLIS) {
                world.log.answers(gameId).any { it.seat == hero && it.origin == "clock" }
            }
            eventually("a screen in step with it") {
                val ui = idle.screens.play
                ui != null && ui.table.heroView(hero)?.played != null && !ui.table.asking
            }
            assertThat(idle.screens.play?.status).isNotEqualTo(TurnStatus.YourTurn)
            assertThat(idle.screens).isInstanceOf(OnlineState.InGame::class.java)
        }
    }

    @Test
    fun whenEveryoneIsGoneTheClocksPauseAndADayLaterItIsClosedInWords(): Unit = runBlocking {
        val rules = RoomRules(
            disconnectedMillis = AWAY_MILLIS,
            allAwayPauseMillis = PAUSE_MILLIS,
            abandonMillis = ABANDON_MILLIS,
        )
        World(rules = rules).use { world ->
            val (ana, bo) = world.signedUp("Ana", "Bo")
            val room = openRoom(listOf(ana, bo), teamSize = 1)
            room.pickAndReady(lineup(2, Random(25)))
            val gameId = room.started()
            val players = listOf(Player(ana, gameId), Player(bo, gameId))
            players.forEach { it.start() }
            eventually("a game under way", GAME_LIMIT_MILLIS) { world.log.answers(gameId).size > UNDER_WAY }
            players.forEach { player ->
                player.person.connector.offline = true
                player.person.connector.killSockets()
            }
            delay(PAUSE_MILLIS + SLACK_MILLIS)
            val paused = world.log.answers(gameId).size
            delay(PAUSE_MILLIS)
            assertWithMessage("no answers while everyone is gone and the clocks are paused")
                .that(world.log.answers(gameId).size).isEqualTo(paused)
            eventually("the game given up on", ABANDON_MILLIS * 2) { world.log.status(gameId) == "voided" }

            ana.connector.offline = false
            ana.onPhone { players.first().driver.resumeNow() }
            val ui = players.first().driver.state.until("the words") { it.play?.online?.closed != null }.play!!
            assertThat(ui.online?.closed).isEqualTo(OnlineWords.voided(VoidReason.ABANDONED))
            assertThat(ui.over).isNull()
            assertThat(ui.online?.rated).isFalse()
            assertThat(world.store.matches()).isEmpty()
            assertThat(world.rooms.runningCount).isEqualTo(0)
        }
    }

    private companion object {
        const val AWAY_MILLIS = 50L
        const val UNDER_WAY = 10
        const val CLOCK_MILLIS = 1_500L
        const val PAUSE_MILLIS = 1_500L
        const val ABANDON_MILLIS = 6_000L
        const val SLACK_MILLIS = 1_500L
    }
}
