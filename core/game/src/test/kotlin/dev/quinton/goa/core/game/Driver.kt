package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex

/** Test engine that also seats heroes not complete yet (stand-ins for rulebook examples). */
val ANY_HERO_ENGINE: GameEngine by lazy {
    GameEngine(TestData.catalog, TestData.maps, TestData.matchRules, allowIncomplete = true)
}

fun pos(
    map: String = TestData.FOREST,
    engine: GameEngine = ANY_HERO_ENGINE,
    block: PositionBuilder.() -> Unit,
): Driver = Driver(position(engine, map, block))

/** Answers decisions in tests, checking what is asked. */
class Driver(val s: EngineSession) {
    val d: Decision get() = s.pending ?: error("No pending decision; events:\n${log()}")

    fun log(): String = s.events.joinToString("\n") { "  ${it.text}" }

    fun texts(): List<String> = s.events.map { it.text }

    fun answer(c: Choice): Driver {
        val r = s.answer(d.id, c)
        check(r is AnswerResult.Accepted) { "Rejected $c: $r\nPending: $d\n${log()}" }
        return this
    }

    fun expect(kind: DecisionKind, seat: String? = null): Decision {
        val p = d
        assertThat(p.kind).isEqualTo(kind)
        if (seat != null) assertThat(p.seat).isEqualTo(seat)
        return p
    }

    fun play(seat: String, cardId: String) = apply {
        expect(DecisionKind.PLAY_CARD, seat)
        answer(Choice.Card(cardId))
    }

    fun action(seat: String, a: ActionType) = apply {
        expect(DecisionKind.CHOOSE_ACTION, seat)
        answer(Choice.Action(a))
    }

    fun actions(): List<ActionType> = expect(DecisionKind.CHOOSE_ACTION).options.map { (it as Choice.Action).action }

    fun space(h: Hex) = answer(Choice.Space(h))

    fun space(q: Int, r: Int) = space(Hex(q, r))

    fun unit(id: String) = answer(Choice.Unit(id))

    fun token(id: String) = answer(Choice.Token(id))

    fun card(id: String) = answer(Choice.Card(id))

    fun yes() = answer(Choice.Yes)

    fun decline() = answer(d.options.first { it is Choice.Decline })

    fun number(n: Int) = answer(Choice.Number(n))

    fun option(i: Int) = answer(d.options.first { it is Choice.Option && it.index == i })

    fun defend(cardId: String?) = apply {
        expect(DecisionKind.DEFEND)
        if (cardId == null) decline() else card(cardId)
    }

    fun spaces(): Set<Hex> = d.options.filterIsInstance<Choice.Space>().map { it.hex }.toSet()

    fun units(): Set<String> = d.options.mapNotNull {
        when (it) {
            is Choice.Unit -> it.unitId
            is Choice.Token -> it.tokenId
            else -> null
        }
    }.toSet()

    fun cards(): Set<String> = d.options.filterIsInstance<Choice.Card>().map { it.cardId }.toSet()

    fun hero(id: String): HeroState = s.state.hero(id)!!

    fun at(id: String): Hex? = s.state.hero(id)?.at ?: s.state.minion(id)?.at ?: s.state.piece(id)?.at

    fun zoneOf(heroId: String, cardId: String): Zone = hero(heroId).card(cardId)!!.zone

    val pending: Decision? get() = s.pending
}
