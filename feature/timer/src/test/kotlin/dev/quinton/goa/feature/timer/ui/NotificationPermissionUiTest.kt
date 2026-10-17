package dev.quinton.goa.feature.timer.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.feature.timer.FixedCoin
import dev.quinton.goa.feature.timer.TimerHarness
import dev.quinton.goa.feature.timer.ui.components.notificationSettingsIntent
import dev.quinton.goa.feature.timer.ui.preview.TimerPreviewData
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupRoute
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupViewModel
import dev.quinton.goa.feature.timer.ui.timer.TimerActions
import dev.quinton.goa.feature.timer.ui.timer.TimerScreen
import dev.quinton.goa.feature.timer.ui.timer.TimerUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** POST_NOTIFICATIONS is requested before every timer start, and a denial is surfaced on the timer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class NotificationPermissionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val h = TimerHarness()

    private fun session(): TimerSnapshot? = runBlocking { h.controller.snapshot.first() }

    private fun showSetup() {
        val vm = MatchSetupViewModel(null, h.repository, h.controller, h.matches, FixedCoin())
        compose.setContent { GoaTheme { MatchSetupRoute(vm, onBack = {}, onTimerStarted = {}) } }
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithText("Start timer").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `match setup asks for notifications before starting, and starts anyway when denied`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showSetup()

        compose.onNodeWithText("Start timer").performClick()
        compose.waitForIdle()

        val request = shadowOf(compose.activity).lastRequestedPermission
        assertThat(request).isNotNull()
        assertThat(request.requestedPermissions.toList()).containsExactly(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(session()).isNull()

        compose.activity.onRequestPermissionsResult(
            request.requestCode,
            request.requestedPermissions,
            intArrayOf(PackageManager.PERMISSION_DENIED),
        )
        compose.waitUntil(WAIT_MS) { session() != null }
    }

    @Test
    fun `match setup starts straight away when notifications are allowed`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showSetup()

        compose.onNodeWithText("Start timer").performClick()

        compose.waitUntil(WAIT_MS) { session() != null }
        assertThat(shadowOf(compose.activity).lastRequestedPermission).isNull()
    }

    @Test
    fun `alerts-off banner links to the notification settings and can be dismissed`() {
        var settings = 0
        var dismissed = 0
        compose.setContent {
            GoaTheme {
                TimerScreen(
                    TimerUiState(loading = false, active = TimerPreviewData.turn.copy(alertsOff = true)),
                    TimerActions(onOpenNotificationSettings = { settings++ }, onDismissAlertsBanner = { dismissed++ }),
                )
            }
        }

        compose.onNodeWithText("Lock-screen alerts are off", substring = true).assertExists()
        compose.onNodeWithText("Turn on").performClick()
        compose.onNodeWithContentDescription("Dismiss").performClick()
        assertThat(settings).isEqualTo(1)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `settings intent opens this app's notification settings`() {
        val intent = notificationSettingsIntent(app)
        assertThat(intent.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        assertThat(intent.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(app.packageName)
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
