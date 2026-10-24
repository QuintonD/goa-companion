package dev.quinton.goa.feature.sync.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.sync.TestGroup
import dev.quinton.goa.feature.sync.group.GroupActions
import dev.quinton.goa.feature.sync.group.GroupScreen
import dev.quinton.goa.feature.sync.group.GroupUiState
import dev.quinton.goa.feature.sync.group.PhoneRow
import dev.quinton.goa.feature.sync.person
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Sync server section of the Group screen (ADR-036) in each state, light/dark, font scale 2, 384 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SyncServerScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(mode: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    @Composable
    private fun Card(server: SyncServerUi, advanced: Boolean = false) = Box(Modifier.padding(Spacing.screen)) {
        SyncServerCard(server, SyncServerActions(), advancedInitially = advanced)
    }

    private val default = "https://oldboi.taila4d38d.ts.net:8443"
    private val synced = SyncServerUi(url = default, defaultUrl = default, status = "Synced 2 min ago")
    private val offline = synced.copy(
        error = "Can't reach the sync server. Check the internet connection.",
        errorAgo = "5 min ago",
        retrying = true,
    )
    private val wrongKey = synced.copy(
        status = "Synced 3 days ago",
        error = "The sync server holds this group under another key. Scan a new invite from a phone that " +
            "syncs, or check the server address.",
        errorAgo = "just now",
    )
    private val custom = SyncServerUi(
        url = "https://goa.example.org",
        custom = true,
        defaultUrl = default,
        status = "Not synced yet",
    )

    private val joined = GroupUiState(
        loading = false,
        groupName = "Friday nights",
        invite = GroupInvite.format(TestGroup),
        me = person("ann", "Ann"),
        deviceName = "Ann",
        phones = listOf(PhoneRow("Bob", "2 h ago")),
        lastSync = "2 h ago",
        players = 6,
        matches = 41,
        server = synced,
    )

    @Test fun serverSyncedDark() = shot { Card(synced) }

    @Test fun serverSyncingLight() = shot(ThemeMode.LIGHT) { Card(synced.copy(syncing = true, status = "Syncing…")) }

    @Test fun serverOfflineDark() = shot { Card(offline) }

    @Test fun serverOfflineDarkFontScale2() = shot(fontScale = 2f) { Card(offline) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun serverWrongKeyLight384() = shot(ThemeMode.LIGHT) { Card(wrongKey) }

    @Test fun serverOffLight() = shot(ThemeMode.LIGHT) { Card(synced.copy(enabled = false, status = "Off")) }

    @Test fun serverNoneDark() = shot { Card(SyncServerUi(), advanced = true) }

    @Test fun serverAdvancedCustomLight() = shot(ThemeMode.LIGHT) { Card(custom, advanced = true) }

    @Test fun serverAdvancedErrorDark() = shot {
        Card(synced.copy(addressError = SyncServerText.NOT_AN_ADDRESS), advanced = true)
    }

    @Test fun groupWithServerDark() = shot { GroupScreen(joined, GroupActions()) }

    @Test fun groupWithServerOfflineLight() =
        shot(ThemeMode.LIGHT) { GroupScreen(joined.copy(server = offline), GroupActions()) }
}
