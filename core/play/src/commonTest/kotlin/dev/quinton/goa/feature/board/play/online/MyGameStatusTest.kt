package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.MyGame
import dev.quinton.goa.core.online.SeatPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

class MyGameStatusTest {
    private val seats = listOf(
        SeatPlayer("arien", "ann", "Ann", Team.RED),
        SeatPlayer("brogan", "cid", "Cid", Team.RED),
        SeatPlayer("wasp", "bob", "Bob", Team.BLUE),
        SeatPlayer("xargatha", "dee", "", Team.BLUE),
    )

    private fun game(id: String, asked: List<String> = emptyList(), over: Boolean = false) =
        MyGame(id, "ABCD1234", "forgotten-island", seats, asked, over)

    @Test
    fun aQuestionForOneOfYourSeatsIsYourTurnWhoeverElseIsAsked() {
        assertEquals(
            MyGameStatus(MyGameState.YOUR_TURN, "Your turn"),
            MyGameStatuses.of(game("g", asked = listOf("wasp", "arien")), "ann"),
        )
    }

    @Test
    fun otherwiseItNamesWhoIsAskedADeletedAccountAsAFormerPlayer() {
        assertEquals("Waiting for Bob", MyGameStatuses.of(game("g", asked = listOf("wasp")), "ann").words)
        assertEquals(
            "Waiting for Cid and Former player",
            MyGameStatuses.of(game("g", asked = listOf("brogan", "xargatha")), "ann").words,
        )
    }

    @Test
    fun nothingAskedIsInPlayAndAnEndedGameIsFinished() {
        // An older server sends neither field: "In play", as before.
        assertEquals(MyGameState.IN_PLAY, MyGameStatuses.of(game("g"), "ann").state)
        assertEquals(MyGameState.OVER, MyGameStatuses.of(game("g", asked = listOf("arien"), over = true), "ann").state)
    }

    @Test
    fun theGamesThatNeedYouComeFirstTheServersOrderKeptWithinEach() {
        val list = listOf(
            game("over", over = true),
            game("waiting-1", asked = listOf("wasp")),
            game("mine", asked = listOf("arien")),
            game("playing"),
            game("waiting-2", asked = listOf("brogan")),
        )
        assertEquals(
            listOf("mine", "waiting-1", "waiting-2", "playing", "over"),
            MyGameStatuses.sorted(list, "ann").map { it.gameId },
        )
    }
}
