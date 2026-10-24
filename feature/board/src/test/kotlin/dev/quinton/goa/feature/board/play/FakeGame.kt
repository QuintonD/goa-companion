package dev.quinton.goa.feature.board.play

import dev.quinton.goa.core.data.game.AiPlayers
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.BoardSetup
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.CoachAnalysis
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.MarkerView
import dev.quinton.goa.core.game.MinionView
import dev.quinton.goa.core.game.PieceView
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/*
 * A scripted 2v2 game on a real map for the play screen's tests and screenshots. NOT the rules
 * engine and never shipped: the beats below are a fixed story (with a few branches on the
 * person's choices) that walks through every DecisionKind once: play cards facedown, the reveal,
 * actions, a move with its path, an attack with Defend and defense previews, a defeat and a Life
 * counter, respawn, a same-team tie, "Choose one", a token, a discard, the end of round (Minion
 * Battle, a push, level up with Upgrade and Keep), a second round and a game over.
 *
 * Line-up: Arien and Brogan (Red) against Wasp and Tigerclaw (Blue), in that seat order. The
 * event texts are the fake's own sentences, not the engine's.
 */

const val FAKE_ENGINE_VERSION = 99

val FAKE_LINEUP = BoardLineup(red = listOf("arien", "brogan"), blue = listOf("wasp", "tigerclaw"))

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class Answer(val id: Long, val choice: Choice, val seat: String)

/** The scripted game. Every answer is recorded; fork, save, restore and undo replay them. */
// One scripted story, read top to bottom: it stays in one class and one list on purpose.
@Suppress("LargeClass")
class FakeGameSession(
    override val setup: GameSetup,
    private val map: BoardMap,
) : GameSession {
    private val geometry = BoardGeometry.of(map)
    private val answers = mutableListOf<Answer>()
    private var s = start()
    private val log = mutableListOf<GameEvent>()
    private var beat = 0
    private var current: Decision? = null

    override val pending: Decision? get() = current

    override val events: List<GameEvent> get() = log.toList()

    /** The scripted answer the fake computer gives to the pending question. */
    fun preferred(): Choice? = current?.let { d -> script.getOrNull(beat)?.prefer?.invoke(s, d) ?: d.options.first() }

    override fun view(seat: String?): GameView {
        val viewer = setup.seats.firstOrNull { it.heroId == seat }
        return GameView(
            mapId = map.id,
            viewer = seat,
            round = s.round,
            turn = s.turn,
            phase = s.phase,
            heroes = setup.seats.map { seatSetup ->
                val h = s.heroes.getValue(seatSetup.heroId)
                val own = seatSetup.heroId == seat
                val revealed = s.phase != GamePhase.PLAY_CARDS
                HeroView(
                    heroId = h.id,
                    team = seatSetup.team,
                    controller = seatSetup.controller,
                    at = s.board.pieces.firstOrNull { it.id == h.id }?.at,
                    level = h.level,
                    coins = h.coins,
                    hand = if (own) h.hand else null,
                    handCount = h.hand.size,
                    played = h.played?.let { card ->
                        if (own || revealed) CardView(card) else CardView(null, facedown = true)
                    },
                    resolved = h.resolved.mapIndexed { i, id -> CardView(id, slot = i + 1) },
                    discard = h.discard.map { CardView(it) },
                    items = h.items,
                    ultimate = false,
                    markers = h.markers,
                    activeEffects = h.active,
                )
            },
            minions = s.board.pieces.filterIsInstance<MinionPiece>().mapNotNull { m ->
                m.at?.let { MinionView(m.id, m.team, m.type, it, m.lane ?: "lane-1", immune = m.id in s.immune) }
            },
            pieces = s.tokens,
            battleZones = s.board.battleZones,
            wavesLeft = mapOf(map.lanes.first().id to s.waves),
            lifeCounters = s.life,
            tieBreaker = s.coin,
            initiativeOrder = s.order,
            acting = s.acting,
            winner = s.winner,
            winReason = s.reason,
        ).let { view -> if (viewer == null) view.copy(heroes = view.heroes.map { it.copy(hand = null) }) else view }
    }

    override fun answer(decisionId: Long, choice: Choice): AnswerResult {
        val decision = current ?: return AnswerResult.Rejected("The game is over.")
        if (decisionId != decision.id) return AnswerResult.Rejected("Stale decision $decisionId")
        if (choice !in decision.options) return AnswerResult.Rejected("Not an option: $choice")
        answers += Answer(decisionId, choice, decision.seat)
        val before = log.size
        script[beat].onAnswer(this, s, choice)
        beat++
        advance()
        return AnswerResult.Accepted(log.drop(before))
    }

    override fun save(): SavedGame = SavedGame(
        engineVersion = FAKE_ENGINE_VERSION,
        setup = setup,
        payload = json.encodeToString(ListSerializer(Answer.serializer()), answers),
    )

    override fun fork(): GameSession = FakeGameSession(setup, map).also { copy -> answers.forEach { copy.replay(it) } }

    override fun determinize(viewer: Team, seed: Long): GameSession = fork()

    override fun undoToLastHumanDecision(): Boolean {
        val humans = setup.seats.filter { it.controller == Controller.Human }.map { it.heroId }.toSet()
        val last = answers.indexOfLast { it.seat in humans }
        if (last < 0) return false
        val keep = answers.take(last)
        answers.clear()
        s = start()
        log.clear()
        beat = 0
        emit(GameEvent.RoundStarted(1, "Round 1 begins."))
        emit(GameEvent.TurnStarted(1, 1, "Turn 1 of round 1."))
        advance()
        keep.forEach { replay(it) }
        return true
    }

    internal fun restoreAnswers(payload: String) {
        json.decodeFromString(ListSerializer(Answer.serializer()), payload).forEach { replay(it) }
    }

    private fun replay(answer: Answer) {
        val result = answer(answer.id, answer.choice)
        check(result is AnswerResult.Accepted) { "Replay failed at ${answer.id}: $result" }
    }

    /** Skips beats that don't apply, then asks the next one's question (or ends the game). */
    private fun advance() {
        while (beat < script.size && !script[beat].applies(s)) {
            script[beat].skip(this, s)
            beat++
        }
        current = script.getOrNull(beat)?.takeIf { s.winner == null }?.ask?.invoke(this, s)?.let { asked ->
            asked.copy(id = answers.size + 1L)
        }
    }

    internal fun emit(event: GameEvent) {
        log += event
    }

    // ---- The scripted state ----------------------------------------------------------------

    internal class Hero(
        val id: String,
        val team: Team,
        var hand: MutableList<String>,
        var level: Int = 1,
        var coins: Int = 0,
        var played: String? = null,
        val resolved: MutableList<String> = mutableListOf(),
        val discard: MutableList<String> = mutableListOf(),
        val items: MutableList<String> = mutableListOf(),
        var markers: List<MarkerView> = emptyList(),
        var active: List<String> = emptyList(),
    )

    internal class State(
        var board: BoardState,
        val heroes: Map<String, Hero>,
        var round: Int = 1,
        var turn: Int = 1,
        var phase: GamePhase = GamePhase.PLAY_CARDS,
        var order: List<String> = emptyList(),
        var acting: String? = null,
        var tokens: List<PieceView> = emptyList(),
        var immune: Set<String> = emptySet(),
        var waves: Int = 3,
        var life: Map<Team, Int> = mapOf(Team.RED to 4, Team.BLUE to 2),
        var coin: Team,
        var winner: Team? = null,
        var reason: WinReason? = null,
        /** Branch flags set by the person's choices. */
        var action: ActionType? = null,
        var target: String? = null,
        var upgraded: String? = null,
    )

    private fun start(): State {
        var n = 0
        val board = BoardSetup.opening(geometry, FAKE_LINEUP, newId = { "m${++n}" })
        // Hero pieces get the hero id as their id (the contract's rule).
        val pieces = board.pieces.map { piece ->
            if (piece is dev.quinton.goa.core.model.board.HeroPiece) piece.copy(id = piece.heroId) else piece
        }
        val heroes = setup.seats.associate { seat ->
            seat.heroId to Hero(seat.heroId, seat.team, basicHand(seat.heroId))
        }
        val heavy = pieces.filterIsInstance<MinionPiece>().first { it.team == Team.BLUE && it.type == MinionType.HEAVY }
        return State(
            board = board.copy(pieces = pieces),
            heroes = heroes,
            coin = setup.tieBreaker,
            immune = setOf(heavy.id),
        )
    }

    internal fun at(id: String): Hex? = s.board.pieces.firstOrNull { it.id == id }?.at

    internal fun move(id: String, to: Hex?) {
        s.board = s.board.copy(pieces = s.board.pieces.map { if (it.id == id) it.moveTo(to) else it })
    }

    internal fun remove(id: String) {
        s.board = s.board.copy(pieces = s.board.pieces.filterNot { it.id == id })
    }

    internal fun emptyNeighbours(of: Hex): List<Hex> {
        val occupied = BoardRules.occupancy(s.board)
        return HexGrid.neighbors(of).filter {
            BoardRules.isEmpty(geometry, occupied, it) &&
                it !in s.tokens.map { t -> t.at }
        }
    }

    /** Shortest paths over empty spaces from [from], up to [steps]: destination → path (excl. start). */
    internal fun paths(from: Hex, steps: Int): Map<Hex, List<Hex>> {
        val occupied = BoardRules.occupancy(s.board) - from
        val result = LinkedHashMap<Hex, List<Hex>>()
        var frontier = listOf(from to emptyList<Hex>())
        val seen = mutableSetOf(from)
        repeat(steps) {
            frontier = frontier.flatMap { (hex, path) ->
                HexGrid.neighbors(hex)
                    .filter { it !in seen && BoardRules.isEmpty(geometry, occupied, it) }
                    .map { next ->
                        seen += next
                        (next to path + next).also { result[next] = it.second }
                    }
            }
        }
        return result
    }

    internal val mapGeometry: BoardGeometry get() = geometry
    internal val laneId: String get() = map.lanes.first().id

    internal fun reveal() {
        s.phase = GamePhase.RESOLVE
        s.order = s.heroes.values.sortedWith(
            compareByDescending<Hero> { initiative(it.played) }.thenBy { if (it.team == s.coin) 0 else 1 },
        ).map { it.id }
        // As the engine does: in the order the cards will act.
        val played = s.order.associateWith { s.heroes.getValue(it).played }
        emit(GameEvent.CardsRevealed(played, "Cards are revealed."))
        s.acting = s.order.first()
    }

    /** The hero's card resolves (p.7): it goes to the next Turn slot; the next hero acts. */
    internal fun resolve(heroId: String) {
        val hero = s.heroes.getValue(heroId)
        hero.played?.let { hero.resolved += it }
        hero.played = null
        val next = s.order.indexOf(heroId) + 1
        s.acting = s.order.getOrNull(next)
    }

    internal fun name(id: String): String = NAMES[id] ?: s.board.pieces.firstOrNull { it.id == id }?.let { piece ->
        (piece as? MinionPiece)?.let {
            "the ${if (it.team == Team.RED) "Red" else "Blue"} ${it.type.name.lowercase()} minion"
        }
    } ?: id

    internal val state: State get() = s

    private class Beat(
        val applies: (State) -> Boolean = { true },
        val skip: FakeGameSession.(State) -> Unit = {},
        val prefer: ((State, Decision) -> Choice?)? = null,
        val ask: FakeGameSession.(State) -> Decision,
        val onAnswer: FakeGameSession.(State, Choice) -> Unit,
    )

    private fun decision(
        seat: String,
        kind: DecisionKind,
        prompt: String,
        options: List<Choice>,
        source: String? = null,
        context: DecisionContext = DecisionContext(),
    ) = Decision(0, seat, s.heroes.getValue(seat).team, kind, prompt, source, options, context)

    private fun playCard(heroId: String, preferred: String? = null) = Beat(
        prefer = { _, d -> preferred?.let { Choice.Card(it) }?.takeIf { it in d.options } },
        ask = { st ->
            val hand = st.heroes.getValue(heroId).hand
            decision(
                heroId,
                DecisionKind.PLAY_CARD,
                "Play a card facedown",
                if (hand.isEmpty()) listOf(Choice.Pass) else hand.map { Choice.Card(it) },
            )
        },
        onAnswer = { st, choice ->
            val hero = st.heroes.getValue(heroId)
            if (choice is Choice.Card) {
                hero.hand.remove(choice.cardId)
                hero.played = choice.cardId
                emit(GameEvent.Note("${name(heroId)} plays a card facedown."))
            } else {
                emit(GameEvent.Note("${name(heroId)} has no cards and passes."))
            }
            if (st.heroes.values.all { it.played != null || it.hand.isEmpty() }) reveal()
        },
    )

    private fun chooseAction(heroId: String, actions: List<ActionType>, preferred: ActionType? = null) = Beat(
        prefer = { _, d -> preferred?.let { Choice.Action(it) }?.takeIf { it in d.options } },
        ask = { st ->
            val card = st.heroes.getValue(heroId).played
            decision(
                heroId,
                DecisionKind.CHOOSE_ACTION,
                "Choose an action for ${CARD_NAMES[card] ?: "your card"}",
                actions.map { Choice.Action(it) },
                source = card,
            )
        },
        onAnswer = { st, choice ->
            val action = (choice as Choice.Action).action
            st.action = action
            emit(
                GameEvent.ActionChosen(
                    heroId,
                    st.heroes.getValue(heroId).played.orEmpty(),
                    action,
                    "${name(heroId)} chooses ${action.name.lowercase().replace('_', ' ')}.",
                ),
            )
            if (action == ActionType.HOLD) {
                emit(GameEvent.Note("${name(heroId)} holds."))
                resolve(heroId)
            }
        },
    )

    /** A move: the destinations with the engine's path to each (context.paths keys "q,r"). */
    private fun moveBeat(
        heroId: String,
        steps: Int,
        applies: (State) -> Boolean = { true },
        near: String? = null,
    ) = Beat(
        applies = applies,
        ask = { st ->
            val from = at(heroId)!!
            val reach = if (near != null) {
                emptyNeighbours(at(near)!!).associateWith { listOf(it) }
            } else {
                paths(from, steps).filterValues { it.size <= steps }.entries.take(MAX_MOVES).associate {
                    it.key to
                        it.value
                }
            }
            decision(
                heroId,
                DecisionKind.CHOOSE_SPACE,
                "Choose a space to move to (up to ${if (steps == 1) "1 space" else "$steps spaces"})",
                reach.keys.map { Choice.Space(it) },
                source = st.heroes.getValue(heroId).played,
                context = DecisionContext(subjectId = heroId, paths = reach.mapKeys { (h, _) -> "${h.q},${h.r}" }),
            )
        },
        onAnswer = { _, choice ->
            val to = (choice as Choice.Space).hex
            val from = at(heroId)!!
            move(heroId, to)
            val dist = HexGrid.distance(from, to)
            emit(
                GameEvent.Moved(
                    heroId,
                    listOf(from, to),
                    "${name(heroId)} moves ${if (dist == 1) "1 space" else "$dist spaces"}.",
                ),
            )
            resolve(heroId)
        },
    )

    private val script: List<Beat> = beats()

    // Last: every property above is initialised by now.
    init {
        emit(GameEvent.RoundStarted(1, "Round 1 begins."))
        emit(GameEvent.TurnStarted(1, 1, "Turn 1 of round 1."))
        advance()
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun beats(): List<Beat> = listOf(
        // ---- Round 1, turn 1: simultaneous card play, then the reveal (p.7).
        playCard("arien", "arien-red-1"),
        playCard("brogan", "brogan-red-1"),
        playCard("wasp", "wasp-blue-1"),
        playCard("tigerclaw", "tigerclaw-red-1"),
        // Wasp (initiative 10) moves.
        chooseAction(
            "wasp",
            listOf(ActionType.PRIMARY, ActionType.MOVEMENT, ActionType.FAST_TRAVEL, ActionType.HOLD),
            ActionType.MOVEMENT,
        ),
        moveBeat("wasp", 3),
        // Tigerclaw (9) moves next to Arien.
        chooseAction(
            "tigerclaw",
            listOf(ActionType.PRIMARY, ActionType.MOVEMENT, ActionType.HOLD),
            ActionType.MOVEMENT,
        ),
        moveBeat("tigerclaw", 4, near = "arien"),
        // Arien (8): an attack, or a move with its path, or hold.
        chooseAction(
            "arien",
            listOf(ActionType.PRIMARY, ActionType.MOVEMENT, ActionType.FAST_TRAVEL, ActionType.HOLD),
            ActionType.PRIMARY,
        ),
        Beat(
            applies = { it.action == ActionType.PRIMARY },
            ask = { st ->
                val adjacent = s.board.pieces.filter { p ->
                    val pa = p.at ?: return@filter false
                    HexGrid.adjacent(pa, at("arien")!!) && when (p) {
                        is dev.quinton.goa.core.model.board.HeroPiece -> p.team == Team.BLUE
                        is MinionPiece -> p.team == Team.BLUE
                        else -> false
                    }
                }.map { it.id }.ifEmpty { listOf("tigerclaw") }
                decision(
                    "arien",
                    DecisionKind.CHOOSE_UNIT,
                    "Choose an adjacent enemy to attack",
                    adjacent.map { Choice.Unit(it) },
                    source = st.heroes.getValue("arien").played,
                    context = DecisionContext(
                        highlight = HexGrid.neighbors(at("arien")!!).filter {
                            mapGeometry.isSpace(it)
                        },
                    ),
                )
            },
            prefer = { _, _ -> Choice.Unit("tigerclaw") },
            onAnswer = { st, choice ->
                st.target = (choice as Choice.Unit).unitId
                emit(GameEvent.Attacked("arien", st.target!!, 6, "Arien attacks ${name(st.target!!)} with Attack 6."))
                if (st.target !in st.heroes) {
                    emit(
                        GameEvent.Defeated(
                            st.target!!,
                            "arien",
                            "${name(st.target!!).replaceFirstChar {
                                it.uppercase()
                            }} is defeated.",
                        ),
                    )
                    remove(st.target!!)
                    st.heroes.getValue("arien").coins += 1
                    emit(GameEvent.CoinsGained("arien", 1, "Arien gains 1 coin."))
                    resolve("arien")
                }
            },
        ),
        // Tigerclaw is attacked: Defend (p.14) with each card's Defense total previewed.
        Beat(
            applies = { it.action == ActionType.PRIMARY && it.target == "tigerclaw" },
            prefer = { _, _ -> Choice.Decline("Don't defend") },
            ask = { st ->
                val hand = st.heroes.getValue("tigerclaw").hand
                decision(
                    "tigerclaw",
                    DecisionKind.DEFEND,
                    "Arien attacks you (Attack 6): discard a card to defend",
                    hand.map { Choice.Card(it) } + Choice.Decline("Don't defend"),
                    source = st.heroes.getValue("arien").played,
                    context = DecisionContext(
                        attackerId = "arien",
                        attackTotal = 6,
                        rangedAttack = false,
                        // The +1 minion defense modifier is inside each total, as the contract says.
                        defensePreview = hand.associateWith { id -> DEFENSE[id]?.plus(1) },
                        minionModifier = 1,
                        subjectId = "tigerclaw",
                    ),
                )
            },
            onAnswer = { st, choice ->
                val defender = st.heroes.getValue("tigerclaw")
                if (choice is Choice.Card) {
                    defender.hand.remove(choice.cardId)
                    defender.discard += choice.cardId
                    val total = DEFENSE[choice.cardId]
                    val held = (total ?: 0) >= 6
                    emit(
                        GameEvent.Defended(
                            "tigerclaw",
                            choice.cardId,
                            total,
                            held,
                            "Tigerclaw defends with ${CARD_NAMES[choice.cardId]}: " +
                                if (held) "the attack is blocked." else "not enough.",
                        ),
                    )
                    if (held) {
                        resolve("arien")
                        return@Beat
                    }
                }
                defeat(st, "tigerclaw", by = "arien")
                resolve("arien")
            },
        ),
        moveBeat("arien", 4, applies = { it.action == ActionType.MOVEMENT || it.action == ActionType.FAST_TRAVEL }),
        // Brogan (7): a number and an optional step.
        chooseAction("brogan", listOf(ActionType.PRIMARY, ActionType.HOLD), ActionType.PRIMARY),
        Beat(
            applies = { it.action == ActionType.PRIMARY },
            prefer = { _, _ -> Choice.Number(2) },
            ask = { st ->
                decision(
                    "brogan",
                    DecisionKind.CHOOSE_NUMBER,
                    "Mad Dash: move up to 3 spaces in a straight line first. How far?",
                    (0..3).map { Choice.Number(it) },
                    source = st.heroes.getValue("brogan").played,
                )
            },
            onAnswer = { _, choice ->
                emit(GameEvent.Note("Brogan dashes ${(choice as Choice.Number).value} spaces."))
            },
        ),
        Beat(
            applies = { it.action == ActionType.PRIMARY },
            prefer = { _, _ -> Choice.Decline("Skip the attack") },
            ask = { st ->
                decision(
                    "brogan",
                    DecisionKind.CONFIRM,
                    "Attack an adjacent enemy hero?",
                    listOf(Choice.Yes, Choice.Decline("Skip the attack")),
                    source = st.heroes.getValue("brogan").played,
                )
            },
            onAnswer = { _, choice ->
                emit(
                    GameEvent.Note(
                        if (choice ==
                            Choice.Yes
                        ) {
                            "Brogan swings at the air."
                        } else {
                            "Brogan does not attack."
                        },
                    ),
                )
                resolve("brogan")
            },
        ),
        // ---- Turn 4 (the fake skips turns 2 and 3).
        Beat(
            ask = { st ->
                endTurn(st, nextTurn = 4)
                val spawns = mapGeometry.heroSpawns(Team.BLUE).filter { h -> s.board.pieces.none { it.at == h } }
                decision(
                    "tigerclaw",
                    DecisionKind.RESPAWN,
                    "Respawn in your Throne Zone?",
                    spawns.map { Choice.Space(it) } + Choice.Decline("Stay off the board"),
                    context = DecisionContext(subjectId = "tigerclaw"),
                )
            },
            applies = { at("tigerclaw") == null },
            skip = { st -> endTurn(st, nextTurn = 4) },
            onAnswer = { _, choice ->
                if (choice is Choice.Space) {
                    move("tigerclaw", choice.hex)
                    emit(GameEvent.Placed("tigerclaw", null, choice.hex, "Tigerclaw respawns in the Blue Throne Zone."))
                } else {
                    emit(GameEvent.Note("Tigerclaw stays off the board."))
                }
            },
        ),
        Beat(
            prefer = { _, _ -> Choice.Unit("arien") },
            ask = { _ ->
                decision(
                    "arien",
                    DecisionKind.ACT_FIRST,
                    "Arien and Brogan tie on initiative 8: which of them acts first?",
                    listOf(Choice.Unit("arien"), Choice.Unit("brogan")),
                )
            },
            onAnswer = { _, choice -> emit(GameEvent.Note("${name((choice as Choice.Unit).unitId)} acts first.")) },
        ),
        Beat(
            prefer = { _, d -> d.options.first() },
            ask = { _ ->
                decision(
                    "arien",
                    DecisionKind.CHOOSE_OPTION,
                    "Choose one —",
                    listOf(
                        Choice.Option(0, "Move up to 2 spaces"),
                        Choice.Option(1, "Swap places with an adjacent minion"),
                    ),
                    source = "arien-green-1",
                )
            },
            onAnswer = { _, choice -> emit(GameEvent.Note("Arien chooses: ${(choice as Choice.Option).label}.")) },
        ),
        Beat(
            ask = { st ->
                val spot = emptyNeighbours(at("brogan")!!).first()
                st.tokens = listOf(PieceView("t1", "token_rock", "Rock", spot, ownerHeroId = null, team = null))
                emit(GameEvent.Note("A Rock token stands next to Brogan."))
                decision(
                    "brogan",
                    DecisionKind.CHOOSE_TOKEN,
                    "Clear: remove an adjacent token",
                    listOf(Choice.Token("t1"), Choice.Decline("Leave it")),
                    context = DecisionContext(highlight = listOf(spot)),
                )
            },
            onAnswer = { st, choice ->
                if (choice is Choice.Token) {
                    st.tokens = emptyList()
                    emit(GameEvent.Removed("t1", "Brogan removes the Rock token."))
                }
            },
        ),
        Beat(
            ask = { st ->
                decision(
                    "wasp",
                    DecisionKind.CHOOSE_CARD,
                    "Discard a card",
                    st.heroes.getValue("wasp").hand.map { Choice.Card(it) },
                    source = "tigerclaw-silver",
                )
            },
            onAnswer = { st, choice ->
                val id = (choice as Choice.Card).cardId
                st.heroes.getValue("wasp").hand.remove(id)
                st.heroes.getValue("wasp").discard += id
                emit(GameEvent.CardsChanged("wasp", "Wasp discards ${CARD_NAMES[id]}."))
            },
        ),
        // ---- End of round 1 (p.8): Minion Battle, the push, retrieve, level up, Pity coin.
        Beat(
            ask = { st ->
                st.phase = GamePhase.END_OF_ROUND
                st.order = emptyList()
                st.acting = null
                emit(GameEvent.Note("End of round 1."))
                emit(GameEvent.Note("Minion Battle: Red has more minions in the Battle Zone; Blue removes one (p.8)."))
                val blue = st.board.pieces.filterIsInstance<MinionPiece>()
                    .filter { it.team == Team.BLUE && it.type != MinionType.HEAVY && it.at != null }
                decision(
                    "wasp",
                    DecisionKind.REMOVE_MINION,
                    "Minion Battle: remove one of your minions (the Heavy goes last)",
                    blue.map { Choice.Unit(it.id) },
                    context = DecisionContext(highlight = blue.mapNotNull { it.at }),
                )
            },
            onAnswer = { st, choice ->
                val id = (choice as Choice.Unit).unitId
                remove(id)
                emit(GameEvent.Removed(id, "Blue removes a minion."))
                st.immune = emptySet()
            },
        ),
        Beat(
            ask = { st ->
                // A Red minion stands outside the Battle Zone and goes back (p.18).
                val minion = st.board.pieces.filterIsInstance<MinionPiece>().first {
                    it.team == Team.RED &&
                        it.at != null
                }
                val zone = st.board.battleZones[laneId]!!
                val spaces = BoardRules.returnToBattleZone(mapGeometry, st.board, minion).ifEmpty {
                    mapGeometry.zoneSpaces[zone].orEmpty().filter { h ->
                        st.board.pieces.none { it.at == h }
                    }.take(2).toSet()
                }.take(2)
                decision(
                    "arien",
                    DecisionKind.MINION_RETURN,
                    "Your melee minion returns to the Battle Zone: choose the space",
                    spaces.map { Choice.Space(it) },
                    context = DecisionContext(subjectId = minion.id),
                )
            },
            onAnswer = { st, choice ->
                val minion = st.board.pieces.filterIsInstance<MinionPiece>().first {
                    it.team == Team.RED &&
                        it.at != null
                }
                move(minion.id, (choice as Choice.Space).hex)
                emit(
                    GameEvent.Moved(
                        minion.id,
                        listOf((choice).hex),
                        "The Red melee minion returns to the Battle Zone.",
                    ),
                )
                // The push (p.8 step 3): Red wins it, one Wave counter flips.
                var n = 100
                when (val pushed = BoardSetup.pushLane(mapGeometry, st.board, laneId, Team.RED) { "m${++n}" }) {
                    is BoardSetup.PushResult.Pushed -> st.board = pushed.state
                    is BoardSetup.PushResult.ThroneCaptured -> Unit
                }
                st.waves -= 1
                emit(
                    GameEvent.LanePushed(
                        laneId,
                        Team.RED,
                        st.board.battleZones[laneId]!!,
                        "Red pushes the lane. ${st.waves} Wave counters left.",
                    ),
                )
                st.heroes.values.forEach { h ->
                    h.hand.addAll(h.resolved + h.discard + listOfNotNull(h.played))
                    h.resolved.clear()
                    h.discard.clear()
                    h.played = null
                }
                emit(GameEvent.Note("Everyone retrieves their cards."))
                st.heroes.getValue("arien").level = 2
                emit(GameEvent.LevelledUp("arien", 2, "Arien levels up to 2."))
            },
        ),
        Beat(
            prefer = { _, _ -> Choice.Card("arien-red-1") },
            ask = { _ ->
                decision(
                    "arien",
                    DecisionKind.UPGRADE_CARD,
                    "Level up: choose a Tier I card to upgrade",
                    listOf("arien-red-1", "arien-green-1", "arien-blue-1").map { Choice.Card(it) },
                )
            },
            onAnswer = { st, choice ->
                st.upgraded = (choice as Choice.Card).cardId
                emit(GameEvent.Note("Arien upgrades ${CARD_NAMES[st.upgraded]}."))
            },
        ),
        Beat(
            prefer = { _, d -> d.options.last() },
            ask = { st ->
                val color = st.upgraded!!.removePrefix("arien-").substringBefore("-")
                decision(
                    "arien",
                    DecisionKind.KEEP_UPGRADE,
                    "Keep one card in hand; the other becomes an item",
                    listOf("arien-$color-2a", "arien-$color-2b").map { Choice.Card(it) },
                )
            },
            onAnswer = { st, choice ->
                val keep = (choice as Choice.Card).cardId
                val color = st.upgraded!!.removePrefix("arien-").substringBefore("-")
                val item = listOf("arien-$color-2a", "arien-$color-2b").first { it != keep }
                val arien = st.heroes.getValue("arien")
                arien.hand.remove(st.upgraded)
                arien.hand.add(keep)
                arien.items.add(item)
                emit(
                    GameEvent.CardsChanged(
                        "arien",
                        "Arien keeps ${CARD_NAMES[keep]}; ${CARD_NAMES[item]} becomes an item.",
                    ),
                )
                st.heroes.getValue("brogan").coins += 1
                emit(GameEvent.CoinsGained("brogan", 1, "Brogan didn't level up and collects a Pity coin."))
                st.round = 2
                st.turn = 1
                st.phase = GamePhase.PLAY_CARDS
                st.heroes.getValue("tigerclaw").markers = listOf(MarkerView("poison", "arien"))
                st.heroes.getValue("brogan").active = listOf("brogan-silver")
                emit(GameEvent.RoundStarted(2, "Round 2 begins."))
                emit(GameEvent.TurnStarted(2, 1, "Turn 1 of round 2."))
            },
        ),
        // ---- Round 2: Wasp attacks Arien, Arien defends; then the last Life counter goes.
        playCard("arien", "arien-blue-1"),
        playCard("brogan", "brogan-gold"),
        playCard("wasp", "wasp-red-1"),
        playCard("tigerclaw", "tigerclaw-gold"),
        Beat(
            prefer = { _, _ -> Choice.Unit("arien") },
            ask = { st ->
                emptyNeighbours(at("arien")!!).firstOrNull()?.let { move("wasp", it) }
                decision(
                    "wasp",
                    DecisionKind.CHOOSE_UNIT,
                    "Shock: choose an adjacent enemy to attack",
                    listOf(Choice.Unit("arien")),
                    source = st.heroes.getValue("wasp").played,
                    context = DecisionContext(highlight = listOfNotNull(at("arien"))),
                )
            },
            onAnswer = { _, _ -> emit(GameEvent.Attacked("wasp", "arien", 5, "Wasp attacks Arien with Attack 5.")) },
        ),
        Beat(
            prefer = { _, d -> d.options.first() },
            ask = { st ->
                val hand = st.heroes.getValue("arien").hand
                val bonus = if (st.heroes.getValue("arien").items.any { ITEM_DEFENSE.contains(it) }) 1 else 0
                decision(
                    "arien",
                    DecisionKind.DEFEND,
                    "Wasp attacks Arien (Attack 5): discard a card to defend",
                    hand.map { Choice.Card(it) } + Choice.Decline("Don't defend"),
                    source = st.heroes.getValue("wasp").played,
                    context = DecisionContext(
                        attackerId = "wasp",
                        attackTotal = 5,
                        rangedAttack = false,
                        defensePreview = hand.associateWith { id -> DEFENSE[id]?.let { it + bonus + 1 } },
                        minionModifier = 1,
                        subjectId = "arien",
                    ),
                )
            },
            onAnswer = { st, choice ->
                if (choice is Choice.Card) {
                    val arien = st.heroes.getValue("arien")
                    arien.hand.remove(choice.cardId)
                    arien.discard += choice.cardId
                    emit(
                        GameEvent.Defended(
                            "arien",
                            choice.cardId,
                            DEFENSE[choice.cardId],
                            true,
                            "Arien defends with ${CARD_NAMES[choice.cardId]}.",
                        ),
                    )
                } else {
                    defeat(st, "arien", by = "wasp")
                }
            },
        ),
        Beat(
            ask = { st ->
                decision(
                    "brogan",
                    DecisionKind.CHOOSE_ACTION,
                    "Choose an action for Onslaught",
                    listOf(Choice.Action(ActionType.PRIMARY), Choice.Action(ActionType.HOLD)),
                    source = "brogan-gold",
                )
            },
            onAnswer = { st, _ ->
                emit(GameEvent.Attacked("brogan", "wasp", 4, "Brogan attacks Wasp with Attack 4."))
                defeat(st, "wasp", by = "brogan")
                // However the story went, it ends here: Blue is out of Life counters.
                if (st.winner == null) {
                    st.life = st.life + (Team.BLUE to 0)
                    st.winner = Team.RED
                    st.reason = WinReason.LIFE_COUNTERS
                    st.phase = GamePhase.GAME_OVER
                    st.acting = null
                    emit(
                        GameEvent.GameOver(
                            Team.RED,
                            WinReason.LIFE_COUNTERS,
                            "Red wins: Blue is out of Life counters.",
                        ),
                    )
                }
            },
        ),
    )

    private fun endTurn(st: State, nextTurn: Int) {
        if (st.turn == nextTurn) return
        st.heroes.values.forEach { h ->
            h.played?.let { h.resolved += it }
            h.played = null
        }
        st.turn = nextTurn
        st.order = listOf("arien", "brogan", "wasp", "tigerclaw")
        st.acting = "arien"
        st.phase = GamePhase.RESOLVE
        emit(GameEvent.Note("(Scripted test game: turns 2 and 3 are skipped.)"))
        emit(GameEvent.TurnStarted(1, nextTurn, "Turn $nextTurn of round 1."))
    }

    private fun defeat(st: State, heroId: String, by: String) {
        move(heroId, null)
        val team = st.heroes.getValue(heroId).team
        val left = (st.life[team] ?: 0) - 1
        st.life = st.life + (team to left)
        emit(GameEvent.Defeated(heroId, by, "${name(heroId)} is defeated by ${name(by)}."))
        emit(
            GameEvent.LifeSpent(
                team,
                1,
                left,
                "${if (team == Team.RED) "Red" else "Blue"} spends a Life counter; $left left.",
            ),
        )
        st.heroes.getValue(by).coins += 2
        emit(GameEvent.CoinsGained(by, 2, "${name(by)} gains 2 coins."))
        if (left <= 0) {
            st.winner = team.opponent
            st.reason = WinReason.LIFE_COUNTERS
            st.phase = GamePhase.GAME_OVER
            st.acting = null
            emit(
                GameEvent.GameOver(
                    team.opponent,
                    WinReason.LIFE_COUNTERS,
                    "${if (team == Team.RED) "Blue" else "Red"} wins: the other team is out of Life counters.",
                ),
            )
        }
    }

    private companion object {
        const val MAX_MOVES = 12

        val NAMES = mapOf("arien" to "Arien", "brogan" to "Brogan", "wasp" to "Wasp", "tigerclaw" to "Tigerclaw")

        fun basicHand(heroId: String) = mutableListOf(
            "$heroId-gold",
            "$heroId-silver",
            "$heroId-red-1",
            "$heroId-green-1",
            "$heroId-blue-1",
        )
    }
}

/** Card names and values the fake prints (from the bundled catalog, for its own sentences). */
private val CARD_NAMES: Map<String?, String> by lazy {
    dev.quinton.goa.feature.board.bundledCatalog.heroes.flatMap { it.allCards }.associate { it.id to it.name }
}

/** A card's printed Defense, or null for a Defense card whose text decides (Tigerclaw's Dodge). */
private val DEFENSE: Map<String, Int?> by lazy {
    dev.quinton.goa.feature.board.bundledCatalog.heroes.flatMap { it.allCards }.associate { it.id to it.values.defense }
}

private val ITEM_DEFENSE: Set<String> by lazy {
    dev.quinton.goa.feature.board.bundledCatalog.heroes.flatMap { it.allCards }
        .filter { it.item == dev.quinton.goa.core.model.catalog.Stat.DEFENSE }.map { it.id }.toSet()
}

private fun initiative(cardId: String?): Int = cardId?.let { id ->
    dev.quinton.goa.feature.board.bundledCatalog.heroes.flatMap { it.allCards }.firstOrNull { it.id == id }?.initiative
} ?: 0

/** Makes the scripted game. [failRestore]: restoring throws, like a save from an older engine. */
class FakeGameFactory(
    private val maps: List<BoardMap>,
    override val playableHeroes: Set<String> = setOf("arien", "brogan", "wasp", "tigerclaw"),
    var failRestore: Boolean = false,
) : GameFactory {
    override val engineVersion: Int = FAKE_ENGINE_VERSION
    override val playableMaps: List<String> = maps.filter { it.verified }.map { it.id }
    val created = mutableListOf<FakeGameSession>()

    override fun newGame(setup: GameSetup): GameSession {
        require(setup.seats.map { it.heroId }.toSet() == (FAKE_LINEUP.red + FAKE_LINEUP.blue).toSet()) {
            "The fake plays only Arien and Brogan against Wasp and Tigerclaw"
        }
        return FakeGameSession(setup, maps.first { it.id == setup.mapId }).also { created += it }
    }

    override fun restore(saved: SavedGame): GameSession {
        check(!failRestore && saved.engineVersion == FAKE_ENGINE_VERSION) { "Saved by another engine" }
        return FakeGameSession(saved.setup, maps.first { it.id == saved.setup.mapId }).also {
            it.restoreAnswers(saved.payload)
            created += it
        }
    }
}

/** The fake computer: the script's answer for each question. Counts its calls. */
class FakeAi : AiPlayer {
    var calls = 0

    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
        calls++
        return (session as FakeGameSession).preferred() ?: decision.options.first()
    }
}

fun fakeAiPlayers(ai: FakeAi = FakeAi()) = AiPlayers { ai }

/**
 * Nudges when the person holds instead of attacking, or doesn't defend; two turning points after.
 * With [prepares] it also looks ahead ([prepare]) and answers from that look.
 */
class FakeCoach(var silent: Boolean = false, var prepares: Boolean = false) : Coach {
    /** Answers looked at without a prepared look. */
    var asked = 0

    /** Answers looked at with a prepared look. */
    var askedPrepared = 0

    /** Each [prepare] call: the decision id, and whether it was already cancelled when it began. */
    val looks = mutableListOf<Pair<Long, Boolean>>()

    class Look(override val decisionId: Long) : CoachAnalysis

    override fun prepare(session: GameSession, decision: Decision, cancelled: () -> Boolean): CoachAnalysis? {
        if (!prepares) return null
        looks += decision.id to cancelled()
        return Look(decision.id)
    }

    override fun nudge(
        analysis: CoachAnalysis,
        session: GameSession,
        decision: Decision,
        choice: Choice,
        cancelled: () -> Boolean,
    ): CoachNote? {
        askedPrepared++
        check(analysis.decisionId == decision.id) { "A look at ${analysis.decisionId} used for ${decision.id}" }
        return verdict(decision, choice)
    }

    override fun nudge(session: GameSession, decision: Decision, choice: Choice, cancelled: () -> Boolean): CoachNote? {
        asked++
        return verdict(decision, choice)
    }

    private fun verdict(decision: Decision, choice: Choice): CoachNote? {
        if (silent) return null
        return when {
            decision.kind == DecisionKind.CHOOSE_ACTION && choice == Choice.Action(ActionType.HOLD) -> CoachNote(
                "Tigerclaw is next to you",
                "Holding wastes Dangerous Current's Attack 6 while an enemy hero stands adjacent (p.13).",
                decisionId = decision.id,
            )

            decision.kind == DecisionKind.DEFEND && choice is Choice.Decline -> CoachNote(
                "Expert Duelist holds this attack",
                "A Defense total at least the Attack blocks it (p.14); not defending costs a Life counter.",
                decisionId = decision.id,
            )

            else -> null
        }
    }

    override fun turningPoints(saved: SavedGame, seat: String, cancelled: () -> Boolean): List<CoachNote> = listOf(
        CoachNote(
            "Arien's attack on Tigerclaw",
            "Defeating a hero cost Blue a Life counter early (p.13).",
            round = 1,
            turn = 1,
        ),
        CoachNote("Brogan finished Wasp", "Blue had one Life counter left (p.13).", round = 2, turn = 1),
    )
}
