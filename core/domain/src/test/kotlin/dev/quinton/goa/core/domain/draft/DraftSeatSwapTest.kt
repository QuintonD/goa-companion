package dev.quinton.goa.core.domain.draft

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.match.Team
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Rearranging heroes between seats after the draft never duplicates or loses a hero. */
class DraftSeatSwapTest {
    /** A 3 v 3 learning-game style lineup (synthetic ids) where "r3" is missing → one Red seat is open. */
    private val lineup = DraftMethod(
        id = "learn",
        name = "Learn",
        kind = DraftMethodKind.CUSTOM,
        origin = MethodOrigin.RULEBOOK,
        pool = PoolRule.All,
        sequence = SequenceRule.Lineup(
            listOf(TeamLineup(listOf("r1", "r2", "r3"), listOf("b1", "b2", "b3"))),
        ),
    )

    private fun learningGame(): DraftSession = DraftEngine.start(
        DraftFixtures.setup(
            lineup,
            DraftFixtures.seats(3, 3),
            teamA = Team.RED,
            candidates = listOf("r1", "r2", "b1", "b2", "b3"),
        ),
    ).getOrThrow()

    @Test
    fun `swapping a filled seat with an open seat moves the hero`() {
        val s = learningGame()
        val red = DraftEngine.assignments(s).filter { it.seat.team == Team.RED }
        // The lineup is dealt at random within the team, so find the open and a filled seat.
        val open = red.single { it.heroId == null }.seat
        val filled = red.first { it.heroId != null }
        val other = red.single { it.heroId != null && it != filled }

        val swapped = DraftEngine.swapSeats(s, filled.seat.id, open.id).getOrThrow()
        val bySeat = DraftEngine.assignments(swapped).associate { it.seat.id to it.heroId }
        assertThat(bySeat[filled.seat.id]).isNull()
        assertThat(bySeat[open.id]).isEqualTo(filled.heroId)
        assertThat(bySeat[other.seat.id]).isEqualTo(other.heroId)

        val match = DraftEngine.toPendingMatch(swapped, "m", 0).getOrThrow()
        val heroes = match.participants.mapNotNull { it.heroId }
        assertThat(heroes).containsNoDuplicates()
        assertThat(match.participants.first { it.playerId == filled.seat.pilotId }.heroId).isNull()
        assertThat(match.participants.first { it.playerId == open.pilotId }.heroId).isEqualTo(filled.heroId)

        // And back again: the open seat moves too.
        val back = DraftEngine.swapSeats(swapped, open.id, filled.seat.id).getOrThrow()
        assertThat(DraftEngine.assignments(back)).isEqualTo(DraftEngine.assignments(s))
    }

    @Test
    fun `random swap sequences keep the assigned heroes equal to the picked heroes`() = runBlocking<Unit> {
        val sessions = listOf(learningGame()) + completedHouseDrafts()
        for (start in sessions) {
            checkAll(50, Arb.long()) { seed ->
                val rng = Random(seed)
                var s = start
                repeat(rng.nextInt(1, 12)) {
                    val team = Team.entries.random(rng)
                    val seats = s.seatsOf(team)
                    val a = seats.random(rng).id
                    val b = seats.random(rng).id
                    s = DraftEngine.swapSeats(s, a, b).getOrThrow()
                    val assigned = DraftEngine.assignments(s).mapNotNull { it.heroId }
                    val label = "${start.method.id} seed=$seed"
                    assertWithMessage(label).that(assigned).containsNoDuplicates()
                    assertWithMessage(label).that(assigned).containsExactlyElementsIn(s.pickedIds)
                    // Heroes never cross teams.
                    for (assignment in DraftEngine.assignments(s)) {
                        val hero = assignment.heroId ?: continue
                        assertWithMessage(label).that(s.picks.first { it.heroId == hero }.team)
                            .isEqualTo(assignment.seat.team)
                    }
                }
                // The rearranged session survives persistence.
                val json = DraftJson.lenient.encodeToString(DraftSession.serializer(), s)
                val decoded = DraftJson.lenient.decodeFromString(DraftSession.serializer(), json)
                assertThat(DraftEngine.assignments(decoded)).isEqualTo(DraftEngine.assignments(s))
            }
        }
    }

    private fun completedHouseDrafts(): List<DraftSession> = listOf(2 to 2, 3 to 2, 3 to 3).map { (red, blue) ->
        var s = DraftEngine.start(
            DraftFixtures.setup(DraftFixtures.method("house"), DraftFixtures.seats(red, blue)),
        ).getOrThrow()
        while (!s.isComplete) s = DraftEngine.apply(s, DraftCommand(DraftEngine.available(s).first())).getOrThrow()
        s
    }
}
