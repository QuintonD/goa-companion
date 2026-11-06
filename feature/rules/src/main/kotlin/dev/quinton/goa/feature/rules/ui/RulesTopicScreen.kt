package dev.quinton.goa.feature.rules.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.rules.RulesText
import dev.quinton.goa.core.model.rules.RulesExcerpt
import dev.quinton.goa.core.model.rules.RulesTopic
import dev.quinton.goa.feature.rules.RulesTopicUiState
import kotlinx.coroutines.launch

/**
 * One quick-reference topic set like the rulebook's page, on the parchment in both themes: the topic's title when
 * the banner holds only its head, a navigator over a long topic, an optional labelled summary, then the verbatim
 * passages, with the page reference in the banner at the foot (a plain line at the page's end when sideways or
 * with large text).
 */
@Composable
fun RulesTopicScreen(state: RulesTopicUiState, onBack: () -> Unit) {
    val title = (state as? RulesTopicUiState.Ready)?.topic?.title ?: "Rules"
    // A long title would shrink to fit the banner: its head there, the whole title at the top of the page.
    val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    GoaScreen(title = bannerTitle(title, large), onBack = onBack) { padding ->
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
    val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    // Sideways or with large text, height is scarce and the band would wrap: its words end the page instead.
    val plainFoot = large || LocalConfiguration.current.screenHeightDp < BANNER_FOOT_MIN_HEIGHT
    val sections = remember(topic) { topicSections(topic) }
    val showTitle = bannerTitle(topic.title, large) != topic.title
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Items before the first passage: the title, the navigator, the summary.
    val lead = (if (showTitle) 1 else 0) + (if (sections.isNotEmpty()) 1 else 0) + (if (topic.summary != null) 1 else 0)
    val navIndex = if (showTitle) 1 else 0
    val navHeight = with(LocalDensity.current) { NAV_HEIGHT.roundToPx() }
    val active by remember(sections, lead) {
        derivedStateOf {
            val info = list.layoutInfo
            val top = info.visibleItemsInfo
                .firstOrNull { it.key != NAV_KEY && it.offset + it.size > navHeight }?.index ?: 0
            val atEnd = !list.canScrollForward && list.canScrollBackward
            val excerpt = if (atEnd) topic.excerpts.lastIndex else top - lead
            sections.lastOrNull { it.excerpt <= excerpt }
        }
    }
    val margin = Modifier.padding(horizontal = PAGE_MARGIN)
    ParchmentPage(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (showTitle) item(key = "title") { TopicTitle(topic.title, margin) }
            if (sections.isNotEmpty()) {
                stickyHeader(key = NAV_KEY) {
                    TopicNavigator(sections, active) { section ->
                        val index = lead + section.excerpt
                        scope.launch { list.animateScrollToItem(index, -navHeight) }
                    }
                }
            }
            topic.summary?.let { summary -> item(key = "summary") { SummaryNote(summary, margin) } }
            itemsIndexed(topic.excerpts, key = { i, _ -> "excerpt-$i" }) { i, excerpt ->
                Column(margin, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    if (i > 0 && excerpt.heading != null) HorizontalDivider(color = colors.parchmentEdge)
                    val diagram = remember(excerpt) { RulesDiagrams.of(excerpt) }
                    if (diagram != null) DiagramWithCaption(diagram, excerpt) else ExcerptBlock(excerpt)
                }
            }
            item(key = "source") {
                Column(
                    margin.padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (plainFoot) {
                        Text(pages, style = MaterialTheme.typography.labelLarge, color = colors.onParchmentVariant)
                    }
                    Text(
                        "Source: the official Guards of Atlantis II rulebook, 2nd printing (printed page numbers).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onParchmentVariant,
                    )
                }
            }
        }
        if (!plainFoot) RulesFooter(pages)
    }
}

/** The whole title, as the book sets a chapter's: Cinzel, in the page's ink. */
@Composable
private fun TopicTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = GoaTextStyles.sectionHead.copy(fontSize = 26.sp, lineHeight = 32.sp),
        color = GoaTheme.colors.onParchment,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The topic's parts, held at the top of the page as you read: the rulebook's own heads (and "Diagrams"), a tap
 * opens that passage. The one you are reading is filled in the page's ink (never by colour alone: filled, not
 * outlined). Scrolls sideways when the heads don't fit.
 */
@Composable
private fun TopicNavigator(sections: List<TopicSection>, active: TopicSection?, onSelect: (TopicSection) -> Unit) {
    val colors = GoaTheme.colors
    val row = rememberScrollState()
    Column(Modifier.fillMaxWidth().background(colors.parchment)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = NAV_HEIGHT)
                .horizontalScroll(row)
                .padding(horizontal = PAGE_MARGIN - Spacing.xs)
                .semantics { contentDescription = "Sections" },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEach { section ->
                SectionChip(section.label, selected = section == active) { onSelect(section) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.parchmentEdge))
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = GoaTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .heightIn(min = NAV_HEIGHT)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.parchment else colors.onParchment,
            maxLines = 1,
            modifier = Modifier
                .clip(shape)
                .then(if (selected) Modifier.background(colors.onParchment, shape) else Modifier)
                .border(1.dp, if (selected) colors.onParchment else colors.parchmentEdge, shape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
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
                    // The crop's edges hold a line of the book's page panel.
                    modifier = Modifier.fillMaxSize().drawWithContent {
                        clipRect(left = size.width * DIAGRAM_EDGE, right = size.width * (1 - DIAGRAM_EDGE)) {
                            this@drawWithContent.drawContent()
                        }
                    },
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
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(Color.Black, Offset(0f, 1f), 2f),
            ),
            color = colors.onBanner,
        )
    }
}

/**
 * The banner's title: a short one whole; a long one by its head, which the page then follows with the whole
 * title ("End of round: the 6 steps" → "End of round", "Attack, defense & defeats" → "Attack, defense",
 * "Minions, minion coins & the Heavy minion" → "Minions"). With large text even a short one is cut to its head.
 */
internal fun bannerTitle(title: String, large: Boolean): String {
    if (title.length <= BANNER_WHOLE && !large) return title
    val head = title.substringBefore(':').substringBefore(" & ").trim()
    return if (head.length > BANNER_HEAD || large) head.substringBefore(',').trim() else head
}

/** Titles up to this many letters fit the banner at full size. */
private const val BANNER_WHOLE = 18
private const val BANNER_HEAD = 16
private const val NAV_KEY = "sections"
private val NAV_HEIGHT = 48.dp

private const val DIAGRAM_EDGE = 0.015f
private val PAGE_MARGIN = 18.dp
private val FOOTER_HEIGHT = 42.dp

/** Below this height (dp) the foot band gives its room back to the text. */
private const val BANNER_FOOT_MIN_HEIGHT = 480
