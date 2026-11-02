package dev.quinton.goa.feature.board.online

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.feature.board.play.game.OnlineUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.ui.PlateSurface

/**
 * Over the table in an online game: who plays each hero, whether they are here, and the clock (ADR-056). Scrolls
 * sideways if the seats don't fit; the clock stays in view at its end. Team is the ring's colour and which side
 * of the divide it is on, as everywhere: the hero is named to TalkBack.
 */
@Composable
internal fun OnlineStrip(online: OnlineUi, table: TableUi, modifier: Modifier = Modifier) {
    PlateSurface(modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val seats = table.view.heroes.sortedBy { it.team != table.bottomTeam }
                seats.forEach { hero ->
                    Seat(online, table, hero.heroId, teamColor(hero.team))
                }
            }
            online.clockSeconds?.let { seconds ->
                Spacer(Modifier.width(Spacing.sm))
                OnlineClock(seconds, online.clockSeat?.let { seat -> whoSits(online, table, seat) })
            }
        }
    }
}

private fun whoSits(online: OnlineUi, table: TableUi, seat: String): String =
    if (seat in table.mine) "Your" else "${online.players[seat] ?: table.hero(seat).name}'s"

@Composable
private fun Seat(online: OnlineUi, table: TableUi, heroId: String, ring: Color) {
    val presence = online.presence[heroId] ?: Presence.HERE
    val hero = table.hero(heroId)
    val name = online.players[heroId] ?: hero.name
    val onClock = online.clockSeat == heroId
    val spoken = buildString {
        append(name)
        if (heroId in table.mine) append(" (you)")
        append(", playing ${hero.name}: ${OnlineWords.presence(presence)}")
        if (onClock) append(", on the clock")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        HeroPortrait(
            heroId,
            hero.portrait,
            null,
            Modifier.size(scaledIconSize(26.dp, 1.4f)).border(
                BorderStroke(
                    if (onClock) 2.5.dp else 1.5.dp,
                    if (onClock) MaterialTheme.colorScheme.secondary else ring,
                ),
                CircleShape,
            ),
            shape = CircleShape,
            framed = false,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (onClock) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 88.dp),
        )
        Spacer(Modifier.width(2.dp))
        PresenceMark(presence)
    }
}

/** Never by colour alone: each state has its own shape. */
@Composable
private fun PresenceMark(presence: Presence) {
    val icon: ImageVector = when (presence) {
        Presence.HERE -> Icons.Outlined.CheckCircle
        Presence.AWAY -> Icons.Outlined.WifiOff
        Presence.COMPUTER -> Icons.Outlined.SmartToy
    }
    val tint = when (presence) {
        Presence.HERE -> MaterialTheme.colorScheme.secondary
        Presence.AWAY -> GoaTheme.colors.unverified
        Presence.COMPUTER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(scaledIconSize(14.dp, 1.4f)))
}

/** The running clock is teal (DESIGN.md), and the last seconds the error colour with the same digits. */
@Composable
private fun OnlineClock(seconds: Int, whose: String?) {
    val urgent = seconds <= URGENT_SECONDS
    val color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "${whose ?: "The"} clock: ${spokenSeconds(seconds)} left"
        },
    ) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(scaledIconSize(16.dp)),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            clockText(seconds),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

private fun spokenSeconds(seconds: Int): String = if (seconds == 1) "1 second" else "$seconds seconds"

internal fun clockText(seconds: Int): String {
    val whole = seconds.coerceAtLeast(0)
    return "${whole / SECONDS_A_MINUTE}:${(whole % SECONDS_A_MINUTE).toString().padStart(2, '0')}"
}

/** Another person is being asked: only who, never what. In the panel, as the computer's move is. */
@Composable
internal fun OtherPlayerMove(table: TableUi, status: TurnStatus.OtherPlayer, online: OnlineUi?) {
    val hero = table.hero(status.heroId)
    val view = table.heroView(status.heroId)
    val awayNote = online?.presence?.get(status.heroId)?.takeIf { it != Presence.HERE }?.let(OnlineWords::presence)
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeroPortrait(
            status.heroId,
            hero.portrait,
            null,
            Modifier.size(40.dp).border(
                2.dp,
                view?.team?.let { teamColor(it) } ?: GoaTheme.colors.engravedBorder,
                CircleShape,
            ),
            shape = CircleShape,
            framed = false,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(hero.name.uppercase(), style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
            Text(
                if (table.decision?.kind == DecisionKind.PLAY_CARD) {
                    "${status.name} is choosing a card"
                } else {
                    "${status.name} is choosing"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            awayNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

/** The connection dropped: the last table stays under this until the driver is back. */
@Composable
internal fun BoxScope.ReconnectingBanner() {
    PlateSurface(
        brackets = true,
        border = BorderStroke(1.dp, GoaTheme.colors.unverified),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
    ) {
        Row(
            Modifier
                .padding(Spacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = GoaTheme.colors.unverified)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                OnlineWords.RECONNECTING,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

/** Calls [onReturn] on the main thread when the app comes to the foreground and when the network returns. */
@SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is in the app's manifest
@Composable
internal fun OnlineResumeEffect(onReturn: () -> Unit) {
    val latest by rememberUpdatedState(onReturn)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) latest()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val context = LocalContext.current
    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = latest()
        }
        connectivity?.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { connectivity?.unregisterNetworkCallback(callback) }
    }
}

private const val SECONDS_A_MINUTE = 60
private const val URGENT_SECONDS = 10
