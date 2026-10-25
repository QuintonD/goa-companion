package dev.quinton.goa.web.rules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.rules.RulesSearch
import dev.quinton.goa.core.domain.rules.RulesText
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.core.model.rules.RulesTopic
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.RouteLink
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import dev.quinton.goa.web.ui.rememberLoad
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** The rules search text, kept while the Rules screen is in the history (Back from a topic keeps it). */
private class RulesQuery {
    var text by mutableStateOf("")
    var ladderOpen by mutableStateOf(false)
}

/** Rules: the level & defeat ladder first, then the searchable quick-reference topics (feature:rules). */
@Composable
fun RulesScreen() {
    val services = LocalServices.current
    val reference = rememberLoad(Unit) { services.data.content.rulesReference.load() }
    val state = rememberRetained("rules") { RulesQuery() }
    Screen("Rules") {
        LoadContent(reference, "Couldn't load the rules") { rules -> RulesList(rules, state) }
    }
    if (state.ladderOpen) {
        LevelLadderSheet(showRulesLink = false, onDismiss = { state.ladderOpen = false })
    }
}

@Composable
private fun RulesList(reference: RulesReference, state: RulesQuery) {
    val query = state.text
    val topics = RulesSearch.filter(reference.topics, query)
    val showLadder = RulesText.showsLadder(query)
    SearchField(query) { state.text = it }
    if (showLadder) LadderCard(onClick = { state.ladderOpen = true })
    SectionHeader(if (query.isBlank()) "Quick reference" else "${topics.size} of ${reference.topics.size} topics")
    if (topics.isEmpty() && !showLadder) {
        EmptyState("search_off", "No rules match", "Try another word, such as “respawn”, “tie” or “push”.")
    }
    if (topics.isNotEmpty()) {
        GoaCard(flush = true) {
            Ul({ classes("goa-list") }) {
                topics.forEach { topic -> TopicRow(topic) }
            }
        }
    }
    P({ classes("t-body-small", "c-variant", "goa-rules-footnote") }) {
        Text(
            "Quoted word for word from the official Guards of Atlantis II rulebook (2nd printing). " +
                "Page numbers are the rulebook's printed pages.",
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Label(attrs = { classes("goa-rules-search") }) {
        Icon("search")
        Span({ classes("goa-visually-hidden") }) { Text("Search rules") }
        Input(InputType.Search) {
            value(query)
            placeholder("Search rules")
            attr("enterkeyhint", "search")
            attr("autocomplete", "off")
            attr("autocorrect", "off")
            attr("autocapitalize", "none")
            attr("spellcheck", "false")
            classes("t-body-large")
            onInput { onChange(it.value) }
            onKeyDown { if (it.key == "Escape" && query.isNotEmpty()) onChange("") }
        }
        if (query.isNotEmpty()) IconButton("close", "Clear search", onClick = { onChange("") })
    }
}

@Composable
private fun LadderCard(onClick: () -> Unit) {
    Button({
        classes("goa-card", "clickable", "goa-rules-ladder-card")
        attr("type", "button")
        attr("aria-haspopup", "dialog")
        onClick { onClick() }
    }) {
        Span({ classes("goa-rules-ladder-icon") }) { Icon("medal", IconSize.LARGE) }
        Span({ classes("goa-rules-ladder-text") }) {
            Span({ classes("t-title-large", "goa-rules-ladder-title") }) { Text("Levels & defeats") }
            Span({ classes("t-body-medium", "c-variant") }) {
                Text("Life counters, coins and assist coins per level, level costs and card Tiers")
            }
        }
        PageTag(RulesText.LADDER_PAGES)
    }
}

@Composable
private fun TopicRow(topic: RulesTopic) {
    val pages = topic.pages.sorted()
    Li {
        RouteLink(Route.RulesTopic(topic.id), {
            classes("goa-row")
            attr("aria-label", "${topic.title}, ${RulesText.spokenPages(pages)}")
        }) {
            Span({ classes("goa-row-text") }) {
                Span({ classes("goa-row-title", "goa-rules-topic-title") }) { Text(topic.title) }
                Span({ classes("goa-row-subtitle", "t-label-medium") }) { Text(RulesText.pages(pages)) }
            }
            Span({ classes("goa-row-trailing") }) { Icon("chevron_right", IconSize.SMALL) }
        }
    }
}

/** One quick-reference topic: an optional labelled summary, then the verbatim rulebook passages. */
@Composable
fun RulesTopicScreen(topicId: String) {
    val services = LocalServices.current
    val reference = rememberLoad(Unit) { services.data.content.rulesReference.load() }
    val topic = (reference.state as? Load.Ready)?.value?.topics?.firstOrNull { it.id == topicId }
    Screen(topic?.title ?: "Rules") {
        LoadContent(reference, "Couldn't load the rules") {
            if (topic == null) {
                EmptyState("rules", "Topic not found", "This rules topic is not in the bundled reference.") {
                    GoaButton("All rules", {
                        services.router.navigate(Route.Rules, replace = true)
                    }, ButtonKind.TONAL)
                }
            } else {
                TopicContent(topic)
            }
        }
    }
}

@Composable
private fun TopicContent(topic: RulesTopic) {
    Div({ classes("goa-rules-topic") }) {
        P({ classes("t-label-large", "c-variant") }) {
            Text("Rulebook ${RulesText.pages(topic.pages)} · quoted word for word")
        }
        topic.summary?.let { SummaryNote(it) }
        topic.excerpts.forEachIndexed { i, excerpt ->
            if (i > 0 && excerpt.heading != null) Div({ classes("goa-rules-divider") })
            ExcerptBlock(excerpt)
        }
        P({ classes("t-body-small", "c-variant", "goa-rules-footnote") }) {
            Text("Source: the official Guards of Atlantis II rulebook, 2nd printing (printed page numbers).")
        }
    }
}
