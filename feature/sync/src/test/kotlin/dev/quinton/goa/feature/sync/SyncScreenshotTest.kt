package dev.quinton.goa.feature.sync

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.protocol.ClientPhase
import dev.quinton.goa.core.domain.sync.protocol.ClientState
import dev.quinton.goa.core.domain.sync.protocol.HostPhase
import dev.quinton.goa.core.domain.sync.protocol.HostState
import dev.quinton.goa.core.domain.sync.protocol.PeerProgress
import dev.quinton.goa.core.domain.sync.protocol.PeerStage
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import dev.quinton.goa.core.domain.sync.protocol.SyncFailure
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.sync.group.GroupActions
import dev.quinton.goa.feature.sync.group.GroupScreen
import dev.quinton.goa.feature.sync.group.GroupUiState
import dev.quinton.goa.feature.sync.group.PhoneRow
import dev.quinton.goa.feature.sync.importfile.ImportStage
import dev.quinton.goa.feature.sync.importfile.SyncFileImportActions
import dev.quinton.goa.feature.sync.importfile.SyncFileImportScreen
import dev.quinton.goa.feature.sync.importfile.SyncFileImportUiState
import dev.quinton.goa.feature.sync.me.WhoAreYouActions
import dev.quinton.goa.feature.sync.me.WhoAreYouScreen
import dev.quinton.goa.feature.sync.me.WhoAreYouUiState
import dev.quinton.goa.feature.sync.session.PermissionUi
import dev.quinton.goa.feature.sync.session.SessionStage
import dev.quinton.goa.feature.sync.session.SyncSessionActions
import dev.quinton.goa.feature.sync.session.SyncSessionSheet
import dev.quinton.goa.feature.sync.session.SyncSessionUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SyncScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun setContent(mode: ThemeMode, fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
            }
        }
    }

    private fun shot(mode: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f, content: @Composable () -> Unit) {
        setContent(mode, fontScale, content)
        compose.onRoot().captureRoboImage()
    }

    /** Every window, so the invite dialog is included. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun screen(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        setContent(mode, 1f, content)
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    private val noGroup = GroupUiState(loading = false)
    private val joined = GroupUiState(
        loading = false,
        groupName = "Friday nights",
        invite = GroupInvite.format(TestGroup),
        me = person("ann", "Ann"),
        deviceName = "Ann",
        phones = listOf(PhoneRow("Bob", "2 h ago"), PhoneRow("Cid", "3 days ago")),
        lastSync = "2 h ago",
        players = 6,
        matches = 41,
    )

    @Composable private fun Group(state: GroupUiState, invite: Boolean = false) =
        GroupScreen(state, GroupActions(), showInvite = invite)

    @Test fun groupNoGroupDark() = shot { Group(noGroup) }

    @Test fun groupNoGroupLightFontScale2() = shot(ThemeMode.LIGHT, 2f) { Group(noGroup) }

    @Test fun groupJoinedDark() = shot { Group(joined) }

    @Test fun groupJoinedLight() = shot(ThemeMode.LIGHT) { Group(joined.copy(me = null)) }

    @Test fun groupJoinedDarkFontScale2() = shot(fontScale = 2f) { Group(joined) }

    @Test fun inviteDialogDark() = screen { Group(joined, invite = true) }

    /**
     * Pasting an invite with the keyboard up (hands-on 2026-10-25: Join was under the keyboard). The
     * room the keyboard leaves on a phone is a short window here: Join must be on screen once the
     * field has the invite.
     */
    @Config(qualifiers = "w411dp-h420dp-xxhdpi")
    @Test
    fun groupPasteInviteShortDark() {
        setContent(ThemeMode.DARK, 1f) { Group(noGroup) }
        compose.onNode(hasSetTextAction() and hasText("Or paste an invite")).performTextInput("Join “Friday nights” in Atlantis Companion: goa1-abc")
        compose.waitForIdle()
        compose.onNode(hasText("Join") and hasClickAction()).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    private val session = SyncSessionUiState(groupName = "Friday nights", deviceName = "Ann")
    private val peers = listOf(
        PeerProgress("1", "Bob", SyncDevice("b", "Bob"), PeerStage.READY),
        PeerProgress("2", "Cid", SyncDevice("c", "Cid"), PeerStage.RECEIVING, progress = 0.6f),
        PeerProgress("3", "Eve", stage = PeerStage.FAILED, failure = SyncFailure.WRONG_KEY),
    )

    @Composable private fun Sheet(state: SyncSessionUiState, permission: PermissionUi? = null) =
        SyncSessionSheet(state, permission, SyncSessionActions())

    @Test fun sessionChooseDark() = shot { Sheet(session) }

    @Test fun sessionPermissionDark() = shot { Sheet(session, PermissionUi.NEEDED) }

    @Test fun sessionPermissionRefusedDark() = shot { Sheet(session, PermissionUi.REFUSED) }

    @Test fun sessionPermissionDeniedLightFontScale2() = shot(ThemeMode.LIGHT, 2f) {
        Sheet(session, PermissionUi.DENIED)
    }

    @Test fun sessionHostingDark() = shot {
        Sheet(session.copy(stage = SessionStage.Hosting(HostState(peers = peers))))
    }

    @Test fun sessionHostDoneLight() = shot(ThemeMode.LIGHT) {
        val done = HostState(
            phase = HostPhase.DONE,
            peers = peers.take(2).map { it.copy(stage = PeerStage.DONE, progress = null) },
            changes = SyncChanges(matchesAdded = 5, playersAdded = 1, matchesUpdated = 2),
        )
        Sheet(
            session.copy(
                stage = SessionStage.Hosting(done),
                duplicateHint = SyncText.duplicateHint(listOf("Dee")),
            ),
        )
    }

    @Test fun sessionJoinWaitingDark() = shot {
        Sheet(session.copy(stage = SessionStage.Joining(ClientState(phase = ClientPhase.WAITING, hostName = "Bob"))))
    }

    @Test fun sessionJoinFailedDarkFontScale2() = shot(fontScale = 2f) {
        val failed = ClientState(phase = ClientPhase.FAILED, failure = SyncFailure.NO_HOST)
        Sheet(session.copy(stage = SessionStage.Joining(failed)))
    }

    @Test fun sessionFirstSyncDoneDark() = shot {
        val done =
            ClientState(
                phase = ClientPhase.DONE,
                hostName = "Bob",
                changes = SyncChanges(matchesAdded = 41, playersAdded = 6),
            )
        Sheet(session.copy(firstSync = true, stage = SessionStage.Joining(done)))
    }

    /** A later sync on a phone that never picked its player (its first sync failed). */
    @Test fun sessionDoneWithoutMeDark() = shot {
        val done = ClientState(phase = ClientPhase.DONE, hostName = "Bob", changes = SyncChanges(matchesAdded = 4))
        Sheet(session.copy(meChosen = false, stage = SessionStage.Joining(done)))
    }

    private val whoAreYou = WhoAreYouUiState(
        loading = false,
        players = listOf(person("ann", "Ann"), person("bob", "Bob"), person("cid", "Cid")),
        meId = "bob",
    )

    @Test fun whoAreYouDark() = shot { WhoAreYouScreen(whoAreYou, WhoAreYouActions()) }

    @Test fun whoAreYouLightFontScale2() = shot(ThemeMode.LIGHT, 2f) {
        WhoAreYouScreen(
            whoAreYou.copy(
                meId = null,
                newName = "ann",
                nameError = "“Ann” is already in the group. If that's you, pick it above.",
            ),
            WhoAreYouActions(),
        )
    }

    @Test fun importReadyDark() = shot {
        SyncFileImportScreen(
            SyncFileImportUiState(
                ImportStage.Ready("Friday nights", 6, 44, SyncChanges(matchesAdded = 3, matchesRemoved = 1)),
            ),
            SyncFileImportActions(),
        )
    }

    @Test fun importFailedLight() = shot(ThemeMode.LIGHT) {
        SyncFileImportScreen(
            SyncFileImportUiState(
                ImportStage.Failed(
                    "This file is from another group (“Tuesday crew”). This phone syncs with “Friday nights”.",
                    SyncFileProblem.OTHER_GROUP,
                ),
            ),
            SyncFileImportActions(),
        )
    }
}
