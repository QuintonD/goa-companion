package dev.quinton.goa.hub.settings

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.backup.BackupCodec
import dev.quinton.goa.core.domain.backup.BackupDocument
import dev.quinton.goa.core.domain.backup.BackupFiles
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.presentation.settings.RatingConstants
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.match
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.player
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.store.Devices
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.testHub
import dev.quinton.goa.hub.web.ui.ShellSlots
import dev.quinton.goa.hub.web.ui.ThemeChoice
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Settings on the web: per-browser settings, the group's rating constants, backup (ADR-030 §8). */
class SettingsPagesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock()

    private class Browser(val client: HttpClient, val token: String) {
        val id: String get() = Devices.idOf(token)

        suspend fun get(path: String, htmx: Boolean = false): HttpResponse = client.get(path) {
            header(HttpHeaders.Cookie, cookie(token))
            if (htmx) header("HX-Request", "true")
        }

        suspend fun post(
            path: String,
            vararg fields: Pair<String, String>,
            htmx: Boolean = false,
            origin: String? = HUB_URL,
        ): HttpResponse = client.submitForm(path, Parameters.build { fields.forEach { (k, v) -> append(k, v) } }) {
            header(HttpHeaders.Cookie, cookie(token))
            origin?.let { header(HttpHeaders.Origin, it) }
            if (htmx) header("HX-Request", "true")
        }

        suspend fun upload(text: String): HttpResponse = client.post("/settings/backup/import") {
            header(HttpHeaders.Cookie, cookie(token))
            header(HttpHeaders.Origin, HUB_URL)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            text.toByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/json")
                                append(HttpHeaders.ContentDisposition, "filename=\"backup.json\"")
                            },
                        )
                    },
                ),
            )
        }
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.browser(): Browser {
        val client = plainClient()
        return Browser(client, client.signIn())
    }

    private fun hub(): Hub = testHub(tmp.root.toPath(), clock).also { hub ->
        runBlocking {
            hub.groupStore.upsertPlayer(player("ann", "Ann"))
            hub.groupStore.upsertPlayer(player("bob", "Bob"))
            hub.groupStore.upsertMatch(match("m1", listOf("ann"), listOf("bob"), playedAt = 1))
        }
    }

    @Test
    fun `settings round-trip into the browser's slots`() {
        val hub = hub()
        hubTest(hub) {
            val b = browser()
            val page = b.get("/settings").bodyAsText()
            assertThat(page).contains("I own every product")
            assertThat(page).contains("Lock-screen alerts")
            assertThat(page).contains("iPhones can't vibrate")

            assertThat(b.post("/settings/theme", "theme" to "LIGHT").headers[HttpHeaders.Location])
                .isEqualTo("/settings#appearance")
            assertThat(hub.deviceStore.read(b.id, ShellSlots.Theme)).isEqualTo(ThemeChoice.LIGHT)
            assertThat(hub.deviceStore.read(b.id, SharedSlots.Settings)!!.theme).isEqualTo(ThemeMode.LIGHT)
            assertThat(b.get("/settings").bodyAsText()).contains("data-theme=\"light\"")

            b.post("/settings/table", "sounds" to "true")
            val table = hub.deviceStore.read(b.id, SharedSlots.Settings)!!
            assertThat(table.hapticsEnabled).isFalse()
            assertThat(table.soundsEnabled).isTrue()
            assertThat(table.theme).isEqualTo(ThemeMode.LIGHT)

            val fragment = b.post("/settings/timer-layout", "layout" to "ROUND", htmx = true)
            assertThat(fragment.bodyAsText()).doesNotContain("<html")
            assertThat(fragment.bodyAsText()).contains("id=\"running-timer\"")
            assertThat(hub.deviceStore.read(b.id, SharedSlots.Settings)!!.timerLayout).isEqualTo(TimerLayout.ROUND)

            val products = hub.catalog.catalog.products.map { it.id }
            // "Everything" off keeps every product ticked; then one product is unticked.
            b.post("/settings/products")
            assertThat(hub.deviceStore.read(b.id, SharedSlots.Settings)!!.ownedProducts)
                .containsExactlyElementsIn(products)
            b.post("/settings/products", *products.drop(1).map { "product" to it }.toTypedArray())
            assertThat(hub.deviceStore.read(b.id, SharedSlots.Settings)!!.ownedProducts)
                .containsExactlyElementsIn(products.drop(1))
            b.post("/settings/products", "everything" to "true")
            assertThat(hub.deviceStore.read(b.id, SharedSlots.Settings)!!.ownedProducts).isEmpty()
        }
    }

    @Test
    fun `a fresh browser follows the app's defaults`() {
        val hub = hub()
        hubTest(hub) {
            val b = browser()
            assertThat(SettingsFeature.settings(HubContext(hub), b.id)).isEqualTo(AppSettings())
            val page = b.get("/settings").bodyAsText()
            assertThat(page).contains("data-theme=\"dark\"")
            assertThat(page).contains("aria-pressed=\"true\"")
        }
    }

    @Test
    fun `rating constants are stamped like the app, and unchanged values store nothing`() {
        val hub = hub()
        hubTest(hub) {
            val b = browser()
            assertThat(b.get("/settings/ratings").bodyAsText()).contains("(1 rated match)")
            clock.advance(1_000)
            val before = hub.groupStore.read().eloConfig
            b.post("/settings/ratings", "kEstablished" to "33", "heroInfluence" to "0.5")
            val after = hub.groupStore.read().eloConfig
            // Snapped to the slider's 5-point grid, stamped now (EditStamp.next), as the app's repository does.
            assertThat(after.config).isEqualTo(EloConfig.DEFAULT.copy(kEstablished = 35.0))
            assertThat(after.updatedAt).isEqualTo(maxOf(clock.nowMillis(), before.updatedAt + 1))

            clock.advance(1_000)
            b.post("/settings/ratings", "kEstablished" to "35")
            assertThat(hub.groupStore.read().eloConfig).isEqualTo(after)

            val fragment = b.post("/settings/ratings", "seatAdvantage" to "-15", htmx = true).bodyAsText()
            assertThat(fragment).doesNotContain("<html")
            assertThat(fragment).contains("−15 pts")

            assertThat(
                b.get("/settings/ratings/reset").bodyAsText(),
            ).contains("All constants go back to their defaults")
            clock.advance(1_000)
            b.post("/settings/ratings/reset")
            val reset = hub.groupStore.read().eloConfig
            assertThat(reset).isEqualTo(StampedEloConfig(EloConfig.DEFAULT, clock.nowMillis()))
        }
    }

    @Test
    fun `a ratings form opened before a phone's change writes back only the constant moved on it`() {
        val hub = hub()
        hubTest(hub) {
            val b = browser()
            assertThat(b.get("/settings/ratings").bodyAsText()).contains("name=\"shown-kEstablished\"")
            // The form as the page showed it: every slider at the defaults.
            val form = RatingConstants.all.flatMap {
                val shown = it.get(EloConfig.DEFAULT).toString()
                listOf(it.key to shown, "shown-${it.key}" to shown)
            }.toMap()
            // Meanwhile a phone sets K and syncs.
            val phone = StampedEloConfig(EloConfig.DEFAULT.copy(kEstablished = 40.0), clock.nowMillis() + 10)
            runBlocking { hub.groupStore.mergeIn(SyncDocument(eloConfig = phone)) }
            clock.advance(1_000)
            b.post("/settings/ratings", *(form + ("heroInfluence" to "0.25")).toList().toTypedArray())
            val after = hub.groupStore.read().eloConfig
            assertThat(after.config).isEqualTo(phone.config.copy(heroInfluence = 0.25))
            assertThat(after.updatedAt).isGreaterThan(phone.updatedAt)
            // Saving the stale form untouched stores nothing.
            clock.advance(1_000)
            b.post("/settings/ratings", *form.toList().toTypedArray())
            assertThat(hub.groupStore.read().eloConfig).isEqualTo(after)
        }
    }

    @Test
    fun `the backup export is the app's file for the same data`() {
        val hub = hub()
        runBlocking { hub.groupStore.updateEloConfig { it.copy(kProvisional = 50.0) } }
        hubTest(hub) {
            val b = browser()
            b.post("/settings/table", "haptics" to "true")
            val response = b.get("/settings/backup/export")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.headers[HttpHeaders.ContentDisposition]).contains("goa-backup-2025-10-09.json")
            val text = response.bodyAsText()
            val document = BackupCodec.decode(text)
            val expected = BackupFiles.export(
                hub.groupStore.read(),
                exportedAt = document.exportedAt,
                appVersion = document.appVersion,
                extras = mapOf(
                    BackupFiles.SETTINGS_KEY to
                        BackupFiles.settingsJson(AppSettings(hapticsEnabled = true, soundsEnabled = false)),
                ),
            )
            assertThat(text).isEqualTo(BackupCodec.encode(expected))
            assertThat(document.format).isEqualTo(BackupDocument.FORMAT)
            assertThat(document.version).isEqualTo(BackupDocument.CURRENT_VERSION)
            assertThat(document.eloConfig).isEqualTo(EloConfig.DEFAULT.copy(kProvisional = 50.0))
            assertThat(document.eloConfigUpdatedAt).isEqualTo(hub.groupStore.read().eloConfig.updatedAt)
            assertThat(document.players.map { it.id }).containsExactly("ann", "bob")
            assertThat(document.matches.map { it.id }).containsExactly("m1")
        }
    }

    @Test
    fun `importing previews the change, then merges like a sync where the later edit wins`() {
        val hub = hub()
        hubTest(hub) {
            val b = browser()
            val ann = hub.groupStore.read().players.first { it.id == "ann" }
            val bob = hub.groupStore.read().players.first { it.id == "bob" }
            val file = BackupDocument(
                exportedAt = 5,
                // Ann renamed later than the hub's copy; Bob's file copy is older and loses.
                players = listOf(
                    ann.copy(name = "Anna", updatedAt = ann.updatedAt + 10),
                    bob.copy(name = "Robert", updatedAt = 0),
                    player("cid", "Cid"),
                ),
                matches = listOf(match("m2", listOf("ann"), listOf("cid"), playedAt = 9)),
                eloConfig = EloConfig.DEFAULT.copy(kEstablished = 25.0),
                extras = mapOf(
                    BackupFiles.SETTINGS_KEY to BackupFiles.settingsJson(AppSettings(theme = ThemeMode.LIGHT)),
                ),
            )
            val preview = b.upload(BackupCodec.encode(file))
            assertThat(preview.status).isEqualTo(HttpStatusCode.OK)
            val previewText = preview.bodyAsText()
            assertThat(previewText).contains("+1 match, +1 player, 1 updated")
            assertThat(previewText).contains("custom rating settings")
            // Nothing changes before Merge.
            assertThat(hub.groupStore.read().players.map { it.name }).containsExactly("Ann", "Bob")

            clock.advance(1_000)
            val done = b.post("/settings/backup/import/confirm").bodyAsText()
            assertThat(done).contains("Merged: +1 match, +1 player, 1 updated")
            val names = hub.groupStore.data().players().map { it.name }
            assertThat(names).containsExactly("Anna", "Bob", "Cid")
            assertThat(hub.groupStore.read().matches.map { it.id }).containsExactly("m1", "m2")
            // The file's settings weren't asked for: the group's constants and this browser's theme stay.
            assertThat(hub.groupStore.read().eloConfig.config).isEqualTo(EloConfig.DEFAULT)
            assertThat(hub.deviceStore.read(b.id, ShellSlots.Theme)).isNull()
            // The pending file is gone: a second Merge does nothing.
            assertThat(b.post("/settings/backup/import/confirm").status).isEqualTo(HttpStatusCode.Conflict)

            b.upload(BackupCodec.encode(file))
            b.post("/settings/backup/import/confirm", "useSettings" to "true")
            val elo = hub.groupStore.read().eloConfig
            assertThat(elo.config.kEstablished).isEqualTo(25.0)
            assertThat(elo.updatedAt).isEqualTo(clock.nowMillis())
            assertThat(hub.deviceStore.read(b.id, ShellSlots.Theme)).isEqualTo(ThemeChoice.LIGHT)
        }
    }

    @Test
    fun `a file that isn't a backup is refused with the app's message`() {
        val hub = hub()
        hubTest(hub) {
            val b = browser()
            val bad = b.upload("""{"format":"other","exportedAt":1}""")
            assertThat(bad.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
            assertThat(bad.bodyAsText()).contains("This file isn't a Guards of Atlantis backup.")
            val ghost = BackupDocument(
                exportedAt = 1,
                players = listOf(player("ann", "Ann")),
                matches = listOf(match("g", listOf("ann"), listOf("nobody"))),
            )
            assertThat(b.upload(BackupCodec.encode(ghost)).bodyAsText()).contains("player nobody isn't in this backup")
            // Not a file upload at all.
            val form = b.post("/settings/backup/import", "file" to "{}")
            assertThat(form.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(form.bodyAsText()).contains("Choose a backup file")
        }
    }

    @Test
    fun `about credits the sources, fonts and libraries, and settings changes need the hub's origin`() {
        val hub = hub()
        hubTest(hub) {
            val b = browser()
            val about = b.get("/settings/about").bodyAsText()
            assertThat(about).contains("Wolff Designa")
            assertThat(about).contains("dmrediger/goa2 @")
            assertThat(about).contains("Rules text: Guards of Atlantis II rulebook")
            assertThat(about).contains("htmx 2.0.11")
            assertThat(about).contains("Material Symbols")
            assertThat(about).contains("/static/fonts/OFL-Cinzel.txt")
            assertThat(b.get("/static/licenses/htmx/LICENSE").status).isEqualTo(HttpStatusCode.OK)

            val forged = b.post("/settings/theme", "theme" to "LIGHT", origin = "https://evil.example")
            assertThat(forged.status).isEqualTo(HttpStatusCode.Forbidden)
            val ratings = b.post("/settings/ratings", "kEstablished" to "50", origin = null)
            assertThat(ratings.status).isEqualTo(HttpStatusCode.Forbidden)
            assertThat(hub.deviceStore.read(b.id, ShellSlots.Theme)).isNull()
            assertThat(hub.groupStore.read().eloConfig.config).isEqualTo(EloConfig.DEFAULT)
        }
    }

    @Test
    fun `official sources are read from the importer's list`() {
        val sources = OfficialSources.parse(
            """
            | Source | Commit |
            |---|---|
            | https://github.com/a/b | `abc123` |

            | Document | URL | SHA-256 |
            |---|---|---|
            | Rulebook | https://x.example/?a=1&b=2 | `${"a".repeat(64)}` |

            | File | Source |
            |---|---|
            | `official/cards/x.webp` | https://raw.example/x |
            | `official/cards/y.webp` | https://raw.example/y |
            """.trimIndent(),
        )
        assertThat(sources.repositories).containsExactly("https://github.com/a/b" to "abc123")
        assertThat(sources.documents).containsExactly("Rulebook" to "a".repeat(64))
        assertThat(sources.files).isEqualTo(2)
    }
}
