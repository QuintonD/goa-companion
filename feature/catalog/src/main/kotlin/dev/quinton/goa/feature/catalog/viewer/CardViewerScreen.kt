package dev.quinton.goa.feature.catalog.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.assetUrl
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.catalog.detail.printedValues
import dev.quinton.goa.feature.catalog.ui.CardTextView
import dev.quinton.goa.feature.catalog.ui.GameIcon
import dev.quinton.goa.feature.catalog.ui.GameIcons
import dev.quinton.goa.feature.catalog.ui.kindLabel
import dev.quinton.goa.feature.catalog.ui.label
import kotlinx.coroutines.launch

/** Official card image (or our designed card) per page; swipe between the hero's cards. */
@Composable
fun CardViewerScreen(state: CardViewerUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var textOnly by rememberSaveable { mutableStateOf(false) }
    when (state) {
        CardViewerUiState.Loading -> GoaScreen(title = "", onBack = onBack, modifier = modifier) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading card" })
            }
        }

        CardViewerUiState.NotFound -> GoaScreen(title = "Card", onBack = onBack, modifier = modifier) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    title = "Card not found",
                    message = "This card isn't in the catalog bundled with this build.",
                )
            }
        }

        is CardViewerUiState.Ready -> {
            val pager = rememberPagerState(initialPage = state.initialIndex) { state.cards.size }
            val current = state.cards.getOrNull(pager.currentPage) ?: state.cards.first()
            GoaScreen(
                title = current.name,
                onBack = onBack,
                modifier = modifier,
                actions = {
                    if (current.image != null) {
                        TextModeToggle(textOnly) { textOnly = !textOnly }
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    PagerHeader(state, pager)
                    CardPager(state, pager, textOnly)
                }
            }
        }
    }
}

@Composable
private fun TextModeToggle(textOnly: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (textOnly) Icons.Outlined.Image else Icons.AutoMirrored.Outlined.Article,
            contentDescription = if (textOnly) "Show card image" else "Show text only",
        )
    }
}

@Composable
private fun PagerHeader(state: CardViewerUiState.Ready, pager: PagerState) {
    val scope = rememberCoroutineScope()
    val index = pager.currentPage
    val card = state.cards.getOrNull(index) ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { scope.launch { pager.animateScrollToPage(index - 1) } }, enabled = index > 0) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous card")
        }
        Column(
            Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                card.kindLabel(state.isHandicap(card)),
                style = MaterialTheme.typography.labelLarge,
                color = cardColor(card.color),
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${state.hero.name} · ${index + 1} of ${state.cards.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { scope.launch { pager.animateScrollToPage(index + 1) } },
            enabled = index < state.cards.lastIndex,
        ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next card")
        }
    }
}

@Composable
private fun CardPager(state: CardViewerUiState.Ready, pager: PagerState, textOnly: Boolean) {
    val scope = rememberCoroutineScope()
    val zoomStates = remember(state.cards) { state.cards.associate { it.id to ZoomState() } }
    val currentZoom = zoomStates[state.cards.getOrNull(pager.currentPage)?.id]
    LaunchedEffect(pager.currentPage) { zoomStates.values.forEach { it.reset() } }
    HorizontalPager(
        state = pager,
        key = { state.cards[it].id },
        userScrollEnabled = currentZoom?.isZoomed != true,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize().semantics {
            customActions = listOf(
                CustomAccessibilityAction("Next card") {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    true
                },
                CustomAccessibilityAction("Previous card") {
                    scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                    true
                },
            )
        },
    ) { page ->
        val card = state.cards[page]
        CardPage(
            card = card,
            isHandicap = state.isHandicap(card),
            textOnly = textOnly,
            zoom = zoomStates.getValue(card.id),
        )
    }
}

@Composable
private fun CardPage(card: Card, isHandicap: Boolean, textOnly: Boolean, zoom: ZoomState) {
    val hasImage = card.image != null
    var imageFailed by remember(card.id) { mutableStateOf(false) }
    val showsDesigned = !hasImage || imageFailed
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = !zoom.isZoomed)
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = !textOnly || showsDesigned,
            enter = fadeIn(tween(MOTION_MS)) + expandVertically(tween(MOTION_MS)),
            exit = fadeOut(tween(MOTION_MS)) + shrinkVertically(tween(MOTION_MS)),
        ) {
            Box(Modifier.widthIn(max = MAX_CARD_WIDTH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showsDesigned) {
                    DesignedCard(card, isHandicap, Modifier.fillMaxWidth())
                } else {
                    OfficialCardImage(card, zoom, onError = { imageFailed = true })
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        CardDetails(card, includeText = !showsDesigned, modifier = Modifier.widthIn(max = MAX_CARD_WIDTH))
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun OfficialCardImage(card: Card, zoom: ZoomState, onError: () -> Unit) {
    val path = card.image ?: return
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT)
            .clip(RoundedCornerShape(16.dp))
            .zoomable(zoom),
    ) {
        SubcomposeAsyncImage(
            model = assetUrl(path),
            contentDescription = "Card image: ${card.name}. Pinch or double-tap to zoom.",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            },
            error = { LaunchedEffect(Unit) { onError() } },
        )
        if (!zoom.isZoomed) {
            Icon(
                Icons.Outlined.ZoomOutMap,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.sm)
                    .size(20.dp),
            )
        }
    }
}

/** Structured card info under the image: actions, values, markers and (optionally) the text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetails(card: Card, includeText: Boolean, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    val accent = cardColor(card.color)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, GoaTheme.colors.engravedBorder, shape)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DetailRow("Initiative") {
            Text(card.initiative?.toString() ?: "—", style = MaterialTheme.typography.titleMedium)
        }
        card.primary?.let { primary ->
            DetailRow("Primary action") { IconLabel(GameIcons.forPrimary(primary), accent) }
        }
        if (card.secondary.isNotEmpty()) {
            DetailRow("Secondary") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    card.secondary.forEach {
                        IconLabel(GameIcons.forSecondary(it), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val values = card.printedValues()
        if (values.isNotEmpty()) {
            DetailRow("Values") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    values.forEach {
                        IconLabel(
                            it.icon,
                            accent,
                            "${it.icon.label} ${it.printed}",
                            Modifier.clearAndSetSemantics { contentDescription = it.spoken },
                        )
                    }
                }
            }
        }
        val markers = listOfNotNull(
            GameIcons.ranged.takeIf { card.ranged },
            GameIcons.block.takeIf { card.block },
        )
        if (markers.isNotEmpty()) {
            DetailRow("Markers") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    markers.forEach { IconLabel(it, MaterialTheme.colorScheme.secondary) }
                }
            }
        }
        card.item?.let { stat ->
            DetailRow("Item") { IconLabel(GameIcons.item, MaterialTheme.colorScheme.secondary, "+1 ${stat.label}") }
        }
        if (includeText && card.text.isNotBlank()) {
            CardTextView(card.text, iconTint = accent, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

@Composable
private fun DetailRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Box(Modifier.weight(0.6f)) { content() }
    }
}

@Composable
private fun IconLabel(
    icon: GameIcon,
    tint: androidx.compose.ui.graphics.Color,
    text: String = icon.label,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon.vector, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val CARD_ASPECT = 729f / 1026f
private val MAX_CARD_WIDTH = 480.dp
private const val MOTION_MS = 250
