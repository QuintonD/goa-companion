package dev.quinton.goa.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.icon.GoaIcons
import dev.quinton.goa.core.designsystem.theme.FontLicense
import dev.quinton.goa.core.designsystem.theme.GoaFontLicenses
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.settings.ThemeMode

/** App identity shown on the About screen. */
data class AppInfo(val name: String, val versionName: String, val versionCode: Long)

/** Community projects whose published data informed the catalog import (credit only, no code reused). */
internal val DataSourceCredits = listOf(
    "Stats-of-Atlantis (QuasiStellar)" to "Card data, hero details and artwork",
    "goa2-faq (yvo-niedrich)" to "Card text cross-check and FAQ rulings",
    "goa2 (dmrediger)" to "Full card images",
    "guards-of-atlantis-ii-data (colefin8)" to "Card values cross-check",
)

@Composable
fun AboutRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val info = remember(context) { context.appInfo() }
    AboutScreen(info = info, onBack = onBack)
}

private fun Context.appInfo(): AppInfo {
    val name = applicationInfo.loadLabel(packageManager).toString()
    val pkg = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
    val code = when {
        pkg == null -> 0L

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> pkg.longVersionCode

        else ->
            @Suppress("DEPRECATION")
            pkg.versionCode.toLong()
    }
    return AppInfo(name = name, versionName = pkg?.versionName ?: "dev", versionCode = code)
}

@Composable
fun AboutScreen(info: AppInfo, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var openLicense by rememberSaveable { mutableStateOf<String?>(null) }
    GoaScreen(title = "About", onBack = onBack, modifier = modifier) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xl,
            ),
        ) {
            item { AboutHeader(info) }
            item {
                GoaCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text("Private build", style = MaterialTheme.typography.titleMedium)
                            Body(
                                "Made for one play group. Not sold, not published, and it never goes online: " +
                                    "everything stays on this phone.",
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Game content") }
            item {
                GoaCard(Modifier.fillMaxWidth()) {
                    Body(
                        "Guards of Atlantis II, its heroes, card text, card images and artwork are by " +
                            "Wolff Designa. They appear here for private, non-commercial play.",
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        "Not affiliated with or endorsed by Wolff Designa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            item { SectionHeader("Data sources") }
            item {
                GoaCard(Modifier.fillMaxWidth()) {
                    Body("Thanks to these community projects, whose published data helped build and check the catalog:")
                    DataSourceCredits.forEach { (name, what) ->
                        Spacer(Modifier.size(Spacing.sm))
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Body(what)
                    }
                }
            }
            item { SectionHeader("Original artwork") }
            item {
                GoaCard(Modifier.fillMaxWidth()) {
                    Body("Hero sigils, player crests, icons and the app icon were drawn for this app.")
                }
            }
            item { SectionHeader("Fonts") }
            item {
                GoaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                    GoaFontLicenses.forEach { font ->
                        FontRow(font) { openLicense = font.family }
                    }
                }
            }
        }
    }
    GoaFontLicenses.firstOrNull { it.family == openLicense }?.let { font ->
        LicenseDialog(font, onDismiss = { openLicense = null })
    }
}

@Composable
private fun AboutHeader(info: AppInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(GoaIcons.BrandMark), contentDescription = null, modifier = Modifier.size(88.dp))
        Spacer(Modifier.size(Spacing.lg))
        Text(
            info.name,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Version ${info.versionName} (${info.versionCode})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FontRow(font: FontLicense, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Article,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(font.family, style = MaterialTheme.typography.titleMedium)
                Body("${font.license} · ${font.copyright}")
            }
            Text("View", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LicenseDialog(font: FontLicense, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val text = remember(font) {
        resources.openRawResource(font.textRes).bufferedReader().use { it.readText() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(Spacing.lg),
        title = { Text("${font.family} licence") },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

internal val PreviewAppInfo = AppInfo(name = "Atlantis Companion", versionName = "0.1.0", versionCode = 1)

@Preview
@Composable
private fun AboutScreenPreview() {
    GoaTheme { AboutScreen(PreviewAppInfo, onBack = {}) }
}

@Preview
@Composable
private fun AboutScreenLightPreview() {
    GoaTheme(ThemeMode.LIGHT) { AboutScreen(PreviewAppInfo, onBack = {}) }
}
