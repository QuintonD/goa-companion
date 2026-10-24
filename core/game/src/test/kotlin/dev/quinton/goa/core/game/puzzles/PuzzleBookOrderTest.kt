package dev.quinton.goa.core.game.puzzles

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import org.junit.Test

/**
 * The book as a whole (STATUS.md): fifty puzzles in a learning order whose difficulty never drops,
 * about a third at each level, numbered in the registry's order; the cut concepts stay out.
 */
class PuzzleBookOrderTest {
    private val ids = Puzzles.all.map { it.info.id }

    @Test
    fun `the book ships fifty distinct puzzles and none of the cut ones`() {
        assertThat(ids).hasSize(50)
        assertThat(ids).containsNoDuplicates()
        assertThat(ids).containsNoneIn(Puzzles.cut.map { it.info.id })
        assertThat(ids.size + Puzzles.cut.size).isEqualTo(60)
    }

    @Test
    fun `difficulty never drops along the book and each level holds about a third`() {
        val levels = Puzzles.all.map { it.info.difficulty }
        assertWithMessage("difficulties in book order").that(levels).isInOrder()
        for (d in 1..3) assertWithMessage("puzzles at difficulty $d").that(levels.count { it == d }).isIn(14..19)
    }

    @Test
    fun `the book numbers the playable puzzles in registry order`() {
        val book = EnginePuzzleBook(TestData.engine)
        val playable = Puzzles.all.filter { p -> p.info.heroes.all { it in TestData.engine.playableHeroes } }
        assertThat(book.puzzles.map { it.id }).isEqualTo(playable.map { it.info.id })
        assertThat(book.puzzles.map { it.number }).isEqualTo((1..playable.size).toList())
    }
}
