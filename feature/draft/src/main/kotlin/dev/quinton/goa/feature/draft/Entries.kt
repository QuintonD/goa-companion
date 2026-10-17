package dev.quinton.goa.feature.draft

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.designsystem.component.PlaceholderScreen
import dev.quinton.goa.core.navigation.DraftBoardRoute
import dev.quinton.goa.core.navigation.DraftMethodEditRoute
import dev.quinton.goa.core.navigation.DraftMethodsRoute
import dev.quinton.goa.core.navigation.DraftRoute
import dev.quinton.goa.core.navigation.Navigator

fun EntryProviderScope<NavKey>.draftEntries(navigator: Navigator) {
    entry<DraftRoute> { PlaceholderScreen("Draft", onBack = navigator::back) }
    entry<DraftBoardRoute> { PlaceholderScreen("Draft board", onBack = navigator::back) }
    entry<DraftMethodsRoute> { PlaceholderScreen("Draft methods", onBack = navigator::back) }
    entry<DraftMethodEditRoute> { PlaceholderScreen("Edit draft method", onBack = navigator::back) }
}
