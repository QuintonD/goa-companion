package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.review.ReviewAction
import dev.quinton.goa.server.game.ratings.FORMER_PLAYER
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The whole journey on the real wire: accounts, a ranked room, seats and heroes, a game played to its end
 * through the screens' state only, the result in the store and on the board; and all of what crossed the wire,
 * frame by frame, against the server's own session.
 */
class JourneyE2eTest {
    @Test
    fun fourPeoplePlayAWholeRankedGameAndItCounts(): Unit = runBlocking {
        World().use { world ->
            val people = world.signedUp("Ana", "Bo", "Cy", "Di")
            val (ana, bo) = people
            val room = openRoom(people.take(3), teamSize = 2)
            // Cy picks a seat of their own: the join put them on Red, and they want to be with Bo.
            room.sitting[2].seat(Team.BLUE, 1)
            room.comeIn(people[3])
            val heroes = lineup(4, Random(1))
            room.pickAndReady(heroes)
            val gameId = room.started()

            val players = people.map { Player(it, gameId) }
            val reviewing = players.first()
            reviewing.person.onPhone { reviewing.start() }
            eventually("a table") { reviewing.seen != null }
            reviewing.person.onPhone { reviewing.driver.onAction(GameAction.Review(ReviewAction.Open(true))) }
            playToEnd(players)

            val truth = world.log.truth(gameId)
            println("journey: ${truth.answers.size} answers, ${truth.events.size} events")
            assertThat(truth.status).isEqualTo("finished")
            val winner = checkNotNull(truth.positions.last().spectator.winner) { "No winner in the log" }
            players.forEach { player ->
                val over = checkNotNull(player.seen?.over) { "${player.person.name} sees no result" }
                val team = room.teamOf(player.person)
                assertThat(over.winner).isEqualTo(winner)
                assertWithMessage("${player.person.name} on $team").that(over.youWon).isEqualTo(team == winner)
                assertThat(player.seen?.online?.rated).isTrue()
                val end = checkNotNull(player.person.end(gameId))
                assertThat(end.rated).isTrue()
                assertThat(end.winner).isEqualTo(winner)
            }
            val match = world.store.matches().single { it.id == gameId }
            assertThat(match.winner).isEqualTo(winner)
            assertThat(match.participants.map { it.playerId }).containsExactlyElementsIn(people.map { it.accountId })
            assertThat(truth.answers.map { it.origin }).doesNotContain("clock")

            val scan = WireScan(truth)
            people.forEach { person ->
                val tables = person.tables(gameId)
                assertWithMessage("the frames of ${person.name}").that(tables.size).isGreaterThan(truth.answers.size / 2)
                assertThat(scan.scan(person.connector.wire, room.heroOf(person))).isEmpty()
                assertWithMessage("${person.name}'s log").that(rebuiltLog(tables)).isEqualTo(truth.events)
            }
            assertThat(players.map { it.seen?.log?.map { line -> line.text } }.toSet())
                .containsExactly(truth.events.map { it.text })
            scanCanFindALeak(scan, people[0], people[1], room, gameId)
            val secrets = people.flatMap { listOfNotNull(it.identities.identity?.token, it.lastRecoveryCode) }
            people.forEach { person ->
                assertThat(secrets.filter { secret -> person.connector.wire.any { secret in it.raw } }).isEmpty()
            }

            // The board shows nobody before their fifth rated game.
            assertThat(world.board().players).isEmpty()
            val duel = heroes.take(2)
            repeat(GAMES_TO_APPEAR - 2) { aDuel(ana, bo, duel) }
            assertThat(world.board().players.map { it.id }).doesNotContain(ana.accountId)
            aDuel(ana, bo, duel)
            assertThat(world.board().matches).isEqualTo(GAMES_TO_APPEAR)
            val shown = world.board()
            assertThat(shown.players.map { it.name }).containsExactly("Ana", "Bo")
            assertThat(shown.heroes.map { it.id }).containsExactlyElementsIn(duel)
            val log = world.store.matches()
            shown.players.forEach { row ->
                assertThat(row.games).isEqualTo(GAMES_TO_APPEAR)
                val won = log.count { m -> m.participants.any { it.playerId == row.id && it.team == m.winner } }
                assertThat(row.wins).isEqualTo(won)
            }
            val again = world.getText(OnlineRoutes.LEADERBOARD)
            val tag = again.headers().firstValue("ETag").orElseThrow()
            val unchanged = world.getText(OnlineRoutes.LEADERBOARD, mapOf("If-None-Match" to tag))
            assertThat(unchanged.statusCode()).isEqualTo(NOT_MODIFIED)

            // A deleted account keeps its games, and its name is gone from the public board.
            bo.onPhone {
                bo.home.deleteAccount()
                bo.home.state.until("the account gone") { it.name.isEmpty() && bo.identities.identity == null }
            }
            val changed = world.getText(OnlineRoutes.LEADERBOARD, mapOf("If-None-Match" to tag))
            assertThat(changed.statusCode()).isEqualTo(OK)
            assertThat(changed.headers().firstValue("ETag").orElseThrow()).isNotEqualTo(tag)
            assertThat(changed.body()).doesNotContain("\"Bo\"")
            assertThat(world.board().players.map { it.name }).containsExactly("Ana", FORMER_PLAYER)
            assertThat(world.store.matches().size).isEqualTo(GAMES_TO_APPEAR)
        }
    }

    /** A scan that can't fail is worth nothing: the same frames with a hand in them, a question and a key. */
    private fun scanCanFindALeak(scan: WireScan, viewer: Person, other: Person, room: Room, gameId: String) {
        val frames = viewer.connector.wire
        val hero = room.heroOf(viewer)
        val otherHero = room.heroOf(other)
        val index = frames.indexOfFirst {
            val table = it.message as? ServerMessage.Table
            table?.gameId == gameId && table.view.round == 1 && table.decision?.seat == hero
        }
        val frame = frames[index]
        val table = frame.message as ServerMessage.Table
        fun scanOf(doctored: ServerMessage.Table) = scan.scan(
            listOf(WireFrame(0, OnlineProtocol.json.encodeToString(ServerMessage.serializer(), doctored), doctored)),
            hero,
        )
        val hand = truthHand(other, gameId)
        val shownHand = table.copy(
            view = table.view.copy(
                heroes = table.view.heroes.map { if (it.heroId == otherHero) it.copy(hand = hand) else it },
            ),
        )
        assertThat(scanOf(shownHand).any { "shows the hand of $otherHero" in it }).isTrue()
        val theirQuestion = checkNotNull(table.decision).copy(seat = otherHero)
        assertThat(scanOf(table.copy(decision = theirQuestion)).any { "$otherHero's question" in it }).isTrue()
        assertThat(scan.scan(listOf(frame.withRawKey("payload")), hero).any { "payload" in it }).isTrue()
    }

    private fun truthHand(person: Person, gameId: String): List<String> =
        person.tables(gameId).first().view.heroes.firstNotNullOf { it.hand }

    /** One more rated game between two of them. */
    private suspend fun aDuel(red: Person, blue: Person, heroes: List<String>) {
        val room = openRoom(listOf(red, blue), teamSize = 1)
        room.pickAndReady(heroes)
        val gameId = room.started()
        val players = listOf(red, blue).map { Player(it, gameId) }
        playToEnd(players)
        assertThat(players.map { it.seen?.online?.rated }.toSet()).containsExactly(true)
    }

    private companion object {
        const val GAMES_TO_APPEAR = 5
        const val OK = 200
        const val NOT_MODIFIED = 304
    }
}
