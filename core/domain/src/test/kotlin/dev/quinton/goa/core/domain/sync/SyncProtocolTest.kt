package dev.quinton.goa.core.domain.sync

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.protocol.ClientPhase
import dev.quinton.goa.core.domain.sync.protocol.HostPhase
import dev.quinton.goa.core.domain.sync.protocol.PeerStage
import dev.quinton.goa.core.domain.sync.protocol.SyncClient
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import dev.quinton.goa.core.domain.sync.protocol.SyncFailure
import dev.quinton.goa.core.domain.sync.protocol.SyncHost
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The sync session over the in-memory transport: a host and its group's phones at the table. */
class SyncProtocolTest {
    private val group = GroupCredentials.create("Friday nights")
    private val people = listOf("ann", "bob", "cid", "dee").map { player(it, it.replaceFirstChar(Char::uppercase)) }

    /** A phone's own log: everyone, plus [count] matches only this phone has, with bulky notes. */
    private fun phoneData(phone: String, count: Int, seed: Int): SyncDocument {
        val random = Random(seed)
        return SyncDocument(
            players = people,
            matches = (1..count).map { i ->
                match(
                    "$phone-m$i",
                    red = listOf("ann", "bob"),
                    blue = listOf("cid", "dee"),
                    updatedAt = i.toLong(),
                    playedAt = random.nextLong(0, 1000),
                    notes = (1..40).map { 'a' + random.nextInt(26) }.joinToString(""),
                )
            },
        )
    }

    private class Phone(
        val name: String,
        val data: InMemorySyncData,
        network: InMemorySyncNetwork,
        group: GroupCredentials,
    ) {
        val device = SyncDevice("id-$name", name)
        val transport = network.transport(name)
        val host by lazy { SyncHost(transport, group, device, data) }
        val client by lazy { SyncClient(transport, group, device, data) }
    }

    private fun CoroutineScope.runAll(host: Phone, clients: List<Phone>) {
        launch { host.host.run() }
        clients.forEach { launch { it.client.run() } }
    }

    @Test
    fun aHostAndThreePhonesEndWithIdenticalData() = runTest {
        val network = InMemorySyncNetwork(maxMessageBytes = 512) // forces chunking
        val phones = listOf("Host", "Bob", "Cid", "Dee").mapIndexed { i, n ->
            Phone(n, InMemorySyncData(phoneData(n, count = 5 + i, seed = i)), network, group)
        }
        val before = phones.map { it.data.document }
        val (host, clients) = phones.first() to phones.drop(1)
        runAll(host, clients)

        host.host.state.first { s -> s.peers.size == 3 && s.peers.all { it.stage == PeerStage.READY } }
        host.host.syncNow()
        host.host.state.first { it.phase == HostPhase.DONE }
        clients.forEach { c -> c.client.state.first { it.phase == ClientPhase.DONE } }

        val expected = SyncMerge.mergeAll(before)
        phones.forEach { assertThat(it.data.document).isEqualTo(expected) }
        assertThat(expected.matches).hasSize(5 + 6 + 7 + 8)
        assertThat(host.host.state.value.synced.map { it.name }).containsExactly("Bob", "Cid", "Dee")
        assertThat(host.host.state.value.changes!!.matchesAdded).isEqualTo(6 + 7 + 8)
        assertThat(clients[0].client.state.value.changes!!.matchesAdded).isEqualTo(5 + 7 + 8)
        assertThat(clients[0].client.state.value.host).isEqualTo(host.device)
    }

    @Test
    fun aPhoneDroppingMidUploadIsLeftOutAndUntouchedWhileTheOthersSync() = runTest {
        val network = InMemorySyncNetwork(maxMessageBytes = 512)
        network.dropAfter("Cid", messages = 4) // hello, proof, two chunks of its upload
        val host = Phone("Host", InMemorySyncData(phoneData("Host", 3, 1)), network, group)
        val bob = Phone("Bob", InMemorySyncData(phoneData("Bob", 3, 2)), network, group)
        val cid = Phone("Cid", InMemorySyncData(phoneData("Cid", 30, 3)), network, group)
        val cidBefore = cid.data.document
        runAll(host, listOf(bob, cid))

        host.host.state.first { s ->
            s.peers.size == 2 && s.peers.any { it.stage == PeerStage.READY } &&
                s.peers.any { it.stage == PeerStage.FAILED }
        }
        host.host.syncNow()
        host.host.state.first { it.phase == HostPhase.DONE }
        bob.client.state.first { it.phase == ClientPhase.DONE }
        cid.client.state.first { it.phase == ClientPhase.FAILED }

        assertThat(cid.data.document).isEqualTo(cidBefore)
        assertThat(cid.data.writes).isEqualTo(0)
        assertThat(host.data.document).isEqualTo(bob.data.document)
        assertThat(host.data.document.matches.map { it.id }).containsNoneIn(cidBefore.matches.map { it.id })
    }

    @Test
    fun aPhoneDroppingMidDownloadKeepsItsDataAndItsUploadStillReachesTheGroup() = runTest {
        val network = InMemorySyncNetwork(maxMessageBytes = 512)
        // The host's link to Cid breaks after hello, proof, "received" and one chunk of the merged data.
        network.dropAfter("Host", messages = 4, receiver = "Cid")
        val host = Phone("Host", InMemorySyncData(phoneData("Host", 20, 1)), network, group)
        val bob = Phone("Bob", InMemorySyncData(phoneData("Bob", 20, 2)), network, group)
        val cid = Phone("Cid", InMemorySyncData(phoneData("Cid", 4, 3)), network, group)
        val cidBefore = cid.data.document
        runAll(host, listOf(bob, cid))

        host.host.state.first { s -> s.peers.size == 2 && s.peers.all { it.stage == PeerStage.READY } }
        host.host.syncNow()
        host.host.state.first { it.phase == HostPhase.DONE }
        cid.client.state.first { it.phase == ClientPhase.FAILED }
        bob.client.state.first { it.phase == ClientPhase.DONE }

        assertThat(cid.data.writes).isEqualTo(0)
        assertThat(cid.data.document).isEqualTo(cidBefore)
        // Cid's matches made it to the host and Bob; Cid gets theirs next time.
        assertThat(host.data.document.matches.map { it.id }).containsAtLeastElementsIn(cidBefore.matches.map { it.id })
        assertThat(bob.data.document).isEqualTo(host.data.document)
        val cidRow = host.host.state.value.peers.single { it.name == "Cid" }
        assertThat(cidRow.stage).isEqualTo(PeerStage.FAILED)
        // Its data was in: the host can say so.
        assertThat(cidRow.failedAt).isEqualTo(PeerStage.SENDING)
    }

    @Test
    fun aPhoneWithTheWrongKeyGetsNothingAndGivesNothing() = runTest {
        val network = InMemorySyncNetwork()
        val host = Phone("Host", InMemorySyncData(phoneData("Host", 2, 1)), network, group)
        val bob = Phone("Bob", InMemorySyncData(phoneData("Bob", 2, 2)), network, group)
        // Same group id (so it finds the host), wrong key: an old invite after a key change, or a fake.
        val impostorGroup = GroupCredentials(group.groupId, SyncCrypto.newGroupKey(), group.name)
        val eve = Phone("Eve", InMemorySyncData(phoneData("Eve", 2, 3)), network, impostorGroup)
        val eveBefore = eve.data.document
        runAll(host, listOf(bob, eve))

        host.host.state.first { s ->
            s.peers.size == 2 && s.peers.any { it.stage == PeerStage.READY } &&
                s.peers.any { it.failure == SyncFailure.WRONG_KEY }
        }
        host.host.syncNow()
        host.host.state.first { it.phase == HostPhase.DONE }
        eve.client.state.first { it.phase == ClientPhase.FAILED }

        assertThat(eve.client.state.value.failure).isEqualTo(SyncFailure.WRONG_KEY)
        assertThat(eve.data.document).isEqualTo(eveBefore)
        assertThat(host.data.document.matches.map { it.id }).containsNoneIn(eveBefore.matches.map { it.id })
    }

    @Test
    fun aRelayBetweenTwoPhonesFailsTheKeyProof() = runTest {
        val network = InMemorySyncNetwork().apply { relayInTheMiddle = true }
        val host = Phone("Host", InMemorySyncData(phoneData("Host", 1, 1)), network, group)
        val bob = Phone("Bob", InMemorySyncData(phoneData("Bob", 1, 2)), network, group)
        runAll(host, listOf(bob))
        bob.client.state.first { it.phase == ClientPhase.FAILED }
        assertThat(bob.client.state.value.failure).isEqualTo(SyncFailure.WRONG_KEY)
        host.host.state.first { s -> s.peers.singleOrNull()?.failure == SyncFailure.WRONG_KEY }
        host.host.syncNow()
        host.host.state.first { it.phase == HostPhase.FAILED }
        assertThat(host.data.writes).isEqualTo(0)
    }

    @Test
    fun phonesOfAnotherGroupNeverSeeTheHost() = runTest {
        val network = InMemorySyncNetwork()
        val host = Phone("Host", InMemorySyncData(), network, group)
        val stranger = Phone("Zed", InMemorySyncData(), network, GroupCredentials.create("Other group"))
        launch { host.host.run() }
        val job = launch { stranger.client.run() }
        stranger.client.state.first { it.phase == ClientPhase.FAILED }
        assertThat(stranger.client.state.value.failure).isEqualTo(SyncFailure.NO_HOST)
        assertThat(host.host.state.value.peers).isEmpty()
        job.cancel()
        host.host.syncNow()
    }

    @Test
    fun whenNearbyCantRunBothSidesSayWhyAndNothingChanges() = runTest {
        val network = InMemorySyncNetwork().apply { unavailable = "Turn on Bluetooth." }
        val host = Phone("Host", InMemorySyncData(phoneData("Host", 1, 1)), network, group)
        val bob = Phone("Bob", InMemorySyncData(phoneData("Bob", 1, 2)), network, group)
        runAll(host, listOf(bob))
        host.host.state.first { it.phase == HostPhase.FAILED }
        bob.client.state.first { it.phase == ClientPhase.FAILED }
        assertThat(host.host.state.value.failure).isEqualTo(SyncFailure.UNAVAILABLE)
        assertThat(bob.client.state.value.failure).isEqualTo(SyncFailure.UNAVAILABLE)
        assertThat(bob.client.state.value.message).isEqualTo("Turn on Bluetooth.")
        assertThat(host.data.writes + bob.data.writes).isEqualTo(0)
    }

    @Test
    fun aPhoneWithInvalidDataIsRefusedWithTheReasonAndTheOthersSync() = runTest {
        val network = InMemorySyncNetwork()
        val host = Phone("Host", InMemorySyncData(phoneData("Host", 2, 1)), network, group)
        val bob = Phone("Bob", InMemorySyncData(phoneData("Bob", 2, 2)), network, group)
        val broken = phoneData("Cid", 1, 3).let {
            it.copy(
                matches =
                    it.matches + match("bad", listOf("ann"), listOf("ann")),
            )
        }
        val cid = Phone("Cid", InMemorySyncData(broken), network, group)
        runAll(host, listOf(bob, cid))

        cid.client.state.first { it.phase == ClientPhase.FAILED }
        assertThat(cid.client.state.value.message).contains("Ann is on both teams")
        host.host.state.first { s -> s.peers.any { it.stage == PeerStage.READY } }
        host.host.syncNow()
        bob.client.state.first { it.phase == ClientPhase.DONE }
        assertThat(host.data.document.matches.map { it.id }).doesNotContain("bad")
    }

    @Test
    fun aPhoneThatCantSaveIsReportedAndKeepsItsData() = runTest {
        val network = InMemorySyncNetwork()
        val host = Phone("Host", InMemorySyncData(phoneData("Host", 2, 1)), network, group)
        val bob = Phone("Bob", InMemorySyncData(phoneData("Bob", 2, 2), failApply = true), network, group)
        val bobBefore = bob.data.document
        runAll(host, listOf(bob))
        host.host.state.first { s -> s.peers.any { it.stage == PeerStage.READY } }
        host.host.syncNow()
        host.host.state.first { it.phase == HostPhase.DONE }
        assertThat(host.host.state.value.peers.single().failure).isEqualTo(SyncFailure.REJECTED)
        assertThat(bob.client.state.value.failure).isEqualTo(SyncFailure.APPLY_FAILED)
        assertThat(bob.data.document).isEqualTo(bobBefore)
    }

    @Test
    fun cancellingTheHostBeforeItSendsLeavesEveryPhoneAsItWas() = runTest {
        val network = InMemorySyncNetwork()
        val host = Phone("Host", InMemorySyncData(phoneData("Host", 2, 1)), network, group)
        val bob = Phone("Bob", InMemorySyncData(phoneData("Bob", 2, 2)), network, group)
        val bobBefore = bob.data.document
        val hostJob = launch { host.host.run() }
        launch { bob.client.run() }
        host.host.state.first { s -> s.peers.any { it.stage == PeerStage.READY } }
        hostJob.cancel()
        bob.client.state.first { it.phase == ClientPhase.FAILED }
        assertThat(bob.client.state.value.failure).isEqualTo(SyncFailure.DISCONNECTED)
        assertThat(bob.data.document).isEqualTo(bobBefore)
        assertThat(host.data.writes).isEqualTo(0)
    }

    @Test
    fun aDeletionOnOnePhoneReachesEveryPhone() = runTest {
        val network = InMemorySyncNetwork()
        val shared = phoneData("Shared", 3, 9)
        val target = shared.matches.first()
        val deleted = shared.copy(
            matches = shared.matches.drop(1),
            deletions = listOf(Deletion(DeletionKind.MATCH, target.id, target.updatedAt + 1)),
        )
        val host = Phone("Host", InMemorySyncData(shared), network, group)
        val bob = Phone("Bob", InMemorySyncData(deleted), network, group)
        runAll(host, listOf(bob))
        host.host.state.first { s -> s.peers.any { it.stage == PeerStage.READY } }
        host.host.syncNow()
        bob.client.state.first { it.phase == ClientPhase.DONE }
        assertThat(host.data.document.matches.map { it.id }).doesNotContain(target.id)
        assertThat(bob.data.document).isEqualTo(host.data.document)
        assertThat(host.host.state.value.changes!!.matchesRemoved).isEqualTo(1)
    }
}
