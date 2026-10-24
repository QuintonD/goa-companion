package dev.quinton.goa.core.game.position

import dev.quinton.goa.core.domain.board.BoardSetup
import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.ActiveEffect
import dev.quinton.goa.core.game.engine.CardInst
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Marker
import dev.quinton.goa.core.game.engine.MinionState
import dev.quinton.goa.core.game.engine.NEXT_ACTOR
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team

/**
 * Where the built position stands in the turn (EFFECTS.md "Positions").
 */
sealed interface PositionPhase {
    /** Turn start: start-of-turn effects apply, then every seat plays a card. */
    data object TurnStart : PositionPhase

    /** Playing cards, from seat [fromSeat] (index in hero order); earlier seats' played cards are as given. */
    data class PlayCards(val fromSeat: Int = 0) : PositionPhase

    /** Cards are revealed and resolving: the highest unresolved initiative acts next, or [next] when given. */
    data class Resolving(val next: String? = null, val applyRevealEffects: Boolean = false) : PositionPhase

    /** Turn 4 is over: the end of round steps run next (End of round effects, Minion Battle, …). */
    data object EndOfRound : PositionPhase
}

@DslMarker
annotation class PositionDsl

/**
 * Builds a [EngineSession] from an arbitrary mid-game position: for hero tests, the 50 puzzles and
 * the board sandbox's "play from this position". Anything not given keeps the opening's value:
 * heroes at level 1 with their starting hand, the starting minions (unless [clearMinions]), full
 * counters, Battle Zones at the start.
 *
 * ```
 * val session = position(engine, "forgotten-island") {
 *     round = 2; turn = 3; tieBreaker = Team.BLUE
 *     clearMinions()
 *     hero("arien", Team.BLUE, Hex(0, 0)) { played("arien-red-1"); level = 3; coins = 1 }
 *     hero("wasp", Team.RED, Hex(1, 0)) { hand("wasp-green-1", "wasp-gold") }
 *     minion(Team.RED, MinionType.MELEE, Hex(2, -1))
 *     phase = PositionPhase.Resolving(next = "arien")
 * }
 * ```
 */
fun position(engine: GameEngine, mapId: String, block: PositionBuilder.() -> Unit): EngineSession =
    PositionBuilder(engine, mapId).apply(block).build()

@PositionDsl
class PositionBuilder(private val engine: GameEngine, private val mapId: String) {
    var round: Int = 1
    var turn: Int = 1
    var tieBreaker: Team = Team.RED
    var length: GameLength = GameLength.QUICK
    var phase: PositionPhase = PositionPhase.TurnStart
    var heroDefeatedThisRound: Boolean = false

    private val heroes = ArrayList<HeroBuilder>()
    private val minions = ArrayList<MinionState>()
    private val pieces = ArrayList<PieceState>()
    private val battleZones = LinkedHashMap<String, String>()
    private val waves = LinkedHashMap<String, Int>()
    private val life = LinkedHashMap<Team, Int>()
    private val effects = ArrayList<EffectSpec>()
    private var openingMinions = true
    private var nextId = 1000

    /** A hero at [at] (null = off the board, waiting to respawn). Seats are in call order. */
    fun hero(heroId: String, team: Team, at: Hex?, block: HeroBuilder.() -> Unit = {}) {
        heroes += HeroBuilder(heroId, team, at).apply(block)
    }

    /** Remove the opening's minions (place them yourself with [minion]). */
    fun clearMinions() {
        openingMinions = false
    }

    fun minion(team: Team, type: MinionType, at: Hex, lane: String = "lane-1"): String {
        val id = "m${nextId++}"
        minions += MinionState(id, team, type, at, lane)
        return id
    }

    /** A token or other piece; [kind] is its registry key (e.g. `token_rock`). */
    @Suppress("LongParameterList")
    fun token(
        kind: String,
        at: Hex,
        owner: String? = null,
        team: Team? = null,
        label: String = kind.removePrefix("token_").replaceFirstChar { it.uppercase() },
        removeAtEndOfRound: Boolean = true,
        faceDown: Boolean = false,
    ): String {
        val id = "t${nextId++}"
        val tk = engine.data.registry.tokenKind(kind)
        pieces += PieceState(
            id, kind, tk?.label ?: label, at, owner, team, token = true, unit = false,
            removeAtEndOfRound = tk?.removeAtEndOfRound ?: removeAtEndOfRound,
            passable = tk?.passable ?: false,
            faceDown = faceDown,
        )
        return id
    }

    /** Any other piece (a unit piece such as a hero body, or a non-token object such as a Turret). */
    fun piece(piece: PieceState) {
        pieces += piece
    }

    fun battleZone(lane: String, zone: String) {
        battleZones[lane] = zone
    }

    fun wavesLeft(lane: String, n: Int) {
        waves[lane] = n
    }

    fun lifeCounters(team: Team, n: Int) {
        life[team] = n
    }

    /** An Active effect in force ([timing] as when it was made in [madeTurn] of this round). */
    @Suppress("LongParameterList")
    fun effect(
        owner: String,
        cardId: String,
        timing: Timing,
        action: ActionKind,
        madeTurn: Int = turn,
        data: Map<String, String> = emptyMap(),
        key: String? = null,
    ) {
        effects += EffectSpec(owner, cardId, timing, action, madeTurn, data, key)
    }

    private class EffectSpec(
        val owner: String,
        val cardId: String,
        val timing: Timing,
        val action: ActionKind,
        val turn: Int,
        val data: Map<String, String>,
        val key: String?,
    )

    @PositionDsl
    class HeroBuilder(val heroId: String, val team: Team, val at: Hex?) {
        var level: Int = 1
        var coins: Int = 0
        var controller: Controller = Controller.Human
        var handicap: Boolean = false
        internal val placed = LinkedHashMap<String, Pair<Zone, Int>>()
        internal val faceDown = HashSet<String>()
        internal val markers = ArrayList<Marker>()
        internal var ultimate = false
        internal val vars = LinkedHashMap<String, String>()
        internal val counters = LinkedHashMap<String, Int>()
        internal val extraTags = LinkedHashMap<String, String>()
        internal var exactHand = false

        /**
         * Cards in hand. With [exactly], the hand is only these: the rest of this round's cards (one
         * per colour) not placed elsewhere are in the discard pile.
         */
        fun hand(vararg cardIds: String, exactly: Boolean = true) {
            cardIds.forEach { placed[it] = Zone.HAND to 0 }
            exactHand = exactHand || exactly
        }

        /** This turn's played card (facedown until the reveal unless the phase is Resolving). */
        fun played(cardId: String) {
            placed[cardId] = Zone.PLAYED to 0
        }

        fun resolved(slot: Int, cardId: String, faceDown: Boolean = false) {
            placed[cardId] = Zone.RESOLVED to slot
            if (faceDown) this.faceDown += cardId
        }

        fun discard(vararg cardIds: String) {
            cardIds.forEach { placed[it] = Zone.DISCARD to 0 }
        }

        fun items(vararg cardIds: String) {
            cardIds.forEach { placed[it] = Zone.ITEM to 0 }
        }

        fun deck(vararg cardIds: String) {
            cardIds.forEach { placed[it] = Zone.DECK to 0 }
        }

        /**
         * Extra cards (the hero JSON `extras`, e.g. Gydion's spells) in the hero-specific place [tag]
         * of [Zone.EXTRA] (`CardInst.tag`); extras not named stay in the box (tag `box`).
         */
        fun extra(vararg cardIds: String, tag: String) {
            cardIds.forEach { extraTags[it] = tag }
        }

        /** The Ultimate is unlocked (level 8). */
        fun ultimate() {
            ultimate = true
            level = 8
        }

        fun marker(kind: String, from: String?, persistent: Boolean = false) {
            markers += Marker(kind, from, persistent)
        }

        fun vars(vararg pairs: Pair<String, String>) {
            vars += pairs
        }

        fun counter(name: String, value: Int) {
            counters[name] = value
        }
    }

    fun build(): EngineSession {
        require(heroes.isNotEmpty()) { "A position needs heroes" }
        val data = engine.data
        val geo = data.geometry(mapId)
        val state = GameState(mapId = mapId, length = length, tieBreaker = tieBreaker)
        state.round = round
        state.turn = turn
        state.nextId = nextId + 1000
        state.heroDefeatedThisRound = heroDefeatedThisRound
        if (openingMinions) {
            val opening = BoardSetup.opening(geo, BoardLineup()) { "m${nextId++}" }
            opening.pieces.filterIsInstance<MinionPiece>().forEach {
                state.minions += MinionState(it.id, it.team, it.type, it.at, it.lane ?: geo.map.lanes.first().id)
            }
        }
        state.minions += minions
        state.pieces += pieces
        for (lane in geo.map.lanes) {
            state.battleZones[lane.id] = battleZones[lane.id] ?: lane.startingBattleZone
            state.wavesLeft[lane.id] = waves[lane.id] ?: data.wavesPerLane(geo.map.lanes.size, length)
        }
        val lifeDefault = data.lifeCounters(geo.map.lanes.size, length, heroes.size)
        state.lifeCounters[Team.RED] = life[Team.RED] ?: lifeDefault
        state.lifeCounters[Team.BLUE] = life[Team.BLUE] ?: lifeDefault
        heroes.forEachIndexed { seat, hb -> state.heroes += buildHero(hb, seat) }
        // A minion placed on a space a hero or token now holds is dropped (one obstacle per space).
        val taken = (state.heroes.mapNotNull { it.at } + state.pieces.mapNotNull { it.at }).toSet()
        state.minions.removeAll { m -> m.at != null && m.at in taken && m.id !in minions.map { it.id } }
        for (e in effects) {
            state.effects +=
                ActiveEffect(
                    state.nextId++, e.cardId, e.owner, e.timing, round, e.turn, e.action, e.key,
                    LinkedHashMap(
                        e.data,
                    ),
                )
        }
        val seen = HashSet<Hex>()
        (
            state.heroes.mapNotNull {
                it.at
            } + state.minions.mapNotNull { it.at } + state.pieces.mapNotNull { it.at }
            ).forEach {
            require(seen.add(it)) { "Two obstacles in $it (p.18: never more than one)" }
            require(geo.isSpace(it)) { "$it is not a space of $mapId" }
        }
        when (val p = phase) {
            PositionPhase.TurnStart -> state.step = Step.TURN_START

            is PositionPhase.PlayCards -> {
                state.step = Step.PLAY
                state.stepSeat = p.fromSeat
                for (id in state.heroes.flatMap { h -> h.cards.filter { it.zone == Zone.PLAYED }.map { h.id } }) {
                    require(state.hero(id)!!.seat < p.fromSeat) {
                        "$id has played a card but plays after seat ${p.fromSeat}"
                    }
                }
            }

            is PositionPhase.Resolving -> {
                state.step = if (p.applyRevealEffects) Step.REVEAL else Step.RESOLVE
                state.revealed = !p.applyRevealEffects
                if (state.revealed) {
                    for (h in state.heroes) {
                        if (h.cards.any {
                                it.zone == Zone.PLAYED &&
                                    data.card(it.id).primary == dev.quinton.goa.core.model.catalog.PrimaryAction.ATTACK
                            }
                        ) {
                            state.turnRecord.playedAttack += h.id
                        }
                    }
                }
                p.next?.let { state.vars[NEXT_ACTOR] = it }
            }

            PositionPhase.EndOfRound -> {
                state.step = Step.EOR_EFFECTS
                state.turn = 4
                state.revealed = true
            }
        }
        for (h in state.heroes) {
            state.turnRecord.startPositions[h.id] = h.at
        }
        val setup = GameSetup(
            mapId = mapId,
            seats = heroes.map { SeatSetup(it.heroId, it.team, it.controller, it.handicap) },
            length = length,
            tieBreaker = tieBreaker,
        )
        return engine.fromState(setup, state)
    }

    private fun buildHero(hb: HeroBuilder, seat: Int): HeroState {
        val hero = engine.data.catalogHero(hb.heroId)
        val h = HeroState(hb.heroId, hb.team, seat, hb.at, hb.level, hb.coins, handicap = hb.handicap)
        val start = StartingCards.of(hero).map { it.id }.toMutableSet()
        var boxed: String? = null
        if (hb.handicap) {
            hero.handicap?.let { hc ->
                val replaced = start.firstOrNull { id -> hero.cards.first { it.id == id }.color == hc.color }
                if (replaced != null) {
                    start -= replaced
                    start += hc.id
                    boxed = replaced
                }
            }
        }
        // A colour slot holds one card: placing a card of a colour moves the starting card of that colour out.
        val placedColours = hb.placed.keys.mapNotNull { id ->
            engine.data.card(id).takeIf { it.tier in 1..3 }?.color
        }.toSet()
        for (card in hero.allCards) {
            val explicit = hb.placed[card.id]
            // Extras before the purple rule: Gydion's purple Wish spell is not the Ultimate.
            val zone = when {
                explicit != null -> explicit.first
                card in hero.extras -> Zone.EXTRA
                card.color == CardColor.PURPLE -> if (hb.ultimate) Zone.ULTIMATE else Zone.DECK
                card.id in start && card.tier in 1..3 && card.color in placedColours -> Zone.DECK
                card.id in start && !hb.exactHand -> Zone.HAND
                card.id in start && hb.exactHand -> Zone.DISCARD
                card.id == hero.handicap?.id || card.id == boxed -> Zone.BOX
                else -> Zone.DECK
            }
            val slot = explicit?.second ?: 0
            h.cards += CardInst(
                card.id,
                zone,
                slot = slot,
                faceDown = card.id in hb.faceDown,
                played = zone == Zone.PLAYED || zone == Zone.RESOLVED,
                tag = if (zone == Zone.EXTRA) hb.extraTags[card.id] ?: "box" else null,
            )
        }
        h.markers += hb.markers
        h.vars += hb.vars
        h.counters += hb.counters
        return h
    }
}
