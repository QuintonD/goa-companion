package dev.quinton.goa.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.settings.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun sample(mode: ThemeMode) {
        compose.setContent {
            GoaTheme(themeMode = mode) {
                GoaScreen(title = "Steel and bronze", onBack = {}) { padding ->
                    Column(Modifier.padding(padding).padding(Spacing.screen)) {
                        Text("Display", style = MaterialTheme.typography.headlineMedium)
                        Text("Body text on the app canvas.", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = {}) { Text("Primary action") }
                    }
                }
            }
        }
    }

    @Test fun dark() {
        sample(ThemeMode.DARK)
        compose.onRoot().captureRoboImage()
    }

    @Test fun light() {
        sample(ThemeMode.LIGHT)
        compose.onRoot().captureRoboImage()
    }
}
