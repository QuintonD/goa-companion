package dev.quinton.goa.feature.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerMatchUpRepository
import dev.quinton.goa.core.testing.FakePlayerMergeRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.players.Fixtures.ann
import dev.quinton.goa.feature.players.players.PlayersUiState
import dev.quinton.goa.feature.players.players.PlayersViewModel
import dev.quinton.goa.feature.players.samepeople.SamePeopleOutcome
import dev.quinton.goa.feature.players.samepeople.SamePeopleViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * "Same people?" (ADR-031): the owner entered "Anna", then Ann joined from her own phone as "Ann".
 * The Players tab says so, "Same person" merges the newer into the older, "Different people" is
 * remembered on this phone, and a merge the log forbids is explained.
 */
class SamePeopleTest {
    @get:Rule val main = MainDispatcherRule()

    /** Added later, on another phone, never in a match. */
    private val anna = Player("p-anna", "Anna", 0xFF62A8F2, avatarId = 2, createdAt = 50)
    private val players = FakePlayerRepository(Fixtures.players + anna)
    private val matches = FakeMatchRepository(Fixtures.matches)
    private val merges = FakePlayerMergeRepository(matches, players.merges)
    private val matchUp = FakePlayerMatchUpRepository()

    private fun vm(mergeRepo: PlayerMergeRepository = merges) = SamePeopleViewModel(
        players,
        matches,
        mergeRepo,
        matchUp,
    )

    @Test
    fun thePlayersTabCountsLookAlikesFromTheCurrentData() = runTest {
        val ratings = FakeRatingsSnapshotRepository(matches, FakeDraftRecordRepository(), FakeEloConfigRepository())
        val tab = PlayersViewModel(players, ratings, FakeGroupRepository(), matches, merges, matchUp)
        assertThat(tab.uiState.first { !it.loading }.samePeople).isEqualTo(1)
        // A sync brings another look-alike: the count follows, no screen pops up.
        players.upsert(Player("p-bobby", "Bobb", 0xFF62A8F2, avatarId = 1, createdAt = 60))
        assertThat(tab.uiState.first { it.samePeople == 2 }.canFindDuplicates).isTrue()
    }

    @Test
    fun samePersonMergesTheNewerIntoTheOlder() = runTest {
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        val pair = vm.uiState.first { !it.loading }.pairs.single()
        assertThat(pair.keep.player).isEqualTo(ann)
        assertThat(pair.keep.games).isEqualTo(Fixtures.matches.count { ann.id in it.playerIds })
        val annsLast = Fixtures.matches.filter { ann.id in it.playerIds }.maxOf { it.playedAt }
        assertThat(pair.keep.lastPlayed).isEqualTo(annsLast)
        assertThat(pair.other.player).isEqualTo(anna)
        assertThat(pair.other.games).isEqualTo(0)
        assertThat(SamePeopleViewModel.summary(pair.other)).isEqualTo("No matches yet")

        vm.samePerson(pair.key)
        val done = vm.uiState.first { it.outcome != null }
        assertThat(done.outcome).isEqualTo(SamePeopleOutcome.Merged("Anna", "Ann"))
        assertThat(done.pairs).isEmpty()
        assertThat(PlayerMerges(merges.merges.value).resolve(anna.id)).isEqualTo(ann.id)
        assertThat(players.observePlayers().first().map { it.id }).doesNotContain(anna.id)
        vm.outcomeShown()
        assertThat(vm.uiState.first { it.outcome == null }.pairs).isEmpty()
    }

    @Test
    fun differentPeopleStayDifferentUntilAskedAgain() = runTest {
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        val pair = vm.uiState.first { !it.loading }.pairs.single()
        vm.differentPeople(pair.key)
        val after = vm.uiState.first { it.pairs.isEmpty() }
        assertThat(after.differentCount).isEqualTo(1)
        assertThat(matchUp.state.value).containsExactly(pair.key)
        assertThat(merges.merges.value).isEmpty()
        // A new screen (or a restart) still knows.
        assertThat(vm().uiState.first { !it.loading }.pairs).isEmpty()

        vm.askAgain()
        assertThat(vm.uiState.first { it.pairs.isNotEmpty() }.differentCount).isEqualTo(0)
    }

    @Test
    fun aRefusedMergeListsTheMatchesTheyPlayedTogether() = runTest {
        // The log changed on another phone between the list and the tap: they played together after all.
        val together = Fixtures.match("x1", 20, Team.RED, Fixtures.red(ann, 1), Fixtures.blue(anna, 2))
        val refusing = object : PlayerMergeRepository {
            override fun observeMerges(): Flow<PlayerMerges> = flowOf(PlayerMerges.NONE)

            override suspend fun merge(fromId: String, toId: String) =
                PlayerMergeResult.PlayedTogether(listOf(together))
        }
        val vm = vm(refusing)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.samePerson(vm.uiState.first { !it.loading }.pairs.single().key)
        val refused = vm.uiState.first { it.outcome != null }.outcome as SamePeopleOutcome.PlayedTogether
        assertThat(refused.matches.single()).endsWith("Ann vs Anna")
    }

    @Test
    fun playersWhoPlayedTogetherAreNeverOffered() = runTest {
        matches.upsert(Fixtures.match("x1", 20, Team.RED, Fixtures.red(ann, 1), Fixtures.blue(anna, 2)))
        assertThat(vm().uiState.first { !it.loading }.pairs).isEmpty()
        // With one player there is nothing to look for.
        assertThat(PlayersUiState(loading = false).canFindDuplicates).isFalse()
    }
}
