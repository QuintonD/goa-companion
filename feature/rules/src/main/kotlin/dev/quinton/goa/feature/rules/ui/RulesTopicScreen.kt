package dev.quinton.goa.feature.rules.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSheet
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateRowDivider
import dev.quinton.goa.core.designsystem.component.SelectablePlate
import dev.quinton.goa.core.designsystem.component.bannerEndWidth
import dev.quinton.goa.core.designsystem.component.fadingHorizontalScroll
import dev.quinton.goa.core.designsystem.component.rememberOfficialBitmap
import dev.quinton.goa.core.designsystem.component.scaledIconSize
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
 * One quick-reference topic set like the rulebook's page (parchment when light, a dark parchment when dark): the
 * title when the banner holds only its head, a navigator over a long topic, an optional labelled summary, the verbatim
 * passages, with the page reference in the banner at the foot (a plain line at the page's end when sideways or
 * with large text).
 */
@Composable
fun RulesTopicScreen(state: RulesTopicUiState, onBack: () -> Unit) {
    val title = (state as? RulesTopicUiState.Ready)?.topic?.title ?: "Rules"
    // A long title would shrink to fit the banner: its head there, the whole title at the top of the page.
    val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
    // The sections are listed from the navigator's own labelled "Sections" button, so the banner keeps only Back.
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

                is RulesTopicUiState.Ready -> TopicContent(
                    state.topic,
                    contentsOpen = contentsOpen,
                    onContentsOpen = { contentsOpen = it },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicContent(
    topic: RulesTopic,
    contentsOpen: Boolean = false,
    onContentsOpen: (Boolean) -> Unit = {},
) {
    val colors = GoaTheme.colors
    val pages = "Rulebook, 2nd printing · ${RulesText.pages(topic.pages)} · word for word"
    val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    // Sideways or with large text, height is scarce and the band would wrap: its words end the page instead.
    val plainFoot = large || LocalConfiguration.current.screenHeightDp < BANNER_FOOT_MIN_HEIGHT
    val sections = remember(topic) { topicSections(topic) }
    // A long topic with diagrams shows them (and where its steps are) before the first passage.
    val anchors = remember(topic, sections) { topicAnchors(topic)?.takeIf { sections.isNotEmpty() } }
    val showTitle = bannerTitle(topic.title, large) != topic.title
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Items before the first passage: the title, the navigator, the summary.
    val lead = (if (showTitle) 1 else 0) + (if (sections.isNotEmpty()) 1 else 0) +
        (if (topic.summary != null) 1 else 0) + (if (anchors != null) 1 else 0)
    val navIndex = if (showTitle) 1 else 0
    val navHeight = with(LocalDensity.current) { NAV_HEIGHT.roundToPx() }
    val active by remember(sections, lead) {
        derivedStateOf {
            val info = list.layoutInfo
            val top = info.visibleItemsInfo
                .firstOrNull { it.key != NAV_KEY && it.offset + it.size > navHeight }?.index ?: 0
            val atEnd = !list.canScrollForward && list.canScrollBackward
            val excerpt = if (atEnd) topic.excerpts.lastIndex else top - lead
            // Above the first passage (the title, the summary) the reader is at the first section.
            sections.lastOrNull { it.excerpt <= excerpt } ?: sections.firstOrNull()
        }
    }
    val openExcerpt: (Int) -> Unit = { excerpt ->
        scope.launch { list.animateScrollToItem(lead + excerpt, -navHeight) }
    }
    val open: (TopicSection) -> Unit = { section -> openExcerpt(section.excerpt) }
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
                    TopicNavigator(sections, active, onSelect = open, onSections = { onContentsOpen(true) })
                }
            }
            topic.summary?.let { summary -> item(key = "summary") { SummaryNote(summary, margin) } }
            anchors?.let { a -> item(key = "anchors") { TopicAnchorRow(a, margin, onOpen = openExcerpt) } }
            itemsIndexed(topic.excerpts, key = { i, _ -> "excerpt-$i" }) { i, excerpt ->
                Column(margin, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    if (i > 0 && excerpt.heading != null) HorizontalDivider(color = colors.pageEdge)
                    val diagram = remember(excerpt) { RulesDiagrams.of(excerpt) }
                    if (diagram != null) DiagramWithCaption(diagram, excerpt) else ExcerptBlock(excerpt)
                }
            }
            // The foot names the source once: the band, or this line where the band won't fit.
            if (plainFoot) {
                item(key = "source") {
                    Text(
                        pages,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPageVariant,
                        modifier = margin.padding(top = Spacing.sm),
                    )
                }
            }
        }
        if (!plainFoot) RulesFooter(pages)
    }
    if (contentsOpen) {
        GoaSheet(onDismissRequest = { onContentsOpen(false) }) {
            TopicContents(sections, active) { section ->
                onContentsOpen(false)
                open(section)
            }
        }
    }
}

/**
 * The whole title, only when the banner holds just its head: Cinzel in the page's ink, a step under the passages'
 * heads, so the rules still start on the first screen.
 */
@Composable
private fun TopicTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = GoaTextStyles.sectionHead.copy(fontSize = 20.sp, lineHeight = 24.sp),
        color = GoaTheme.colors.onPage,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * The topic's parts, held at the top of the page as you read. First a labelled **Sections** button that opens a sheet
 * of every part by its whole name (so a later part is never found by scrolling sideways through cut labels), then the
 * rulebook's own heads (and "Diagrams") as shortcuts, a tap opening that passage. The one you are reading is bronze,
 * as everything selected is (and filled, not only coloured), and stays in view as you read. The shortcuts scroll
 * sideways when they don't fit, the edge that hides more fading out (the design system's edge fade).
 */
@Composable
private fun TopicNavigator(
    sections: List<TopicSection>,
    active: TopicSection?,
    onSelect: (TopicSection) -> Unit,
    onSections: () -> Unit,
) {
    val colors = GoaTheme.colors
    val row = rememberScrollState()
    val density = LocalDensity.current
    val starts = remember(sections) { mutableStateMapOf<Int, Int>() }
    // The chip being read stays in view: scrolled to just past the gutter whenever the reader moves on.
    LaunchedEffect(active, row.maxValue) {
        val x = active?.let { starts[it.excerpt] } ?: return@LaunchedEffect
        val lead = with(density) { (Spacing.xs + Spacing.lg).roundToPx() }
        val target = (x - lead).coerceIn(0, row.maxValue)
        if (row.value != target) row.animateScrollTo(target)
    }
    Column(Modifier.fillMaxWidth().background(colors.page)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            SectionsButton(sections.size, Modifier.padding(start = PAGE_MARGIN - Spacing.xs), onSections)
            Box(
                Modifier
                    .padding(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(colors.pageEdge),
            )
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = NAV_HEIGHT)
                    .fadingHorizontalScroll(row, gutter = Spacing.xs)
                    .semantics { contentDescription = "Section shortcuts" },
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                sections.forEach { section ->
                    SectionChip(
                        section.label,
                        selected = section == active,
                        modifier = Modifier.onPlaced { starts[section.excerpt] = it.positionInParent().x.toInt() },
                    ) { onSelect(section) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.pageEdge))
    }
}

/**
 * "Sections" in words with the list glyph: opens the sheet of every part by its whole name. 48 dp tall, the page's
 * ink on the page, the same edge as an unselected shortcut; TalkBack hears "Sections, 5 parts, button".
 */
@Composable
private fun SectionsButton(count: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = GoaTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .heightIn(min = NAV_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$SECTIONS, $count parts"
                role = Role.Button
                onClick(label = "List all sections") {
                    onClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(shape)
                .border(1.dp, colors.pageEdge, shape)
                .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.List,
                contentDescription = null,
                tint = colors.onPage,
                modifier = Modifier.size(scaledIconSize(18.dp, 1.4f)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                SECTIONS,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPage,
                maxLines = 1,
            )
        }
    }
}

/** Every part of a topic, the one being read marked as selected; a tap closes the list and opens that part. */
@Composable
internal fun TopicContents(sections: List<TopicSection>, active: TopicSection?, onSelect: (TopicSection) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            SECTIONS,
            style = GoaTextStyles.sectionHead.copy(fontSize = 22.sp, lineHeight = 28.sp),
            color = GoaTheme.colors.heading,
            modifier = Modifier.semantics { heading() },
        )
        GoaPlate(Modifier.fillMaxWidth().selectableGroup(), contentPadding = PaddingValues(0.dp)) {
            sections.forEachIndexed { i, section ->
                if (i > 0) PlateRowDivider()
                SelectablePlate(
                    selected = section == active,
                    onClick = { onSelect(section) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(section.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = GoaTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .heightIn(min = NAV_HEIGHT)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            // Selected, dark ink on the bronze in both themes (about 7:1); otherwise the page's own ink.
            color = if (selected) CHIP_ON_BRONZE else colors.onPage,
            maxLines = 1,
            modifier = Modifier
                .clip(shape)
                .then(if (selected) Modifier.background(CHIP_BRONZE, shape) else Modifier)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) CHIP_BRONZE_EDGE else colors.pageEdge, shape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/**
 * A diagram the book prints, over its printed caption (in italics, as printed). On the light page its white page is
 * multiplied into the parchment so it lies on our page as on the book's; on the dark page it is a framed plate in a
 * muted parchment, so the picture keeps its colours without flashing a white page. Decorative for TalkBack: the
 * caption says what it shows. Space stays reserved while it loads.
 */
@Composable
private fun DiagramWithCaption(diagram: RulesDiagrams.Entry, caption: RulesExcerpt) {
    val image = rememberOfficialBitmap(diagram.art)
    val dark = GoaTheme.isDark
    val page = if (dark) DIAGRAM_DARK_PAPER else GoaTheme.colors.page
    val frameShape = RoundedCornerShape(6.dp)
    val frame = if (dark) {
        Modifier.clip(frameShape).border(1.dp, GoaTheme.colors.pageEdge, frameShape).background(page)
    } else {
        Modifier
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.fillMaxWidth().then(frame).aspectRatio(diagram.aspect)) {
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

/**
 * Near the top of a long topic with diagrams: "Steps" (when a passage prints numbered steps) and each diagram as a
 * thumbnail, every one a tap from its place on the page, so the most glanceable part is found without scrolling past
 * the passages. Only pointers: the words are the rulebook's, further down.
 */
@Composable
private fun TopicAnchorRow(anchors: TopicAnchors, modifier: Modifier, onOpen: (Int) -> Unit) {
    val colors = GoaTheme.colors
    val shape = RoundedCornerShape(6.dp)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        anchors.steps?.let { steps ->
            Column(
                Modifier
                    .widthIn(min = 76.dp)
                    .fillMaxHeight()
                    .clip(shape)
                    .border(1.dp, colors.pageEdge, shape)
                    .clickable(onClickLabel = "Open the steps", role = Role.Button) { onOpen(steps) }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.FormatListNumbered,
                    contentDescription = null,
                    tint = colors.onPage,
                    modifier = Modifier.size(scaledIconSize(24.dp, 1.4f)),
                )
                Text(STEPS, style = MaterialTheme.typography.titleSmall, color = colors.onPage)
            }
        }
        anchors.diagrams.forEachIndexed { n, (excerpt, diagram) ->
            DiagramThumb(
                diagram,
                label = "$DIAGRAMS: ${n + 1} of ${anchors.diagrams.size}",
                modifier = Modifier.weight(1f),
            ) { onOpen(excerpt) }
        }
    }
}

/** A diagram small, framed as on its page, opening the full one. TalkBack: "Diagrams: 1 of 2", a button. */
@Composable
private fun DiagramThumb(diagram: RulesDiagrams.Entry, label: String, modifier: Modifier, onClick: () -> Unit) {
    val image = rememberOfficialBitmap(diagram.art)
    val dark = GoaTheme.isDark
    val page = if (dark) DIAGRAM_DARK_PAPER else GoaTheme.colors.page
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .aspectRatio(diagram.aspect)
            .clip(shape)
            .border(1.dp, GoaTheme.colors.pageEdge, shape)
            .background(page)
            .clickable(onClickLabel = "Open the diagram", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
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
            .padding(start = Spacing.md, end = bannerEndWidth() + Spacing.sm, top = 10.dp, bottom = 10.dp),
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

/** The navigator's button and the sheet it opens: every part of the topic by its whole name. */
internal const val SECTIONS = "Sections"
private val NAV_HEIGHT = 48.dp

/** Bronze on the page, the same in both themes: the dark ink on it is about 7:1, and it stands out on either page. */
private val CHIP_BRONZE = Color(0xFFC9A35B)
private val CHIP_BRONZE_EDGE = Color(0xFF84662A)
private val CHIP_ON_BRONZE = Color(0xFF231C14)

/** The diagram's paper on the dark page: a muted parchment, well below the light page's brightness. */
private val DIAGRAM_DARK_PAPER = Color(0xFFB3AA96)

private const val DIAGRAM_EDGE = 0.015f
private val PAGE_MARGIN = 18.dp
private val FOOTER_HEIGHT = 42.dp

/** Below this height (dp) the foot band gives its room back to the text. */
private const val BANNER_FOOT_MIN_HEIGHT = 480
