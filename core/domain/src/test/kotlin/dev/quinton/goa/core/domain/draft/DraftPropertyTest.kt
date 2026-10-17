package dev.quinton.goa.core.domain.draft

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.random.Random

/**
 * Every bundled method × player counts 2–10 × seat layouts (even, Handicap, double pilot,
 * 3-player official, raw uneven seats) × both coin results completes legally (PRODUCT.md §4).
 */
class DraftPropertyTest {
    private val methods = DraftFixtures.bundled.methods.filter { it.sequence !is SequenceRule.Lineup }

    /** Named seat layouts: player-count based (via SeatLayouts) plus raw seat counts. */
    private val layouts: List<Pair<String, List<Seat>>> = buildList {
        for (n in 2..10) {
            val ids = (1..n).map { "p$it" }
            val red = ids.take((n + 1) / 2)
            val blue = ids.drop((n + 1) / 2)
            for (mode in OddSeatMode.entries) {
                add("$n players $mode" to SeatLayouts.build(red, blue, mode).getOrThrow())
                add("$n players $mode swapped" to SeatLayouts.build(blue, red, mode, doublePilot = blue.lastOrNull()).getOrThrow())
            }
        }
        for (a in 1..5) {
            for (b in (a - 1)..(a + 1)) {
                if (b in 1..5) add("raw $a v $b" to DraftFixtures.seats(a, b))
            }
        }
    }

    @Test
    fun `every method completes legally with unique heroes for every layout`() = runBlocking<Unit> {
        for (method in methods) {
            for ((name, seats) in layouts) {
                for (teamA in Team.entries) {
                    checkAll(6, Arb.long()) { seed ->
                        val setup = DraftFixtures.setup(method, seats, teamA, seed = seed)
                        val start = DraftEngine.start(setup)
                        assertThat(start.exceptionOrNull()).isNull()
                        playToCompletion(start.getOrThrow(), Random(seed), "${method.id} / $name / A=$teamA")
                    }
                }
            }
        }
    }

    @Test
    fun `plans never contain more picks than seats`() {
        for (method in methods) {
            for (a in 1..5) {
                for (b in 1..5) {
                    val plan = DraftPlanner.plan(method.sequence, a, b).getOrNull() ?: continue
                    val picksA = plan.count { it.team == TeamSlot.A && it.action == DraftAction.PICK }
                    val picksB = plan.count { it.team == TeamSlot.B && it.action == DraftAction.PICK }
                    assertThat(picksA).isEqualTo(a)
                    assertThat(picksB).isEqualTo(b)
                    assertThat(plan.last().action).isEqualTo(DraftAction.PICK)
                    // Each seat index is filled exactly once, in order.
                    assertThat(plan.filter { it.team == TeamSlot.A && it.seat != null }.map { it.seat })
                        .containsExactlyElementsIn(0 until a).inOrder()
                }
            }
        }
    }

    @Test
    fun `a pool that is too small is rejected up front`() = runBlocking<Unit> {
        for (method in methods) {
            checkAll(5, Arb.long()) { seed ->
                val result = DraftEngine.start(
                    DraftFixtures.setup(method, DraftFixtures.seats(5, 5), candidates = DraftFixtures.heroes.take(5), seed = seed),
                )
                assertThat(result.exceptionOrNull()).isInstanceOf(DraftError.PoolTooSmall::class.java)
            }
        }
    }

    private fun playToCompletion(initial: DraftSession, rng: Random, label: String) {
        var session = initial
        var guard = 0
        while (!session.isComplete) {
            check(guard++ < 100) { "$label never completes" }
            val step = session.currentStep!!
            val available = DraftEngine.available(session)
            assertThat(available).isNotEmpty()

            // Illegal choices are rejected with typed errors and leave the session unchanged.
            session.bannedIds.firstOrNull()?.let {
                assertThat(DraftEngine.apply(session, DraftCommand(it)).exceptionOrNull())
                    .isInstanceOf(DraftError.AlreadyBanned::class.java)
            }
            session.pickedIds.firstOrNull()?.let {
                assertThat(DraftEngine.apply(session, DraftCommand(it)).exceptionOrNull())
                    .isInstanceOf(DraftError.AlreadyPicked::class.java)
            }
            assertThat(DraftEngine.apply(session, DraftCommand("not-a-hero")).exceptionOrNull())
                .isInstanceOf(DraftError.NotInPool::class.java)
            assertThat(DraftEngine.apply(session, DraftCommand(available.first(), team = step.team.opponent)).exceptionOrNull())
                .isInstanceOf(DraftError.WrongTeam::class.java)

            val before = session
            val next = DraftEngine.apply(session, DraftCommand(available.random(rng), step.team, step.action)).getOrThrow()
            // Undo at every step restores the previous state exactly.
            assertThat(DraftEngine.undo(next).getOrThrow()).isEqualTo(before)
            session = next
        }
        assertLegal(session, label)
    }

    private fun assertLegal(session: DraftSession, label: String) {
        val heroes = session.history.map { it.heroId }
        assertWithMessage(label).that(heroes).containsNoDuplicates()
        assertWithMessage(label).that(session.pool).containsAtLeastElementsIn(heroes)
        assertWithMessage(label).that(session.bannedIds.intersect(session.pickedIds)).isEmpty()
        assertWithMessage(label).that(session.picks.size).isEqualTo(session.seats.size)
        for (team in Team.entries) {
            assertWithMessage(label).that(session.picks.count { it.team == team }).isEqualTo(session.seatsOf(team).size)
        }
        val assignments = DraftEngine.assignments(session)
        assertWithMessage(label).that(assignments.all { it.heroId != null }).isTrue()
        for (assignment in assignments) {
            session.handOf(assignment.seat.id)?.let { hand -> assertThat(hand).contains(assignment.heroId) }
        }
        assertThat(DraftEngine.apply(session, DraftCommand(session.pool.first())).exceptionOrNull())
            .isEqualTo(DraftError.Complete)
    }
}
