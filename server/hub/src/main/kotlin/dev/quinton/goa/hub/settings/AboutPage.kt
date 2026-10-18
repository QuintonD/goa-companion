package dev.quinton.goa.hub.settings

import dev.quinton.goa.core.presentation.settings.AboutCredits
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.catalog.HubCatalog
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.web.ui.StaticFiles
import dev.quinton.goa.hub.web.ui.card
import dev.quinton.goa.hub.web.ui.respondPage
import dev.quinton.goa.hub.web.ui.sectionHeader
import io.ktor.server.application.ApplicationCall
import java.io.FileNotFoundException
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.code
import kotlinx.html.dd
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h2
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul

/** The official assets' provenance, read from the catalog's `official/SOURCES.md` (written by the importer). */
data class OfficialSources(
    /** Repository URL → pinned commit. */
    val repositories: List<Pair<String, String>>,
    /** Document name → SHA-256 (the rulebook the rules text is checked against). */
    val documents: List<Pair<String, String>>,
    /** How many bundled files the list names. */
    val files: Int,
) {
    companion object {
        const val PATH = "official/SOURCES.md"

        private val CELL = Regex("""^\|(.+)\|\s*$""")

        fun parse(markdown: String): OfficialSources {
            val rows = markdown.lineSequence().mapNotNull { line ->
                CELL.find(line.trim())?.groupValues?.get(1)?.split('|')?.map { it.trim().trim('`') }
            }.filterNot { cells -> cells.all { it.isEmpty() || it.all { c -> c == '-' } } }.toList()
            val repositories = rows.filter { it.size == 2 && it[0].startsWith("https://") }.map { it[0] to it[1] }
            val documents = rows.filter { it.size == 3 && it[2].matches(Regex("[0-9a-f]{64}")) }.map { it[0] to it[2] }
            val files = rows.count { it.size == 2 && it[0].startsWith("official/") }
            return OfficialSources(repositories, documents, files)
        }
    }
}

/** `/settings/about`: the app's About screen, for the web app and its hub. */
internal suspend fun ApplicationCall.respondAboutPage(ctx: HubContext) {
    val sources = try {
        OfficialSources.parse(ctx.catalog.readText(OfficialSources.PATH))
    } catch (_: FileNotFoundException) {
        null
    }
    respondPage(settingsPage("About")) {
        section("about-header") {
            img(src = "/static/icons/brand_mark.svg", alt = "", classes = "brand-mark")
            h2 { +"Guards of Atlantis Companion" }
            p("muted") { +"Web app · hub ${Hub::class.java.`package`?.implementationVersion ?: "dev"}" }
        }
        card {
            h2 { +"Private build" }
            p("muted") {
                +"Made for one play group. Not sold, not published. This hub shows the group's data only to the "
                +"browsers that joined with the group's invite, and keeps it on the group's own machine."
            }
        }
        sectionHeader("Game content")
        card {
            p("muted") { +AboutCredits.GAME_CONTENT }
            p { +AboutCredits.NOT_AFFILIATED }
            sources?.let { officialAssets(it) }
        }
        sectionHeader("Data sources")
        card {
            p("muted") { +AboutCredits.DATA_SOURCES_INTRO }
            dl("credits") {
                AboutCredits.dataSources.forEach { (name, what) ->
                    dt { +name }
                    dd { +what }
                }
            }
        }
        sectionHeader("Original artwork")
        card { p("muted") { +AboutCredits.ORIGINAL_ARTWORK } }
        sectionHeader("Fonts")
        card {
            dl("credits") {
                licence(
                    "Cinzel",
                    "SIL Open Font License 1.1 · Copyright 2020 The Cinzel Project Authors",
                    "fonts/OFL-Cinzel.txt",
                )
                licence(
                    "Inter",
                    "SIL Open Font License 1.1 · Copyright 2020 The Inter Project Authors",
                    "fonts/OFL-Inter.txt",
                )
            }
        }
        sectionHeader("Web app")
        card {
            dl("credits") {
                licence("htmx 2.0.11", "Big Sky Software · Zero-Clause BSD", "licenses/htmx/LICENSE")
                licence(
                    "Material Symbols",
                    "Google · Apache License 2.0 (the web app's icons)",
                    "licenses/material-symbols/LICENSE",
                )
            }
            p("muted small") {
                +"The hub runs on Ktor, kotlinx.html, kotlinx.serialization and kotlinx.coroutines (JetBrains, "
                +"Apache 2.0), ZXing for the invite QR code (Apache 2.0) and SLF4J (MIT)."
            }
        }
    }
}

private fun FlowContent.officialAssets(sources: OfficialSources) {
    p("muted small") {
        +"Card images and portraits (${sources.files} files) come from these community repositories, at "
        +"pinned commits:"
    }
    ul("source-list") {
        sources.repositories.forEach { (url, commit) ->
            li {
                +url.removePrefix("https://github.com/")
                +" @ "
                code { +commit.take(COMMIT_SHOWN) }
            }
        }
        sources.documents.forEach { (name, sha) ->
            li {
                +"Rules text: $name, SHA-256 "
                code { +sha.take(COMMIT_SHOWN) }
            }
        }
    }
    p("small") { a(HubCatalog.url(OfficialSources.PATH)) { +"Every file and its source" } }
}

private fun kotlinx.html.DL.licence(name: String, detail: String, path: String) {
    dt { +name }
    dd {
        +"$detail · "
        a(StaticFiles.url(path)) { +"Licence" }
    }
}

private const val COMMIT_SHOWN = 12
