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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.icon.GoaIcons
import dev.quinton.goa.core.designsystem.theme.FontLicense
import dev.quinton.goa.core.designsystem.theme.GoaFontLicenses
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.settings.ThemeMode

/** App identity shown on the About screen. */
data class AppInfo(val name: String, val versionName: String, val versionCode: Long)

/** The maker's Ko-fi page, for anyone who wants to give something towards the app (as on the web). */
internal const val DONATE_URL = "https://ko-fi.com/quintonde"

/** A community project the catalog is built from: its name, what it gives, and where it is published. */
internal data class DataSourceCredit(val name: String, val what: String, val where: String)

/** Community projects whose published data informed the catalog import (credit only, no code reused). */
internal val DataSourceCredits = listOf(
    DataSourceCredit(
        "Stats-of-Atlantis (QuasiStellar)",
        "Card data, hero details and artwork",
        "github.com/QuasiStellar/Stats-of-Atlantis",
    ),
    DataSourceCredit(
        "goa2-faq (yvo-niedrich)",
        "Card text cross-check and FAQ rulings",
        "github.com/yvo-niedrich/goa2-faq",
    ),
    DataSourceCredit("goa2 (dmrediger)", "Full card images", "github.com/dmrediger/goa2"),
    DataSourceCredit(
        "guards-of-atlantis-ii-data (colefin8)",
        "Card values cross-check",
        "github.com/colefin8/guards-of-atlantis-ii-data",
    ),
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
            // Prose reads as prose, on the page; only the rows you can tap sit on a (flat) group.
            item {
                Prose(
                    "Made by a player for their play group and shared free of charge: not sold, no " +
                        "ads. Your matches and players stay " +
                        "on this phone unless you join a group: then they sync phone to phone at the " +
                        "table, or through the group's sync server, which only ever holds them sealed " +
                        "and can't read them.",
                    Modifier.padding(horizontal = Spacing.xs),
                )
            }
            item { SettingsGroup(Modifier.padding(top = Spacing.lg)) { DonateRow() } }
            item { SectionHeader("Game content") }
            item {
                Column(Modifier.padding(horizontal = Spacing.xs)) {
                    Prose(
                        "Guards of Atlantis II, its heroes, card text, card images and artwork are by " +
                            "Wolff Designa. They appear here for non-commercial play by fans of the game.",
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        "Not affiliated with or endorsed by Wolff Designa.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            item { SectionHeader("Data sources") }
            item {
                Column(Modifier.padding(horizontal = Spacing.xs)) {
                    Prose(
                        "Thanks to these community projects, whose published data helped build and check the catalog:",
                    )
                    DataSourceCredits.forEach { credit ->
                        Spacer(Modifier.size(Spacing.md))
                        Text(credit.name, style = MaterialTheme.typography.titleMedium)
                        Prose(credit.what)
                        Text(
                            credit.where,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Prose("Each hero page names its sources; Verified means its data was checked against two of them.")
                }
            }
            item { SectionHeader("Original artwork") }
            item {
                Prose(
                    "Hero sigils, player crests, icons and the app icon were drawn for this app.",
                    Modifier.padding(horizontal = Spacing.xs),
                )
            }
            item { SectionHeader("Fonts") }
            item {
                SettingsGroup {
                    GoaFontLicenses.forEachIndexed { i, font ->
                        if (i > 0) GroupDivider()
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
        // The screen's one heading on the cards' parchment title plate, as other screens' headings are.
        TitlePlate(info.name, Modifier.widthIn(max = TITLE_PLATE_MAX), gear = false)
        Spacer(Modifier.size(Spacing.sm))
        Text(
            "Version ${info.versionName} (${info.versionCode})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val TITLE_PLATE_MAX = 320.dp

/** A paragraph on the page. */
@Composable
private fun Prose(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A row's second line. */
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

/** Opens the Ko-fi page in the browser; the app stays free either way. */
@Composable
internal fun DonateRow() {
    val uriHandler = LocalUriHandler.current
    val onClick = {
        // No browser to take the link (rare): nothing to open, so the tap does nothing.
        runCatching { uriHandler.openUri(DONATE_URL) }
        Unit
    }
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Support the app", style = MaterialTheme.typography.titleMedium)
                Body("Buy the maker a coffee on Ko-fi, if you like")
            }
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LicenseDialog(font: FontLicense, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val text = remember(font) {
        resources.openRawResource(font.textRes).bufferedReader().use { it.readText() }
    }
    GoaDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(Spacing.lg),
        title = { Text("${font.family} licence") },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
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
