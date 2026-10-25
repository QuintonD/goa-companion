package dev.quinton.goa.web.play.table

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.TurnPlay
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.review.ReviewAction
import dev.quinton.goa.feature.board.play.review.ReviewLine
import dev.quinton.goa.feature.board.play.review.ReviewTab
import dev.quinton.goa.feature.board.play.review.ReviewUi
import dev.quinton.goa.feature.board.play.review.RewindUi
import dev.quinton.goa.feature.board.play.review.TurnUi
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.spokenCard
import dev.quinton.goa.web.play.cards.CardFocusState
import dev.quinton.goa.web.play.cards.CardImage
import dev.quinton.goa.web.play.map.MapCamera
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Segmented
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The game state (PRODUCT §9–10, ADR-037) in a sheet: **Now** — the board at a glance, who acts, every
 * hero by team; **Turns** — every turn so far, newest first, each hero's card at the reveal and what
 * happened, and **Board**: the start of an earlier turn, read only. Only open information (the engine's
 * own views). [review] is null while the worker is still building it.
 *
 * Drive it with the phone's ReviewKeeper: [onAction] gets Open/Rewind/Step/CloseRewind (the screen
 * passes them to `ReviewKeeper.handle` and asks the worker again: GameReview or PuzzleReview).
 * [puzzle]: every hand is open and "The puzzle starts in this turn" is said.
 */
@Composable
fun GameReviewSheet(
    table: TableUi,
    review: ReviewUi?,
    tab: ReviewTab,
    onTab: (ReviewTab) -> Unit,
    onAction: (ReviewAction) -> Unit,
    onDismiss: () -> Unit,
    focus: CardFocusState,
    puzzle: Boolean = false,
) {
    val view = table.view
    BottomSheet("Game state", onDismiss = onDismiss) {
        P({ classes("t-label-large", "goa-quiet") }) {
            Text("Round ${view.round} · Turn ${view.turn} of 4 · ${phaseText(view.phase)}")
        }
        val rewind = review?.rewind
        if (rewind != null) {
            Rewind(table, rewind, onAction)
            return@BottomSheet
        }
        Segmented("Show", ReviewTab.entries, tab, { it.label }) { onTab(it) }
        when (tab) {
            ReviewTab.NOW -> NowTab(table, review, focus, puzzle)

            ReviewTab.TURNS -> if (review ==
                null
            ) {
                LoadingState("Building the history")
            } else {
                TurnsTab(table, review, focus, onAction)
            }
        }
    }
}

private fun phaseText(phase: GamePhase): String = when (phase) {
    GamePhase.PLAY_CARDS -> "Cards are played facedown"
    GamePhase.RESOLVE -> "Cards resolve"
    GamePhase.END_OF_ROUND -> "End of round"
    GamePhase.GAME_OVER -> "Game over"
}

@Composable
private fun NowTab(table: TableUi, review: ReviewUi?, focus: CardFocusState, puzzle: Boolean) {
    val view = table.view
    Div({ classes("goa-review-box") }) {
        Overline("The board")
        Div({ classes("goa-facts") }) {
            Team.entries.forEach { team ->
                Fact("${BoardText.team(team)} Life", "${view.lifeCounters[team] ?: 0}", team)
            }
            table.map.lanes.forEach { lane ->
                Fact(
                    listOfNotNull("Waves", BoardText.lane(table.map, lane.id)).joinToString(" · "),
                    "${view.wavesLeft[lane.id] ?: 0} left",
                )
            }
            review?.let { r ->
                Fact("Pushes won", Team.entries.joinToString(" · ") { "${BoardText.team(it)} ${r.pushes[it] ?: 0}" })
            }
            Fact("Tie Breaker coin", BoardText.team(view.tieBreaker), view.tieBreaker)
        }
        table.map.lanes.forEachIndexed { i, lane ->
            val zone = table.battleZones.getOrNull(i)
            val inZone = view.minions.filter { it.lane == lane.id && table.geometry.zoneOf(it.at) == zone }
            val counts = Team.entries.joinToString(" · ") { team ->
                val mine = inZone.filter { it.team == team }
                val heavy = mine.count { it.type == MinionType.HEAVY }
                "${BoardText.team(team)} ${mine.size}" + if (heavy > 0) " ($heavy Heavy)" else ""
            }
            P({
                classes("t-body-medium")
            }) { Text("Battle Zone: ${BoardText.zone(table.map, zone)} · minions $counts") }
        }
    }
    TurnOrder(table)
    Team.entries.forEach { team ->
        val heroes = view.heroes.filter { it.team == team }
        if (heroes.isEmpty()) return@forEach
        P({ classes("t-section-head", "goa-team-head", teamCss(team)) }) {
            TeamMark(team)
            Text(" ${BoardText.team(team)} team")
        }
        heroes.forEach { HeroCard(table, it, focus, puzzle) }
    }
}

@Composable
private fun Fact(label: String, value: String, team: Team? = null) {
    Div({ classes("goa-fact") }) {
        Span({ classes("t-overline", "goa-table-overline") }) { Text(label) }
        Span({ classes("t-title-medium") }) {
            team?.let { TeamMark(it) }
            Text(" $value")
        }
    }
}

@Composable
private fun TurnOrder(table: TableUi) {
    val view = table.view
    Overline("Turn ${view.turn} · who acts")
    fun names(ids: List<String>) = ids.joinToString(", ") { table.hero(it).name }
    when (view.phase) {
        GamePhase.PLAY_CARDS -> {
            val down = view.heroes.filter { it.played != null }.map { it.heroId }
            val choosing = view.heroes.filter { it.played == null }.map { it.heroId }
            P({ classes("t-body-medium") }) {
                Text(
                    buildList {
                        if (down.isNotEmpty()) add("Played facedown: ${names(down)}")
                        if (choosing.isNotEmpty()) add("Still to play: ${names(choosing)}")
                    }.joinToString(". ").ifEmpty { "Cards are revealed together (p.7)." },
                )
            }
        }

        GamePhase.RESOLVE -> {
            val acted = view.actedThisTurn
            val waiting = view.initiativeOrder.filter { it != view.acting && it !in acted }
            val passed = view.heroes.map { it.heroId }.filter {
                it !in acted && it !in view.initiativeOrder &&
                    it != view.acting
            }
            Div({ classes("goa-order-chips") }) {
                acted.forEach { OrderChip(table, it, "Acted") }
                view.acting?.let { OrderChip(table, it, "Now", strong = true) }
                waiting.forEachIndexed { i, id -> OrderChip(table, id, if (i == 0) "Next" else "Then") }
                passed.forEach { OrderChip(table, it, "No card") }
            }
        }

        GamePhase.END_OF_ROUND -> P({
            classes("t-body-medium")
        }) { Text("Every card has resolved. The end of round steps run (p.8).") }

        GamePhase.GAME_OVER -> P({ classes("t-body-medium") }) { Text("The game is over.") }
    }
}

@Composable
private fun OrderChip(table: TableUi, heroId: String, label: String, strong: Boolean = false) {
    val hero = table.heroView(heroId) ?: return
    Span({
        classes("goa-order-chip")
        if (strong) classes("strong")
        attr("aria-label", "${table.hero(heroId).name}: $label")
    }) {
        TeamMark(hero.team)
        Text(" ${table.hero(heroId).name} · $label")
    }
}

@Composable
private fun HeroCard(table: TableUi, hero: HeroView, focus: CardFocusState, puzzle: Boolean) {
    val info = table.hero(hero.heroId)
    val view = table.view
    val acting = view.acting == hero.heroId
    Div({ classes("goa-review-hero", teamCss(hero.team)) }) {
        Div({ classes("goa-hero-head") }) {
            HeroPortrait(info, hero.team, 40, dim = hero.at == null)
            Div {
                P({ classes("t-title-small") }) { Text(info.name) }
                P({ classes("t-body-small", "goa-quiet") }) {
                    Text(
                        listOfNotNull(
                            table.controllerLabel(hero),
                            when {
                                acting -> "Acting now"
                                hero.at == null -> "Off the board"
                                else -> "On the board"
                            },
                            "Acted this turn".takeIf { hero.heroId in view.actedThisTurn },
                        ).joinToString(" · "),
                    )
                }
            }
        }
        P({ classes("t-body-small") }) {
            Text(
                listOfNotNull(
                    "Level ${hero.level}",
                    BoardText.count(hero.coins, "coin"),
                    "${BoardText.count(hero.handCount, "card")} in hand",
                    "Ultimate unlocked".takeIf { hero.ultimate },
                    hero.itemStats.takeIf { it.isNotEmpty() }?.let { stats ->
                        "Items: " + stats.joinToString(", ") { "+1 ${PlayText.stat(it)}" }
                    },
                ).joinToString(" · "),
            )
        }
        hero.played?.let { played ->
            Overline("This turn")
            CardRow(listOf(played) + hero.alsoPlayed, table, focus, "${info.name} · This turn")
        }
        if (hero.resolved.isNotEmpty()) {
            Overline("Resolved this round · Turn slots")
            CardRow(hero.resolved, table, focus, "${info.name} · Resolved", slots = true)
        }
        if (hero.discard.isNotEmpty()) {
            Overline("Discarded")
            CardRow(hero.discard, table, focus, "${info.name} · Discarded")
        }
        if (hero.markers.isNotEmpty()) {
            Overline("Markers and tokens")
            P({ classes("t-body-small") }) {
                Text(
                    hero.markers.joinToString(", ") { m ->
                        m.kind +
                            (m.fromHeroId?.let { " from ${table.hero(it).name}" } ?: "")
                    },
                )
            }
        }
        ExtrasSection(table, hero, focus)
        val hand = hero.hand
        if (hand != null && (hero.heroId in table.mine || puzzle)) {
            Overline(if (hero.heroId in table.mine) "Your hand" else "In hand · open in this puzzle")
            if (hand.isEmpty()) {
                Quiet("No cards")
            } else {
                CardRow(
                    hand.map {
                        CardView(it)
                    },
                    table,
                    focus,
                    "${info.name} · Hand",
                )
            }
        }
    }
}

@Composable
private fun TurnsTab(table: TableUi, review: ReviewUi, focus: CardFocusState, onAction: (ReviewAction) -> Unit) {
    Quiet(
        "Each hero's card at the reveal, in the order they acted, and what happened. Cards that were " +
            "facedown stay hidden (p.4).",
    )
    if (review.turns.isEmpty()) {
        Quiet("Nothing has happened yet.")
        return
    }
    review.turns.asReversed().groupBy { it.round }.forEach { (round, turns) ->
        P({ classes("t-overline", "goa-table-overline", "goa-round-head") }) { Text("Round $round") }
        turns.forEach { turn -> TurnCard(table, turn, review.turns.last() == turn, focus, onAction) }
    }
}

@Composable
private fun TurnCard(
    table: TableUi,
    turn: TurnUi,
    current: Boolean,
    focus: CardFocusState,
    onAction: (ReviewAction) -> Unit,
) {
    Div({ classes("goa-review-turn") }) {
        Div({ classes("goa-review-turn-head") }) {
            P({ classes("t-title-small") }) { Text("Turn ${turn.turn}" + if (current) " · now" else "") }
            if (turn.hasStart) {
                Button({
                    classes("goa-button", "text")
                    attr("type", "button")
                    onClick { onAction(ReviewAction.Rewind(turn.round, turn.turn)) }
                }) { Text("Board") }
            }
        }
        if (turn.fromPosition) Quiet("The puzzle starts in this turn.")
        when {
            turn.plays.isNotEmpty() -> Div({ classes("goa-card-row") }) {
                val cards = turn.plays.mapNotNull { p ->
                    p.card?.cardId?.takeIf { !p.card!!.facedown }?.let(table::card)
                }
                turn.plays.forEach { play ->
                    PlayTile(table, play) { card ->
                        focus.show(cards, cards.indexOf(card), "Round ${turn.round} · Turn ${turn.turn}")
                    }
                }
            }

            !turn.finished -> Quiet("Cards are played facedown and revealed together (p.7).")

            else -> Quiet("No cards were revealed.")
        }
        Lines(turn.lines, startOpen = current, what = "this turn")
        if (turn.endOfRound.isNotEmpty()) {
            P({ classes("t-title-small", "goa-end-round") }) { Text("End of round ${turn.round}") }
            Lines(turn.endOfRound, startOpen = false, what = "the end of round")
        }
    }
}

@Composable
private fun PlayTile(table: TableUi, play: TurnPlay, onCard: (dev.quinton.goa.core.model.catalog.Card) -> Unit) {
    val info = table.hero(play.heroId)
    val view = play.card
    val card = view?.takeIf { !it.facedown }?.cardId?.let(table::card)
    Div({ classes("goa-card-cell") }) {
        Button({
            classes("goa-thumb")
            attr("type", "button")
            attr(
                "aria-label",
                "${info.name}: " + when {
                    view == null -> "no card"
                    card == null -> "facedown"
                    else -> spokenCard(card)
                },
            )
            if (card == null) attr("disabled", "")
            onClick { card?.let(onCard) }
        }) {
            if (view == null) Span({ classes("goa-track-pass") }) { Text("No card") } else CardImage(card, 52)
        }
        Span({ classes("t-body-small") }) {
            TeamMark(play.team)
            Text(" ${info.name}")
        }
        Span({ classes("t-body-small", "goa-quiet") }) {
            Text(
                when {
                    view == null -> ""
                    card == null -> "Facedown"
                    else -> play.initiative?.let { "Init. $it" } ?: card.initiative?.let { "Init. $it" }.orEmpty()
                },
            )
        }
    }
}

/** The lines not to miss always shown; every line behind "Show all". */
@Composable
private fun Lines(lines: List<ReviewLine>, startOpen: Boolean, what: String) {
    if (lines.isEmpty()) return
    var open by remember(lines) { mutableStateOf(startOpen) }
    val shown = if (open) lines else lines.filter { it.strong }
    if (shown.isNotEmpty()) {
        org.jetbrains.compose.web.dom.Ul({ classes("goa-recent") }) {
            shown.forEach { line ->
                org.jetbrains.compose.web.dom.Li({ if (line.strong) classes("strong") }) { Text(line.text) }
            }
        }
    }
    if (lines.size > shown.size || open) {
        Button({
            classes("goa-button", "text")
            attr("type", "button")
            attr("aria-expanded", open.toString())
            onClick { open = !open }
        }) { Text(if (open) "Show less" else "Show all ${lines.size} lines of $what") }
    }
}

/** The board at the start of an earlier turn, read only, with Back to the list and the turns around it. */
@Composable
private fun Rewind(table: TableUi, rewind: RewindUi, onAction: (ReviewAction) -> Unit) {
    val shown = table.copy(
        view = rewind.view,
        decision = null,
        asking = false,
        selected = null,
        hint = null,
        turnLog = emptyList(),
    )
    val scene = rememberTableScene(shown, landscape = false)
    val camera = remember(rewind.round, rewind.turn) { MapCamera() }
    Div({ classes("goa-rewind-head") }) {
        GoaButton("Back to the game state", { onAction(ReviewAction.CloseRewind) }, ButtonKind.TEXT, icon = "back")
    }
    P({ classes("t-title-small") }) {
        Text(
            if (rewind.fromPosition) {
                "The puzzle's start · round ${rewind.round}, turn ${rewind.turn}"
            } else {
                "Start of round ${rewind.round} · turn ${rewind.turn}"
            },
        )
    }
    Div({ classes("goa-rewind-map") }) {
        TableMap(shown, scene, camera) { }
    }
    Div({ classes("goa-button-row") }) {
        GoaButton("Earlier turn", { onAction(ReviewAction.Step(-1)) }, ButtonKind.OUTLINED, enabled = rewind.hasEarlier)
        GoaButton("Later turn", { onAction(ReviewAction.Step(1)) }, ButtonKind.OUTLINED, enabled = rewind.hasLater)
    }
}
