package dev.quinton.goa.feature.rules.ui

import dev.quinton.goa.core.model.rules.RulesTopic

/** One stop of a topic's section navigator: its label and the passage it opens at. */
internal data class TopicSection(val label: String, val excerpt: Int)

/**
 * Where a long topic can be opened, in page order, for its navigator. Labels are the rulebook's own printed
 * heads (verbatim, without a trailing colon); a diagram is "Diagrams" (the app's word for where the pictures
 * are). A topic with few heads also lists its headless boxed passages, by their own opening words, so the
 * reader can find the box ("A Lane can be Pushed both…") without the app putting a rule in other words.
 * Fewer than [MIN_SECTIONS] stops: no navigator.
 */
internal fun topicSections(topic: RulesTopic): List<TopicSection> {
    val headed = mutableListOf<TopicSection>()
    var diagrams = false
    topic.excerpts.forEachIndexed { i, excerpt ->
        val heading = excerpt.heading?.trim()?.trimEnd(':')?.trim()
        when {
            heading != null -> headed += TopicSection(heading, i)

            RulesDiagrams.of(excerpt) != null && !diagrams -> {
                diagrams = true
                headed += TopicSection(DIAGRAMS, i)
            }
        }
    }
    val sections = if (headed.size >= FEW_HEADS) {
        headed
    } else {
        val boxes = topic.excerpts.withIndex()
            .filter { (_, e) -> e.heading == null && RulesBoxes.of(e) != null && RulesDiagrams.of(e) == null }
            .map { (i, e) -> TopicSection(openingWords(e.text), i) }
        (headed + boxes).sortedBy { it.excerpt }
    }
    // Two heads on one passage index can't happen; the same printed head twice (a repeated passage) can.
    val distinct = sections.distinctBy { it.label }
    return if (distinct.size >= MIN_SECTIONS) distinct else emptyList()
}

/** "A Lane can be Pushed both at the end…" → "A Lane can be Pushed…": the passage's own first words. */
internal fun openingWords(text: String, words: Int = OPENING_WORDS): String {
    val first = text.removePrefix("• ").lineSequence().first().trim()
    val parts = first.split(' ').filter { it.isNotEmpty() }
    if (parts.size <= words) return first.trimEnd('.', ':', ',')
    return parts.take(words).joinToString(" ").trimEnd('.', ':', ',', ';') + "…"
}

internal const val DIAGRAMS = "Diagrams"
private const val MIN_SECTIONS = 3
private const val FEW_HEADS = 4
private const val OPENING_WORDS = 5

/**
 * The anchors a topic with diagrams gets near its top: its diagrams (each a thumbnail that opens it) and, when a
 * passage prints numbered steps, "Steps" (the app's word for where they are, as "Diagrams" is), opening that passage.
 * Nothing is paraphrased: the anchors only point. Null for a topic without diagrams.
 */
internal data class TopicAnchors(val steps: Int?, val diagrams: List<Pair<Int, RulesDiagrams.Entry>>)

internal fun topicAnchors(topic: RulesTopic): TopicAnchors? {
    val diagrams = topic.excerpts.mapIndexedNotNull { i, e -> RulesDiagrams.of(e)?.let { i to it } }
    if (diagrams.isEmpty()) return null
    val steps = topic.excerpts.indexOfFirst { NUMBERED_STEP.containsMatchIn(it.text) }.takeIf { it >= 0 }
    return TopicAnchors(steps, diagrams)
}

/** A numbered step as the rulebook prints it: "1. " at the start of a line. */
private val NUMBERED_STEP = Regex("""(^|\n)\s*1\.\s""")

internal const val STEPS = "Steps"
