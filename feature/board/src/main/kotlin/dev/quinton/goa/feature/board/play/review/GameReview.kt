package dev.quinton.goa.feature.board.play.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.CardView
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.TurnPlay
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.map.MapLayout
import dev.quinton.goa.feature.board.map.MapOrientation
import dev.quinton.goa.feature.board.map.MapViewport
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.CardRow
import dev.quinton.goa.feature.board.play.table.GameMapView
import dev.quinton.goa.feature.board.play.table.HeroStrip
import dev.quinton.goa.feature.board.play.table.ItemIcon
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.TableHud
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.figureOf
import dev.quinton.goa.feature.board.play.table.spokenCard

/** The review's two tabs. */
enum class ReviewTab(val label: String) {
    NOW("Now"),
    TURNS("Turns"),
}

/**
 * The game-state review (PRODUCT §9–10, ADR-037), in a sheet: **Now** — the round at a glance (Life
 * and Wave counters, pushes, minions in the Battle Zone, who has acted and who is next) and every
 * hero by team (card in play, resolved Turn slots, discards, items, level, coins, hand size, markers,
 * tokens); **Turns** — every turn so far, newest first, with each hero's card at the reveal and what
 * happened, and the board at the start of any earlier turn. Only what the person may see (p.4): the
 * engine's own views. Tap a card to read it.
 */
@Composable
fun GameReviewContent(
    table: TableUi,
    review: ReviewUi?,
    tab: ReviewTab,
    onTab: (ReviewTab) -> Unit,
    /**
     * Opens [cards] (the face-up cards of the row tapped) at [index]: one callback, so the screen
     * decides how a card is read (the card focus viewer, view only).
     */
    onOpenCard: (cards: List<Card>, index: Int) -> Unit,
    onAction: (ReviewAction) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** A puzzle: every hand is open. */
    puzzle: Boolean = false,
) {
    val rewind = review?.rewind
    Column(modifier.fillMaxWidth().fillMaxHeight(REVIEW_HEIGHT)) {
        if (rewind != null) {
            Rewind(table, rewind, onAction)
            return@Column
        }
        ReviewTitle(table, onDone)
        PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
            ReviewTab.entries.forEach { t ->
                Tab(selected = tab == t, onClick = { onTab(t) }, text = { Text(t.label, maxLines = 1) })
            }
        }
        when (tab) {
            ReviewTab.NOW -> NowTab(table, review, onOpenCard, puzzle, Modifier.weight(1f))
            ReviewTab.TURNS -> TurnsTab(table, review, onOpenCard, onAction, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReviewTitle(table: TableUi, onDone: () -> Unit) {
    val view = table.view
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Game state", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                "Round ${view.round} · Turn ${view.turn} of 4 · ${phaseText(view.phase)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
    }
}

private fun phaseText(phase: GamePhase): String = when (phase) {
    GamePhase.PLAY_CARDS -> "Cards are played facedown"
    GamePhase.RESOLVE -> "Cards resolve"
    GamePhase.END_OF_ROUND -> "End of round"
    GamePhase.GAME_OVER -> "Game over"
}

// ---- Now -----------------------------------------------------------------------------------

@Composable
private fun NowTab(table: TableUi, review: ReviewUi?, onCard: OpenCard, puzzle: Boolean, modifier: Modifier) {
    val teams = Team.entries.sortedBy { if (it == table.bottomTeam) 0 else 1 }
    LazyColumn(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Spacing.screen,
            vertical = Spacing.md,
        ),
    ) {
        item(key = "board") { BoardSummary(table, review?.pushes.orEmpty()) }
        item(key = "order") { TurnOrder(table) }
        teams.forEach { team ->
            val heroes = table.view.heroes.filter { it.team == team }
            if (heroes.isEmpty()) return@forEach
            item(key = "team-$team") { TeamHeader(team, table) }
            heroes.forEach { hero ->
                item(key = "hero-${hero.heroId}") { HeroCard(table, hero, onCard, puzzle) }
            }
        }
    }
}

/** Life and Wave counters, pushes won, minions in the Battle Zone, the Tie Breaker coin. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardSummary(table: TableUi, pushes: Map<Team, Int>) {
    val view = table.view
    val geometry = table.geometry
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Overline("The board")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Team.entries.forEach { team ->
                    Fact(
                        "${BoardText.team(team)} Life",
                        "${view.lifeCounters[team] ?: 0}",
                        team = team,
                    )
                }
                table.map.lanes.forEach { lane ->
                    val name = BoardText.lane(table.map, lane.id)
                    Fact(listOfNotNull("Waves", name).joinToString(" · "), "${view.wavesLeft[lane.id] ?: 0} left")
                }
                Fact("Pushes won", Team.entries.joinToString(" · ") { "${BoardText.team(it)} ${pushes[it] ?: 0}" })
                Fact("Tie Breaker coin", BoardText.team(view.tieBreaker), team = view.tieBreaker)
            }
            table.map.lanes.forEachIndexed { i, lane ->
                val zone = table.battleZones.getOrNull(i)
                val inZone = view.minions.filter { it.lane == lane.id && geometry.zoneOf(it.at) == zone }
                val counts = Team.entries.joinToString(" · ") { team ->
                    val mine = inZone.filter { it.team == team }
                    val heavy = mine.count { it.type == dev.quinton.goa.core.model.board.MinionType.HEAVY }
                    "${BoardText.team(team)} ${mine.size}" + if (heavy > 0) " ($heavy Heavy)" else ""
                }
                Text(
                    "Battle Zone: ${BoardText.zone(table.map, zone)} · minions $counts",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Who has acted this turn, who acts now and next (p.7), or who has played facedown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TurnOrder(table: TableUi) {
    val view = table.view
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Overline("Turn ${view.turn} · who acts")
        when (view.phase) {
            GamePhase.PLAY_CARDS -> {
                val down = view.heroes.filter { it.played != null }
                val choosing = view.heroes.filter { it.played == null }
                Text(
                    buildList {
                        if (down.isNotEmpty()) add("Played facedown: ${names(table, down.map { it.heroId })}")
                        if (choosing.isNotEmpty()) add("Still to play: ${names(table, choosing.map { it.heroId })}")
                    }.joinToString(". ").ifEmpty { "Cards are revealed together (p.7)." },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            GamePhase.RESOLVE -> {
                val acted = view.actedThisTurn
                val waiting = view.initiativeOrder.filter { it != view.acting && it !in acted }
                val passed = view.heroes.map { it.heroId }
                    .filter { it !in acted && it !in view.initiativeOrder && it != view.acting }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    acted.forEach { OrderChip(table, it, "Acted") }
                    view.acting?.let { OrderChip(table, it, "Now", strong = true) }
                    waiting.forEachIndexed { i, id ->
                        OrderChip(table, id, if (i == 0) "Next" else "Then")
                    }
                    passed.forEach { OrderChip(table, it, "No card") }
                }
            }

            GamePhase.END_OF_ROUND -> Text(
                "Every card has resolved. The end of round steps run (p.8).",
                style = MaterialTheme.typography.bodyMedium,
            )

            GamePhase.GAME_OVER -> Text("The game is over.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun names(table: TableUi, ids: List<String>): String = ids.joinToString(", ") { table.hero(it).name }

@Composable
private fun OrderChip(table: TableUi, heroId: String, label: String, strong: Boolean = false) {
    val hero = table.heroView(heroId) ?: return
    val name = table.hero(heroId).name
    Surface(
        shape = RoundedCornerShape(50),
        color = if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (strong) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$name: $label" },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamMark(hero.team, markSize = 8.dp)
            Spacer(Modifier.width(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                " · $label",
                style = MaterialTheme.typography.labelMedium,
                color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TeamHeader(team: Team, table: TableUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = Spacing.xs).semantics(mergeDescendants = true) { heading() },
    ) {
        TeamMark(team, markSize = 12.dp)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            "${BoardText.team(team).uppercase()} TEAM" +
                if (table.mine.any { table.heroView(it)?.team == team }) " · YOURS" else "",
            style = GoaTextStyles.overline,
            color = teamColor(team),
        )
    }
}

/** One hero in full, open information only (p.4); its hand only when it is the person's (any in a puzzle). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(table: TableUi, hero: HeroView, onCard: OpenCard, puzzle: Boolean) {
    val info = table.hero(hero.heroId)
    val view = table.view
    val acting = view.acting == hero.heroId
    val acted = hero.heroId in view.actedThisTurn
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (acting) 2.dp else 1.dp,
            if (acting) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                },
            ) {
                HeroPortrait(
                    hero.heroId,
                    info.portrait,
                    null,
                    Modifier
                        .size(scaledIconSize(44.dp, 1.3f))
                        .border(2.dp, teamColor(hero.team), CircleShape)
                        .alpha(if (hero.at == null) OFF_BOARD_ALPHA else 1f),
                    shape = CircleShape,
                    framed = false,
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        info.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        listOfNotNull(
                            table.controllerLabel(hero),
                            when {
                                acting -> "Acting now"
                                hero.at == null -> "Off the board"
                                else -> "On the board"
                            },
                            "Acted this turn".takeIf { acted },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (hero.at ==
                            null
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Fact("Level", "${hero.level}")
                Fact("Coins", "${hero.coins}")
                Fact("In hand", "${hero.handCount}")
                if (hero.ultimate) Fact("Ultimate", "Unlocked")
                hero.counters.forEach { (name, value) -> Fact(PlayText.counter(name), "$value") }
                if (hero.itemStats.isNotEmpty()) {
                    Column(
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription =
                                "Items: " + hero.itemStats.joinToString(", ") { "+1 ${PlayText.stat(it)}" }
                        },
                    ) {
                        Overline("Items")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            hero.itemStats.forEach { ItemIcon(it) }
                        }
                    }
                }
            }
            InPlay(table, hero, onCard)
            Section("Resolved this round · Turn slots")
            if (hero.resolved.isEmpty()) {
                Quiet("None yet")
            } else {
                CardRow(hero.resolved, table, opener(hero.resolved, table, onCard), slots = true)
            }
            if (hero.discard.isNotEmpty()) {
                Section("Discarded")
                CardRow(hero.discard, table, opener(hero.discard, table, onCard))
            }
            if (hero.activeEffects.isNotEmpty()) {
                Section("Active effects")
                val effects = hero.activeEffects.map { CardView(it, active = true) }
                CardRow(effects, table, opener(effects, table, onCard))
            }
            val tokens = view.pieces.filter { it.ownerHeroId == hero.heroId && figureOf(it) == null }
            if (hero.markers.isNotEmpty() || tokens.isNotEmpty()) {
                Section("Markers and tokens")
                hero.markers.forEach { marker ->
                    val from = marker.fromHeroId?.let { " · from ${table.hero(it).name}" }.orEmpty()
                    Text(
                        marker.kind.replaceFirstChar {
                            it.uppercase()
                        } + " marker" + from,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (tokens.isNotEmpty()) {
                    Text(
                        "On the board: " +
                            tokens.groupingBy { it.label }.eachCount().entries.joinToString(", ") { (label, n) ->
                                if (n == 1) label else "$label × $n"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            val hand = hero.hand
            if (hand != null && hero.heroId in table.mine) {
                Section(if (table.mine.size > 1) "${info.name}'s hand" else "Your hand")
                if (hand.isEmpty()) Quiet("No cards") else HandRow(hand, table, onCard)
            } else if (hand != null && puzzle) {
                Section("In hand · open in this puzzle")
                if (hand.isEmpty()) Quiet("No cards") else HandRow(hand, table, onCard)
            }
        }
    }
}

@Composable
private fun HandRow(hand: List<String>, table: TableUi, onCard: OpenCard) {
    val cards = hand.map { CardView(it) }
    CardRow(cards, table, opener(cards, table, onCard))
}

/** The card in play this turn: facedown before the reveal (named only to its owner), else face up. */
@Composable
private fun InPlay(table: TableUi, hero: HeroView, onCard: OpenCard) {
    val view = table.view
    val played = hero.played
    Section("This turn")
    when {
        played != null -> {
            val note = when {
                view.phase == GamePhase.PLAY_CARDS && played.cardId == null ->
                    "Played facedown: revealed with the others (p.7)."

                view.phase == GamePhase.PLAY_CARDS -> "Your card, facedown until the reveal."

                else -> hero.initiative?.let { "Initiative $it" }
            }
            val cards = listOf(played) + hero.alsoPlayed
            CardRow(cards, table, opener(cards, table, onCard))
            note?.let { Quiet(it) }
        }

        view.phase == GamePhase.PLAY_CARDS -> Quiet("No card played yet.")

        hero.heroId in view.actedThisTurn -> Quiet("Resolved: on Turn slot ${view.turn}.")

        view.phase == GamePhase.RESOLVE -> Quiet("No card this turn.")

        else -> Quiet("No card in play.")
    }
}

// ---- Turns ---------------------------------------------------------------------------------

@Composable
private fun TurnsTab(
    table: TableUi,
    review: ReviewUi?,
    onCard: OpenCard,
    onAction: (ReviewAction) -> Unit,
    modifier: Modifier,
) {
    if (review == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val rounds = review.turns.groupBy { it.round }.toSortedMap(compareByDescending { it })
    LazyColumn(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Spacing.screen,
            vertical = Spacing.md,
        ),
    ) {
        item(key = "intro") {
            Quiet(
                "Each hero's card at the reveal, in the order they acted, and what happened. " +
                    "Cards that were facedown stay hidden (p.4).",
            )
        }
        rounds.forEach { (round, turns) ->
            roundItems(table, round, turns.sortedByDescending { it.turn }, onCard, onAction)
        }
        if (review.turns.isEmpty()) item(key = "empty") { Quiet("Nothing has happened yet.") }
    }
}

private fun LazyListScope.roundItems(
    table: TableUi,
    round: Int,
    turns: List<TurnUi>,
    onCard: OpenCard,
    onAction: (ReviewAction) -> Unit,
) {
    item(key = "round-$round") {
        Text(
            "ROUND $round",
            style = GoaTextStyles.overline,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
        )
    }
    turns.forEach { turn ->
        if (turn.endOfRound.isNotEmpty()) {
            item(key = "eor-$round") { EndOfRound(round, turn.endOfRound) }
        }
        item(key = "turn-$round-${turn.turn}") { TurnCard(table, turn, onCard, onAction) }
    }
}

@Composable
private fun TurnCard(table: TableUi, turn: TurnUi, onCard: OpenCard, onAction: (ReviewAction) -> Unit) {
    val current = !turn.finished
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (current) 2.dp else 1.dp,
            if (current) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(
                        "Turn ${turn.turn}" + if (current) " · now" else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (turn.fromPosition) Quiet("The puzzle starts in this turn.")
                }
                if (turn.hasStart) {
                    OutlinedButton(
                        onClick = { onAction(ReviewAction.Rewind(turn.round, turn.turn)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md),
                    ) {
                        Icon(Icons.Outlined.Map, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Board", maxLines = 1)
                    }
                }
            }
            if (turn.plays.isEmpty()) {
                Quiet(
                    if (current) {
                        "Cards are played facedown and revealed together (p.7)."
                    } else {
                        "No cards were revealed."
                    },
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    val faceUp = turn.plays.mapNotNull { faceUp(it.card, table) }
                    turn.plays.forEach { play ->
                        PlayTile(table, play) { card -> onCard(faceUp, faceUp.indexOf(card).coerceAtLeast(0)) }
                    }
                }
            }
            Lines(turn.lines, startOpen = current, what = "what happened")
        }
    }
}

/** One hero's card at the reveal: face up (tap to read), facedown, or a pass. */
@Composable
private fun PlayTile(table: TableUi, play: TurnPlay, onCard: (Card) -> Unit) {
    val name = table.hero(play.heroId).name
    val card = play.card?.takeIf { !it.facedown }?.let { table.card(it.cardId) }
    val spoken = buildList {
        add(name)
        add(
            when {
                play.card == null -> "passed"
                card == null -> "facedown card"
                else -> spokenCard(card)
            },
        )
        play.initiative?.let { add("initiative $it as it counted") }
        play.alsoPlayed.forEach { also -> add("also ${also.cardId?.let(table::card)?.name ?: "a facedown card"}") }
    }.joinToString(", ")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TILE + 16.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (card != null) {
                    role = Role.Button
                    onClick(label = "Read the card") {
                        onCard(card)
                        true
                    }
                }
            },
    ) {
        if (play.card == null) {
            PassTile(TILE)
        } else {
            Surface(onClick = { card?.let(onCard) }, enabled = card != null, shape = RoundedCornerShape(6.dp)) {
                TableCard(card, width = TILE)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            TeamMark(play.team, markSize = 7.dp)
            Spacer(Modifier.width(3.dp))
            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            when {
                play.card == null -> "Pass"
                card == null -> "Facedown"
                else -> play.initiative?.let { "Init. $it" } ?: card.initiative?.let { "Init. $it" } ?: ""
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        if (play.alsoPlayed.isNotEmpty()) {
            Text("+${play.alsoPlayed.size} more", style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun PassTile(width: Dp) {
    Box(
        Modifier
            .width(width)
            .aspectRatio(CARD_RATIO)
            .border(1.dp, GoaTheme.colors.engravedBorder, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "No card",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EndOfRound(round: Int, lines: List<ReviewLine>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                "End of round $round",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Lines(lines, startOpen = false, what = "the end of the round")
        }
    }
}

/** The lines not to miss always shown; every line behind "Show all". */
@Composable
private fun Lines(lines: List<ReviewLine>, startOpen: Boolean, what: String) {
    if (lines.isEmpty()) return
    var open by rememberSaveable(lines.size, startOpen) { mutableStateOf(startOpen) }
    val shown = if (open) lines else lines.filter { it.strong }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEach { line ->
            Row {
                Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (line.strong) FontWeight.SemiBold else null,
                )
            }
        }
        if (lines.size > shown.size || open) {
            TextButton(
                onClick = { open = !open },
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
            ) {
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(if (open) "Show less" else "Show all ${lines.size} lines of $what")
            }
        }
    }
}

// ---- Rewind --------------------------------------------------------------------------------

/**
 * The board at the start of an earlier turn, read only, with the heroes as they stood then. System
 * back returns to the Turns list (one level, like the arrow), not past it: the sheet's own back
 * handler would close the whole review, so the next press would leave the game.
 */
@Composable
private fun Rewind(table: TableUi, rewind: RewindUi, onAction: (ReviewAction) -> Unit) {
    // Registered inside the sheet's window after the sheet's own callback, so it is asked first.
    BackHandler { onAction(ReviewAction.CloseRewind) }
    val then = TableUi(
        map = table.map,
        view = rewind.view,
        heroes = table.heroes,
        cards = table.cards,
        mine = table.mine,
        bottomTeam = table.bottomTeam,
    )
    val title = if (rewind.fromPosition) {
        "The puzzle's start · round ${rewind.round}, turn ${rewind.turn}"
    } else {
        "Start of round ${rewind.round} · turn ${rewind.turn}"
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onAction(ReviewAction.CloseRewind) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to the game state")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Read only · as you saw it then (p.4)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onAction(ReviewAction.Step(-1)) }, enabled = rewind.hasEarlier) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Earlier turn")
            }
            IconButton(onClick = { onAction(ReviewAction.Step(1)) }, enabled = rewind.hasLater) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Later turn")
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val landscape = maxWidth > maxHeight
            val orientation = if (landscape) MapOrientation.LANDSCAPE else MapOrientation.PORTRAIT
            val layout = remember(table.map, orientation, table.bottomTeam) {
                MapLayout(table.map, orientation, table.bottomTeam)
            }
            val lane = remember(layout, table.map) { layout.laneRegion(table.map, then.geometry) }
            // A fresh viewport per board, so stepping frames each one.
            val viewport = remember(rewind.round, rewind.turn) { MapViewport() }
            GameMapView(then, layout, viewport, onTap = {}, modifier = Modifier.fillMaxSize(), lane = lane)
            Column(
                Modifier.fillMaxWidth().padding(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TableHud(then)
                HeroStrip(then, onHero = {})
            }
        }
    }
}

// ---- Bits ----------------------------------------------------------------------------------

/** Opens a card among others: the face-up cards of the row and the index of the one tapped. */
internal typealias OpenCard = (cards: List<Card>, index: Int) -> Unit

private fun faceUp(view: CardView?, table: TableUi): Card? = view?.takeIf {
    !it.facedown
}?.let { table.card(it.cardId) }

/** A row's tap: the tapped card opened among the row's face-up cards. */
private fun opener(views: List<CardView>, table: TableUi, open: OpenCard): (Card) -> Unit {
    val cards = views.mapNotNull { faceUp(it, table) }
    return { card -> open(cards, cards.indexOf(card).coerceAtLeast(0)) }
}

@Composable
private fun Fact(label: String, value: String, team: Team? = null) {
    Column(Modifier.widthIn(min = 56.dp).semantics(mergeDescendants = true) {}) {
        Overline(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (team != null) {
                TeamMark(team, markSize = 10.dp)
                Spacer(Modifier.width(4.dp))
            }
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Overline(text: String) {
    Text(text.uppercase(), style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = GoaTextStyles.overline,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val TILE = 56.dp
private const val CARD_RATIO = 729f / 1026f
private const val OFF_BOARD_ALPHA = 0.45f
private const val REVIEW_HEIGHT = 0.95f
