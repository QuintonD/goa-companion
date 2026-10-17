package dev.quinton.goa.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.navigation.AboutRoute
import dev.quinton.goa.core.navigation.BackupRoute
import dev.quinton.goa.core.navigation.DraftMethodsRoute
import dev.quinton.goa.core.navigation.GroupSyncRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.RatingSettingsRoute
import dev.quinton.goa.core.navigation.SettingsRoute
import dev.quinton.goa.core.navigation.TimerPresetsRoute

/** Settings and About. `RatingSettingsRoute` is registered by feature:players. */
fun EntryProviderScope<NavKey>.settingsEntries(navigator: Navigator) {
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
