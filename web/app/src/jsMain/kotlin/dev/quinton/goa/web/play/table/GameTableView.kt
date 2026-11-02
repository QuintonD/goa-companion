package dev.quinton.goa.web.play.table

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.ui.PanelPeek
import dev.quinton.goa.web.play.cards.CardFocusState
import dev.quinton.goa.web.play.map.MapCamera
import dev.quinton.goa.web.play.ui.PlayScaffold
import dev.quinton.goa.web.ui.IconButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div

/**
 * The game table (the phone's GameTable), for the game against the computer and for puzzles: the map
 * filling the screen with the round at a glance and every hero (or, after the reveal, the turn's
 * cards) at the top, Fit and Flip over the map, and [panel] at the bottom in portrait or at the side
 * in landscape. The map frames the Lane and brings what a question asks for into view.
 *
 * The panel hides ([panelShown] false) to [peek]'s one line with [peekActions] (Confirm for a tapped
 * space or piece); the map then fits the whole board unless the person moved it; taps on the map keep
 * selecting. Under a tall panel the turn track takes its small cards: a question's moment is on the map.
 *
 * [onTap] gets every tap on the map that meant something (an option, or a piece to read about);
 * [onHero] opens a hero (from a chip or a track slot); [onFlip] swaps the bottom team. [overlay] lies over
 * the map, so it stays in sight with the panel hidden (an online game's clock).
 */
@Composable
fun GameTableView(
    title: String,
    actions: @Composable () -> Unit,
    table: TableUi,
    camera: MapCamera,
    focus: CardFocusState,
    onTap: (TableTap) -> Unit,
    onHero: (String) -> Unit,
    onFlip: () -> Unit,
    panelShown: Boolean,
    onPanelShown: (Boolean) -> Unit,
    peek: PanelPeek,
    peekActions: @Composable () -> Unit = {},
    panelTitle: (@Composable () -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    panel: @Composable () -> Unit,
) {
    val landscape = rememberLandscape()
    val scene = rememberTableScene(table, landscape)
    val scope = rememberCoroutineScope()
    val hidden = !panelShown
    var tallPanel by remember { mutableStateOf(false) }
    // The whole board while the panel is hidden, unless the person moved the map; back to the Lane after.
    LaunchedEffect(hidden, camera.unit > 0f) {
        if (camera.unit == 0f || camera.userMoved) return@LaunchedEffect
        if (hidden) {
            camera.animateFit()
        } else if (camera.isFit) {
            camera.animateLane()
        }
    }
    // Bring what is asked for into view: the options, the unit they are about, the highlights.
    val decision = table.decision
    // Again when the map's size changes (the turn track appears, the panel grows): as the phone does on its insets.
    LaunchedEffect(decision?.id, table.asking, camera.width, camera.height, hidden) {
        if (decision == null || camera.unit == 0f) return@LaunchedEffect
        if (hidden && !camera.userMoved) return@LaunchedEffect
        if (decision.kind == DecisionKind.PLAY_CARD && !table.asking) return@LaunchedEffect
        val who = if (decision.kind == DecisionKind.PLAY_CARD) table.mine else setOf(decision.seat)
        // Whoever else the question is about: the attacker of a Defend, the hero acting.
        val alsoAbout = listOfNotNull(decision.context.attackerId, table.view.acting).mapNotNull(table::at)
        val hexes = table.spaceOptions.keys + table.pieceOptions.keys.mapNotNull(table::at) +
            decision.context.highlight + listOfNotNull(table.subject?.let(table::at)) + who.mapNotNull(table::at) +
            alsoAbout
        val region = scene.layout.bounds(hexes, pad = 1.1f) ?: return@LaunchedEffect
        // The Battle Zone around them when it fits at a comfortable size: it is where the game is.
        val zones = table.battleZones.toSet()
        val whole = scene.layout.bounds(hexes + table.map.spaces.filter { it.zone in zones }.map { it.hex }, pad = 1.1f)
        val framed = whole?.takeIf { w ->
            w.width * ZONE_UNIT <= camera.width - ZONE_MARGIN && w.height * ZONE_UNIT <= camera.height - ZONE_MARGIN
        } ?: region
        camera.reveal(framed, framed.center)
    }
    PlayScaffold(
        title = title,
        actions = actions,
        panelShown = panelShown,
        onPanelShown = onPanelShown,
        peek = peek,
        peekActions = peekActions,
        panelLabel = "The question and what happened",
        panelTitle = panelTitle,
        onPanelTall = { tallPanel = it },
        top = {
            Div({
                classes("goa-table-top")
                if (landscape && !hidden) classes("row")
            }) {
                val track = table.track
                if (landscape && !hidden) {
                    TableHud(table, compact = true)
                    if (track !=
                        null
                    ) {
                        TurnTrackRow(table, track, onHero, focus, compact = true)
                    } else {
                        HeroStrip(table, onHero, compact = true)
                    }
                } else {
                    TableHud(table)
                    if (track != null) {
                        TurnTrackRow(table, track, onHero, focus, compact = hidden || tallPanel)
                    } else if (!hidden) {
                        HeroStrip(table, onHero)
                    }
                }
            }
        },
        map = {
            TableMap(table, scene, camera, onTap)
            overlay()
            Div({ classes("goa-map-buttons") }) {
                if (camera.isFit) {
                    IconButton("zoom_in", "Zoom to the lane", {
                        scope.launch { camera.animateLane() }
                    }, "goa-map-button")
                } else {
                    IconButton("fit", "Fit the whole board", { scope.launch { camera.animateFit() } }, "goa-map-button")
                }
                IconButton(
                    "flip",
                    "Flip: ${BoardText.team(table.bottomTeam.opponent)} at the bottom",
                    onFlip,
                    "goa-map-button",
                )
            }
        },
        panel = panel,
    )
}

/** Map units a comfortable hex (38 px across its flats) takes per px, and room to spare around the Battle Zone. */
private const val ZONE_UNIT = 38f / 1.7320508f
private const val ZONE_MARGIN = 48f

/** A remembered camera for one screen's table. */
@Composable
fun rememberTableCamera(): MapCamera = remember { MapCamera() }
