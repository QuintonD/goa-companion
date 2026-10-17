package dev.quinton.goa

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.navigation.EXTRA_OPEN_TAB
import dev.quinton.goa.core.navigation.TopLevelTab
import dev.quinton.goa.ui.GoaApp
import dev.quinton.goa.ui.TabRequest
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** Tabs asked for by launch intents (the timer notification asks for the Timer tab). */
    private val tabRequests = MutableStateFlow<TabRequest?>(null)
    private var requestCount = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process restore) keeps its tab; only a real launch obeys the intent.
        val coldStart = savedInstanceState == null
        if (coldStart) handle(intent)
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val theme = (state as? MainUiState.Ready)?.theme ?: ThemeMode.DARK
            val dark = when (theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Transparent system bars with icons that contrast with the active theme.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            GoaTheme(themeMode = theme) {
                val canvas = MaterialTheme.colorScheme.background.toArgb()
                DisposableEffect(canvas) {
                    // Window canvas matches the theme so resizes and transitions never flash dark/light.
                    window.setBackgroundDrawable(canvas.toDrawable())
                    onDispose {}
                }
                GoaApp(tabRequests = tabRequests, coldStart = coldStart)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_OPEN_TAB) ?: return
        val tab = TopLevelTab.entries.firstOrNull { it.name == name } ?: return
        tabRequests.value = TabRequest(tab, requestCount++)
    }
}
