package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SheetTitle
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.isSpell

/*
 * Gydion's spellbook, at any point (extends ADR-040): a one-tap control on the game table whenever a
 * hero the person controls has a spellbook, opening a sheet with every spell card where the engine
 * says it is (in the spellbook, cast, not yet added), as the official landscape cards. The same sheet
 * shows an opponent's Gydion (the spellbook facedown, its count; from his hero sheet), a rewound board
 * in the Game state review (the book as it was then), and before the game the 22 spell cards from the
 * catalog (New game, the board sandbox's line-up).
 *
 * Prepare Spells (gydion-silver): "Add all spell cards to the spellbook. Only Gydion can look at the
 * cards in the spellbook. As each spell is cast, remove it from the spellbook faceup."
 */

/**
 * What the person's pending question says about a spell: [offered] now, or why not ([text], the
 * engine's reason with its rule). [short] goes under the thumbnail, [text] to TalkBack and the viewer.
 */
@Immutable
internal data class SpellMark(val offered: Boolean, val short: String, val text: String)

/**
 * The marks of [heroId]'s spells while the person answers a question offering them: Gydion's "Choose
 * and cast one spell in the spellbook" (the options can be cast now; the card's other spells come
 * with the engine's reason, `DecisionContext.unavailableCards`) or Find Familiar's removal. Empty for a
 * question that is not the person's to answer: its options are another seat's (p.4).
 */
internal fun spellMarks(table: TableUi, heroId: String): Map<String, SpellMark> {
    val decision = table.decision ?: return emptyMap()
    if (!table.asking || decision.kind != DecisionKind.CHOOSE_CARD) return emptyMap()
    val offered = table.cardOptions.mapNotNull { table.card(it.cardId) }
    if (offered.isEmpty() || offered.any { !it.isSpell || it.heroId != heroId }) return emptyMap()
    val cast = cardVerb(decision.prompt) == "Cast"
    val yes = if (cast) "Can be cast now" else "${cardVerb(decision.prompt)}: offered now"
    return buildMap {
        offered.forEach { put(it.id, SpellMark(offered = true, short = yes, text = yes)) }
        decision.context.unavailableCards.forEach { u ->
            val line = u.source?.let { "${u.text} ($it)" } ?: u.text
            put(u.cardId, SpellMark(offered = false, short = u.text, text = line))
        }
    }
}

/** The Spellbook sheet's content: whose, what the person may see of it, its groups. */
@Immutable
internal data class SpellbookUi(
    val heroId: String,
    val title: String,
    val subtitle: String,
    val groups: List<ExtrasGroup>,
    val marks: Map<String, SpellMark> = emptyMap(),
    /** The person's pending question about these spells ("Cantrip asks: …"), with its card. */
    val asking: String? = null,
    val askingCard: Card? = null,
    /** Public counters the groups don't count ("Wish casts 1"). */
    val counters: List<Pair<String, Int>> = emptyList(),
    /** The spells in the person's own spellbook a card in their hand names: "Cast with Lesser Evocation". */
    val castWith: Map<String, String> = emptyMap(),
    /** Each group's cards under a head per Tier (the live spellbook); the catalog's are groups by Tier. */
    val byTier: Boolean = false,
)

/**
 * [heroId]'s spellbook as the person sees it at [table] (the live table, or a rewound board's
 * then-view): the engine's places (ExtraPlaceView), so a facedown spellbook stays a count. Null for a
 * hero without spell cards.
 */
internal fun spellbookUi(table: TableUi, heroId: String): SpellbookUi? {
    val hero = table.heroView(heroId) ?: return null
    if (hero.extras.isEmpty() || table.hero(heroId).spells.isEmpty()) return null
    val name = table.hero(heroId).name
    val groups = extrasGroups(table, hero)
    val book = groups.firstOrNull { it.place == SPELLBOOK }
    val mine = heroId in table.mine
    val subtitle = when {
        // The facedown group says why in its own note.
        book?.cards == null -> "Open information (p.4): how many spells, and the ones cast."

        !mine -> "Open in this puzzle."

        else -> "Only you can look at the cards in your spellbook (Prepare Spells)."
    }
    val marks = spellMarks(table, heroId)
    val decision = table.decision?.takeIf { marks.isNotEmpty() }
    val shown = groups.map { group ->
        // The question's own note replaces the general one while it is asked.
        val g = if (group.place == SPELLBOOK && decision != null) group.copy(note = null) else group
        // By Tier, as the catalog lists them (stable: the engine's order within a Tier).
        g.copy(cards = g.cards?.sortedBy { it.tier ?: 0 })
    }
    return SpellbookUi(
        heroId = heroId,
        title = if (mine) "Your spellbook · $name" else "$name's spellbook",
        subtitle = subtitle,
        groups = shown,
        marks = marks,
        asking = decision?.let { d ->
            val offered = marks.values.count { it.offered }
            val card = table.sourceCard?.name
            val left = d.context.unavailableCards.mapNotNull { u ->
                table.card(u.cardId)?.let { " ${it.name}: ${marks[u.cardId]?.text ?: u.text}." }
            }.joinToString("")
            val verb = if (offered == 1) "is" else "are"
            listOfNotNull(card?.let { "$it asks" }, d.prompt).joinToString(": ") +
                ". ${BoardText.count(offered, "spell")} $verb offered; choose in the panel." + left
        },
        askingCard = decision?.let { table.sourceCard },
        counters = hero.counters.filterKeys { it !in GROUP_COUNTERS && it.startsWith("wish") }
            .filterValues { it > 0 }.map { (k, v) -> PlayText.counter(k) to v },
        castWith = castWith(table, heroId),
        byTier = true,
    )
}

/**
 * For the person's own [heroId] only: each spell in the spellbook that a card in the hand names, with
 * the cards ("Cast with Cantrip or Lesser Evocation"). The hand is hidden information (p.4) and
 * "Only Gydion can look at the cards in the spellbook" (Prepare Spells): nothing for anyone else.
 */
internal fun castWith(table: TableUi, heroId: String): Map<String, String> {
    if (heroId !in table.mine) return emptyMap()
    val hero = table.heroView(heroId) ?: return emptyMap()
    val hand = hero.hand?.mapNotNull(table::card) ?: return emptyMap()
    val inBook = hero.extras.firstOrNull { it.place == SPELLBOOK }?.cardIds?.toSet() ?: return emptyMap()
    val spells = table.hero(heroId).spells
    val by = linkedMapOf<String, MutableList<String>>()
    hand.forEach { card ->
        namedSpells(card, spells).filter { it.id in inBook }.forEach { spell ->
            by.getOrPut(spell.id) { mutableListOf() } += card.name
        }
    }
    return by.mapValues { (_, names) -> "Cast with " + orList(names) }
}

/**
 * The spell cards [card]'s text names, in its order: Gydion's cards quote them ("Cast the “Burning
 * Hands” spell in the spellbook", "Choose and cast one spell in the spellbook — • “Fireball” …"). The
 * catalog's own words matched by name to [spells] (the hero's spell cards), apostrophes aside (the
 * cards print "Midas Touch", the spell card "Midas' Touch"); never a mapping of our own.
 */
internal fun namedSpells(card: Card, spells: List<Card>): List<Card> {
    if (spells.isEmpty() || card.isSpell) return emptyList()
    val byName = spells.associateBy { spellKey(it.name) }
    return QUOTED.findAll(card.text).mapNotNull { byName[spellKey(it.groupValues[1])] }.distinct().toList()
}

private val QUOTED = Regex("[“\"]([^”\"]+)[”\"]")

private fun spellKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim()

private fun orList(names: List<String>): String = when (names.size) {
    1 -> names.single()
    else -> names.dropLast(1).joinToString(", ") + " or " + names.last()
}

/**
 * Before the game (New game, the board sandbox's line-up): every spell card from the catalog, by tier
 * as the catalog's hero page lists them. The game starts with an empty spellbook (designer, BGG 3491113).
 */
internal fun catalogSpellbook(hero: BoardHero): SpellbookUi? {
    val spells = hero.spells.ifEmpty { return null }
    val groups = spells.groupBy { it.tier }.entries.sortedBy { (tier, _) -> tier ?: 0 }.map { (tier, cards) ->
        ExtrasGroup(place = "tier-${tier ?: 0}", title = spellTierTitle(tier), count = cards.size, cards = cards)
    }
    return SpellbookUi(
        heroId = hero.id,
        title = "${hero.name}'s spellbook",
        subtitle = "All ${spells.size} spell cards. The game starts with an empty spellbook; Prepare Spells " +
            "adds them (designer, BGG 3491113). ${hero.name}'s cards name the spells they cast.",
        groups = groups,
    )
}

private val GROUP_COUNTERS = setOf("spellbook", "spellsRemoved")

/**
 * The Spellbook sheet: the groups as landscape cards (tap one to see it large, swiping through its
 * group); while the person's cast question is pending, which spells are offered and why the card's
 * others are not. [onOpen] opens the card viewer.
 */
@Composable
internal fun SpellbookContent(
    ui: SpellbookUi,
    onOpen: (cards: List<FocusCard>, index: Int, title: String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    origin: (key: String) -> Modifier = { Modifier },
) {
    Column(modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
        SheetTitle(ui.title, ui.subtitle, onDone = onDone)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            ui.asking?.let { AskingNow(it, ui.askingCard, onOpen) }
            if (ui.counters.isNotEmpty()) {
                Text(
                    ui.counters.joinToString(" · ") { (name, n) -> "$name $n" },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            ui.groups.forEach { group ->
                val title = "${ui.title} · ${group.title}"
                GroupBlock(
                    group,
                    title,
                    onOpen = { cards, index, t ->
                        onOpen(cards.map { focusSpell(it, t, ui.marks[it.id], ui.castWith[it.id]) }, index, t)
                    },
                    origin = { t, card -> origin("$t/${card.id}") },
                    marks = ui.marks,
                    castWith = ui.castWith,
                    byTier = ui.byTier,
                )
            }
        }
    }
}

/**
 * A spell in the viewer: an offered one says so; one the question leaves out says why (dimmed); else
 * the hand's card that casts it.
 */
private fun focusSpell(card: Card, title: String, mark: SpellMark?, castWith: String?): FocusCard = FocusCard(
    card,
    key = "$title/${card.id}",
    caption = if (mark != null) mark.takeIf { it.offered }?.text else castWith,
    reason = mark?.takeIf { !it.offered }?.text,
)

/** The pending question in one line, with the asking card (tap to read it). */
@Composable
private fun AskingNow(text: String, card: Card?, onOpen: (List<FocusCard>, Int, String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (card != null) {
                Surface(
                    onClick = { onOpen(listOf(FocusCard(card)), 0, "Asked by") },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = spokenCard(card)
                        role = Role.Button
                        onClick(label = "Read the card") {
                            onOpen(listOf(FocusCard(card)), 0, "Asked by")
                            true
                        }
                    },
                ) { TableCard(card, width = 36.dp) }
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The Spellbook sheet over a screen, its cards in [focus]. */
@Composable
internal fun SpellbookSheet(ui: SpellbookUi, focus: CardFocusState, onDismiss: () -> Unit) {
    BoardSheet(onDismiss = onDismiss) {
        SpellbookContent(
            ui,
            onOpen = { cards, index, title -> focus.open(cards, index, title = title) },
            onDone = onDismiss,
            origin = { key -> Modifier.cardFocusOrigin(focus, key) },
        )
    }
}

/** The heroes the person controls that have a spellbook, each gets a Spellbook control at the table. */
internal val TableUi.spellbookSeats: List<String>
    get() = mine.filter { heroView(it) != null && hero(it).spells.isNotEmpty() }.sorted()

/**
 * The table's way into [heroId]'s spellbook: the hero's official deck symbol (ADR-042) beside a small
 * landscape card holding the number of spells in the spellbook, on a floating pill like Fit and Flip.
 * One TalkBack node: "Gydion's spellbook: 19 in the spellbook, 3 cast. Open".
 */
@Composable
internal fun SpellbookButton(table: TableUi, heroId: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val info = table.hero(heroId)
    val places = table.heroView(heroId)?.extras.orEmpty().associate { it.place to it.count }
    val inBook = places[SPELLBOOK] ?: 0
    val spoken = buildList {
        add("${info.name}'s spellbook")
        add("$inBook in the spellbook")
        places[REMOVED]?.takeIf { it > 0 }?.let { add("$it cast") }
        places[BOX]?.takeIf { it > 0 }?.let { add("$it not yet added") }
    }.joinToString(", ")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Open the spellbook") {
                    onClick()
                    true
                }
            },
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroSymbol(heroId, info.symbol, Modifier.size(scaledIconSize(28.dp, 1.3f)))
            Spacer(Modifier.width(4.dp))
            // A spell card lies landscape: the count sits on a small one, parchment like the cards.
            Box(
                Modifier
                    .size(width = scaledIconSize(30.dp, 1.4f), height = scaledIconSize(21.dp, 1.4f))
                    .background(GoaTheme.colors.parchment, RoundedCornerShape(3.dp))
                    .border(1.dp, GoaTheme.colors.parchmentEdge, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$inBook",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = GoaTheme.colors.onParchment,
                )
            }
        }
    }
}

/**
 * Before the game, where a player picks who they play: "Gydion's spellbook · 22 spell cards" with the
 * hero's deck symbol; one tap opens the 22 spell cards ([catalogSpellbook]).
 */
@Composable
internal fun SpellbookChip(hero: BoardHero, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        Row(
            Modifier.padding(start = 6.dp, end = Spacing.md, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroSymbol(hero.id, hero.symbol, Modifier.size(scaledIconSize(28.dp, 1.3f)))
            Spacer(Modifier.width(Spacing.xs))
            Text(
                "${hero.name}'s spellbook · ${BoardText.count(hero.spells.size, "spell")}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val SHEET_HEIGHT = 0.9f
