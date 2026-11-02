package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.MyGame
import dev.quinton.goa.core.online.MyGames
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.RoomSeat
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.VoidReason
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException

/** Invites, words, the online home and the lobby. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlinePresentersTest {
    // ---- Invites -------------------------------------------------------------------------------------

    @Test
    fun anInviteIsReadFromALinkAFragmentOrACode() {
        val code = "ABCD1234"
        assertEquals(code, parseInvite(inviteLink(code)))
        assertEquals(code, parseInvite("https://quintond.github.io/goa-companion/#play=abcd1234"))
        assertEquals(code, parseInvite("#play=ABCD1234"))
        assertEquals(code, parseInvite("play=abcd1234"))
        assertEquals(code, parseInvite("  ABCD1234\n"))
        assertEquals(code, parseInvite("abcd1234"))
        assertEquals(code, parseInvite("abcd-1234"))
        assertEquals(code, parseInvite("Come and play: https://quintond.github.io/goa-companion/#play=abcd1234"))
        assertEquals(code, parseInvite("https://quintond.github.io/goa-companion/#play=abcd1234&from=qr"))
    }

    @Test
    fun crockfordsConfusablesReadAsTheDigitsTheyLookLike() {
        assertEquals("01100110", parseInvite("OIL0oilO"))
        assertEquals("A1B0C1D1", parseInvite("aIbOcLdi"))
    }

    @Test
    fun whatHoldsNoCodeIsNotOne() {
        listOf(
            "",
            "   ",
            "ABCD123",
            "ABCD12345",
            // U is not in Crockford's alphabet.
            "ABCDU234",
            "ABC!1234",
            "https://quintond.github.io/goa-companion/",
            "#play=",
            "https://quintond.github.io/goa-companion/#play=short",
        ).forEach { assertNull(parseInvite(it), "\"$it\"") }
    }

    // ---- Words ---------------------------------------------------------------------------------------

    @Test
    fun everyRefusalHasItsOwnPlainWords() {
        val words = Refusal.entries.map { OnlineWords.refusal(it) }
        words.forEach { assertTrue(it.isNotBlank() && it.endsWith("."), it) }
        assertEquals(words.size, words.toSet().size, "each says something different")
        assertTrue("computer" in OnlineWords.refusal(Refusal.ROOMS_FULL))
        assertTrue("minute" in OnlineWords.refusal(Refusal.ROOMS_FULL, 90))
        assertTrue("5 minutes" in OnlineWords.refusal(Refusal.ROOMS_FULL, 300))
        assertTrue("moment" in OnlineWords.refusal(Refusal.RATE_LIMITED, 10))
    }

    @Test
    fun presenceAndVoidedGamesHaveWords() {
        Presence.entries.forEach { assertTrue(OnlineWords.presence(it).isNotBlank()) }
        assertEquals(
            "A rules fix changed this game's past, so it can't go on. It won't count.",
            OnlineWords.voided(VoidReason.RULES_CHANGED),
        )
        assertEquals(
            "Nobody came back for a day, so this game was closed. It won't count.",
            OnlineWords.voided(VoidReason.ABANDONED),
        )
    }

    @Test
    fun aRestErrorBecomesARefusalOrAFailure() {
        assertEquals(
            OnlineResult.Refused(Refusal.ROOMS_FULL, 60),
            onlineFailure("""{"error":"rooms_full"}""", 60),
        )
        assertEquals(OnlineResult.Refused(Refusal.BAD_TOKEN), onlineFailure("""{"error":"bad_token"}""", null))
        assertEquals(OnlineResult.Failed("No."), onlineFailure("""{"error":"other","message":"No."}""", null))
        assertEquals(OnlineResult.Failed(), onlineFailure("<html>Bad gateway</html>", null))
    }

    @Test
    fun aFailureWithoutWordsSaysTheServerCantBeReachedAndTheComputerStillPlays() {
        val words = assertNotNull(OnlineResult.Failed().words())
        assertTrue("can't be reached" in words && "wait" in words && "computer" in words, words)
    }

    // ---- Account -------------------------------------------------------------------------------------

    private class HomeRig(test: TestScope, identity: OnlineIdentity? = null) {
        val gateway = FakeGateway()
        val identities = FakeIdentities(identity)
        val home = OnlineHomePresenter(gateway, identities, test.backgroundScope)
    }

    private val account = OnlineIdentity("acc", "token", "Ann")

    private fun TestScope.home(identity: OnlineIdentity? = null): HomeRig =
        HomeRig(this, identity).also {
            it.home.start()
            runCurrent()
        }

    @Test
    fun aNewAccountMustSaveItsRecoveryCodeBeforeGoingOn() = runTest {
        val rig = home()
        assertEquals(AccountStage.PICK_NAME, rig.home.state.value.stage)

        rig.home.createAccount()
        runCurrent()
        assertEquals(OnlineWords.NAME_NEEDED, rig.home.state.value.message)

        rig.home.setName("  Ann ")
        rig.home.createAccount()
        runCurrent()
        val shown = rig.home.state.value
        assertEquals(AccountStage.SAVE_CODE, shown.stage)
        assertEquals("RECOVER-1", shown.recoveryCode)
        assertNull(rig.identities.identity, "not kept until the code is saved")

        // Nothing goes on until the person says so.
        rig.home.createRoom()
        rig.gateway.joined = OnlineResult.Failed()
        rig.home.join("ABCD1234")
        rig.home.refreshGames()
        rig.home.rename("Other")
        rig.home.deleteAccount()
        runCurrent()
        assertEquals(AccountStage.SAVE_CODE, rig.home.state.value.stage)
        assertEquals("RECOVER-1", rig.home.state.value.recoveryCode)
        assertTrue(rig.gateway.rooms.isEmpty() && rig.gateway.joins.isEmpty())

        rig.home.recoveryCodeSaved()
        runCurrent()
        val ready = rig.home.state.value
        assertEquals(AccountStage.READY, ready.stage)
        assertNull(ready.recoveryCode, "shown once")
        assertEquals(OnlineIdentity("acc", "token", "Ann"), rig.identities.identity)
    }

    @Test
    fun linkingADeviceShowsTheNewCodeBecauseTheOldOneIsBurned() = runTest {
        val rig = home()
        rig.home.linkDevice()
        runCurrent()
        assertEquals(OnlineWords.RECOVERY_CODE_NEEDED, rig.home.state.value.message)
        rig.home.setLinkCode(" RECOVER-OLD ")
        rig.home.linkDevice()
        runCurrent()
        assertEquals(AccountStage.SAVE_CODE, rig.home.state.value.stage)
        assertEquals("RECOVER-2", rig.home.state.value.recoveryCode)
        assertEquals("Linked", rig.identities.identity?.name)
        assertEquals("token2", rig.identities.identity?.token)
        rig.home.recoveryCodeSaved()
        runCurrent()
        assertEquals(AccountStage.READY, rig.home.state.value.stage)
    }

    @Test
    fun aDeviceWithAnAccountGoesStraightToItsGames() = runTest {
        val rig = HomeRig(this, account)
        val game = MyGame("g", "ABCD1234", "forgotten-island", emptyList())
        rig.gateway.games = OnlineResult.Ok(MyGames(listOf(game)))
        rig.home.start()
        runCurrent()
        val ui = rig.home.state.value
        assertEquals(AccountStage.READY, ui.stage)
        assertEquals("Ann", ui.name)
        assertEquals(listOf(game), ui.games)
    }

    @Test
    fun aRefusedAccountAndAServerDownAreSaid() = runTest {
        val rig = home()
        rig.home.setName("Ann")
        rig.gateway.account = OnlineResult.Refused(Refusal.RATE_LIMITED, 300)
        rig.home.createAccount()
        runCurrent()
        assertEquals(OnlineWords.refusal(Refusal.RATE_LIMITED, 300), rig.home.state.value.message)
        assertFalse(rig.home.state.value.working)
        rig.gateway.account = OnlineResult.Failed()
        rig.home.createAccount()
        runCurrent()
        assertEquals(OnlineWords.UNREACHABLE, rig.home.state.value.message)
    }

    @Test
    fun aServerDownKeepsTheGamesAlreadyListed() = runTest {
        val rig = HomeRig(this, account)
        val game = MyGame("g", "ABCD1234", "forgotten-island", emptyList())
        rig.gateway.games = OnlineResult.Ok(MyGames(listOf(game)))
        rig.home.start()
        runCurrent()
        rig.gateway.games = OnlineResult.Failed()
        rig.home.refreshGames()
        runCurrent()
        assertEquals(listOf(game), rig.home.state.value.games)
        assertEquals(OnlineWords.UNREACHABLE, rig.home.state.value.message)
    }

    @Test
    fun aBadTokenAsksToSetUpAgainWithoutThrowingTheTokenAway() = runTest {
        val rig = HomeRig(this, account)
        rig.gateway.games = OnlineResult.Refused(Refusal.BAD_TOKEN)
        rig.home.start()
        runCurrent()
        assertEquals(AccountStage.PICK_NAME, rig.home.state.value.stage)
        assertEquals(OnlineWords.refusal(Refusal.BAD_TOKEN), rig.home.state.value.message)
        assertEquals(account, rig.identities.identity)
    }

    @Test
    fun renameAndDelete() = runTest {
        val rig = home(account)
        rig.home.rename("  ")
        runCurrent()
        assertEquals(OnlineWords.NAME_NEEDED, rig.home.state.value.message)
        rig.home.rename("New name")
        runCurrent()
        assertEquals("New name", rig.identities.identity?.name)
        assertEquals("New name", rig.home.state.value.name)
        rig.home.deleteAccount()
        runCurrent()
        assertNull(rig.identities.identity)
        assertEquals(AccountStage.PICK_NAME, rig.home.state.value.stage)
    }

    // ---- Rooms ---------------------------------------------------------------------------------------

    @Test
    fun theSizesAreTheOnesInOnlineMd() {
        assertEquals(listOf(1, 2, 3), OnlineRooms.option("forgotten-island")?.teamSizes)
        assertEquals(listOf(GameLength.QUICK, GameLength.LONG), OnlineRooms.option("forgotten-island")?.lengths)
        assertEquals(listOf(4, 5), OnlineRooms.option("across-the-river")?.teamSizes)
        assertNull(OnlineRooms.option("magma-and-ice"))
        val shipped = OnlineFixture.maps.map { it.id }
        OnlineRooms.options.forEach { assertTrue(it.mapId in shipped, it.mapId) }
    }

    @Test
    fun aRoomOffersOnlyWhatItsMapAllows() = runTest {
        val rig = home(account)
        val home = rig.home
        home.setTeamSize(5)
        assertEquals(1, home.state.value.room.teamSize, "5v5 isn't on Forgotten Island")
        home.setTeamSize(3)
        home.setLength(GameLength.LONG)
        home.setRanked(true)
        assertEquals(RoomForm("forgotten-island", GameLength.LONG, 3, true), home.state.value.room)

        home.setMap("across-the-river")
        assertEquals(RoomForm("across-the-river", GameLength.QUICK, 4, true), home.state.value.room)
        home.setLength(GameLength.LONG)
        assertEquals(GameLength.QUICK, home.state.value.room.length)
        home.setTeamSize(2)
        assertEquals(4, home.state.value.room.teamSize)
        home.setTeamSize(5)
        assertEquals(5, home.state.value.room.teamSize)
        home.setMap("no-such-map")
        assertEquals("across-the-river", home.state.value.room.mapId)
    }

    @Test
    fun creatingARoomJoinsItAndOpensIt() = runTest {
        val rig = home(account)
        val room = room()
        rig.gateway.joined = OnlineResult.Ok(room)
        rig.home.setRanked(true)
        rig.home.createRoom()
        runCurrent()
        assertEquals(listOf(CreateRoom("forgotten-island", GameLength.QUICK, 1, true)), rig.gateway.rooms)
        assertEquals(room, rig.home.state.value.openRoom)
        rig.home.roomOpened()
        assertNull(rig.home.state.value.openRoom)
    }

    @Test
    fun aFullServerIsSaidWithItsWaitAndTheComputerStillPlays() = runTest {
        val rig = home(account)
        rig.gateway.created = OnlineResult.Refused(Refusal.ROOMS_FULL, 120)
        rig.home.createRoom()
        runCurrent()
        val message = assertNotNull(rig.home.state.value.message)
        assertTrue("computer" in message && "2 minutes" in message, message)
        assertNull(rig.home.state.value.openRoom)
    }

    @Test
    fun joiningByCodeReadsWhatWasPastedAndSaysWhatWentWrong() = runTest {
        val rig = home(account)
        rig.home.setJoinText("no such code here")
        rig.home.join()
        runCurrent()
        assertEquals(OnlineWords.NOT_A_CODE, rig.home.state.value.message)

        rig.gateway.joined = OnlineResult.Refused(Refusal.NO_SUCH_ROOM)
        rig.home.setJoinText("abcd-1234")
        rig.home.join()
        runCurrent()
        assertEquals(listOf("ABCD1234"), rig.gateway.joins)
        assertEquals(OnlineWords.refusal(Refusal.NO_SUCH_ROOM), rig.home.state.value.message)

        val room = room()
        rig.gateway.joined = OnlineResult.Ok(room)
        rig.home.join(inviteLink("ABCD1234"))
        runCurrent()
        assertEquals(room, rig.home.state.value.openRoom)
    }

    // ---- The lobby -----------------------------------------------------------------------------------

    private val heroes = listOf("wasp", "arien", "brogan", "xargatha")

    private fun room(
        seats: List<RoomSeat> = listOf(RoomSeat(Team.RED, 0), RoomSeat(Team.BLUE, 0)),
        host: String = "acc",
        status: RoomStatus = RoomStatus.OPEN,
        gameId: String? = null,
    ) = RoomView("ABCD1234", host, "forgotten-island", GameLength.QUICK, 1, true, seats, status, 0, gameId)

    private inner class LobbyRig(test: TestScope, initial: RoomView? = room()) {
        val gateway = FakeGateway()
        val connector = FakeConnector { test.currentTime }
        val identities = FakeIdentities()
        val lobby = OnlineLobbyPresenter(
            code = "ABCD1234",
            gateway = gateway,
            connector = connector,
            identities = identities,
            playableHeroes = heroes,
            catalogVersion = { 7 },
            scope = test.backgroundScope,
            client = "test/1",
            initial = initial,
            random = Random(1),
        )
        private val scope = test

        fun feed(frame: ServerMessage) {
            connector.last.feed(frame)
            scope.runCurrent()
        }

        fun lobbyFrame(seats: List<RoomSeat>, status: RoomStatus = RoomStatus.OPEN, gameId: String? = null) =
            feed(ServerMessage.Lobby(room(seats, status = status, gameId = gameId)))

        val said: List<ClientMessage> get() = connector.last.said
    }

    private fun TestScope.lobby(initial: RoomView? = room()): LobbyRig = LobbyRig(this, initial).also {
        it.lobby.start()
        runCurrent()
    }

    @Test
    fun theLobbyWatchesItsRoomAndGivesTheInvite() = runTest {
        val rig = lobby()
        val ui = rig.lobby.state.value
        assertEquals("https://quintond.github.io/goa-companion/#play=ABCD1234", ui.inviteLink)
        assertTrue(ui.connected)
        assertEquals("acc", ui.you)
        assertTrue(ui.isHost)
        assertEquals(listOf<ClientMessage>(ClientMessage.WatchRoom("ABCD1234")), rig.said)
        assertEquals(7, rig.connector.hellos.single().catalogVersion)
    }

    @Test
    fun aLobbyWithoutARoomYetJoinsFirstAndSaysWhyNot() = runTest {
        val refused = LobbyRig(this, initial = null)
        refused.gateway.joined = OnlineResult.Refused(Refusal.ROOM_TAKEN)
        refused.lobby.start()
        runCurrent()
        assertEquals(OnlineWords.refusal(Refusal.ROOM_TAKEN), refused.lobby.state.value.message)
        assertTrue(refused.connector.hellos.isEmpty())

        val joined = LobbyRig(this, initial = null)
        joined.gateway.joined = OnlineResult.Ok(room())
        joined.lobby.start()
        runCurrent()
        assertEquals(listOf("ABCD1234"), joined.gateway.joins)
        assertEquals(1, joined.connector.hellos.size)
    }

    @Test
    fun seatsAndHeroesAreAskedForAndShownOnceTheRoomSaysSo() = runTest {
        val rig = lobby()
        val lobby = rig.lobby
        lobby.pickHero("wasp")
        assertEquals(OnlineWords.SEAT_FIRST, lobby.state.value.message)

        lobby.pickSeat(Team.RED, 0)
        runCurrent()
        assertEquals(ClientMessage.PickSeat("ABCD1234", Team.RED, 0, null), rig.said.last())
        rig.lobbyFrame(listOf(RoomSeat(Team.RED, 0, "acc", "Me"), RoomSeat(Team.BLUE, 0, "b", "Bob", "wasp")))
        assertEquals(Team.RED, lobby.state.value.yourSeat?.team)

        assertEquals(listOf("arien", "brogan", "xargatha"), lobby.state.value.heroes, "wasp is taken")
        lobby.pickHero("wasp")
        assertEquals(OnlineWords.HERO_TAKEN, lobby.state.value.message)
        lobby.pickHero("not-playable")
        assertEquals(OnlineWords.HERO_TAKEN, lobby.state.value.message)
        lobby.setReady(true)
        assertEquals(OnlineWords.HERO_FIRST, lobby.state.value.message)
        assertFalse(lobby.state.value.canReady)

        lobby.pickHero("arien")
        runCurrent()
        assertEquals(ClientMessage.PickSeat("ABCD1234", Team.RED, 0, "arien"), rig.said.last())

        lobby.pickSeat(Team.BLUE, 0)
        assertEquals(OnlineWords.SEAT_TAKEN, lobby.state.value.message)
    }

    @Test
    fun onlyTheHostStartsAndOnlyOnceEveryoneIsReady() = runTest {
        val rig = lobby()
        val lobby = rig.lobby
        val me = RoomSeat(Team.RED, 0, "acc", "Me", "arien")
        val bob = RoomSeat(Team.BLUE, 0, "b", "Bob", "wasp")

        rig.lobbyFrame(listOf(me, bob))
        assertTrue(lobby.state.value.canReady)
        assertEquals(0, lobby.state.value.readyCount)
        lobby.setReady(true)
        runCurrent()
        assertEquals(ClientMessage.Ready("ABCD1234", true), rig.said.last())
        lobby.startGame()
        runCurrent()
        assertTrue(rig.said.none { it is ClientMessage.Start })

        rig.lobbyFrame(listOf(me.copy(ready = true), bob))
        assertEquals(1, lobby.state.value.readyCount)
        assertFalse(lobby.state.value.canStart)

        rig.lobbyFrame(listOf(me.copy(ready = true), bob.copy(ready = true)))
        assertTrue(lobby.state.value.canStart)
        lobby.startGame()
        runCurrent()
        assertEquals(ClientMessage.Start("ABCD1234"), rig.said.last())

        val guest = LobbyRig(this, room(host = "someone-else"))
        guest.lobby.start()
        runCurrent()
        val allReady = listOf(me.copy(ready = true), bob.copy(ready = true))
        guest.feed(ServerMessage.Lobby(room(allReady, host = "someone-else")))
        assertFalse(guest.lobby.state.value.isHost)
        assertFalse(guest.lobby.state.value.canStart)
        guest.lobby.startGame()
        runCurrent()
        assertTrue(guest.said.none { it is ClientMessage.Start })
    }

    @Test
    fun theStartedRoomOpensTheGameAndClosesTheLobbysSocket() = runTest {
        val rig = lobby()
        rig.lobbyFrame(room().seats, RoomStatus.STARTED, gameId = "game-9")
        assertEquals("game-9", rig.lobby.state.value.gameId)
        assertTrue(rig.connector.last.closed)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, rig.connector.hellos.size)
    }

    @Test
    fun aRefusalInTheLobbyIsSaidAndTheRoomCarriesOn() = runTest {
        val rig = lobby()
        rig.feed(ServerMessage.Refused(Refusal.ROOM_TAKEN))
        assertEquals(OnlineWords.refusal(Refusal.ROOM_TAKEN), rig.lobby.state.value.message)
        assertFalse(rig.connector.last.closed)
        rig.lobbyFrame(room().seats)
        assertEquals(OnlineWords.refusal(Refusal.ROOM_TAKEN), rig.lobby.state.value.message, "still to be read")

        rig.feed(ServerMessage.Refused(Refusal.UPDATE_APP))
        assertEquals(OnlineWords.refusal(Refusal.UPDATE_APP), rig.lobby.state.value.message)
        assertTrue(rig.connector.last.closed)
    }

    @Test
    fun aFrameTheLobbyCantReadAsksForAnUpdate() = runTest {
        val rig = lobby()
        rig.connector.last.failWith(SerializationException("new"))
        runCurrent()
        assertEquals(OnlineWords.refusal(Refusal.UPDATE_APP), rig.lobby.state.value.message)
    }

    @Test
    fun aDroppedLobbySaysSoAndComesBack() = runTest {
        val rig = lobby()
        rig.connector.last.close()
        runCurrent()
        assertFalse(rig.lobby.state.value.connected)
        assertEquals(OnlineWords.UNREACHABLE, rig.lobby.state.value.message)
        rig.lobby.pickSeat(Team.RED, 0)
        assertEquals(OnlineWords.UNREACHABLE, rig.lobby.state.value.message)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, rig.connector.hellos.size)
        assertTrue(rig.lobby.state.value.connected)
        rig.lobbyFrame(room().seats)
        assertNull(rig.lobby.state.value.message)
    }

    @Test
    fun aClosedRoomSaysSoAndStops() = runTest {
        val rig = lobby()
        rig.lobbyFrame(room().seats, RoomStatus.CLOSED)
        assertEquals(OnlineWords.ROOM_CLOSED, rig.lobby.state.value.message)
        assertTrue(rig.connector.last.closed)
    }

    @Test
    fun leavingGoesBackAndIsSaidIfItFailed() = runTest {
        val rig = lobby()
        rig.lobby.leave()
        runCurrent()
        val ui = rig.lobby.state.value
        assertTrue(ui.left)
        assertEquals(OnlineWords.UNREACHABLE, ui.message)
        assertTrue(rig.connector.last.closed)
    }
}
