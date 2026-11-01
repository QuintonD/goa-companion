package dev.quinton.goa.web.play.map

import kotlin.test.Test
import kotlin.test.assertEquals

/** A piece mustn't be re-inserted while it glides: the draw order only changes once it has settled. */
class DrawOrderTest {
    @Test
    fun aMoveKeepsTheOrderTheBoardSettledOn() {
        assertEquals(listOf("a", "b", "c"), drawOrder(settled = listOf("a", "b", "c"), wanted = listOf("c", "a", "b")))
    }

    @Test
    fun aPieceThatLeftGoesAndANewOneComesOnTop() {
        assertEquals(listOf("a", "c", "d"), drawOrder(settled = listOf("a", "b", "c"), wanted = listOf("d", "c", "a")))
    }
}
