package dev.quinton.goa.web.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconLink
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/*
 * PLACEHOLDER (W1 content replaces): the Heroes tab as a plain list of the catalog, read through
 * the shared CatalogRepository (core:model types, the phone's strict loader), so the shell's
 * routing, deep links and offline caching are exercised with the real catalog JSON and images.
 */

/** Heroes tab: the top bar has the Android app's actions (Rules, Board, Card trivia, Settings). */
@Composable
fun CatalogScreen() {
    val services = LocalServices.current
    val heroes = rememberLoad(Unit) { services.data.content.catalog.catalog.first().heroes }
    var more by remember { mutableStateOf(false) }
    Screen("Heroes", actions = { CatalogActions(onMore = { more = true }) }) {
        LoadContent(heroes, "Couldn't load the heroes") { list ->
            SectionHeader("${list.size} heroes")
            GoaCard(flush = true) {
                RowList {
                    list.forEach { hero ->
                        ListRow(
                            title = hero.name,
                            subtitle = hero.title,
                            route = Route.HeroDetail(hero.id),
                            leading = { HeroSymbol(hero) },
                        )
                    }
                }
            }
        }
    }
    if (more) {
        BottomSheet("More", onDismiss = { more = false }) {
            RowList {
                ListRow("Board", leadingIcon = "board", route = Route.Boards)
                ListRow("Card trivia", leadingIcon = "trivia", route = Route.Trivia())
            }
        }
    }
}

/**
 * Rules and Settings always; Board and Card trivia beside them while they fit, else under More
 * (goa.css container query on the top bar: narrow screens and large text).
 */
@Composable
private fun CatalogActions(onMore: () -> Unit) {
    IconLink("rules", "Rules", Route.Rules)
    IconLink("board", "Board", Route.Boards, extraClass = "collapsible")
    IconLink("trivia", "Card trivia", Route.Trivia(), extraClass = "collapsible")
    IconButton("more", "More options", onClick = onMore, extraClass = "more-only")
    IconLink("settings", "Settings", Route.Settings)
}

@Composable
private fun HeroSymbol(hero: Hero) {
    val symbol = hero.symbol
    if (symbol != null) {
        Img(symbol, alt = "") {
            classes("goa-hero-avatar")
            attr("loading", "lazy")
            attr("decoding", "async")
        }
    } else {
        Div({ classes("goa-hero-avatar") })
    }
}

/** PLACEHOLDER hero page: portrait, name and title from the catalog. */
@Composable
fun HeroDetailScreen(heroId: String) {
    val services = LocalServices.current
    val hero = rememberLoad(heroId) { services.data.content.catalog.catalog.first().hero(heroId) }
    Screen("Hero") {
        LoadContent(hero, "Couldn't load this hero") { found ->
            if (found == null) {
                EmptyState("info", "No such hero", "The catalog has no hero \"$heroId\".") {
                    GoaButton("All heroes", {
                        services.router.navigate(Route.Catalog, replace = true)
                    }, ButtonKind.TONAL)
                }
            } else {
                found.portrait?.let { Img(it, alt = "${found.name} portrait") { classes("goa-hero-portrait") } }
                H2({ classes("t-headline-small") }) { Text(found.name) }
                P({ classes("t-body-large", "c-variant") }) { Text(found.title) }
                EmptyState("nav_heroes", "Coming to the web app", "Stats, abilities and the deck as card scans.")
            }
        }
    }
}
