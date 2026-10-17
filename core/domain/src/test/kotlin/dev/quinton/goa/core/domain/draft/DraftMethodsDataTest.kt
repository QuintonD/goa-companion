package dev.quinton.goa.core.domain.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.DraftFixtures.notation
import dev.quinton.goa.core.model.draft.DraftMethodKind
import org.junit.Test

/** Guards the shipped `draft-methods.json` against the sequences written in docs/DOMAIN.md. */
class DraftMethodsDataTest {
    private val file = DraftFixtures.bundled

    private fun planOf(id: String, a: Int, b: Int) =
        DraftPlanner.plan(DraftFixtures.method(id).sequence, a, b).getOrThrow().notation()

    @Test
    fun `file decodes strictly with unique ids and a valid default`() {
        assertThat(file.methods.map { it.id }).containsNoDuplicates()
        assertThat(file.methods.map { it.id }).contains(file.defaultMethodId)
        assertThat(file.defaultMethodId).isEqualTo("house")
        assertThat(file.methods.all { it.isBuiltIn }).isTrue()
    }

    @Test
    fun `every official method kind is bundled`() {
        val kinds = file.methods.map { it.kind }.toSet()
        assertThat(kinds).containsAtLeast(
            DraftMethodKind.ALL_RANDOM,
            DraftMethodKind.ALL_PICK,
            DraftMethodKind.SINGLE_DRAFT,
            DraftMethodKind.RANDOM_DRAFT,
            DraftMethodKind.PICK_AND_BAN,
        )
    }

    @Test
    fun `house draft uses a pool of 15`() {
        assertThat(DraftFixtures.method("house").pool).isEqualTo(PoolRule.Random(15))
    }

    @Test
    fun `house draft 2v2 matches the owner's sequence`() {
        assertThat(planOf("house", 2, 2)).isEqualTo("Ban A · Pick A · Ban B · Pick B · Pick B · Pick A")
    }

    @Test
    fun `house draft 3v3 ends Pick A Pick A Pick B`() {
        assertThat(planOf("house", 3, 3))
            .isEqualTo("Ban A · Pick A · Ban B · Pick B · Pick B · Pick A · Pick A · Pick B")
    }

    @Test
    fun `house draft alternates pairs and ends with a single pick`() {
        assertThat(planOf("house", 4, 4)).isEqualTo(
            "Ban A · Pick A · Ban B · Pick B · Pick B · Pick A · Pick A · Pick B · Pick B · Pick A",
        )
        assertThat(planOf("house", 5, 5)).isEqualTo(
            "Ban A · Pick A · Ban B · Pick B · Pick B · Pick A · Pick A · Pick B · Pick B · Pick A · Pick A · Pick B",
        )
    }

    @Test
    fun `pick and ban sequences match the official rulebook table`() {
        val four = "Ban A · Ban B · Pick A · Pick B · Ban B · Ban A · Pick B · Pick A"
        // Official PDF p.22: the 6-player row ends Pick B, A (RulesPal shows A, B).
        val six = "$four · Ban A · Ban B · Pick B · Pick A"
        val eight = "$six · Ban B · Ban A · Pick A · Pick B"
        val ten = "$eight · Ban B · Ban A · Pick B · Pick A"
        assertThat(planOf("pick-and-ban", 2, 2)).isEqualTo(four)
        assertThat(planOf("pick-and-ban", 3, 3)).isEqualTo(six)
        assertThat(planOf("pick-and-ban", 4, 4)).isEqualTo(eight)
        assertThat(planOf("pick-and-ban", 5, 5)).isEqualTo(ten)
    }

    @Test
    fun `pick and ban odd counts use the next even sequence and skip the smaller team's surplus pick`() {
        // 3 v 2 (Team B smaller): the 6-player sequence without B's third pick.
        assertThat(planOf("pick-and-ban", 3, 2)).isEqualTo(
            "Ban A · Ban B · Pick A · Pick B · Ban B · Ban A · Pick B · Pick A · Ban A · Ban B · Pick A",
        )
        // 2 v 3 (Team A smaller): A's third pick is skipped, B still picks.
        assertThat(planOf("pick-and-ban", 2, 3)).isEqualTo(
            "Ban A · Ban B · Pick A · Pick B · Ban B · Ban A · Pick B · Pick A · Ban A · Ban B · Pick B",
        )
    }

    @Test
    fun `learning game lineups follow the rulebook first game`() {
        val rule = DraftFixtures.method("learning-game").sequence as SequenceRule.Lineup
        assertThat(rule.forSeats(2, 2)).isEqualTo(TeamLineup(listOf("wasp", "xargatha"), listOf("arien", "brogan")))
        assertThat(rule.forSeats(3, 3))
            .isEqualTo(TeamLineup(listOf("wasp", "xargatha", "tigerclaw"), listOf("arien", "brogan", "sabina")))
        assertThat(DraftPlanner.plan(rule, 4, 4).exceptionOrNull()).isInstanceOf(DraftError.NotSupported::class.java)
    }

    @Test
    fun `alternating methods start with Team A`() {
        for (id in listOf("all-pick", "single-draft", "random-draft")) {
            assertThat(planOf(id, 2, 2)).isEqualTo("Pick A · Pick B · Pick A · Pick B")
        }
    }

    @Test
    fun `random draft reveals seats plus two and single draft deals three per seat`() {
        assertThat(DraftFixtures.method("random-draft").pool).isEqualTo(PoolRule.SeatsPlus(2))
        assertThat(DraftFixtures.method("single-draft").pool).isEqualTo(PoolRule.DealPerSeat(3))
        assertThat(DraftFixtures.method("all-random").sequence).isEqualTo(SequenceRule.RandomAssign)
    }

    @Test
    fun `methods round-trip through the lenient codec`() {
        for (method in file.methods) {
            val json = DraftJson.lenient.encodeToString(DraftMethod.serializer(), method)
            assertThat(DraftJson.lenient.decodeFromString(DraftMethod.serializer(), json)).isEqualTo(method)
        }
    }
}
