package dev.quinton.goa.hub.trivia

import dev.quinton.goa.core.domain.trivia.AnswerStyle
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.DuelPick
import dev.quinton.goa.core.domain.trivia.TriviaAnswer
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.presentation.trivia.TriviaCatalogFacts
import dev.quinton.goa.core.presentation.trivia.TriviaHomeAction
import dev.quinton.goa.core.presentation.trivia.TriviaHomeUiState
import dev.quinton.goa.core.presentation.trivia.TriviaQuizUiState
import dev.quinton.goa.core.presentation.trivia.TriviaRound
import dev.quinton.goa.hub.feature.HubContext
import dev.quinton.goa.hub.feature.HubFeature
import dev.quinton.goa.hub.heroes.CATALOG_CSS
import dev.quinton.goa.hub.store.SharedSlots
import dev.quinton.goa.hub.web.device
import dev.quinton.goa.hub.web.isHtmx
import dev.quinton.goa.hub.web.ui.HubPage
import dev.quinton.goa.hub.web.ui.emptyState
import dev.quinton.goa.hub.web.ui.respondFragment
import dev.quinton.goa.hub.web.ui.respondPage
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.random.Random
import kotlinx.coroutines.sync.withLock

/**
 * Card trivia (ADR-028): the home (progress per hero and the setup) and a quiz round, one question
 * per page or htmx swap. The questions, scoring, spacing and summary are `TriviaRound` from
 * :core:presentation over the domain's `TriviaEngine`, as on the phone; progress is this browser's
 * own ([TriviaSlots.Trivia]), stored with every answer; "all heroes" means the owned products.
 */
object TriviaFeature : HubFeature {
    const val PATH = "/trivia"
    const val QUIZ = "/trivia/quiz"

    internal val styles = listOf(CATALOG_CSS, "trivia/trivia.css")
    internal const val SCRIPT = "trivia/trivia.js"

    /** Rounds use this; tests seed it so a round is reproducible. */
    @Volatile
    internal var random: Random = Random.Default

    override fun Route.routes(ctx: HubContext) {
        get(PATH) { call.home(ctx) }
        post("$PATH/start") { call.start(ctx) }
        get("$PATH/reset") { call.resetPage(ctx) }
        post("$PATH/reset") { call.reset(ctx) }
        get(QUIZ) { call.quiz(ctx) }
        post("$QUIZ/answer") { call.answer(ctx) }
        post("$QUIZ/next") { call.roundAction(ctx) { round, n -> round.nextIf(n) } }
        post("$QUIZ/end") { call.roundAction(ctx) { round, _ -> round.stop() } }
        post("$QUIZ/again") { call.roundAction(ctx) { round, _ -> round.playAgain() } }
        post("$QUIZ/practise") { call.practise(ctx) }
        get("$QUIZ/leave") { call.leavePage() }
        post("$QUIZ/leave") { call.leave(ctx) }
    }

    @Volatile
    private var facts: TriviaCatalogFacts? = null

    private fun facts(catalog: Catalog): TriviaCatalogFacts =
        facts?.takeIf { it.catalog === catalog } ?: TriviaCatalogFacts(catalog).also { facts = it }

    private fun ApplicationCall.settings(ctx: HubContext): AppSettings =
        ctx.deviceStore.read(device.id, SharedSlots.Settings) ?: AppSettings()

    private fun ApplicationCall.record(ctx: HubContext) = TriviaRecord(ctx.deviceStore, device.id)

    // ---- Home -------------------------------------------------------------------------------------

    /** The setup on screen: from the form (with an action applied), else the last one (+ `?hero=`). */
    private fun ApplicationCall.homeState(ctx: HubContext, params: Parameters): TriviaHomeUiState {
        val base = TriviaSetupForm.parse(params) ?: run {
            val last = record(ctx).lastConfig() ?: TriviaConfig()
            params["hero"]?.let { last.copy(heroIds = listOf(it)) } ?: last
        }
        val config = TriviaSetupForm.action(params["act"])?.let { TriviaHomeAction.reduce(base, it) } ?: base
        return TriviaHomeUiState.of(
            facts(ctx.catalog.catalog),
            settings(ctx).ownedProducts,
            record(ctx).progress(),
            config,
        )
    }

    private suspend fun ApplicationCall.home(ctx: HubContext) {
        val state = homeState(ctx, request.queryParameters)
        if (isHtmx && request.headers["HX-Target"] == "trivia-home" && state is TriviaHomeUiState.Ready) {
            response.header("HX-Push-Url", TriviaSetupForm.href(state.setup))
            respondFragment { triviaHome(state) }
            return
        }
        respondPage(HubPage("Card trivia", back = "/heroes", styles = styles, scripts = listOf(SCRIPT))) {
            when (state) {
                is TriviaHomeUiState.Ready -> {
                    triviaHome(state)
                    resetDialog(state)
                }

                else -> emptyState(
                    "No heroes to quiz",
                    "The bundled hero data couldn't be read, so there are no card numbers to learn.",
                )
            }
        }
    }

    /** Start quiz, or (with `only`) a quiz on one hero with the current Numbers and Cards. */
    private suspend fun ApplicationCall.start(ctx: HubContext) {
        val params = receiveParameters()
        val ready = homeState(ctx, params) as? TriviaHomeUiState.Ready ?: return respondRedirect(PATH)
        val only = params["only"]
        val config = if (only != null) ready.heroConfig(only) else ready.startConfig()
        if (config == null) return navigate(TriviaSetupForm.href(ready.setup))
        record(ctx).saveConfig(ready.setup)
        begin(ctx, config, emptyList())
        navigate(QUIZ)
    }

    /** A full navigation to [path]: `HX-Redirect` for htmx, else a redirect. */
    private suspend fun ApplicationCall.navigate(path: String) {
        if (isHtmx) {
            response.header("HX-Redirect", path)
            respond(HttpStatusCode.NoContent)
        } else {
            respondRedirect(path)
        }
    }

    private suspend fun ApplicationCall.resetPage(ctx: HubContext) {
        val ready = homeState(ctx, Parameters.Empty) as? TriviaHomeUiState.Ready
        respondPage(HubPage("Reset progress?", back = PATH, styles = styles)) {
            if (ready == null) return@respondPage
            resetConfirm(ready)
        }
    }

    private suspend fun ApplicationCall.reset(ctx: HubContext) {
        record(ctx).reset()
        respondRedirect(PATH)
    }

    // ---- Quiz -------------------------------------------------------------------------------------

    private suspend fun ApplicationCall.begin(ctx: HubContext, config: TriviaConfig, factKeys: List<String>) {
        val settings = settings(ctx)
        val round = TriviaRound.start(
            catalog = ctx.catalog.catalog,
            config = config,
            factKeys = factKeys,
            ownedProducts = settings.ownedProducts,
            progress = record(ctx).progress(),
            hapticsEnabled = settings.hapticsEnabled,
            random = random,
        )
        val spec = TriviaRoundSpec.of(config, factKeys)
        TriviaRounds.put(device.id, TriviaRounds.Held(round, spec))
        ctx.deviceStore.write(device.id, TriviaSlots.Round, spec)
    }

    /** The round in memory, or the saved one restarted (after a hub restart), or null. */
    private suspend fun ApplicationCall.held(ctx: HubContext): TriviaRounds.Held? {
        TriviaRounds.get(device.id)?.let { return it }
        val spec = ctx.deviceStore.read(device.id, TriviaSlots.Round) ?: return null
        begin(ctx, spec.decodeConfig(), spec.factKeys)
        return TriviaRounds.get(device.id)
    }

    private suspend fun ApplicationCall.quiz(ctx: HubContext) {
        val held = held(ctx) ?: return respondRedirect(PATH)
        val state = held.lock.withLock { held.round.state }
        respondQuiz(state, fragment = false)
    }

    private suspend fun ApplicationCall.respondQuiz(state: TriviaQuizUiState, fragment: Boolean) {
        if (fragment) {
            respondFragment { quizSection(state) }
            return
        }
        val title = if (state is TriviaQuizUiState.Summary) "Round over" else "Card trivia"
        val page = HubPage(title, back = "$QUIZ/leave", showTabs = false, styles = styles, scripts = listOf(SCRIPT))
        respondPage(page) {
            quizSection(state)
            leaveDialog()
        }
    }

    /** After a POST: the new state as an htmx fragment (a full reload into the summary), else a redirect. */
    private suspend fun ApplicationCall.afterChange(state: TriviaQuizUiState) {
        when {
            !isHtmx -> respondRedirect(QUIZ)
            state is TriviaQuizUiState.Playing -> respondQuiz(state, fragment = true)
            else -> navigate(QUIZ)
        }
    }

    private suspend fun ApplicationCall.answer(ctx: HubContext) {
        val params = receiveParameters()
        val held = TriviaRounds.get(device.id) ?: return respondRedirect(PATH)
        val answer = params["value"]?.toIntOrNull()?.let { TriviaAnswer.Number(it) }
            ?: params["pick"]?.let { p -> DuelPick.entries.firstOrNull { it.name == p } }?.let { TriviaAnswer.Duel(it) }
        val number = params["n"]?.toIntOrNull()
        val state = held.lock.withLock {
            // Only the question on screen, once: a stale page or a double tap changes nothing.
            val open = (held.round.state as? TriviaQuizUiState.Playing)?.takeIf { it.number == number && !it.answered }
            if (answer != null && open != null) {
                val updates = held.round.answer(answer, ctx.clock.nowMillis())
                if (updates != null) record(ctx).record(updates)
            }
            held.round.state
        }
        afterChange(state)
    }

    private suspend fun ApplicationCall.roundAction(ctx: HubContext, action: (TriviaRound, Int?) -> Unit) {
        val params = receiveParameters()
        val held = held(ctx) ?: return respondRedirect(PATH)
        val state = held.lock.withLock {
            action(held.round, params["n"]?.toIntOrNull())
            held.round.state
        }
        afterChange(state)
    }

    /** "Practise the missed ones": the same round again if it already was that practice, else a new one. */
    private suspend fun ApplicationCall.practise(ctx: HubContext) {
        val held = held(ctx) ?: return respondRedirect(PATH)
        val practice = held.lock.withLock { held.round.practice() } ?: return respondRedirect(QUIZ)
        val spec = TriviaRoundSpec.of(practice.config, practice.factKeys)
        if (spec == held.spec) {
            held.lock.withLock { held.round.playAgain() }
        } else {
            begin(ctx, practice.config, practice.factKeys)
        }
        respondRedirect(QUIZ)
    }

    /** Back: once a question is answered, "Leave this round?"; before that (or on the summary) straight out. */
    private suspend fun ApplicationCall.leavePage() {
        val held = TriviaRounds.get(device.id)
        val playing = held?.lock?.withLock { held.round.state } as? TriviaQuizUiState.Playing
        if (playing == null || !playing.started) return respondRedirect(PATH)
        respondPage(HubPage("Card trivia", back = QUIZ, showTabs = false, styles = styles)) { leaveConfirm() }
    }

    private suspend fun ApplicationCall.leave(ctx: HubContext) {
        TriviaRounds.remove(device.id)
        ctx.deviceStore.delete(device.id, TriviaSlots.Round)
        respondRedirect(PATH)
    }

    private fun TriviaRound.nextIf(number: Int?) {
        val playing = state as? TriviaQuizUiState.Playing ?: return
        // Next only from the answered question on screen (a double tap doesn't skip one).
        if (playing.answered && (number == null || playing.number == number)) next()
    }
}

/** The setup as form fields (`setup=1&h=…&s=…&g=…&style=…&len=…&duels=1`) and the chip actions (`act`). */
internal object TriviaSetupForm {
    fun parse(params: Parameters): TriviaConfig? {
        if (params["setup"] != "1") return null
        val defaults = TriviaConfig()
        val stats = params.getAll("s").orEmpty().mapNotNull(TriviaStat::fromKey).toSet()
        val groups = params.getAll("g").orEmpty().mapNotNull(CardGroup::fromKey).toSet()
        return TriviaConfig(
            heroIds = params.getAll("h").orEmpty().filter { it.isNotBlank() }.distinct(),
            stats = stats.ifEmpty { defaults.stats },
            groups = groups.ifEmpty { defaults.groups },
            answerStyle = if (params["style"] == "recall") AnswerStyle.RECALL else AnswerStyle.CHOICES,
            length = params["len"]?.toIntOrNull()?.takeIf { it in TriviaConfig.LENGTHS } ?: defaults.length,
            duels = params["duels"] == "1",
        )
    }

    /** `stat:attack`, `group:tier2`, `style:recall`, `len:20`, `duels:on`, `hero:arien`, `all`. */
    fun action(act: String?): TriviaHomeAction? {
        if (act == null) return null
        val kind = act.substringBefore(':')
        val value = act.substringAfter(':', "")
        return when (kind) {
            "stat" -> TriviaStat.fromKey(value)?.let { TriviaHomeAction.ToggleStat(it) }

            "group" -> CardGroup.fromKey(value)?.let { TriviaHomeAction.ToggleGroup(it) }

            "style" -> TriviaHomeAction.SetAnswerStyle(
                if (value ==
                    "recall"
                ) {
                    AnswerStyle.RECALL
                } else {
                    AnswerStyle.CHOICES
                },
            )

            "len" -> value.toIntOrNull()?.takeIf { it in TriviaConfig.LENGTHS }?.let { TriviaHomeAction.SetLength(it) }

            "duels" -> TriviaHomeAction.SetDuels(value == "on")

            "hero" -> value.takeIf { it.isNotBlank() }?.let { TriviaHomeAction.ToggleHero(it) }

            "all" -> TriviaHomeAction.AllHeroes

            else -> null
        }
    }

    fun fields(config: TriviaConfig): List<Pair<String, String>> = buildList {
        add("setup" to "1")
        config.heroIds.forEach { add("h" to it) }
        TriviaStat.entries.filter { it in config.stats }.forEach { add("s" to it.key) }
        CardGroup.entries.filter { it in config.groups }.forEach { add("g" to it.key) }
        add("style" to if (config.answerStyle == AnswerStyle.RECALL) "recall" else "choices")
        add("len" to config.length.toString())
        if (config.duels) add("duels" to "1")
    }

    fun href(config: TriviaConfig): String =
        "${TriviaFeature.PATH}?" +
            Parameters.build { fields(config).forEach { (k, v) -> append(k, v) } }.formUrlEncode()
}
