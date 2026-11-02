package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.feature.board.play.online.OnlineResult
import dev.quinton.goa.feature.board.play.online.OnlineState
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Leaving can't dodge a result, not even with a server restart in the middle. */
class LeaveE2eTest {
    @Test
    fun whoLeavesIsGoneForGoodAndTheGameStillCounts(): Unit = runBlocking {
        World().use { world ->
            val people = world.signedUp("Ana", "Bo", "Cy", "Di")
            val room = openRoom(people, teamSize = 2)
            room.pickAndReady(lineup(4, Random(7)))
            val gameId = room.started()
            val players = people.map { Player(it, gameId) }
            val leaver = players[1]
            val stayers = players - leaver
            val hero = room.hostLobby.value.seats.first { it.accountId == leaver.person.accountId }.heroId!!
            players.forEach { it.start() }

            eventually("a game under way", GAME_LIMIT_MILLIS) { world.log.answers(gameId).size > GAME_UNDER_WAY }
            leaver.hold = { true }
            leaver.person.onPhone { leaver.driver.leave() }

            eventually("the others told") {
                stayers.all { it.seen?.online?.presence?.get(hero) == Presence.COMPUTER }
            }
            suspend fun cantComeBack(what: String) {
                val games = (leaver.person.gateway.myGames() as OnlineResult.Ok).value.games
                assertWithMessage("$what: their games").that(games.map { it.gameId }).doesNotContain(gameId)
                val back = leaver.person.driver(gameId, leaver.person.screen())
                leaver.person.onPhone { back.start() }
                val state = back.state.until("$what: being told it's gone") { it !is OnlineState.Connecting }
                assertWithMessage("$what: a new socket").that(state)
                    .isEqualTo(OnlineState.Refused(Refusal.NO_SUCH_ROOM))
            }
            cantComeBack("after leaving")

            world.restart()
            eventually("the others back after the restart", RECONNECT_MILLIS) {
                stayers.all { it.screens is OnlineState.InGame }
            }
            assertWithMessage("a seat given up stays given up")
                .that(stayers.first().seen?.online?.presence?.get(hero)).isEqualTo(Presence.COMPUTER)
            cantComeBack("after the restart")

            playToEnd(stayers)
            val truth = world.log.truth(gameId)
            assertThat(truth.status).isEqualTo("finished")
            val winner = truth.positions.last().spectator.winner
            assertThat(winner).isNotNull()
            val match = world.store.matches().single { it.id == gameId }
            assertThat(match.winner).isEqualTo(winner)
            assertWithMessage("the team still wins or loses with them on it")
                .that(match.participants.map { it.playerId }).contains(leaver.person.accountId)
            assertThat(stayers.map { it.seen?.online?.rated }.toSet()).containsExactly(true)
            assertThat(truth.answers.last { it.seat == hero }.origin).isEqualTo("clock")
            assertThat(world.log.answers(gameId).filter { it.origin == "clock" }.map { it.seat }.toSet())
                .containsExactly(hero)
            assertThat(world.rooms.runningCount).isEqualTo(0)
        }
    }

    private companion object {
        const val GAME_UNDER_WAY = 30
        const val RECONNECT_MILLIS = 40_000L
    }
}
