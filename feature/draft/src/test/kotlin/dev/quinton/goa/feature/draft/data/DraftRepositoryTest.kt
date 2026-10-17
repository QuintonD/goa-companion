package dev.quinton.goa.feature.draft.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.datastore.draft.BundledDraftMethods
import dev.quinton.goa.core.datastore.draft.DraftStore
import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.TeamLineup
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.ban
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.domain.teams.HeroSelections
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.PlayerDraft
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.bundledMethods
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The in-progress draft survives the real repository's JSON encoding (process death). */
@RunWith(RobolectricTestRunner::class)
class DraftRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val heroes = (1..20).map { "hero-%02d".format(it) }
    private val house = bundledMethods.methods.first { it.id == "house" }

    private fun TestScope.store(): DraftStore = DraftStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            folder.root.resolve("draft.preferences_pb")
        },
    )

    private fun TestScope.repository(store: DraftStore = store()): DefaultDraftRepository =
        DefaultDraftRepository(store, BundledDraftMethods(ApplicationProvider.getApplicationContext()))

    private suspend fun DraftRepository.roundTrip(progress: DraftProgress): DraftProgress? {
        saveActive(progress)
        return active.first()
    }

    private fun board(method: DraftMethod, red: Int = 2, blue: Int = 2): DraftSession {
        val seats = SeatLayouts.build(
            (0 until red).map { "r$it" },
            (0 until blue).map { "b$it" },
            OddSeatMode.HANDICAP,
        ).getOrThrow()
        return DraftEngine.start(DraftSetup("d", 1L, method, seats, Team.BLUE, heroes, 5L, 30)).getOrThrow()
    }

    @Test
    fun `an in-progress board with concealed hidden steps round-trips`() = runTest(UnconfinedTestDispatcher()) {
        val blind = house.copy(
            sequence = SequenceRule.Pattern(
                opening = listOf(ban(TeamSlot.A, hidden = true), ban(TeamSlot.B, hidden = true)),
                repeat = listOf(pick(TeamSlot.A, hidden = true), pick(TeamSlot.B, hidden = true)),
            ),
        )
        var s = board(blind)
        s = DraftEngine.apply(s, DraftCommand(s.pool[0])).getOrThrow()
        assertThat(s.isConcealed(0)).isTrue()
        val progress = DraftProgress.Board(s)

        val decoded = repository().roundTrip(progress)
        assertThat(decoded).isEqualTo(progress)
        val session = (decoded as DraftProgress.Board).session
        assertThat(session.concealedIds()).containsExactly(s.pool[0])
        assertThat(session.currentStep?.hidden).isTrue()
    }

    @Test
    fun `a rearranged board with an open seat round-trips`() = runTest(UnconfinedTestDispatcher()) {
        val lineup = house.copy(
            id = "learn",
            pool = PoolRule.All,
            sequence = SequenceRule.Lineup(listOf(TeamLineup(listOf("hero-01", "x"), listOf("hero-03", "hero-04")))),
        )
        val s = DraftEngine.swapSeats(board(lineup), "red-0", "red-1").getOrThrow()
        assertThat(s.assignments.values).contains(null) // "x" is missing, so one Red seat is open
        val decoded = repository().roundTrip(DraftProgress.Board(s)) as DraftProgress.Board
        assertThat(DraftEngine.assignments(decoded.session)).isEqualTo(DraftEngine.assignments(s))
    }

    @Test
    fun `player drafts round-trip in progress and after the hand-off`() = runTest(UnconfinedTestDispatcher()) {
        val allPick = bundledMethods.methods.first { it.id == "all-pick" }
        val players = listOf("a", "b", "c", "d")
        var pd = PlayerDraftSession.start("pd", 0, allPick, players, "a", "b", Team.RED, heroes, 1).getOrThrow()
        players.forEachIndexed { i, p ->
            pd = pd.copy(selection = HeroSelections.choose(pd.selection, p, heroes[i]).getOrThrow())
        }
        pd = pd.copy(captains = PlayerDraft.pick(pd.captains, "c").getOrThrow())
        val repo = repository()
        assertThat(repo.roundTrip(DraftProgress.Players(pd))).isEqualTo(DraftProgress.Players(pd))

        pd = pd.copy(captains = PlayerDraft.pick(pd.captains, "d").getOrThrow())
        val handOff = DraftProgress.Board(pd.toDraftSession().getOrThrow(), playerDraft = pd)
        val decoded = repo.roundTrip(handOff) as DraftProgress.Board
        assertThat(decoded).isEqualTo(handOff)
        assertThat(decoded.session.fromPlayerDraft).isTrue()
        assertThat(decoded.playerDraft).isEqualTo(pd)
    }

    @Test
    fun `boards saved before the player draft fields existed still load`() = runTest(UnconfinedTestDispatcher()) {
        val s = board(house)
        val json = DraftJson.lenient.encodeToJsonElement(DraftProgress.serializer(), DraftProgress.Board(s)).jsonObject
        val session = json.getValue("session").jsonObject
        val legacy = JsonObject(json - "playerDraft" + ("session" to JsonObject(session - "fromPlayerDraft")))
        assertThat(legacy.toString()).doesNotContain("fromPlayerDraft")
        val store = store()
        store.setActiveDraft(legacy.toString())
        assertThat(repository(store).active.first()).isEqualTo(DraftProgress.Board(s))
    }
}
