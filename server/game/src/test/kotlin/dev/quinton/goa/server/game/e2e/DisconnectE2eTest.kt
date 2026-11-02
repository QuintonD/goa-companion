package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.server.game.room.RoomRules
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * A phone goes into a tunnel in the middle of its question: the others see it, the computer answers when the
 * short clock runs out, and the person comes back with the whole game, event by event, and plays on.
 */
class DisconnectE2eTest {
    @Test
    fun aDroppedPersonIsAnsweredForAndComesBackToTheWholeGame(): Unit = runBlocking {
        World(rules = RoomRules(disconnectedMillis = AWAY_MILLIS), beatMillis = FAST_BEAT_MILLIS).use { world ->
            val people = world.signedUp("Ana", "Bo", "Cy", "Di")
            val room = openRoom(people, teamSize = 2)
            val heroes = lineup(4, Random(3))
            room.pickAndReady(heroes)
            val gameId = room.started()
            val players = people.map { Player(it, gameId) }
            val tunnel = players[2]
            val hero = room.heroOf(tunnel.person)
            val others = players - tunnel

            // Caught in the middle of a question only they can answer: nothing else can move.
            tunnel.hold = { ui -> ui.table.decision?.kind == DecisionKind.CHOOSE_ACTION }
            players.forEach { it.start() }
            eventually("the question", GAME_LIMIT_MILLIS) {
                tunnel.seen?.table?.let { it.asking && it.decision?.kind == DecisionKind.CHOOSE_ACTION } == true
            }

            val cutAt = System.currentTimeMillis()
            tunnel.person.connector.offline = true
            tunnel.person.connector.killSockets()

            eventually("the others seeing them away") {
                others.all { it.seen?.online?.presence?.get(hero) == Presence.AWAY }
            }
            others.forEach { other ->
                val table = other.person.tables(gameId).last()
                assertThat(table.presence[hero]).isEqualTo(Presence.AWAY)
                val clock = checkNotNull(table.clock) { "No clock on the table" }
                assertThat(clock.seat).isEqualTo(hero)
                assertWithMessage("the clock falls to the disconnected time")
                    .that(clock.deadlineAt).isAtMost(cutAt + AWAY_MILLIS + SLACK_MILLIS)
            }
            eventually("the phone noticing") { tunnel.screens is OnlineState.Reconnecting }
            assertThat(tunnel.screens.play?.table?.asking).isFalse()

            // The clock runs out: the computer answers that question, and the game moves without them.
            eventually("the computer answering") { world.log.answers(gameId).any { it.origin == "clock" } }
            eventually("the others told the computer has them") {
                others.all { it.seen?.online?.presence?.get(hero) == Presence.COMPUTER }
            }
            eventually("a few more answers for the absent seat", GAME_LIMIT_MILLIS) {
                world.log.answers(gameId).count { it.origin == "clock" && it.seat == hero } >= ANSWERS_WHILE_AWAY
            }

            // Back: from the events it holds, to a log the same as everybody else's, event by event.
            val heldEvents = rebuiltLog(tunnel.person.tables(gameId)).size
            val socketsBefore = tunnel.person.connector.hellos.size
            tunnel.person.connector.offline = false
            tunnel.hold = { false }
            tunnel.person.onPhone { tunnel.driver.resumeNow() }
            eventually("coming back") {
                tunnel.screens is OnlineState.InGame && tunnel.person.connector.hellos.size > socketsBefore
            }
            val hello = tunnel.person.connector.hellos.last()
            assertThat(hello.resume.single().gameId).isEqualTo(gameId)
            assertThat(hello.resume.single().haveEvents).isEqualTo(heldEvents)
            val resumed = tunnel.person.connector.wire
                .filter { it.connection == tunnel.person.connector.hellos.size - 1 }
                .map { it.message }.filterIsInstance<ServerMessage.Table>()
                .first { it.gameId == gameId }
            assertThat(resumed.eventsFrom).isEqualTo(heldEvents)
            assertWithMessage("it isn't sent the whole log again").that(resumed.eventsFrom).isGreaterThan(0)
            eventually("the others seeing them back") {
                others.all { it.seen?.online?.presence?.get(hero) == Presence.HERE }
            }
            val lastEvent = others.minOf { rebuiltLog(it.person.tables(gameId)).size }
            val theirs = rebuiltLog(tunnel.person.tables(gameId))
            val ours = rebuiltLog(others.first().person.tables(gameId))
            val common = minOf(theirs.size, ours.size, lastEvent)
            assertThat(theirs.take(common)).isEqualTo(ours.take(common))

            playToEnd(players)
            val truth = world.log.truth(gameId)
            players.forEach { player ->
                assertWithMessage("${player.person.name}'s log")
                    .that(rebuiltLog(player.person.tables(gameId))).isEqualTo(truth.events)
                assertThat(player.seen?.log?.map { it.text }).isEqualTo(truth.events.map { it.text })
            }
            assertThat(truth.answers.filter { it.origin == "clock" }.map { it.seat }.toSet()).containsExactly(hero)
            assertThat(world.store.matches().map { it.id }).contains(gameId)
        }
    }

    private companion object {
        const val AWAY_MILLIS = 1_500L
        const val SLACK_MILLIS = 1_000L
        const val ANSWERS_WHILE_AWAY = 3
    }
}
