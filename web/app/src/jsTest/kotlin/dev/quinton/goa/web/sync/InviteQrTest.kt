package dev.quinton.goa.web.sync

import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.web.ui.escapeMarkup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The invite QR code is markup set with innerHTML: the group's name in it must stay text (web review R2). */
class InviteQrTest {
    private val invite = "goa-sync:1?g=6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d&k=" + "A".repeat(43)

    @Test
    fun aGroupNameCannotAddAttributesOrElements() {
        // A crafted invite can name its group anything up to 40 characters, quotes included.
        val name = GroupInvite.parseInvite("$invite&n=x%22%20onload%3D%22alert(1)%22%20%3Cb%3E").getOrThrow().group.name
        assertEquals("x\" onload=\"alert(1)\" <b>", name)
        val svg = InviteQr.svg(GroupInvite.link(invite), "Invite QR code for $name")
        assertFalse("onload=\"" in svg, svg.take(300))
        assertFalse("<b>" in svg)
        assertTrue(
            "aria-label=\"Invite QR code for x&quot; onload=&quot;alert(1)&quot; &lt;b&gt;\"" in svg,
            svg.take(300),
        )
        // One element with attributes: the svg's own, nothing smuggled in.
        val openTag = svg.substringBefore('>')
        assertEquals(listOf("xmlns", "viewBox", "shape-rendering", "role", "aria-label"), attributeNames(openTag))
    }

    @Test
    fun markupIsEscapedForTextAndBothQuotes() {
        assertEquals("&lt;a href=&quot;x&quot; title=&#39;y&#39;&gt;&amp;", escapeMarkup("<a href=\"x\" title='y'>&"))
        assertEquals("Ann & co".replace("&", "&amp;"), escapeMarkup("Ann & co"))
    }

    private fun attributeNames(tag: String): List<String> =
        Regex("""\s([A-Za-z:-]+)="[^"]*"""").findAll(tag).map { it.groupValues[1] }.toList()
}
