package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.puzzles.KnownPlays
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 18, mined from self-play (g2672_r4_BLUE_defeat-brogan; ADR-052, ENCODING.md "Full rounds"). */
object R18APriceOnBrogan : Puzzle() {
    override val concept = "R18"

    override val known = KnownPlays(
        cards = mapOf(
            "brogan" to listOf("brogan-green-2b", "brogan-silver", "brogan-gold", "brogan-red-1"),
            "razzle" to listOf("razzle-green-1", "razzle-blue-2b", "razzle-gold", "razzle-silver"),
        ),
        actions = mapOf(
            "brogan" to mapOf(
                1 to listOf(
                    Choice.Space(Hex(-6, -3)),
                    Choice.Action(ActionType.FAST_TRAVEL),
                    Choice.Space(Hex(5, -11)),
                ),
                2 to listOf(Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(5, -8))),
            ),
            "razzle" to mapOf(
                1 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(4, -1))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(6, -4))),
                3 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Space(Hex(6, -5)),
                        Choice.Space(Hex(5, -4)),
                        Choice.Space(Hex(7, -5)),
                    ),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r18",
        number = 18,
        title = "A Price on Brogan",
        difficulty = 3,
        theme = "Bounty set up for a later shot, spending the enemy's hand",
        heroes = listOf("ursafar", "bain", "brogan", "razzle"),
        goal = PuzzleGoal.DefeatHero("brogan", Deadline.END_OF_ROUND, "Defeat Brogan by the end of the round."),
        intro = "Round 4. You play Ursafar and Bain for Blue. Red has 2 Life counters left. Brogan is off the board " +
            "and respawns in turn 1. Hand Crossbow, Bain's ranged attack, can only reach a hero who carries a " +
            "Bounty marker.",
        nudges = R18_NUDGES.first(),
        nudgesByTurn = R18_NUDGES,
        explanation =
            "Turn 1: Brogan respawns and fast travels away as planned. Nothing can reach him yet. Use Movement on" +
                " both cards to bring Ursafar and Bain up among the minions." +
                "\n\n" +
                "Turn 2: Each of you kills a Red melee minion. Claws That Catch makes Ursafar enraged and stays " +
                "active this round. After Dead or Alive's attack, Bain gives Brogan the Bounty marker: he is in " +
                "radius 4. That marker is the whole point. Hand Crossbow in turn 4 can only target him at range " +
                "because of it." +
                "\n\n" +
                "Turn 3: Bain goes first and uses Get over here! to move Ursafar one space. When Brogan has fast " +
                "travelled in, Angry Roar performs the primary action of Claws That Catch, which moves Ursafar next " +
                "to Brogan (enraged) and attacks for 4. Brogan must spend a card to survive it: with Mad Dash or " +
                "Mighty Punch he defends, without one he is defeated at once." +
                "\n\n" +
                "Turn 4: Brogan's fourth card is played and his spare has gone on that defense. Bain's initiative " +
                "ties his, and the coin shows Blue, so Bain acts first (p.7): Hand Crossbow shoots at Attack 5, " +
                "Brogan has no card to defend with, and he is defeated (p.13). His own card goes unresolved, and Red " +
                "spends its last 2 Life counters too." +
                "\n\n" +
                "The computer at strength 7, playing your heroes, doesn't get there: the plan needs the marker in " +
                "turn 2 and the forced defense in turn 3.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense",
            "p.7 Hero Respawn",
            "p.7 Check the initiative",
        ),
        known = known.display(
            mapOf(
                "brogan" to mapOf(
                    1 to "Respawns in the Red Throne Zone, then fast travels to the space outlined.",
                    2 to "Plays Bulwark as its skill.",
                    3 to "Fast travels to the space outlined.",
                ),
                "razzle" to mapOf(
                    1 to "Fast travels to the space outlined.",
                    2 to "Moves to the space outlined.",
                    3 to "Attacks the Blue melee minion, then spawns three more of herself on the spaces outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("ursafar", "bain")

    override fun PositionBuilder.setUp() {
        round = 4
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 4)
        hero("brogan", Team.RED, null) {
            level = 3
            coins = 2
            hand("brogan-gold", "brogan-silver", "brogan-red-1", "brogan-green-2b", "brogan-blue-2a")
            items("brogan-green-2a", "brogan-blue-2b")
            deck("brogan-green-1", "brogan-blue-1")
        }
        hero("razzle", Team.RED, Hex(1, -7)) {
            level = 2
            coins = 1
            hand("razzle-gold", "razzle-silver", "razzle-red-1", "razzle-green-1", "razzle-blue-2b")
            items("razzle-blue-2a")
            deck("razzle-blue-1")
        }
        hero("ursafar", Team.BLUE, Hex(5, -8)) {
            level = 3
            hand("ursafar-gold", "ursafar-silver", "ursafar-red-1", "ursafar-green-2a", "ursafar-blue-2b")
            items("ursafar-green-2b", "ursafar-blue-2a")
            deck("ursafar-green-1", "ursafar-blue-1")
        }
        hero("bain", Team.BLUE, Hex(4, -6)) {
            level = 4
            coins = 2
            hand("bain-gold", "bain-silver", "bain-red-2b", "bain-green-2b", "bain-blue-2a")
            items("bain-red-2a", "bain-green-2a", "bain-blue-2b")
            deck("bain-red-1", "bain-green-1", "bain-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -11))
        minion(Team.RED, MinionType.MELEE, Hex(7, -10))
        minion(Team.RED, MinionType.MELEE, Hex(8, -9))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.RED, MinionType.RANGED, Hex(6, -7))
        minion(Team.RED, MinionType.MELEE, Hex(7, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(7, -5))
    }

    override val solution = listOf(
        card("ursafar-red-1"),
        card("bain-blue-2a"),
        action(ActionType.MOVEMENT),
        space(7, -9),
        action(ActionType.MOVEMENT),
        space(7, -11),
        card("ursafar-gold"),
        card("bain-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        hero("brogan"),
        card("ursafar-silver"),
        card("bain-silver"),
        action(ActionType.PRIMARY),
        hero("ursafar"),
        action(ActionType.PRIMARY),
        space(6, -9),
        card("ursafar-green-2a"),
        card("bain-red-2b"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("ursafar-blue-2b"),
                card("bain-blue-2a"),
                hero("ursafar"),
                action(ActionType.PRIMARY),
                space(6, -9),
                action(ActionType.MOVEMENT),
                space(6, -5),
                card("ursafar-green-2a"),
                card("bain-gold"),
                action(ActionType.PRIMARY),
                decline(),
                action(ActionType.PRIMARY),
                card("ursafar-gold"),
                card("bain-red-2b"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(6, -8),
                card("ursafar-silver"),
                card("bain-silver"),
                action(ActionType.HOLD),
                action(ActionType.PRIMARY),
                space(8, -10),
            ),
        ),
    )
}

private val R18_NUDGES = listOf(
    listOf(
        "Brogan will arrive far away. You won't catch him with Ursafar alone. Which of your cards reaches" +
            " a hero who isn't next to you?",
        "Move both heroes up now, while the enemy is arriving: you want minions to attack next turn and " +
            "Brogan close by turn 3.",
        "Play Prey Drive on Ursafar and Dead Man's Hand on Bain, and take the Movement of both.",
    ),
    listOf(
        "Hand Crossbow needs a Bounty marker on its target. Which card gives one?",
        "Dead or Alive's after-the-attack gives the Bounty to an enemy hero in radius 4. Ursafar's card " +
            "also matters later: it stays active, and he is enraged from now on.",
        "Play Claws That Catch on Ursafar and Dead or Alive on Bain. Attack minions with both, and give " +
            "Brogan the Bounty.",
    ),
    listOf(
        "Brogan comes to you this turn. Count the cards he has left, and what happens if he has to spend " +
            "one.",
        "Angry Roar repeats an active card's primary action when you are enraged. Claws That Catch is " +
            "active.",
        "Play Angry Roar on Ursafar and Get over here! on Bain. Bain pulls Ursafar one space, and Angry " +
            "Roar performs Claws That Catch on Brogan.",
    ),
    listOf(
        "Brogan has played a card each turn and spent one on defense. What does he have left?",
        "Bain ties Brogan on initiative and the coin shows Blue. Hand Crossbow is Attack 5, and his " +
            "Bounty makes him a legal target in range.",
        "Play Eyes on the Prey on Ursafar and Hand Crossbow on Bain, and shoot Brogan.",
    ),
)
