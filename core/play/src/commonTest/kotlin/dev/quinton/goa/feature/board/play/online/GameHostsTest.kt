package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.GameHost
import dev.quinton.goa.core.online.NetworkInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.Refusal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** `network.json` is read before connecting: too old means no socket at all, and so does a closed host. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameHostsTest {
    private val builtIn = "https://built.in/game"

    private fun network(vararg hosts: GameHost, minProtocol: Int = OnlineProtocol.VERSION) =
        NetworkInfo(NetworkInfo.SCHEMA, minProtocol, hosts.toList())

    @Test
    fun withNoFileTheBuiltInAddressHolds() {
        assertEquals(HostChoice(builtIn), chooseHost(null, builtIn))
    }

    @Test
    fun theFirstOpenHostInItsOrder() {
        val info = network(GameHost("https://a.example/", "closed"), GameHost("https://b.example/", "open"))
        assertEquals("https://b.example", chooseHost(info, builtIn).base)
    }

    @Test
    fun aClosedBuiltInAddressIsNotUsedAndTheNoteSaysWhy() {
        val info = network(GameHost("$builtIn/", HOST_STATUS_CLOSED, note = "Back on Sunday"))
        val choice = chooseHost(info, builtIn)
        assertNull(choice.base)
        assertEquals("Back on Sunday", choice.note)
    }

    @Test
    fun aHigherMinProtocolIsTooOldWhateverTheHosts() {
        assertTrue(chooseHost(network(minProtocol = OnlineProtocol.VERSION + 1), builtIn).tooOld)
        assertEquals(false, chooseHost(network(), builtIn).tooOld)
    }

    @Test
    fun theConnectorOpensNothingWhenTheFileSaysNot() = runTest {
        val hello = ClientMessage.Hello(1, "t", "test/1", 1)
        val opened = ArrayList<String>()
        fun connector(choice: HostChoice) = hostAwareConnector({ choice }) { base, _ ->
            opened += base
            FakeGameConnection { 0 }
        }
        val tooOld = assertFailsWith<HostUnavailable> { connector(HostChoice(builtIn, tooOld = true)).open(hello) }
        assertTrue(tooOld.tooOld)
        assertEquals(
            "Soon",
            assertFailsWith<HostUnavailable> { connector(HostChoice(null, note = "Soon")).open(hello) }.note,
        )
        assertTrue(opened.isEmpty())
        connector(HostChoice(builtIn)).open(hello)
        assertEquals(listOf(builtIn), opened)
    }

    @Test
    fun theDriverAsksForAnUpdateAndStops() = runTest {
        var tries = 0
        val driver = OnlineGameDriver(
            gameId = FakeServer.GAME,
            connector = {
                tries++
                throw HostUnavailable(tooOld = true)
            },
            identities = FakeIdentities(),
            catalog = { OnlineFixture.catalog },
            maps = { OnlineFixture.maps },
            clock = { 0 },
            scope = backgroundScope,
            client = "test/1",
            random = Random(1),
        )
        driver.start()
        runCurrent()
        assertEquals(Refusal.UPDATE_APP, assertIs<OnlineState.Refused>(driver.state.value).reason)
        advanceTimeBy(10 * MINUTE)
        runCurrent()
        assertEquals(1, tries)
    }

    @Test
    fun theDriverWaitsOutAClosedHostInWordsAndComesBackWhenItOpens() = runTest {
        val rig = DriverRig(this, "wasp", FakeServer())
        var closed = true
        val driver = OnlineGameDriver(
            gameId = FakeServer.GAME,
            connector = { hello ->
                if (closed) throw HostUnavailable(tooOld = false, note = "Back on Sunday")
                rig.connector.open(hello)
            },
            identities = FakeIdentities(),
            catalog = { OnlineFixture.catalog },
            maps = { OnlineFixture.maps },
            clock = { 0 },
            scope = backgroundScope,
            client = "test/1",
            random = Random(1),
        )
        driver.start()
        runCurrent()
        assertEquals("Back on Sunday", assertIs<OnlineState.Connecting>(driver.state.value).note)
        assertTrue(rig.connector.hellos.isEmpty())

        closed = false
        advanceTimeBy(MINUTE + 1)
        runCurrent()
        assertEquals(1, rig.connector.hellos.size)
        assertIs<OnlineState.InGame>(driver.state.value)
    }
}
