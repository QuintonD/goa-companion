package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.OnlineWords
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
            // The whole of what the server logged: nothing that would let a reader of the logs in.
            val secrets = people.flatMap { listOfNotNull(it.identities.identity?.token, it.lastRecoveryCode) }
            val logged = host.output.toList()
            assertThat(secrets.filter { secret -> logged.any { secret in it } }).isEmpty()
            assertThat(logged.filter { room.code in it }).isEmpty()
            assertThat(logged.filter { " ERROR " in it }).isEmpty()
            host.kill()
            SqliteGameStore.open(host.dir).use { store ->
                val match = store.matches().single { it.id == gameId }
                assertThat(match.winner).isEqualTo(truth.positions.last().spectator.winner)
            }
        }
    }

    /** A rules fix that changes nothing of a game's past goes on; one that changes it is void, and said in words. */
    @Test
    fun aNewRulesBuildCarriesOnUnlessTheGamesPastChanged(): Unit = runBlocking {
        var build = "build-1"
        World(configure = { it.copy(rulesBuild = build) }).use { world ->
            val people = world.signedUp("Ana", "Bo")
            val room = openRoom(people, teamSize = 1)
            room.pickAndReady(lineup(2, Random(31)))
            val gameId = room.started()
            val players = people.map { Player(it, gameId) }
            players.forEach { player ->
                player.hold = { ui ->
                    ui.table.decision?.kind == DecisionKind.PLAY_CARD && ui.table.view.round >= STOP_AT_ROUND
                }
            }
            players.forEach { it.start() }
            eventually("a past to check", GAME_LIMIT_MILLIS) {
                players.all { it.seen?.table?.view?.round == STOP_AT_ROUND }
            }
            eventually("a question to wait on") { players.any { it.seen?.table?.asking == true } }

            build = "build-2"
            world.restart()
            assertWithMessage("the same past on a new build").that(world.rooms.runningCount).isEqualTo(1)
            eventually("the phones back", RECONNECT_MILLIS) { players.all { it.screens is OnlineState.InGame } }

            world.stopServer()
            world.log.tamperWith(gameId)
            build = "build-3"
            world.startServer()
            assertWithMessage("a changed past").that(world.rooms.runningCount).isEqualTo(0)
            assertThat(world.log.status(gameId)).isEqualTo("voided")
            players.forEach { player ->
                val shown = player.driver.state.until("the words", RECONNECT_MILLIS) {
                    it.play?.online?.closed != null
                }
                val ui = checkNotNull(shown.play)
                assertThat(ui.online?.closed).isEqualTo(OnlineWords.voided(VoidReason.RULES_CHANGED))
                assertThat(ui.online?.rated).isFalse()
                assertThat(ui.over).isNull()
            }
            assertThat(world.store.matches()).isEmpty()
        }
    }

    private companion object {
        /** The backoff doubles up to 30 s while the server was down for several seconds. */
        const val RECONNECT_MILLIS = 40_000L
        const val STOP_AT_ROUND = 2
    }
}
