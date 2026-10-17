package dev.quinton.goa.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.designsystem.component.PlaceholderScreen
import dev.quinton.goa.core.navigation.AboutRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.RatingSettingsRoute
import dev.quinton.goa.core.navigation.SettingsRoute

fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator) {
    entry<SettingsRoute> { PlaceholderScreen("Settings", onBack = navigator::back) }
    entry<AboutRoute> { PlaceholderScreen("About", onBack = navigator::back) }
    entry<RatingSettingsRoute> { PlaceholderScreen("Rating settings", onBack = navigator::back) }
}
