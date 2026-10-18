package dev.quinton.goa.hub.web

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.hub.web.ui.primaryButton
import dev.quinton.goa.hub.web.ui.teamBadge
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.Test

/** Components that set attributes must do so before writing children (kotlinx.html streams tags). */
class ButtonAttributesTest {
    @Test
    fun attributesFromTheBlockLandOnTheButton() {
        val html = createHTML().div { primaryButton("Save") { attributes["hx-post"] = "/x" } }
        assertThat(html).contains("hx-post=\"/x\"")
        assertThat(html).contains("<span>Save</span>")
    }

    @Test
    fun aTeamBadgeWithoutItsLabelStillNamesTheTeam() {
        val html = createHTML().div { teamBadge(Team.RED, showLabel = false) }
        assertThat(html).contains("aria-label=\"Red\"")
        assertThat(html).doesNotContain(">Red<")
    }
}
