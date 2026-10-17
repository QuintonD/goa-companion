package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Standard screen frame: title bar (with back arrow when [onBack] is set) over the content,
 * finished with an engraved bronze rule. The app shell owns the bottom bar and window insets,
 * so content gets its own padding only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoaScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                EngravedRule()
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

/** Hairline bronze rule that fades out at both ends; separates bars from content. */
@Composable
fun EngravedRule(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.2f to c.copy(alpha = 0.35f),
                    0.8f to c.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/** Temporary body for screens not built yet. Must not ship (PRODUCT.md quality bar). */
@Composable
fun PlaceholderScreen(title: String, onBack: (() -> Unit)? = null) {
    GoaScreen(title = title, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("$title — coming soon", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
