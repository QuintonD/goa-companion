package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.Role
import java.text.Normalizer

/** A value on which source 1 and a cross-check source differ. [field] matches override keys. */
data class Disagreement(
    val heroId: String,
    val entity: String,
    val field: String,
    val primary: String,
    val other: String,
    val source: String,
)

/** Coverage of one hero by the cross-check sources. */
data class CheckResult(
    val disagreements: List<Disagreement>,
    /** Card IDs with no matching row/card in the cross-check source. */
    val unmatched: List<String>,
    /** Whether the source had data for this hero at all. */
    val present: Boolean,
)

object CrossCheck {
    /** Name key for matching cards between sources: case, punctuation and spacing ignored. */
    fun nameKey(name: String): String = Normalizer.normalize(TextMarkup.normalizeWord(name), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")

    private fun romanTier(tier: String?): Int? = when (tier?.uppercase()) {
        "I" -> 1
        "II" -> 2
        "III" -> 3
        "IV" -> 4
        else -> null
    }

    /* ---- numbers vs colefin8 ---------------------------------------------------------------- */

    private val RANGE_OR_RADIUS = setOf("range", "radius")

    /**
     * colefin8 labels the Range item icon (bullseye) "Radius" in 57 of 62 cases and the Radius
     * icon "Radius", "Range" or "Eye", so its item label cannot tell the two apart (checked on
     * Arien's scans: Master Duelist shows the bullseye, colefin8 says "Radius"). Either label is
     * accepted for either icon; every other item is compared exactly.
     */
    private val COLE_RANGE_OR_RADIUS = setOf("range", "radius", "eye")

    /** colefin8 writes printed signs into numbers (`2+`, `1+`); the sign is not part of the value. */
    fun number(text: String?): Int? = text?.let { Regex("""\d+""").find(it)?.value?.toInt() }

    fun numbers(heroId: String, cards: List<Card>, rows: List<ColeRow>?): CheckResult {
        if (rows == null) return CheckResult(emptyList(), cards.map { it.id }, present = false)
        val byName = rows.groupBy { nameKey(it["Card Name"].orEmpty()) }
        val out = mutableListOf<Disagreement>()
        val unmatched = mutableListOf<String>()
        for (card in cards) {
            val candidates = byName[nameKey(card.name)].orEmpty()
            val row = pickRow(card, candidates)
            if (row == null) {
                unmatched += card.id
                continue
            }
            fun cmp(field: String, ours: Any?, theirs: Any?) {
                if (ours?.toString() != theirs?.toString()) {
                    out += Disagreement(heroId, card.id, field, ours.toString(), theirs.toString(), "colefin8")
                }
            }
            cmp("initiative", card.initiative, number(row["Initiative"]))
            cmp("values.attack", card.values.attack, number(row["Attack"]))
            cmp("values.movement", card.values.movement, number(row["Movement"]))
            val defense = row["Defense"]
            if (defense == "!") {
                cmp("block", card.block, true)
            } else {
                cmp("values.defense", card.values.defense, number(defense))
                if (card.block) cmp("block", card.block, false)
            }
            val modifier = card.values.range ?: card.values.radius
            cmp("values.range/radius", modifier, number(row["Range/Radius"]))
            val item = card.item?.name?.lowercase()
            val theirItem = row["Item Icon"]?.lowercase()
            if (!(item in RANGE_OR_RADIUS && theirItem in COLE_RANGE_OR_RADIUS)) cmp("item", item, theirItem)
        }
        return CheckResult(out, unmatched, present = true)
    }

    private fun pickRow(card: Card, rows: List<ColeRow>): ColeRow? {
        if (rows.size <= 1) return rows.firstOrNull()
        val wantHandicap = card.id.endsWith("-handicap")
        return rows.firstOrNull { (it["Tier"] == "H") == wantHandicap } ?: rows.first()
    }

    /* ---- text and identity vs goa2-faq ------------------------------------------------------ */

    fun texts(heroId: String, cards: List<Card>, faqCards: List<FaqCard>?): CheckResult {
        if (faqCards == null) return CheckResult(emptyList(), cards.map { it.id }, present = false)
        val byName = faqCards.groupBy { nameKey(it.name) }
        val out = mutableListOf<Disagreement>()
        val unmatched = mutableListOf<String>()
        val matchedByName = cards.mapNotNull { card ->
            faqMatch(card, byName[nameKey(card.name)].orEmpty())?.let {
                card.id to
                    it
            }
        }.toMap()
        val unused = faqCards - matchedByName.values.toSet()
        for (card in cards) {
            // Fallback when the names differ: the only unused goa2-faq card in the same slot.
            val faq = matchedByName[card.id] ?: unused.filter { sameSlot(card, it) }.singleOrNull()?.also {
                out += Disagreement(heroId, card.id, "name", card.name, it.name, "goa2-faq")
            }
            if (faq == null) {
                unmatched += card.id
                continue
            }
            val faqText = TextMarkup.fromFaq(faq.text)
            if (!TextMarkup.agrees(card.text, faqText)) {
                out += Disagreement(heroId, card.id, "text", card.text, faqText, "goa2-faq")
            }
            val coloured = card.color in setOf(CardColor.RED, CardColor.GREEN, CardColor.BLUE, CardColor.PURPLE)
            if (coloured && romanTier(faq.tier) != card.tier) {
                out += Disagreement(heroId, card.id, "tier", card.tier.toString(), faq.tier.toString(), "goa2-faq")
            }
            if (coloured && card.tier in 2..3 && faq.alternative != null) {
                val ourAlt = card.id.endsWith("b")
                if (ourAlt != faq.alternative) {
                    out +=
                        Disagreement(
                            heroId,
                            card.id,
                            "variant",
                            if (ourAlt) "b" else "a",
                            if (faq.alternative) "b" else "a",
                            "goa2-faq",
                        )
                }
            }
            if (card.color != CardColor.OTHER && card.color.name.lowercase() != faq.color.lowercase()) {
                out += Disagreement(heroId, card.id, "color", card.color.name.lowercase(), faq.color, "goa2-faq")
            }
        }
        return CheckResult(out, unmatched, present = true)
    }

    private fun faqMatch(card: Card, candidates: List<FaqCard>): FaqCard? {
        val wantHandicap = card.id.endsWith("-handicap")
        return candidates.firstOrNull { (it.tier == "H") == wantHandicap } ?: candidates.firstOrNull()
    }

    private fun sameSlot(card: Card, faq: FaqCard): Boolean {
        if (card.color == CardColor.OTHER || card.color.name.lowercase() != faq.color.lowercase()) return false
        if (card.id.contains("-extra-")) return false
        return when (card.tier) {
            null -> faq.tier == null
            2, 3 -> romanTier(faq.tier) == card.tier && faq.alternative == card.id.endsWith("b")
            else -> romanTier(faq.tier) == card.tier
        }
    }

    fun hero(hero: Hero, faq: FaqHero?): List<Disagreement> {
        if (faq == null) return emptyList()
        val out = mutableListOf<Disagreement>()
        fun cmp(field: String, ours: Any?, theirs: Any?) {
            if (ours?.toString() != theirs?.toString()) {
                out += Disagreement(hero.id, hero.id, field, ours.toString(), theirs.toString(), "goa2-faq")
            }
        }
        cmp("complexity", hero.complexity, faq.complexity)
        cmp("stats.attack", hero.stats.attack, faq.stat("attack"))
        cmp("stats.defense", hero.stats.defense, faq.stat("defense"))
        cmp("stats.initiative", hero.stats.initiative, faq.stat("initiative"))
        cmp("stats.movement", hero.stats.movement, faq.stat("movement"))
        val faqRoles = faq.roles.mapNotNull { r -> Role.entries.firstOrNull { it.name.equals(r, ignoreCase = true) } }
        cmp("roles", hero.roles.sortedBy { it.name }, faqRoles.sortedBy { it.name })
        val faqProduct = if (faq.expansion == "core") "core" else faq.expansion
        cmp("product", hero.product, faqProduct)
        return out
    }
}
