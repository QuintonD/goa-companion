package dev.quinton.goa.feature.sync.hub

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

/** The Hub section of the Group screen (ADR-030 §5) in each state, light/dark, font scale 2, 384 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HubScreenshotTest {
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
    private fun Card(hub: HubUi) = Box(Modifier.padding(Spacing.screen)) { HubCard(hub, HubActions()) }

    private val hub = "https://goa.tail4f2a9c.ts.net"
    private val synced = HubUi(url = hub, status = "Synced 2 min ago")
    private val offline = synced.copy(
        error = "Can't reach the hub. Check the internet connection and the hub address.",
        errorAgo = "5 min ago",
        retrying = true,
    )
    private val wrongKey = synced.copy(
        status = "Synced 3 days ago",
        error = "This phone's group key isn't accepted by the hub. Check the hub address, or scan a new invite.",
        errorAgo = "just now",
    )

    private val joined = GroupUiState(
        loading = false,
        groupName = "Friday nights",
        invite = GroupInvite.format(TestGroup, hubUrl = hub),
        me = person("ann", "Ann"),
        deviceName = "Ann",
        phones = listOf(PhoneRow("Bob", "2 h ago")),
        lastSync = "2 h ago",
        players = 6,
        matches = 41,
        hub = synced,
    )

    @Test fun hubAddDark() = shot { Card(HubUi()) }

    @Test fun hubAddErrorLight() = shot(ThemeMode.LIGHT) { Card(HubUi(addressError = HubText.NOT_AN_ADDRESS)) }

    @Test fun hubAddLightFontScale2() = shot(ThemeMode.LIGHT, 2f) { Card(HubUi()) }

    @Test fun hubSyncedDark() = shot { Card(synced) }

    @Test fun hubSyncingLight() = shot(ThemeMode.LIGHT) { Card(synced.copy(syncing = true, status = "Syncing…")) }

    @Test fun hubOfflineDark() = shot { Card(offline) }

    @Test fun hubOfflineDarkFontScale2() = shot(fontScale = 2f) { Card(offline) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun hubWrongKeyLight384() = shot(ThemeMode.LIGHT) { Card(wrongKey) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun hubWrongKeyDark384FontScale2() = shot(fontScale = 2f) { Card(wrongKey) }

    @Test fun groupWithHubDark() = shot { GroupScreen(joined, GroupActions()) }

    @Test fun groupWithHubLight() = shot(ThemeMode.LIGHT) { GroupScreen(joined.copy(hub = offline), GroupActions()) }
}
