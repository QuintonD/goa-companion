package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.domain.board.BoardSetup
import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.match.Team

/**
 * The opening state of a game (p.5–6 "Game Setup", p.20). Counters by game length and heroes in
 * play (p.5 steps 4–5, p.20; [EngineData.lifeCounters]); the Tie Breaker coin as the setup says
 * (step 3); heroes on their hero spawn points in pick order and minions on the starting Battle
 * Zones' spawn points ([BoardSetup.opening], steps 9–10); hands per step 11 ([StartingCards]),
 * Handicap cards per p.20.
 */
object Setup {
    fun initialState(data: EngineData, setup: GameSetup): GameState {
        require(setup.seats.isNotEmpty()) { "No heroes" }
        require(
            setup.seats.map {
                it.heroId
            }.distinct().size == setup.seats.size,
        ) { "A hero is picked at most once per game" }
        require(
            setup.seats.any {
                it.team == Team.RED
            } && setup.seats.any { it.team == Team.BLUE },
        ) { "Two teams are needed" }
        val geo = data.geometry(setup.mapId)
        val state = GameState(mapId = setup.mapId, length = setup.length, tieBreaker = setup.tieBreaker)
        val lineup = BoardLineup(
            red = setup.seats.filter { it.team == Team.RED }.map { it.heroId },
            blue = setup.seats.filter { it.team == Team.BLUE }.map { it.heroId },
        )
        val board = BoardSetup.opening(geo, lineup) { state.newId("m") }
        val heroAt = board.pieces.filterIsInstance<HeroPiece>().associate { it.heroId to it.at }
        for (p in board.pieces.filterIsInstance<MinionPiece>()) {
            state.minions += MinionState(p.id, p.team, p.type, p.at, p.lane ?: geo.map.lanes.first().id)
        }
        setup.seats.forEachIndexed { seat, s ->
            val hero = data.catalogHero(s.heroId)
            val h = HeroState(s.heroId, s.team, seat, heroAt[s.heroId], handicap = s.handicap)
            h.cards += startingCards(hero, s.handicap)
            state.heroes += h
        }
        for (lane in geo.map.lanes) {
            state.battleZones[lane.id] = lane.startingBattleZone
            state.wavesLeft[lane.id] = data.wavesPerLane(geo.map.lanes.size, setup.length)
        }
        val life = data.lifeCounters(geo.map.lanes.size, setup.length, setup.seats.size)
        state.lifeCounters[Team.RED] = life
        state.lifeCounters[Team.BLUE] = life
        return state
    }

    /**
     * Step 11: "the three Tier I cards and the two basic cards with no Tier … into your hand and return
     * the remaining Tier II, Tier III, and Tier IV cards to your deck. … Return the Handicap card to
     * the box." p.20: a Handicap hero "replace[s] one of their basic cards with a handicap card" —
     * the basic card of the Handicap card's printed colour (Gold; Silver for Takahide).
     */
    fun startingCards(hero: Hero, handicap: Boolean): List<CardInst> {
        val start = StartingCards.of(hero).map { it.id }.toMutableList()
        val out = ArrayList<CardInst>()
        val replaced = if (handicap) {
            hero.handicap?.let { h -> start.firstOrNull { id -> hero.cards.first { it.id == id }.color == h.color } }
        } else {
            null
        }
        for (c in hero.cards) {
            val zone = when {
                c.id == replaced -> Zone.BOX
                c.id in start -> Zone.HAND
                else -> Zone.DECK
            }
            out += CardInst(c.id, zone)
        }
        hero.handicap?.let { out += CardInst(it.id, if (replaced != null) Zone.HAND else Zone.BOX) }
        for (e in hero.extras) out += CardInst(e.id, Zone.EXTRA, tag = "box")
        return out
    }
}
