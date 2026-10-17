package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.rules.LevelRow
import dev.quinton.goa.core.model.rules.RulesExcerpt
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.core.model.rules.RulesTopic
import org.junit.Test

class RulesCheckTest {
    private val sha = "aa".repeat(32)
    private val rulebook = PinnedDocument(
        id = "rulebook",
        name = "Rulebook",
        url = "https://example.invalid/rb.pdf",
        sha256 = sha,
        printing = 2,
        pinned = "2026-10-17",
        use = "rules",
    )

    private val levels = listOf(
        LevelRow(1, 1, 1, 1, 1, null, null),
        LevelRow(2, 1, 2, 1, 1, 1, 2),
        LevelRow(3, 1, 3, 1, 1, 2, 2),
        LevelRow(4, 2, 4, 2, 2, 3, 2),
        LevelRow(5, 2, 5, 2, 2, 4, 3),
        LevelRow(6, 2, 6, 2, 2, 5, 3),
        LevelRow(7, 3, 7, 3, 3, 6, 3),
        LevelRow(8, 3, 8, 3, 3, 7, 4),
    )

    /** PDF pages as the text layer gives them: index 0 is the cover, index N is printed page N. */
    private fun pdf(page7: String = PAGE_7): List<String> = List(24) { "" }.toMutableList().apply {
        this[7] = page7
        this[13] = "H ero  Level1 2 3 4 5 6 7 8\nLowest card Tier I I I II II II III III\n" +
            "Coins (Defeat)1 2 3 4 5 6 7 8\nAssist Coins1 1 1 2 2 2 3 3\n"
        this[15] = "Max card Tier**\nHero Level*\nLevel Up cost\n" +
            "\t- II II II III III III IV\n\t1 2 3 4 5 6 7 8\n\t- 1 2 3 4 5 6 7\n"
        this[23] = levels.joinToString("\n") { r ->
            "${r.level}\n${r.defeatCoins}\n${"I".repeat(r.lowestTier)}\n${r.lifeCounters}"
        }
    }

    private fun reference(text: String, rows: List<LevelRow> = levels) = RulesReference(
        schemaVersion = 1,
        printing = 2,
        rulebook = "rulebook",
        ladder = LevelLadder(
            pages = listOf(13, 15, 23),
            levels = rows,
            defeat = emptyList(),
            levelUp = emptyList(),
            cardText = RulesExcerpt(7, null, "Ties are broken by the Tie Breaker coin."),
        ),
        topics = listOf(RulesTopic("turn", "Turn", listOf(7), excerpts = listOf(RulesExcerpt(7, "Turn Ends", text)))),
    )

    private fun problems(
        text: String,
        pages: List<String> = pdf(),
        confirm: List<ConfirmedExcerpt> = emptyList(),
        rows: List<LevelRow> = levels,
    ) = RulesReferenceCheck(reference(text, rows), pages, rulebook, RulesConfirmations(confirm = confirm)).problems()

    @Test
    fun `verbatim excerpt matches across line breaks, hyphenation, dashes and curly quotes`() {
        val text = "If this is the end of turn 4, the round ends — follow the “End of Round” steps. " +
            "Otherwise, start another turn."
        assertThat(problems(text)).isEmpty()
    }

    @Test
    fun `a reworded excerpt fails loudly`() {
        val text = "If this is the end of turn 3, the round ends — follow the “End of Round” steps."
        assertThat(problems(text).single())
            .startsWith("turn p.7: excerpt not found on the page: \"If this is the end of turn 3")
    }

    @Test
    fun `a heading that is not on the page fails`() {
        val bad = reference(TURN).copy(
            topics = listOf(
                RulesTopic("turn", "Turn", listOf(7), excerpts = listOf(RulesExcerpt(7, "Turn Begins", TURN))),
            ),
        )
        val out = RulesReferenceCheck(bad, pdf(), rulebook, RulesConfirmations()).problems()
        assertThat(out).containsExactly("turn p.7: heading \"Turn Begins\" not on the page")
    }

    @Test
    fun `a table must appear row by row on the page and have even rows`() {
        fun withTable(table: List<List<String>>): List<String> {
            val ref = reference(TURN).copy(
                topics = listOf(
                    RulesTopic("turn", "Turn", listOf(7), excerpts = listOf(RulesExcerpt(7, null, TURN, table))),
                ),
            )
            return RulesReferenceCheck(ref, pdf(page7 = "$PAGE_7\n$TABLE_7"), rulebook, RulesConfirmations()).problems()
        }
        val header = listOf("Player Count", "Wave Counters", "Life Counters")
        assertThat(withTable(listOf(header, listOf("6-8 Players", "2 x 7", "6")))).isEmpty()
        assertThat(withTable(listOf(header, listOf("6-8 Players", "2 x 7", "7"))).single())
            .contains("table not found on the page")
        assertThat(withTable(listOf(header, listOf("6-8 Players", "2 x 7"))).first())
            .contains("different cell counts")
    }

    @Test
    fun `ladder numbers must match the three tables`() {
        assertThat(problems(TURN)).isEmpty()
        val wrongCoins = levels.map { if (it.level == 5) it.copy(defeatCoins = 6) else it }
        assertThat(problems(TURN, rows = wrongCoins)).hasSize(2) // p.13 row and p.23 row
        val wrongCost = levels.map { if (it.level == 8) it.copy(levelUpCost = 8) else it }
        assertThat(problems(TURN, rows = wrongCost).single()).contains("not on p.15")
        val wrongLife = levels.map { if (it.level == 4) it.copy(lifeCounters = 1) else it }
        assertThat(problems(TURN, rows = wrongLife)).hasSize(2)
    }

    @Test
    fun `a confirmation covers a scrambled excerpt only for the reviewed rulebook file`() {
        val scrambled = pdf(page7 = "If your hero is not on the board, you maSylcihpoposeertyo rGesrpoawunnd\n$PAGE_7")
        val text = "If your hero is not on the board, you may choose to respawn"
        assertThat(problems(text, scrambled).single()).contains("excerpt not found")
        val reviewed = ConfirmedExcerpt(7, "If your hero is not", sha, "two text frames overlap")
        assertThat(problems(text, scrambled, listOf(reviewed))).isEmpty()
        val older = reviewed.copy(rulebookSha256 = "bb".repeat(32))
        assertThat(problems(text, scrambled, listOf(older)).single()).contains("re-review")
    }

    @Test
    fun `stale and unused confirmations are reported`() {
        val stale = ConfirmedExcerpt(7, "If this is the end", sha, "was scrambled")
        assertThat(problems(TURN, confirm = listOf(stale)).single()).contains("drop its confirmation")
        val unused = ConfirmedExcerpt(9, "Nothing here", sha, "typo")
        assertThat(problems(TURN, confirm = listOf(unused)).single()).contains("Unused confirmation")
    }

    private companion object {
        const val TURN = "If this is the end of turn 4, the round ends"
        const val PAGE_7 = "Turn Ends\nIf this is the end of turn 4, the round ends -- follow the \n\"End of Round\" " +
            "steps. Other-\nwise, start another turn.\nTies are broken by the Tie Breaker coin."
        const val TABLE_7 = "Player Count Wave Counters Life Counters\n6-8 Players 2 x 7 6\n9-10 Players 2 x 7 7"
    }
}
