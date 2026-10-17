package dev.quinton.goa.core.domain.teams

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftJson
import dev.quinton.goa.core.domain.draft.Seat
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.match.Team
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TeamsTest {
    // --- Seat layouts ---

    @Test
    fun `even teams get one seat per player`() {
        val seats = SeatLayouts.build(listOf("a", "b"), listOf("c", "d"), OddSeatMode.HANDICAP).getOrThrow()
        assertThat(seats.map { it.pilotId }).containsExactly("a", "b", "c", "d").inOrder()
        assertThat(seats.none { it.handicap }).isTrue()
    }

    @Test
    fun `two players control two heroes each`() {
        val seats = SeatLayouts.build(listOf("a"), listOf("b"), OddSeatMode.HANDICAP).getOrThrow()
        assertThat(seats).containsExactly(
            Seat(Team.RED, 0, "a"),
            Seat(Team.RED, 1, "a"),
            Seat(Team.BLUE, 0, "b"),
            Seat(Team.BLUE, 1, "b"),
        )
    }

    @Test
    fun `three players use the official double pilot plus one Handicap card`() {
        for (mode in OddSeatMode.entries) {
            val seats = SeatLayouts.build(listOf("a", "b"), listOf("c"), mode).getOrThrow()
            assertThat(seats).containsExactly(
                Seat(Team.RED, 0, "a"),
                Seat(Team.RED, 1, "b"),
                Seat(Team.BLUE, 0, "c"),
                Seat(Team.BLUE, 1, "c", handicap = true),
            )
        }
        assertThat(SeatLayouts.offersOddChoice(3)).isFalse()
        assertThat(SeatLayouts.offersOddChoice(5)).isTrue()
    }

    @Test
    fun `five players with Handicap give the larger team Handicap cards`() {
        val seats = SeatLayouts.build(listOf("a", "b"), listOf("c", "d", "e"), OddSeatMode.HANDICAP).getOrThrow()
        assertThat(seats.filter { it.team == Team.BLUE }.all { it.handicap }).isTrue()
        assertThat(seats.filter { it.team == Team.RED }.none { it.handicap }).isTrue()
        assertThat(seats).hasSize(5)
    }

    @Test
    fun `five players with a double pilot give the smaller team an extra seat`() {
        val seats = SeatLayouts.build(
            listOf("a", "b"),
            listOf("c", "d", "e"),
            OddSeatMode.DOUBLE_PILOT,
            "b",
        ).getOrThrow()
        assertThat(seats.filter { it.team == Team.RED }.map { it.pilotId }).containsExactly("a", "b", "b").inOrder()
        assertThat(seats.none { it.handicap }).isTrue()
    }

    @Test
    fun `teams more than one apart are invalid`() {
        assertThat(SeatLayouts.build(listOf("a", "b", "c"), listOf("d"), OddSeatMode.HANDICAP).isFailure).isTrue()
    }

    // --- Balancer ---

    @Test
    fun `balancer finds the optimal split by brute force comparison`() = runBlocking<Unit> {
        checkAll(60, Arb.list(Arb.int(800..1800).map { it.toDouble() }, 2..10), Arb.long()) { values, seed ->
            val players = values.indices.map { "p$it" }
            val ratings = players.zip(values).toMap()
            val split = TeamBalancer.balance(players, ratings, seed = seed)!!
            assertThat(abs(split.red.size - split.blue.size)).isAtMost(1)
            assertThat(split.red + split.blue).containsExactlyElementsIn(players)
            assertThat(split.difference).isWithin(1e-6).of(bruteForceBest(values))
        }
    }

    @Test
    fun `balancer respects locked players and is deterministic by seed`() = runBlocking<Unit> {
        checkAll(40, Arb.int(4..10), Arb.long()) { n, seed ->
            val players = (0 until n).map { "p$it" }
            val ratings = players.associateWith { 1000.0 + it.removePrefix("p").toInt() * 37 % 400 }
            val locked = mapOf("p0" to Team.BLUE, "p1" to Team.RED)
            val split = TeamBalancer.balance(players, ratings, locked, seed)!!
            assertThat(split.blue).contains("p0")
            assertThat(split.red).contains("p1")
            assertThat(TeamBalancer.balance(players, ratings, locked, seed)).isEqualTo(split)
        }
    }

    @Test
    fun `unrated players count as 1200`() {
        val split = TeamBalancer.balance(listOf("a", "b"), emptyMap())!!
        assertThat(split.difference).isEqualTo(0.0)
    }

    private fun bruteForceBest(values: List<Double>): Double {
        val n = values.size
        var best = Double.MAX_VALUE
        for (mask in 0 until (1 shl n)) {
            val k = mask.countOneBits()
            if (k != n / 2 && k != (n + 1) / 2) continue
            val red = values.filterIndexed { i, _ -> mask shr i and 1 == 1 }
            val blue = values.filterIndexed { i, _ -> mask shr i and 1 == 0 }
            best = minOf(best, abs(red.average() - blue.average()))
        }
        return best
    }

    // --- Player Draft ---

    @Test
    fun `team A's captain drafts first and captains alternate`() {
        val players = listOf("a", "b", "c", "d", "e", "f")
        var s = PlayerDraft.start(players, redCaptain = "a", blueCaptain = "b", teamA = Team.BLUE).getOrThrow()
        assertThat(s.nextTeam).isEqualTo(Team.BLUE)
        s = PlayerDraft.pick(s, "c").getOrThrow()
        assertThat(s.nextTeam).isEqualTo(Team.RED)
        assertThat(PlayerDraft.pick(s, "c").exceptionOrNull()).isInstanceOf(DraftError.NotInPool::class.java)
        s = PlayerDraft.pick(s, "d").getOrThrow()
        s = PlayerDraft.pick(s, "e").getOrThrow()
        s = PlayerDraft.pick(s, "f").getOrThrow()
        assertThat(s.isComplete).isTrue()
        assertThat(s.blue).containsExactly("b", "c", "e").inOrder()
        assertThat(s.red).containsExactly("a", "d", "f").inOrder()
        val undone = PlayerDraft.undo(s, players).getOrThrow()
        assertThat(undone.remaining).containsExactly("f")
    }

    @Test
    fun `the forced last pick is made automatically and undone with the pick that forced it`() {
        val players = listOf("a", "b", "c", "d", "e")
        var s = PlayerDraft.start(players, redCaptain = "a", blueCaptain = "b", teamA = Team.RED).getOrThrow()
        s = PlayerDraft.pickForcingLast(s, "c").getOrThrow()
        assertThat(s.remaining).containsExactly("d", "e")
        s = PlayerDraft.pickForcingLast(s, "e").getOrThrow()
        // Only "d" was left, and alternation gives Red (Team A) the next pick: no choice to make.
        assertThat(s.isComplete).isTrue()
        assertThat(s.red).containsExactly("a", "c", "d").inOrder()
        assertThat(s.blue).containsExactly("b", "e").inOrder()
        assertThat(s.picks.last().auto).isTrue()
        val undone = PlayerDraft.undo(s, players).getOrThrow()
        assertThat(undone.remaining).containsExactly("d", "e").inOrder()
        assertThat(undone.nextTeam).isEqualTo(Team.BLUE)
    }

    @Test
    fun `player draft needs four players and distinct captains`() {
        assertThat(PlayerDraft.start(listOf("a", "b", "c"), "a", "b", Team.RED).isFailure).isTrue()
        assertThat(PlayerDraft.start(listOf("a", "b", "c", "d"), "a", "a", Team.RED).isFailure).isTrue()
    }

    @Test
    fun `captains default to the most experienced players`() {
        val captains = PlayerDraft.suggestCaptains(
            listOf("a", "b", "c", "d"),
            games = mapOf("b" to 12, "c" to 30, "d" to 12),
            ratings = mapOf("d" to 1300.0, "b" to 1250.0),
        )
        assertThat(captains).containsExactly("c", "d").inOrder()
    }

    @Test
    fun `official player draft picks heroes first then captains draft players`() {
        val methods = DraftJson.decodeMethods(bundledMethods())
        val allPick = methods.methods.first { it.id == "all-pick" }
        val players = listOf("a", "b", "c", "d", "e")
        val heroes = (1..20).map { "h$it" }
        var s = PlayerDraftSession.start("d1", 0, allPick, players, "a", "b", Team.RED, heroes, seed = 1).getOrThrow()
        assertThat(s.phase).isEqualTo(PlayerDraftPhase.HEROES)
        assertThat(s.toDraftSession().isFailure).isTrue()
        players.forEachIndexed { i, p ->
            s = s.copy(selection = HeroSelections.choose(s.selection, p, heroes[i]).getOrThrow())
        }
        assertThat(HeroSelections.choose(s.selection, "a", "h2").exceptionOrNull()).isEqualTo(DraftError.Taken("h2"))
        assertThat(s.phase).isEqualTo(PlayerDraftPhase.CAPTAINS)
        for (p in listOf("c", "d", "e")) s = s.copy(captains = PlayerDraft.pick(s.captains, p).getOrThrow())
        assertThat(s.phase).isEqualTo(PlayerDraftPhase.DONE)

        val session = s.toDraftSession().getOrThrow()
        assertThat(session.isComplete).isTrue()
        assertThat(session.seats).hasSize(5)
        // Red (Team A) drafted c and e: 3 v 2, so Red's heroes carry Handicap cards.
        assertThat(session.seatsOf(Team.RED).all { it.handicap }).isTrue()
        val match = DraftEngine.toPendingMatch(session, "m", 0).getOrThrow()
        assertThat(match.participants.associate { it.playerId to it.heroId })
            .containsExactly("a", "h1", "b", "h2", "c", "h3", "d", "h4", "e", "h5")
    }

    @Test
    fun `hero selection supports methods without bans and deals hands per player`() {
        val methods = DraftJson.decodeMethods(bundledMethods()).methods
        val supported = methods.filter { HeroSelections.supports(it) }.map { it.id }
        assertThat(supported).containsExactly("all-random", "all-pick", "single-draft", "random-draft")
        val single = methods.first { it.id == "single-draft" }
        val sel = HeroSelections.start(single, listOf("a", "b"), (1..10).map { "h$it" }, 3).getOrThrow()
        assertThat(sel.hands.getValue("a")).hasSize(3)
        assertThat(HeroSelections.choose(sel, "a", sel.hands.getValue("b").first()).isFailure).isTrue()
        val random = methods.first { it.id == "all-random" }
        assertThat(
            HeroSelections.start(random, listOf("a", "b"), listOf("h1", "h2"), 3).getOrThrow().isComplete,
        ).isTrue()
    }

    // --- Complexity ---

    @Test
    fun `complexity filter removes heroes above the cap and keeps unknowns`() {
        fun hero(
            id: String,
            c: Int?,
        ) = Hero(id, id, "", "p", complexity = c, printing = 2, cards = emptyList(), verified = false)
        val heroes = listOf(hero("a", 1), hero("b", 2), hero("c", 4), hero("d", null))
        assertThat(ComplexityFilter.filter(heroes, ComplexityFilter.capFor(listOf(3, 2, 4))).map { it.id })
            .containsExactly("a", "b", "d")
        assertThat(ComplexityFilter.filter(heroes, null)).hasSize(4)
        assertThat(ComplexityFilter.name(1)).isEqualTo("Challenging")
        assertThat(ComplexityFilter.name(4)).isEqualTo("Wizard")
    }

    private fun bundledMethods(): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/assets/catalog/draft-methods.json")
            if (f.exists()) return f.readText()
            dir = dir.parentFile
        }
        error("draft-methods.json not found")
    }
}
