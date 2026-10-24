package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random

/**
 * Samples a state consistent with what [viewer]'s team can see (p.4 "Hidden Information"), for the AI
 * (ADR-035: it "never reads hidden cards"). For each hero of the other team:
 * - **Upgrade history** (standard heroes): which colours were upgraded to which Tier, and which card
 *   of each pair went to hand, is hidden; only the level, the item *icons* (designer, BGG 3590819)
 *   and the faceup cards are public. A history consistent with those (and with [Options.seen], cards
 *   seen faceup since the hero's last level up) is drawn uniformly; its kept cards and the hidden basic
 *   cards are then dealt at random to the hidden places (hand, facedown cards, the unrevealed played
 *   card), its other cards become the items, the rest the deck.
 * - Heroes whose cards don't follow the one-card-per-colour pattern (spellbooks, extras, cards moved by
 *   text) fall back to swapping identities within each colour and Tier among the hidden places.
 * - Before the reveal, the played card is dealt again from the hero's hand pool, weighted by
 *   [Options.playWeight] (an opponent model) when given, else uniformly.
 * Facedown tokens of the other team get their kinds shuffled among themselves. Heroes of the viewer's
 * team named in [Options.hidePlayedOf] (human teammates) have their unrevealed played card dealt again
 * too: a facedown card is hidden information even to a teammate (p.4).
 *
 * The sample depends only on public information and [rng]: hidden places are canonicalised (sorted)
 * before dealing and hand stamps cleared, so two states that differ only in hidden cards give the same
 * sample (SessionTest, PropertyTest, ai/NoPeekingTest).
 */
object Determinizer {
    /**
     * Refinements the AI may pass. [seen]: per hero, card ids seen faceup since its last level up
     * (public memory from the log). [playWeight]: weights for the cards of a hero's hand pool (all in
     * hand when it is called) to be the one it played. [hidePlayedOf]: viewer-team heroes whose
     * unrevealed played card is hidden.
     */
    class Options(
        val seen: Map<String, Set<String>> = emptyMap(),
        val playWeight: ((state: GameState, heroId: String, pool: List<String>) -> List<Double>)? = null,
        val hidePlayedOf: Set<String> = emptySet(),
    )

    fun sample(data: EngineData, source: GameState, viewer: Team, rng: Random): GameState =
        sample(data, source, viewer, rng, Options())

    fun sample(data: EngineData, source: GameState, viewer: Team, rng: Random, options: Options): GameState {
        val s = source.copy()
        for (h in s.heroes) {
            if (h.team == viewer) continue
            if (!resampleHistory(data, s, h, rng, options.seen[h.id].orEmpty())) resampleIdentities(data, s, h, rng)
        }
        for (h in s.heroes) {
            if (h.team != viewer || h.id in options.hidePlayedOf) redealHand(s, h, rng, options.playWeight)
        }
        shuffleFacedownTokens(s, viewer, rng)
        s.stamp++
        return s
    }

    private val ACTIVE = setOf(Zone.HAND, Zone.PLAYED, Zone.RESOLVED, Zone.DISCARD)
    private val COLOURED = listOf(CardColor.RED, CardColor.GREEN, CardColor.BLUE)
    private val BASIC = listOf(CardColor.GOLD, CardColor.SILVER)

    private fun hidden(state: GameState?, c: CardInst): Boolean = when (c.zone) {
        Zone.HAND, Zone.DECK -> true
        Zone.PLAYED -> state?.revealed == false
        Zone.RESOLVED, Zone.DISCARD -> c.faceDown
        else -> false
    }

    // ---- upgrade histories -----------------------------------------------------------------

    /** One colour's part of a history: the card in the round's hand pool and the items it left. */
    private class ColourChoice(val kept: String, val items: List<String>)

    @Suppress("CyclomaticComplexMethod")
    private fun resampleHistory(data: EngineData, s: GameState, h: HeroState, rng: Random, seen: Set<String>): Boolean {
        if (data.catalogHero(h.id).extras.isNotEmpty()) return false
        if (h.cards.any { it.zone == Zone.EXTRA || it.zone == Zone.ASIDE }) return false
        val colourOf = h.cards.associate { it.id to data.card(it.id).color }
        for (c in BASIC + COLOURED) if (h.cards.count { it.zone in ACTIVE && colourOf[it.id] == c } != 1) return false
        // Per colour: Tier I, the Tier II pair, the Tier III pair (catalog order, a before b).
        val tiers = COLOURED.associateWith { c ->
            (1..3).map { t -> h.cards.filter { colourOf[it.id] == c && data.card(it.id).tier == t }.map { it.id } }
        }
        if (tiers.values.any { it[0].size != 1 || it[1].size != 2 || it[2].size != 2 }) return false
        val upgrades = (h.level - 1).coerceIn(0, 6)
        val itemIcons = h.cards.filter { it.zone == Zone.ITEM }.map { data.card(it.id).item }.sortedBy {
            it?.ordinal
                ?: -1
        }
        fun public(c: CardInst) = c.zone in ACTIVE && !hidden(s, c)
        val fixed = COLOURED.associateWith { c ->
            h.cards.firstOrNull { colourOf[it.id] == c && public(it) }?.id
                ?: seen.firstOrNull { id -> colourOf[id] == c && h.card(id) != null }
        }
        // Every history: a Tier per colour (all Tier II before any Tier III, p.15) and the pair choices.
        val perColour = COLOURED.map { c ->
            val t = tiers.getValue(c)
            (1..3).associateWith { tier ->
                when (tier) {
                    1 -> listOf(ColourChoice(t[0][0], emptyList()))
                    2 -> t[1].map { k -> ColourChoice(k, t[1] - k) }
                    else -> t[2].flatMap { k -> t[1].map { item2 -> ColourChoice(k, listOf(item2) + (t[2] - k)) } }
                }.filter { choice -> fixed.getValue(c)?.let { it == choice.kept } ?: true }
            }
        }
        val vectors = cartesian(COLOURED.map { listOf(1, 2, 3) })
            .filter { v -> v.sumOf { it - 1 } == upgrades && !(v.any { it == 3 } && v.any { it == 1 }) }
        fun icons(combo: List<ColourChoice>) =
            combo.flatMap { it.items }.map { data.card(it).item }.sortedBy { it?.ordinal ?: -1 }
        val histories = vectors.flatMap { v -> cartesian(v.mapIndexed { i, t -> perColour[i].getValue(t) }) }
            .filter { icons(it) == itemIcons }
        if (histories.isEmpty()) return false
        val history = histories[rng.nextInt(histories.size)]
        apply(data, h, history, colourOf, rng, ::public)
        return true
    }

    /** Every way to pick one element from each list, in order. */
    private fun <T> cartesian(lists: List<List<T>>): List<List<T>> =
        lists.fold(listOf(emptyList())) { acc, l -> acc.flatMap { prefix -> l.map { prefix + it } } }

    @Suppress("LongParameterList")
    private fun apply(
        data: EngineData,
        h: HeroState,
        history: List<ColourChoice>,
        colourOf: Map<String, CardColor>,
        rng: Random,
        public: (CardInst) -> Boolean,
    ) {
        val byId = h.cards.associateBy { it.id }
        val publicActive = h.cards.filter { it.zone in ACTIVE && public(it) }
        val publicColours = publicActive.map { colourOf.getValue(it.id) }.toSet()
        // Hidden places of the hand pool, in a public canonical order.
        val places = h.cards.filter {
            it.zone in ACTIVE && !public(it)
        }.map { Snapshot.of(it) }.sortedWith(Snapshot.ORDER)
        val dealt = ArrayList<String>()
        for (c in BASIC) {
            if (c in publicColours) continue
            val candidates = h.cards.filter {
                colourOf[it.id] == c && data.card(it.id).tier == null && (it.zone in ACTIVE || it.zone == Zone.DECK)
            }.map { it.id }.sorted()
            dealt += candidates[rng.nextInt(candidates.size)]
        }
        for ((i, c) in COLOURED.withIndex()) if (c !in publicColours) dealt += history[i].kept
        val items = history.flatMap { it.items }.toSet()
        val keptAll = history.map { it.kept }.toSet()
        val publicIds = publicActive.map { it.id }.toSet()
        // Everything not public is reset first: the deck by default.
        for (card in h.cards) {
            if (card.id in publicIds) continue
            val col = colourOf.getValue(card.id)
            val inPlay = col in COLOURED || col in BASIC
            if (!inPlay || card.zone == Zone.BOX || card.zone == Zone.ULTIMATE) continue
            if (col in BASIC && data.card(card.id).tier != null) continue
            card.zone = if (card.id in items) Zone.ITEM else Zone.DECK
            card.slot = 0
            card.faceDown = false
            card.played = false
            card.tag = null
            card.stamp = 0
        }
        check(keptAll.containsAll(dealt.filter { colourOf[it] in COLOURED }))
        val shuffled = dealt.sorted().shuffled(rng)
        check(shuffled.size == places.size) { "Hidden places ${places.size} != cards ${shuffled.size} for ${h.id}" }
        for ((i, id) in shuffled.withIndex()) places[i].applyTo(byId.getValue(id))
    }

    // ---- fallback: identities within colour and Tier ----------------------------------------

    /** Swap identities within each (colour, tier) class among hidden positions (deck included). */
    private fun resampleIdentities(data: EngineData, s: GameState, h: HeroState, rng: Random) {
        val hiddenCards = h.cards.filter { hidden(s, it) }
        val byClass = hiddenCards.groupBy { data.card(it.id).let { c -> c.color to c.tier } }
        for ((_, members) in byClass.entries.sortedBy { it.value.minOf { c -> c.id } }) {
            if (members.size < 2) continue
            // Positions in a canonical order (public), identities dealt to them at random.
            val states = members.map { Snapshot.of(it) }.sortedWith(Snapshot.ORDER)
            val ids = members.map { it.id }.sorted().shuffled(rng)
            val byId = h.cards.associateBy { it.id }
            for ((i, id) in ids.withIndex()) states[i].applyTo(byId.getValue(id))
        }
    }

    /** A place a card can be in. Stamps are public only for the order of a discard pile or the slots. */
    private class Snapshot(
        val zone: Zone,
        val slot: Int,
        val faceDown: Boolean,
        val played: Boolean,
        val tag: String?,
        val stamp: Long,
    ) {
        fun applyTo(c: CardInst) {
            c.zone = zone
            c.slot = slot
            c.faceDown = faceDown
            c.played = played
            c.tag = tag
            c.stamp = stamp
        }

        companion object {
            fun of(c: CardInst) = Snapshot(
                c.zone,
                c.slot,
                c.faceDown,
                c.played,
                c.tag,
                if (c.zone == Zone.DISCARD || c.zone == Zone.RESOLVED) c.stamp else 0,
            )

            val ORDER: Comparator<Snapshot> = compareBy(
                { it.zone.ordinal },
                { it.slot },
                { it.faceDown },
                { it.played },
                { it.tag ?: "" },
                { it.stamp },
            )
        }
    }

    // ---- the played card ---------------------------------------------------------------------

    /** Before the reveal, any card of the hand pool may be the one played facedown. */
    private fun redealHand(
        s: GameState,
        h: HeroState,
        rng: Random,
        weight: ((GameState, String, List<String>) -> List<Double>)?,
    ) {
        if (s.revealed) return
        val pool = h.cards.filter { it.zone == Zone.HAND || it.zone == Zone.PLAYED }.sortedBy { it.id }
        val playedCount = pool.count { it.zone == Zone.PLAYED }
        if (playedCount == 0) return
        // Everything back in hand first, so the weights can't see which card was really played.
        for (c in pool) {
            c.zone = Zone.HAND
            c.played = false
            c.stamp = 0 // stamps would tell which card was played last
        }
        val ids = pool.map { it.id }
        val w = weight?.invoke(s, h.id, ids)?.map { if (it.isFinite() && it > 0) it else 0.0 }
        val left = pool.toMutableList()
        val lw = w?.toMutableList()
        repeat(playedCount) {
            val i = if (lw == null || lw.sum() <= 0) rng.nextInt(left.size) else pick(lw, rng)
            val c = left.removeAt(i)
            lw?.removeAt(i)
            c.zone = Zone.PLAYED
            c.played = true
        }
    }

    private fun pick(weights: List<Double>, rng: Random): Int {
        var r = rng.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r < 0) return i
        }
        return weights.lastIndex
    }

    private fun shuffleFacedownTokens(s: GameState, viewer: Team, rng: Random) {
        val hiddenPieces = s.pieces.filter { it.faceDown && it.team != viewer }.groupBy { it.owner }
        for ((_, pieces) in hiddenPieces) {
            if (pieces.size < 2) continue
            val kinds = pieces.sortedBy { it.id }.map { it.kind to it.label }.sortedBy { it.first }.shuffled(rng)
            val sorted = pieces.sortedBy { it.id }
            for ((i, p) in sorted.withIndex()) {
                val idx = s.pieces.indexOf(p)
                s.pieces[idx] = PieceState(
                    p.id, kinds[i].first, kinds[i].second, p.at, p.owner, p.team, p.token, p.unit,
                    p.removeAtEndOfRound, p.passable, p.faceDown, p.heroBody, LinkedHashMap(p.data),
                )
            }
        }
    }
}
