package dev.quinton.goa.hub.web

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.hub.web.ui.primaryButton
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.Test

class ButtonAttributesTest {
    @Test
    fun attributesFromTheBlockLandOnTheButton() {
        val html = createHTML().div { primaryButton("Save") { attributes["hx-post"] = "/x" } }
        assertThat(html).contains("hx-post=\"/x\"")
        assertThat(html).contains("<span>Save</span>")
    }
}
