package dev.quinton.goa.web.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.HANDICAP_EXPLANATION
import dev.quinton.goa.core.domain.catalog.HeroLogSummary
import dev.quinton.goa.core.domain.catalog.HeroPilotStat
import dev.quinton.goa.core.domain.catalog.PROFILE_SCALE
import dev.quinton.goa.core.domain.catalog.ProfileSegment
import dev.quinton.goa.core.domain.catalog.attackProfile
import dev.quinton.goa.core.domain.catalog.elo
import dev.quinton.goa.core.domain.catalog.handicapConflict
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.percent
import dev.quinton.goa.core.domain.catalog.plural
import dev.quinton.goa.core.domain.catalog.profileBars
import dev.quinton.goa.core.domain.meta.HeroPilots
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.Ruling
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.PlayerAvatar
import dev.quinton.goa.web.ui.RouteLink
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.browser.document
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** Personal stats for the hero: derived Elo/usage plus who played it (feature:catalog `HeroPersonalStats`). */
private data class HeroPersonalStats(val rating: Rating?, val usage: HeroUsage?, val log: HeroLogSummary) {
    val hasGames: Boolean get() = log.games > 0 || (rating?.games ?: 0) > 0 || (usage?.matches ?: 0) > 0
}

/**
 * A hero's page (feature:catalog `HeroDetailScreen`): art and identity, the Hero Profile, the
 * group's results with the hero, the deck as the official card scans by colour and tier, the
 * Handicap card, extras (Gydion's spellbook), FAQ rulings, lore and sources.
 */
@Composable
fun HeroDetailScreen(heroId: String) {
    val services = LocalServices.current
    val loaded = rememberLoad(heroId) {
        val catalog = services.data.content.browse.indexed().catalog
        catalog.hero(heroId)?.let { hero ->
            Triple(hero, catalog.product(hero.product)?.name ?: hero.product, DeckLayout.of(hero))
        }
    }
    var headerVisible by remember { mutableStateOf(true) }
    val ready = (loaded.state as? Load.Ready)?.value
    HideTitleWhile(headerVisible && ready != null)
    Screen(ready?.first?.name ?: "Hero") {
        LoadContent(loaded, "Couldn't load this hero") { found ->
            if (found == null) {
                EmptyState("person_search", "Unknown hero", "This hero isn't in the catalog bundled with this build.") {
                    GoaButton("All heroes", {
                        services.router.navigate(Route.Catalog, replace = true)
                    }, ButtonKind.TONAL)
                }
            } else {
                Div({ classes("goa-hero-page") }) { HeroPage(found, onHeaderVisible = { headerVisible = it }) }
            }
        }
    }
}

@Composable
private fun HeroPage(found: Triple<Hero, String, DeckLayout>, onHeaderVisible: (Boolean) -> Unit) {
    val (hero, productName, deck) = found
    HeroHeader(hero, productName, onVisible = onHeaderVisible)
    Section {
        SectionTitle("Profile")
        StatBars(hero)
    }
    Section {
        SectionTitle("Your table")
        PersonalStatsCard(rememberPersonalStats(hero.id))
    }
    DeckSections(hero, deck)
    LearnNumbers(hero)
    deck.handicap?.let { handicap ->
        Section {
            SectionTitle("Handicap")
            P({ classes("t-body-small", "c-variant") }) { Text(HANDICAP_EXPLANATION) }
            handicapConflict(hero, handicap)?.let { P({ classes("t-body-small", "c-error") }) { Text(it) } }
            DeckCardGrid(hero, listOf(handicap), isHandicap = true)
        }
    }
    ExtrasSection(hero, deck)
    Section {
        SectionTitle("FAQ rulings")
        if (hero.faq.isEmpty()) {
            P({
                classes("t-body-medium", "c-variant")
            }) { Text("No official rulings recorded for this hero.") }
        } else {
            hero.faq.forEach { FaqItem(it) }
        }
    }
    hero.lore?.takeIf { it.isNotBlank() }?.let { lore ->
        Section {
            SectionTitle("Lore")
            P({ classes("goa-lore", "t-body-large") }) { Text(lore) }
        }
    }
    Section {
        SectionTitle("Sources")
        SourcesBlock(hero)
    }
}

/**
 * The phone shows the hero's name in the top bar only once the big name has scrolled away; the
 * title stays in the page for VoiceOver either way (visually hidden).
 */
@Composable
private fun HideTitleWhile(hidden: Boolean) {
    DisposableEffect(hidden) {
        val root = document.documentElement
        if (hidden) root?.classList?.add(TITLE_HIDDEN) else root?.classList?.remove(TITLE_HIDDEN)
        onDispose { root?.classList?.remove(TITLE_HIDDEN) }
    }
}

@Composable
private fun rememberPersonalStats(heroId: String): HeroPersonalStats {
    val group = LocalServices.current.data.group
    val rating by remember(heroId) { optional<Rating?>(null) { group.ratings.observeHeroRatings().map { it[heroId] } } }
        .collectAsState(null)
    val usage by remember(heroId) { optional<HeroUsage?>(null) { group.ratings.observeHeroUsage().map { it[heroId] } } }
        .collectAsState(null)
    val pilots by remember(heroId) { optional<HeroPilots?>(null) { group.heroPilots.observeHeroPilots(heroId) } }
        .collectAsState(null)
    val players by remember { optional(emptyList<Player>()) { group.players.observePlayers(includeArchived = true) } }
        .collectAsState(emptyList())
    return HeroPersonalStats(rating, usage, pilots?.let { HeroLogSummary.of(it, players) } ?: HeroLogSummary.EMPTY)
}

@Composable
internal fun SectionTitle(text: String, trailing: String? = null) {
    H2({ classes("goa-catalog-section", "t-title-large") }) {
        Span { Text(text) }
        trailing?.let { Span({ classes("t-label-medium", "c-variant") }) { Text(it) } }
    }
}

@Composable
private fun HeroHeader(hero: Hero, productName: String, onVisible: (Boolean) -> Unit) {
    Div({ classes("goa-hero-header") }) {
        HeroArt(hero, "header", alt = "${hero.name} artwork", eager = true)
        Div({ classes("goa-hero-header-text") }) {
            Div({ classes("goa-hero-header-names") }) {
                // The top bar's title is the page's heading for VoiceOver (hidden from sight while
                // this big name shows): the name isn't read twice.
                P({
                    classes("goa-hero-name", "t-headline-medium")
                    attr("aria-hidden", "true")
                    ref { element ->
                        val options: dynamic = js("({})")
                        options.rootMargin = HEADER_MARGIN
                        val observer = IntersectionObserver({ entries, _ ->
                            entries.forEach { onVisible(it.isIntersecting as Boolean) }
                        }, options)
                        observer.observe(element)
                        onDispose { observer.disconnect() }
                    }
                }) { Text(hero.name) }
                P({ classes("goa-hero-header-title", "t-title-medium", "c-variant") }) { Text(hero.title) }
            }
            HeroSymbol(hero, "header", alt = "${hero.name}'s deck symbol")
        }
        if (hero.aliases.isNotEmpty()) {
            P({ classes("t-body-small", "c-variant") }) { Text("Also known as ${hero.aliases.joinToString()}") }
        }
        Div({ classes("goa-pills", "goa-hero-header-pills") }) {
            hero.complexity?.let { ComplexityStars(it, showName = true) }
            MetaPill(productName)
            // App-derived from the Ranged subtype of the deck's Attack cards (DOMAIN.md); kept apart
            // from the official role chips (the rulebook calls no-range attacks "Non-Ranged", p.13).
            hero.attackProfile().pill?.let { MetaPill(it) }
            if (hero.printing != 2) MetaPill("Printing ${hero.printing}")
            if (hero.verified) VerifiedPill() else UnverifiedBadge()
        }
        if (hero.roles.isNotEmpty()) {
            Div({ classes("goa-pills", "goa-hero-roles") }) {
                hero.roles.forEach { Span({ classes("goa-role", "t-label-large") }) { Text(it.label) } }
            }
        }
    }
}

@Composable
private fun VerifiedPill() {
    Span({ classes("goa-verified", "t-label-medium") }) {
        Icon("verified", IconSize.SMALL)
        Text("Verified")
    }
}

/** Hero Profile bars from 1 ("Very poor") to 8 ("Exceptional"): solid to the base, light to the max. */
@Composable
private fun StatBars(hero: Hero) {
    Div({ classes("goa-profile") }) {
        hero.stats.profileBars().forEach { bar ->
            Div({
                classes("goa-profile-bar")
                attr("role", "img")
                attr("aria-label", bar.spoken)
            }) {
                Div({
                    classes("goa-profile-head")
                    attr("aria-hidden", "true")
                }) {
                    GameIcon(statToken(bar.stat), extraClass = "primary")
                    Span({ classes("t-title-small") }) { Text(bar.stat.label) }
                    Span({ classes("t-title-small", "goa-profile-value") }) { Text(bar.rangeText) }
                }
                Div({
                    classes("goa-profile-segments")
                    attr("aria-hidden", "true")
                }) {
                    for (level in 1..PROFILE_SCALE) {
                        Span({
                            classes(
                                "goa-profile-segment",
                                when (bar.segment(level)) {
                                    ProfileSegment.BASE -> "base"
                                    ProfileSegment.CONDITIONAL -> "conditional"
                                    ProfileSegment.EMPTY -> "empty"
                                },
                            )
                        })
                    }
                }
            }
        }
        Div({
            classes("goa-profile-scale", "t-label-small", "c-variant")
            attr("aria-hidden", "true")
        }) {
            Span { Text("1 · Very poor") }
            Span { Text("Exceptional · 8") }
        }
        Div({
            classes("goa-profile-legend", "t-label-small", "c-variant")
            attr("aria-hidden", "true")
        }) {
            Span({ classes("goa-profile-swatch", "base") })
            Span { Text("Base") }
            Span({ classes("goa-profile-swatch", "conditional") })
            Span { Text("Conditional (level-ups, card text)") }
        }
    }
}

/** The inline icon token for a stat (`game_<token>.svg`). */
internal fun statToken(stat: Stat): String = stat.name.lowercase()

@Composable
private fun PersonalStatsCard(stats: HeroPersonalStats) {
    Div({ classes("goa-card", "goa-hero-stats") }) {
        if (!stats.hasGames) {
            P({ classes("t-title-small") }) { Text("No games logged with this hero yet") }
            P({ classes("t-body-medium", "c-variant") }) {
                Text("Log a match in the Players tab to see Elo, win rate and who plays this hero best.")
            }
            return@Div
        }
        val rating = stats.rating
        val usage = stats.usage
        val games = usage?.matches ?: rating?.games ?: stats.log.games
        val wins = usage?.wins ?: rating?.wins ?: stats.log.wins
        Div({ classes("goa-metrics") }) {
            Metric(
                "Elo",
                rating?.takeIf { it.games > 0 }?.let { elo(it.value) } ?: "—",
                rating?.takeIf { it.provisional && it.games > 0 }?.let { "provisional" },
            )
            Metric("Games", games.toString(), "${wins}W · ${games - wins}L")
            Metric("Win rate", if (games > 0) percent(wins.toDouble() / games) else "—", null)
            usage?.let {
                Metric("Pick rate", percent(it.pickRate), null)
                Metric("Ban rate", percent(it.banRate), plural(it.bans, "ban"))
            }
        }
        PilotsBlock(stats)
    }
}

@Composable
private fun Metric(label: String, value: String, note: String?) {
    Div({ classes("goa-metric") }) {
        Span({ classes("t-label-medium", "c-variant") }) { Text(label) }
        Span({ classes("t-headline-small", "c-primary") }) { Text(value) }
        note?.let { Span({ classes("t-label-small", "c-variant") }) { Text(it) } }
    }
}

@Composable
private fun PilotsBlock(stats: HeroPersonalStats) {
    val best = stats.log.bestPilots
    val pilots = best.ifEmpty { stats.log.allPilots }
    if (pilots.isEmpty()) return
    H3({
        classes("t-title-small", "goa-pilots-title")
    }) { Text(if (best.isNotEmpty()) "Best with this hero" else "Played by") }
    if (best.isEmpty()) {
        P({
            classes("t-body-small", "c-variant")
        }) { Text("Rankings start at ${stats.log.minGames} games per player.") }
    }
    pilots.take(MAX_PILOTS).forEachIndexed { index, pilot -> PilotRow(index + 1, pilot, ranked = best.isNotEmpty()) }
}

@Composable
private fun PilotRow(rank: Int, pilot: HeroPilotStat, ranked: Boolean) {
    val name = pilot.player?.name ?: "Unknown player"
    val losses = pilot.games - pilot.wins
    Div({ classes("goa-pilot") }) {
        if (ranked) Span({ classes("goa-pilot-rank", "t-title-medium", "c-primary") }) { Text("$rank") }
        PlayerAvatar(pilot.player, PILOT_AVATAR)
        Span({ classes("goa-pilot-name", "t-body-large") }) { Text(name) }
        Span({ classes("goa-pilot-record") }) {
            Span({ classes("t-title-small") }) { Text(percent(pilot.winRate)) }
            Span({
                classes("t-label-small", "c-variant")
                attr("aria-label", "${plural(pilot.wins, "win")}, ${plural(losses, "loss", "losses")}")
            }) { Text("${pilot.wins}–$losses") }
        }
    }
}

/** Opens card trivia on this hero: an unobtrusive tonal action under the deck. */
@Composable
private fun LearnNumbers(hero: Hero) {
    RouteLink(Route.Trivia(hero.id), { classes("goa-button", "tonal", "block", "goa-learn-numbers") }) {
        Icon("trivia")
        Text("Learn ${hero.name}'s card numbers")
    }
}

/** A ruling: the question opens its answer (a disclosure button, `aria-expanded`). */
@Composable
private fun FaqItem(ruling: Ruling) {
    var expanded by remember(ruling.question) { mutableStateOf(false) }
    Div({
        classes("goa-faq")
        if (expanded) classes("open")
    }) {
        Button({
            type(ButtonType.Button)
            classes("goa-faq-question")
            attr("aria-expanded", expanded.toString())
            onClick { expanded = !expanded }
        }) {
            Icon("help", IconSize.SMALL)
            Span({ classes("t-title-small") }) { Text(ruling.question) }
            Span({ classes("goa-faq-chevron", "c-variant") }) { Icon("expand_more", IconSize.SMALL) }
        }
        if (expanded) {
            Div({ classes("goa-faq-answer") }) {
                P({ classes("t-body-medium") }) { Text(ruling.answer) }
                P({ classes("t-label-small", "c-variant") }) { Text("Source: ${ruling.source}") }
            }
        }
    }
}

@Composable
private fun SourcesBlock(hero: Hero) {
    if (hero.sources.isEmpty()) P({ classes("t-body-medium", "c-variant") }) { Text("No sources recorded.") }
    hero.sources.forEach { source ->
        Div({ classes("goa-source") }) {
            P({ classes("t-body-medium") }) { Text(source.name) }
            // Only web addresses become links (never `javascript:` or `data:`, whatever the data says).
            val url = source.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            val retrieved = source.retrieved?.let { "retrieved $it" }
            if (url != null || retrieved != null) {
                P({ classes("t-body-small", "c-variant") }) {
                    if (url != null) {
                        A(url, {
                            target(ATarget.Blank)
                            attr("rel", "noopener noreferrer")
                        }) { Text(url) }
                    }
                    if (url != null && retrieved != null) Text(" · ")
                    retrieved?.let { Text(it) }
                }
            }
        }
    }
    P({ classes("t-label-small", "c-variant", "goa-source-notice") }) {
        Text("Game content and art © Wolff Designa. A non-commercial fan companion, not affiliated with Wolff Designa.")
    }
}

/** The top bar's height: the name counts as gone once it has scrolled under the bar. */
private const val HEADER_MARGIN = "-64px 0px 0px 0px"

/** The browser's IntersectionObserver (iOS 12.2+): tells when the hero's name scrolls out of view. */
private external class IntersectionObserver(
    callback: (entries: Array<dynamic>, observer: dynamic) -> Unit,
    options: dynamic,
) {
    fun observe(target: org.w3c.dom.Element)

    fun disconnect()
}

private const val MAX_PILOTS = 3
private const val TITLE_HIDDEN = "goa-title-hidden"

/** The phone's pilot row avatar (36 dp). */
private const val PILOT_AVATAR = 36
