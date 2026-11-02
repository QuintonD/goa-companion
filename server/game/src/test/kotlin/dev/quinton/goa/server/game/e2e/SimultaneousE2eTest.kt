package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.simultaneous
import dev.quinton.goa.server.game.room.TestRules
import dev.quinton.goa.server.game.store.GameRow
import dev.quinton.goa.server.game.store.GameSeatRow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * People answer at the same time, on real sockets, in reverse seat order or all at once. Whatever the order:
 * everyone's table makes sense at each step, nobody is told it's their turn after locking, no ticket hangs.
 * The clock is what a hung ticket would come to, so one answer from the computer fails the game.
 */
class SimultaneousE2eTest {
    /** The screen as it must be, whatever else has happened. */
    private fun screenRules(name: String, answered: Set<Long>, broken: MutableList<String>): (PlayUi) -> Unit = { ui ->
        val d = ui.table.decision
        if (d != null && d.kind.simultaneous && d.id in answered) {
            if (ui.table.asking) broken += "$name is asked ${d.kind} again on ticket ${d.id}"
            if (ui.status == TurnStatus.YourTurn) broken += "$name is told it's their turn after locking ${d.id}"
        }
    }

    /** What every frame must say, in the order it came: read off the wire, nothing conflated away. */
    private fun wireRules(person: Person, gameId: String, hero: String): List<String> {
        val broken = ArrayList<String>()
        val acked = HashSet<Long>()
        var round = 0
        var turn = 0
        var seq = 0L
        for (frame in person.connector.wire) {
            when (val message = frame.message) {
                is ServerMessage.Ack -> if (message.gameId == gameId) {
                    if (message.accepted) acked += message.ticket else broken += "refused: $message"
                }

                is ServerMessage.Table -> if (message.gameId == gameId) {
                    val view = message.view
                    if (view.round < round || (view.round == round && view.turn < turn)) {
                        broken += "the table went back to ${view.round}.${view.turn} from $round.$turn"
                    }
                    round = view.round
                    turn = view.turn
                    if (message.seq <= seq) broken += "seq ${message.seq} after $seq"
                    seq = message.seq
                    broken += tableRules(person.name, message, hero, acked)
                }

                else -> Unit
            }
        }
        return broken
    }

    private fun tableRules(name: String, table: ServerMessage.Table, hero: String, acked: Set<Long>): List<String> {
        val broken = ArrayList<String>()
        val decision = table.decision
        val me = table.view.heroes.first { it.heroId == hero }
        if (decision == null && table.view.winner == null) broken += "no question and no winner: $table"
        if (decision?.seat == hero && decision.id in acked && hero !in table.locked) {
            broken += "$name was sent the ticket ${decision.id} again, after its ack"
        }
        if (decision?.seat == hero && decision.kind == DecisionKind.PLAY_CARD) {
            val cards = decision.options.filterIsInstance<Choice.Card>().map { it.cardId }
            if (cards.any { it !in me.hand.orEmpty() }) {
                broken += "$name is asked to play a card not in their hand: $cards ${me.hand}"
            }
        }
        if (hero in table.locked && decision?.seat == hero && decision.options.isNotEmpty()) {
            broken += "$name is shown as done and asked at once: $decision"
        }
        return broken
    }

    /** Returns how many level-up questions the game had. */
    private suspend fun playOneGame(world: World, people: List<Person>, inOrder: Boolean, seed: Int): Int {
        val room = openRoom(people, teamSize = 2)
        room.pickAndReady(lineup(people.size, Random(seed)))
        val gameId = room.started()
        val order = room.order
        val broken = CopyOnWriteArrayList<String>()
        val latest = ConcurrentHashMap<String, PlayUi>()
        val players = people.map { person ->
            val hero = room.heroOf(person)
            val mine = ConcurrentHashMap.newKeySet<Long>()
            val rules = screenRules(person.name, mine, broken)
            Player(
                person,
                gameId,
                brain = { ui, question, random ->
                    mine += question.id
                    LeansIn.choose(ui, question, random)
                },
                hold = { ui -> holds(ui, hero, order, latest, inOrder) },
                look = { ui ->
                    latest[hero] = ui
                    rules(ui)
                },
            )
        }
        playToEnd(players)
        val truth = world.log.truth(gameId)
        assertThat(truth.status).isEqualTo("finished")
        assertWithMessage("a hung ticket comes to this").that(truth.answers.filter { it.origin == "clock" }).isEmpty()
        assertWithMessage("what the screens showed").that(broken).isEmpty()
        people.forEach { person ->
            assertWithMessage("what ${person.name}'s wire carried")
                .that(wireRules(person, gameId, room.heroOf(person))).isEmpty()
            assertThat(rebuiltLog(person.tables(gameId))).isEqualTo(truth.events)
        }
        return truth.positions.zip(truth.answers).count { (position, answer) ->
            position.pending?.kind == DecisionKind.UPGRADE_CARD && answer.seat == position.pending.seat
        }
    }

    /**
     * Holds a card until the whole table has this turn on the screen. In order, then, the last seat answers
     * first and each waits for the seats after it; otherwise everybody goes at once and races.
     */
    private fun holds(
        ui: PlayUi,
        hero: String,
        order: List<String>,
        latest: Map<String, PlayUi>,
        inOrder: Boolean,
    ): Boolean {
        if (ui.table.decision?.kind?.simultaneous != true) return false
        val view = ui.table.view
        val here = order.mapNotNull { latest[it] }
        val allThere = here.size == order.size && here.all { other ->
            other.table.view.round == view.round && other.table.view.turn == view.turn
        }
        return !allThere || (
            inOrder && order.drop(order.indexOf(hero) + 1).any { later ->
                latest[later]?.let { it.table.asking && it.table.decision?.kind?.simultaneous == true } != false
            }
            )
    }

    @Test
    fun peopleAnsweringInAnyOrderAllSeeASensibleTable(): Unit = runBlocking {
        World().use { world ->
            val people = world.signedUp("Ana", "Bo", "Cy", "Di")
            var levelQuestions = 0
            repeat(GAMES) { game ->
                levelQuestions += playOneGame(world, people, inOrder = game % 2 == 0, seed = 100 + game)
            }
            println("simul: $GAMES games, $levelQuestions level-up questions")
            assertWithMessage("whole level-ups went through the window").that(levelQuestions).isGreaterThan(0)
        }
    }

    /**
     * A hand runs out: nobody has anything to decide for that seat, so nobody waits for it. A built position,
     * because people playing whole games don't often get there: it goes in the database and comes back from a
     * restart like any running game.
     */
    @Test
    fun aSeatWithNoCardsIsPassedForAndNobodyWaits(): Unit = runBlocking {
        World().use { world ->
            val people = world.signedUp("Ana", "Bo", "Cy", "Di")
            val session = position(TestRules.engine, FORGOTTEN_ISLAND) {
                clearMinions()
                hero("wasp", Team.RED, Hex(1, -2)) { hand() }
                hero("xargatha", Team.RED, Hex(2, -2)) { hand("xargatha-gold", "xargatha-silver") }
                hero("arien", Team.BLUE, Hex(-5, -4)) { hand("arien-gold", "arien-silver") }
                hero("brogan", Team.BLUE, Hex(-4, -4)) { hand("brogan-gold", "brogan-silver") }
                phase = PositionPhase.PlayCards(0)
            }
            val owners = listOf("wasp", "xargatha", "arien", "brogan").zip(people).toMap()
            val gameId = "built-game"
            val row = GameRow(
                gameId,
                "BUILTROO",
                session.setup,
                session.save(),
                "e2e",
                TestRules.engine.engineVersion,
                ranked = false,
                session.setup.mapId,
                session.setup.length,
                createdAt = System.currentTimeMillis(),
            )
            world.store.createGame(
                row,
                session.setup.seats.mapIndexed { i, seat ->
                    GameSeatRow(gameId, seat.heroId, owners.getValue(seat.heroId).accountId, seat.team, false, i)
                },
            )
            world.restart()

            val order = session.setup.seats.map { it.heroId }
            val latest = ConcurrentHashMap<String, PlayUi>()
            val players = order.map { hero ->
                Player(
                    owners.getValue(hero),
                    gameId,
                    hold = { ui ->
                        val later = order.drop(order.indexOf(hero) + 1)
                        ui.table.view.turn == 1 && ui.table.decision?.kind == DecisionKind.PLAY_CARD &&
                            later.any { other ->
                                latest[other]?.let { it.table.asking || it.table.decision == null } != false
                            }
                    },
                    look = { ui -> latest[hero] = ui },
                )
            }
            val wasp = players.first()
            players.forEach { it.start() }
            eventually("the other seats' tables") { latest.size == order.size }
            // The first seat in the game has no cards, so the whole game would hang on it if the server didn't answer.
            eventually("the pass in the log") {
                world.log.answers(gameId).any { it.seat == "wasp" && it.origin == "auto" && it.choice == Choice.Pass }
            }
            eventually("the next turn", GAME_LIMIT_MILLIS) { players.all { it.seen?.table?.view?.turn == 2 } }
            players.forEach { player ->
                val tables = player.person.tables(gameId)
                assertWithMessage("${player.person.name} sees who is done").that(
                    tables.filter { it.view.turn == 1 && it.decision?.kind == DecisionKind.PLAY_CARD }
                        .all { "wasp" in it.locked },
                ).isTrue()
                assertThat(tables.all { it.decision == null || it.decision?.options != listOf(Choice.Pass) }).isTrue()
            }
            assertWithMessage("wasp's phone never asked").that(
                wasp.person.tables(gameId).filter { it.view.turn == 1 }.none { it.decision?.seat == "wasp" },
            ).isTrue()
            assertThat(world.log.answers(gameId).filter { it.origin == "clock" }).isEmpty()
            assertThat(players.flatMap { rebuiltLog(it.person.tables(gameId)) }.map { it.text })
                .contains("Wasp has no cards and passes.")
        }
    }

    private companion object {
        const val GAMES = 2
    }
}
