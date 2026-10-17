package dev.quinton.goa.feature.players

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.designsystem.component.PlaceholderScreen
import dev.quinton.goa.core.navigation.MatchEditRoute
import dev.quinton.goa.core.navigation.MatchLogRoute
import dev.quinton.goa.core.navigation.MetaRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.PlayerDetailRoute
import dev.quinton.goa.core.navigation.PlayerEditRoute
import dev.quinton.goa.core.navigation.PlayersRoute

fun EntryProviderScope<NavKey>.playersEntries(navigator: Navigator) {
    entry<PlayersRoute> { PlaceholderScreen("Players", onBack = navigator::back) }
    entry<PlayerDetailRoute> { PlaceholderScreen("Player", onBack = navigator::back) }
    entry<PlayerEditRoute> { PlaceholderScreen("Edit player", onBack = navigator::back) }
    entry<MatchLogRoute> { PlaceholderScreen("Matches", onBack = navigator::back) }
    entry<MatchEditRoute> { PlaceholderScreen("Edit match", onBack = navigator::back) }
    entry<MetaRoute> { PlaceholderScreen("Meta", onBack = navigator::back) }
}
