package dev.quinton.goa.feature.timer

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.designsystem.component.PlaceholderScreen
import dev.quinton.goa.core.navigation.MatchSetupRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.TimerPresetEditRoute
import dev.quinton.goa.core.navigation.TimerPresetsRoute
import dev.quinton.goa.core.navigation.TimerRoute

fun EntryProviderScope<NavKey>.timerEntries(navigator: Navigator) {
    entry<TimerRoute> { PlaceholderScreen("Timer", onBack = navigator::back) }
    entry<MatchSetupRoute> { PlaceholderScreen("Match setup", onBack = navigator::back) }
    entry<TimerPresetsRoute> { PlaceholderScreen("Timer presets", onBack = navigator::back) }
    entry<TimerPresetEditRoute> { PlaceholderScreen("Edit preset", onBack = navigator::back) }
}
