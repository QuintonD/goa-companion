package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.server.game.store.SqliteGameStore
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The server is killed, not stopped, in the middle of a window with one card locked in. The real `main` comes
 * back on the same database; the phones find it by themselves through their backoff; nobody plays anything
 * again that they had played; and the game goes on to a rated result.
 */
class RestartE2eTest {
    @Test
    fun aKilledServerComesBackWithTheGameAndTheLock(): Unit = runBlocking {
        ServerProcess().use { host ->
            host.startProcess()
            val people = host.signedUp("Ana", "Bo", "Cy", "Di")
            val room = openRoom(people, teamSize = 2)
            room.pickAndReady(lineup(4, Random(5)))
            val gameId = room.started()
            val players = people.map { Player(it, gameId) }
            val heroOf = players.associateWith { room.heroOf(it.person) }

            // Everyone stops at a turn's card, a few answers in, so there is a past to replay.
            var open = true
            players.forEach { player ->
                player.hold = { ui ->
                    open && ui.table.decision?.kind == DecisionKind.PLAY_CARD && ui.table.view.round >= STOP_AT_ROUND
                }
            }
            players.forEach { it.start() }
            eventually("the window", GAME_LIMIT_MILLIS) {
                players.all {
                    val table = it.seen?.table
                    table != null && table.view.round >= STOP_AT_ROUND && table.asking &&
                        table.decision?.kind == DecisionKind.PLAY_CARD
                }
            }

            // The last seat in order locks in: the engine won't reach it until the others are in.
            val locker = players.first { heroOf.getValue(it) == room.order.last() }
            val card = locker.person.onPhone {
                val ui = checkNotNull(locker.screens.play)
                val choice = ui.table.decision!!.options.filterIsInstance<Choice.Card>().first()
                locker.driver.onAction(GameAction.Answer(choice))
                choice
            }
            eventually("the lock on disk") { heroOf.getValue(locker) in host.log.locks(gameId) }
            val answersBefore = host.log.answers(gameId).size
            val eventsBefore = players.associateWith { rebuiltLog(it.person.tables(gameId)) }

            host.kill()
            eventually("the phones noticing") { players.all { it.screens is OnlineState.Reconnecting } }
            assertWithMessage("a phone can't answer into the void")
                .that(players.none { it.screens.play?.table?.asking == true }).isTrue()

            val restart = host.startProcess()
            println("restart: server back in $restart ms, ${host.health()}")
            assertThat(host.health().rooms).isEqualTo(1)

            // No one taps anything: the driver's own backoff finds the server.
            eventually("the phones reconnecting by themselves", RECONNECT_MILLIS) {
                players.all { it.screens is OnlineState.InGame }
            }
            assertThat(host.log.answers(gameId).size).isEqualTo(answersBefore)
            assertThat(host.log.locks(gameId)).contains(heroOf.getValue(locker))
            players.forEach { player ->
                val log = rebuiltLog(player.person.tables(gameId))
                val before = eventsBefore.getValue(player)
                assertWithMessage("${player.person.name}'s log across the restart")
                    .that(log.take(before.size)).isEqualTo(before)
            }

            // The lock held: they are not asked again, and the rest of the table can't tell what it was.
            eventually("the locked seat shown as waiting") {
                val ui = locker.screens.play
                ui != null && !ui.table.asking && ui.status is TurnStatus.Waiting
            }
            assertThat(locker.screens.play?.table?.heroView(heroOf.getValue(locker))?.played?.cardId)
                .isEqualTo(card.cardId)

            open = false
            playToEnd(players)

            val truth = host.log.truth(gameId)
            assertThat(truth.status).isEqualTo("finished")
            val lockedAnswer = truth.answers.drop(answersBefore).first { it.seat == heroOf.getValue(locker) }
            assertThat(lockedAnswer.choice).isEqualTo(card)
            players.forEach { player ->
                assertThat(rebuiltLog(player.person.tables(gameId))).isEqualTo(truth.events)
                assertThat(player.seen?.online?.rated).isTrue()
                val winner = truth.positions.last().spectator.winner
                assertThat(player.seen?.over?.youWon).isEqualTo(winner == room.teamOf(player.person))
            }
            host.kill()
            SqliteGameStore.open(host.dir).use { store ->
                val match = store.matches().single { it.id == gameId }
                assertThat(match.winner).isEqualTo(truth.positions.last().spectator.winner)
            }
        }
    }

    private companion object {
        /** The backoff doubles up to 30 s while the server was down for several seconds. */
        const val RECONNECT_MILLIS = 40_000L
        const val STOP_AT_ROUND = 2
    }
}
