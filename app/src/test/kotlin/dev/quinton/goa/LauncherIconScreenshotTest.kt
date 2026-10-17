package dev.quinton.goa

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Adaptive icon layers under a circle and a squircle-ish mask, plus the themed (monochrome) icon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LauncherIconScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun launcherIcon() {
        compose.setContent {
            Row(
                Modifier.background(Color(0xFF5A6A7A)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Layered(CircleShape)
                Layered(RoundedCornerShape(32.dp))
                Box(Modifier.size(108.dp).clip(CircleShape).background(Color(0xFFDCE6F0))) {
                    Image(
                        painterResource(R.drawable.ic_launcher_monochrome),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color(0xFF1C3550)),
                        modifier = Modifier.size(108.dp),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }
}

@androidx.compose.runtime.Composable
private fun Layered(shape: Shape) {
    Box(Modifier.size(108.dp).clip(shape)) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(108.dp))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(108.dp))
    }
}
