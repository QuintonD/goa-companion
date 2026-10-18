package dev.quinton.goa.feature.sync

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.PlayerMerge
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerMergeRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.sync.invite.GroupInviteViewModel
import dev.quinton.goa.feature.sync.invite.InviteFor
import dev.quinton.goa.feature.sync.me.MergeOffer
import dev.quinton.goa.feature.sync.me.WhoAreYouViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Linking a phone to its player (ADR-031). The owner entered Bob; Bob installs the app and joins
 * with a personal invite: his phone asks "You're Bob?" and becomes Bob. A friend who used the app
 * alone first can merge that old player in; one who types a look-alike name is asked first.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerLinkingTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock = EditClock { 1_000 }
    private val people = listOf(person("ann"), person("bob"), person("cid"), person("dee"))

    private inner class Phone(
        extra: List<Player> = emptyList(),
        me: String? = null,
        invited: String? = null,
        log: List<dev.quinton.goa.core.model.match.Match> = emptyList(),
        merged: List<PlayerMerge> = emptyList(),
    ) {
        val players = FakePlayerRepository(people + extra, kotlinx.coroutines.flow.MutableStateFlow(merged))
        val matches = FakeMatchRepository(log)
        val merges = FakePlayerMergeRepository(matches, players.merges)
        val groups = FakeGroupRepository(groupState(me = me).copy(invitedPlayerId = invited))

        fun whoAreYou(scope: TestScope) = WhoAreYouViewModel(players, groups, matches, merges, clock).also { vm ->
            scope.backgroundScope.launch { vm.uiState.collect {} }
        }

        fun invite(scope: TestScope, playerId: String? = null) =
            GroupInviteViewModel(playerId, groups, players, merges).also { vm ->
                scope.backgroundScope.launch { vm.uiState.collect {} }
            }
    }

    @Test
    fun inviteAnyoneOrAPlayerYouEntered() = runTest {
        val vm = Phone(me = "ann").invite(this)
        val choosing = vm.uiState.first { !it.loading }
        assertThat(choosing.choosing).isTrue()
        assertThat(choosing.invite).isNull()
        // Not yourself: you're already on this phone.
        assertThat(choosing.players.map { it.id }).containsExactly("bob", "cid", "dee").inOrder()

        vm.choose(InviteFor.One("bob"))
        val forBob = vm.uiState.first { !it.choosing }
        assertThat(forBob.player?.name).isEqualTo("Bob")
        val parsed = GroupInvite.parseInvite(forBob.invite!!).getOrThrow()
        assertThat(parsed.playerId).isEqualTo("bob")
        assertThat(parsed.group.groupId).isEqualTo(TestGroup.groupId)
        assertThat(forBob.shareText).startsWith("For Bob: ")
        assertThat(GroupInvite.parseInvite(forBob.shareText!!).getOrThrow().playerId).isEqualTo("bob")

        vm.choose(InviteFor.Anyone)
        val anyone = vm.uiState.first { it.player == null && !it.choosing }
        assertThat(GroupInvite.parseInvite(anyone.invite!!).getOrThrow().playerId).isNull()

        vm.choose(InviteFor.Choosing)
        assertThat(vm.uiState.first { it.choosing }.invite).isNull()
    }

    @Test
    fun aPlayerPageInviteIsForThatPlayerOrWhoTheyWereMergedInto() = runTest {
        val phone = Phone(extra = listOf(person("bob-2", "Bobby")), merged = listOf(PlayerMerge("bob-2", "bob", 1)))
        val state = phone.invite(this, playerId = "bob-2").uiState.first { !it.loading }
        assertThat(state.choosing).isFalse()
        assertThat(state.player?.id).isEqualTo("bob")
        // Gone altogether: ask who it's for.
        assertThat(phone.invite(this, playerId = "ghost").uiState.first { !it.loading }.choosing).isTrue()
    }

    @Test
    fun withoutAGroupThereIsNoInvite() = runTest {
        val phone = Phone()
        phone.groups.leave()
        val state = phone.invite(this, "bob").uiState.first { !it.loading }
        assertThat(state.inGroup).isFalse()
        assertThat(state.invite).isNull()
    }

    @Test
    fun yesMakesThisPhoneTheInvitedPlayer() = runTest {
        val phone = Phone(invited = "bob")
        val vm = phone.whoAreYou(this)
        assertThat(vm.uiState.first { !it.loading }.invited?.name).isEqualTo("Bob")
        vm.confirmInvited()
        vm.uiState.first { it.done }
        assertThat(phone.groups.current.value.mePlayerId).isEqualTo("bob")
        assertThat(phone.groups.current.value.invitedPlayerId).isNull()
        assertThat(phone.players.state.value).hasSize(4)
    }

    @Test
    fun notMeShowsTheListAndForgetsTheInvite() = runTest {
        val phone = Phone(invited = "bob")
        val vm = phone.whoAreYou(this)
        vm.uiState.first { it.invited != null }
        vm.notMe()
        val list = vm.uiState.first { it.invited == null }
        assertThat(list.done).isFalse()
        assertThat(list.players).hasSize(4)
        assertThat(phone.groups.current.value.invitedPlayerId).isNull()
        assertThat(phone.groups.current.value.mePlayerId).isNull()
        vm.choose("cid")
        assertThat(vm.uiState.first { it.done }.meId).isEqualTo("cid")
    }

    @Test
    fun anInvitedPlayerNotOnThePhoneYetFallsBackToTheList() = runTest {
        // The first sync failed: Bob's record hasn't arrived.
        val phone = Phone(invited = "ghost")
        val state = phone.whoAreYou(this).uiState.first { !it.loading }
        assertThat(state.invited).isNull()
        assertThat(state.players).hasSize(4)
        // Kept: a later sync may still bring the player.
        assertThat(phone.groups.current.value.invitedPlayerId).isEqualTo("ghost")
    }

    @Test
    fun aMergedInvitedPlayerReadsAsTheOneTheyJoined() = runTest {
        val phone = Phone(
            extra = listOf(person("bob-2", "Bobby")),
            invited = "bob-2",
            merged = listOf(PlayerMerge("bob-2", "bob", 1)),
        )
        assertThat(phone.whoAreYou(this).uiState.first { !it.loading }.invited?.id).isEqualTo("bob")
    }

    @Test
    fun yesOffersToMergeThePlayerThisPhoneUsedAlone() = runTest {
        val quin = person("quin", "Quin", createdAt = 5)
        val phone = Phone(
            extra = listOf(quin),
            me = "quin",
            invited = "bob",
            log = listOf(game("solo", listOf("quin"), listOf("cid"))),
        )
        val vm = phone.whoAreYou(this)
        vm.uiState.first { it.invited != null }
        vm.confirmInvited()
        val offer = vm.uiState.first { it.mergeOffer != null }.mergeOffer
        assertThat(offer).isEqualTo(MergeOffer(quin, 1, people[1]))
        assertThat(phone.groups.current.value.mePlayerId).isEqualTo("bob")
        vm.mergeOld()
        vm.uiState.first { it.done }
        assertThat(PlayerMerges(phone.merges.merges.value).resolve("quin")).isEqualTo("bob")
        assertThat(phone.players.observePlayers().first().map { it.id }).doesNotContain("quin")
    }

    @Test
    fun keepBothLeavesTheOldPlayerAlone() = runTest {
        val phone = Phone(extra = listOf(person("quin", "Quin")), me = "quin", invited = "bob")
        val vm = phone.whoAreYou(this)
        vm.uiState.first { it.invited != null }
        vm.confirmInvited()
        vm.uiState.first { it.mergeOffer != null }
        vm.keepBoth()
        vm.uiState.first { it.done }
        assertThat(phone.merges.merges.value).isEmpty()
        assertThat(phone.groups.current.value.mePlayerId).isEqualTo("bob")
    }

    @Test
    fun aMergeOfTwoPeopleWhoPlayedTogetherIsRefusedPlainly() = runTest {
        val phone = Phone(
            extra = listOf(person("quin", "Quin")),
            me = "quin",
            invited = "bob",
            log = listOf(game("together", listOf("quin", "bob"), listOf("cid", "dee"))),
        )
        val vm = phone.whoAreYou(this)
        vm.uiState.first { it.invited != null }
        vm.confirmInvited()
        vm.uiState.first { it.mergeOffer != null }
        vm.mergeOld()
        val refused = vm.uiState.first { it.mergeRefused != null }
        assertThat(refused.mergeRefused!!.single()).endsWith("Quin, Bob vs Cid, Dee")
        assertThat(refused.done).isFalse()
        vm.refusalShown()
        vm.uiState.first { it.done }
        assertThat(phone.merges.merges.value).isEmpty()
        assertThat(phone.groups.current.value.mePlayerId).isEqualTo("bob")
    }

    @Test
    fun addMeAsksIsOneOfTheseYouFirst() = runTest {
        val phone = Phone(extra = listOf(person("robert", "Robert")))
        val vm = phone.whoAreYou(this)
        vm.onNameChange("Rob")
        vm.createMe()
        val check = vm.uiState.first { it.addCheck != null }.addCheck!!
        assertThat(check.candidates.map { it.name }).containsExactly("Robert")
        assertThat(check.nameTaken).isFalse()
        assertThat(phone.players.state.value).hasSize(5)
        // "That's me": pick Robert, nothing added.
        vm.choose("robert")
        vm.uiState.first { it.done }
        assertThat(phone.groups.current.value.mePlayerId).isEqualTo("robert")
        assertThat(phone.players.state.value).hasSize(5)
    }

    @Test
    fun noAddMeAddsTheNewPlayer() = runTest {
        val phone = Phone(extra = listOf(person("robert", "Robert")))
        val vm = phone.whoAreYou(this)
        vm.onNameChange("Rob")
        vm.createMe()
        vm.uiState.first { it.addCheck != null }
        vm.addAnyway()
        vm.uiState.first { it.done }
        val rob = phone.players.state.value.single { it.name == "Rob" }
        assertThat(phone.groups.current.value.mePlayerId).isEqualTo(rob.id)
        // A name like no one's goes straight in.
        val other = Phone().whoAreYou(this)
        other.onNameChange("Zed")
        other.createMe()
        assertThat(other.uiState.first { it.done }.addCheck).isNull()
    }

    @Test
    fun anArchivedPlayersNameStaysTaken() = runTest {
        val phone = Phone(extra = listOf(person("old", "Gus").copy(archived = true)))
        val vm = phone.whoAreYou(this)
        vm.onNameChange("gus")
        vm.createMe()
        assertThat(vm.uiState.first { it.nameError != null }.nameError).contains("already in the group")
    }
}
