package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardSpace
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class MapPaintingTest {
    /** Zone "a" is a hexagon of radius 2 around (0,0), with "b" as a hexagon of radius 2 around (5,0). */
    private fun hexagon(cq: Int, zone: String) = (-2..2).flatMap { q ->
        (-2..2).filter { r -> kotlin.math.abs(q + r) <= 2 }.map { r -> BoardSpace(cq + q, r, zone) }
    }

    private fun map(spaces: List<BoardSpace>, terrain: List<Hex> = emptyList()) = BoardMap(
        id = "m",
        name = "M",
        lanes = emptyList(),
        zones = emptyList(),
        spaces = spaces,
        terrain = terrain,
        printing = 2,
        verified = false,
    )

    private val two = map(hexagon(0, "a") + hexagon(5, "b"))

    @Test
    fun `a donor has nothing but its own zone around it`() {
        assertThat(MapPainting.cleanDonor(two, Hex(0, 0))).isTrue()
        // The hexagon's rim touches the sea or the next Zone: its edge would come along.
        assertThat(MapPainting.cleanDonor(two, Hex(2, 0))).isFalse()
        assertThat(MapPainting.cleanDonor(two, Hex(1, 0))).isTrue()
    }

    @Test
    fun `a rock or a spawn point beside or on the space rules it out`() {
        val rocky = map(hexagon(0, "a").filterNot { it.hex == Hex(1, 0) }, terrain = listOf(Hex(1, 0)))
        assertThat(MapPainting.cleanDonor(rocky, Hex(0, 0))).isFalse()
        val spawn = hexagon(0, "a").map {
            if (it.hex == Hex(0, 0)) it.copy(spawn = SpawnPoint(Team.RED, SpawnKind.MELEE)) else it
        }
        assertThat(MapPainting.cleanDonor(map(spawn), Hex(0, 0))).isFalse()
        assertThat(MapPainting.cleanDonor(map(spawn), Hex(1, 0))).isTrue()
    }

    @Test
    fun `a patched space takes clean ground of its own zone`() {
        val patches = MapPainting.patches(two, setOf(Hex(2, 0), Hex(3, 0))) { true }
        val donors = patches.pairs.toMap()
        assertThat(patches.weak).isEmpty()
        assertThat(two.spaces.first { it.hex == donors.getValue(Hex(2, 0)) }.zone).isEqualTo("a")
        assertThat(two.spaces.first { it.hex == donors.getValue(Hex(3, 0)) }.zone).isEqualTo("b")
        donors.values.forEach { assertThat(MapPainting.cleanDonor(two, it)).isTrue() }
    }

    @Test
    fun `a zone with no clean ground borrows clean ground and is reported`() {
        val thin = map(hexagon(0, "a") + BoardSpace(4, 0, "b") + BoardSpace(5, 0, "b"))
        val patches = MapPainting.patches(thin, setOf(Hex(5, 0))) { true }
        assertThat(patches.weak).containsExactly(Hex(5, 0))
        assertThat(MapPainting.cleanDonor(thin, patches.pairs.single().second)).isTrue()
    }

    @Test
    fun `a space off the art is never a donor`() {
        val patches = MapPainting.patches(two, setOf(Hex(2, 0))) { it != Hex(0, 0) && it != Hex(1, 0) }
        assertThat(patches.pairs.single().second).isNotEqualTo(Hex(0, 0))
        assertThat(patches.pairs.single().second).isNotEqualTo(Hex(1, 0))
    }

    @Test
    fun `the image's edge is the sea whatever the art there`() {
        val w = 40
        val h = 30
        val rgb = FloatArray(w * h * 3) { 200f }
        val out = MapPainting.onSea(rgb, FloatArray(w * h) { 1f }, w, h, band = 8f)
        val sea = MapPainting.SEA.removePrefix("#").toInt(16)
        (0 until w).forEach { x ->
            assertThat(out[x]).isEqualTo(sea)
            assertThat(out[(h - 1) * w + x]).isEqualTo(sea)
        }
        (0 until h).forEach { y ->
            assertThat(out[y * w]).isEqualTo(sea)
            assertThat(out[y * w + w - 1]).isEqualTo(sea)
        }
        // Past the band, the art in the lit scale.
        assertThat(out[15 * w + 20] and 0xFF).isEqualTo(184)
    }

    @Test
    fun `no art means sea, and land stays whole at the art's edge`() {
        val w = 40
        val h = 40
        val weight = FloatArray(w * h) { i -> if (i % w < 20) 1f else 0f }
        val none = FloatArray(w * h)
        val edge = 20 * w + 19
        val deep = 20 * w + 2
        val faded = MapPainting.artOpacity(weight, none, w, h, radius = 6)
        assertThat(faded[20 * w + 30]).isEqualTo(0f)
        assertThat(faded[edge]).isLessThan(0.05f)
        assertThat(faded[deep]).isEqualTo(1f)
        val land = FloatArray(w * h) { 1f }
        assertThat(MapPainting.artOpacity(weight, land, w, h, radius = 6)[edge]).isEqualTo(1f)
    }
}
