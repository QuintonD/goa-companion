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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.BannerMedallionWidth
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
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
 * labelled summary, then the verbatim passages, with the page reference in the banner at the foot
 * (a plain line at the page's end when sideways or with large text).
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
    val pages = "Rulebook ${RulesText.pages(topic.pages)} · quoted word for word"
    // Sideways or with large text, height is scarce and the band would wrap: its words end the page instead.
    val plainFoot = LocalDensity.current.fontScale > LARGE_FONT_SCALE ||
        LocalConfiguration.current.screenHeightDp < BANNER_FOOT_MIN_HEIGHT
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
                    val diagram = remember(excerpt) { RulesDiagrams.of(excerpt) }
                    if (diagram != null) DiagramWithCaption(diagram, excerpt) else ExcerptBlock(excerpt)
                }
            }
            item(key = "source") {
                Column(
                    Modifier.padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (plainFoot) {
                        Text(pages, style = MaterialTheme.typography.labelLarge, color = colors.onParchmentVariant)
                    }
                    Text(
                        "Source: the official Guards of Atlantis II rulebook, 2nd printing (printed page numbers).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onParchmentVariant,
                    )
                }
            }
        }
        if (!plainFoot) RulesFooter(pages)
    }
}

/**
 * A diagram the book prints, its white page multiplied into the parchment so it lies on our page as on the
 * book's, over its printed caption (in italics, as printed). Decorative for TalkBack: the caption says what
 * it shows. Space stays reserved while it loads.
 */
@Composable
private fun DiagramWithCaption(diagram: RulesDiagrams.Entry, caption: RulesExcerpt) {
    val image = rememberOfficialBitmap(diagram.art)
    val page = GoaTheme.colors.parchment
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.fillMaxWidth().aspectRatio(diagram.aspect)) {
            if (image != null) {
                Image(
                    image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(page, BlendMode.Multiply),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        ExcerptBlock(caption, style = readingStyle().copy(fontStyle = FontStyle.Italic))
    }
}

/** The page's foot, as the book's: the steel banner with the page reference. */
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
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(Color.Black, Offset(0f, 1f), 2f),
            ),
            color = colors.onBanner,
        )
    }
}

private val PAGE_MARGIN = 18.dp
private val FOOTER_HEIGHT = 42.dp

/** Below this height (dp) the foot band gives its room back to the text. */
private const val BANNER_FOOT_MIN_HEIGHT = 480
