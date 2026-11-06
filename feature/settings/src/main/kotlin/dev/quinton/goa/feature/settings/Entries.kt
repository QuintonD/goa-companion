package dev.quinton.goa.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.navigation.AboutRoute
import dev.quinton.goa.core.navigation.BackupRoute
import dev.quinton.goa.core.navigation.DraftMethodsRoute
import dev.quinton.goa.core.navigation.GroupSyncRoute
import dev.quinton.goa.core.navigation.MoreRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.RatingSettingsRoute
import dev.quinton.goa.core.navigation.RulesRoute
import dev.quinton.goa.core.navigation.SettingsRoute
import dev.quinton.goa.core.navigation.TimerPresetsRoute
import dev.quinton.goa.core.navigation.TopLevelTab

/** More, Settings and About. `RatingSettingsRoute` is registered by feature:players. */
fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator) {
    entry<MoreRoute> {
        MoreScreen(
            onOpen = { link ->
                when (link) {
                    // The Draft keeps a stack of its own, so a draft in progress is where it was left.
                    MoreLink.DRAFT -> navigator.navigateToTab(TopLevelTab.DRAFT)

                    MoreLink.RULES -> navigator.navigate(RulesRoute)

                    MoreLink.GROUP_SYNC -> navigator.navigate(GroupSyncRoute)

                    MoreLink.BACKUP -> navigator.navigate(BackupRoute)

                    MoreLink.SETTINGS -> navigator.navigate(SettingsRoute)

                    MoreLink.ABOUT -> navigator.navigate(AboutRoute)
                }
            },
        )
    }
    entry<SettingsRoute> {
        SettingsRoute(
            onBack = navigator::back,
            onOpen = { link ->
                navigator.navigate(
                    when (link) {
                        SettingsLink.TIMER_PRESETS -> TimerPresetsRoute
                        SettingsLink.DRAFT_METHODS -> DraftMethodsRoute
                        SettingsLink.RATINGS -> RatingSettingsRoute
                        SettingsLink.GROUP_SYNC -> GroupSyncRoute
                        SettingsLink.BACKUP -> BackupRoute
                        SettingsLink.ABOUT -> AboutRoute
                    },
                )
            },
        )
    }
    entry<AboutRoute> { AboutRoute(onBack = navigator::back) }
}
