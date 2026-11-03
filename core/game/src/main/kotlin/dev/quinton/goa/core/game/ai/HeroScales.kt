package dev.quinton.goa.core.game.ai

/**
 * The per-hero evaluation table (ADR-063, AI.md "Stream D"): for a hero id, multipliers of the hand
 * evaluation's terms that concern that hero ([EvalKnobs.heroScales]). Data only, keyed by id and tuned
 * on wins; a hero not listed is scored as before. Entries are kept only when they confirmed at
 * strength 10 at the phone's work.
 */
internal object HeroScales {
    val TABLE: Map<String, Map<Term, Double>> = emptyMap()
}
