package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.heuristic.DefaultHeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.General
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroPlaybooks
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team

/** Shared checks for the arien–garrus playbooks. */
internal object HeroBookCases {
    fun finishes(hero: String) {
        val twos = listOf(listOf(hero, "wasp") to listOf("xargatha", "min"))
        val threes = listOf(listOf(hero, "wasp", "xargatha") to listOf("min", "misa", "sabina"))
        for (strength in listOf(5, 10)) {
            for ((red, blue) in twos + threes) {
                val level = AiLevel.of(strength)
                val played = AiHarness.play(
                    AiHarness.setup(red, blue, level, level, Team.RED),
                    mapOf(Team.RED to HeuristicAi(strength, 11), Team.BLUE to HeuristicAi(strength, 12)),
                )
                assertThat(played.session.pending).isNull()
                assertThat(played.winner).isNotNull()
            }
        }
    }

    /**
     * The signature note, added to the general score, picks a different card than the general
     * book alone. Strength 10, so the playbook is on and a card play is not re-scored by lookahead.
     */
    fun flips(hero: String, book: HeroHeuristics, session: EngineSession) {
        val d = checkNotNull(session.pending) { "the position asked nothing" }
        assertThat(d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.seat).isEqualTo(hero)
        val ctx = HContext(
            session,
            d,
            d.team,
            HeuristicProfile.of(10),
            playbooks = { id -> if (id == hero) book else DefaultHeroHeuristics },
        )
        fun plain(c: Choice) = General.score(ctx, d, c)
        fun total(c: Choice) = plain(c) + book.adjust(ctx, d, c)
        val without = d.options.maxBy { plain(it) }
        val withBook = d.options.maxBy { total(it) }
        val dump = d.options.joinToString("\n") { "$it plain=${plain(it)} adj=${book.adjust(ctx, d, it)}" }
        assertWithMessage(dump).that(withBook).isNotEqualTo(without)
    }

    /**
     * The signature note fires: in this position it scores [favoured] above every card in [over] (all the
     * other options when empty). Strength 8, a static score, where a correction of the general estimate
     * applies. Since general v2 the card play from strength 5 is the engine's look plus this note (a
     * correction is silent there), so a test that the note flips the general book's static pick checks a
     * path the player no longer takes; the note's direction is what the hero file owns.
     */
    fun noteFavours(book: HeroHeuristics, session: EngineSession, favoured: String, vararg over: String) {
        val d = checkNotNull(session.pending) { "the position asked nothing" }
        assertThat(d.seat).isEqualTo(book.heroId)
        val ctx = HContext(
            session,
            d,
            d.team,
            HeuristicProfile.of(8),
            playbooks = { id -> if (id == book.heroId) book else HeroPlaybooks.forHero(id) },
        )
        val notes = d.options.filterIsInstance<Choice.Card>().associate { it.cardId to book.adjust(ctx, d, it) }
        val others = if (over.isEmpty()) notes.keys - favoured else over.toSet()
        val mine = checkNotNull(notes[favoured]) { "$favoured is not an option: ${notes.keys}" }
        for (o in others) {
            assertWithMessage("notes $notes").that(mine).isGreaterThan(checkNotNull(notes[o]) { "$o is not an option" })
        }
    }

    /** The note scores [avoided] below the best other option: it argues against that card. */
    fun noteAvoids(book: HeroHeuristics, session: EngineSession, avoided: String) {
        val d = checkNotNull(session.pending) { "the position asked nothing" }
        val ctx = HContext(
            session,
            d,
            d.team,
            HeuristicProfile.of(8),
            playbooks = { id -> if (id == book.heroId) book else HeroPlaybooks.forHero(id) },
        )
        val notes = d.options.filterIsInstance<Choice.Card>().associate { it.cardId to book.adjust(ctx, d, it) }
        val mine = checkNotNull(notes[avoided]) { "$avoided is not an option: ${notes.keys}" }
        assertWithMessage("notes $notes").that(mine).isLessThan((notes - avoided).values.max())
    }
}
