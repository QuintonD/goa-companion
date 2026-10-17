package dev.quinton.goa.core.domain.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.teams.HeroSelections
import dev.quinton.goa.core.domain.teams.PlayerDraft
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** Who may change what after (and during) a draft, and what hidden steps may reveal. */
class DraftRulesTest {
    private val house = DraftFixtures.method("house")

    private fun DraftSession.choose(heroId: String) = DraftEngine.apply(this, DraftCommand(heroId)).getOrThrow()

    private fun complete(method: DraftMethod, red: Int = 2, blue: Int = 2): DraftSession {
        var s = DraftEngine.start(DraftFixtures.setup(method, DraftFixtures.seats(red, blue))).getOrThrow()
        while (!s.isComplete) s = s.choose(DraftEngine.available(s).first())
        return s
    }

    private fun playerDraft(): DraftSession {
        val allPick = DraftFixtures.method("all-pick")
        val players = listOf("a", "b", "c", "d")
        val heroes = DraftFixtures.heroes
        var s = PlayerDraftSession.start("pd", 0, allPick, players, "a", "b", Team.RED, heroes, seed = 1)
            .getOrThrow()
        players.forEachIndexed { i, p ->
            s = s.copy(selection = HeroSelections.choose(s.selection, p, heroes[i]).getOrThrow())
        }
        for (p in listOf("c", "d")) s = s.copy(captains = PlayerDraft.pick(s.captains, p).getOrThrow())
        assertThat(s.phase).isEqualTo(PlayerDraftPhase.DONE)
        return s.toDraftSession().getOrThrow()
    }

    // --- Player Draft [RB p.22]: everyone plays their pre-selected hero ---

    @Test
    fun `player draft sessions refuse re-roll, pool swaps and seat swaps`() {
        val s = playerDraft()
        assertThat(s.fromPlayerDraft).isTrue()
        assertThat(s.canEditPool).isFalse()
        assertThat(s.canSwapSeats).isFalse()
        assertThat(DraftEngine.reroll(s, 99).exceptionOrNull()).isEqualTo(DraftError.PlayerDraftFixed)
        assertThat(DraftEngine.swapPoolHero(s, s.pool.first(), "hero-99").exceptionOrNull())
            .isEqualTo(DraftError.PlayerDraftFixed)
        assertThat(DraftEngine.swapSeats(s, "red-0", "red-1").exceptionOrNull()).isEqualTo(DraftError.PlayerDraftFixed)
        // Seat details (pilots are fixed by the captains, Handicap cards) may still be adjusted.
        assertThat(DraftEngine.setHandicap(s, "red-0", true).isSuccess).isTrue()
    }

    @Test
    fun `the player draft origin survives persistence and old sessions decode as board drafts`() {
        val s = playerDraft()
        val json = DraftJson.lenient.encodeToString(DraftSession.serializer(), s)
        assertThat(DraftJson.lenient.decodeFromString(DraftSession.serializer(), json)).isEqualTo(s)

        // A session saved before the flag existed has no "fromPlayerDraft" key.
        val board = complete(house)
        val element = DraftJson.lenient.encodeToJsonElement(DraftSession.serializer(), board).jsonObject
        val legacy = JsonObject(element - "fromPlayerDraft")
        val decoded = DraftJson.lenient.decodeFromJsonElement(DraftSession.serializer(), legacy)
        assertThat(decoded.fromPlayerDraft).isFalse()
        assertThat(decoded).isEqualTo(board)
    }

    // --- Seat swaps: team-pick methods only ---

    @Test
    fun `single draft heroes stay with the seat whose hand they came from`() {
        val s = complete(DraftFixtures.method("single-draft"))
        assertThat(s.canSwapSeats).isFalse()
        assertThat(DraftEngine.swapSeats(s, "red-0", "red-1").exceptionOrNull()).isEqualTo(DraftError.HandsFixed)
    }

    @Test
    fun `team pick methods keep within-team seat swaps`() {
        for (id in listOf("house", "pick-and-ban", "all-pick", "random-draft", "all-random")) {
            val s = complete(DraftFixtures.method(id))
            assertThat(s.canSwapSeats).isTrue()
            assertThat(DraftEngine.swapSeats(s, "red-0", "red-1").isSuccess).isTrue()
        }
    }

    // --- Hidden steps never leak what the other team chose ---

    @Test
    fun `choosing a hero the other team concealed gives a neutral error`() {
        val blindBans = house.copy(
            sequence = SequenceRule.Pattern(
                opening = listOf(ban(TeamSlot.A, hidden = true), ban(TeamSlot.B, hidden = true)),
                repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.B)),
            ),
        )
        var s = DraftEngine.start(DraftFixtures.setup(blindBans, teamA = Team.RED)).getOrThrow()
        val secret = s.pool.first()
        s = s.choose(secret) // Red's hidden ban
        assertThat(s.concealedIds()).containsExactly(secret)

        val error = DraftEngine.apply(s, DraftCommand(secret)).exceptionOrNull()
        assertThat(error).isEqualTo(DraftError.Unavailable)
        assertThat(error!!.message).doesNotContain(secret)
        assertThat(error.message).doesNotContain("ban")

        // Once the hidden run is over the ban is public and reported normally.
        s = s.choose(s.pool[1])
        assertThat(s.concealedIds()).isEmpty()
        assertThat(
            DraftEngine.apply(s, DraftCommand(secret)).exceptionOrNull(),
        ).isEqualTo(DraftError.AlreadyBanned(secret))
    }

    @Test
    fun `a hidden pick is not revealed by a clashing hidden pick`() {
        val blindPicks = house.copy(
            sequence = SequenceRule.Pattern(repeat = listOf(pick(TeamSlot.A, true), pick(TeamSlot.B, true))),
        )
        var s = DraftEngine.start(DraftFixtures.setup(blindPicks, teamA = Team.BLUE)).getOrThrow()
        val secret = s.pool.first()
        s = s.choose(secret)
        assertThat(DraftEngine.apply(s, DraftCommand(secret)).exceptionOrNull()).isEqualTo(DraftError.Unavailable)
    }

    // --- Dealt hands + bans would let a ban empty a hand ---

    @Test
    fun `dealt hands cannot be combined with bans`() {
        val dealtBans = house.copy(pool = PoolRule.DealPerSeat(3))
        assertThat(DraftPlanner.problem(dealtBans)).isInstanceOf(DraftError.NotSupported::class.java)
        assertThat(
            DraftPlanner.plan(dealtBans, 2, 2).exceptionOrNull(),
        ).isInstanceOf(DraftError.NotSupported::class.java)
        assertThat(DraftEngine.start(DraftFixtures.setup(dealtBans)).exceptionOrNull())
            .isInstanceOf(DraftError.NotSupported::class.java)
        val dealtPickAndBan = DraftFixtures.method("pick-and-ban").copy(pool = PoolRule.DealPerSeat(3))
        assertThat(DraftPlanner.problem(dealtPickAndBan)).isInstanceOf(DraftError.NotSupported::class.java)
        // Without bans dealing is fine (Single Draft).
        assertThat(DraftPlanner.problem(DraftFixtures.method("single-draft"))).isNull()
        assertThat(DraftPlanner.plan(DraftFixtures.method("single-draft"), 2, 2).isSuccess).isTrue()
    }

    // --- Commands are tied to the step they were made for ---

    @Test
    fun `a command made for an earlier step is rejected`() {
        var s = DraftEngine.start(DraftFixtures.setup(house, teamA = Team.RED)).getOrThrow()
        val forStepZero = DraftCommand(s.pool[1], step = 0)
        s = s.choose(s.pool[0])
        assertThat(DraftEngine.apply(s, forStepZero).exceptionOrNull()).isEqualTo(DraftError.StaleStep(0, 1))
        assertThat(DraftEngine.apply(s, forStepZero.copy(step = 1)).isSuccess).isTrue()
    }
}
