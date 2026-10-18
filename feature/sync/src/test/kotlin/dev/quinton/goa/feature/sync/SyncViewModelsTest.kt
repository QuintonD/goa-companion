package dev.quinton.goa.feature.sync

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.backup.AppInfo
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.InMemorySyncNetwork
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.protocol.PeerStage
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.sync.group.GroupEvent
import dev.quinton.goa.feature.sync.group.GroupViewModel
import dev.quinton.goa.feature.sync.importfile.ImportStage
import dev.quinton.goa.feature.sync.importfile.SyncFileImportViewModel
import dev.quinton.goa.feature.sync.me.WhoAreYouViewModel
import dev.quinton.goa.feature.sync.session.SessionRole
import dev.quinton.goa.feature.sync.session.SessionStage
import dev.quinton.goa.feature.sync.session.SyncSessionViewModel
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a friend at the table sees: hosting and joining end with the same data on every phone,
 * a phone with broken data is told before it shares it, joining by invite goes straight to the
 * first sync, and "Who are you?" never creates a second player with a taken name.
 */
@RunWith(RobolectricTestRunner::class)
class SyncViewModelsTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = EditClock { 1_000 }
    private val phoneName = PhoneName { "Galaxy S25 Ultra" }
    private val people = listOf(person("ann"), person("bob"), person("cid"), person("dee"))

    private inner class Phone(
        name: String,
        network: InMemorySyncNetwork,
        data: SyncDocument,
        inGroup: Boolean = true,
        version: String = "1.0",
    ) {
        val groups =
            FakeGroupRepository(
                groupState(if (inGroup) TestGroup else null).copy(deviceId = "id-$name", deviceName = name),
            )
        val sync = FakeSyncRepository(data)
        val transports = RoomTransports(network, name)
        val vm = SyncSessionViewModel(
            null,
            false,
            groups,
            sync,
            FakePlayerRepository(people),
            transports,
            clock,
            AppInfo { version },
            PhoneName { "Galaxy S25 Ultra" },
        )
    }

    private fun TestScope.watch(vararg phones: Phone) = phones.forEach { p ->
        backgroundScope.launch { p.vm.uiState.collect {} }
    }

    private fun data(vararg ids: String) = SyncDocument(
        players = people,
        matches = ids.map { game(it, listOf("ann", "bob"), listOf("cid", "dee")) },
    )

    @Test
    fun hostingAndJoiningEndWithTheSameDataAndEachPhoneRemembersTheOthers() = runTest {
        val network = InMemorySyncNetwork()
        val host = Phone("Host", network, data("h1"))
        val bob = Phone("Bob", network, data("b1", "b2"))
        val cid = Phone("Cid", network, data("c1"))
        watch(host, bob, cid)

        bob.vm.start(SessionRole.JOIN)
        cid.vm.start(SessionRole.JOIN)
        host.vm.start(SessionRole.HOST)
        host.vm.uiState.first { s ->
            (s.stage as? SessionStage.Hosting)?.state?.peers?.count {
                it.stage ==
                    PeerStage.READY
            } ==
                2
        }
        host.vm.syncNow()
        listOf(host, bob, cid).forEach { p -> p.vm.uiState.first { it.finished } }

        listOf(host, bob, cid).forEach { assertThat(it.vm.uiState.value.succeeded).isTrue() }
        val expected = host.sync.export()
        assertThat(expected.matches.map { it.id }).containsExactly("b1", "b2", "c1", "h1")
        assertThat(bob.sync.export()).isEqualTo(expected)
        assertThat(cid.sync.export()).isEqualTo(expected)
        assertThat(host.groups.current.value.phones.map { it.name }).containsExactly("Bob", "Cid")
        assertThat(bob.groups.current.value.phones.map { it.name }).containsExactly("Host")
        // The radios are released when the session ends.
        assertThat(host.transports.shutdowns).isEqualTo(1)
        assertThat(bob.transports.shutdowns).isEqualTo(1)
    }

    @Test
    fun aPhoneWithBrokenDataIsToldBeforeAnythingIsShared() = runTest {
        val broken = data("ok").let { it.copy(matches = it.matches + game("bad", listOf("ann"), listOf("ann"))) }
        val phone = Phone("Ann", InMemorySyncNetwork(), broken)
        watch(phone)
        phone.vm.start(SessionRole.HOST)
        val stage = phone.vm.uiState.first { it.stage is SessionStage.LocalProblem }.stage as SessionStage.LocalProblem
        assertThat(stage.message).contains("Ann is on both teams")
        assertThat(phone.transports.opened).isEqualTo(0)
    }

    @Test
    fun aPhoneOutsideAGroupIsSentToStartOrJoinOne() = runTest {
        val phone = Phone("Ann", InMemorySyncNetwork(), data(), inGroup = false)
        watch(phone)
        assertThat(phone.vm.uiState.first { it.groupName == null && it.deviceName.isNotEmpty() }.stage)
            .isEqualTo(SessionStage.NotInGroup)
    }

    @Test
    fun cancellingAJoinStopsTheRadiosAndGoesBackToTheChoice() = runTest {
        val phone = Phone("Bob", InMemorySyncNetwork(), data())
        watch(phone)
        phone.vm.start(SessionRole.JOIN) // no host in the room: still searching
        phone.vm.uiState.first { it.stage is SessionStage.Joining }
        phone.vm.reset()
        assertThat(phone.vm.uiState.first { it.stage == SessionStage.Choose }.busy).isFalse()
        assertThat(phone.transports.shutdowns).isEqualTo(1)
    }

    @Test
    fun twoPeopleWithTheSameNameAfterASyncGetAMergeHint() = runTest {
        val network = InMemorySyncNetwork()
        val host = Phone("Host", network, data())
        // Bob's phone added "Dee" again before it ever synced.
        val bob = Phone("Bob", network, data().copy(players = people + person("dee-2", "Dee", createdAt = 9)))
        val hostVm = SyncSessionViewModel(
            null,
            false,
            host.groups,
            host.sync,
            FakePlayerRepository(people + person("dee-2", "Dee", createdAt = 9)),
            host.transports,
            clock,
            AppInfo { "1.0" },
            PhoneName { "Galaxy S25 Ultra" },
        )
        backgroundScope.launch { hostVm.uiState.collect {} }
        watch(bob)
        bob.vm.start(SessionRole.JOIN)
        hostVm.start(SessionRole.HOST)
        hostVm.uiState.first { s -> (s.stage as? SessionStage.Hosting)?.state?.canSync == true }
        hostVm.syncNow()
        val done = hostVm.uiState.first { it.finished }
        assertThat(done.duplicateHint).contains("“Dee”")
    }

    @Test
    fun phonesOnDifferentAppVersionsAreToldTheirRatingsCanDiffer() = runTest {
        val network = InMemorySyncNetwork()
        val host = Phone("Host", network, data("h1"), version = "0.2.0")
        val bob = Phone("Bob", network, data("b1"), version = "0.1.0")
        watch(host, bob)
        bob.vm.start(SessionRole.JOIN)
        host.vm.start(SessionRole.HOST)
        host.vm.uiState.first { s -> (s.stage as? SessionStage.Hosting)?.state?.canSync == true }
        host.vm.syncNow()
        assertThat(host.vm.uiState.first { it.finished }.versionHint).contains("Bob (0.1.0)")
        assertThat(bob.vm.uiState.first { it.finished }.versionHint).contains("Host (0.2.0)")
        // The data itself still ends up the same.
        assertThat(bob.sync.export()).isEqualTo(host.sync.export())
    }

    @Test
    fun beforeChoosingYourselfThePhoneGoesByItsOwnNameNotItsModelCode() = runTest {
        val vm = GroupViewModel(
            FakeGroupRepository(groupState()),
            FakePlayerRepository(people),
            FakeMatchRepository(),
            FakeSyncRepository(),
            clock,
            phoneName,
        )
        assertThat(vm.uiState.first { !it.loading }.deviceName).isEqualTo("Galaxy S25 Ultra")
    }

    @Test
    fun joiningWithAnInviteGoesStraightToTheFirstSyncAndBadInvitesSayWhy() = runTest {
        val groups = FakeGroupRepository(groupState(group = null))
        val vm = GroupViewModel(
            groups,
            FakePlayerRepository(people),
            FakeMatchRepository(),
            FakeSyncRepository(),
            clock,
            phoneName,
        )
        backgroundScope.launch { vm.uiState.collect {} }
        vm.navigation.test {
            vm.join("https://example.com")
            assertThat(
                vm.uiState.first {
                    it.joinError != null
                }.joinError,
            ).contains("isn't a Guards of Atlantis group invite")
            vm.join(GroupInvite.format(TestGroup))
            assertThat(awaitItem()).isEqualTo(GroupEvent.OpenFirstSync)
            assertThat(vm.uiState.first { it.inGroup }.groupName).isEqualTo("Friday nights")
            // The same invite again: no second first sync, a note instead.
            vm.join(GroupInvite.format(TestGroup))
            assertThat(vm.uiState.first { it.message != null }.message).contains("already in")
            expectNoEvents()
        }
    }

    @Test
    fun startingAGroupShowsItsInviteAndLeavingKeepsTheData() = runTest {
        val groups = FakeGroupRepository(groupState(group = null))
        val sync = FakeSyncRepository(data("m1"))
        val vm = GroupViewModel(groups, FakePlayerRepository(people), FakeMatchRepository(), sync, clock, phoneName)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.start("   ")
        assertThat(vm.uiState.first { it.message != null }.inGroup).isFalse()
        vm.messageShown()
        vm.start("Friday nights")
        val state = vm.uiState.first { it.inGroup }
        assertThat(state.invite).startsWith("goa-sync:1?g=")
        assertThat(GroupInvite.parse(state.invite!!).getOrThrow().name).isEqualTo("Friday nights")
        vm.leave()
        assertThat(vm.uiState.first { !it.inGroup }.message).contains("stays on this phone")
        assertThat(sync.export().matches).hasSize(1)
    }

    @Test
    fun sharingASyncFileGivesOneThatOpensForTheGroup() = runTest {
        val sync = FakeSyncRepository(data("m1"))
        val vm =
            GroupViewModel(
                FakeGroupRepository(groupState()),
                FakePlayerRepository(people),
                FakeMatchRepository(),
                sync,
                clock,
                phoneName,
            )
        vm.navigation.test {
            vm.shareFile()
            val share = awaitItem() as GroupEvent.Share
            assertThat(share.fileName).endsWith(".goasync")
            assertThat(SyncCodec.decodeFile(share.bytes, TestGroup).matches.map { it.id }).containsExactly("m1")
        }
    }

    @Test
    fun whoAreYouNeverAddsATakenNameAndRemembersTheChoice() = runTest {
        val players = FakePlayerRepository(people)
        val groups = FakeGroupRepository(groupState())
        val vm = WhoAreYouViewModel(players, groups, clock)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onNameChange(" ann ")
        vm.createMe()
        assertThat(vm.uiState.first { it.nameError != null }.nameError).contains("already in the group")
        assertThat(players.state.value).hasSize(4)

        vm.onNameChange("Eve")
        vm.createMe()
        vm.uiState.first { it.done }
        val eve = players.state.value.single { it.name == "Eve" }
        assertThat(groups.current.value.mePlayerId).isEqualTo(eve.id)
        assertThat(eve.color).isNotEqualTo(0L)

        val again = WhoAreYouViewModel(players, groups, clock)
        again.choose("bob")
        assertThat(groups.current.value.mePlayerId).isEqualTo("bob")
    }

    private fun importVm(
        bytes: () -> ByteArray,
        inGroup: Boolean = true,
        sync: FakeSyncRepository = FakeSyncRepository(),
    ) =
        SyncFileImportViewModel(
            "content://test/file.goasync",
            { bytes() },
            FakeGroupRepository(groupState(if (inGroup) TestGroup else null)),
            sync,
        )

    @Test
    fun aSyncFileShowsWhatItWillChangeThenMergesIt() = runTest {
        val file = SyncCodec.encodeFile(data("m1", "m2"), TestGroup, createdAt = 5)
        val sync = FakeSyncRepository(data("m1"))
        val vm = importVm({ file }, sync = sync)
        val ready = vm.uiState.first { it.stage is ImportStage.Ready }.stage as ImportStage.Ready
        assertThat(ready.changes.matchesAdded).isEqualTo(1)
        assertThat(SyncText.summary(ready.changes)).isEqualTo("+1 match")
        vm.confirm()
        assertThat(vm.uiState.first { it.stage is ImportStage.Done }.stage).isEqualTo(ImportStage.Done(ready.changes))
        assertThat(sync.export().matches.map { it.id }).containsExactly("m1", "m2")
    }

    @Test
    fun aSyncFileFromAnotherGroupOrUnreadableGetsAClearError() = runTest {
        val other = dev.quinton.goa.core.domain.sync.GroupCredentials.create("Tuesday crew")
        val file = SyncCodec.encodeFile(data("m1"), other, createdAt = 5)
        val failed = importVm({ file }).uiState.first { it.stage is ImportStage.Failed }.stage as ImportStage.Failed
        assertThat(failed.problem).isEqualTo(SyncFileProblem.OTHER_GROUP)
        assertThat(failed.message).contains("Tuesday crew")

        val notJoined = importVm({ file }, inGroup = false).uiState.first { it.stage is ImportStage.Failed }.stage
        assertThat((notJoined as ImportStage.Failed).problem).isEqualTo(SyncFileProblem.NOT_IN_A_GROUP)

        val unreadable = importVm({ throw IOException("gone") }).uiState.first { it.stage is ImportStage.Failed }.stage
        assertThat((unreadable as ImportStage.Failed).problem).isNull()
    }
}
