package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.Resume
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.game.gamePeek
import dev.quinton.goa.feature.board.play.game.humans
import dev.quinton.goa.feature.board.play.game.mergedView
import dev.quinton.goa.feature.board.play.review.ReviewAction
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.turnLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The online driver on a real game of the shipped catalog: frames are what the engine's views give. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlineGameDriverTest {
    private fun stub(seat: String, kind: DecisionKind = DecisionKind.DEFEND, team: Team = Team.BLUE) =
        Decision(0, seat, team, kind, "", options = emptyList())

    // ---- Events --------------------------------------------------------------------------------------

    @Test
    fun eventsGrowFromEventsFrom() = runTest {
        val rig = rig()
        val server = rig.server
        val held = server.session.events.size
        assertEquals(held, rig.play.log.size)
        server.answerFirst()
        server.answerFirst()
        rig.feed(server.table("wasp", have = held))
        assertEquals(server.session.events.map { it.text }, rig.play.log.map { it.text })
    }

    @Test
    fun aPageOfACatchUpAddsToTheLogAndTheLastOneShowsIt() = runTest {
        val rig = rig()
        val server = rig.server
        val held = server.session.events.size
        while (server.session.events.size < held + 2) server.answerFirst()
        val whole = server.table("wasp", have = held)

        rig.feed(whole.copy(events = whole.events.take(1), more = true))
        // Nothing shown yet: the screen still has the last whole table, not a part of this one.
        assertEquals(held, rig.play.log.size)

        rig.feed(whole.copy(eventsFrom = held + 1, events = whole.events.drop(1)))
        assertEquals(server.session.events.map { it.text }, rig.play.log.map { it.text })
    }

    @Test
    fun aFrameThatOverlapsWhatIsHeldAddsNothingTwice() = runTest {
        val rig = rig()
        val server = rig.server
        server.answerFirst()
        rig.feed(server.table("wasp", have = 0))
        assertEquals(server.session.events.map { it.text }, rig.play.log.map { it.text })
    }

    @Test
    fun aGapReconnectsWithResumeInsteadOfGuessing() = runTest {
        val rig = rig()
        val server = rig.server
        val held = rig.play.log.size
        repeat(4) { server.answerFirst() }
        val more = server.session.events.size
        assertTrue(more > held, "the answers must have made events")
        rig.feed(server.table("wasp", have = held + 1))
        assertEquals(held, rig.play.log.size, "a frame past what is held isn't applied")
        advanceTimeBy(BACKOFF_FIRST)
        runCurrent()
        assertEquals(2, rig.connector.hellos.size)
        assertEquals(listOf(Resume(FakeServer.GAME, held)), rig.connector.hellos.last().resume)
        assertEquals(server.session.events.map { it.text }, rig.play.log.map { it.text })
    }

    // ---- Stubs ---------------------------------------------------------------------------------------

    @Test
    fun aStubNeverReadsAsYourTurn() = runTest {
        val rig = rig()
        val other = rig.play.table.view.heroes.first { it.heroId == "arien" }
        rig.feed(rig.server.table("wasp", have = rig.play.log.size, decision = stub(other.heroId)))
        assertEquals(TurnStatus.OtherPlayer("arien", "arien person"), rig.play.status)
        assertFalse(rig.play.table.asking)

        // Even one that names the person's own seat: it has no ticket and nothing to answer.
        rig.feed(rig.server.table("wasp", have = rig.play.log.size, decision = stub("wasp", team = Team.RED)))
        assertEquals(TurnStatus.OtherPlayer("wasp", "wasp person"), rig.play.status)
        assertFalse(rig.play.table.asking)
        rig.driver.onAction(GameAction.Answer(Choice.Yes))
        runCurrent()
        assertTrue(rig.answers.isEmpty())
    }

    @Test
    fun aStubDefendLeavesYourHandOnShow() = runTest {
        val rig = rig()
        rig.feed(rig.server.table("wasp", have = rig.play.log.size, decision = stub("arien")))
        assertFalse(rig.play.table.handDecision)
    }

    @Test
    fun theTableAgainstTheComputerStillHidesTheHandWhileAComputerSeatChoosesItsCard() {
        // The fix for the stub must not change the local table: a real question with options still counts.
        val session = OnlineFixture.engine.newGame(OnlineFixture.setup)
        val pending = checkNotNull(session.pending)
        val table = TableUi(
            map = OnlineFixture.maps.first { it.id == session.setup.mapId },
            view = session.view("arien"),
            heroes = OnlineFixture.catalog.boardHeroes(),
            cards = emptyMap(),
            mine = setOf("arien"),
            decision = pending,
            asking = false,
            turnLog = turnLog(session.events),
        )
        assertEquals(DecisionKind.PLAY_CARD, pending.kind)
        assertTrue(table.handDecision)
    }

    @Test
    fun theOtherPersonIsNamedAndTheComputerStandInIsSaid() = runTest {
        val rig = rig()
        rig.feed(
            rig.server.table(
                "wasp",
                have = rig.play.log.size,
                decision = stub("arien"),
                presence = mapOf("arien" to Presence.COMPUTER, "wasp" to Presence.HERE),
            ),
        )
        val online = assertNotNull(rig.play.online)
        assertEquals("arien person", online.players["arien"])
        assertEquals(Presence.COMPUTER, online.presence["arien"])
        assertTrue(online.ranked)
        val peek = gamePeek(rig.play)
        assertEquals("arien person", peek.label)
        assertEquals(OnlineWords.presence(Presence.COMPUTER), peek.text)
        val arien = assertNotNull(rig.play.table.heroView("arien"))
        assertEquals("arien person", rig.play.table.controllerLabel(arien))
        val wasp = assertNotNull(rig.play.table.heroView("wasp"))
        assertEquals("You", rig.play.table.controllerLabel(wasp))
    }

    // ---- Answering -----------------------------------------------------------------------------------

    @Test
    fun aSecondAnswerToTheSameTicketIsNotSent() = runTest {
        val rig = rig()
        val question = checkNotNull(rig.play.table.decision)
        assertEquals(TurnStatus.YourTurn, rig.play.status)
        assertEquals(rig.server.ticket(checkNotNull(rig.server.session.pending)), question.id)
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        runCurrent()
        assertEquals(1, rig.answers.size)
        assertEquals(question.id, rig.answers.single().ticket)
        assertFalse(rig.play.table.asking)
    }

    @Test
    fun anAnswerThatIsNoOptionIsNotSent() = runTest {
        val rig = rig()
        rig.driver.onAction(GameAction.Answer(Choice.Card("not in this hand")))
        runCurrent()
        assertTrue(rig.answers.isEmpty())
    }

    @Test
    fun aTapSelectsTheOptionItStandsFor() = runTest {
        val rig = rig()
        assertFalse(rig.driver.tap(TableTap.Piece("nothing here")))
        val question = checkNotNull(rig.play.table.decision)
        rig.driver.onAction(GameAction.Select(question.firstCard()))
        runCurrent()
        assertEquals(question.firstCard(), rig.play.table.selected)
    }

    @Test
    fun lockedShowsWaitingUntilANewTicketArrives() = runTest {
        val rig = rig()
        val server = rig.server
        val question = checkNotNull(rig.play.table.decision)
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        runCurrent()
        assertIs<TurnStatus.Waiting>(rig.play.status, "a simultaneous answer is in as soon as it is sent")
        assertFalse(rig.play.busy, "there is nothing to hold back: the person waits for the others")

        rig.feed(ServerMessage.Ack(FakeServer.GAME, question.id, accepted = true))
        rig.feed(
            server.table(
                "wasp",
                have = rig.play.log.size,
                decision = stub("xargatha", DecisionKind.PLAY_CARD, Team.RED),
                locked = listOf("wasp"),
            ),
        )
        val waiting = assertIs<TurnStatus.Waiting>(rig.play.status)
        assertTrue("xargatha" in waiting.heroIds && "wasp" !in waiting.heroIds, "$waiting")
        assertFalse(rig.play.table.asking)
        assertFalse(rig.play.table.handDecision)

        // The forecast's next question for this seat (KEEP_UPGRADE follows UPGRADE_CARD): a new ticket.
        val next = question.copy(id = question.id + 1, kind = DecisionKind.KEEP_UPGRADE)
        rig.feed(server.table("wasp", have = rig.play.log.size, decision = next, locked = listOf("wasp")))
        assertEquals(TurnStatus.YourTurn, rig.play.status)
        assertTrue(rig.play.table.asking)
        rig.driver.onAction(GameAction.Answer(next.firstCard()))
        runCurrent()
        assertEquals(listOf(question.id, next.id), rig.answers.map { it.ticket })
        assertEquals("Your choice is in · waiting for the others", gamePeek(rig.play).text)
    }

    @Test
    fun aRealQuestionMayCarryTheSameTicketWhileItsAnswerIsLocked() = runTest {
        val rig = rig()
        val question = checkNotNull(rig.play.table.decision)
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        runCurrent()
        rig.feed(rig.server.table("wasp", have = rig.play.log.size, decision = question, locked = listOf("wasp")))
        assertIs<TurnStatus.Waiting>(rig.play.status)
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        runCurrent()
        assertEquals(1, rig.answers.size)
    }

    @Test
    fun aStaleTicketsRejectedAckBringsTheQuestionBack() = runTest {
        val rig = rig()
        val question = checkNotNull(rig.play.table.decision)
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        runCurrent()
        assertFalse(rig.play.table.asking)

        rig.feed(ServerMessage.Ack(FakeServer.GAME, question.id, accepted = false, reason = "stale"))
        assertEquals(TurnStatus.YourTurn, rig.play.status)
        assertTrue(rig.play.table.asking)
        assertFalse(rig.play.busy)
        assertEquals(OnlineWords.ANSWER_NOT_TAKEN, rig.play.message)
        val id = rig.play.messageId
        rig.driver.onAction(GameAction.MessageShown(id))
        runCurrent()
        assertNull(rig.play.message)

        // Told no twice for one ticket: this device has missed something, so it asks for the table again.
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        runCurrent()
        rig.feed(ServerMessage.Ack(FakeServer.GAME, question.id, accepted = false))
        advanceTimeBy(BACKOFF_FIRST)
        runCurrent()
        assertEquals(2, rig.connector.hellos.size)
        assertTrue(rig.play.table.asking)
    }

    @Test
    fun aRejectedAckForAQuestionAlreadyGoneShowsTheLatestOne() = runTest {
        val rig = rig()
        val server = rig.server
        val question = checkNotNull(rig.play.table.decision)
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        runCurrent()
        // The clock ran out and the computer answered: a new table, then the answer comes back refused.
        rig.feed(server.table("wasp", have = rig.play.log.size, decision = stub("arien")))
        rig.feed(ServerMessage.Ack(FakeServer.GAME, question.id, accepted = false))
        assertEquals(TurnStatus.OtherPlayer("arien", "arien person"), rig.play.status)
        assertFalse(rig.play.busy)
    }

    // ---- Off online ----------------------------------------------------------------------------------

    @Test
    fun coachTakeBackAndPaceDoNothing() = runTest {
        val rig = rig()
        val before = rig.play
        val sent = rig.connector.last.said.size
        listOf(
            GameAction.TakeBack,
            GameAction.SetCoach(true),
            GameAction.SetPace(AiPace.entries.last()),
            GameAction.ChooseAnyway,
            GameAction.Rethink,
            GameAction.AskSuggestions,
            GameAction.CloseSuggestions,
            GameAction.StartReport,
            GameAction.CancelReport,
            GameAction.Rematch,
        ).forEach(rig.driver::onAction)
        runCurrent()
        assertEquals(sent, rig.connector.last.said.size)
        assertEquals(before, rig.play)
        assertFalse(before.coach)
        assertFalse(before.canTakeBack)
        assertFalse(before.canSuggest)
    }

    // ---- Hidden information --------------------------------------------------------------------------

    /** Every string the screen is given about the game, without the catalog's whole card table. */
    private fun shown(play: PlayUi): String = listOf(
        play.table.view,
        play.table.decision,
        play.table.mine,
        play.recent,
        play.log,
        play.flow,
        play.reveal,
        play.online,
    ).joinToString()

    @Test
    fun aGameSeenThroughTwoSeatsNeverShowsTheOthersHand() = runTest {
        val server = FakeServer()
        assertEquals(4, server.session.setup.humans.size, "with every seat human, this is what must not be used")
        val wasp = rig("wasp", server)
        val arien = rig("arien", server)

        assertEquals(setOf("wasp"), wasp.play.table.mine)
        assertEquals(setOf("arien"), arien.play.table.mine)

        val waspHand = assertNotNull(wasp.play.table.heroView("wasp")?.hand)
        val arienHand = assertNotNull(arien.play.table.heroView("arien")?.hand)
        assertTrue(waspHand.isNotEmpty() && arienHand.isNotEmpty())
        assertNull(wasp.play.table.heroView("arien")?.hand)
        assertNull(arien.play.table.heroView("wasp")?.hand)

        val waspSees = shown(wasp.play)
        val arienSees = shown(arien.play)
        arienHand.forEach { assertFalse(it in waspSees, "wasp sees arien's $it") }
        waspHand.forEach { assertFalse(it in arienSees, "arien sees wasp's $it") }

        // The other side of the trap: what the local merge would have given the same phone.
        val everyHand = server.session.mergedView(server.session.setup.humans).toString()
        assertTrue(arienHand.first() in everyHand && waspHand.first() in everyHand)

        assertNotEquals(TurnStatus.YourTurn, arien.play.status)
        assertEquals(TurnStatus.YourTurn, wasp.play.status)
    }

    @Test
    fun onePersonOnTwoSeatsSeesBothOfTheirHandsAndNoOneElses() = runTest {
        val server = FakeServer()
        val rig = rig(seat = null, server = server)
        val connection = rig.connector.last
        rig.feed(server.start("wasp", "xargatha"))
        // Wasp is asked first; the frame for Xargatha, sent after it, holds only a stub for Wasp.
        connection.feed(server.table("wasp"))
        connection.feed(server.table("xargatha", have = server.session.events.size))
        runCurrent()
        val table = rig.play.table
        assertNotNull(table.heroView("wasp")?.hand)
        assertNotNull(table.heroView("xargatha")?.hand)
        assertNull(table.heroView("arien")?.hand)
        assertNull(table.heroView("brogan")?.hand)
        assertEquals(TurnStatus.YourTurn, rig.play.status, "the other seat's stub doesn't hide the real question")
        assertEquals("wasp", table.decision?.seat)
    }

    @Test
    fun framesOfAnotherGameAreIgnored() = runTest {
        val rig = rig()
        val held = rig.play.log.size
        val other = FakeServer()
        other.answerFirst()
        rig.feed(other.start("arien", gameId = "another"))
        rig.feed(other.table("arien", gameId = "another"))
        rig.feed(ServerMessage.End("another", Team.RED, null, rated = true))
        assertEquals(setOf("wasp"), rig.play.table.mine)
        assertEquals(held, rig.play.log.size)
        assertEquals(TurnStatus.YourTurn, rig.play.status)
    }

    // ---- The end -------------------------------------------------------------------------------------

    @Test
    fun youWonIsRightOnBothTeams() = runTest {
        val server = FakeServer()
        val ai = AiPlayers.deterministic(AiLevel.EASY, 7)
        var answers = 0
        while (true) {
            val pending = server.session.pending ?: break
            server.session.answer(pending.id, ai.choose(server.session, pending))
            assertTrue(++answers < MAX_ANSWERS, "the game never ended")
        }
        val red = rig("wasp", server)
        val blue = rig("arien", server)
        val winner = assertNotNull(server.session.view(null).winner)
        val redOver = assertNotNull(red.play.over)
        val blueOver = assertNotNull(blue.play.over)
        assertEquals(winner, redOver.winner)
        assertEquals(winner == Team.RED, redOver.youWon)
        assertEquals(winner == Team.BLUE, blueOver.youWon)
        assertNotEquals(redOver.youWon, blueOver.youWon)
        assertEquals(TurnStatus.Over, red.play.status)
        assertFalse(redOver.coach)
    }

    @Test
    fun theEndFrameEndsTheGameAndStopsReconnecting() = runTest {
        val rig = rig()
        val waspWins = ServerMessage.End(
            FakeServer.GAME,
            Team.RED,
            WinReason.THRONE,
            rated = true,
        )
        rig.feed(waspWins)
        assertEquals(TurnStatus.Over, rig.play.status)
        assertTrue(assertNotNull(rig.play.over).youWon)
        assertEquals(true, rig.play.online?.rated)
        assertFalse(rig.play.table.asking)
        rig.connector.last.close()
        advanceTimeBy(MINUTE)
        runCurrent()
        assertEquals(1, rig.connector.hellos.size)
        assertIs<OnlineState.InGame>(rig.driver.state.value)
    }

    @Test
    fun aVoidedGameSaysWhyAndHasNoResult() = runTest {
        val rules = rig()
        rules.feed(ServerMessage.End(FakeServer.GAME, null, null, rated = false, voided = VoidReason.RULES_CHANGED))
        assertEquals(TurnStatus.Over, rules.play.status)
        assertNull(rules.play.over)
        assertEquals(
            "A rules fix changed this game's past, so it can't go on. It won't count.",
            rules.play.online?.closed,
        )
        val abandoned = rig()
        abandoned.feed(ServerMessage.End(FakeServer.GAME, null, null, rated = false, voided = VoidReason.ABANDONED))
        assertEquals(
            "Nobody came back for a day, so this game was closed. It won't count.",
            abandoned.play.online?.closed,
        )
    }

    @Test
    fun aGameTheServerNoLongerHasIsSaidNotWaitedForForever() = runTest {
        val rig = rig()
        rig.connector.last.close()
        rig.connector.onOpen = { it.feed(ServerMessage.Welcome(1, "acc", "Me", currentTime)) }
        advanceTimeBy(BACKOFF_FIRST)
        runCurrent()
        assertIs<OnlineState.Reconnecting>(rig.driver.state.value)
        advanceTimeBy(GAME_GONE)
        runCurrent()
        assertIs<OnlineState.InGame>(rig.driver.state.value)
        assertEquals(TurnStatus.Over, rig.play.status)
        assertEquals(OnlineWords.GAME_GONE, rig.play.online?.closed)
    }

    // ---- The table's own state ---------------------------------------------------------------------

    @Test
    fun theBoardStartsOnYourSideAndFlips() = runTest {
        val red = rig("wasp")
        val blue = rig("arien")
        assertEquals(Team.RED, red.play.table.bottomTeam)
        assertEquals(Team.BLUE, blue.play.table.bottomTeam)
        blue.driver.onAction(GameAction.Flip)
        runCurrent()
        assertEquals(Team.RED, blue.play.table.bottomTeam)
    }

    @Test
    fun newEventsAreAnnouncedAndTheFirstTableIsNot() = runTest {
        val rig = rig()
        val server = rig.server
        assertNull(rig.play.announcement, "coming into a game isn't a reading of the whole log")
        val held = rig.play.log.size
        server.answerFirst()
        val fresh = server.session.events.drop(held)
        rig.feed(server.table("wasp", have = held))
        if (fresh.isNotEmpty()) {
            assertEquals(fresh.joinToString(" ") { it.text }, rig.play.announcement?.text)
        }
    }

    @Test
    fun theReviewAsksTheServerForThePersonsViewAndRewinds() = runTest {
        val rig = rig()
        val server = rig.server
        assertNull(rig.play.review)
        rig.driver.onAction(GameAction.Review(ReviewAction.Open(true)))
        runCurrent()
        val asked = rig.connector.last.said.filterIsInstance<ClientMessage.AskReview>()
        assertEquals(listOf(ClientMessage.AskReview(FakeServer.GAME)), asked)

        val reviews = server.session.review(setOf("wasp"))
        rig.feed(ServerMessage.Reviewed(FakeServer.GAME, reviews))
        assertEquals(reviews.size, rig.play.review?.turns?.size)

        val first = reviews.first()
        rig.driver.onAction(GameAction.Review(ReviewAction.Rewind(first.round, first.turn)))
        runCurrent()
        val rewound = rig.connector.last.said.filterIsInstance<ClientMessage.AskReview>().last()
        assertEquals(first.round, rewound.rewind?.round)
        rig.feed(ServerMessage.Reviewed(FakeServer.GAME, reviews, rewind = server.session.view("wasp")))
        if (reviews.first().hasStart) assertNotNull(rig.play.review?.rewind)

        rig.driver.onAction(GameAction.Review(ReviewAction.Open(false)))
        runCurrent()
        assertNull(rig.play.review)
    }

    private companion object {
        const val MAX_ANSWERS = 20_000
    }
}
