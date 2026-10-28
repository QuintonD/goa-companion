package dev.quinton.goa.web.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.web.data.Assets
import dev.quinton.goa.web.pwa.BuildInfo
import dev.quinton.goa.web.ui.BodyText
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.MarkdownBlocks
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.SimpleMarkdown
import dev.quinton.goa.web.ui.rememberLoad
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** Community projects whose published data informed the catalog import (as on Android's About). */
private val DataSourceCredits = listOf(
    "Stats-of-Atlantis (QuasiStellar)" to "Card data, hero details and artwork",
    "goa2-faq (yvo-niedrich)" to "Card text cross-check and FAQ rulings",
    "goa2 (dmrediger)" to "Full card images",
    "guards-of-atlantis-ii-data (colefin8)" to "Card values cross-check",
)

private data class FontCredit(
    val family: String,
    val copyright: String,
    val file: String,
    val licence: String = "SIL Open Font License 1.1",
)

/** The bundled fonts (OFL 1.1); their licence texts are copied from `third_party/fonts/` at build time. */
private val FontCredits = listOf(
    FontCredit("Cinzel", "Copyright 2020 The Cinzel Project Authors", "OFL-Cinzel.txt"),
    FontCredit("Inter", "Copyright 2020 The Inter Project Authors", "OFL-Inter.txt"),
    FontCredit("Crimson Pro", "Copyright 2018 The Crimson Pro Project Authors", "OFL-CrimsonPro.txt"),
)

private sealed interface AboutSheet {
    data object Sources : AboutSheet

    data class License(val font: FontCredit) : AboutSheet
}

/** About: who made what. The official assets' sources (`official/SOURCES.md`) stay one tap away. */
@Composable
fun AboutScreen() {
    var sheet by remember { mutableStateOf<AboutSheet?>(null) }
    Screen("About") {
        Div({ classes("goa-brand") }) {
            Img("icons/brand_mark.svg", alt = "")
            H1({ classes("t-headline-small") }) { Text("Atlantis Companion") }
            BodyText(
                if (BuildInfo.isDev) {
                    "Development build"
                } else {
                    "Version ${BuildInfo.version} · build ${BuildInfo.build.take(
                        8,
                    )}"
                },
            )
        }
        GoaCard {
            Div({ classes("goa-inline", "goa-settings-notice-row") }) {
                Span({ classes("c-primary") }) { Icon("info") }
                Div({ classes("goa-stack") }) {
                    H2({ classes("t-title-medium") }) { Text("A free fan companion") }
                    BodyText(
                        "Made by a player for their play group and shared publicly, free of charge. " +
                            "Not sold and no ads.",
                    )
                }
            }
        }
        SectionHeader("Game content")
        GoaCard {
            Div({ classes("goa-stack") }) {
                BodyText(
                    "Guards of Atlantis II, its heroes, card text, card images and artwork are by Wolff Designa, " +
                        "who keep all rights to them. They appear here for non-commercial play by fans of the game.",
                )
                P({ classes("t-body-medium") }) { Text("Not affiliated with or endorsed by Wolff Designa.") }
                BodyText(
                    "Lists show smaller copies of the card scans and deck symbols so they load faster; " +
                        "the card viewer shows the scans themselves.",
                )
            }
        }
        GoaCard(flush = true) {
            RowList {
                ListRow(
                    "Official images and their sources",
                    subtitle = "Every card scan, portrait and symbol, and where it comes from",
                    leadingIcon = "article",
                    onClick = { sheet = AboutSheet.Sources },
                )
            }
        }
        SectionHeader("Data sources")
        GoaCard {
            Div({ classes("goa-stack") }) {
                BodyText("Thanks to these community projects, whose published data helped build and check the catalog:")
                DataSourceCredits.forEach { (name, what) ->
                    Div {
                        H3({ classes("t-title-small", "goa-card-title") }) { Text(name) }
                        BodyText(what)
                    }
                }
            }
        }
        SectionHeader("This web app")
        GoaCard {
            Div({ classes("goa-stack") }) {
                BodyText(
                    "The same app as on Android, running in the browser. Your players, matches and settings are " +
                        "stored in this browser on this device. Group sync shares the match log with your group " +
                        "through its relay, end-to-end encrypted: the relay never sees it.",
                )
            }
        }
        SectionHeader("Original artwork")
        GoaCard { BodyText("Hero sigils, player crests, icons and the app icon were drawn for this app.") }
        SectionHeader("Fonts")
        GoaCard(flush = true) {
            RowList {
                FontCredits.forEach { font ->
                    ListRow(
                        font.family,
                        subtitle = "${font.licence} · ${font.copyright}",
                        leadingIcon = "article",
                        trailing = "View",
                        onClick = { sheet = AboutSheet.License(font) },
                    )
                }
            }
        }
        SectionHeader("Icons")
        GoaCard(flush = true) {
            RowList {
                ListRow(
                    IconCredit.family,
                    subtitle = "${IconCredit.licence} · the game icons in card text",
                    leadingIcon = "article",
                    trailing = "View",
                    onClick = { sheet = AboutSheet.License(IconCredit) },
                )
            }
        }
    }
    when (val open = sheet) {
        AboutSheet.Sources -> BottomSheet("Official images", onDismiss = { sheet = null }) { SourcesDocument() }

        is AboutSheet.License -> BottomSheet("${open.font.family} licence", onDismiss = { sheet = null }) {
            LicenseText(open.font.file)
        }

        null -> Unit
    }
}

/** The glyphs of the game icons in card text, as on Android (material-icons-extended), Apache 2.0. */
private val IconCredit =
    FontCredit("Material Icons", "Copyright Google LLC", "LICENSE-Apache-2.0.txt", "Apache License 2.0")

@Composable
private fun SourcesDocument() {
    val doc = rememberLoad(Unit) { SimpleMarkdown.parse(Assets.text("official/SOURCES.md")) }
    LoadContent(doc, "Couldn't load the sources") { blocks -> MarkdownBlocks(blocks) }
}

@Composable
private fun LicenseText(file: String) {
    val text = rememberLoad(file) { Assets.text("licenses/$file") }
    LoadContent(text, "Couldn't load the licence") { Pre({ classes("goa-pre") }) { Text(it) } }
}
