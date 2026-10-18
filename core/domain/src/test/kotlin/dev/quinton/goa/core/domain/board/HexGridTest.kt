package dev.quinton.goa.core.domain.board

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class HexGridTest {
    private val o = Hex(0, 0)

    @Test
    fun `distance counts spaces, adjacent is 1 and the same space 0`() {
        assertThat(HexGrid.distance(o, o)).isEqualTo(0)
        HexGrid.DIRECTIONS.forEach { assertThat(HexGrid.distance(o, it)).isEqualTo(1) }
        assertThat(HexGrid.distance(o, Hex(3, 0))).isEqualTo(3)
        assertThat(HexGrid.distance(o, Hex(2, 1))).isEqualTo(3)
        assertThat(HexGrid.distance(o, Hex(2, -1))).isEqualTo(2)
        assertThat(HexGrid.distance(Hex(-2, 5), Hex(3, -1))).isEqualTo(HexGrid.distance(Hex(3, -1), Hex(-2, 5)))
    }

    @Test
    fun `distance obeys the triangle inequality over a patch of the grid`() {
        val patch = HexGrid.within(o, 3) + o
        for (a in patch) {
            for (b in patch) {
                for (c in patch.take(10)) {
                    assertThat(HexGrid.distance(a, b)).isAtMost(HexGrid.distance(a, c) + HexGrid.distance(c, b))
                }
            }
        }
    }

    @Test
    fun `neighbors are the six adjacent spaces`() {
        val n = HexGrid.neighbors(Hex(2, -1))
        assertThat(n).hasSize(6)
        assertThat(n.toSet()).hasSize(6)
        n.forEach { assertThat(HexGrid.adjacent(Hex(2, -1), it)).isTrue() }
        assertThat(HexGrid.adjacent(o, Hex(2, 0))).isFalse()
    }

    @Test
    fun `within N covers 1 to N spaces away, never the centre`() {
        assertThat(HexGrid.within(o, 0)).isEmpty()
        assertThat(HexGrid.within(o, 1)).containsExactlyElementsIn(HexGrid.DIRECTIONS)
        assertThat(HexGrid.within(o, 2)).hasSize(18)
        assertThat(HexGrid.within(o, 3)).hasSize(36)
        assertThat(HexGrid.within(o, 3)).doesNotContain(o)
        assertThat(HexGrid.within(Hex(4, -2), 2).all { HexGrid.distance(Hex(4, -2), it) in 1..2 }).isTrue()
    }

    @Test
    fun `straight lines run along the six directions only`() {
        assertThat(HexGrid.straightLineDirection(o, Hex(3, 0))).isEqualTo(0)
        assertThat(HexGrid.straightLineDirection(o, Hex(0, -2))).isEqualTo(2)
        assertThat(HexGrid.straightLineDirection(o, Hex(-2, 2))).isEqualTo(4)
        assertThat(HexGrid.straightLineDirection(o, Hex(2, 1))).isNull()
        assertThat(HexGrid.straightLineDirection(o, o)).isNull()
        assertThat(HexGrid.straightLine(o, Hex(0, 3))).containsExactly(Hex(0, 1), Hex(0, 2), Hex(0, 3)).inOrder()
        assertThat(HexGrid.straightLine(o, Hex(1, 1))).isNull()
    }

    @Test
    fun `p19 adjacent units and a single space are in a straight line`() {
        HexGrid.DIRECTIONS.forEach { assertThat(HexGrid.inStraightLine(o, it)).isTrue() }
        assertThat(HexGrid.inStraightLine(o, o)).isTrue()
        assertThat(HexGrid.inStraightLine(o, Hex(-3, 3))).isTrue()
        assertThat(HexGrid.inStraightLine(o, Hex(1, 1))).isFalse()
    }
}
