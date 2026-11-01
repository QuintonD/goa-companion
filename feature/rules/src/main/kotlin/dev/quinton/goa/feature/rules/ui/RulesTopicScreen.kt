package dev.quinton.goa.feature.rules.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.BannerMedallionWidth
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.rememberOfficialBitmap
import dev.quinton.goa.core.designsystem.component.steelBanner
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.rules.RulesText
import dev.quinton.goa.core.model.rules.RulesExcerpt
import dev.quinton.goa.core.model.rules.RulesTopic
import dev.quinton.goa.feature.rules.RulesTopicUiState

/**
 * One quick-reference topic set like the rulebook's page, on the parchment in both themes: an optional
 * labelled summary, then the verbatim passages, with the page reference in the banner at the foot.
 */
@Composable
fun RulesTopicScreen(state: RulesTopicUiState, onBack: () -> Unit) {
    val title = (state as? RulesTopicUiState.Ready)?.topic?.title ?: "Rules"
    GoaScreen(title = title, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                RulesTopicUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                RulesTopicUiState.NotFound -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "Topic not found",
                    message = "This rules topic is not in the bundled reference.",
                    modifier = Modifier.align(Alignment.Center),
                )

                is RulesTopicUiState.Ready -> TopicContent(state.topic)
            }
        }
    }
}

@Composable
internal fun TopicContent(topic: RulesTopic) {
    val colors = GoaTheme.colors
    ParchmentPage(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = PAGE_MARGIN, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            topic.summary?.let { summary -> item(key = "summary") { SummaryNote(summary) } }
            itemsIndexed(topic.excerpts, key = { i, _ -> "excerpt-$i" }) { i, excerpt ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    if (i > 0 && excerpt.heading != null) HorizontalDivider(color = colors.parchmentEdge)
                    ExcerptBlock(excerpt)
                    if (showsPushDiagram(topic, excerpt)) PushDiagram()
                }
            }
            item(key = "source") {
                Text(
                    "Source: the official Guards of Atlantis II rulebook, 2nd printing (printed page numbers).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onParchmentVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
        RulesFooter("Rulebook ${RulesText.pages(topic.pages)} · quoted word for word")
    }
}

/** The p.8 diagram goes under the three steps of a push, where the book prints it. */
private fun showsPushDiagram(topic: RulesTopic, excerpt: RulesExcerpt): Boolean =
    topic.id == PUSH_TOPIC && excerpt.page == PUSH_PAGE && excerpt.heading == PUSH_HEADING

/**
 * The rulebook's "Lane Push" diagram (p.8). Its white page is multiplied into the parchment, so it lies
 * on our page as on the book's. Its printed caption isn't in the verbatim data, so it has none; decorative
 * for TalkBack, the steps above say what it shows. Space stays reserved while it loads.
 */
@Composable
private fun PushDiagram() {
    val diagram = rememberOfficialBitmap(LookArt.PUSH_DIAGRAM_1)
    val page = GoaTheme.colors.parchment
    Box(Modifier.fillMaxWidth().aspectRatio(PUSH_DIAGRAM_ASPECT)) {
        if (diagram != null) {
            Image(
                diagram,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(page, BlendMode.Multiply),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The page's foot, as the book's: the steel banner with the page reference. Grows with large text. */
@Composable
private fun RulesFooter(text: String) {
    val colors = GoaTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = FOOTER_HEIGHT)
            .steelBanner(colors, MaterialTheme.colorScheme.primary)
            .padding(start = Spacing.md, end = BannerMedallionWidth + Spacing.sm, top = 10.dp, bottom = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = colors.onBanner,
        )
    }
}

private val PAGE_MARGIN = 18.dp
private val FOOTER_HEIGHT = 42.dp
private const val PUSH_TOPIC = "pushing-the-lane"
private const val PUSH_PAGE = 8
private const val PUSH_HEADING = "Push the Lane"

/** Width ÷ height of the p.8 diagram (1461 × 1126). */
private const val PUSH_DIAGRAM_ASPECT = 1461f / 1126f
