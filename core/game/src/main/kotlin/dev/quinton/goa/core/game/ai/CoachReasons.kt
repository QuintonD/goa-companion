package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachReason
import dev.quinton.goa.core.game.CoachReasonKind
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.heavyImmune
import dev.quinton.goa.core.game.engine.lowestTier
import dev.quinton.goa.core.game.engine.minionModifier
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/** What a sentence claims about the playouts, kept so the survey can check it against the events. */
internal enum class PlayoutClaim {
    DEFEATS_HERO,
    SEAT_SAFE,
    PUSH_OURS,
    PUSH_THEIRS,
    MINIONS,
    GAME_OVER,
    LEVEL,
    FOCUS,
    MECHANIC,
    ACTS_BEFORE,
}

internal class Why(
    val reason: CoachReason,
    /** The evaluation's parts this reason speaks of: the gap between the options picks which to name. */
    val terms: Set<Term>,
    val claim: PlayoutClaim? = null,
    val subject: String? = null,
    val share: Double = 0.0,
)

/**
 * Why an option, from engine events and printed values (ADR-051). [forOption] compares it with another:
 * each sentence is a fact the playouts or the printed cards show, with its rule and page, and the
 * reasons are ordered by which of the evaluation's twelve parts ([Term]) the gap between the two options
 * lies in. No reason is better than a vague one: every generator can stay silent.
 */
internal class ReasonFinder(private val f: CoachFacts) {
    private val seat = f.seat
    private val team = f.team
    private val d = f.d
    private val data = f.data

    fun forOption(x: Choice, y: Choice?): List<Why> {
        val always = printedFacts(x, y)
        val played = if (y == null) emptyList() else fromPlayouts(x, y)
        val gap = if (y == null) DoubleArray(Term.entries.size) else termGap(x, y)
        val ranked = played.sortedByDescending { w -> w.terms.maxOfOrNull { gap[it.ordinal] } ?: 0.0 }
        // What the playouts found first where the fact is only about the rule (Fast Travel at a move).
        return if (d.kind == DecisionKind.CHOOSE_ACTION) ranked + always else always + ranked
    }

    /** Facts of the decision itself, true whatever the playouts do. */
    private fun printedFacts(x: Choice, y: Choice?): List<Why> = when (d.kind) {
        DecisionKind.DEFEND -> defense(x, y)
        DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE -> upgrade(x)
        DecisionKind.RESPAWN -> respawn(x, y)
        DecisionKind.CHOOSE_ACTION -> fastTravel(x)
        DecisionKind.CHOOSE_UNIT -> target(x)
        else -> emptyList()
    }

    private fun fromPlayouts(x: Choice, y: Choice): List<Why> = listOfNotNull(
        kill(x, y),
        focusFire(x, y),
        safer(x, y),
        pushWon(x, y),
        pushStopped(x, y),
        gameDecided(x, y),
        minions(x, y),
        minionBattle(x, y),
        level(x, y),
        initiative(x, y),
        keepDefender(x, y),
        modifier(x, y),
        reach(x, y),
        position(x, y),
    ) + mechanics(x, y)

    /** Mean over the paired samples of x's parts minus y's, from the team's side, scaled as the score is. */
    private fun termGap(x: Choice, y: Choice): DoubleArray {
        val out = DoubleArray(Term.entries.size)
        val ys = f.leaves(y).associateBy { it.sample }
        var n = 0
        for (lx in f.leaves(x)) {
            val ly = ys[lx.sample] ?: continue
            val a = f.ev.context(lx.end.state).termsRed()
            val b = f.ev.context(ly.end.state).termsRed()
            for (t in Term.entries) {
                val scale = f.ev.knobs.scales[t] ?: 1.0
                out[t.ordinal] += (a[t.ordinal] - b[t.ordinal]) * scale
            }
            n++
        }
        val sign = if (team == Team.RED) 1.0 else -1.0
        if (n > 0) for (i in out.indices) out[i] *= sign / n
        return out
    }

    private fun why(
        kind: CoachReasonKind,
        text: String,
        detail: String,
        terms: Set<Term> = emptySet(),
        claim: PlayoutClaim? = null,
        subject: String? = null,
        share: Double = 0.0,
    ) = Why(CoachReason(kind, text, detail), terms, claim, subject, share)

    // ---- Defending (p.14) ---------------------------------------------------------------------

    private fun holds(cardId: String, total: Int): Boolean? = when (d.context.blockPreview[cardId]) {
        true -> true
        false -> false
        null -> d.context.defensePreview[cardId]?.let { it >= total }
    }

    private fun defense(x: Choice, y: Choice?): List<Why> {
        val total = d.context.attackTotal ?: return emptyList()
        val out = ArrayList<Why>()
        val options = d.options.filterIsInstance<Choice.Card>()
        when (x) {
            is Choice.Card -> {
                val card = f.cardName(x.cardId) ?: return emptyList()
                val value = d.context.defensePreview[x.cardId]
                when (d.context.blockPreview[x.cardId]) {
                    true -> out += why(
                        CoachReasonKind.BLOCK,
                        "$card's Block holds against this ${attackType()} attack, whatever its total.",
                        "If the Block condition is fulfilled you successfully defend, regardless of the attack " +
                            "value (p.14).",
                    )

                    false -> out += why(
                        CoachReasonKind.BLOCK,
                        "$card's Block condition isn't met by this ${attackType()} attack.",
                        "A Block card defends only by fulfilling its condition; if not, you are defeated (p.14).",
                    )

                    null -> if (value != null) {
                        val mod = d.context.minionModifier ?: 0
                        val modified = if (mod == 0) "" else " Minions count ${signed(mod)} in that total (p.14)."
                        out += if (value >= total) {
                            why(
                                CoachReasonKind.DEFENSE_HOLDS,
                                "$card holds: Defense $value against Attack $total.",
                                "A Defense total equal to or higher than the Attack total defends successfully " +
                                    "(p.14).$modified",
                            )
                        } else {
                            why(
                                CoachReasonKind.DEFENSE_FALLS,
                                "$card falls: Defense $value against Attack $total.",
                                "The card is discarded and ${f.name} is still defeated (p.14).$modified",
                            )
                        }
                    }
                }
                val other = (y as? Choice.Card)
                if (other != null && holds(x.cardId, total) == true && holds(other.cardId, total) == true) {
                    out += why(
                        CoachReasonKind.DEFENSE_CHEAPER,
                        "$card is enough, so ${f.cardName(other.cardId)} stays in your hand.",
                        "Cards used to defend are always discarded from your hand (p.10).",
                    )
                }
            }

            is Choice.Decline -> {
                out += defeatCost()
                if (options.isNotEmpty() && options.all { holds(it.cardId, total) == false }) {
                    out += why(
                        CoachReasonKind.DEFENSE_WASTED,
                        "No card in your hand holds Attack $total, so a defense would only cost you the card.",
                        "If you cannot defend successfully you are defeated (p.14); the card used is discarded (p.10).",
                    )
                }
            }

            else -> Unit
        }
        return out
    }

    private fun attackType() = if (d.context.rangedAttack == true) "ranged" else "non-ranged"

    private fun signed(n: Int) = if (n > 0) "+$n" else "−${-n}"

    private fun plural(n: Int, what: String) = "$n $what${if (n == 1) "" else "s"}"

    /** What the hero's defeat costs, by the printed table (p.13). */
    private fun defeatCost(): Why {
        val tier = f.g.lowestTier(seat)
        val life = f.view.lifeCounters[team] ?: 0
        val attacker = d.context.attackerId?.let(data::heroName) ?: "The attacker"
        val coins = f.me.level
        val spend = if (life <= tier) {
            "your team spends its last Life counter and loses the game"
        } else {
            "your team spends ${plural(tier, "Life counter")} (${life - tier} left)"
        }
        return why(
            CoachReasonKind.LIFE_COST,
            "Defeated, $spend and $attacker gains ${plural(coins, "coin")}.",
            "A defeated hero's team spends Life counters equal to its lowest card Tier; the attacker collects " +
                "coins equal to its level (p.13).",
        )
    }

    // ---- Levels and items (p.15, p.16) ---------------------------------------------------------

    private fun statWord(s: Stat) = s.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun printed(c: Card): String = listOfNotNull(
        c.values.attack?.let { "Attack $it" },
        c.values.defense?.let { "Defense $it" },
        c.values.movement?.let { "Movement $it" },
        c.values.range?.let { "Range $it" },
        c.values.radius?.let { "Radius $it" },
        c.initiative?.let { "Initiative $it" },
    ).joinToString(", ")

    private fun hasIcon(c: Card, s: Stat): Boolean = when (s) {
        Stat.ATTACK -> c.values.attack != null
        Stat.DEFENSE -> c.values.defense != null
        Stat.INITIATIVE -> c.initiative != null
        Stat.RANGE -> c.values.range != null
        Stat.MOVEMENT -> c.values.movement != null
        Stat.RADIUS -> c.values.radius != null
    }

    private fun upgrade(x: Choice): List<Why> {
        val id = (x as? Choice.Card)?.cardId ?: return emptyList()
        val card = data.cardOrNull(id) ?: return emptyList()
        val itemRule = "The card you keep goes to hand; the other becomes an item: +1 on that icon on every card " +
            "that has it, " +
            "nothing on a card without it (p.15, p.16)."
        if (d.kind == DecisionKind.UPGRADE_CARD) {
            val next = d.context.upgrades[id].orEmpty().mapNotNull(data::cardOrNull)
            if (next.isEmpty()) return emptyList()
            val offers = next.joinToString(" or ") { c ->
                c.name + (c.item?.let { " (item: +1 ${statWord(it)})" } ?: "")
            }
            return listOf(why(CoachReasonKind.UPGRADE, "${card.name} can become $offers.", itemRule))
        }
        val other = d.options.filterIsInstance<Choice.Card>().firstOrNull { it.cardId != id }
            ?.let { data.cardOrNull(it.cardId) }
        val item = other?.item
        val itemText = if (other != null && item != null) {
            val hand = (f.myHand.filter { it.id != other.id && it.id != id } + card)
            val fit = hand.count { hasIcon(it, item) }
            " ${other.name} becomes an item: +1 ${statWord(item)} on $fit of your ${hand.size} cards."
        } else {
            ""
        }
        return listOf(why(CoachReasonKind.UPGRADE, "${card.name}: ${printed(card)}.$itemText", itemRule))
    }

    private fun levelCost(level: Int) = level

    /** The Tier a new level's card has (p.15's table); level 8 is the Ultimate. */
    private fun tierWords(level: Int): String = when (level) {
        in 2..4 -> "a Tier II card and an item"
        in 5..7 -> "a Tier III card and an item"
        else -> "the Ultimate"
    }

    private fun level(x: Choice, y: Choice): Why? {
        val level = f.me.level
        if (level >= MAX_LEVEL || f.me.coins >= levelCost(level)) return null
        val reaches = { l: Leaf -> (l.end.state.hero(seat)?.coins ?: 0) >= levelCost(level) || levelled(l) }
        val sx = f.share(x, reaches)
        if (sx < MOST || sx - f.share(y, reaches) < CLEAR_GAP) return null
        return why(
            CoachReasonKind.LEVEL,
            "The coins bring you level ${level + 1} at the end of the round: ${tierWords(level + 1)}.",
            "Level ${level + 1} costs ${plural(levelCost(level), "coin")}; at the end of the round you must buy " +
                "every level you can afford (p.15).",
            setOf(Term.LEVELS, Term.COINS),
            PlayoutClaim.LEVEL,
            share = sx,
        )
    }

    private fun levelled(l: Leaf) = f.events(l).any { it is GameEvent.LevelledUp && it.heroId == seat }

    // ---- Hero kills and danger (p.13, p.14) ----------------------------------------------------

    private fun defeatsBySeat(l: Leaf): List<String> = f.events(l).filterIsInstance<GameEvent.Defeated>()
        .filter { it.byHeroId == seat && f.enemy(it.unitId) }.map { it.unitId }

    private fun kill(x: Choice, y: Choice): Why? {
        val sx = f.share(x) { defeatsBySeat(it).isNotEmpty() }
        val sy = f.share(y) { defeatsBySeat(it).isNotEmpty() }
        if (sx < MOST || sx - sy < CLEAR_GAP || holds(x)) return null
        val victim = f.leaves(x).flatMap(::defeatsBySeat).groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy({ -it.value }, { it.key })).first().key
        val hero = f.view.heroes.first { it.heroId == victim }
        val tier = levelTier(hero.level)
        return why(
            CoachReasonKind.KILL,
            "You defeat ${data.heroName(victim)} in most playouts of this.",
            "You collect ${plural(hero.level, "coin")} (their level) and their team spends " +
                "${plural(tier, "Life counter")} (their lowest card Tier) (p.13).",
            setOf(Term.LIFE, Term.COINS),
            PlayoutClaim.DEFEATS_HERO,
            victim,
            sx,
        )
    }

    private fun levelTier(level: Int) = when (level) {
        in 1..3 -> 1
        in 4..6 -> 2
        else -> 3
    }

    private fun holds(x: Choice) = (x as? Choice.Action)?.action == ActionType.HOLD

    /** A hero of [enemy] attacked by both: a teammate's attack and the seat's on the same turn bring it down. */
    private fun focusFire(x: Choice, y: Choice): Why? {
        fun setUp(l: Leaf): String? {
            val ev = f.events(l)
            val attackedBy = HashMap<String, MutableSet<String>>()
            for (e in ev) {
                if (e is GameEvent.Attacked && f.enemy(e.targetId)) {
                    attackedBy.getOrPut(e.targetId) { HashSet() } += e.attackerId
                }
                if (e is GameEvent.Defeated && f.enemy(e.unitId)) {
                    val by = attackedBy[e.unitId].orEmpty()
                    if (seat in by && by.any(f::ally)) return e.unitId
                }
            }
            return null
        }
        val sx = f.share(x) { setUp(it) != null }
        if (sx < MOST || sx - f.share(y) { setUp(it) != null } < CLEAR_GAP) return null
        val target = f.leaves(x).mapNotNull(::setUp).groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy({ -it.value }, { it.key })).first().key
        return why(
            CoachReasonKind.FOCUS_FIRE,
            "Together with a teammate's attack this brings ${data.heroName(target)} down in most playouts.",
            "Each defense costs a card from hand, and a hero with no card left cannot defend (p.13, p.14).",
            setOf(Term.LIFE, Term.EXPOSURE),
            PlayoutClaim.FOCUS,
            target,
            sx,
        )
    }

    private fun seatDefeated(l: Leaf) = f.events(l).any { it is GameEvent.Defeated && it.unitId == seat }

    /** This option keeps the hero alive where the other gets it defeated. */
    private fun safer(x: Choice, y: Choice): Why? {
        val sy = f.share(y, ::seatDefeated)
        val sx = f.share(x, ::seatDefeated)
        if (sy < MOST || sy - sx < CLEAR_GAP) return null
        val tier = f.g.lowestTier(seat)
        return why(
            CoachReasonKind.DANGER,
            "You stay on the board with this; with \"${f.label(y)}\" you are defeated in most playouts.",
            "A defeat costs your team ${plural(tier, "Life counter")} (your lowest card Tier) and gives the " +
                "attacker ${plural(f.me.level, "coin")} (p.13).",
            setOf(Term.LIFE, Term.EXPOSURE, Term.PRESENCE),
            PlayoutClaim.SEAT_SAFE,
            share = 1 - sx,
        )
    }

    // ---- The lane (p.8) ------------------------------------------------------------------------

    private fun pushes(l: Leaf, t: Team) = f.events(l).any { it is GameEvent.LanePushed && it.winner == t }

    private fun pushWon(x: Choice, y: Choice): Why? {
        val sx = f.share(x) { pushes(it, team) }
        if (sx < MOST || sx - f.share(y) { pushes(it, team) } < CLEAR_GAP) return null
        return why(
            CoachReasonKind.PUSH_WON,
            "Your team pushes the lane in most playouts of this.",
            "Defeating or removing the last enemy minion in the Battle Zone pushes the Lane at once; the Minion " +
                "Battle can too (p.8).",
            setOf(Term.LANE, Term.MINION_MARGIN),
            PlayoutClaim.PUSH_OURS,
            share = sx,
        )
    }

    private fun pushStopped(x: Choice, y: Choice): Why? {
        val sy = f.share(y) { pushes(it, team.opponent) }
        val sx = f.share(x) { pushes(it, team.opponent) }
        if (sy < MOST || sy - sx < CLEAR_GAP) return null
        return why(
            CoachReasonKind.PUSH_GIVEN,
            "\"${f.label(y)}\" gives the other team the lane push in most playouts; this doesn't.",
            "The team with minions left in the Battle Zone wins the push, and the fight moves one Zone towards the " +
                "loser's Throne (p.8).",
            setOf(Term.LANE, Term.MINION_MARGIN),
            PlayoutClaim.PUSH_THEIRS,
            share = sy,
        )
    }

    /** The game ends the same way in most playouts of both: this card doesn't change it. */
    private fun gameDecided(x: Choice, y: Choice): Why? {
        if (d.kind != DecisionKind.DEFEND) return null
        fun over(l: Leaf, t: Team) = f.events(l).any { it is GameEvent.GameOver && it.winner == t }
        for (t in Team.entries) {
            val sx = f.share(x) { over(it, t) }
            val sy = f.share(y) { over(it, t) }
            if (sx >= NEARLY_ALL && sy >= NEARLY_ALL) {
                val who = if (t == team) "your team wins" else "the other team wins"
                return why(
                    CoachReasonKind.DEFENSE_WASTED,
                    "In nearly every playout $who this turn whichever you choose, so the card matters little.",
                    "Pushing the Lane into the enemy Throne Zone, the last Wave counter or the last Life counter " +
                        "ends the game at once (p.8, p.13).",
                    setOf(Term.LANE),
                    PlayoutClaim.GAME_OVER,
                    t.name,
                    sx,
                )
            }
        }
        return null
    }

    private fun minionsBySeat(l: Leaf): List<GameEvent.Defeated> = f.events(l).filterIsInstance<GameEvent.Defeated>()
        .filter { e ->
            e.byHeroId == seat && !f.isHero(e.unitId) &&
                (f.state.minion(e.unitId)?.team ?: l.end.state.minion(e.unitId)?.team) == team.opponent
        }

    private fun minions(x: Choice, y: Choice): Why? {
        if (holds(x)) return null
        val mx = f.leaves(x).map { minionsBySeat(it).size }.average()
        val my = f.leaves(y).map { minionsBySeat(it).size }.average()
        if (mx.isNaN() || my.isNaN() || mx - my < MINION_GAP) return null
        val heavy = f.leaves(x).flatMap(::minionsBySeat).any { e ->
            (f.state.minion(e.unitId))?.type == MinionType.HEAVY
        }
        val coins = if (heavy) "2 coins, a Heavy 4" else "2 coins"
        return why(
            CoachReasonKind.MINION,
            "You take a minion in most playouts of this: $coins.",
            "Minions cannot defend: an attack defeats them. Melee and ranged minions are worth 2 coins, Heavy 4 " +
                "(p.13).",
            setOf(Term.MINION_MARGIN, Term.COINS, Term.LANE, Term.OPPORTUNITY),
            PlayoutClaim.MINIONS,
            share = f.share(x) { minionsBySeat(it).isNotEmpty() },
        )
    }

    /** The arithmetic of the coming Minion Battle, from the board (p.8), when minions are what this is about. */
    private fun minionBattle(x: Choice, y: Choice): Why? {
        if (f.view.turn < BATTLE_TURN) return null
        val mx = f.leaves(x).map { minionsBySeat(it).size }.average()
        val my = f.leaves(y).map { minionsBySeat(it).size }.average()
        if (mx.isNaN() || my.isNaN() || mx - my < MINION_GAP) return null
        val lane = f.state.battleZones.keys.singleOrNull() ?: return null
        val (ours, theirs) = f.battleCount(lane) ?: return null
        val now = when {
            ours > theirs -> "they would remove ${ours - theirs}"
            ours < theirs -> "your team would remove ${theirs - ours}"
            else -> "the battle would end with no effect"
        }
        val after = theirs - 1
        val oneMore = when {
            after <= 0 -> "One more enemy minion gone is their last: the lane is pushed at once."

            ours - after >= after ->
                "One more gone and they must remove every minion they have left: the lane is " +
                    "pushed."

            ours > after -> "One more gone and they would remove ${ours - after}."

            ours == after -> "One more gone and the battle is even."

            else -> "One more gone and your team would remove ${after - ours}."
        }
        return why(
            CoachReasonKind.MINION_BATTLE,
            "Minion Battle as it stands: your $ours against their $theirs, so $now. $oneMore",
            "At the end of the round the team with fewer minions in the Battle Zone removes the difference; losing " +
                "the last one pushes the Lane (p.8).",
            setOf(Term.LANE, Term.MINION_MARGIN),
        )
    }

    // ---- The card played (p.7, p.14) -----------------------------------------------------------

    /** Enemies the seat acts before, in the playouts of an option (their cards are guesses until the reveal). */
    private fun actsBefore(l: Leaf, enemy: String): Boolean? {
        val reveal =
            f.events(l).firstOrNull { it is GameEvent.CardsRevealed } as? GameEvent.CardsRevealed ?: return null
        val order = reveal.played.keys.toList()
        val me = order.indexOf(seat)
        val them = order.indexOf(enemy)
        if (me < 0 || them < 0 || reveal.played[seat] == null || reveal.played[enemy] == null) return null
        return me < them
    }

    private fun initiative(x: Choice, y: Choice): Why? {
        if (d.kind != DecisionKind.PLAY_CARD || x !is Choice.Card || y !is Choice.Card) return null
        val mine = data.cardOrNull(x.cardId)?.initiative ?: return null
        val other = data.cardOrNull(y.cardId)?.initiative ?: return null
        for (e in f.view.heroes.filter { it.team != team && it.at != null }.map { it.heroId }.sorted()) {
            val sx = f.share(x) { actsBefore(it, e) == true }
            val sy = f.share(y) { actsBefore(it, e) == true }
            val after = f.share(x) { attackedBy(it, e) }
            val afterY = f.share(y) { attackedBy(it, e) }
            if (sx >= NEARLY_MOST && sy <= 1 - NEARLY_MOST && afterY - after >= CLEAR_GAP) {
                return why(
                    CoachReasonKind.INITIATIVE,
                    "In most guesses Initiative $mine acts before ${data.heroName(e)}, and no attack on you comes; " +
                        "with Initiative $other ${data.heroName(e)} acts first and attacks you.",
                    "The highest Initiative acts first (p.7), so a hero acting first can leave before the attack " +
                        "comes.",
                    setOf(Term.EXPOSURE, Term.LIFE, Term.PRESENCE),
                    PlayoutClaim.ACTS_BEFORE,
                    e,
                    sx,
                )
            }
        }
        return null
    }

    private fun attackedBy(l: Leaf, e: String) =
        f.events(l).any { it is GameEvent.Attacked && it.attackerId == e && it.targetId == seat }

    private fun bestDefense(cards: List<Card>): Int? {
        val items = f.me.itemStats.count { it == Stat.DEFENSE }
        return cards.mapNotNull { CardProfile.of(it).takeIf { p -> p.canDefend }?.defense }.maxOrNull()?.plus(items)
    }

    private fun keepDefender(x: Choice, y: Choice): Why? {
        if (d.kind != DecisionKind.PLAY_CARD || x !is Choice.Card || y !is Choice.Card) return null
        val keptX = f.myHand.filter { it.id != x.cardId }
        val keptY = f.myHand.filter { it.id != y.cardId }
        val bestX = bestDefense(keptX)
        val bestY = bestDefense(keptY)
        val defenderLost = bestX != null && bestY == null
        if (!defenderLost && (bestX == null || bestY == null || bestX - bestY < DEFENSE_GAP)) return null
        val kept = keptX.filter { CardProfile.of(it).canDefend }.maxByOrNull { it.values.defense ?: 0 } ?: return null
        val threatened = f.share(x) { l -> f.events(l).any { it is GameEvent.Attacked && it.targetId == seat } }
        val threatenedY = f.share(y) { l -> f.events(l).any { it is GameEvent.Attacked && it.targetId == seat } }
        if (threatened + threatenedY <= 0.0) return null
        val without = if (bestY == null) "no card to defend with" else "a best Defense of $bestY"
        return why(
            CoachReasonKind.KEEP_DEFENDER,
            "Playing ${f.label(x)} keeps ${kept.name} in hand (Defense $bestX); playing ${f.label(y)} would leave " +
                "you $without.",
            "You defend by discarding a card from your hand; with none, you are defeated (p.13, p.14).",
            setOf(Term.EXPOSURE, Term.HAND, Term.HAND_RATING),
        )
    }

    // ---- Where to stand (p.7, p.11, p.13, p.14) ------------------------------------------------

    private fun ownSpace(c: Choice): Hex? {
        if (d.kind != DecisionKind.CHOOSE_SPACE && d.kind != DecisionKind.RESPAWN) return null
        val subject = d.context.subjectId
        return if (subject == null || subject == seat) (c as? Choice.Space)?.hex else null
    }

    private fun modifierWords(hex: Hex): String {
        val mod = f.g.minionModifier(hex, team, null)
        return when {
            mod == 0 -> "no minion modifier"
            else -> "${signed(mod)} Defense from minions"
        }
    }

    private fun modifier(x: Choice, y: Choice): Why? {
        val hx = ownSpace(x) ?: return null
        val hy = ownSpace(y) ?: return null
        val mx = f.g.minionModifier(hx, team, null)
        val my = f.g.minionModifier(hy, team, null)
        if (mx <= my) return null
        return why(
            CoachReasonKind.MINION_MODIFIER,
            "Here you have ${modifierWords(hx)}; at ${f.label(y)} you would have ${modifierWords(hy)}.",
            "+1 per adjacent friendly melee or heavy minion, −1 per adjacent enemy one, −1 per enemy ranged minion " +
                "within 2 spaces (p.14).",
            setOf(Term.EXPOSURE),
        )
    }

    /** Attack cards the hero still has this round: the played one, then the hand. */
    private fun attackCards(): List<CardProfile> {
        val played = listOfNotNull(f.me.played?.takeIf { f.view.heroes.isNotEmpty() }?.cardId)
            .filter { it !in f.me.resolved.mapNotNull { r -> r.cardId } }
        return (played + f.me.hand.orEmpty()).mapNotNull(data::cardOrNull).map(CardProfile::of).filter { it.attackCard }
    }

    private fun reach(x: Choice, y: Choice): Why? {
        val hx = ownSpace(x) ?: return null
        val hy = ownSpace(y)
        val minions = f.state.minions.filter { it.team != team && it.at != null && !f.g.heavyImmune(it.id) }
        val cards = attackCards()
        fun inReach(h: Hex) = cards.flatMap { c ->
            minions.filter { HexGrid.distance(h, it.at!!) <= c.reach }.map { c to it }
        }
        val here = inReach(hx)
        if (here.isEmpty() || (hy != null && inReach(hy).isNotEmpty())) return null
        val (card, minion) = here.minWith(compareBy({ it.first.id }, { it.second.id }))
        return why(
            CoachReasonKind.REACH,
            "From here ${data.card(card.id).name} reaches ${f.unitName(minion.id)}; from " +
                "${hy?.let { f.spaceLabel(it) } ?: "the other space"} it doesn't.",
            "Minions cannot defend: an attack that reaches one defeats it (p.13).",
            setOf(Term.OPPORTUNITY, Term.MINION_MARGIN),
        )
    }

    private fun battleDistance(h: Hex): Int? {
        val info = MapInfo.of(f.g)
        return f.state.battleZones.values.minOfOrNull { info.distanceToZone(h, it) }
    }

    private fun position(x: Choice, y: Choice): Why? {
        val hx = ownSpace(x) ?: return null
        val hy = ownSpace(y) ?: return null
        val dx = battleDistance(hx) ?: return null
        val dy = battleDistance(hy) ?: return null
        if (dy - dx < POSITION_GAP) return null
        val here = if (dx == 0) "in the Battle Zone" else "${plural(dx, "space")} from the Battle Zone"
        return why(
            CoachReasonKind.POSITION,
            "This puts you $here; the other is ${plural(dy, "space")} away.",
            "The minions fight in the Battle Zone, and the Minion Battle there decides the push (p.8).",
            setOf(Term.PRESENCE, Term.OPPORTUNITY),
        )
    }

    private fun respawn(x: Choice, y: Choice?): List<Why> = when (x) {
        is Choice.Decline -> listOf(
            why(
                CoachReasonKind.RESPAWN,
                "Staying off the board resolves your card without an action.",
                "You may choose not to respawn and resolve your card without performing an action (p.7).",
            ),
        )

        is Choice.Space -> {
            val text = if (y is Choice.Decline) {
                "Respawning here puts you back before your action, so this card still acts."
            } else {
                val away = battleDistance(x.hex)?.let {
                    if (it == 0) {
                        "in the Battle " +
                            "Zone"
                    } else {
                        "${plural(it, "space")} from the Battle Zone"
                    }
                }
                "This Spawn point is ${away ?: "in your Throne Zone"}, with ${modifierWords(x.hex)}."
            }
            listOf(
                why(
                    CoachReasonKind.RESPAWN,
                    text,
                    "If your hero is not on the board, you may respawn on any empty Spawn point in your Throne Zone " +
                        "before performing an action (p.7).",
                ),
            )
        }

        else -> emptyList()
    }

    private fun fastTravel(x: Choice): List<Why> {
        val act = (x as? Choice.Action)?.action ?: return emptyList()
        val rule = "Fast Travel replaces the whole Movement action: to any space in the same or an adjacent Zone, " +
            "with no enemy heroes or minions in the Zone you leave or the one you reach (p.11)."
        if (act == ActionType.FAST_TRAVEL) {
            val card = d.sourceCardId?.let(data::cardOrNull)
            val red = card?.color == CardColor.RED || card?.color == CardColor.GOLD
            val colour = if (card?.color == CardColor.RED) "Red" else "Gold"
            return listOf(
                why(
                    CoachReasonKind.FAST_TRAVEL,
                    if (red) {
                        "Fast Travel is open here, but it spends your $colour card: the rulebook's tip is to avoid that."
                    } else {
                        "Fast Travel is open: no enemy hero or minion stands in your Zone."
                    },
                    rule,
                ),
            )
        }
        if (act != ActionType.MOVEMENT || d.options.contains(Choice.Action(ActionType.FAST_TRAVEL))) return emptyList()
        val at = f.myAt ?: return emptyList()
        val zone = f.g.zoneOf(at) ?: return emptyList()
        val blocker =
            f.view.heroes.filter { it.team != team && it.at?.let(f.g::zoneOf) == zone }.minOfOrNull { it.heroId }
                ?: f.view.minions.firstOrNull { it.team != team && f.g.zoneOf(it.at) == zone }?.id
                ?: return emptyList()
        return listOf(
            why(
                CoachReasonKind.FAST_TRAVEL,
                "Fast Travel is blocked here: ${f.unitName(blocker)} is in your Zone.",
                rule,
            ),
        )
    }

    /** An attack target's public facts: a hero with no card can't defend; a minion can't at all. */
    private fun target(x: Choice): List<Why> {
        val unit = (x as? Choice.Unit)?.unitId ?: return emptyList()
        if (!d.prompt.startsWith("Target")) return emptyList()
        val hero = f.view.heroes.firstOrNull { it.heroId == unit && it.team != team }
        if (hero != null && hero.handCount == 0) {
            return listOf(
                why(
                    CoachReasonKind.NO_DEFENSE_TARGET,
                    "${data.heroName(unit)} has no card in hand, so can't defend.",
                    "If the attacked hero has no cards in hand, that hero is defeated (p.13).",
                ),
            )
        }
        return emptyList()
    }

    // ---- What the hero's own card did, seen through events --------------------------------------

    private class Mechanic(val key: String, val kind: CoachReasonKind, val text: String, val detail: String)

    /** The events of the seat's own action in a playout. */
    private fun ownAction(l: Leaf): List<GameEvent> {
        val out = ArrayList<GameEvent>()
        var open = d.kind != DecisionKind.PLAY_CARD && f.state.acting == seat
        for (e in f.events(l)) {
            when (e) {
                is GameEvent.ActionChosen -> open = e.heroId == seat
                is GameEvent.TurnStarted, is GameEvent.RoundStarted -> open = false
                else -> Unit
            }
            if (open) out += e
        }
        return out
    }

    private fun mechanicsOf(l: Leaf): List<Mechanic> {
        val out = ArrayList<Mechanic>()
        val own = ownAction(l)
        val killed = own.any { it is GameEvent.Defeated }
        for (e in own) {
            when (e) {
                is GameEvent.Placed -> l.end.state.piece(e.pieceId)?.let { p ->
                    when {
                        p.heroBody != null -> out += Mechanic(
                            "body:${p.kind}",
                            CoachReasonKind.BODY,
                            "This places ${p.label}: it counts as ${f.who(p.heroBody!!)}.",
                            "The card text makes it the same hero (card text beats the rules, p.3).",
                        )

                        p.owner == seat && !p.faceDown -> out += Mechanic(
                            "place:${p.kind}",
                            CoachReasonKind.TOKEN,
                            "This places ${p.label}.",
                            if (p.removeAtEndOfRound) {
                                "All tokens are obstacles; unless the card says otherwise they are removed at the end of the round (p.19)."
                            } else {
                                "All tokens are obstacles; the card says this one stays past the end of the round (p.19)."
                            },
                        )
                    }
                }

                is GameEvent.Swapped -> out += Mechanic(
                    "swap:${listOf(e.a, e.b).sorted()}",
                    CoachReasonKind.SWAP,
                    "${f.unitName(e.a, l.end.state).replaceFirstChar { it.uppercase() }} and " +
                        "${f.unitName(e.b, l.end.state)} swap places.",
                    "A swap does not count as movement or placement (p.19).",
                )

                is GameEvent.Pushed -> if (e.unitId != seat) out += forced(e.unitId, l)

                is GameEvent.Moved -> if (e.unitId != seat && f.state.hero(e.unitId) != null) out += forced(e.unitId, l)

                is GameEvent.CardsChanged -> if (f.enemy(e.heroId) && "discard" in e.text.lowercase()) {
                    out += Mechanic(
                        "discard:${e.heroId}",
                        CoachReasonKind.DISCARD,
                        "${data.heroName(e.heroId)} discards a card.",
                        "A discarded card can't be played or used to defend until it is retrieved at the end of the " +
                            "round (p.8, p.10).",
                    )
                }

                is GameEvent.CoinsGained -> if (e.heroId == seat && !killed) {
                    out += Mechanic(
                        "coins:${e.amount}",
                        CoachReasonKind.COINS,
                        "You gain ${plural(e.amount, "coin")} from the card's text.",
                        "Coins buy levels at the end of the round: level N+1 costs N coins (p.15).",
                    )
                }

                else -> Unit
            }
        }
        out += markers(l)
        out += counters(l)
        return out
    }

    private fun forced(unit: String, l: Leaf): Mechanic {
        val name = f.unitName(unit, l.end.state)
        val minion = f.state.minion(unit) != null
        return Mechanic(
            "forced:$unit",
            CoachReasonKind.FORCED_MOVE,
            "This moves $name.",
            if (minion) {
                "Moving a minion doesn't count for the Minion Battle; one outside the Battle Zone comes straight back (p.18)."
            } else {
                "Movement in the card text follows the rules of a Movement action, except that it can't be a Fast Travel (p.11)."
            },
        )
    }

    private fun markers(l: Leaf): List<Mechanic> {
        val out = ArrayList<Mechanic>()
        for (hero in l.end.state.heroes) {
            val before = f.state.hero(hero.id)?.markers.orEmpty().filter {
                it.from == seat
            }.groupingBy { it.kind }.eachCount()
            val after = hero.markers.filter { it.from == seat }.groupingBy { it.kind }.eachCount()
            for ((kind, n) in after) {
                if (n > (before[kind] ?: 0)) {
                    out += Mechanic(
                        "marker:${hero.id}:$kind",
                        CoachReasonKind.MARKER,
                        "${f.who(hero.id).replaceFirstChar { it.uppercase() }} " +
                            "${if (hero.id == seat) "get" else "gets"} a ${markerWord(kind)} marker.",
                        "Markers remind of a card's effect (p.18); they go back to the supply at the end of the " +
                            "round (p.8).",
                    )
                }
            }
        }
        return out
    }

    private fun markerWord(kind: String) = kind.removePrefix("marker_").replace('_', ' ')

    private fun counters(l: Leaf): List<Mechanic> {
        val now = f.state.hero(seat)?.counters.orEmpty()
        val after = l.end.state.hero(seat)?.counters.orEmpty()
        val out = ArrayList<Mechanic>()
        for ((key, n) in after) {
            val was = now[key] ?: 0
            if (n <= was) continue
            val text = when (key) {
                "spellbook" -> "This adds to your spellbook: $n ready."
                "spellsRemoved" -> "This casts a spell: $n cast so far."
                "wishCasts" -> "This casts Wish: $n so far."
                else -> "$key: $was to $n."
            }
            out += Mechanic(
                "counter:$key",
                CoachReasonKind.COUNTER,
                text,
                "A hero's card text decides what its dashboard counters do (card text beats the rules, p.3).",
            )
        }
        return out
    }

    private fun mechanics(x: Choice, y: Choice): List<Why> {
        val lx = f.leaves(x)
        if (lx.isEmpty()) return emptyList()
        val perX = lx.map { l -> mechanicsOf(l).associateBy { it.key } }
        val perY = f.leaves(y).map { l -> mechanicsOf(l).map { it.key }.toSet() }
        val keys = perX.flatMap { it.keys }.distinct().sorted()
        val out = ArrayList<Why>()
        for (k in keys) {
            val sx = perX.count { k in it }.toDouble() / perX.size
            val sy = if (perY.isEmpty()) 0.0 else perY.count { k in it }.toDouble() / perY.size
            if (sx < MOST || sx - sy < CLEAR_GAP) continue
            val m = perX.firstNotNullOf { it[k] }
            out += why(
                m.kind,
                m.text,
                m.detail,
                setOf(Term.PIECES, Term.COUNTERS, Term.COINS, Term.HAND, Term.EXPOSURE, Term.PRESENCE),
                PlayoutClaim.MECHANIC,
                k,
                sx,
            )
        }
        return out
    }

    private companion object {
        const val MOST = 0.6
        const val NEARLY_MOST = 0.75
        const val NEARLY_ALL = 0.8
        const val CLEAR_GAP = 0.5
        const val MINION_GAP = 0.7
        const val DEFENSE_GAP = 2
        const val POSITION_GAP = 2
        const val MAX_LEVEL = 8
        const val BATTLE_TURN = 3
    }
}
