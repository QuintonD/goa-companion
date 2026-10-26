package dev.quinton.goa.feature.board.ui.cards

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.feature.board.play.table.CARD_ASPECT
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.spokenCard
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The card focus viewer (docs/DESIGN.md, "Card focus"): while [state] has cards open, the card on
 * show grows out of its thumbnail into a large card over a dimmed scrim, with its values, text and
 * the question's context beside or under it.
 * - **Swipe sideways** for the other cards (the neighbours peek at the edges; tap one to go to it).
 * - **Tap the focused card again** to confirm it (when opened with a [CardSelection]): it presses,
 *   settles with a tick, the phone gives a confirm tick, then the choice is made. The Confirm
 *   button under it does the same (TalkBack: the card's "Select" action).
 * - **Back** (the edge swipe, shown as the card shrinking while you drag), an **upward swipe** on
 *   the card, the scrim or × close it with nothing chosen.
 *
 * It is its own full-screen window, so it shows above sheets too; place it once per screen.
 */
@Composable
fun CardFocusHost(state: CardFocusState) {
    val request = state.request ?: return
    // The window's own density would drop a font scale set by the screen (screenshots at scale 2).
    val density = LocalDensity.current
    Dialog(
        onDismissRequest = { state.close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        // The scrim is drawn (and animated) here, not by the window.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setWindowAnimations(0)
        }
        CompositionLocalProvider(LocalDensity provides density) {
            key(request.id) { FocusOverlay(state, request) }
        }
    }
}

/** Light text on the scrim, the same in both themes (the scrim is always dark). */
private val OnScrim = Color(0xFFE8E2D6)
private val ScrimColor = Color(0xFF04080D)

/** The dark theme's bronze: the light theme's is too dark on the scrim. */
private val BronzeOnScrim = Color(0xFFC9A35B)

@Composable
private fun FocusOverlay(state: CardFocusState, request: CardFocusRequest) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val pager = rememberPagerState(initialPage = request.initial) { request.cards.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { state.index = it } }

    val motion = remember { FocusMotion(state) }
    LaunchedEffect(Unit) {
        motion.reveal.animateTo(1f, tween(Motion.LONG_MS + 40, easing = Motion.EmphasizedDecelerate))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(state.closing) {
            if (state.closing) {
                motion.exit(state.exit, heightPx)
                state.finish()
            }
        }
        PredictiveBackHandler(enabled = !state.closing) { events ->
            try {
                events.collect { e ->
                    motion.back = e.progress
                    motion.backEdge = e.swipeEdge
                }
                state.close()
            } catch (e: CancellationException) {
                scope.launch { animate(motion.back, 0f) { v, _ -> motion.back = v } }
                throw e
            }
        }

        val confirm: () -> Unit = confirm@{
            val selection = request.selection ?: return@confirm
            val shown = request.cards.getOrNull(state.index) ?: return@confirm
            if (state.closing || motion.confirming) return@confirm
            if (!shown.enabled || !request.canConfirm) {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                scope.launch { motion.shake() }
                return@confirm
            }
            motion.confirming = true
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            val chosen = state.index
            scope.launch {
                motion.press()
                selection.onConfirm(chosen)
                state.confirmed()
            }
        }
        val step: (Int) -> Unit = { delta ->
            val to = (pager.currentPage + delta).coerceIn(0, request.cards.lastIndex)
            scope.launch { pager.animateScrollToPage(to) }
        }
        val goTo: (Int) -> Unit = { to ->
            scope.launch { pager.animateScrollToPage(to.coerceIn(0, request.cards.lastIndex)) }
        }
        val actions = FocusActions(confirm = confirm, step = step, close = { state.close() }, goTo = goTo)

        // The scrim: tap to close (TalkBack has × instead).
        val scrimAlpha = SCRIM_ALPHA * motion.reveal.value * (1f - motion.back * 0.35f) *
            (1f - (-motion.drag / heightPx).coerceIn(0f, 1f) * 0.7f)
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics { testTag = SCRIM_TAG }
                .background(ScrimColor.copy(alpha = scrimAlpha))
                .clickable(remember { MutableInteractionSource() }, indication = null) { state.close() },
        )
        // The screen's top bar sits right under the viewer's header: the scrim turns opaque there,
        // fading out below the header, so its icons don't show through behind the title.
        val topShade = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() } + TOP_BAR + Spacing.lg
        Box(
            Modifier
                .fillMaxWidth()
                .height(topShade)
                .clearAndSetSemantics {}
                .background(
                    Brush.verticalGradient(
                        0f to ScrimColor.copy(alpha = scrimAlpha / SCRIM_ALPHA),
                        TOP_SHADE_SOLID to ScrimColor.copy(alpha = scrimAlpha / SCRIM_ALPHA),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            FocusLayout(state, request, pager, motion, actions)
        }
    }
}

/** What the viewer's parts can do. */
private class FocusActions(
    val confirm: () -> Unit,
    val step: (Int) -> Unit,
    val close: () -> Unit,
    val goTo: (Int) -> Unit,
)

/** Every animated value of the viewer. */
private class FocusMotion(private val state: CardFocusState) {
    /** 0 = in the thumbnail (or gone), 1 = focused. */
    val reveal = Animatable(0f)

    /** The confirm press and settle (a scale). */
    val press = Animatable(1f)

    /** The tick that settles on a confirmed card. */
    val seal = Animatable(0f)

    /** A refused tap (a card that can't be chosen) shakes it. */
    val shakeX = Animatable(0f)
    var drag by mutableFloatStateOf(0f)

    // The predictive back gesture's progress lives in the state (tests read it).
    var back: Float
        get() = state.backProgress
        set(value) {
            state.backProgress = value
        }
    var backEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    var confirming by mutableStateOf(false)
    var exitKind by mutableStateOf<FocusExit?>(null)

    /** Where the card on show is laid out (window coordinates), to grow out of its thumbnail. */
    var target by mutableStateOf<Rect?>(null)

    suspend fun press() = coroutineScope {
        press.animateTo(PRESSED, tween(PRESS_MS))
        launch { seal.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        press.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    suspend fun shake() {
        listOf(1f, -1f, 0.6f, -0.4f, 0f).forEach { shakeX.animateTo(it, tween(SHAKE_MS)) }
    }

    suspend fun exit(how: FocusExit, heightPx: Float) = coroutineScope {
        exitKind = how
        launch { animate(back, 0f, animationSpec = tween(Motion.MEDIUM_MS)) { v, _ -> back = v } }
        when (how) {
            FocusExit.SHRINK -> reveal.animateTo(0f, tween(Motion.MEDIUM_MS, easing = Motion.EmphasizedAccelerate))

            FocusExit.FLY_UP -> {
                launch {
                    animate(
                        drag,
                        -heightPx,
                        animationSpec = tween(Motion.MEDIUM_MS, easing = Motion.EmphasizedAccelerate),
                    ) {
                            v,
                            _,
                        ->
                        drag = v
                    }
                }
                reveal.animateTo(0f, tween(Motion.MEDIUM_MS))
            }

            FocusExit.CONFIRM -> reveal.animateTo(0f, tween(Motion.SHORT_MS, easing = Motion.EmphasizedAccelerate))
        }
    }
}

@Composable
private fun FocusLayout(
    state: CardFocusState,
    request: CardFocusRequest,
    pager: PagerState,
    motion: FocusMotion,
    actions: FocusActions,
) {
    val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    // Spell cards are landscape (CardShape.kt): each card keeps its own aspect inside the stage.
    val aspects = remember(request) { request.cards.map { cardAspect(it.card) } }
    val wide = aspects.any { it > 1f }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val details: @Composable (Modifier) -> Unit = { m ->
            FocusDetails(request, state.index, motion.reveal.value, actions.confirm, actions.goTo, landscape, m)
        }
        if (landscape) {
            val share = if (wide) LANDSCAPE_WIDE_CARD_SHARE else LANDSCAPE_CARD_SHARE
            val frame = stageFrame(aspects, maxWidth * share, maxHeight - Spacing.lg * 2)
            val stage = frame.width + PEEK * 2
            Row(Modifier.fillMaxSize()) {
                CardStage(state, request, pager, motion, actions, frame, Modifier.width(stage).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().padding(end = Spacing.md)) {
                    // The height is what the words lack here: a 48 dp header, the panel as tall as its
                    // words (up to the bottom edge), at the bottom where the thumb is.
                    TopBar(request, state.index, actions.close, motion.reveal.value, height = TOP_BAR_LANDSCAPE)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        details(Modifier.fillMaxWidth().padding(bottom = Spacing.sm))
                    }
                }
            }
        } else {
            // Read as text, the words get the room: the card steps back (still tappable, still swipes).
            val share = when {
                request.asText -> if (large) PORTRAIT_TEXT_CARD_SHARE_LARGE else PORTRAIT_TEXT_CARD_SHARE
                large -> PORTRAIT_CARD_SHARE_LARGE
                else -> PORTRAIT_CARD_SHARE
            }
            val frame = stageFrame(aspects, maxWidth - PEEK * 2, (maxHeight - TOP_BAR) * share)
            Column(Modifier.fillMaxSize()) {
                TopBar(request, state.index, actions.close, motion.reveal.value)
                CardStage(
                    state,
                    request,
                    pager,
                    motion,
                    actions,
                    frame,
                    Modifier.fillMaxWidth().height(frame.height + Spacing.lg),
                )
                // Only what the card doesn't print is under it (ADR-041): a sheet as tall as that, at the bottom.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    details(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * The stage's page: as wide as the widest card and as tall as the tallest, each card the largest it
 * can be inside [maxWidth] × [maxHeight] at its own aspect (a portrait deck card, a landscape spell).
 */
private fun stageFrame(aspects: List<Float>, maxWidth: Dp, maxHeight: Dp): DpSize {
    val sizes = aspects.ifEmpty { listOf(CARD_ASPECT) }.map { fitCard(it, maxWidth, maxHeight) }
    return DpSize(sizes.maxOf { it.width }, sizes.maxOf { it.height })
}

/** × to close, what is asked (or the viewer's title) and "Card 2 of 4", on the scrim. */
@Composable
private fun TopBar(request: CardFocusRequest, index: Int, onClose: () -> Unit, reveal: Float, height: Dp = TOP_BAR) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = reveal },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(contentColor = OnScrim),
            modifier = Modifier.testTag(CLOSE_TAG),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Close")
        }
        Column(Modifier.weight(1f)) {
            request.title?.let {
                Text(
                    it.uppercase(),
                    style = GoaTextStyles.overline,
                    color = BronzeOnScrim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (request.cards.size > 1) {
                Text(
                    "Card ${index + 1} of ${request.cards.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnScrim,
                    maxLines = 1,
                )
            }
        }
        if (request.cards.size > 1) PageDots(request.cards.size, index)
        Spacer(Modifier.width(Spacing.lg))
    }
}

/** One dot per card, the shown one long and bronze; up to [MAX_DOTS]. */
@Composable
private fun PageDots(count: Int, index: Int) {
    if (count > MAX_DOTS) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics {}) {
        repeat(count) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == index) 16.dp else 6.dp)
                    .background(
                        if (i == index) BronzeOnScrim else OnScrim.copy(alpha = 0.45f),
                        CircleShape,
                    ),
            )
        }
    }
}

/** The pager of large cards: swipe sideways, tap the focused one to confirm, swipe up to close. */
@Composable
private fun CardStage(
    state: CardFocusState,
    request: CardFocusRequest,
    pager: PagerState,
    motion: FocusMotion,
    actions: FocusActions,
    frame: DpSize,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val flyPx = with(density) { FLY_DISTANCE.toPx() }
    val flingPx = with(density) { FLY_VELOCITY.toPx() }
    BoxWithConstraints(
        modifier.draggable(
            rememberDraggableState { delta ->
                // Up follows the finger; down only gives a little (it closes upward).
                motion.drag = (motion.drag + if (delta > 0 && motion.drag >= 0f) delta * DOWN_RESIST else delta)
            },
            Orientation.Vertical,
            enabled = !state.closing && !motion.confirming,
            onDragStopped = { velocity ->
                if (motion.drag < -flyPx || velocity < -flingPx) {
                    state.flyAway()
                } else {
                    animate(motion.drag, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                        motion.drag = v
                    }
                }
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        val side = ((maxWidth - frame.width) / 2).coerceAtLeast(0.dp)
        HorizontalPager(
            state = pager,
            key = { request.cards[it].key },
            contentPadding = PaddingValues(horizontal = side),
            pageSpacing = PAGE_GAP,
            beyondViewportPageCount = 1,
            userScrollEnabled = !state.closing && !motion.confirming,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Box(Modifier.size(frame), contentAlignment = Alignment.Center) {
                val item = request.cards[page]
                val size = fitCard(cardAspect(item.card), frame.width, frame.height)
                FocusPage(state, request, pager, page, motion, actions, size)
            }
        }
    }
}

@Composable
private fun FocusPage(
    state: CardFocusState,
    request: CardFocusRequest,
    pager: PagerState,
    page: Int,
    motion: FocusMotion,
    actions: FocusActions,
    size: DpSize,
) {
    val item = request.cards[page]
    val current = page == state.index
    val selectable = request.selection != null
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current
    val shakePx = with(density) { SHAKE.toPx() }
    val backShiftPx = with(density) { BACK_SHIFT.toPx() }
    val semantics = if (current) {
        Modifier.clearAndSetSemantics {
            testTag = CARD_TAG
            // The scan is the card's words now (the sheet under it no longer retypes them): TalkBack
            // reads its data and text from here.
            contentDescription = listOfNotNull(
                spokenCard(item.card),
                item.caption,
                CardText.plain(item.card.text).takeIf { it.isNotBlank() },
            ).joinToString(", ")
            stateDescription = listOfNotNull(
                item.verdict?.label,
                if (!item.enabled) item.reason ?: "Can't be chosen" else null,
                "card ${page + 1} of ${request.cards.size}",
            ).joinToString(", ")
            val selection = request.selection
            if (selection != null && item.enabled) {
                onClick(label = selection.action(item.card)) {
                    actions.confirm()
                    true
                }
            }
            customActions = buildList {
                if (selection != null && item.enabled) {
                    add(
                        CustomAccessibilityAction("Select") {
                            actions.confirm()
                            true
                        },
                    )
                }
                if (page < request.cards.lastIndex) {
                    add(
                        CustomAccessibilityAction("Next card") {
                            actions.step(1)
                            true
                        },
                    )
                }
                if (page > 0) {
                    add(
                        CustomAccessibilityAction("Previous card") {
                            actions.step(-1)
                            true
                        },
                    )
                }
                answerIndex(request, item)?.let { to ->
                    add(
                        CustomAccessibilityAction("Go to ${request.cards[to].card.name}") {
                            actions.goTo(to)
                            true
                        },
                    )
                }
                add(
                    CustomAccessibilityAction("Close") {
                        actions.close()
                        true
                    },
                )
            }
        }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Box(
        Modifier
            .width(size.width)
            .aspectRatio(cardAspect(item.card))
            .then(semantics)
            .onGloballyPositioned { if (page == state.index) motion.target = it.boundsInWindow() }
            .clickable(interaction, indication = null) {
                if (page == state.index) {
                    if (selectable) actions.confirm()
                } else {
                    actions.step(page - state.index)
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = motion.reveal.value
                    val offset = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin.Center
                    shape = RoundedCornerShape(min(size.width, size.height) * CORNER)
                    clip = true
                    if (page == state.index) {
                        val grow = motion.exitKind == null || motion.exitKind == FocusExit.SHRINK
                        val origin = state.origins[item.key]?.takeIf { grow }
                        val target = motion.target?.takeIf { it.width > 0f }
                        var scale: Float
                        if (origin != null && target != null) {
                            scale = lerp(origin.width / target.width, 1f, t)
                            translationX = (origin.center.x - target.center.x) * (1f - t)
                            translationY = (origin.center.y - target.center.y) * (1f - t)
                            alpha = if (t > 0f || !state.closing) 1f else 0f
                        } else {
                            scale = lerp(if (motion.exitKind == FocusExit.FLY_UP) 1f else ENTER_SCALE, 1f, t)
                            alpha = t
                        }
                        scale *=
                            motion.press.value * (1f - motion.back * BACK_SHRINK) *
                            if (pressed && selectable) 0.98f else 1f
                        scaleX = scale
                        scaleY = scale
                        val edge = if (motion.backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                        translationX += motion.shakeX.value * shakePx + edge * backShiftPx * motion.back
                        translationY += motion.drag
                        shadowElevation = 24.dp.toPx() * t
                    } else {
                        val s = lerp(1f, NEIGHBOUR_SCALE, offset)
                        scaleX = s
                        scaleY = s
                        alpha = t * lerp(1f, NEIGHBOUR_ALPHA, offset)
                    }
                },
        ) {
            // TableCard's width is a portrait card's; a landscape spell keeps the height (CardShape.kt).
            TableCard(item.card, width = if (item.card.isSpell) size.height * CARD_ASPECT else size.width)
            // A card an answer leads to (an upgrade card) is shown as it is, not veiled.
            if (!item.enabled && item.answeredBy == null) NotAnOption(Modifier.align(Alignment.Center))
            item.verdict?.let { VerdictBadge(it, Modifier.align(Alignment.TopEnd).padding(Spacing.sm)) }
            if (current && motion.seal.value > 0f) ConfirmSeal(motion.seal.value, Modifier.align(Alignment.Center))
        }
    }
}

/** A card that can't be chosen: veiled, with a "Not an option" pill. */
@Composable
private fun NotAnOption(modifier: Modifier) {
    Box(Modifier.fillMaxSize().background(ScrimColor.copy(alpha = 0.5f)))
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Block, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text("Not an option", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "✓ Holds" / "! Falls" on the card's corner: word and shape, never colour alone. */
@Composable
internal fun VerdictBadge(verdict: CardVerdict, modifier: Modifier = Modifier) {
    val color = when (verdict.holds) {
        true -> MaterialTheme.colorScheme.secondary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (verdict.holds != null) {
                Icon(
                    if (verdict.holds) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    null,
                    Modifier.size(16.dp),
                    tint = color,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                verdict.label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The tick that settles on a confirmed card. */
@Composable
private fun ConfirmSeal(progress: Float, modifier: Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondary,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = progress
                scaleY = progress
                alpha = progress.coerceIn(0f, 1f)
            }
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, "Chosen", Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondary)
        }
    }
}

internal const val CARD_TAG = "cardFocus:card"
internal const val SCRIM_TAG = "cardFocus:scrim"
internal const val CLOSE_TAG = "cardFocus:close"
internal const val CONFIRM_TAG = "cardFocus:confirm"

/** The "Go to <card>" button of a card an answer leads to (an upgrade card). */
internal const val GO_TO_ANSWER_TAG = "cardFocus:goToAnswer"

private const val SCRIM_ALPHA = 0.93f
private const val PORTRAIT_CARD_SHARE = 0.6f
private const val PORTRAIT_CARD_SHARE_LARGE = 0.5f
private const val PORTRAIT_TEXT_CARD_SHARE = 0.36f
private const val PORTRAIT_TEXT_CARD_SHARE_LARGE = 0.26f
private const val LANDSCAPE_CARD_SHARE = 0.4f

/** A landscape spell card takes more of a landscape screen's width (it is short: room is left above). */
private const val LANDSCAPE_WIDE_CARD_SHARE = 0.55f
private const val ENTER_SCALE = 0.86f
private const val NEIGHBOUR_SCALE = 0.93f
private const val NEIGHBOUR_ALPHA = 0.55f
private const val BACK_SHRINK = 0.14f
private const val DOWN_RESIST = 0.3f
private const val PRESSED = 0.93f
private const val PRESS_MS = 90
private const val SHAKE_MS = 45
private const val CORNER = 0.05f
private const val MAX_DOTS = 8
private val TOP_BAR = 56.dp
private val TOP_BAR_LANDSCAPE = 48.dp

/** The share of the top shade that is fully opaque (the header), before it fades out. */
private const val TOP_SHADE_SOLID = 0.7f
private val PEEK = 36.dp
private val PAGE_GAP = 10.dp
private val FLY_DISTANCE = 96.dp
private val FLY_VELOCITY = 900.dp
private val SHAKE = 10.dp
private val BACK_SHIFT = 18.dp
