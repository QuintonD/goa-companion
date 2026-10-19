package dev.quinton.goa.hub.trivia

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaJson
import dev.quinton.goa.core.domain.trivia.TriviaQuestion
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.presentation.trivia.QuizQuestionUi
import dev.quinton.goa.core.presentation.trivia.TriviaQuizUiState
import dev.quinton.goa.hub.HUB_URL
import dev.quinton.goa.hub.Hub
import dev.quinton.goa.hub.cookie
import dev.quinton.goa.hub.hubTest
import dev.quinton.goa.hub.plainClient
import dev.quinton.goa.hub.signIn
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.testHub
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Card trivia over HTTP: setup, a whole round, retries, summary, practice, progress per browser, Leave. */
class TriviaWebTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun withHub(block: suspend TriviaClient.(Hub, String) -> Unit) {
        TriviaFeature.random = Random(42)
        val hub = testHub(tmp.root.toPath())
        hubTest(hub) {
            val client = plainClient()
            val token = client.signIn()
            TriviaClient(client, token).block(hub, hub.devices.list().single().id)
        }
    }

    class TriviaClient(val client: HttpClient, val token: String) {
        suspend fun get(path: String): HttpResponse = client.get(path) { header(HttpHeaders.Cookie, cookie(token)) }

        suspend fun page(path: String): String {
            val response = get(path)
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            return response.bodyAsText()
        }

        suspend fun post(path: String, fields: List<Pair<String, String>> = emptyList(), htmx: Boolean = false) =
            client.submitForm(path, parameters { fields.forEach { (k, v) -> append(k, v) } }) {
                header(HttpHeaders.Cookie, cookie(token))
                header(HttpHeaders.Origin, HUB_URL)
                if (htmx) header("HX-Request", "true")
            }
    }

    private fun playing(device: String) = TriviaRounds.get(device)?.round?.state as? TriviaQuizUiState.Playing

    /** The right answer to the question on screen, as the form field the page would post. */
    private fun rightField(state: TriviaQuizUiState.Playing): Pair<String, String> = when (val q = state.question) {
        is QuizQuestionUi.Number -> "value" to q.value.toString()
        is QuizQuestionUi.Duel -> "pick" to q.right.name
    }

    private fun wrongField(state: TriviaQuizUiState.Playing): Pair<String, String> = when (val q = state.question) {
        is QuizQuestionUi.Number -> "value" to q.choices.first { it != q.value }.toString()

        is QuizQuestionUi.Duel ->
            "pick" to
                dev.quinton.goa.core.domain.trivia.DuelPick.entries.first { it != q.right }.name
    }

    @Test
    fun `the home preselects a hero, and the last Number can't be turned off`() = withHub { hub, _ ->
        val arien = hub.catalog.catalog.heroes.first()
        val html = page("/trivia?hero=${arien.id}")
        assertThat(html).contains("<span>${arien.name}</span>")
        assertThat(html).contains("1 hero")
        assertThat(html).contains("Start quiz")
        val only = "/trivia?setup=1&h=${arien.id}&s=initiative&g=basic&style=choices&len=10&act=stat:initiative"
        val after = page(only)
        assertThat(
            after,
        ).contains("id=\"act-stat-initiative\" name=\"act\" value=\"stat:initiative\" aria-pressed=\"true\"")
        // htmx gets the setup back with its URL to push.
        val fragment = client.get("/trivia?setup=1&s=attack&act=stat:defense") {
            header(HttpHeaders.Cookie, cookie(token))
            header("HX-Request", "true")
            header("HX-Target", "trivia-home")
        }
        assertThat(fragment.bodyAsText()).doesNotContain("<html")
        assertThat(fragment.headers["HX-Push-Url"]).contains("s=attack&s=defense")
    }

    @Test
    fun `a whole round with a wrong then right answers, the retry, summary, practice, play again and leave`() =
        withHub { hub, device ->
            val hero = hub.catalog.catalog.heroes.first()
            val setup = TriviaSetupForm.fields(TriviaConfig(heroIds = listOf(hero.id), length = 10, duels = true))
            val start = post("/trivia/start", setup)
            assertThat(start.headers[HttpHeaders.Location]).isEqualTo("/trivia/quiz")
            // The setup is remembered like the phone's last config.
            val docs = requireNotNull(hub.deviceStore.read(device, TriviaSlots.Trivia))
            assertThat(TriviaJson.decodeConfig(docs.config)?.heroIds).containsExactly(hero.id)

            var missedKey: String? = null
            var retried = false
            var asked = 0
            while (true) {
                val state = playing(device) ?: break
                asked++
                val question = TriviaRounds.get(device)!!.round.session.current
                val key = (question as? TriviaQuestion.CardNumber)?.fact?.key
                if (missedKey != null && key == missedKey) retried = true
                val wrong = missedKey == null && key != null
                val field = if (wrong) wrongField(state) else rightField(state)
                val answered = post("/trivia/quiz/answer", listOf(field, "n" to state.number.toString()), htmx = true)
                assertThat(answered.status).isEqualTo(HttpStatusCode.OK)
                val fragment = answered.bodyAsText()
                assertThat(fragment).contains(if (wrong) "Not quite." else "Right!")
                assertThat(fragment).contains("alt=\"Card image:")
                if (wrong) missedKey = key
                // A second tap on the same question changes nothing.
                post("/trivia/quiz/answer", listOf(rightField(state), "n" to state.number.toString()))
                assertThat(playing(device)?.correct).isEqualTo(!wrong)
                val next = post("/trivia/quiz/next", listOf("n" to state.number.toString()), htmx = true)
                if (playing(device) == null) assertThat(next.headers["HX-Redirect"]).isEqualTo("/trivia/quiz")
                check(asked < 50)
            }
            assertThat(asked).isEqualTo(10)
            assertThat(retried).isTrue()

            val summary = TriviaRounds.get(device)!!.round.state as TriviaQuizUiState.Summary
            assertThat(summary.misses).hasSize(1)
            val html = page("/trivia/quiz")
            assertThat(html).contains("Round over")
            assertThat(html).contains("9 of 10 right")
            assertThat(html).contains("Practise the missed ones")

            // Progress is this browser's, stored with every answer.
            val progress = TriviaJson.decodeProgress(hub.deviceStore.read(device, TriviaSlots.Trivia)?.progress)
            assertThat(progress).containsKey(missedKey)
            // Missed, then right on its retry three questions later: seen twice, right once, and the box
            // stays at 0 (a retry within MIN_SPACING questions doesn't move it, ADR-028).
            val missed = progress.getValue(missedKey!!)
            assertThat(missed.seen).isEqualTo(2)
            assertThat(missed.correct).isEqualTo(1)
            assertThat(missed.box).isEqualTo(0)

            // Practise: exactly the missed number, about twice.
            post("/trivia/quiz/practise")
            val spec = requireNotNull(hub.deviceStore.read(device, TriviaSlots.Round))
            assertThat(spec.factKeys).containsExactly(missedKey)
            assertThat(playing(device)?.length).isEqualTo(2)

            // End shows the summary at any point; Play again starts over.
            post("/trivia/quiz/end")
            assertThat(TriviaRounds.get(device)!!.round.state).isInstanceOf(TriviaQuizUiState.Summary::class.java)
            post("/trivia/quiz/again")
            assertThat(playing(device)?.number).isEqualTo(1)

            // Back before an answer leaves directly; after one it asks.
            assertThat(get("/trivia/quiz/leave").headers[HttpHeaders.Location]).isEqualTo("/trivia")
            val state = requireNotNull(playing(device))
            post("/trivia/quiz/answer", listOf(rightField(state), "n" to "1"))
            assertThat(page("/trivia/quiz/leave")).contains("Leave this round?")
            post("/trivia/quiz/leave")
            assertThat(TriviaRounds.get(device)).isNull()
            assertThat(hub.deviceStore.read(device, TriviaSlots.Round)).isNull()
            assertThat(get("/trivia/quiz").headers[HttpHeaders.Location]).isEqualTo("/trivia")
        }

    @Test
    fun `a round survives a hub restart from its saved setup, and reset forgets progress`() = withHub { hub, device ->
        val hero = hub.catalog.catalog.heroes.first()
        post("/trivia/start", TriviaSetupForm.fields(TriviaConfig(heroIds = listOf(hero.id))))
        val state = requireNotNull(playing(device))
        post("/trivia/quiz/answer", listOf(rightField(state), "n" to "1"))
        TriviaRounds.remove(device) // as after a restart
        assertThat(page("/trivia/quiz")).contains("Question 1 of 10")
        assertThat(TriviaJson.decodeProgress(hub.deviceStore.read(device, TriviaSlots.Trivia)?.progress)).isNotEmpty()
        assertThat(page("/trivia/reset")).contains("Reset progress?")
        post("/trivia/reset")
        assertThat(TriviaJson.decodeProgress(hub.deviceStore.read(device, TriviaSlots.Trivia)?.progress)).isEmpty()
        assertThat(hub.deviceStore.read(device, TriviaSlots.Trivia)?.config).isNotNull()
    }

    @Test
    fun `all heroes means the owned products`() = withHub { hub, device ->
        val product = hub.catalog.catalog.products.first().id
        hub.deviceStore.write(device, SharedSlots.Settings, AppSettings(ownedProducts = setOf(product)))
        val owned = hub.catalog.catalog.heroes.filter { it.product == product }.map { it.id }.toSet()
        val home = page("/trivia")
        assertThat(home).contains("All heroes you own")
        post("/trivia/start", TriviaSetupForm.fields(TriviaConfig()))
        val pool = TriviaRounds.get(device)!!.round.session.pool
        assertThat(pool).isNotEmpty()
        assertThat(pool.map { it.heroId }.toSet()).isEqualTo(owned)
        // One hero alone from its progress row.
        val other = owned.first()
        post("/trivia/start", TriviaSetupForm.fields(TriviaConfig()) + ("only" to other))
        assertThat(TriviaRounds.get(device)!!.round.session.pool.map { it.heroId }.toSet()).containsExactly(other)
    }

    @Test
    fun `a start that would ask nothing goes back to the setup`() = withHub { _, device ->
        val response = post(
            "/trivia/start",
            TriviaSetupForm.fields(TriviaConfig(heroIds = listOf("nobody"))) + ("only" to "nobody"),
        )
        assertThat(response.headers[HttpHeaders.Location]).startsWith("/trivia?")
        assertThat(TriviaRounds.get(device)).isNull()
    }
}
