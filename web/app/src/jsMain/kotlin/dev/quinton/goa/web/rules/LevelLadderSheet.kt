package dev.quinton.goa.web.rules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.rules.DefeatExceptions
import dev.quinton.goa.core.domain.rules.LadderLookup
import dev.quinton.goa.core.domain.rules.RulesText
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.rules.LevelRow
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

/** A catalog card whose printed text changes a defeat's cost or reward. [text] is the card's markup. */
internal data class ExceptionCard(val heroName: String, val cardName: String, val label: String, val text: String)

/** What the ladder sheet shows, built as on the phone (`LevelLadderViewModel`). */
internal data class Ladder(
    val ladder: LevelLadder,
    val exceptions: List<ExceptionCard>,
    /** True when [exceptions] cover the heroes of the current match, false = every hero. */
    val matchHeroesOnly: Boolean,
) {
    companion object {
        fun of(ladder: LevelLadder, catalog: Catalog, heroIds: List<String>): Ladder {
            val matchHeroes = heroIds.distinct().mapNotNull(catalog::hero)
            val heroes = matchHeroes.ifEmpty { catalog.heroes }
            return Ladder(
                ladder = ladder,
                exceptions = DefeatExceptions.find(heroes).map { (hero, card) ->
                    ExceptionCard(hero.name, card.name, RulesText.cardLabel(hero, card), card.text)
                },
                matchHeroesOnly = matchHeroes.isNotEmpty(),
            )
        }
    }
}

/**
 * "Levels & defeats" (bottom sheet): pick the defeated hero's level for the three numbers that
 * matter, the full p.13/p.15 table, the rules behind it (verbatim) and the cards that change it
 * (verbatim). [heroIds] are the current match's heroes (their cards only); empty = every hero.
 * [showRulesLink] offers "All rules" (off when opened from the Rules screen). Open it from any
 * screen (the Rules screen does; the timer opens the [LevelLadderScreen] page).
 */
@Composable
fun LevelLadderSheet(heroIds: List<String> = emptyList(), showRulesLink: Boolean = true, onDismiss: () -> Unit) {
    val router = LocalServices.current.router
    BottomSheet("Levels & defeats", onDismiss = onDismiss) {
        LevelLadderBody(
            heroIds,
            onOpenRules = if (showRulesLink) {
                {
                    onDismiss()
                    router.navigate(Route.Rules)
                }
            } else {
                null
            },
        )
    }
}

/**
 * The same ladder as a page, for [Route.LevelLadder] (`#/levels?heroes=…`): the running timer's
 * "Levels & defeats" and Level Up step open it with the match's heroes, and Back returns to the
 * timer. A page rather than a sheet so the address survives a reload (Android: `LevelLadderRoute`).
 */
@Composable
fun LevelLadderScreen(heroIds: List<String>) {
    val router = LocalServices.current.router
    Screen("Levels & defeats") {
        LevelLadderBody(heroIds, onOpenRules = { router.navigate(Route.Rules) })
    }
}

@Composable
private fun LevelLadderBody(heroIds: List<String>, onOpenRules: (() -> Unit)?) {
    val services = LocalServices.current
    val loaded = rememberLoad(heroIds) {
        val reference = services.data.content.rulesReference.load()
        val catalog = try {
            services.data.content.catalog.catalog.first()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            console.warn("Ladder without card exceptions: the catalog didn't load", e)
            Catalog.EMPTY // the numbers and rules still show, as on the phone
        }
        Ladder.of(reference.ladder, catalog, heroIds)
    }
    LoadContent(loaded, "Couldn't load the rules") { ladder -> LadderContent(ladder, onOpenRules) }
}

@Composable
private fun LadderContent(state: Ladder, onOpenRules: (() -> Unit)?) {
    val ladder = state.ladder
    var selected by remember { mutableStateOf<Int?>(null) }
    val select: (Int) -> Unit = { level -> selected = if (selected == level) null else level }
    Div({ classes("goa-rules-ladder") }) {
        Div({ classes("goa-rules-ladder-pages") }) { PageTag(ladder.pages) }
        LevelPicker(ladder.levels, selected, select)
        Div({ attr("aria-live", "polite") }) {
            selected?.let { LadderLookup.row(ladder, it) }?.let { DefeatResult(it) }
        }
        LadderTable(ladder, selected, select)
        SectionHeader("When a hero is defeated")
        ladder.defeat.forEach { ExcerptBlock(it, compact = true) }
        SectionHeader("Levelling up")
        ladder.levelUp.forEach { ExcerptBlock(it, compact = true) }
        SectionHeader(if (state.matchHeroesOnly) "Cards in this match that change this" else "Cards that change this")
        ExcerptBlock(ladder.cardText, compact = true)
        if (state.exceptions.isEmpty()) {
            P({ classes("t-body-medium", "c-variant") }) {
                Text(
                    if (state.matchHeroesOnly) {
                        "No card of the heroes in this match changes Life counters or assist coins."
                    } else {
                        "No card in the catalog changes Life counters or assist coins."
                    },
                )
            }
        }
        state.exceptions.forEach { ExceptionCardView(it) }
        if (onOpenRules != null) {
            Div({ classes("goa-rules-all") }) {
                GoaButton("All rules", onOpenRules, ButtonKind.OUTLINED, icon = "rules", block = true)
            }
        }
    }
}

/** Eight large level buttons (two rows of four): the defeated hero's level. */
@Composable
private fun LevelPicker(levels: List<LevelRow>, selected: Int?, onSelect: (Int) -> Unit) {
    Div({
        classes("t-overline", "c-primary", "goa-rules-picker-label")
        attr("id", "goa-ladder-picker")
    }) { Text("Hero defeated at level") }
    Div({
        classes("goa-rules-levels")
        attr("role", "radiogroup")
        attr("aria-labelledby", "goa-ladder-picker")
    }) {
        levels.forEach { row ->
            Button({
                type(ButtonType.Button)
                classes("goa-rules-level")
                attr("role", "radio")
                attr("aria-checked", (row.level == selected).toString())
                attr("aria-label", "Level ${row.level}")
                onClick { onSelect(row.level) }
            }) { Text("${row.level}") }
        }
    }
}

/** The three numbers for the picked level, in the rulebook's table terms. */
@Composable
private fun DefeatResult(row: LevelRow) {
    Div({
        classes("goa-rules-result")
        attr("role", "group")
        attr("aria-label", RulesText.spokenDefeat(row))
    }) {
        P({ classes("t-title-medium") }) {
            Text("Level ${row.level} hero defeated · lowest card Tier ${RulesText.roman(row.lowestTier)}")
        }
        Div({
            classes("goa-rules-numbers")
            attr("aria-hidden", "true")
        }) {
            listOf(
                Triple(row.lifeCounters, "Life counters", "their team spends"),
                Triple(row.defeatCoins, "Coins (Defeat)", "the hero who defeated them collects"),
                Triple(row.assistCoins, "Assist Coins", "that hero's friendly heroes collect"),
            ).forEach { (value, label, detail) ->
                Div({ classes("goa-rules-number") }) {
                    Span({ classes("goa-rules-digit") }) { Text("$value") }
                    Span({ classes("goa-rules-number-text") }) {
                        Span({ classes("t-label-large") }) { Text(label) }
                        Span({ classes("t-body-small", "c-variant") }) { Text(detail) }
                    }
                }
            }
        }
    }
}

/** Row per level; scrolls sideways when large text makes it wider than the sheet. */
@Composable
private fun LadderTable(ladder: LevelLadder, selected: Int?, onSelect: (Int) -> Unit) {
    Div({ classes("goa-rules-ladder-table-wrap") }) {
        Table({ classes("goa-rules-ladder-table") }) {
            Thead {
                Tr({ attr("aria-hidden", "true") }) {
                    RulesText.LADDER_HEADERS.forEach { Th({ attr("scope", "col") }) { Text(it) } }
                }
            }
            Tbody {
                ladder.levels.forEach { row ->
                    val next = LadderLookup.nextLevelCost(ladder, row.level)
                    Tr({
                        if (row.level == selected) classes("selected")
                        onClick { onSelect(row.level) }
                    }) {
                        Th({ attr("scope", "row") }) {
                            // The row's one control: VoiceOver reads the whole row through it.
                            Button({
                                type(ButtonType.Button)
                                classes("goa-rules-row-button")
                                attr("aria-label", RulesText.spokenLadderRow(row, next))
                                attr("aria-pressed", (row.level == selected).toString())
                                onClick {
                                    it.stopPropagation()
                                    onSelect(row.level)
                                }
                            }) { Text("${row.level}") }
                        }
                        listOf(
                            RulesText.roman(row.lowestTier),
                            "${row.defeatCoins}",
                            "${row.assistCoins}",
                            "${row.lifeCounters}",
                            next?.toString() ?: "–",
                            RulesText.roman(row.maxTier),
                        ).forEach { Td({ attr("aria-hidden", "true") }) { Text(it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExceptionCardView(card: ExceptionCard) {
    Div({ classes("goa-rules-exception") }) {
        H3({ classes("t-title-medium") }) { Text("${card.heroName} · ${card.cardName}") }
        P({ classes("t-label-medium", "c-variant") }) { Text(card.label) }
        P({ classes("t-body-large", "goa-rules-card-text") }) { CardTextView(card.text) }
    }
}
