package dev.quinton.goa.feature.board.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Stat

/**
 * One card in the focused viewer. [enabled] false = shown but marked, and it can't be confirmed;
 * [reason] says why (shown under the card). [caption] is a short context line ("Turn 2 · Active"),
 * [verdict] a defense card's Holds / Falls against the Attack, [notes] what choosing it means
 * ("Magnetic Dagger goes to your hand"), [related] other cards that come with it (the two of the
 * next Tier when upgrading) under [relatedLabel]. [key] matches the thumbnail's
 * [Modifier.cardFocusOrigin], so the card grows out of it and shrinks back into it.
 */
@Immutable
data class FocusCard(
    val card: Card,
    val key: String = card.id,
    val enabled: Boolean = true,
    val reason: String? = null,
    val caption: String? = null,
    val verdict: CardVerdict? = null,
    val notes: List<CardNote> = emptyList(),
    val related: List<Card> = emptyList(),
    val relatedLabel: String? = null,
)

/** A defense card against the Attack (p.14): "Holds" / "Falls" / "Decided by its text", and why. */
@Immutable
data class CardVerdict(val holds: Boolean?, val label: String, val detail: String)

/** A line of what choosing a card means; [item] puts the item icon of that stat before it (p.16). */
@Immutable
data class CardNote(val text: String, val item: Stat? = null)

/**
 * What confirming a focused card does. [action] names it for the card on show ("Discard Magnetic
 * Dagger", "Play Shock facedown"): the Confirm button's label and the hint "Tap the card again to
 * discard Magnetic Dagger". [onConfirm] gets the index of the confirmed card in the list opened.
 * [context] is the question's own line above the card ("Arien attacks · melee · Attack 6").
 */
@Immutable
class CardSelection(
    val action: (Card) -> String,
    val context: String? = null,
    val onConfirm: (index: Int) -> Unit,
)

/** An open viewer: the cards, the one first shown, and what confirming does (null = view only). */
@Stable
class CardFocusRequest internal constructor(
    val id: Int,
    val cards: List<FocusCard>,
    val initial: Int,
    val title: String?,
    val selection: CardSelection?,
    val owner: Any?,
    val onClose: (() -> Unit)?,
) {
    /** False while the question can't be answered (the game is busy): Confirm waits. */
    var canConfirm by mutableStateOf(true)
        internal set
}

/**
 * The card focus viewer's state (docs/DESIGN.md, "Card focus"): which cards are open, which one
 * shows, where each thumbnail sits on screen. One per screen, shown by [CardFocusHost]:
 *
 * ```
 * val focus = rememberCardFocusState()
 * CardFocusHost(focus)
 * // a row of thumbnails, each Modifier.cardFocusOrigin(focus, card.id):
 * focus.open(cards.map { FocusCard(it) }, index)                        // view only
 * focus.open(cards, index, selection = CardSelection({ "Discard ${it.name}" }) { i -> answer(i) })
 * ```
 */
@Stable
class CardFocusState {
    var request: CardFocusRequest? by mutableStateOf(null)
        private set

    /** The card on show (an index into the open request's cards). */
    var index by mutableIntStateOf(0)
        internal set

    /** True while the viewer animates out after [close]. */
    var closing by mutableStateOf(false)
        private set

    /** Why it is closing: an upward fling flies the card away, a confirm settles it. */
    internal var exit by mutableStateOf(FocusExit.SHRINK)
        private set

    /** How far a predictive back gesture has gone (0–1): the card shrinks with it. */
    var backProgress by mutableFloatStateOf(0f)
        internal set

    internal val origins = mutableStateMapOf<String, Rect>()
    private var next = 0

    val isOpen: Boolean get() = request != null

    /** The card on show, while open. */
    val current: FocusCard? get() = request?.cards?.getOrNull(index)

    /**
     * Opens [cards] at [index]. With a [selection] a second tap on the focused card (or its Confirm
     * button) confirms it; without, the viewer only shows. [owner] ties it to a question:
     * [closeFor] closes it when that question is gone. [onClose] runs once it has closed, however.
     */
    fun open(
        cards: List<FocusCard>,
        index: Int = 0,
        selection: CardSelection? = null,
        title: String? = null,
        owner: Any? = null,
        onClose: (() -> Unit)? = null,
    ) {
        if (cards.isEmpty()) return
        val start = index.coerceIn(0, cards.lastIndex)
        request = CardFocusRequest(next++, cards, start, title, selection, owner, onClose)
        this.index = start
        closing = false
        exit = FocusExit.SHRINK
        backProgress = 0f
    }

    /** Opens plain cards to read, swiping between them. */
    fun show(cards: List<Card>, index: Int = 0, title: String? = null, onClose: (() -> Unit)? = null) =
        open(cards.map { FocusCard(it) }, index, title = title, onClose = onClose)

    /** Closes with nothing chosen: the card shrinks back into its thumbnail. */
    fun close() = startClosing(FocusExit.SHRINK)

    internal fun flyAway() = startClosing(FocusExit.FLY_UP)

    internal fun confirmed() = startClosing(FocusExit.CONFIRM)

    private fun startClosing(how: FocusExit) {
        if (request == null || closing) return
        exit = how
        closing = true
    }

    /** Closes at once, without animating, when the viewer was opened for [owner]. */
    fun closeFor(owner: Any?) {
        if (request != null && request?.owner == owner) finish()
    }

    /** While a question is busy (an answer on its way), its Confirm waits. */
    fun setCanConfirm(owner: Any?, can: Boolean) {
        request?.takeIf { it.owner == owner }?.canConfirm = can
    }

    internal fun finish() {
        val done = request ?: return
        request = null
        closing = false
        done.onClose?.invoke()
    }
}

internal enum class FocusExit { SHRINK, FLY_UP, CONFIRM }

@Composable
fun rememberCardFocusState(): CardFocusState = remember { CardFocusState() }

/** Marks a thumbnail as where the card with [key] grows out of (and shrinks back into). */
fun Modifier.cardFocusOrigin(state: CardFocusState, key: String): Modifier =
    onGloballyPositioned { state.origins[key] = it.boundsInWindow() }

/** "Discard Magnetic Dagger" → "discard Magnetic Dagger", for "Tap the card again to …". */
internal fun lowerFirst(text: String): String = text.replaceFirstChar { it.lowercaseChar() }
