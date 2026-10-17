package dev.quinton.goa.feature.catalog

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.designsystem.component.PlaceholderScreen
import dev.quinton.goa.core.navigation.CardViewerRoute
import dev.quinton.goa.core.navigation.CatalogRoute
import dev.quinton.goa.core.navigation.HeroDetailRoute
import dev.quinton.goa.core.navigation.Navigator

fun EntryProviderScope<NavKey>.catalogEntries(navigator: Navigator) {
    entry<CatalogRoute> { PlaceholderScreen("Heroes", onBack = navigator::back) }
    entry<HeroDetailRoute> { PlaceholderScreen("Hero", onBack = navigator::back) }
    entry<CardViewerRoute> { PlaceholderScreen("Card", onBack = navigator::back) }
}
