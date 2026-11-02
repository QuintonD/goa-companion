package dev.quinton.goa.web.play.online

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.feature.board.play.game.OnlineUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.web.play.table.HeroPortrait
import dev.quinton.goa.web.play.table.Overline
import dev.quinton.goa.web.play.table.teamCss
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/*
 * What an online game adds to the table (ADR-056). Every name here is an account's name, typed by a stranger:
 * always a Text node, never markup.
 */

private const val CLOCK_URGENT_SECONDS = 10

/** Another person is being asked: who, never what (docs/ONLINE.md "Hidden information"). */
@Composable
fun OtherPlayerMove(table: TableUi, status: TurnStatus.OtherPlayer, online: OnlineUi?) {
    val hero = table.heroView(status.heroId)
    val info = table.hero(status.heroId)
    Div({
        classes("goa-computer", "goa-online-other")
        attr("aria-live", "polite")
    }) {
        HeroPortrait(info, hero?.team, 40)
        Div({ classes("goa-computer-text") }) {
            P({ classes("t-overline", "goa-table-overline") }) { Text(info.name) }
            P({ classes("t-title-medium") }) { Text("${status.name} is choosing") }
            if (online?.presence?.get(status.heroId) == Presence.COMPUTER) {
                P({ classes("t-body-small", "goa-quiet") }) { Text(OnlineWords.presence(Presence.COMPUTER)) }
            }
        }
    }
}

/** Who plays which hero, and who is here. */
@Composable
fun OnlinePlayers(table: TableUi, online: OnlineUi) {
    if (online.players.isEmpty()) return
    Overline("Players")
    Ul({ classes("goa-online-players") }) {
        table.view.heroes.forEach { hero ->
            val name = if (hero.heroId in table.mine) "You" else online.players[hero.heroId].orEmpty()
            val presence = online.presence[hero.heroId] ?: Presence.HERE
            Li({
                classes("goa-online-player")
                if (presence != Presence.HERE) classes("away")
            }) {
                HeroPortrait(table.hero(hero.heroId), hero.team, PLAYER_PORTRAIT, dim = presence != Presence.HERE)
                Span({ classes("goa-online-player-text") }) {
                    Span({ classes("t-title-small", "goa-online-name") }) { Text(name) }
                    Span({ classes("t-body-small", "goa-quiet") }) {
                        Text("${table.hero(hero.heroId).name} · ${OnlineWords.presence(presence)}")
                    }
                }
            }
        }
    }
}

private const val PLAYER_PORTRAIT = 32

/**
 * Over the map, in sight with the panel hidden: the clock of whoever is being asked and, once the connection has
 * dropped, the words for it over the table that is kept. The clock ticks once a second: it is no live region
 * ([Announce][dev.quinton.goa.web.play.table.Announce] says what happens), only its label is read on request.
 */
@Composable
fun OnlineBar(table: TableUi, online: OnlineUi?, reconnecting: Boolean) {
    Div({ classes("goa-online-bar") }) {
        if (reconnecting) {
            Div({
                classes("goa-online-reconnecting", "t-label-large")
                attr("role", "status")
            }) {
                Div({ classes("goa-spinner", "small") })
                Text(OnlineWords.RECONNECTING)
            }
        }
        val seat = online?.clockSeat
        val seconds = online?.clockSeconds
        if (seat != null && seconds != null) {
            val mine = seat in table.mine
            val who = if (mine) "You" else online.players[seat] ?: table.hero(seat).name
            Div({
                classes("goa-online-clock", "t-label-large")
                table.heroView(seat)?.team?.let { classes(teamCss(it)) }
                if (mine) classes("mine")
                if (seconds <= CLOCK_URGENT_SECONDS) classes("urgent")
                attr("role", "timer")
                attr("aria-label", "$who: $seconds seconds left")
            }) {
                Icon("timer", IconSize.SMALL)
                Span({ classes("goa-online-name") }) { Text(who) }
                Span({ classes("t-numeric") }) { Text("$seconds s") }
            }
        }
    }
}
