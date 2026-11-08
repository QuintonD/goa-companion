package dev.quinton.goa.feature.sync

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.protocol.HostPhase
import dev.quinton.goa.core.domain.sync.protocol.HostState
import dev.quinton.goa.core.domain.sync.protocol.PeerProgress
import dev.quinton.goa.core.domain.sync.protocol.PeerStage
import dev.quinton.goa.core.domain.sync.protocol.SyncFailure
import dev.quinton.goa.feature.sync.nearby.NearbyPermissions
import dev.quinton.goa.feature.sync.share.SyncFiles
import java.time.LocalDate
import org.junit.Test

class SyncTextTest {
    @Test
    fun theResultSummaryReadsLikeTheTableWouldSayIt() {
        assertThat(SyncText.summary(SyncChanges.NONE)).isEqualTo("Already up to date")
        assertThat(
            SyncText.summary(SyncChanges(matchesAdded = 5, playersAdded = 1, matchesUpdated = 1, playersUpdated = 1)),
        ).isEqualTo("+5 matches, +1 player, 2 updated")
        assertThat(SyncText.summary(SyncChanges(matchesAdded = 1, matchesRemoved = 1, playersMerged = 1)))
            .isEqualTo("+1 match, 1 removed, 1 duplicate merged")
        assertThat(SyncText.summary(SyncChanges(ratingConstantsChanged = true))).isEqualTo("new rating constants")
        assertThat(SyncText.summary(SyncChanges(matchesAdded = 2, triviaRoundsAdded = 3)))
            .isEqualTo("+2 matches, +3 trivia rounds")
        assertThat(SyncText.summary(SyncChanges(triviaRoundsAdded = 1, triviaRoundsRemoved = 2, matchesRemoved = 1)))
            .isEqualTo("+1 trivia round, 3 removed")
    }

    @Test
    fun theHostSaysWhetherADroppedPhonesDataMadeIt() {
        fun dropped(at: PeerStage) = SyncText.peerStage(
            PeerProgress("1", "Bob", stage = PeerStage.FAILED, failure = SyncFailure.DISCONNECTED, failedAt = at),
        )
        assertThat(dropped(PeerStage.RECEIVING)).contains("before its data arrived")
        assertThat(dropped(PeerStage.READY)).contains("Its data is in")
        assertThat(dropped(PeerStage.CONFIRMING)).contains("may be up to date")
    }

    @Test
    fun theHostMergesOnlyOnceEveryIncludedPhonesDataIsIn() {
        val bob = PeerProgress("1", "Bob", stage = PeerStage.READY)
        val cid = PeerProgress("2", "Cid", stage = PeerStage.RECEIVING, progress = 0.6f)
        val eve = PeerProgress("3", "Eve", stage = PeerStage.FAILED, failure = SyncFailure.WRONG_KEY)
        val all = listOf(bob, cid, eve)
        // The count only: Eve's own row says why she is out.
        assertThat(SyncText.hostIncluded(all)).isEqualTo("2 phones included · 1 excluded")
        assertThat(SyncText.hostIncluded(listOf(bob))).isEqualTo("1 phone included")
        // Cid still sending: the command is off and waits for him, with his upload's live percent.
        assertThat(SyncText.hostAction(all)).isEqualTo("Waiting for Cid · 60%")
        assertThat(SyncText.readyToMerge(HostState(peers = all))).isFalse()
        assertThat(SyncText.hostAction(listOf(cid.copy(stage = PeerStage.VERIFYING, progress = null))))
            .isEqualTo("Waiting for Cid…")
        assertThat(SyncText.hostAction(listOf(cid, cid.copy(linkId = "4", name = "Dee", progress = 0.2f))))
            .isEqualTo("Waiting for Cid and Dee · 40%")
        // Every included phone in: the command merges them (Eve left out).
        val ready = listOf(bob, cid.copy(stage = PeerStage.READY, progress = null), eve)
        assertThat(SyncText.hostAction(ready)).isEqualTo("Merge data from 2 phones")
        assertThat(SyncText.readyToMerge(HostState(peers = ready))).isTrue()
        assertThat(SyncText.readyToMerge(HostState(phase = HostPhase.MERGING, peers = ready))).isFalse()
        assertThat(SyncText.hostAction(listOf(bob))).isEqualTo("Merge data from 1 phone")
        // Nothing to merge yet.
        assertThat(SyncText.hostAction(listOf(eve))).isEqualTo("Waiting for a phone to join…")
        assertThat(SyncText.hostAction(emptyList())).isEqualTo("Waiting for a phone to join…")
        assertThat(SyncText.readyToMerge(HostState(peers = listOf(eve)))).isFalse()
        // The auto-start switch, armed, says what it waits for.
        assertThat(SyncText.autoStartArmed(all)).isEqualTo("On · merges once Cid's data is in")
        assertThat(SyncText.hostMerging(listOf(bob))).isEqualTo("Merging…")
        assertThat(SyncText.hostStep(HostPhase.MERGING)).isEqualTo(1)
    }

    @Test
    fun hostDoneSaysWhoIsUpToDate() {
        val bob = PeerProgress("1", "Bob", stage = PeerStage.DONE)
        val cid = PeerProgress("2", "Cid", stage = PeerStage.DONE)
        val eve = PeerProgress("3", "Eve", stage = PeerStage.FAILED, failure = SyncFailure.WRONG_KEY)
        val dee = PeerProgress("4", "Dee", stage = PeerStage.FAILED, failure = SyncFailure.DISCONNECTED)
        assertThat(SyncText.hostDoneTitle(listOf(bob, cid, eve))).isEqualTo("Every phone is up to date: Bob and Cid")
        assertThat(SyncText.hostDoneMissed(listOf(bob, cid, eve))).isNull()
        assertThat(SyncText.hostDoneTitle(listOf(bob, dee))).isEqualTo("Synced with Bob")
        assertThat(SyncText.hostDoneMissed(listOf(bob, dee))).isEqualTo("Dee didn't finish: sync again to include it.")
    }

    @Test
    fun lastSyncedIsShortAndHuman() {
        val now = 10_000_000_000L
        assertThat(SyncText.ago(now - 10_000, now)).isEqualTo("just now")
        assertThat(SyncText.ago(now - 5 * 60_000, now)).isEqualTo("5 min ago")
        assertThat(SyncText.ago(now - 3 * 3_600_000, now)).isEqualTo("3 h ago")
        assertThat(SyncText.ago(now - 2 * 86_400_000, now)).isEqualTo("2 days ago")
    }

    @Test
    fun duplicateNamesIgnoreCaseAndSpaces() {
        val names = SyncText.duplicateNames(listOf(person("a", "Dee"), person("b", " dee "), person("c", "Cid")))
        assertThat(names).containsExactly("Dee")
        assertThat(SyncText.duplicateHint(emptyList())).isNull()
    }

    @Test
    fun nearbyAsksForWhatEachAndroidVersionNeedsAndNothingMore() {
        val coarse = "android.permission.ACCESS_COARSE_LOCATION"
        val fine = "android.permission.ACCESS_FINE_LOCATION"
        val bluetooth = listOf(
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
        )
        val wifi = "android.permission.NEARBY_WIFI_DEVICES"
        val lan = "android.permission.ACCESS_LOCAL_NETWORK"
        assertThat(NearbyPermissions.required(26)).containsExactly(coarse)
        assertThat(NearbyPermissions.required(29)).containsExactly(fine)
        // Android 12 ignores FINE asked without COARSE: a friend on 12 could never allow Nearby.
        assertThat(NearbyPermissions.required(31)).containsExactlyElementsIn(listOf(fine, coarse) + bluetooth)
        assertThat(NearbyPermissions.required(32)).containsExactlyElementsIn(bluetooth)
        assertThat(NearbyPermissions.required(36)).containsExactlyElementsIn(bluetooth + wifi)
        assertThat(NearbyPermissions.required(37)).containsExactlyElementsIn(bluetooth + wifi + lan)
        assertThat(NearbyPermissions.groupName(30)).isEqualTo("Location")
        assertThat(NearbyPermissions.groupName(36)).isEqualTo("Nearby devices")
    }

    @Test
    fun syncFileNamesAreSafeAndDated() {
        assertThat(SyncFiles.fileName("Friday Nights!", LocalDate.of(2026, 10, 17)))
            .isEqualTo("goa-sync-friday-nights-2026-10-17.goasync")
        assertThat(SyncFiles.fileName("???", LocalDate.of(2026, 1, 2))).isEqualTo("goa-sync-group-2026-01-02.goasync")
    }
}
